async function loadSessions() {
    const list = document.getElementById('session-list');
    list.innerHTML = '<li>Loading...</li>';
    
    const token = localStorage.getItem('jwtToken');
    
    try {
        const res = await fetch('/api/history/sessions', {
            headers: {
                'Authorization': `Bearer ${token}`
            }
        });
        if (res.status === 401 || res.status === 403) {
            logout(); // Force login if token expired
            return;
        }
        if (!res.ok) throw new Error("Failed to load sessions");
        
        const sessions = await res.json();
        list.innerHTML = '';
        
        if (sessions.length === 0) {
            list.innerHTML = '<li>No sessions found. Run the simulation to complete a lap.</li>';
            return;
        }

        sessions.forEach(session => {
            const li = document.createElement('li');
            const date = new Date(session.timestamp).toLocaleString();
            li.innerHTML = `<strong>${session.trackName}</strong><br/><small>${date}</small>`;
            li.onclick = () => loadLapDetails(session.sessionId, session.trackName);
            list.appendChild(li);
        });

    } catch (e) {
        console.error(e);
        list.innerHTML = '<li>Error loading sessions.</li>';
    }
}

async function loadLapDetails(sessionId, trackName) {
    document.getElementById('lap-details-title').textContent = `Laps for ${trackName}`;
    const tbody = document.getElementById('lap-table-body');
    tbody.innerHTML = '<tr><td colspan="5">Loading...</td></tr>';
    
    const token = localStorage.getItem('jwtToken');

    try {
        const res = await fetch(`/api/history/sessions/${sessionId}/laps`, {
            headers: {
                'Authorization': `Bearer ${token}`
            }
        });
        if (res.status === 401 || res.status === 403) {
            logout();
            return;
        }
        if (!res.ok) throw new Error("Failed to load laps");
        
        const laps = await res.json();
        tbody.innerHTML = '';

        if (laps.length === 0) {
            tbody.innerHTML = '<tr><td colspan="5">No laps completed yet for this session.</td></tr>';
            return;
        }

        laps.forEach(lap => {
            const tr = document.createElement('tr');
            
            // Convert MS to seconds with 3 decimals
            const s1 = lap.sector1TimeInMS > 0 ? (lap.sector1TimeInMS / 1000).toFixed(3) : "-";
            const s2 = lap.sector2TimeInMS > 0 ? (lap.sector2TimeInMS / 1000).toFixed(3) : "-";
            const s3 = lap.sector3TimeInMS > 0 ? (lap.sector3TimeInMS / 1000).toFixed(3) : "-";
            
            // Format total to MM:SS.mmm
            let totalFormatted = "-";
            if (lap.totalLapTimeInMS > 0) {
                const mins = Math.floor(lap.totalLapTimeInMS / 60000);
                const secs = ((lap.totalLapTimeInMS % 60000) / 1000).toFixed(3);
                totalFormatted = mins > 0 ? `${mins}:${secs.padStart(6, '0')}` : secs;
            }

            tr.innerHTML = `
                <td>${lap.lapNumber}</td>
                <td>${s1}</td>
                <td>${s2}</td>
                <td>${s3}</td>
                <td><strong>${totalFormatted}</strong></td>
                <td><button onclick="fetchTelemetry('${sessionId}', ${lap.lapNumber})" style="padding: 3px 8px; background:var(--primary-color); border:none; border-radius:3px; cursor:pointer;">View Telemetry</button></td>
            `;
            tbody.appendChild(tr);
        });
    } catch (e) {
        console.error(e);
        tbody.innerHTML = '<tr><td colspan="6">Error loading laps.</td></tr>';
    }
}

let telemetryChartInstance = null;

async function fetchTelemetry(sessionId, lapNumber) {
    const token = localStorage.getItem('jwtToken');
    try {
        const res = await fetch(`/api/history/sessions/${sessionId}/laps/${lapNumber}/telemetry`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (!res.ok) throw new Error("Failed to load telemetry");
        
        const telemetryData = await res.json();
        if (telemetryData.length === 0) {
            alert("No telemetry data recorded for this lap.");
            return;
        }
        
        renderChart(telemetryData, lapNumber);
    } catch (e) {
        console.error(e);
        alert("Error loading telemetry.");
    }
}

function renderChart(data, lapNumber) {
    document.getElementById('telemetry-chart-container').style.display = 'block';
    document.getElementById('telemetry-chart-title').textContent = `Telemetry - Lap ${lapNumber}`;
    
    const ctx = document.getElementById('telemetryChart').getContext('2d');
    
    if (telemetryChartInstance) {
        telemetryChartInstance.destroy();
    }
    
    // Use relative time in seconds for the x-axis
    const startTime = data[0].timestamp;
    const labels = data.map(d => ((d.timestamp - startTime) / 1000).toFixed(1));
    
    const speedData = data.map(d => d.speed);
    const throttleData = data.map(d => d.throttle * 100);
    const brakeData = data.map(d => d.brake * 100);

    telemetryChartInstance = new Chart(ctx, {
        type: 'line',
        data: {
            labels: labels,
            datasets: [
                {
                    label: 'Speed (km/h)',
                    data: speedData,
                    borderColor: '#ff0055',
                    backgroundColor: 'rgba(255, 0, 85, 0.1)',
                    yAxisID: 'ySpeed',
                    tension: 0.1,
                    pointRadius: 0
                },
                {
                    label: 'Throttle (%)',
                    data: throttleData,
                    borderColor: '#00ff00',
                    backgroundColor: 'rgba(0, 255, 0, 0.1)',
                    yAxisID: 'yInputs',
                    tension: 0.1,
                    pointRadius: 0
                },
                {
                    label: 'Brake (%)',
                    data: brakeData,
                    borderColor: '#0088ff',
                    backgroundColor: 'rgba(0, 136, 255, 0.1)',
                    yAxisID: 'yInputs',
                    tension: 0.1,
                    pointRadius: 0
                }
            ]
        },
        options: {
            responsive: true,
            interaction: {
                mode: 'index',
                intersect: false,
            },
            scales: {
                x: {
                    title: { display: true, text: 'Time (s)', color: '#fff' },
                    ticks: { color: '#aaa', maxTicksLimit: 20 }
                },
                ySpeed: {
                    type: 'linear',
                    display: true,
                    position: 'left',
                    title: { display: true, text: 'Speed', color: '#ff0055' },
                    ticks: { color: '#ff0055' },
                    suggestedMin: 0,
                    suggestedMax: 350
                },
                yInputs: {
                    type: 'linear',
                    display: true,
                    position: 'right',
                    title: { display: true, text: 'Inputs %', color: '#00ff00' },
                    ticks: { color: '#aaa' },
                    suggestedMin: 0,
                    suggestedMax: 100,
                    grid: { drawOnChartArea: false }
                }
            },
            plugins: {
                legend: { labels: { color: '#fff' } }
            }
        }
    });
}
