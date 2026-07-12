/**
 * Telemetry Charting & Waveform Rendering Library
 * Handles:
 * 1. 30Hz high-performance HTML5 Canvas scrolling waveform (Live Dashboard).
 * 2. Multi-lap post-race comparisons with dual Y-axes (Session History).
 */

const TelemetryChart = (function () {
    // ── Live Scrolling Waveform Configurations ────────────────────────────────
    let canvas = null;
    let ctx = null;
    const maxPoints = 200; // ~3-4 seconds of history at 60Hz
    const history = {
        speed: [],
        throttle: [],
        brake: []
    };

    function initLiveWaveform(canvasId) {
        canvas = document.getElementById(canvasId);
        if (!canvas) return;
        ctx = canvas.getContext('2d');
        
        // Handle High-DPI screens
        resizeCanvas();
        window.addEventListener('resize', resizeCanvas);
        
        // Start redraw loop (using requestAnimationFrame for 60 FPS fluidity)
        drawWaveformLoop();
    }

    function resizeCanvas() {
        if (!canvas) return;
        const rect = canvas.getBoundingClientRect();
        canvas.width = rect.width * window.devicePixelRatio;
        canvas.height = rect.height * window.devicePixelRatio;
        ctx.scale(window.devicePixelRatio, window.devicePixelRatio);
    }

    function pushPoint(speed, throttle, brake) {
        history.speed.push(speed);
        history.throttle.push(throttle);
        history.brake.push(brake);

        if (history.speed.length > maxPoints) {
            history.speed.shift();
            history.throttle.shift();
            history.brake.shift();
        }
    }

    function drawWaveformLoop() {
        if (!canvas || !ctx) return;
        requestAnimationFrame(drawWaveformLoop);

        const w = canvas.width / window.devicePixelRatio;
        const h = canvas.height / window.devicePixelRatio;

        ctx.clearRect(0, 0, w, h);

        // 1. Draw Grid Lines
        ctx.strokeStyle = 'rgba(0, 0, 0, 0.05)';
        ctx.lineWidth = 1;
        // Horizontal grid
        for (let y = 0; y < h; y += 20) {
            ctx.beginPath();
            ctx.moveTo(0, y);
            ctx.lineTo(w, y);
            ctx.stroke();
        }
        // Vertical grid (moving)
        const offset = (Date.now() / 30) % 20;
        for (let x = -offset; x < w; x += 20) {
            ctx.beginPath();
            ctx.moveTo(x, 0);
            ctx.lineTo(x, h);
            ctx.stroke();
        }

        const len = history.speed.length;
        if (len < 2) return;

        // 2. Helper to draw individual trace lines
        function drawTrace(data, color, minVal, maxVal, isPercent) {
            ctx.beginPath();
            ctx.strokeStyle = color;
            ctx.lineWidth = 2.5;
            ctx.lineJoin = 'round';

            for (let i = 0; i < len; i++) {
                const x = (i / (maxPoints - 1)) * w;
                // Scale value to canvas height (0 is bottom, height is top)
                const val = data[i];
                const normalized = (val - minVal) / (maxVal - minVal);
                const y = h - (normalized * (h - 20) + 10); // leave 10px padding top/bottom

                if (i === 0) {
                    ctx.moveTo(x, y);
                } else {
                    ctx.lineTo(x, y);
                }
            }
            ctx.stroke();
        }

        // Speed (0 to 360 km/h) -> Blue
        drawTrace(history.speed, '#3b82f6', 0, 360, false);
        // Throttle (0 to 1) -> Green
        drawTrace(history.throttle, '#10b981', 0, 1, true);
        // Brake (0 to 1) -> Red
        drawTrace(history.brake, '#ff3366', 0, 1, true);
    }


    // ── Post-Race Lap Comparison Chart ──────────────────────────────────────
    let compareChart = null;

    function renderComparisonChart(canvasId, lapsData) {
        const canvasEl = document.getElementById(canvasId);
        if (!canvasEl) return;

        if (compareChart) {
            compareChart.destroy();
        }

        // We determine standard F1 colors to differentiate laps:
        const colorPalette = [
            { speed: '#1d4ed8', throttle: '#15803d', brake: '#dc2626' }, // First Lap (strong Blue, Green, Red)
            { speed: '#0ea5e9', throttle: '#d97706', brake: '#be123c' }, // Second Lap (Sky, Amber, Rose)
            { speed: '#8b5cf6', throttle: '#6d28d9', brake: '#db2777' }  // Third Lap (Purple, Violet, Pink)
        ];

        const chartDatasets = [];

        lapsData.forEach((lap, index) => {
            const colors = colorPalette[index % colorPalette.length];
            const dashConfig = index > 0 ? [5, 5] : []; // Dashed line for comparison lap
            const prefix = `Lap ${lap.lapNum} (${lap.trackName}) · `;

            // Speed dataset (Left Axis)
            chartDatasets.push({
                label: `${prefix}Speed`,
                data: lap.telemetry.map(t => ({ x: t.timestamp - lap.telemetry[0].timestamp, y: t.speed })),
                borderColor: colors.speed,
                borderWidth: 2.5,
                borderDash: dashConfig,
                fill: false,
                yAxisID: 'ySpeed',
                pointRadius: 0
            });

            // Throttle dataset (Right Axis)
            chartDatasets.push({
                label: `${prefix}Throttle`,
                data: lap.telemetry.map(t => ({ x: t.timestamp - lap.telemetry[0].timestamp, y: Math.round(t.throttle * 100) })),
                borderColor: colors.throttle,
                borderWidth: 1.5,
                borderDash: [2, 4],
                fill: false,
                yAxisID: 'yInputs',
                pointRadius: 0
            });

            // Brake dataset (Right Axis)
            chartDatasets.push({
                label: `${prefix}Brake`,
                data: lap.telemetry.map(t => ({ x: t.timestamp - lap.telemetry[0].timestamp, y: Math.round(t.brake * 100) })),
                borderColor: colors.brake,
                borderWidth: 1.5,
                borderDash: [2, 4],
                fill: false,
                yAxisID: 'yInputs',
                pointRadius: 0
            });
        });

        compareChart = new Chart(canvasEl, {
            type: 'line',
            data: {
                datasets: chartDatasets
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                interaction: {
                    mode: 'index',
                    intersect: false
                },
                scales: {
                    x: {
                        type: 'linear',
                        title: {
                            display: true,
                            text: 'Lap Time Elapsed (ms)',
                            color: '#64748b',
                            font: { family: 'Rajdhani', weight: 'bold' }
                        },
                        grid: { color: 'rgba(0, 0, 0, 0.05)' },
                        ticks: { color: '#64748b', font: { family: 'Rajdhani' } }
                    },
                    ySpeed: {
                        type: 'linear',
                        position: 'left',
                        title: {
                            display: true,
                            text: 'Speed (km/h)',
                            color: '#1d4ed8',
                            font: { family: 'Rajdhani', weight: 'bold', size: 12 }
                        },
                        grid: { color: 'rgba(0, 0, 0, 0.05)' },
                        ticks: { color: '#1d4ed8', font: { family: 'Rajdhani' } },
                        min: 0,
                        max: 360
                    },
                    yInputs: {
                        type: 'linear',
                        position: 'right',
                        title: {
                            display: true,
                            text: 'Throttle / Brake (%)',
                            color: '#15803d',
                            font: { family: 'Rajdhani', weight: 'bold', size: 12 }
                        },
                        grid: { drawOnChartArea: false }, // avoid overlapping grids
                        ticks: { color: '#15803d', font: { family: 'Rajdhani' } },
                        min: 0,
                        max: 100
                    }
                },
                plugins: {
                    legend: {
                        labels: {
                            color: '#0f172a',
                            font: { family: 'Rajdhani', weight: '600' }
                        }
                    },
                    tooltip: {
                        backgroundColor: 'rgba(255, 255, 255, 0.95)',
                        titleColor: '#0f172a',
                        bodyColor: '#334155',
                        borderColor: 'rgba(0, 0, 0, 0.1)',
                        borderWidth: 1,
                        titleFont: { family: 'Rajdhani' },
                        bodyFont: { family: 'Rajdhani' }
                    }
                }
            }
        });
    }

    return {
        initLiveWaveform,
        pushLiveWaveformPoint: pushPoint,
        renderComparisonChart
    };
})();
window.TelemetryChart = TelemetryChart;
