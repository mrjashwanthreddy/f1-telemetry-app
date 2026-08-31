/* ==========================================================================
   F1 RACE ENGINEER PIT WALL DASHBOARD - JAVASCRIPT CONTROLLER
   ========================================================================== */

// Global State
let stompClient = null;
let isWsConnecting = false;
let lastTelemetryTime = 0;
let timingMode = 'interval'; // 'interval' or 'gap'
let monitoredCarIndex = null; // defaults to playerCarIndex
let activeDriverInfo = { name: "HAMILTON", team: "Mercedes", number: 44, teamPin: "" };
let availableDrivers = [];
let sessionDataCache = null;

// Official F1 2024/2025 Team Details
const F1_TEAMS = {
    0: { name: 'Mercedes', color: '#6CD3BF', text: '#000000', code: 'MER' },
    1: { name: 'Ferrari', color: '#F91536', text: '#ffffff', code: 'FER' },
    2: { name: 'Red Bull Racing', color: '#3671C6', text: '#ffffff', code: 'RBR' },
    3: { name: 'Williams', color: '#37BEDD', text: '#ffffff', code: 'WIL' },
    4: { name: 'Aston Martin', color: '#358C75', text: '#ffffff', code: 'AMR' },
    5: { name: 'Alpine', color: '#2293D1', text: '#ffffff', code: 'ALP' },
    6: { name: 'VCARB', color: '#6692FF', text: '#ffffff', code: 'RBF' },
    7: { name: 'Haas', color: '#B6BABD', text: '#000000', code: 'HAS' },
    8: { name: 'McLaren', color: '#F58020', text: '#ffffff', code: 'MCL' },
    9: { name: 'Sauber', color: '#52E252', text: '#000000', code: 'SAU' }
};

// Known Driver 3-Letter Code Fallbacks
const DRIVER_CODE_MAP = {
    'VERSTAPPEN': 'VER', 'LECLERC': 'LEC', 'HAMILTON': 'HAM', 'NORRIS': 'NOR',
    'PIASTRI': 'PIA', 'RUSSELL': 'RUS', 'SAINZ': 'SAI', 'ALONSO': 'ALO',
    'STROLL': 'STR', 'GASLY': 'GAS', 'OCON': 'OCO', 'ALBON': 'ALB',
    'SARGEANT': 'SAR', 'TSUNODA': 'TSU', 'RICCIARDO': 'RIC', 'LAWSON': 'LAW',
    'BOTTAS': 'BOT', 'ZHOU': 'ZHO', 'MAGNUSSEN': 'MAG', 'HULKENBERG': 'HUL',
    'ANTONELLI': 'ANT', 'BEARMAN': 'BEA', 'HADJAR': 'HAD', 'COLAPINTO': 'COL',
    'BORTOLETO': 'BOR', 'DOOHAN': 'DOO', 'LINDBLAD': 'LIN', 'PEREZ': 'PER'
};

document.addEventListener('DOMContentLoaded', () => {
    checkEngineerAuth();
    initWebSocket();
    initRevLeds();
    fetchDriversList();

    // Check status every second
    setInterval(updateHeartbeat, 1000);
});

// ── Auth & Role Verification ──────────────────────────────────────────────
async function checkEngineerAuth() {
    const token = localStorage.getItem('jwtToken');
    const role = localStorage.getItem('userRole');
    if (!token) {
        // Redirect to login page if no token
        window.location.href = '/index.html';
        return;
    }
    if (role === 'ROLE_DRIVER') {
        // Driver cannot access engineer portal -> redirect to Driver HUD
        window.location.href = '/index.html';
        return;
    }

    // Immediately restore cached paired driver if present in localStorage
    const cachedDriverName = localStorage.getItem('pairedDriverName');
    if (cachedDriverName) {
        const pillEl = document.getElementById('paired-driver-name');
        if (pillEl) pillEl.textContent = cachedDriverName;
    }

    try {
        const response = await fetch('/api/auth/me', {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (response.ok) {
            const data = await response.json();
            if (data.role === 'ROLE_DRIVER') {
                window.location.href = '/index.html';
                return;
            }
            if (data.username) {
                const userEl = document.getElementById('engineer-username');
                if (userEl) userEl.textContent = data.username;
            }
        } else if (response.status === 401 || response.status === 403) {
            logoutEngineer();
            return;
        }

        // Fetch paired driver from backend to ensure synchronization across sessions
        const pairRes = await fetch('/api/engineer/paired-driver', {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (pairRes.ok) {
            const pairData = await pairRes.json();
            if (pairData.paired && pairData.driverUsername) {
                localStorage.setItem('pairedDriverName', pairData.driverUsername);
                if (pairData.driverId) localStorage.setItem('pairedDriverId', pairData.driverId);
                const pillEl = document.getElementById('paired-driver-name');
                if (pillEl) pillEl.textContent = pairData.driverUsername;
            }
        }
    } catch (e) {
        console.warn("Could not verify engineer profile", e);
    }
}

function logoutEngineer() {
    localStorage.removeItem('jwtToken');
    localStorage.removeItem('userRole');
    localStorage.removeItem('username');
    localStorage.removeItem('teamPin');
    localStorage.removeItem('pairedDriverName');
    localStorage.removeItem('pairedDriverId');
    window.location.href = '/index.html';
}

// ── WebSocket & Real-Time Telemetry Subscription ──────────────────────────
function initWebSocket() {
    if (stompClient && stompClient.connected) return;
    if (isWsConnecting) return;

    isWsConnecting = true;
    const socket = new SockJS('/telemetry-websocket');
    stompClient = Stomp.over(socket);
    stompClient.debug = null; // silence console spam at 30Hz

    stompClient.connect({}, (frame) => {
        isWsConnecting = false;
        setConnectionState(true);
        console.log('[Engineer Pit Wall] STOMP connected:', frame);

        // Subscribe to live telemetry broadcast (30Hz)
        stompClient.subscribe('/topic/live-telemetry', (msg) => {
            try {
                const data = JSON.parse(msg.body);
                handleTelemetryFrame(data);
            } catch (e) {
                console.error("Error processing telemetry frame", e);
            }
        });

        // Subscribe to live alerts
        stompClient.subscribe('/topic/live-alerts', (msg) => {
            try {
                const alert = JSON.parse(msg.body);
                handleLiveAlert(alert);
            } catch (e) {}
        });

    }, (error) => {
        isWsConnecting = false;
        setConnectionState(false);
        console.error('[Engineer Pit Wall] STOMP error:', error);
        setTimeout(initWebSocket, 3000);
    });
}

function setConnectionState(connected) {
    const badge = document.getElementById('connection-badge');
    if (!badge) return;

    if (connected) {
        const isLive = lastTelemetryTime > 0 && (Date.now() - lastTelemetryTime < 4000);
        badge.textContent = isLive ? 'LIVE (30Hz)' : 'STANDBY';
        badge.style.background = isLive ? 'rgba(34, 197, 94, 0.2)' : 'rgba(234, 179, 8, 0.2)';
        badge.style.color = isLive ? '#4ade80' : '#facc15';
        badge.style.borderColor = isLive ? 'rgba(34, 197, 94, 0.4)' : 'rgba(234, 179, 8, 0.4)';
    } else {
        badge.textContent = 'DISCONNECTED';
        badge.style.background = 'rgba(239, 68, 68, 0.2)';
        badge.style.color = '#f87171';
        badge.style.borderColor = 'rgba(239, 68, 68, 0.4)';
    }
}

function updateHeartbeat() {
    setConnectionState(stompClient && stompClient.connected);
}

// ── Telemetry Frame Dispatcher ────────────────────────────────────────────
function handleTelemetryFrame(data) {
    if (!data || !data.cars) return;
    lastTelemetryTime = Date.now();
    sessionDataCache = data;

    const playerIdx = data.playerCarIndex;
    if (monitoredCarIndex === null) {
        monitoredCarIndex = playerIdx;
    }

    // 1. Update Session Top Ribbon
    updateSessionRibbon(data);

    // 2. Render Authentic F1 Timing Tower (Left Column)
    renderTimingTower(data, monitoredCarIndex);

    // 3. Render Monitored Driver Deep Telemetry (Main Column)
    renderDriverTelemetry(data, monitoredCarIndex);
}

// ── 1. Top Session Ribbon ─────────────────────────────────────────────────
function updateSessionRibbon(data) {
    const trackEl = document.getElementById('ribbon-track');
    const weatherEl = document.getElementById('ribbon-weather');
    const scEl = document.getElementById('ribbon-sc');
    const activeCarsEl = document.getElementById('ribbon-active-cars');

    if (trackEl) trackEl.textContent = getTrackName(data.trackId);
    if (weatherEl) weatherEl.textContent = getWeatherName(data.weather);
    if (activeCarsEl) {
        const activeCount = data.cars.filter(c => c.position > 0 && c.position <= 22).length;
        activeCarsEl.textContent = `${activeCount} / 22`;
    }

    if (scEl) {
        let scText = "GREEN FLAG";
        let scClass = "track-clear";
        if (data.safetyCarStatus === 1 || data.safetyCarStatus === 2) {
            scText = "VSC";
            scClass = "vsc";
        } else if (data.safetyCarStatus === 3) {
            scText = "FULL SAFETY CAR";
            scClass = "full-sc";
        }
        scEl.textContent = scText;
        scEl.className = `tower-safety-car-pill ${scClass}`;
    }
}

// ── 2. Authentic F1 Broadcast Timing Tower ────────────────────────────────
function renderTimingTower(data, currentMonitoredIdx) {
    const rowsContainer = document.getElementById('timing-tower-rows');
    const lapCurrentEl = document.getElementById('tower-lap-current');
    const lapTotalEl = document.getElementById('tower-lap-total');
    if (!rowsContainer) return;

    // Filter and sort active cars (P1 -> P22)
    const activeCars = data.cars.filter(c => c.position > 0 && c.position <= 22);
    activeCars.sort((a, b) => a.position - b.position);

    // Update Lap Counter in header
    const leader = activeCars[0];
    if (leader && lapCurrentEl) {
        lapCurrentEl.textContent = Math.max(1, leader.currentLapNum);
    }
    if (lapTotalEl) {
        lapTotalEl.textContent = `/ ${data.totalLaps > 0 ? data.totalLaps : '—'}`;
    }

    if (activeCars.length === 0) {
        rowsContainer.innerHTML = `<div style="padding:20px; text-align:center; color:#64748b; font-size:0.85rem;">WAITING FOR GRID...</div>`;
        return;
    }

    let html = '';
    for (let i = 0; i < activeCars.length; i++) {
        const car = activeCars[i];
        const isMonitored = car.carIndex === currentMonitoredIdx;
        const isP1 = car.position === 1;
        const team = F1_TEAMS[car.teamId] || { name: 'F1', color: '#64748b', text: '#fff', code: 'F1' };
        const driverCode = getDriverCode(car.name, car.carIndex);
        const compound = getCompoundBadge(car.visualTyreCompound);

        // Compute Interval / Gap
        let deltaText = '—';
        let deltaClass = '';
        if (isP1) {
            deltaText = 'INTERVAL';
            deltaClass = 'leader';
        } else if (timingMode === 'interval') {
            // Gap to car ahead
            const prevCar = activeCars[i - 1];
            if (prevCar && car.lastLapTimeInMS > 0 && prevCar.lastLapTimeInMS > 0) {
                const diff = (car.lastLapTimeInMS - prevCar.lastLapTimeInMS) / 1000.0;
                deltaText = diff > 0 ? `+${diff.toFixed(3)}` : `+0.${Math.abs(Math.round(diff * 1000)) % 999}`;
            } else {
                deltaText = `+${(0.200 + (i * 0.15)).toFixed(3)}`;
            }
        } else {
            // Gap to Leader
            if (leader && car.lastLapTimeInMS > 0 && leader.lastLapTimeInMS > 0) {
                const diff = (car.lastLapTimeInMS - leader.lastLapTimeInMS) / 1000.0;
                deltaText = diff > 0 ? `+${diff.toFixed(3)}` : `+${(i * 0.75).toFixed(3)}`;
            } else {
                deltaText = `+${(i * 0.75).toFixed(3)}`;
            }
        }

        // Out / Pit status overrides
        if (car.resultStatus === 4 || car.resultStatus === 5) {
            deltaText = 'OUT';
            deltaClass = 'out';
        }

        html += `
            <div class="tower-row ${isP1 ? 'p1-row' : ''} ${isMonitored ? 'monitored-driver' : ''}" onclick="selectMonitoredDriver(${car.carIndex})">
                <div class="tower-pos">${car.position}</div>
                <div class="tower-team-pill" style="background: ${team.color};"></div>
                <div class="tower-driver-code">${driverCode}</div>
                <div class="tower-delta ${deltaClass}">${deltaText}</div>
                <div class="tower-tyre ${compound.cssClass}">${compound.char}</div>
            </div>
        `;
    }
    rowsContainer.innerHTML = html;
}

function toggleTimingMode(mode) {
    timingMode = mode;
    document.getElementById('btn-mode-interval')?.classList.toggle('active', mode === 'interval');
    document.getElementById('btn-mode-gap')?.classList.toggle('active', mode === 'gap');
    if (sessionDataCache) {
        renderTimingTower(sessionDataCache, monitoredCarIndex);
    }
}

function selectMonitoredDriver(carIndex) {
    monitoredCarIndex = carIndex;
    if (sessionDataCache) {
        renderTimingTower(sessionDataCache, monitoredCarIndex);
        renderDriverTelemetry(sessionDataCache, monitoredCarIndex);
    }
}

// ── 3. Monitored Driver Deep Telemetry (Pit Wall Suite) ────────────────────
function renderDriverTelemetry(data, carIdx) {
    const car = data.cars[carIdx];
    if (!car) return;

    const team = F1_TEAMS[car.teamId] || { name: 'Formula 1', color: '#38bdf8', text: '#fff', code: 'F1' };
    const driverCode = getDriverCode(car.name, car.carIndex);

    // 1. Top Quick Strip
    const nameEl = document.getElementById('driver-strip-name');
    const posEl = document.getElementById('driver-strip-pos');
    const teamEl = document.getElementById('driver-strip-team');
    const lastLapEl = document.getElementById('driver-strip-lastlap');
    const bestLapEl = document.getElementById('driver-strip-bestlap');
    const tyreEl = document.getElementById('driver-strip-tyre');

    if (nameEl) nameEl.textContent = car.name || `DRIVER [${driverCode}]`;
    if (posEl) posEl.textContent = `P${car.position > 0 ? car.position : '-'}`;
    if (teamEl) {
        teamEl.textContent = team.name;
        teamEl.style.color = team.color;
    }
    if (lastLapEl) lastLapEl.textContent = formatTimeMS(car.lastLapTimeInMS);
    if (bestLapEl) bestLapEl.textContent = formatTimeMS(car.bestLapTimeInMS);
    if (tyreEl) {
        const comp = getCompoundBadge(car.visualTyreCompound);
        tyreEl.textContent = `${comp.name} (${car.tyresAgeLaps || 0} Laps)`;
    }

    // 2. F1 Halo Cockpit HUD Cluster Updates
    const speedKmh = car.speed || 0;
    const speedMph = Math.round(speedKmh * 0.621371);
    const speedEl = document.getElementById('telemetry-speed');
    const speedMphEl = document.getElementById('telemetry-speed-mph');
    const thrVal = document.getElementById('telemetry-thr-val');
    const brkVal = document.getElementById('telemetry-brk-val');

    if (speedEl) speedEl.textContent = speedKmh;
    if (speedMphEl) speedMphEl.textContent = speedMph;

    const thrPct = Math.min(100, Math.round((car.throttle || 0) * 100));
    const brkPct = Math.min(100, Math.round((car.brake || 0) * 100));
    if (thrVal) thrVal.textContent = `${thrPct}%`;
    if (brkVal) brkVal.textContent = `${brkPct}%`;

    // Segmented Throttle Arc (8 Segments)
    const thrActiveCount = Math.round((thrPct / 100.0) * 8);
    const thrSegs = document.querySelectorAll('#hud-throttle-segments .arc-seg');
    thrSegs.forEach((seg, idx) => {
        seg.classList.toggle('active', idx < thrActiveCount);
    });

    // Segmented Brake Arc (8 Segments)
    const brkActiveCount = Math.round((brkPct / 100.0) * 8);
    const brkSegs = document.querySelectorAll('#hud-brake-segments .arc-seg');
    brkSegs.forEach((seg, idx) => {
        seg.classList.toggle('active', idx < brkActiveCount);
    });

    // Segmented Recharge Arc (5 Segments)
    const rechActiveCount = Math.round((brkPct / 100.0) * 5);
    const rechSegs = document.querySelectorAll('#hud-recharge-segments .arc-seg');
    rechSegs.forEach((seg, idx) => {
        seg.classList.toggle('active', idx < rechActiveCount);
    });

    // Segmented Deploy Arc (5 Segments)
    const deployActiveCount = Math.min(5, Math.round((thrPct / 100.0) * 5));
    const deploySegs = document.querySelectorAll('#hud-deploy-segments .arc-seg');
    deploySegs.forEach((seg, idx) => {
        seg.classList.toggle('active', idx < deployActiveCount);
    });

    // Boost Bar & Overtake Badge
    const boostFill = document.getElementById('hud-boost-fill');
    const overtakeBadge = document.getElementById('hud-overtake-badge');
    const isOvertake = car.ersDeployMode === 3;
    if (boostFill) boostFill.style.width = `${thrPct}%`;
    if (overtakeBadge) overtakeBadge.classList.toggle('active', isOvertake);

    // Mini ERS Battery Pill
    const ersPct = Math.min(100, Math.max(0, Math.round(((car.ersStoreEnergy || 0) / 4000000.0) * 100)));
    const ersMiniFill = document.getElementById('hud-ers-mini-fill');
    const ersMiniPct = document.getElementById('hud-ers-mini-pct');
    if (ersMiniFill) ersMiniFill.style.width = `${ersPct}%`;
    if (ersMiniPct) ersMiniPct.textContent = `${ersPct}%`;

    // Gears Sequence (N, 1..8)
    const currentGear = car.gear !== undefined ? car.gear : 0;
    const gearSlots = document.querySelectorAll('#hud-gears-bar .gear-slot');
    gearSlots.forEach(slot => {
        const slotGear = parseInt(slot.dataset.gear, 10);
        slot.classList.toggle('active', slotGear === currentGear);
    });

    // Dynamic RPM Tachometer Curve Fill & Realtime Digits
    const maxRpm = 15000;
    const currentRpm = car.engineRPM || 0;
    const rpmCurveFill = document.getElementById('rpm-tach-curve-fill');
    const rpmPct = Math.min(100, Math.max(0, Math.round((currentRpm / maxRpm) * 100)));
    if (rpmCurveFill) {
        rpmCurveFill.style.width = `${rpmPct}%`;
    }
    const rpmEl = document.getElementById('telemetry-rpm');
    if (rpmEl) {
        rpmEl.textContent = `${currentRpm.toLocaleString()} RPM`;
        if (currentRpm > 12500) {
            rpmEl.style.color = '#ef4444';
            rpmEl.style.textShadow = '0 0 8px rgba(239, 68, 68, 0.6)';
        } else if (currentRpm > 10500) {
            rpmEl.style.color = '#f59e0b';
            rpmEl.style.textShadow = 'none';
        } else {
            rpmEl.style.color = '#1e1b4b';
            rpmEl.style.textShadow = 'none';
        }
    }

    updateRevLeds(currentRpm);

    // 3. Quad Tyre Thermals & Wear Suite
    // Index map: 0 = RL, 1 = RR, 2 = FL, 3 = FR
    const temps = car.tyreSurfaceTemps || [0, 0, 0, 0];
    const brakes = car.brakesTemperature || [0, 0, 0, 0];
    const wear = car.tyreWear || [0, 0, 0, 0];

    updateCornerHUD('fl', temps[2], brakes[2], wear[2]);
    updateCornerHUD('fr', temps[3], brakes[3], wear[3]);
    updateCornerHUD('rl', temps[0], brakes[0], wear[0]);
    updateCornerHUD('rr', temps[1], brakes[1], wear[1]);

    // 4. Aerodynamics & Damage
    const flWing = car.frontLeftWingDamage || 0;
    const frWing = car.frontRightWingDamage || 0;
    updateDamageBar('dmg-fl-wing', flWing);
    updateDamageBar('dmg-fr-wing', frWing);
    updateDamageBar('dmg-rear-wing', Math.round((flWing + frWing) * 0.3));

    // 5. Energy, Fuel & Weather Strategy
    const ersFill = document.getElementById('ers-meter-fill');
    const ersVal = document.getElementById('ers-meter-val');
    const ersModeBadge = document.getElementById('ers-mode-badge');
    if (ersFill) ersFill.style.width = `${ersPct}%`;
    if (ersVal) ersVal.textContent = `${ersPct}%`;
    if (ersModeBadge) {
        // F1 23/24/25 Official Deploy Modes: 0=NONE, 1=MEDIUM, 2=HOTLAP, 3=OVERTAKE
        const modes = ['NONE', 'MEDIUM', 'HOTLAP', 'OVERTAKE'];
        const modeName = modes[car.ersDeployMode] !== undefined ? modes[car.ersDeployMode] : 'NONE';
        ersModeBadge.textContent = modeName;
        ersModeBadge.className = `ers-mode-tag mode-${modeName.toLowerCase()}`;
    }

    const fuelVal = document.getElementById('fuel-tank-val');
    const fuelDelta = document.getElementById('fuel-delta-badge');
    const fuelEstLaps = document.getElementById('fuel-est-laps');
    if (fuelVal) fuelVal.textContent = `${(car.fuelInTank || 0).toFixed(1)} kg`;
    if (fuelEstLaps) {
        const estLaps = (car.fuelInTank || 0) > 0 ? ((car.fuelInTank || 0) / 1.65).toFixed(1) : '—';
        fuelEstLaps.textContent = `EST. REMAINING: ${estLaps} LAPS`;
    }
    if (fuelDelta) {
        const deltaLaps = ((car.fuelInTank || 0) - 12.0) * 0.35;
        const isPos = deltaLaps >= 0;
        fuelDelta.textContent = `${isPos ? '+' : ''}${deltaLaps.toFixed(2)} LAPS`;
        fuelDelta.className = `fuel-delta-tag ${isPos ? 'positive' : 'negative'}`;
    }

    // Weather & Track Environment Suite
    const weatherId = data.weather !== undefined ? data.weather : 0;
    const trackTemp = data.trackTemperature !== undefined && data.trackTemperature !== 0 ? data.trackTemperature : 38;
    const airTemp = data.airTemperature !== undefined && data.airTemperature !== 0 ? data.airTemperature : 26;
    const rainPct = data.rainPercentage !== undefined ? data.rainPercentage : (weatherId >= 3 ? 80 : (weatherId === 2 ? 15 : 0));

    const weatherIcons = ['☀️', '🌤️', '☁️', '🌦️', '🌧️', '⛈️'];
    const weatherTitles = ['CLEAR / DRY', 'LIGHT CLOUDS', 'OVERCAST', 'LIGHT RAIN', 'HEAVY RAIN', 'STORM / WET'];
    
    const weatherIconEl = document.getElementById('weather-icon');
    const weatherTitleEl = document.getElementById('weather-condition-txt');
    const weatherTrackEl = document.getElementById('weather-track-temp');
    const weatherAirEl = document.getElementById('weather-air-temp');
    const weatherRainEl = document.getElementById('weather-rain-pct');

    if (weatherIconEl) weatherIconEl.textContent = weatherIcons[weatherId] || '☀️';
    if (weatherTitleEl) weatherTitleEl.textContent = weatherTitles[weatherId] || 'CLEAR / DRY';
    if (weatherTrackEl) weatherTrackEl.textContent = `${trackTemp}°C`;
    if (weatherAirEl) weatherAirEl.textContent = `${airTemp}°C`;
    if (weatherRainEl) {
        weatherRainEl.textContent = `${rainPct}%`;
        weatherRainEl.style.color = rainPct > 30 ? '#2563eb' : '#10b981';
    }

    // Forecast timeline sample updates
    const fIcon0 = document.getElementById('forecast-icon-0');
    const fTemp0 = document.getElementById('forecast-temp-0');
    if (fIcon0) fIcon0.textContent = weatherIcons[weatherId] || '☀️';
    if (fTemp0) fTemp0.textContent = `${trackTemp}°C`;

    // 6. Sector Splits Table
    updateSectorSplitsTable(car, data);
}

function updateCornerHUD(corner, surfaceTemp, brakeTemp, wearPct) {
    const tempEl = document.getElementById(`temp-${corner}`);
    const brakeEl = document.getElementById(`brake-${corner}`);
    const wearFill = document.getElementById(`wear-fill-${corner}`);
    const wearTxt = document.getElementById(`wear-txt-${corner}`);

    if (tempEl) {
        tempEl.textContent = `${surfaceTemp}°C`;
        tempEl.style.color = getTyreTempColor(surfaceTemp);
    }
    if (brakeEl) {
        brakeEl.textContent = `${brakeTemp}°C`;
        brakeEl.style.color = getBrakeTempColor(brakeTemp);
    }
    if (wearFill && wearTxt) {
        const roundedWear = Math.round(wearPct);
        wearFill.style.width = `${roundedWear}%`;
        wearFill.style.background = getWearColor(roundedWear);
        wearTxt.textContent = `${roundedWear}%`;
    }
}

function updateDamageBar(elementId, damagePercent) {
    const fill = document.getElementById(`${elementId}-fill`);
    const val = document.getElementById(`${elementId}-val`);
    if (fill && val) {
        fill.style.width = `${damagePercent}%`;
        fill.style.background = damagePercent > 40 ? '#ef4444' : (damagePercent > 15 ? '#eab308' : '#22c55e');
        val.textContent = `${damagePercent}%`;
    }
}

function updateSectorSplitsTable(car, sessionData) {
    const s1El = document.getElementById('split-s1-live');
    const s2El = document.getElementById('split-s2-live');
    const s3El = document.getElementById('split-s3-live');
    const s1Last = document.getElementById('split-s1-last');
    const s2Last = document.getElementById('split-s2-last');
    const s3Last = document.getElementById('split-s3-last');
    const s1Best = document.getElementById('split-s1-best');
    const s2Best = document.getElementById('split-s2-best');
    const s3Best = document.getElementById('split-s3-best');

    if (s1Last) s1Last.textContent = formatSectorMS(car.lastLapSector1TimeInMS);
    if (s2Last) s2Last.textContent = formatSectorMS(car.lastLapSector2TimeInMS);
    if (s3Last) s3Last.textContent = formatSectorMS(car.lastLapSector3TimeInMS);

    if (s1Best) s1Best.textContent = formatSectorMS(car.bestSector1TimeInMS);
    if (s2Best) s2Best.textContent = formatSectorMS(car.bestSector2TimeInMS);
    if (s3Best) s3Best.textContent = formatSectorMS(car.bestSector3TimeInMS);

    if (s1El) s1El.textContent = formatSectorMS(car.sector1TimeInMS);
    if (s2El) s2El.textContent = formatSectorMS(car.sector2TimeInMS);
    if (s3El) s3El.textContent = '—';
}

// ── Rev LED Lights Bar ────────────────────────────────────────────────────
function initRevLeds() {
    const container = document.getElementById('rev-led-bar');
    if (!container) return;

    container.innerHTML = '';
    // 15 LEDs: 5 Green, 5 Yellow, 3 Red, 2 Blue
    for (let i = 0; i < 15; i++) {
        const led = document.createElement('div');
        let colorClass = 'green';
        if (i >= 5 && i < 10) colorClass = 'yellow';
        else if (i >= 10 && i < 13) colorClass = 'red';
        else if (i >= 13) colorClass = 'blue';

        led.className = `rev-led ${colorClass}`;
        led.id = `rev-led-${i}`;
        container.appendChild(led);
    }
}

function updateRevLeds(rpm) {
    const MAX_RPM = 13500;
    const MIN_RPM = 8000;
    const pct = Math.max(0, Math.min(1, (rpm - MIN_RPM) / (MAX_RPM - MIN_RPM)));
    const activeCount = Math.floor(pct * 15);

    for (let i = 0; i < 15; i++) {
        const led = document.getElementById(`rev-led-${i}`);
        if (led) {
            led.classList.toggle('active', i < activeCount);
        }
    }
}

// ── Driver Pairing & Roster Modal ─────────────────────────────────────────
async function fetchDriversList() {
    try {
        const token = localStorage.getItem('jwtToken');
        const res = await fetch('/api/engineer/drivers', {
            headers: token ? { 'Authorization': `Bearer ${token}` } : {}
        });
        if (res.ok) {
            availableDrivers = await res.json();
            renderDriverRoster();
        }
    } catch (e) {
        console.warn("Could not fetch drivers roster", e);
    }
}

function openPairingModal() {
    document.getElementById('pairing-modal').style.display = 'flex';
    fetchDriversList();
}

function closePairingModal() {
    document.getElementById('pairing-modal').style.display = 'none';
}

function renderDriverRoster() {
    const listEl = document.getElementById('driver-roster-list');
    if (!listEl) return;

    if (availableDrivers.length === 0) {
        listEl.innerHTML = `<div style="text-align:center; color:#64748b; padding:12px;">No active drivers online. Ask your driver for their Team PIN.</div>`;
        return;
    }

    let html = '';
    availableDrivers.forEach(d => {
        html += `
            <div class="roster-item" onclick="pairWithDriverId(${d.id})">
                <div style="display:flex; align-items:center; gap:10px;">
                    <div style="width:10px; height:10px; border-radius:50%; background:${d.live ? '#22c55e' : '#64748b'};"></div>
                    <div>
                        <div style="font-weight:800; font-size:0.95rem;">${d.username}</div>
                        <div style="font-size:0.75rem; color:#94a3b8;">PIN: ${d.teamPin || 'N/A'} • ${d.position || 'P—'}</div>
                    </div>
                </div>
                <button class="btn-pair-driver" style="font-size:0.75rem; padding:4px 8px;">LINK</button>
            </div>
        `;
    });
    listEl.innerHTML = html;
}

async function pairWithDriverPin() {
    const pinInput = document.getElementById('input-team-pin');
    const pin = pinInput ? pinInput.value.trim() : '';
    if (!pin) return;

    const token = localStorage.getItem('jwtToken');
    try {
        const res = await fetch('/api/engineer/pair', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify({ teamPin: pin })
        });

        if (res.ok) {
            const data = await res.json();
            const driverName = data.driverUsername || 'LINKED';
            localStorage.setItem('pairedDriverName', driverName);
            if (data.driverId) localStorage.setItem('pairedDriverId', data.driverId);
            const pillEl = document.getElementById('paired-driver-name');
            if (pillEl) pillEl.textContent = driverName;
            closePairingModal();
        } else {
            const err = await res.text();
            alert("Pairing failed: " + err);
        }
    } catch (e) {
        alert("Network error pairing with driver.");
    }
}

async function pairWithDriverId(driverId) {
    const token = localStorage.getItem('jwtToken');
    try {
        const res = await fetch('/api/engineer/pair', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify({ driverId: driverId })
        });
        if (res.ok) {
            const data = await res.json();
            const driverName = data.driverUsername || 'LINKED';
            localStorage.setItem('pairedDriverName', driverName);
            if (data.driverId) localStorage.setItem('pairedDriverId', data.driverId);
            const pillEl = document.getElementById('paired-driver-name');
            if (pillEl) pillEl.textContent = driverName;
            closePairingModal();
        }
    } catch (e) {}
}

// ── Helpers & Formatting ──────────────────────────────────────────────────
function getDriverCode(name, carIndex) {
    if (!name) return `C${carIndex}`;
    const clean = name.trim().toUpperCase();
    if (DRIVER_CODE_MAP[clean]) return DRIVER_CODE_MAP[clean];
    const parts = clean.split(/\s+/);
    const lastName = parts[parts.length - 1];
    return (lastName || clean).substring(0, 3);
}

function getCompoundBadge(compoundId) {
    // 16=Soft, 17=Medium, 18=Hard, 7=Inter, 8=Wet
    switch (compoundId) {
        case 16: return { char: 'S', name: 'SOFT', cssClass: 'compound-s' };
        case 17: return { char: 'M', name: 'MEDIUM', cssClass: 'compound-m' };
        case 18: return { char: 'H', name: 'HARD', cssClass: 'compound-h' };
        case 7:  return { char: 'I', name: 'INTER', cssClass: 'compound-i' };
        case 8:  return { char: 'W', name: 'WET', cssClass: 'compound-w' };
        default: return { char: '—', name: 'DRY', cssClass: 'compound-none' };
    }
}

function formatTimeMS(ms) {
    if (!ms || ms <= 0) return "--:--.---";
    const min = Math.floor(ms / 60000);
    const sec = Math.floor((ms % 60000) / 1000);
    const mil = ms % 1000;
    return `${min}:${sec.toString().padStart(2, '0')}.${mil.toString().padStart(3, '0')}`;
}

function formatSectorMS(ms) {
    if (!ms || ms <= 0) return "—";
    return (ms / 1000.0).toFixed(3);
}

function getTyreTempColor(temp) {
    if (temp < 80) return '#38bdf8'; // cold
    if (temp <= 105) return '#22c55e'; // optimal
    if (temp <= 115) return '#eab308'; // warm
    return '#ef4444'; // overheated
}

function getBrakeTempColor(temp) {
    if (temp < 300) return '#94a3b8';
    if (temp <= 650) return '#22c55e';
    if (temp <= 850) return '#f97316';
    return '#ef4444';
}

function getWearColor(wearPct) {
    if (wearPct < 35) return '#22c55e';
    if (wearPct < 65) return '#eab308';
    return '#ef4444';
}

function getTrackName(trackId) {
    const tracks = {
        0: 'Melbourne', 1: 'Paul Ricard', 2: 'Shanghai', 3: 'Bahrain',
        4: 'Catalunya', 5: 'Monaco', 6: 'Montreal', 7: 'Silverstone',
        8: 'Hockenheim', 9: 'Hungaroring', 10: 'Spa', 11: 'Monza',
        12: 'Singapore', 13: 'Suzuka', 14: 'Abu Dhabi', 15: 'Texas',
        16: 'Brazil', 17: 'Austria', 18: 'Sochi', 19: 'Mexico',
        20: 'Baku', 26: 'Zandvoort', 27: 'Imola', 29: 'Jeddah',
        30: 'Miami', 31: 'Las Vegas', 32: 'Qatar'
    };
    return tracks[trackId] || 'Grand Prix Circuit';
}

function getWeatherName(weatherId) {
    const weather = ['CLEAR', 'LIGHT CLOUD', 'OVERCAST', 'LIGHT RAIN', 'HEAVY RAIN', 'STORM'];
    return weather[weatherId] || 'DRY';
}

function handleLiveAlert(alert) {
    console.log('[Pit Wall Alert]', alert);
}

// ── Mobile / Tablet View Switcher ─────────────────────────────────────────
function switchMobilePitwallView(view, btn) {
    const towerCol = document.getElementById('pitwall-left-col');
    const mainGrid = document.querySelector('.pitwall-main-grid');
    const tabBtns = document.querySelectorAll('.mobile-tab-btn');

    tabBtns.forEach(b => b.classList.remove('active'));
    if (btn) btn.classList.add('active');

    if (window.innerWidth <= 1024) {
        if (view === 'tower') {
            if (towerCol) towerCol.style.display = 'flex';
            if (mainGrid) mainGrid.style.display = 'none';
        } else {
            if (towerCol) towerCol.style.display = 'none';
            if (mainGrid) mainGrid.style.display = 'grid';
        }
    }
}

// Reset mobile displays on resize back to desktop
window.addEventListener('resize', () => {
    const towerCol = document.getElementById('pitwall-left-col');
    const mainGrid = document.querySelector('.pitwall-main-grid');
    if (window.innerWidth > 1024) {
        if (towerCol) towerCol.style.display = '';
        if (mainGrid) mainGrid.style.display = '';
    } else {
        const activeBtn = document.querySelector('.mobile-tab-btn.active');
        const view = activeBtn && activeBtn.id === 'btn-tab-tower' ? 'tower' : 'telemetry';
        switchMobilePitwallView(view, activeBtn);
    }
});
