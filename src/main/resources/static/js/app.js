// DOM Elements
const elements = {
    status: document.getElementById('connection-status'),
    
    // Inputs
    throttleBar: document.getElementById('throttle-bar'),
    throttleVal: document.getElementById('throttle-val'),
    brakeBar: document.getElementById('brake-bar'),
    brakeVal: document.getElementById('brake-val'),
    steerMarker: document.getElementById('steer-marker'),
    steerVal: document.getElementById('steer-val'),
    
    // Core
    rpmVal: document.getElementById('rpm-val'),
    revBar: document.getElementById('rev-bar'),
    speedVal: document.getElementById('speed-val'),
    gearVal: document.getElementById('gear-val'),
    
    // Stats
    lapNum: document.getElementById('lap-num'),
    position: document.getElementById('position'),
    fuel: document.getElementById('fuel'),
    ers: document.getElementById('ers'),
    lapTime: document.getElementById('lap-time'),
    tyreCompound: document.getElementById('tyre-compound'),
    
    // Tyres
    tempFL: document.getElementById('temp-fl'),
    tempFR: document.getElementById('temp-fr'),
    tempRL: document.getElementById('temp-rl'),
    tempRR: document.getElementById('temp-rr'),
    
    wearFL: document.getElementById('wear-fl'),
    wearFR: document.getElementById('wear-fr'),
    wearRL: document.getElementById('wear-rl'),
    wearRR: document.getElementById('wear-rr'),

    // Wings
    wingLFill: document.getElementById('wing-l-fill'),
    wingLVal: document.getElementById('wing-l-val'),
    wingRFill: document.getElementById('wing-r-fill'),
    wingRVal: document.getElementById('wing-r-val')
};

// State handling
let stompClient = null;
let isConnecting = false;
const MAX_RPM = 13500; // Approx max RPM for F1

function connect() {
    if (stompClient && (stompClient.connected || (stompClient.ws && (stompClient.ws.readyState === 1 || stompClient.ws.readyState === 0)))) {
        console.log("WebSocket already active or connecting, skipping duplicate connect()");
        return;
    }
    if (isConnecting) {
        return;
    }
    isConnecting = true;
    const socket = new SockJS('/telemetry-websocket');
    stompClient = Stomp.over(socket);
    
    // Disable debug logging to avoid console spam at 30Hz
    stompClient.debug = null; 
    
    stompClient.connect({}, function (frame) {
        setConnected(true);
        isConnecting = false;
        console.log('Connected: ' + frame);

        // Auto-register session start on connect/reconnect so active player status is never lost
        const token = localStorage.getItem('jwtToken');
        if (token) {
            fetch('/api/session/start', {
                method: 'POST',
                headers: { 'Authorization': `Bearer ${token}` }
            }).catch(e => console.error("Auto-session start error", e));
        }
        
        stompClient.subscribe('/topic/live-telemetry', function (message) {
            const data = JSON.parse(message.body);
            updateDashboard(data);
        });

        stompClient.subscribe('/topic/live-alerts', function (message) {
            const alertData = JSON.parse(message.body);
            showToast(alertData);
        });
    }, function(error) {
        setConnected(false);
        isConnecting = false;
        console.error("STOMP error", error);
        // Attempt to reconnect after 3 seconds
        setTimeout(connect, 3000);
    });
}

function disconnect() {
    if (stompClient !== null) {
        try {
            stompClient.disconnect();
        } catch (e) {
            console.error("Error during disconnect", e);
        }
        stompClient = null;
    }
    setConnected(false);
    console.log("Disconnected");
}

function showToast(alertData) {
    const severity = (alertData.severity || 'warning').toLowerCase();

    // Fastest lap gets the special cinematic banner treatment
    if (alertData.type === 'FASTEST_LAP' && alertData.detail) {
        showFastestLapBanner(alertData.detail, alertData.message);
    }

    // Always also log to the persistent event feed
    appendEventLog(alertData);

    // Build the toast
    const container = document.getElementById('toast-container');
    const toast = document.createElement('div');
    toast.className = `toast ${severity}`;

    const typeLabel = {
        'FASTEST_LAP':    '🏆 Fastest Lap',
        'SPEED_TRAP':     '⚡ Speed Trap',
        'PENALTY':        '⚠️ Penalty',
        'SESSION_START':  '🏁 Session Started',
        'SESSION_END':    '🏁 Session Ended',
        'CHEQUERED_FLAG': '🏁 Race End',
        'RED_FLAG':       '🚩 Red Flag',
        'FLASHBACK':      '⏪ Flashback',
        'DRS':            'DRS',
        'SAFETY_CAR':     '🟡 Safety Car',
        'TIRE_OVERHEAT':  '🔥 Tyres',
        'DAMAGE':         '💥 Damage',
    }[alertData.type] || alertData.type;

    toast.innerHTML = `
        <span class="toast-type">${typeLabel}</span>
        <span class="toast-msg">${alertData.message}</span>
        ${alertData.detail && alertData.type === 'SPEED_TRAP'
            ? `<span class="toast-detail">${alertData.detail} km/h</span>` : ''}
    `;

    container.appendChild(toast);

    // Variable dismiss: CRITICAL=8s, WARNING=6s, SUCCESS=5s, INFO=4s
    const dismissMs = { critical: 8000, warning: 6000, success: 5000, info: 4000 }[severity] ?? 5000;

    setTimeout(() => {
        toast.style.animation = 'slideOut 0.35s ease-in forwards';
        setTimeout(() => toast.remove(), 350);
    }, dismissMs);
}

let fastestLapBannerTimer = null;
function showFastestLapBanner(lapTime, fullMessage) {
    const banner = document.getElementById('fastest-lap-banner');
    const timeEl = document.getElementById('fastest-lap-time');

    if (!banner || !timeEl) return;

    // Clear any existing timer
    if (fastestLapBannerTimer) {
        clearTimeout(fastestLapBannerTimer);
        banner.classList.remove('hiding');
    }

    timeEl.textContent = lapTime;
    banner.style.display = 'flex';
    banner.classList.remove('hiding');

    // Auto-hide after 5 seconds
    fastestLapBannerTimer = setTimeout(() => {
        banner.classList.add('hiding');
        setTimeout(() => {
            banner.style.display = 'none';
            banner.classList.remove('hiding');
        }, 400);
        fastestLapBannerTimer = null;
    }, 5000);
}

const MAX_LOG_ENTRIES = 8;
function appendEventLog(alertData) {
    const container = document.getElementById('event-log-entries');
    if (!container) return;

    // Clear the "waiting" placeholder on first real event
    if (container.querySelector('[data-placeholder]')) {
        container.innerHTML = '';
    } else if (container.children.length === 1 && container.firstChild.textContent.includes('Waiting')) {
        container.innerHTML = '';
    }

    const severity = (alertData.severity || 'info').toLowerCase();
    const now = new Date();
    const timeStr = now.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', second: '2-digit' });

    const entry = document.createElement('div');
    entry.className = 'event-log-entry';
    entry.innerHTML = `
        <div class="event-log-dot ${severity}"></div>
        <div class="event-log-text">${alertData.message}</div>
        <div class="event-log-time">${timeStr}</div>
    `;

    // Prepend so newest is at top
    container.insertBefore(entry, container.firstChild);

    // Keep max 8 entries
    while (container.children.length > MAX_LOG_ENTRIES) {
        container.removeChild(container.lastChild);
    }
}

function setConnected(connected) {
    if (connected) {
        elements.status.textContent = 'CONNECTED (LIVE)';
        elements.status.className = 'status connected';
    } else {
        elements.status.textContent = 'DISCONNECTED';
        elements.status.className = 'status disconnected';
    }
}

function getGearString(gear) {
    if (gear === 0) return 'N';
    if (gear === -1) return 'R';
    return gear.toString();
}

function getWearColor(wearPercent) {
    if (wearPercent < 30) return 'var(--accent-green)';
    if (wearPercent < 60) return 'var(--accent-yellow)';
    return 'var(--accent-red)';
}

function formatLapTime(ms) {
    if (!ms || ms <= 0) return "--:--.---";
    const minutes = Math.floor(ms / 60000);
    const seconds = Math.floor((ms % 60000) / 1000);
    const millis = ms % 1000;
    return `${minutes}:${seconds.toString().padStart(2, '0')}.${millis.toString().padStart(3, '0')}`;
}

function getCompoundName(compoundId) {
    // F1 25 Compound IDs: 16 = Soft, 17 = Medium, 18 = Hard, 7 = Intermediate, 8 = Wet
    switch (compoundId) {
        case 16: return { text: 'SOFT', color: '#ff3366', bg: 'rgba(255, 51, 102, 0.15)' };
        case 17: return { text: 'MEDIUM', color: '#fbbf24', bg: 'rgba(251, 191, 36, 0.15)' };
        case 18: return { text: 'HARD', color: '#f8fafc', bg: 'rgba(255, 255, 255, 0.12)' };
        case 7:  return { text: 'INTER', color: '#10b981', bg: 'rgba(16, 185, 129, 0.15)' };
        case 8:  return { text: 'WET', color: '#3b82f6', bg: 'rgba(59, 130, 246, 0.15)' };
        default: return { text: 'DRY', color: '#94a3b8', bg: 'rgba(255, 255, 255, 0.05)' };
    }
}

function getDamageColor(damageVal) {
    if (damageVal < 10) return 'var(--accent-green)';
    if (damageVal < 40) return 'var(--accent-yellow)';
    return 'var(--accent-red)';
}

function updateDashboard(data) {
    if (!data || !data.cars) return;
    
    const playerIdx = data.playerCarIndex;
    const playerCar = data.cars[playerIdx];
    
    if (!playerCar) return;
    
    // 1. Update Core Telemetry
    // Speed
    elements.speedVal.textContent = playerCar.speed;
    
    // Gear
    elements.gearVal.textContent = getGearString(playerCar.gear);
    
    // RPM
    elements.rpmVal.textContent = playerCar.engineRPM.toString().padStart(5, '0');
    let revPercent = Math.min(100, (playerCar.engineRPM / MAX_RPM) * 100);
    elements.revBar.style.width = `${revPercent}%`;

    // 2. Update Inputs
    const throttlePercent = Math.round(playerCar.throttle * 100);
    elements.throttleBar.style.width = `${throttlePercent}%`;
    elements.throttleVal.textContent = `${throttlePercent}%`;

    const brakePercent = Math.round(playerCar.brake * 100);
    elements.brakeBar.style.width = `${brakePercent}%`;
    elements.brakeVal.textContent = `${brakePercent}%`;

    // Steer angle (from -1.0 left to 1.0 right)
    const steerPos = 50 + (playerCar.steer * 50); // Convert to 0% - 100%
    elements.steerMarker.style.left = `${steerPos}%`;
    elements.steerVal.textContent = `${Math.round(playerCar.steer * 90)}°`;

    // Rotate steering wheel HUD
    const wheelHUD = document.getElementById('steering-wheel-hud');
    if (wheelHUD) {
        wheelHUD.style.transform = `rotate(${Math.round(playerCar.steer * 120)}deg)`;
    }

    // Push coordinates to the live rolling waveform
    if (window.TelemetryChart) {
        window.TelemetryChart.pushLiveWaveformPoint(playerCar.speed, playerCar.throttle, playerCar.brake);
    }

    // 3. Update Race Status
    elements.lapNum.textContent = playerCar.currentLapNum;
    elements.position.textContent = `P${playerCar.position}`;
    elements.fuel.textContent = `${playerCar.fuelInTank.toFixed(1)}L`;
    
    // Assuming ERS max is ~4,000,000 Joules for F1 23/24/25
    const ersPercent = Math.min(100, (playerCar.ersStoreEnergy / 4000000) * 100);
    elements.ers.textContent = `${Math.round(ersPercent)}%`;

    // Running Lap Time
    elements.lapTime.textContent = formatLapTime(playerCar.currentLapTimeInMS);

    // Tyre Compound Badge
    const comp = getCompoundName(playerCar.visualTyreCompound);
    elements.tyreCompound.textContent = comp.text;
    elements.tyreCompound.style.color = comp.color;
    elements.tyreCompound.parentNode.style.borderTop = `2px solid ${comp.color}`;

    // 4. Update Tyres
    // Index map: 0 = RL, 1 = RR, 2 = FL, 3 = FR
    const temps = playerCar.tyreSurfaceTemps;
    const wear = playerCar.tyreWear;
    
    if (temps && temps.length === 4) {
        elements.tempRL.textContent = `${temps[0]}°C`;
        elements.tempRR.textContent = `${temps[1]}°C`;
        elements.tempFL.textContent = `${temps[2]}°C`;
        elements.tempFR.textContent = `${temps[3]}°C`;
    }
    
    if (wear && wear.length === 4) {
        elements.wearRL.style.width = `${wear[0]}%`;
        elements.wearRL.style.backgroundColor = getWearColor(wear[0]);
        
        elements.wearRR.style.width = `${wear[1]}%`;
        elements.wearRR.style.backgroundColor = getWearColor(wear[1]);
        
        elements.wearFL.style.width = `${wear[2]}%`;
        elements.wearFL.style.backgroundColor = getWearColor(wear[2]);
        
        elements.wearFR.style.width = `${wear[3]}%`;
        elements.wearFR.style.backgroundColor = getWearColor(wear[3]);
    }

    // 5. Update Wings
    const wingL = playerCar.frontLeftWingDamage;
    const wingR = playerCar.frontRightWingDamage;
    
    elements.wingLFill.style.width = `${wingL}%`;
    elements.wingLFill.style.backgroundColor = getDamageColor(wingL);
    elements.wingLVal.textContent = `${wingL}%`;
    elements.wingLVal.style.color = getDamageColor(wingL);

    elements.wingRFill.style.width = `${wingR}%`;
    elements.wingRFill.style.backgroundColor = getDamageColor(wingR);
    elements.wingRVal.textContent = `${wingR}%`;
    elements.wingRVal.style.color = getDamageColor(wingR);
}

// Start connection & waveform rendering loop when page loads
window.onload = function() {
    connect();
    if (window.TelemetryChart) {
        window.TelemetryChart.initLiveWaveform('liveWaveformCanvas');
    }
};
