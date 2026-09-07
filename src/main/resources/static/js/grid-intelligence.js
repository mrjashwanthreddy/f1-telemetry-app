/**
 * Grid Intelligence — All Drivers Live Dashboard
 * Self-contained module. Shares no global state with engineer-dashboard.js.
 * Subscribes to /topic/live-telemetry WebSocket — same topic, separate instance.
 */

'use strict';

// ── Constants ────────────────────────────────────────────────────────────────

const GI_TRACK_NAMES = {
    0:'Melbourne',1:'Paul Ricard',2:'Shanghai',3:'Sakhir (Bahrain)',4:'Catalunya',
    5:'Monaco',6:'Montreal',7:'Silverstone',8:'Hockenheim',9:'Hungaroring',
    10:'Spa',11:'Monza',12:'Singapore',13:'Suzuka',14:'Abu Dhabi',15:'Texas',
    16:'Brazil',17:'Austria',18:'Sochi',19:'Mexico',20:'Baku',
    21:'Sakhir Short',22:'Silverstone Short',23:'Texas Short',24:'Suzuka Short',
    25:'Hanoi',26:'Zandvoort',27:'Imola',28:'Portimão',29:'Jeddah',
    30:'Miami',31:'Las Vegas',32:'Losail',33:'Spielberg',34:'Silverstone'
};

const GI_WEATHER_NAMES = {0:'Clear',1:'Light Cloud',2:'Overcast',3:'Light Rain',4:'Heavy Rain',5:'Storm'};
const GI_SC_STATUS = {0:'GREEN FLAG',1:'VIRTUAL SC',2:'SAFETY CAR',3:'FORMATION LAP'};
const GI_SC_CLASS  = {0:'track-clear',1:'vsc',2:'full-sc',3:'full-sc'};

const GI_COMPOUND_NAMES = {
    16:'Soft',17:'Medium',18:'Hard',7:'Inter',8:'Wet',
    0:'?',4:'Hyper',19:'Soft (C5)',20:'Medium (C4)',21:'Hard (C3)',
    22:'Soft (C4)',23:'Medium (C3)',24:'Hard (C2)'
};
const GI_COMPOUND_CLASS = {16:'soft',17:'medium',18:'hard',7:'inter',8:'wet'};

const GI_ERS_MODE_NAMES  = {0:'NONE',1:'MEDIUM',2:'HOTLAP',3:'OVERTAKE'};
const GI_ERS_MODE_CLASSES = {0:'mode-none',1:'mode-medium',2:'mode-hotlap',3:'mode-overtake'};

// Official F1 2024/2025 Team Details (matches engineer-dashboard.js & app.js)
const GI_TEAMS = {
    0: { name: 'Mercedes-AMG Petronas', color: '#6CD3BF' },
    1: { name: 'Scuderia Ferrari', color: '#F91536' },
    2: { name: 'Oracle Red Bull Racing', color: '#3671C6' },
    3: { name: 'Williams Racing', color: '#37BEDD' },
    4: { name: 'Aston Martin Aramco', color: '#358C75' },
    5: { name: 'BWT Alpine F1', color: '#2293D1' },
    6: { name: 'Visa Cash App RB', color: '#6692FF' },
    7: { name: 'MoneyGram Haas F1', color: '#B6BABD' },
    8: { name: 'McLaren Formula 1', color: '#F58020' },
    9: { name: 'Stake Kick Sauber', color: '#52E252' }
};

// Driver name to team fallback (in case teamId is missing or unknown)
const GI_DRIVER_TEAM_MAP = {
    'RUSSELL': 0, 'ANTONELLI': 0,
    'LECLERC': 1, 'HAMILTON': 1,
    'VERSTAPPEN': 2, 'TSUNODA': 2,
    'ALBON': 3, 'SAINZ': 3,
    'ALONSO': 4, 'STROLL': 4,
    'GASLY': 5, 'COLAPINTO': 5,
    'LAWSON': 6, 'HADJAR': 6,
    'OCON': 7, 'BEARMAN': 7,
    'NORRIS': 8, 'PIASTRI': 8,
    'HULKENBERG': 9, 'BORTOLETO': 9, 'ZHOU': 9, 'BOTTAS': 9
};

function giResolveTeamId(teamId, driverName) {
    if (teamId !== undefined && teamId !== null && GI_TEAMS[teamId]) {
        return teamId;
    }
    if (driverName) {
        const upper = driverName.toUpperCase();
        for (const [key, tId] of Object.entries(GI_DRIVER_TEAM_MAP)) {
            if (upper.includes(key)) return tId;
        }
    }
    return teamId;
}

const GI_ERS_MAX_JOULES = 4_000_000; // 4 MJ battery capacity per F1 spec

// ── State ─────────────────────────────────────────────────────────────────────

let giStompClient  = null;
let giConnected    = false;
let giSelectedCar  = -1;
let giLastData     = null;
let giPitStopCount = new Array(22).fill(0);
let giLastLapNum   = new Array(22).fill(0);
let giLastResultStatus = new Array(22).fill(0);

/**
 * lapHistoryStore[carIndex] = Array<LapRecord>
 * LapRecord = { lapNum, totalMS, s1MS, s2MS, s3MS, compound, valid, hasPit }
 */
const lapHistoryStore = Array.from({length: 22}, () => []);

// Per-car best lap/sector for purple colouring
const giPersonalBests = Array.from({length: 22}, () => ({
    lapMS: 0, s1MS: 0, s2MS: 0, s3MS: 0
}));

// Performance & rendering loop throttle state
let giAnimationStarted = false;
let giLastDriverListRender = 0;
let giLastDeepDiveRender = 0;

// ── Helpers ───────────────────────────────────────────────────────────────────

function giFormatTime(ms) {
    if (!ms || ms <= 0) return '—';
    const totalSec = ms / 1000;
    const min = Math.floor(totalSec / 60);
    const sec = totalSec % 60;
    return `${min}:${String(Math.floor(sec)).padStart(2,'0')}.${String(ms % 1000).padStart(3,'0')}`;
}

function giFormatSector(ms) {
    if (!ms || ms <= 0) return '—';
    const sec = ms / 1000;
    return sec.toFixed(3);
}

function giFormatDelta(ms) {
    if (!ms || ms <= 0) return '—';
    if (ms < 60000) return `+${(ms/1000).toFixed(3)}s`;
    const m = Math.floor(ms/60000);
    const s = ((ms % 60000)/1000).toFixed(3);
    return `+${m}L ${s}s`;
}

function giGetTeamColour(teamId, driverName) {
    const resolved = giResolveTeamId(teamId, driverName);
    return GI_TEAMS[resolved]?.color ?? '#7c3aed';
}

function giGetTeamName(teamId, driverName) {
    const resolved = giResolveTeamId(teamId, driverName);
    return GI_TEAMS[resolved]?.name ?? 'Formula 1';
}

function giGetCompoundName(id) {
    return GI_COMPOUND_NAMES[id] ?? '?';
}

function giGetCompoundClass(id) {
    return GI_COMPOUND_CLASS[id] ?? 'medium';
}

function giCompoundBadge(id) {
    return `<span class="compound-badge ${giGetCompoundClass(id)}">${giGetCompoundName(id).toUpperCase()}</span>`;
}

function giWearColour(wear) {
    if (wear < 20) return '#10b981';
    if (wear < 50) return '#f59e0b';
    if (wear < 75) return '#f97316';
    return '#ef4444';
}

function giIsRestricted(car) {
    return car.setupFrontWing === 0 && car.setupRearWing === 0 &&
           car.setupBrakePressure === 0 && car.setupFuelLoad === 0;
}

function giPct(val, max) {
    return Math.min(100, Math.max(0, (val / max) * 100)).toFixed(1);
}

function giBar50(val) {
    return Math.min(100, Math.max(0, (val / 50) * 100)).toFixed(1);
}

// ── Connected Driver Resolution ───────────────────────────────────────────────

function giGetConnectedDriverIdx(data) {
    if (!data || !data.cars) return 0;
    // 1. If playerCarIndex is set and valid
    if (data.playerCarIndex !== undefined && data.playerCarIndex >= 0 && data.playerCarIndex < 22 && data.cars[data.playerCarIndex]) {
        return data.playerCarIndex;
    }
    // 2. Check if engineer paired with a driver by name
    const pairedName = localStorage.getItem('pairedDriverName');
    if (pairedName) {
        const upper = pairedName.toUpperCase();
        for (let i = 0; i < data.cars.length; i++) {
            const c = data.cars[i];
            if (c && c.name && c.name.toUpperCase().includes(upper)) {
                return i;
            }
        }
    }
    return 0;
}

// ── WebSocket ─────────────────────────────────────────────────────────────────

function giInitWebSocket() {
    if (giStompClient && giStompClient.connected) return;

    const socket = new SockJS('/telemetry-websocket');
    giStompClient = Stomp.over(socket);
    giStompClient.debug = null; // suppress debug spam

    giStompClient.connect({}, () => {
        giConnected = true;
        giSetConnectionBadge(true);
        giStartAnimationLoop();

        giStompClient.subscribe('/topic/live-telemetry', (msg) => {
            try {
                const data = JSON.parse(msg.body);
                giLastData = data;
                giOnTelemetry(data);
            } catch(e) { /* silent */ }
        });
    }, () => {
        giConnected = false;
        giSetConnectionBadge(false);
        // Reconnect after 3s
        setTimeout(giInitWebSocket, 3000);
    });
}

function giSetConnectionBadge(connected) {
    const badge = document.getElementById('gi-connection-badge');
    if (!badge) return;
    if (connected) {
        badge.textContent = 'LIVE';
        badge.style.background = 'rgba(16, 185, 129, 0.15)';
        badge.style.color = '#059669';
        badge.style.borderColor = 'rgba(16, 185, 129, 0.35)';
    } else {
        badge.textContent = 'RECONNECTING...';
        badge.style.background = 'rgba(239, 68, 68, 0.12)';
        badge.style.color = '#dc2626';
        badge.style.borderColor = 'rgba(239, 68, 68, 0.35)';
    }
}

// ── Lightweight Telemetry Ingestion (Zero Lag, Decoupled) ────────────────────

function giOnTelemetry(data) {
    if (!data || !data.cars) return;

    // Fast lap history accumulation (in-memory only, no DOM manipulation)
    const cars = data.cars || [];
    for (let i = 0; i < 22; i++) {
        const car = cars[i];
        if (car) giAccumulateLapHistory(i, car, data);
    }

    // Auto-select on initial telemetry packet if nothing selected yet
    if (giSelectedCar === -1 && cars.length > 0) {
        const pIdx = data.playerCarIndex;
        let defaultIdx = (pIdx !== undefined && pIdx >= 0 && pIdx < 22 && cars[pIdx]) ? pIdx : -1;
        if (defaultIdx === -1) {
            const p1Idx = cars.findIndex(c => c && c.position === 1);
            defaultIdx = p1Idx >= 0 ? p1Idx : 0;
        }
        giSelectDriver(defaultIdx);
    }
}

// ── Decoupled 60FPS UI Animation Loop ─────────────────────────────────────────

function giStartAnimationLoop() {
    if (giAnimationStarted) return;
    giAnimationStarted = true;
    requestAnimationFrame(giAnimationStep);
}

function giAnimationStep(timestamp) {
    requestAnimationFrame(giAnimationStep);

    if (!giLastData) return;

    // 1. Fast telemetry inputs & gauges (30 FPS)
    if (timestamp - giLastDeepDiveRender >= 33) {
        giLastDeepDiveRender = timestamp;
        giUpdateSessionRibbon(giLastData);
        if (giSelectedCar >= 0 && giLastData.cars && giLastData.cars[giSelectedCar]) {
            giRenderDetailPanel(giSelectedCar, giLastData.cars[giSelectedCar], giLastData);
        }
    }

    // 2. Standings & Driver List (4 Hz = every 250ms) — stable in-place DOM, zero click loss, zero lag
    if (timestamp - giLastDriverListRender >= 250) {
        giLastDriverListRender = timestamp;
        giRenderDriverList(giLastData);
    }
}

// ── Session Ribbon ────────────────────────────────────────────────────────────

function giUpdateSessionRibbon(data) {
    const trackEl    = document.getElementById('gi-ribbon-track');
    const weatherEl  = document.getElementById('gi-ribbon-weather');
    const lapEl      = document.getElementById('gi-ribbon-lap');
    const statusEl   = document.getElementById('gi-ribbon-status');

    if (trackEl)   trackEl.textContent  = GI_TRACK_NAMES[data.trackId] ?? 'Grand Prix';
    if (weatherEl) weatherEl.textContent = GI_WEATHER_NAMES[data.weather] ?? 'Dry';

    const playerIdx = data.playerCarIndex ?? 0;
    const playerCar = (data.cars || [])[playerIdx];
    const curLap    = playerCar?.currentLapNum ?? '—';
    const totalLaps = data.totalLaps > 0 ? data.totalLaps : '—';
    if (lapEl) lapEl.textContent = `${curLap} / ${totalLaps}`;

    if (statusEl) {
        const sc = data.safetyCarStatus ?? 0;
        statusEl.textContent = GI_SC_STATUS[sc] ?? 'GREEN FLAG';
        statusEl.className   = `status-pill ${GI_SC_CLASS[sc] ?? 'track-clear'}`;
        if (data.redFlag)        { statusEl.textContent = 'RED FLAG';  statusEl.className = 'status-pill red-flag'; }
        if (data.chequeredFlag)  { statusEl.textContent = '🏁 FINISHED'; statusEl.className = 'status-pill chequered-flag'; }
    }
}

// ── Lap History Ingestion & Accumulation ───────────────────────────────────────

function giAccumulateLapHistory(carIdx, car, data) {
    // 1. Ingest backend completed laps from PacketSessionHistoryData if available
    if (car.lapHistory && Array.isArray(car.lapHistory) && car.lapHistory.length > 0) {
        const compound = car.visualTyreCompound ?? 17;
        lapHistoryStore[carIdx] = car.lapHistory.map(l => ({
            lapNum: l.lapNum,
            totalMS: l.lapTimeInMS,
            s1MS: l.sector1TimeInMS,
            s2MS: l.sector2TimeInMS,
            s3MS: l.sector3TimeInMS,
            compound: compound,
            valid: l.valid !== false,
            hasPit: false
        }));

        // Recompute Personal Bests
        const pb = giPersonalBests[carIdx];
        for (const l of lapHistoryStore[carIdx]) {
            if (l.valid && l.totalMS > 0 && (!pb.lapMS || l.totalMS < pb.lapMS)) pb.lapMS = l.totalMS;
            if (l.s1MS > 0 && (!pb.s1MS || l.s1MS < pb.s1MS)) pb.s1MS = l.s1MS;
            if (l.s2MS > 0 && (!pb.s2MS || l.s2MS < pb.s2MS)) pb.s2MS = l.s2MS;
            if (l.s3MS > 0 && (!pb.s3MS || l.s3MS < pb.s3MS)) pb.s3MS = l.s3MS;
        }

        if (giSelectedCar === carIdx) {
            giRenderLapHistoryTable(carIdx);
        }
        giLastLapNum[carIdx] = car.currentLapNum ?? 0;
        return;
    }

    // 2. Fallback: If lapHistoryStore is empty but car has completed at least one lap
    if (lapHistoryStore[carIdx].length === 0 && car.lastLapTimeInMS > 0) {
        const completedLapNum = Math.max(1, (car.currentLapNum || 2) - 1);
        const lastMS = car.lastLapTimeInMS;
        const s1MS   = car.lastLapSector1TimeInMS || 0;
        const s2MS   = car.lastLapSector2TimeInMS || 0;
        const s3MS   = car.lastLapSector3TimeInMS || 0;
        const compound = car.visualTyreCompound ?? 17;

        lapHistoryStore[carIdx].push({
            lapNum: completedLapNum,
            totalMS: lastMS,
            s1MS: s1MS,
            s2MS: s2MS,
            s3MS: s3MS,
            compound: compound,
            valid: true,
            hasPit: false
        });

        const pb = giPersonalBests[carIdx];
        if (!pb.lapMS || lastMS < pb.lapMS) pb.lapMS = lastMS;
        if (s1MS > 0 && (!pb.s1MS || s1MS < pb.s1MS)) pb.s1MS = s1MS;
        if (s2MS > 0 && (!pb.s2MS || s2MS < pb.s2MS)) pb.s2MS = s2MS;
        if (s3MS > 0 && (!pb.s3MS || s3MS < pb.s3MS)) pb.s3MS = s3MS;

        if (giSelectedCar === carIdx) {
            giRenderLapHistoryTable(carIdx);
        }
    }

    // 3. Live lap transition detection
    const newLap = car.currentLapNum ?? 0;
    const oldLap = giLastLapNum[carIdx];

    if (newLap > oldLap && oldLap > 0) {
        const lastMS = car.lastLapTimeInMS ?? 0;
        const s1MS   = car.lastLapSector1TimeInMS ?? 0;
        const s2MS   = car.lastLapSector2TimeInMS ?? 0;
        const s3MS   = car.lastLapSector3TimeInMS ?? 0;

        if (lastMS > 0) {
            const alreadyExists = lapHistoryStore[carIdx].some(l => l.lapNum === oldLap);
            if (!alreadyExists) {
                const compound = car.visualTyreCompound ?? 17;
                lapHistoryStore[carIdx].push({
                    lapNum: oldLap,
                    totalMS: lastMS,
                    s1MS, s2MS, s3MS,
                    compound,
                    valid: true,
                    hasPit: false
                });

                const pb = giPersonalBests[carIdx];
                if (!pb.lapMS || lastMS < pb.lapMS) pb.lapMS = lastMS;
                if (s1MS > 0 && (!pb.s1MS || s1MS < pb.s1MS)) pb.s1MS = s1MS;
                if (s2MS > 0 && (!pb.s2MS || s2MS < pb.s2MS)) pb.s2MS = s2MS;
                if (s3MS > 0 && (!pb.s3MS || s3MS < pb.s3MS)) pb.s3MS = s3MS;

                if (giSelectedCar === carIdx) {
                    giRenderLapHistoryTable(carIdx);
                }
            }
        }
    }

    giLastLapNum[carIdx] = newLap;
}

// ── Driver List Rendering (Stable In-Place DOM — No Element Destruction) ─────

function giRenderDriverList(data) {
    const cars       = data.cars || [];
    const playerIdx  = data.playerCarIndex ?? -1;
    const container  = document.getElementById('gi-driver-rows');
    const countEl    = document.getElementById('gi-active-count');

    if (!container) return;

    // Filter all 20 active cars on track (position 1 to 22)
    const activeCars = [];
    for (let i = 0; i < 22; i++) {
        const car = cars[i];
        if (!car) continue;
        if (car.position > 0 && car.position <= 22) {
            activeCars.push({ idx: i, car, isPlayer: i === playerIdx });
        }
    }
    activeCars.sort((a, b) => (a.car.position || 99) - (b.car.position || 99));

    if (countEl) countEl.textContent = `${activeCars.length} CARS`;

    // Remove waiting notice once cars are detected
    if (activeCars.length > 0) {
        const notices = container.querySelectorAll('.gi-waiting-notice, .gi-no-cars');
        notices.forEach(n => n.remove());
    }

    if (activeCars.length === 0) {
        container.innerHTML = '<div class="gi-no-cars" style="padding:30px 16px;text-align:center;color:#6b21a8;font-size:0.78rem;font-weight:800;">NO ACTIVE CARS DETECTED</div>';
        return;
    }

    activeCars.forEach(({idx, car, isPlayer}) => {
        const driverName   = (car.name || 'DRIVER ' + (idx+1)).toUpperCase();
        const teamName     = giGetTeamName(car.teamId, driverName);
        const teamColour   = giGetTeamColour(car.teamId, driverName);
        const isSelected   = giSelectedCar === idx;
        const posText      = `P${car.position || '?'}`;
        const lastLapTxt   = giFormatTime(car.lastLapTimeInMS);
        const compoundId   = car.visualTyreCompound ?? 17;
        const compoundCls  = giGetCompoundClass(compoundId);
        const compoundName = giGetCompoundName(compoundId).toUpperCase();
        const tyresAge     = car.tyresAgeLaps || 0;
        const playerLabel  = isPlayer ? '<span class="player-badge-mini">YOU</span>' : '';

        let row = document.getElementById(`gi-row-${idx}`);
        if (!row || !row.querySelector('.gi-row-info')) {
            if (!row) {
                row = document.createElement('div');
                row.id = `gi-row-${idx}`;
            }
            row.className = `gi-driver-row ${isSelected ? 'active' : ''}`;
            row.setAttribute('data-car-idx', idx);
            row.innerHTML = `
                <div class="gi-row-pos ${car.position === 1 ? 'p1' : ''}">${posText}</div>
                <div class="gi-row-team-bar" style="background:${teamColour};"></div>
                <div class="gi-row-info">
                    <div class="gi-row-name"><span class="live-dot"></span> <span class="gi-name-text">${driverName}</span> ${playerLabel}</div>
                    <div class="gi-row-meta">
                        <span class="gi-team-text">${teamName}</span> &nbsp;·&nbsp;
                        <span class="compound-badge ${compoundCls}" style="font-size:0.5rem;">${compoundName}</span>
                        &nbsp;<span class="gi-tyre-age">${tyresAge}L</span>
                    </div>
                </div>
                <div class="gi-row-laptime">${lastLapTxt}</div>
            `;
            container.appendChild(row);
        } else {
            // In-place update without destroying DOM elements
            row.classList.toggle('active', isSelected);

            const posEl = row.querySelector('.gi-row-pos');
            if (posEl) {
                if (posEl.textContent !== posText) posEl.textContent = posText;
                posEl.className = `gi-row-pos ${car.position === 1 ? 'p1' : ''}`;
            }

            const barEl = row.querySelector('.gi-row-team-bar');
            if (barEl && barEl.style.background !== teamColour) barEl.style.background = teamColour;

            const nameTextEl = row.querySelector('.gi-name-text');
            if (nameTextEl && nameTextEl.textContent !== driverName) nameTextEl.textContent = driverName;

            const teamTextEl = row.querySelector('.gi-team-text');
            if (teamTextEl && teamTextEl.textContent !== teamName) teamTextEl.textContent = teamName;

            const badgeEl = row.querySelector('.compound-badge');
            if (badgeEl) {
                badgeEl.className = `compound-badge ${compoundCls}`;
                badgeEl.textContent = compoundName;
            }

            const ageEl = row.querySelector('.gi-tyre-age');
            if (ageEl) ageEl.textContent = `${tyresAge}L`;

            const lapEl = row.querySelector('.gi-row-laptime');
            if (lapEl && lapEl.textContent !== lastLapTxt) lapEl.textContent = lastLapTxt;

            // Re-order node in container (appendChild moves existing node without destroying it)
            container.appendChild(row);
        }
    });
}

// ── Driver Selection (Instant 0ms & Completely Reliable) ─────────────────────

function giSelectDriver(carIdx) {
    if (carIdx < 0 || carIdx >= 22) return;
    giSelectedCar = carIdx;

    // Immediately update active row highlight in DOM
    const allRows = document.querySelectorAll('.gi-driver-row');
    allRows.forEach(r => {
        const rIdx = parseInt(r.getAttribute('data-car-idx'), 10);
        r.classList.toggle('active', rIdx === carIdx);
    });

    // Unhide detail content
    const emptyState = document.getElementById('gi-empty-state');
    if (emptyState) emptyState.style.display = 'none';
    const detail = document.getElementById('gi-detail-content');
    if (detail) detail.style.display = 'flex';

    // Render immediately from latest cached telemetry
    if (giLastData && giLastData.cars && giLastData.cars[carIdx]) {
        giRenderDetailPanel(carIdx, giLastData.cars[carIdx], giLastData);
    }

    // Refresh lap history
    giRenderLapHistoryTable(carIdx);
}

// ── Detail Panel Rendering ────────────────────────────────────────────────────

function giRenderDetailPanel(carIdx, car, data) {
    giRenderDetailHeader(carIdx, car, data);
    giRenderLiveInputs(car, data);
    giRenderSetupPanel(car);
    giRenderStrategyPanel(car, data);
}

// -- Detail Header Strip --

function giRenderDetailHeader(carIdx, car, data) {
    const driverName = (car.name || `DRIVER ${carIdx+1}`).toUpperCase();
    const teamColour = giGetTeamColour(car.teamId, driverName);
    const teamName   = giGetTeamName(car.teamId, driverName);

    const posEl = document.getElementById('gi-detail-pos');
    if (posEl) {
        posEl.textContent = `P${car.position || '?'}`;
        posEl.style.background = `${teamColour}18`;
        posEl.style.borderColor = `${teamColour}55`;
        posEl.style.color = teamColour;
    }

    const nameEl = document.getElementById('gi-detail-name');
    if (nameEl) nameEl.textContent = driverName;

    const teamEl = document.getElementById('gi-detail-team');
    if (teamEl) {
        teamEl.textContent = teamName.toUpperCase();
        teamEl.style.color = teamColour;
    }

    // Quick stats
    const el = (id, val) => { const e = document.getElementById(id); if(e) e.textContent = val; };
    el('gi-qs-lastlap',  giFormatTime(car.lastLapTimeInMS));
    el('gi-qs-bestlap',  giFormatTime(car.bestLapTimeInMS));
    el('gi-qs-gap',      giFormatDelta(car.deltaToLeaderInMS));
    el('gi-qs-interval', giFormatDelta(car.deltaToCarInFrontInMS));
    el('gi-qs-pits',     giPitStopCount[carIdx] || 0);

    const tyreEl = document.getElementById('gi-qs-tyre');
    if (tyreEl) {
        const cid = car.visualTyreCompound ?? 17;
        tyreEl.innerHTML = `${giCompoundBadge(cid)} <span style="color:#6b21a8;font-weight:800;">${car.tyresAgeLaps||0}L</span>`;
    }
}

// -- Column 1: Live Inputs & Telemetry Comparison Engine --

function giRenderLiveInputs(car, data) {
    const cars = data?.cars || [];
    const connIdx = giGetConnectedDriverIdx(data);
    const isSelf = (giSelectedCar === connIdx);

    // Determine reference car for comparison:
    // If selected car is own car, benchmark against P1 Leader (or P2 if self is P1)
    let refIdx = connIdx;
    if (isSelf) {
        const p1Idx = cars.findIndex(c => c && c.position === 1);
        const p2Idx = cars.findIndex(c => c && c.position === 2);
        refIdx = (p1Idx >= 0 && p1Idx !== giSelectedCar) ? p1Idx : (p2Idx >= 0 ? p2Idx : (giSelectedCar === 0 ? 1 : 0));
    }
    const refCar = cars[refIdx] || car;

    const selName = (car.name || 'SEL').toUpperCase().split(' ').pop();
    const refName = (refCar.name || (isSelf ? 'LEADER' : 'YOU')).toUpperCase().split(' ').pop();
    const refTag  = isSelf ? 'P1' : 'YOU';

    // Update Comparison Header & Badges
    const badgeEl = document.getElementById('gi-comp-badge');
    if (badgeEl) badgeEl.textContent = isSelf ? `VS ${refName} (P1 LEADER)` : `VS ${refName} (YOU)`;

    const legSel = document.getElementById('gi-legend-selected');
    if (legSel) legSel.textContent = selName;

    const legRef = document.getElementById('gi-legend-ref');
    if (legRef) legRef.textContent = `${refName} (${refTag})`;

    const tagThrSel = document.getElementById('gi-tag-thr-sel');
    if (tagThrSel) tagThrSel.textContent = selName.substring(0, 3);
    const tagThrRef = document.getElementById('gi-tag-thr-ref');
    if (tagThrRef) tagThrRef.textContent = refTag;

    const tagBrkSel = document.getElementById('gi-tag-brk-sel');
    if (tagBrkSel) tagBrkSel.textContent = selName.substring(0, 3);
    const tagBrkRef = document.getElementById('gi-tag-brk-ref');
    if (tagBrkRef) tagBrkRef.textContent = refTag;

    // 1. Throttle Comparison
    const thr    = Math.round((car.throttle ?? 0) * 100);
    const refThr = Math.round((refCar.throttle ?? 0) * 100);
    const thrFill = document.getElementById('gi-thr-fill');
    const thrVal  = document.getElementById('gi-thr-val');
    if (thrFill) thrFill.style.width = `${thr}%`;
    if (thrVal)  thrVal.textContent  = `${thr}%`;

    const refThrFill = document.getElementById('gi-ref-thr-fill');
    const refThrVal  = document.getElementById('gi-ref-thr-val');
    if (refThrFill) refThrFill.style.width = `${refThr}%`;
    if (refThrVal)  refThrVal.textContent  = `${refThr}%`;

    const dThr = thr - refThr;
    const dThrEl = document.getElementById('gi-delta-thr');
    if (dThrEl) {
        dThrEl.textContent = `Δ ${dThr >= 0 ? '+' : ''}${dThr}%`;
        dThrEl.style.color = dThr > 0 ? '#10b981' : (dThr < 0 ? '#ef4444' : '#6b21a8');
    }

    // 2. Brake Comparison
    const brk    = Math.round((car.brake ?? 0) * 100);
    const refBrk = Math.round((refCar.brake ?? 0) * 100);
    const brkFill = document.getElementById('gi-brk-fill');
    const brkVal  = document.getElementById('gi-brk-val');
    if (brkFill) brkFill.style.width = `${brk}%`;
    if (brkVal)  brkVal.textContent  = `${brk}%`;

    const refBrkFill = document.getElementById('gi-ref-brk-fill');
    const refBrkVal  = document.getElementById('gi-ref-brk-val');
    if (refBrkFill) refBrkFill.style.width = `${refBrk}%`;
    if (refBrkVal)  refBrkVal.textContent  = `${refBrk}%`;

    const dBrk = brk - refBrk;
    const dBrkEl = document.getElementById('gi-delta-brk');
    if (dBrkEl) {
        dBrkEl.textContent = `Δ ${dBrk >= 0 ? '+' : ''}${dBrk}%`;
        dBrkEl.style.color = dBrk > 0 ? '#ef4444' : (dBrk < 0 ? '#10b981' : '#6b21a8');
    }

    // 3. Speed & Gear Comparison
    const spd    = car.speed ?? 0;
    const refSpd = refCar.speed ?? 0;
    const dSpd   = spd - refSpd;

    const spdSelEl = document.getElementById('gi-speed-sel-val');
    if (spdSelEl) spdSelEl.textContent = spd;
    const spdRefEl = document.getElementById('gi-speed-ref-val');
    if (spdRefEl) spdRefEl.textContent = refSpd;

    const gearSelEl = document.getElementById('gi-gear-sel');
    if (gearSelEl) gearSelEl.textContent = (car.gear === 0 ? 'N' : (car.gear === -1 ? 'R' : `G${car.gear ?? 0}`));
    const gearRefEl = document.getElementById('gi-gear-ref');
    if (gearRefEl) gearRefEl.textContent = (refCar.gear === 0 ? 'N' : (refCar.gear === -1 ? 'R' : `G${refCar.gear ?? 0}`));

    const dSpdEl = document.getElementById('gi-delta-speed');
    if (dSpdEl) {
        dSpdEl.textContent = `${dSpd >= 0 ? '+' : ''}${dSpd} km/h`;
        dSpdEl.style.color = dSpd > 0 ? '#059669' : (dSpd < 0 ? '#dc2626' : '#6b21a8');
    }

    // 4. ERS Store Comparison
    const storeJ      = car.ersStoreEnergy ?? 0;
    const storePct    = parseFloat(giPct(storeJ, GI_ERS_MAX_JOULES));
    const refStoreJ   = refCar.ersStoreEnergy ?? 0;
    const refStorePct = parseFloat(giPct(refStoreJ, GI_ERS_MAX_JOULES));

    const ersFill = document.getElementById('gi-ers-fill');
    const ersPct  = document.getElementById('gi-ers-pct');
    if (ersFill) ersFill.style.width = `${storePct}%`;
    if (ersPct)  ersPct.textContent  = `${Math.round(storePct)}%`;

    const refErsFill = document.getElementById('gi-ref-ers-fill');
    const refErsPct  = document.getElementById('gi-ref-ers-pct');
    if (refErsFill) refErsFill.style.width = `${refStorePct}%`;
    if (refErsPct)  refErsPct.textContent  = `${Math.round(refStorePct)}%`;

    const dErs = storePct - refStorePct;
    const dErsEl = document.getElementById('gi-delta-ers');
    if (dErsEl) {
        dErsEl.textContent = `Δ ${dErs >= 0 ? '+' : ''}${dErs.toFixed(0)}%`;
        dErsEl.style.color = dErs >= 0 ? '#059669' : '#dc2626';
    }

    // 5. ERS Deploy Mode
    const modeSel = car.ersDeployMode ?? 0;
    const modeRef = refCar.ersDeployMode ?? 0;
    const modeSelEl = document.getElementById('gi-ers-mode');
    if (modeSelEl) {
        modeSelEl.textContent = `${selName.substring(0,3)}: ${GI_ERS_MODE_NAMES[modeSel] ?? 'NONE'}`;
        modeSelEl.className   = `ers-mode-badge ${GI_ERS_MODE_CLASSES[modeSel] ?? 'mode-none'}`;
    }
    const modeRefEl = document.getElementById('gi-ref-ers-mode');
    if (modeRefEl) {
        modeRefEl.textContent = `${refTag}: ${GI_ERS_MODE_NAMES[modeRef] ?? 'NONE'}`;
        modeRefEl.className   = `ers-mode-badge ${GI_ERS_MODE_CLASSES[modeRef] ?? 'mode-none'} mode-ref`;
    }

    // 6. ERS Deployed this lap
    const depJ    = car.ersDeployedThisLap ?? 0;
    const depMJ   = (depJ / 1_000_000).toFixed(2);
    const refDepJ = refCar.ersDeployedThisLap ?? 0;
    const refDepMJ = (refDepJ / 1_000_000).toFixed(2);
    const depVal    = document.getElementById('gi-deployed-val');
    const refDepVal = document.getElementById('gi-ref-deployed-val');
    if (depVal)    depVal.textContent    = `${depMJ} MJ`;
    if (refDepVal) refDepVal.textContent = `${refDepMJ} MJ`;

    const dDep = parseFloat(depMJ) - parseFloat(refDepMJ);
    const dDepEl = document.getElementById('gi-delta-dep');
    if (dDepEl) {
        dDepEl.textContent = `Δ ${dDep >= 0 ? '+' : ''}${dDep.toFixed(2)} MJ`;
        dDepEl.style.color = dDep <= 0 ? '#059669' : '#d97706';
    }

    // 7. ERS Harvested (MGU-K / MGU-H)
    const kHarvJ    = car.ersHarvestedThisLapMGUK ?? 0;
    const kHarvMJ   = (kHarvJ / 1_000_000).toFixed(2);
    const refKHarvJ = refCar.ersHarvestedThisLapMGUK ?? 0;
    const refKHarvMJ = (refKHarvJ / 1_000_000).toFixed(2);
    const mgukVal    = document.getElementById('gi-mguk-val');
    const refMgukVal = document.getElementById('gi-ref-mguk-val');
    if (mgukVal)    mgukVal.textContent    = `${kHarvMJ} MJ`;
    if (refMgukVal) refMgukVal.textContent = `${refKHarvMJ} MJ`;

    const hHarvJ    = car.ersHarvestedThisLapMGUH ?? 0;
    const hHarvMJ   = (hHarvJ / 1_000_000).toFixed(2);
    const refHHarvJ = refCar.ersHarvestedThisLapMGUH ?? 0;
    const refHHarvMJ = (refHHarvJ / 1_000_000).toFixed(2);
    const mguhVal    = document.getElementById('gi-mguh-val');
    const refMguhVal = document.getElementById('gi-ref-mguh-val');
    if (mguhVal)    mguhVal.textContent    = `${hHarvMJ} MJ`;
    if (refMguhVal) refMguhVal.textContent = `${refHHarvMJ} MJ`;
}

// -- Column 2: Car Setup --

function giRenderSetupPanel(car) {
    const restricted = giIsRestricted(car);
    const restrictedEl = document.getElementById('gi-setup-restricted');
    if (restrictedEl) restrictedEl.style.display = restricted ? 'flex' : 'none';

    const sv = (id, val) => { const e = document.getElementById(id); if(e) e.textContent = val; };
    const sb = (id, pct) => { const e = document.getElementById(id); if(e) e.style.width = `${pct}%`; };

    // Aero (0-50 scale)
    sv('gi-s-fw-v', car.setupFrontWing ?? 0);
    sb('gi-s-fw',   giBar50(car.setupFrontWing ?? 0));
    sv('gi-s-rw-v', car.setupRearWing ?? 0);
    sb('gi-s-rw',   giBar50(car.setupRearWing ?? 0));

    // Differential
    sv('gi-s-don-v',  `${car.setupOnThrottle  ?? 0}%`);
    sb('gi-s-don',    car.setupOnThrottle  ?? 0);
    sv('gi-s-doff-v', `${car.setupOffThrottle ?? 0}%`);
    sb('gi-s-doff',   car.setupOffThrottle ?? 0);

    // Suspension
    sv('gi-s-fs-v',   car.setupFrontSuspension ?? 0);
    sb('gi-s-fs',     car.setupFrontSuspension ?? 0);
    sv('gi-s-rs-v',   car.setupRearSuspension  ?? 0);
    sb('gi-s-rs',     car.setupRearSuspension  ?? 0);
    sv('gi-s-farb-v', car.setupFrontAntiRollBar ?? 0);
    sb('gi-s-farb',   car.setupFrontAntiRollBar ?? 0);
    sv('gi-s-rarb-v', car.setupRearAntiRollBar  ?? 0);
    sb('gi-s-rarb',   car.setupRearAntiRollBar  ?? 0);
    sv('gi-s-frh-v',  car.setupFrontSuspensionHeight ?? 0);
    sb('gi-s-frh',    car.setupFrontSuspensionHeight ?? 0);
    sv('gi-s-rrh-v',  car.setupRearSuspensionHeight  ?? 0);
    sb('gi-s-rrh',    car.setupRearSuspensionHeight  ?? 0);

    // Geometry
    sv('gi-s-fca-v', `${(car.setupFrontCamber ?? 0).toFixed(2)}°`);
    sv('gi-s-rca-v', `${(car.setupRearCamber  ?? 0).toFixed(2)}°`);
    sv('gi-s-fto-v', `${(car.setupFrontToe    ?? 0).toFixed(2)}°`);
    sv('gi-s-rto-v', `${(car.setupRearToe     ?? 0).toFixed(2)}°`);

    // Brakes
    sv('gi-s-bp-v', `${car.setupBrakePressure  ?? 0}%`);
    sb('gi-s-bp',    car.setupBrakePressure  ?? 0);
    sv('gi-s-bb-v', `${car.setupBrakeBias     ?? 0}%`);
    sb('gi-s-bb',    car.setupBrakeBias     ?? 0);
    sv('gi-s-eb-v', `${car.setupEngineBraking ?? 0}%`);
    sb('gi-s-eb',    car.setupEngineBraking ?? 0);

    // Tyre pressures
    sv('gi-s-pfl', (car.setupFrontLeftTyrePressure  ?? 0).toFixed(1));
    sv('gi-s-pfr', (car.setupFrontRightTyrePressure ?? 0).toFixed(1));
    sv('gi-s-prl', (car.setupRearLeftTyrePressure   ?? 0).toFixed(1));
    sv('gi-s-prr', (car.setupRearRightTyrePressure  ?? 0).toFixed(1));

    // Ballast & Fuel load
    sv('gi-s-ball-v', car.setupBallast  ?? 0);
    sv('gi-s-fuel-v', `${(car.setupFuelLoad ?? 0).toFixed(1)} kg`);
}

// -- Column 3: Strategy & Status --

function giRenderStrategyPanel(car, data) {
    const sv = (id, val) => { const e = document.getElementById(id); if(e) e.textContent = val; };
    const si = (id, html) => { const e = document.getElementById(id); if(e) e.innerHTML = html; };

    // Position & timing
    sv('gi-st-pos',      `P${car.position || '—'}`);
    sv('gi-st-gap',      giFormatDelta(car.deltaToLeaderInMS));
    sv('gi-st-interval', giFormatDelta(car.deltaToCarInFrontInMS));
    sv('gi-st-lapnum',   `LAP ${car.currentLapNum || '—'}`);
    sv('gi-st-pits2',    giPitStopCount[giSelectedCar] || 0);

    // DRS
    const drsOn = car.drs === 1 || car.drsAllowed === 1;
    si('gi-st-drs', `<span class="drs-badge ${drsOn ? 'drs-on' : 'drs-off'}">${drsOn ? 'ACTIVE' : 'OFF'}</span>`);

    // Fuel
    const fuelKg    = car.fuelInTank ?? 0;
    const fuelLaps  = car.fuelRemainingLaps ?? 0;
    sv('gi-st-fuel',     `${fuelKg.toFixed(1)} kg`);
    sv('gi-st-fuellaps', fuelLaps > 0 ? `${fuelLaps.toFixed(1)} laps` : '—');

    const totalLaps = data.totalLaps ?? 0;
    const lapNum    = car.currentLapNum ?? 0;
    const lapsLeft  = totalLaps > 0 ? totalLaps - lapNum : 0;
    const delta     = fuelLaps - lapsLeft;
    const deltaSign = delta >= 0 ? '+' : '';
    si('gi-st-fueldelta', `<span class="fuel-delta-badge ${delta >= 0 ? 'positive' : 'negative'}">${deltaSign}${delta.toFixed(2)} LAPS</span>`);

    // Tyre
    const cid = car.visualTyreCompound ?? 17;
    si('gi-st-compound', giCompoundBadge(cid));
    sv('gi-st-tyreage',  `${car.tyresAgeLaps || 0} laps`);

    // Tyre wear
    const wear = car.tyreWear || [0,0,0,0];
    const corners = ['fl','fr','rl','rr'];
    corners.forEach((c, i) => {
        const w = Math.round(wear[i] ?? 0);
        const pct = Math.min(100, w);
        const colour = giWearColour(pct);
        const wEl = document.getElementById(`gi-wear-${c}`);
        const bEl = document.getElementById(`gi-wf-${c}`);
        if (wEl) wEl.textContent = `${pct}%`;
        if (bEl) { bEl.style.width = `${pct}%`; bEl.style.background = colour; }
    });

    // Sector splits — last completed lap
    sv('gi-spl-s1', giFormatSector(car.lastLapSector1TimeInMS));
    sv('gi-spl-s2', giFormatSector(car.lastLapSector2TimeInMS));
    sv('gi-spl-s3', giFormatSector(car.lastLapSector3TimeInMS));

    const qpEl = document.getElementById('gi-qs-pits');
    if (qpEl) qpEl.textContent = giPitStopCount[giSelectedCar] || 0;
}

// ── Lap History Table ─────────────────────────────────────────────────────────

function giRenderLapHistoryTable(carIdx) {
    const tbody   = document.getElementById('gi-lap-tbody');
    const countEl = document.getElementById('gi-lap-count-badge');
    const laps    = lapHistoryStore[carIdx];
    const pb      = giPersonalBests[carIdx];

    if (countEl) countEl.textContent = `${laps.length} LAPS`;

    if (!tbody) return;

    if (laps.length === 0) {
        tbody.innerHTML = '<tr><td colspan="8" class="lap-history-empty">NO LAPS RECORDED YET — WAITING FOR LAP COMPLETION</td></tr>';
        return;
    }

    const bestOverallMS = laps.reduce((best, l) => (l.valid && l.totalMS > 0 && (!best || l.totalMS < best)) ? l.totalMS : best, 0);

    const rows = [...laps].reverse().map(lap => {
        const isBestOverall = lap.valid && lap.totalMS === bestOverallMS;
        const isPersonalBest = lap.valid && lap.totalMS === pb.lapMS;
        const rowClass = lap.valid ? (isBestOverall ? 'lap-best' : '') : 'lap-invalid';

        const lapTimeClass  = isBestOverall ? 'is-best' : (isPersonalBest ? 'is-purple' : '');
        const s1Class = lap.s1MS && lap.s1MS === pb.s1MS ? 'sector-best' : '';
        const s2Class = lap.s2MS && lap.s2MS === pb.s2MS ? 'sector-best' : '';
        const s3Class = lap.s3MS && lap.s3MS === pb.s3MS ? 'sector-best' : '';

        const pitBadge  = lap.hasPit ? '<span class="lap-pit-badge">PIT</span>' : '';
        const validBadge = lap.valid
            ? '<span class="lap-validity-badge valid">VALID</span>'
            : '<span class="lap-validity-badge invalid">DEL</span>';

        return `
        <tr class="${rowClass}">
            <td class="lap-num-cell">LAP ${lap.lapNum}</td>
            <td class="lap-time-cell ${lapTimeClass}">${giFormatTime(lap.totalMS)}</td>
            <td class="lap-sector-cell ${s1Class}">${giFormatSector(lap.s1MS)}</td>
            <td class="lap-sector-cell ${s2Class}">${giFormatSector(lap.s2MS)}</td>
            <td class="lap-sector-cell ${s3Class}">${giFormatSector(lap.s3MS)}</td>
            <td>${giCompoundBadge(lap.compound)}</td>
            <td>${validBadge}</td>
            <td style="text-align:right;">${pitBadge}</td>
        </tr>`;
    }).join('');

    tbody.innerHTML = rows;
}

// ── Auth / Logout ─────────────────────────────────────────────────────────────

function giLogout() {
    localStorage.removeItem('jwtToken');
    localStorage.removeItem('userRole');
    localStorage.removeItem('username');
    localStorage.removeItem('teamPin');
    localStorage.removeItem('pairedDriverName');
    localStorage.removeItem('pairedDriverId');
    window.location.href = '/index.html';
}

// ── Init ──────────────────────────────────────────────────────────────────────

document.addEventListener('DOMContentLoaded', () => {
    // Auth guard: Engineer dashboard stores auth in localStorage ('jwtToken' and 'userRole')
    const token = localStorage.getItem('jwtToken');
    const role = localStorage.getItem('userRole');
    if (!token) {
        window.location.href = '/index.html';
        return;
    }
    if (role === 'ROLE_DRIVER') {
        window.location.href = '/index.html';
        return;
    }

    // Attach high-priority event delegation for driver selection list (pointerdown for 0ms latency + click backup)
    const rowsContainer = document.getElementById('gi-driver-rows');
    if (rowsContainer) {
        rowsContainer.addEventListener('pointerdown', (e) => {
            const row = e.target.closest('.gi-driver-row');
            if (row) {
                const idx = parseInt(row.getAttribute('data-car-idx'), 10);
                if (!isNaN(idx)) {
                    giSelectDriver(idx);
                }
            }
        });
        rowsContainer.addEventListener('click', (e) => {
            const row = e.target.closest('.gi-driver-row');
            if (row) {
                const idx = parseInt(row.getAttribute('data-car-idx'), 10);
                if (!isNaN(idx)) {
                    giSelectDriver(idx);
                }
            }
        });
    }

    giInitWebSocket();
});
