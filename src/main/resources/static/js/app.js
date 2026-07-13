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
let currentVoiceHotkey = 70;
let currentVoiceHotkeyLabel = "Scroll Lock";
let browserHotkeyListener = null;
window.aiEnabled = false;

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

            // Fetch preferences on startup to initialize window.aiEnabled
            loadPreferences();
        }
        
        stompClient.subscribe('/topic/live-telemetry', function (message) {
            const data = JSON.parse(message.body);
            updateDashboard(data);
        });

        stompClient.subscribe('/topic/live-alerts', function (message) {
            const alertData = JSON.parse(message.body);

            // Phase 10: Handle AI-specific alert types before the generic toast
            if (alertData.type === 'LAP_DEBRIEF') {
                if (window.aiEnabled) {
                    handleLapDebrief(alertData);
                }
                return; // LAP_DEBRIEF renders its own rich UI
            }
            if (alertData.type === 'VOICE_TRIGGER') {
                if (window.aiEnabled) {
                    activateVoiceQuery();
                }
                return; // Voice trigger — no toast needed
            }
            if (alertData.type === 'HOTKEY_BOUND') {
                handleHotkeyBound(alertData);
                return;
            }
            if (alertData.type === 'SECTOR_DELTA') {
                showToast(alertData);
                if (window.aiEnabled && typeof engineerSpeak === 'function') {
                    engineerSpeak(alertData.message, alertData.severity === 'SUCCESS');
                }
                return;
            }

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
        'BRAKE_OVERHEAT': '🛑 Brakes',
        'LOW_FUEL':       '⛽ Fuel',
        'LOW_BATTERY':    '🔋 Battery',
        'DAMAGE':         '💥 Damage',
        'LAP_DEBRIEF':    '🏎️ Engineer',
        'SECTOR_DELTA':   '⏱️ Sector Check',
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

    // Update leaderboard standings and progress timeline
    updateLeaderboardAndTimeline(data, playerIdx);

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

// Update live standings leaderboard and track progress timeline
function updateLeaderboardAndTimeline(data, playerIdx) {
    const markersContainer = document.getElementById('timeline-markers-container');
    const rowsContainer = document.getElementById('leaderboard-rows');
    const summaryText = document.getElementById('leaderboard-summary');
    
    if (!markersContainer || !rowsContainer) return;
    
    // Safety car state string
    let safetyCarState = data.safetyCarStatus;
    let safetyCarText = "OFF";
    if (safetyCarState === 1 || safetyCarState === 2) safetyCarText = "VSC";
    if (safetyCarState === 3) safetyCarText = "FULL SC";
    
    let trackLength = data.trackLength;
    if (!trackLength || trackLength <= 0) {
        trackLength = 6000; // default F1 track length fallback
    }
    
    // Filter active cars (position between 1 and 22)
    const activeCars = data.cars.filter(car => car.position > 0 && car.position <= 22);
    
    if (summaryText) {
        summaryText.textContent = `Active Cars: ${activeCars.length} | Safety Car: ${safetyCarText}`;
    }
    
    // 1. Render Track Timeline Markers
    markersContainer.innerHTML = '';
    
    activeCars.forEach(car => {
        let distance = Math.max(0, Math.min(trackLength, car.lapDistance));
        let pct = (distance / trackLength) * 100;
        
        const marker = document.createElement('div');
        marker.className = 'car-marker';
        if (car.carIndex === playerIdx) {
            marker.classList.add('player');
        }
        marker.style.left = `${pct}%`;
        marker.textContent = car.position;
        marker.title = `Car #${car.carIndex} (P${car.position}) - ${Math.round(car.lapDistance)}m`;
        
        markersContainer.appendChild(marker);
    });
    
    // 2. Render Standings Table Rows
    activeCars.sort((a, b) => a.position - b.position);
    
    if (activeCars.length === 0) {
        rowsContainer.innerHTML = `<tr><td colspan="8" style="padding:20px; text-align:center; color:var(--text-dim);">Waiting for live standings...</td></tr>`;
        return;
    }
    
    rowsContainer.innerHTML = '';
    activeCars.forEach(car => {
        const isPlayer = car.carIndex === playerIdx;
        const row = document.createElement('tr');
        if (isPlayer) {
            row.classList.add('player-row');
        }
        
        const lastLapTime = formatLapTime(car.lastLapTimeInMS);
        const tyreComp = getCompoundName(car.visualTyreCompound);
        const tyreBadge = `<span style="background:${tyreComp.bg}; color:${tyreComp.color}; padding:2px 8px; border-radius:4px; font-size:0.75rem; font-weight:800;">${tyreComp.text}</span>`;
        
        let avgWear = 0;
        if (car.tyreWear && car.tyreWear.length === 4) {
            avgWear = Math.round((car.tyreWear[0] + car.tyreWear[1] + car.tyreWear[2] + car.tyreWear[3]) / 4);
        }
        
        const wingL = car.frontLeftWingDamage;
        const wingR = car.frontRightWingDamage;
        const wingText = `<span style="color:${getDamageColor(wingL)};">${wingL}%</span> / <span style="color:${getDamageColor(wingR)};">${wingR}%</span>`;
        
        row.innerHTML = `
            <td style="padding:6px 12px; font-weight:700;">P${car.position}</td>
            <td style="padding:6px 12px; font-weight:600;">${isPlayer ? 'YOU' : 'Car #' + car.carIndex}</td>
            <td style="padding:6px 12px;">L${car.currentLapNum}</td>
            <td style="padding:6px 12px;">${car.speed} km/h</td>
            <td style="padding:6px 12px; font-family:var(--font-main);">${lastLapTime}</td>
            <td style="padding:6px 12px;">${tyreBadge}</td>
            <td style="padding:6px 12px;">${avgWear}%</td>
            <td style="padding:6px 12px;">${wingText}</td>
        `;
        
        rowsContainer.appendChild(row);
    });
}

// Start connection when page loads
window.onload = function() {
    connect();
    loadPreferences();
};

// Load and save user preference functions
function toggleHotkeyGroupVisibility(visible) {
    const hotkeyGroup = document.getElementById('settings-hotkey-group');
    if (hotkeyGroup) {
        hotkeyGroup.style.display = visible ? 'block' : 'none';
    }
}

// Load and save user preference functions
function loadPreferences() {
    const token = localStorage.getItem('jwtToken');
    if (!token) return;

    fetch('/api/preferences', {
        headers: { 'Authorization': `Bearer ${token}` }
    })
    .then(response => {
        if (!response.ok) throw new Error("Failed to load preferences");
        return response.json();
    })
    .then(data => {
        const tireField = document.getElementById('pref-tire-temp');
        const brakeField = document.getElementById('pref-brake-temp');
        const fuelField = document.getElementById('pref-fuel-level');
        const batteryField = document.getElementById('pref-ers-battery');
        const hotkeyDisplay = document.getElementById('hotkey-display');
        const aiToggle = document.getElementById('pref-ai-enabled');

        if (tireField) tireField.value = Math.round(data.tireOverheatTemp);
        if (brakeField) brakeField.value = Math.round(data.brakeOverheatTemp);
        if (fuelField) fuelField.value = data.criticalFuelDelta;
        if (batteryField) batteryField.value = Math.round(data.lowBatteryPercentage);
        if (data.voiceHotkey !== undefined && data.voiceHotkey !== null) {
            currentVoiceHotkey = data.voiceHotkey;
        }
        if (data.voiceHotkeyLabel) {
            currentVoiceHotkeyLabel = data.voiceHotkeyLabel;
        }
        if (hotkeyDisplay) {
            hotkeyDisplay.textContent = currentVoiceHotkeyLabel;
        }
        
        let aiEnabled = data.aiEnabled === true;
        if (aiToggle) {
            aiToggle.checked = aiEnabled;
            toggleHotkeyGroupVisibility(aiEnabled);
            aiToggle.onchange = (e) => {
                toggleHotkeyGroupVisibility(e.target.checked);
            };
        }
        window.aiEnabled = aiEnabled;
    })
    .catch(error => {
        console.error("Error loading preferences:", error);
    });
}

function savePreferences(event) {
    if (event) event.preventDefault();
    const token = localStorage.getItem('jwtToken');
    if (!token) return;

    const payload = {
        tireOverheatTemp: parseFloat(document.getElementById('pref-tire-temp').value),
        brakeOverheatTemp: parseFloat(document.getElementById('pref-brake-temp').value),
        criticalFuelDelta: parseFloat(document.getElementById('pref-fuel-level').value),
        lowBatteryPercentage: parseFloat(document.getElementById('pref-ers-battery').value),
        voiceHotkey: currentVoiceHotkey,
        voiceHotkeyLabel: currentVoiceHotkeyLabel,
        aiEnabled: document.getElementById('pref-ai-enabled').checked
    };

    fetch('/api/preferences', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify(payload)
    })
    .then(response => {
        if (!response.ok) throw new Error("Failed to save preferences");
        return response.json();
    })
    .then(data => {
        window.aiEnabled = data.aiEnabled === true;
        showToast({
            type: 'SUCCESS',
            severity: 'SUCCESS',
            message: 'Engineer preferences saved successfully!'
        });
    })
    .catch(error => {
        console.error("Error saving preferences:", error);
        showToast({
            type: 'ERROR',
            severity: 'CRITICAL',
            message: 'Failed to save engineer preferences.'
        });
    });
}

// JS to JNativeHook Key Code Mapping (US Keyboard Layout/Standard scan codes)
const JS_TO_NATIVE_KEY_MAP = {
    // Navigation & Editing
    "End": { code: 3663, label: "End" },
    "Home": { code: 3655, label: "Home" },
    "PageUp": { code: 3657, label: "Page Up" },
    "PageDown": { code: 3665, label: "Page Down" },
    "Insert": { code: 3666, label: "Insert" },
    "Delete": { code: 3667, label: "Delete" },
    "ArrowUp": { code: 57416, label: "Up" },
    "ArrowDown": { code: 57424, label: "Down" },
    "ArrowLeft": { code: 57419, label: "Left" },
    "ArrowRight": { code: 57421, label: "Right" },
    
    // Control & Special
    "ScrollLock": { code: 70, label: "Scroll Lock" },
    "Space": { code: 57, label: "Space" },
    " ": { code: 57, label: "Space" },
    "Enter": { code: 28, label: "Enter" },
    "Escape": { code: 1, label: "Escape" },
    "Backspace": { code: 14, label: "Backspace" },
    "Tab": { code: 15, label: "Tab" },
    "CapsLock": { code: 58, label: "Caps Lock" },
    
    // Function keys
    "F1": { code: 59, label: "F1" },
    "F2": { code: 60, label: "F2" },
    "F3": { code: 61, label: "F3" },
    "F4": { code: 62, label: "F4" },
    "F5": { code: 63, label: "F5" },
    "F6": { code: 64, label: "F6" },
    "F7": { code: 65, label: "F7" },
    "F8": { code: 66, label: "F8" },
    "F9": { code: 67, label: "F9" },
    "F10": { code: 68, label: "F10" },
    "F11": { code: 87, label: "F11" },
    "F12": { code: 88, label: "F12" },

    // Alphabet
    "KeyA": { code: 30, label: "A" },
    "KeyB": { code: 48, label: "B" },
    "KeyC": { code: 46, label: "C" },
    "KeyD": { code: 32, label: "D" },
    "KeyE": { code: 18, label: "E" },
    "KeyF": { code: 33, label: "F" },
    "KeyG": { code: 34, label: "G" },
    "KeyH": { code: 35, label: "H" },
    "KeyI": { code: 23, label: "I" },
    "KeyJ": { code: 36, label: "J" },
    "KeyK": { code: 37, label: "K" },
    "KeyL": { code: 38, label: "L" },
    "KeyM": { code: 50, label: "M" },
    "KeyN": { code: 49, label: "N" },
    "KeyO": { code: 24, label: "O" },
    "KeyP": { code: 25, label: "P" },
    "KeyQ": { code: 16, label: "Q" },
    "KeyR": { code: 19, label: "R" },
    "KeyS": { code: 31, label: "S" },
    "KeyT": { code: 20, label: "T" },
    "KeyU": { code: 22, label: "U" },
    "KeyV": { code: 47, label: "V" },
    "KeyW": { code: 17, label: "W" },
    "KeyX": { code: 45, label: "X" },
    "KeyY": { code: 21, label: "Y" },
    "KeyZ": { code: 44, label: "Z" },

    // Digits
    "Digit1": { code: 2, label: "1" },
    "Digit2": { code: 3, label: "2" },
    "Digit3": { code: 4, label: "3" },
    "Digit4": { code: 5, label: "4" },
    "Digit5": { code: 6, label: "5" },
    "Digit6": { code: 7, label: "6" },
    "Digit7": { code: 8, label: "7" },
    "Digit8": { code: 9, label: "8" },
    "Digit9": { code: 10, label: "9" },
    "Digit0": { code: 11, label: "0" },
};

// Phase 10: Customizable Hotkey bindings
function startHotkeyBinding() {
    const btn = document.getElementById('btn-bind-hotkey');
    const display = document.getElementById('hotkey-display');
    const token = localStorage.getItem('jwtToken');

    if (!btn || !token) return;

    btn.textContent = 'Press any key...';
    btn.disabled = true;
    if (display) display.textContent = '⏱️ Waiting...';

    // Remove any pre-existing browser listener
    if (browserHotkeyListener) {
        window.removeEventListener('keydown', browserHotkeyListener, { capture: true });
    }

    // Register browser keydown fallback handler
    browserHotkeyListener = function(event) {
        event.preventDefault();
        event.stopPropagation();

        // Find key translation matching code or key
        const match = JS_TO_NATIVE_KEY_MAP[event.code] || JS_TO_NATIVE_KEY_MAP[event.key];
        const keyCode = match ? match.code : 0;
        const keyLabel = match ? match.label : event.key;

        // If it's a key we can bind (code must be non-zero)
        if (keyCode > 0) {
            // Clean up listener
            window.removeEventListener('keydown', browserHotkeyListener, { capture: true });
            browserHotkeyListener = null;

            // Submit keypress to backend
            fetch('/api/ai/hotkey/bind-keypress', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${token}`
                },
                body: JSON.stringify({ keyCode: keyCode, keyLabel: keyLabel })
            })
            .catch(err => {
                console.error("Error saving browser hotkey", err);
                if (btn) btn.textContent = 'Change Hotkey';
                if (btn) btn.disabled = false;
                if (display) display.textContent = currentVoiceHotkeyLabel;
            });
        } else {
            console.warn("Unbindable key pressed in browser:", event.key);
        }
    };

    window.addEventListener('keydown', browserHotkeyListener, { capture: true });

    // Tell the backend to enter bind mode (in case global hook is also active)
    fetch('/api/ai/hotkey/bind', {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${token}` }
    })
    .catch(e => {
        console.error("Failed to start backend hotkey binding", e);
    });
}

function handleHotkeyBound(alertData) {
    const keyLabel = alertData.message || 'Scroll Lock';
    const display = document.getElementById('hotkey-display');
    const btn = document.getElementById('btn-bind-hotkey');

    // Clean up browser listener if it's still running
    if (browserHotkeyListener) {
        window.removeEventListener('keydown', browserHotkeyListener, { capture: true });
        browserHotkeyListener = null;
    }

    if (display) display.textContent = keyLabel;
    if (btn) {
        btn.textContent = 'Change Hotkey';
        btn.disabled = false;
    }

    // Keep our local state variables in sync!
    currentVoiceHotkeyLabel = keyLabel;
    const match = Object.values(JS_TO_NATIVE_KEY_MAP).find(v => v.label === keyLabel);
    if (match) {
        currentVoiceHotkey = match.code;
    }

    showToast({
        type: 'SUCCESS',
        severity: 'SUCCESS',
        message: `Radio hotkey bound to: ${keyLabel}`
    });
}

// Register browser keydown listener to trigger voice query fallback
window.addEventListener('keydown', function(event) {
    // If the browser is currently in bind mode, let the bind listener handle it
    if (browserHotkeyListener) return;

    // Ignore keypresses if the user is typing in an input or textarea
    if (document.activeElement && (document.activeElement.tagName === 'INPUT' || document.activeElement.tagName === 'TEXTAREA')) {
        return;
    }

    // Check if the pressed key matches the bound hotkey
    const match = JS_TO_NATIVE_KEY_MAP[event.code] || JS_TO_NATIVE_KEY_MAP[event.key];
    const keyCode = match ? match.code : 0;

    if (keyCode === currentVoiceHotkey) {
        event.preventDefault();
        event.stopPropagation();
        
        // Trigger voice query activation
        if (typeof activateVoiceQuery === 'function') {
            activateVoiceQuery();
        }
    }
});
