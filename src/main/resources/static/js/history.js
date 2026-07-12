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
            li.style.display = 'flex';
            li.style.justifyContent = 'space-between';
            li.style.alignItems = 'center';
            li.style.paddingRight = '16px';
            
            const date = new Date(session.timestamp).toLocaleString();
            const typeLabel = session.sessionType || 'Session';
            
            const infoDiv = document.createElement('div');
            infoDiv.style.flex = '1';
            infoDiv.style.cursor = 'pointer';
            infoDiv.innerHTML = `
                <strong>${session.trackName}</strong>
                <span style="margin-left:8px; padding:2px 7px; border-radius:3px; font-size:0.75rem; background:rgba(0,176,255,0.15); color:var(--accent-blue); font-weight:600;">${typeLabel}</span>
                <br/><small style="color:var(--text-dim)">${date}</small>
            `;
            infoDiv.onclick = () => loadLapDetails(session.sessionId, session.trackName + ' · ' + typeLabel);
            
            const deleteBtn = document.createElement('button');
            deleteBtn.innerHTML = '🗑️';
            deleteBtn.style.background = 'transparent';
            deleteBtn.style.border = 'none';
            deleteBtn.style.cursor = 'pointer';
            deleteBtn.style.padding = '8px';
            deleteBtn.style.fontSize = '1.1rem';
            deleteBtn.style.transition = 'transform 0.2s';
            deleteBtn.title = "Delete Session";
            
            deleteBtn.onmouseenter = () => deleteBtn.style.transform = 'scale(1.2)';
            deleteBtn.onmouseleave = () => deleteBtn.style.transform = 'scale(1.0)';
            
            deleteBtn.onclick = (e) => {
                e.stopPropagation();
                if (confirm(`Are you sure you want to delete the session at ${session.trackName} (${typeLabel})?`)) {
                    deleteSession(session.sessionId);
                }
            };
            
            // Phase 10: AI Debrief button
            const aiDebriefBtn = document.createElement('button');
            aiDebriefBtn.className = 'btn-ai-debrief';
            aiDebriefBtn.innerHTML = '🤖 AI Debrief';
            aiDebriefBtn.title = 'Get an AI-powered post-session analysis';
            aiDebriefBtn.style.marginRight = '6px';
            aiDebriefBtn.onclick = (e) => {
                e.stopPropagation();
                if (typeof showSessionDebrief === 'function') {
                    showSessionDebrief(session.sessionId);
                }
            };

            // Phase 10: Chat button
            const chatBtn = document.createElement('button');
            chatBtn.className = 'btn-ai-chat';
            chatBtn.innerHTML = '💬 Chat';
            chatBtn.title = 'Ask your AI engineer about this session';
            chatBtn.style.marginRight = '8px';
            chatBtn.onclick = (e) => {
                e.stopPropagation();
                if (typeof openChatPanel === 'function') {
                    openChatPanel(session.sessionId, session.trackName, typeLabel);
                }
            };

            li.appendChild(infoDiv);
            li.appendChild(aiDebriefBtn);
            li.appendChild(chatBtn);
            li.appendChild(deleteBtn);
            list.appendChild(li);
        });

    } catch (e) {
        console.error(e);
        list.innerHTML = '<li>Error loading sessions.</li>';
    }
}

async function deleteSession(sessionId) {
    const token = localStorage.getItem('jwtToken');
    try {
        const res = await fetch(`/api/history/sessions/${sessionId}`, {
            method: 'DELETE',
            headers: {
                'Authorization': `Bearer ${token}`
            }
        });
        if (res.status === 401 || res.status === 403) {
            logout();
            return;
        }
        if (!res.ok) throw new Error("Failed to delete session");
        
        loadSessions();
        
        // Reset lap details view if details for this session were currently loaded
        const lapTitle = document.getElementById('lap-details-title');
        if (lapTitle && lapTitle.textContent.includes(sessionId)) {
            lapTitle.textContent = 'Select a session';
            document.getElementById('lap-table-body').innerHTML = '<tr><td colspan="5">Select a session to view lap details.</td></tr>';
            document.getElementById('telemetry-chart-container').style.display = 'none';
        }
    } catch (e) {
        console.error(e);
        alert("Failed to delete session: " + e.message);
    }
}

async function loadLapDetails(sessionId, trackName) {
    document.getElementById('lap-details-title').textContent = `Laps for ${trackName}`;
    const tbody = document.getElementById('lap-table-body');
    tbody.innerHTML = '<tr><td colspan="7">Loading...</td></tr>';
    
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
            tbody.innerHTML = '<tr><td colspan="7">No laps completed yet for this session.</td></tr>';
            return;
        }

        // Extract unique, sorted lists of valid (>0) times for each column
        const s1Times = [...new Set(laps.map(l => l.sector1TimeInMS).filter(t => t > 0))].sort((a, b) => a - b);
        const s2Times = [...new Set(laps.map(l => l.sector2TimeInMS).filter(t => t > 0))].sort((a, b) => a - b);
        const s3Times = [...new Set(laps.map(l => l.sector3TimeInMS).filter(t => t > 0))].sort((a, b) => a - b);
        const totalTimes = [...new Set(laps.map(l => l.totalLapTimeInMS).filter(t => t > 0))].sort((a, b) => a - b);

        function getHighlightStyle(time, sortedTimes) {
            if (!time || time <= 0 || sortedTimes.length === 0) return "";
            if (time === sortedTimes[0]) {
                // Fastest: Purple
                return "background-color: rgba(124, 58, 237, 0.12); color: #7c3aed; font-weight: 700; border-radius: 4px; padding: 2px 8px; display: inline-block;";
            } else if (sortedTimes.length > 1 && time === sortedTimes[1]) {
                // Second fastest: Green
                return "background-color: rgba(21, 128, 61, 0.12); color: #15803d; font-weight: 700; border-radius: 4px; padding: 2px 8px; display: inline-block;";
            } else {
                // Remaining: Yellow
                return "background-color: rgba(217, 119, 6, 0.12); color: #b45309; font-weight: 700; border-radius: 4px; padding: 2px 8px; display: inline-block;";
            }
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

            const s1Style = getHighlightStyle(lap.sector1TimeInMS, s1Times);
            const s2Style = getHighlightStyle(lap.sector2TimeInMS, s2Times);
            const s3Style = getHighlightStyle(lap.sector3TimeInMS, s3Times);
            const totalStyle = getHighlightStyle(lap.totalLapTimeInMS, totalTimes);

            const s1Span = s1Style ? `<span style="${s1Style}">${s1}</span>` : s1;
            const s2Span = s2Style ? `<span style="${s2Style}">${s2}</span>` : s2;
            const s3Span = s3Style ? `<span style="${s3Style}">${s3}</span>` : s3;
            const totalSpan = totalStyle ? `<span style="${totalStyle}"><strong>${totalFormatted}</strong></span>` : `<strong>${totalFormatted}</strong>`;

            tr.innerHTML = `
                <td style="text-align:center;">
                    <input type="checkbox" class="lap-compare-checkbox" 
                           data-session="${sessionId}" 
                           data-lap="${lap.lapNumber}" 
                           data-track="${lap.raceSession ? lap.raceSession.trackName : 'Track'}" 
                           style="width: 16px; height: 16px; cursor: pointer;" 
                           onchange="onLapCompareChange()"/>
                </td>
                <td>${lap.lapNumber}</td>
                <td>${s1Span}</td>
                <td>${s2Span}</td>
                <td>${s3Span}</td>
                <td>${totalSpan}</td>
                <td><button class="telemetry-btn" onclick="viewSingleLap('${sessionId}', ${lap.lapNumber})">View Telemetry</button></td>
            `;
            tbody.appendChild(tr);
        });
    } catch (e) {
        console.error(e);
        tbody.innerHTML = '<tr><td colspan="7">Error loading laps.</td></tr>';
    }
}

function viewSingleLap(sessionId, lapNumber) {
    const checkboxes = document.querySelectorAll('.lap-compare-checkbox');
    checkboxes.forEach(cb => {
        const isTarget = cb.getAttribute('data-session') === sessionId && parseInt(cb.getAttribute('data-lap')) === lapNumber;
        cb.checked = isTarget;
    });
    onLapCompareChange();
}

async function onLapCompareChange() {
    const checkboxes = document.querySelectorAll('.lap-compare-checkbox:checked');
    const chartContainer = document.getElementById('telemetry-chart-container');
    
    if (checkboxes.length === 0) {
        chartContainer.style.display = 'none';
        return;
    }
    
    chartContainer.style.display = 'block';
    
    const token = localStorage.getItem('jwtToken');
    const promises = Array.from(checkboxes).map(async (cb) => {
        const sessionId = cb.getAttribute('data-session');
        const lapNumber = parseInt(cb.getAttribute('data-lap'));
        const trackName = cb.getAttribute('data-track');
        
        const res = await fetch(`/api/history/sessions/${sessionId}/laps/${lapNumber}/telemetry`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (!res.ok) throw new Error(`Failed to load telemetry for Lap ${lapNumber}`);
        const telemetry = await res.json();
        
        return {
            lapNum: lapNumber,
            trackName: trackName,
            telemetry: telemetry
        };
    });
    
    try {
        const lapsData = await Promise.all(promises);
        
        const validLaps = lapsData.filter(ld => ld.telemetry && ld.telemetry.length > 0);
        if (validLaps.length === 0) {
            document.getElementById('telemetry-chart-title').textContent = "No Telemetry Available";
            return;
        }
        
        const lapLabels = validLaps.map(ld => `Lap ${ld.lapNum}`).join(' vs ');
        document.getElementById('telemetry-chart-title').textContent = `Telemetry Comparison: ${lapLabels}`;
        
        if (window.TelemetryChart) {
            window.TelemetryChart.renderComparisonChart('telemetryChart', validLaps);
        }
    } catch (e) {
        console.error(e);
        alert("Error loading comparison telemetry: " + e.message);
    }
}
