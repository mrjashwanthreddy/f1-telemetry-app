let speedChartInstance = null;
let inputsChartInstance = null;

// Configure global Chart.js dark mode aesthetics
Chart.defaults.color = '#8b95a5';
Chart.defaults.font.family = "'Rajdhani', sans-serif";

async function loadAnalytics() {
    const sessionId = document.getElementById('sessionInput').value;
    const lapNum = document.getElementById('lapInput').value;
    
    // Using demo user JWT is normally required, but we left this endpoint public in SecurityConfig (or we'll just handle 401 if needed).
    // Actually we need to make sure the endpoint is public or we pass JWT. For now, since it's local, we fetch directly.
    try {
        const response = await fetch(`/api/analytics/sessions/${sessionId}/lap/${lapNum}`);
        if (!response.ok) {
            alert("Failed to fetch data (ensure you are logged in or endpoint is public)");
            return;
        }
        
        const data = await response.json();
        if (data.length === 0) {
            alert(`No data found for Session: ${sessionId} | Lap: ${lapNum}`);
            return;
        }
        
        renderCharts(data);
    } catch (e) {
        console.error("Error fetching analytics", e);
    }
}

function renderCharts(data) {
    // Generate labels (using array index as proxy for distance/time)
    const labels = data.map((_, index) => index);
    const speeds = data.map(record => record.speed);
    const throttles = data.map(record => record.throttle * 100);
    const brakes = data.map(record => record.brake * 100);

    // Speed Chart
    const ctxSpeed = document.getElementById('speedChart').getContext('2d');
    if (speedChartInstance) speedChartInstance.destroy();
    speedChartInstance = new Chart(ctxSpeed, {
        type: 'line',
        data: {
            labels: labels,
            datasets: [{
                label: 'Speed (km/h)',
                data: speeds,
                borderColor: '#00b0ff',
                backgroundColor: 'rgba(0, 176, 255, 0.1)',
                fill: true,
                tension: 0.2,
                pointRadius: 0
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            scales: {
                y: { min: 0, max: 350 }
            },
            interaction: {
                intersect: false,
                mode: 'index',
            }
        }
    });

    // Inputs Chart
    const ctxInputs = document.getElementById('inputsChart').getContext('2d');
    if (inputsChartInstance) inputsChartInstance.destroy();
    inputsChartInstance = new Chart(ctxInputs, {
        type: 'line',
        data: {
            labels: labels,
            datasets: [
                {
                    label: 'Throttle %',
                    data: throttles,
                    borderColor: '#00e676', // Green
                    tension: 0.1,
                    pointRadius: 0
                },
                {
                    label: 'Brake %',
                    data: brakes,
                    borderColor: '#ff2a2a', // Red
                    tension: 0.1,
                    pointRadius: 0
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            scales: {
                y: { min: 0, max: 105 }
            },
            interaction: {
                intersect: false,
                mode: 'index',
            }
        }
    });
}
