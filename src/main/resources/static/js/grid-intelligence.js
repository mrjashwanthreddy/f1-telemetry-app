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
            // Only render analysis panel if that tab is active
            if (giActiveBattleTab === 'analysis') {
                giRenderDetailPanel(giSelectedCar, giLastData.cars[giSelectedCar], giLastData);
            }
            // Battle mode rendering when battle tab is active
            if (giActiveBattleTab === 'battle') {
                giRenderBattleMode(giLastData);
            }
        }
    }

    // 2. Standings & Driver List (4 Hz = every 250ms) — stable in-place DOM, zero click loss, zero lag
    if (timestamp - giLastDriverListRender >= 250) {
        giLastDriverListRender = timestamp;
        giRenderDriverList(giLastData);
        // Also refresh rival dropdown if battle tab is open
        if (giActiveBattleTab === 'battle') {
            giUpdateRivalDropdown(giLastData);
        }
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

// ═══════════════════════════════════════════════════════════════════════════
//  BATTLE MODE — Race Engineer Comparison Engine
// ═══════════════════════════════════════════════════════════════════════════

// ── Battle State ─────────────────────────────────────────────────────────────

let giBattleRivalIdx      = -1;
let giActiveBattleTab     = 'analysis'; // 'analysis' | 'battle'
let giInsightLastUpdate   = 0;

/**
 * Per-lap sector history for all 22 cars.
 * giBattleLapData[carIdx] = [{lapNum, s1MS, s2MS, s3MS, totalMS, compound}]
 */
const giBattleLapData = Array.from({length: 22}, () => []);

/**
 * Gap history between my car and rival over last laps.
 * giBattleGapHistory = [{lapNum, gapMs, isLeading}]
 */
let giBattleGapHistory = [];

// ── Tab Switcher ──────────────────────────────────────────────────────────────

function giSwitchTab(tab) {
    giActiveBattleTab = tab;

    const analysisBtn = document.getElementById('gi-tab-analysis-btn');
    const battleBtn   = document.getElementById('gi-tab-battle-btn');
    const analysisTab = document.getElementById('gi-tab-analysis');
    const battleTab   = document.getElementById('gi-tab-battle');
    const rivalInfo   = document.getElementById('gi-battle-rival-info');

    if (analysisBtn) analysisBtn.classList.toggle('active', tab === 'analysis');
    if (battleBtn)   battleBtn.classList.toggle('active', tab === 'battle');

    if (analysisTab) {
        analysisTab.classList.toggle('gi-tab-content-active', tab === 'analysis');
        analysisTab.style.display = tab === 'analysis' ? 'flex' : 'none';
    }
    if (battleTab) {
        battleTab.classList.toggle('gi-tab-content-active', tab === 'battle');
        battleTab.style.display = tab === 'battle' ? 'flex' : 'none';
    }

    if (rivalInfo) rivalInfo.style.display = tab === 'battle' && giBattleRivalIdx >= 0 ? 'flex' : 'none';

    if (tab === 'battle' && giLastData) {
        giUpdateRivalDropdown(giLastData);
        if (giBattleRivalIdx < 0) giAutoSelectRival(giLastData);
        giRenderBattleMode(giLastData);
    }
}

// ── Rival Dropdown Population ─────────────────────────────────────────────────

function giUpdateRivalDropdown(data) {
    const sel = document.getElementById('gi-rival-select');
    if (!sel || !data || !data.cars) return;

    const myIdx  = giGetConnectedDriverIdx(data);
    const cars   = data.cars;

    const activeCars = [];
    for (let i = 0; i < 22; i++) {
        const car = cars[i];
        if (!car || i === myIdx) continue;
        if (car.position > 0 && car.position <= 22) {
            activeCars.push({ idx: i, car });
        }
    }
    activeCars.sort((a, b) => (a.car.position || 99) - (b.car.position || 99));

    // Rebuild options only if count changed
    const expectedCount = activeCars.length + 1; // +1 for placeholder
    if (sel.options.length === expectedCount) return;

    sel.innerHTML = '<option value="-1">— SELECT RIVAL —</option>';
    activeCars.forEach(({idx, car}) => {
        const name = (car.name || `DRIVER ${idx+1}`).toUpperCase();
        const pos  = `P${car.position || '?'}`;
        const opt  = document.createElement('option');
        opt.value = idx;
        opt.textContent = `${pos} ${name}`;
        if (idx === giBattleRivalIdx) opt.selected = true;
        sel.appendChild(opt);
    });
}

/**
 * Auto-select the car directly in front of my driver as the default rival.
 */
function giAutoSelectRival(data) {
    if (!data || !data.cars) return;
    const myIdx  = giGetConnectedDriverIdx(data);
    const myCar  = data.cars[myIdx];
    if (!myCar) return;

    const myPos = myCar.position ?? 99;
    if (myPos <= 1) {
        // I am leader — rival is P2
        const p2Idx = data.cars.findIndex((c, i) => c && i !== myIdx && c.position === 2);
        if (p2Idx >= 0) { giSetBattleRival(p2Idx); return; }
    } else {
        // rival is car directly ahead (myPos - 1)
        const aheadIdx = data.cars.findIndex((c, i) => c && i !== myIdx && c.position === myPos - 1);
        if (aheadIdx >= 0) { giSetBattleRival(aheadIdx); return; }
    }
}

function giSetBattleRival(carIdx) {
    giBattleRivalIdx = carIdx;
    giBattleGapHistory = []; // Reset gap history when rival changes

    // Update badge in tab bar
    const badge = document.getElementById('gi-battle-rival-name-badge');
    const info  = document.getElementById('gi-battle-rival-info');
    if (giLastData && giLastData.cars && giLastData.cars[carIdx]) {
        const name = (giLastData.cars[carIdx].name || `DRIVER ${carIdx+1}`).toUpperCase();
        if (badge) badge.textContent = name;
        if (info)  info.style.display = giActiveBattleTab === 'battle' ? 'flex' : 'none';
    }

    // Sync dropdown
    const sel = document.getElementById('gi-rival-select');
    if (sel) sel.value = carIdx;

    // Immediately re-render
    if (giLastData) giRenderBattleMode(giLastData);
}

// ── Lap Data Accumulation for Battle (sector history per car) ─────────────────

function giAccumulateBattleLapData(carIdx, car) {
    if (!car) return;
    const newLap = car.currentLapNum ?? 0;
    const store  = giBattleLapData[carIdx];

    // Avoid duplicates
    if (newLap > 1 && store.length === 0 && car.lastLapTimeInMS > 0) {
        const prevLap = Math.max(1, newLap - 1);
        if (!store.some(l => l.lapNum === prevLap)) {
            store.push({
                lapNum: prevLap,
                totalMS: car.lastLapTimeInMS ?? 0,
                s1MS: car.lastLapSector1TimeInMS ?? 0,
                s2MS: car.lastLapSector2TimeInMS ?? 0,
                s3MS: car.lastLapSector3TimeInMS ?? 0,
                compound: car.visualTyreCompound ?? 17
            });
        }
    }
}

// ── Master Battle Mode Render ─────────────────────────────────────────────────

function giRenderBattleMode(data) {
    if (!data || !data.cars) return;

    const myIdx    = giGetConnectedDriverIdx(data);
    const myCar    = data.cars[myIdx];
    const rivalIdx = giBattleRivalIdx;
    const rivalCar = rivalIdx >= 0 ? data.cars[rivalIdx] : null;

    // Update MY DRIVER card
    if (myCar) {
        const myName = (myCar.name || 'MY DRIVER').toUpperCase();
        const myPos  = `P${myCar.position || '?'}`;
        const myNameEl = document.getElementById('gi-battle-my-name');
        const myPosEl  = document.getElementById('gi-battle-my-pos');
        if (myNameEl) myNameEl.textContent = myName;
        if (myPosEl)  myPosEl.textContent  = myPos;
    }

    // Update RIVAL card
    if (rivalCar) {
        const rivalName = (rivalCar.name || `RIVAL ${rivalIdx+1}`).toUpperCase();
        const rivalPos  = `P${rivalCar.position || '?'}`;
        const rnEl = document.getElementById('gi-battle-rival-name');
        const rpEl = document.getElementById('gi-battle-rival-pos');
        if (rnEl) rnEl.textContent = rivalName;
        if (rpEl) rpEl.textContent = rivalPos;
    }

    // Render sub-sections
    giRenderBattleGapTrend(myIdx, rivalIdx, data);
    giRenderLapDeltaTrend(myIdx, rivalIdx);
    giRenderSectorComparison(myCar, rivalCar, myIdx, rivalIdx);
    giRenderBattleERSPanel(myCar, rivalCar);

    // Insights: throttled to every 3 seconds
    const now = Date.now();
    if (now - giInsightLastUpdate > 3000 && myCar && rivalCar) {
        giInsightLastUpdate = now;
        giGenerateInsights(myCar, rivalCar, myIdx, rivalIdx, data);
    }
}

// ── Gap Trend (live interval) ─────────────────────────────────────────────────

function giRenderBattleGapTrend(myIdx, rivalIdx, data) {
    if (rivalIdx < 0 || !data.cars) return;

    const myCar    = data.cars[myIdx];
    const rivalCar = data.cars[rivalIdx];
    if (!myCar || !rivalCar) return;

    const gapValEl      = document.getElementById('gi-battle-gap-val');
    const gapArrowEl    = document.getElementById('gi-battle-gap-arrow');
    const gapTxtEl      = document.getElementById('gi-battle-gap-trend-txt');
    const gapSublabelEl = document.getElementById('gi-battle-gap-sublabel');

    const myPos     = myCar.position    ?? 99;
    const rivalPos  = rivalCar.position ?? 99;
    const isLeading = myPos < rivalPos;

    // Calculate absolute gap between the two cars in milliseconds
    let gapMs = 0;
    if (myPos === rivalPos) {
        gapMs = 0;
    } else {
        const myLeadDelta = myCar.deltaToLeaderInMS    ?? 0;
        const rvLeadDelta = rivalCar.deltaToLeaderInMS ?? 0;

        if (myLeadDelta > 0 || rvLeadDelta > 0) {
            gapMs = Math.abs(myLeadDelta - rvLeadDelta);
        } else {
            // Fallback to deltaToCarInFront
            gapMs = rivalPos > myPos
                ? (rivalCar.deltaToCarInFrontInMS ?? 0)
                : (myCar.deltaToCarInFrontInMS ?? 0);
        }
    }

    const gapSec = gapMs / 1000;

    // Display formatted gap with clear situational context
    let gapStr = '—';
    if (gapMs > 0) {
        if (isLeading) {
            gapStr = `+${gapSec.toFixed(3)}s LEAD`;
        } else {
            gapStr = `+${gapSec.toFixed(3)}s BEHIND`;
        }
    } else if (myPos === rivalPos) {
        gapStr = '0.000s TIED';
    }

    if (gapSublabelEl) {
        gapSublabelEl.textContent = isLeading ? 'LEAD OVER RIVAL' : 'INTERVAL TO RIVAL';
    }

    // Record gap history per-lap (store gap as positive distance)
    const myLap = myCar.currentLapNum ?? 0;
    if (giBattleGapHistory.length === 0 || giBattleGapHistory[giBattleGapHistory.length - 1].lapNum !== myLap) {
        if (gapMs > 0) {
            giBattleGapHistory.push({ lapNum: myLap, gapMs: gapMs, isLeading: isLeading });
            if (giBattleGapHistory.length > 10) giBattleGapHistory.shift();
        }
    }

    // Trend: compare earliest vs latest gap entries (last 3 laps)
    let trendClass = '';
    let trendTxt   = 'STABLE';
    let arrowChar  = '→';

    if (giBattleGapHistory.length >= 2) {
        const first = giBattleGapHistory[Math.max(0, giBattleGapHistory.length - 3)];
        const last  = giBattleGapHistory[giBattleGapHistory.length - 1];
        const gapChange = last.gapMs - first.gapMs; // > 0 means distance between cars grew

        if (isLeading) {
            // My driver is ahead (e.g. Hamilton P1 vs Russell P2)
            if (gapChange > 200) {
                // Gap grew: we are pulling away!
                trendClass = 'closing'; // green color in CSS
                trendTxt   = 'PULLING AWAY';
                arrowChar  = '▲';
            } else if (gapChange < -200) {
                // Gap shrunk: rival is closing in on us!
                trendClass = 'opening'; // red warning color in CSS
                trendTxt   = 'RIVAL CLOSING';
                arrowChar  = '▼';
            } else {
                trendTxt   = 'STABLE LEAD';
            }
        } else {
            // My driver is behind (chasing rival ahead)
            if (gapChange < -200) {
                // Gap shrunk: we are closing in on the rival ahead!
                trendClass = 'closing'; // green color in CSS
                trendTxt   = 'CLOSING IN';
                arrowChar  = '▲';
            } else if (gapChange > 200) {
                // Gap grew: rival ahead is pulling away!
                trendClass = 'opening'; // red warning color in CSS
                trendTxt   = 'GAP OPENING';
                arrowChar  = '▼';
            } else {
                trendTxt   = 'STABLE GAP';
            }
        }
    }

    if (gapValEl)  gapValEl.textContent  = gapStr;
    if (gapArrowEl) {
        gapArrowEl.textContent = arrowChar;
        gapArrowEl.className   = `gi-battle-gap-arrow ${trendClass}`;
    }
    if (gapTxtEl) {
        gapTxtEl.textContent = trendTxt;
        gapTxtEl.className   = `gi-battle-gap-trend-txt ${trendClass}`;
    }
}

// ── Lap Delta Trend Cells ─────────────────────────────────────────────────────

function giRenderLapDeltaTrend(myIdx, rivalIdx) {
    const container = document.getElementById('gi-battle-trend-cells');
    if (!container) return;

    const myLaps    = lapHistoryStore[myIdx]    || [];
    const rivalLaps = lapHistoryStore[rivalIdx] || [];

    if (rivalIdx < 0 || myLaps.length === 0 || rivalLaps.length === 0) {
        container.innerHTML = '<div class="gi-trend-empty">SELECT A RIVAL — WAITING FOR LAP DATA</div>';
        return;
    }

    // Match laps by lapNum, compute deltas for last 5 matching laps
    const deltas = [];
    const myMap = Object.fromEntries(myLaps.filter(l => l.valid && l.totalMS > 0).map(l => [l.lapNum, l.totalMS]));
    const rvMap = Object.fromEntries(rivalLaps.filter(l => l.valid && l.totalMS > 0).map(l => [l.lapNum, l.totalMS]));

    const sharedLaps = Object.keys(myMap).filter(k => rvMap[k]).map(Number).sort((a,b) => a-b);
    const last5 = sharedLaps.slice(-5);

    if (last5.length === 0) {
        container.innerHTML = '<div class="gi-trend-empty">WAITING FOR SHARED LAP DATA...</div>';
        return;
    }

    // Max delta for normalizing bar heights
    const rawDeltas = last5.map(lap => myMap[lap] - rvMap[lap]);
    const maxAbs = Math.max(1, ...rawDeltas.map(d => Math.abs(d)));

    const cells = last5.map((lapNum, i) => {
        const delta   = rawDeltas[i];
        const pct     = Math.min(100, Math.round(Math.abs(delta) / maxAbs * 100));
        const cls     = delta < 0 ? 'gain' : (delta > 0 ? 'loss' : 'even');
        const sign    = delta < 0 ? '▲' : (delta > 0 ? '▼' : '—');
        const deltaS  = delta === 0 ? '±0.000' : `${delta < 0 ? '' : '+'}${(delta/1000).toFixed(3)}s`;

        return `
        <div class="gi-trend-cell">
            <div class="gi-trend-cell-label">L${lapNum}</div>
            <div class="gi-trend-cell-bar-wrap">
                <div class="gi-trend-cell-bar-fill ${cls}" style="height:${pct}%"></div>
            </div>
            <div class="gi-trend-cell-delta ${cls}">${sign} ${Math.abs(delta/1000).toFixed(3)}s</div>
        </div>`;
    });

    container.innerHTML = cells.join('');
}

// ── Sector Comparison ─────────────────────────────────────────────────────────

function giRenderSectorComparison(myCar, rivalCar, myIdx, rivalIdx) {
    if (!myCar) return;

    const sectors = ['s1', 's2', 's3'];
    const myFields = [
        myCar.lastLapSector1TimeInMS ?? 0,
        myCar.lastLapSector2TimeInMS ?? 0,
        myCar.lastLapSector3TimeInMS ?? 0
    ];
    const rvFields = rivalCar ? [
        rivalCar.lastLapSector1TimeInMS ?? 0,
        rivalCar.lastLapSector2TimeInMS ?? 0,
        rivalCar.lastLapSector3TimeInMS ?? 0
    ] : [0, 0, 0];

    sectors.forEach((s, i) => {
        const myMs   = myFields[i];
        const rvMs   = rvFields[i];
        const delta  = myMs > 0 && rvMs > 0 ? myMs - rvMs : null;

        const myEl    = document.getElementById(`gi-bs-my-${s}`);
        const rvEl    = document.getElementById(`gi-bs-rival-${s}`);
        const delEl   = document.getElementById(`gi-bs-delta-${s}`);
        const barEl   = document.getElementById(`gi-bs-bar-${s}`);
        const trendEl = document.getElementById(`gi-bs-trend-${s}`);
        const cellEl  = document.getElementById(`gi-bs-${s}`);

        if (myEl) myEl.textContent = myMs > 0 ? (myMs/1000).toFixed(3) : '—';
        if (rvEl) rvEl.textContent = rvMs > 0 ? (rvMs/1000).toFixed(3) : '—';

        if (delta !== null) {
            const sign  = delta < 0 ? '▲ ' : (delta > 0 ? '▼ ' : '');
            const abs   = Math.abs(delta/1000).toFixed(3);
            const cls   = delta < 0 ? 'gain' : (delta > 0 ? 'loss' : 'even');

            if (delEl) {
                delEl.textContent = `${sign}${abs}s`;
                delEl.className   = `gi-bs-delta ${cls}`;
            }

            // Bar shows proportional gain/loss (capped at 1s = 100%)
            const barPct = Math.min(100, Math.abs(delta) / 1000 * 100);
            if (barEl) {
                barEl.style.width      = `${barPct}%`;
                barEl.style.background = delta < 0
                    ? 'linear-gradient(90deg, #10b981, #059669)'
                    : 'linear-gradient(90deg, #ef4444, #dc2626)';
                barEl.style.left = delta < 0 ? '0' : 'auto';
                barEl.style.right = delta < 0 ? 'auto' : '0';
            }

            if (cellEl) {
                cellEl.classList.toggle('gaining', delta < 0);
                cellEl.classList.toggle('losing',  delta > 0);
            }

            // Sector trend from last 3 matching laps
            if (trendEl) {
                const trend = giComputeSectorTrend(myIdx, rivalIdx, i);
                trendEl.textContent  = trend.text;
                trendEl.style.color  = trend.color;
            }
        } else {
            if (delEl)  { delEl.textContent = 'Δ —'; delEl.className = 'gi-bs-delta even'; }
            if (barEl)  { barEl.style.width = '0%'; }
            if (trendEl){ trendEl.textContent = '—'; trendEl.style.color = '#94a3b8'; }
            if (cellEl) { cellEl.classList.remove('gaining', 'losing'); }
        }
    });
}

/**
 * Compute sector trend over last 3 laps (is my driver gaining or losing this sector?)
 * Returns {text, color}
 */
function giComputeSectorTrend(myIdx, rivalIdx, sectorIdx) {
    const myLaps    = lapHistoryStore[myIdx]    || [];
    const rivalLaps = lapHistoryStore[rivalIdx] || [];

    const sFields = ['s1MS', 's2MS', 's3MS'];
    const field   = sFields[sectorIdx];

    const myMap = Object.fromEntries(myLaps.filter(l => l.valid && l[field] > 0).map(l => [l.lapNum, l[field]]));
    const rvMap = Object.fromEntries(rivalLaps.filter(l => l.valid && l[field] > 0).map(l => [l.lapNum, l[field]]));

    const shared = Object.keys(myMap).filter(k => rvMap[k]).map(Number).sort((a,b) => a-b).slice(-3);
    if (shared.length < 2) return { text: 'NOT ENOUGH DATA', color: '#94a3b8' };

    const first = myMap[shared[0]] - rvMap[shared[0]];
    const last  = myMap[shared[shared.length-1]] - rvMap[shared[shared.length-1]];
    const diff  = last - first;

    if (diff < -50)  return { text: `▲ GAINING ${(Math.abs(diff)/1000).toFixed(3)}s/lap`, color: '#059669' };
    if (diff > 50)   return { text: `▼ LOSING ${(diff/1000).toFixed(3)}s/lap`, color: '#dc2626' };
    return { text: 'STABLE PACE', color: '#64748b' };
}

// ── ERS Battle Panel ──────────────────────────────────────────────────────────

function giRenderBattleERSPanel(myCar, rivalCar) {
    if (!myCar) return;

    const myErsJ   = myCar.ersStoreEnergy ?? 0;
    const myErsPct = Math.round(myErsJ / GI_ERS_MAX_JOULES * 100);
    const rvErsJ   = rivalCar ? (rivalCar.ersStoreEnergy ?? 0) : 0;
    const rvErsPct = Math.round(rvErsJ / GI_ERS_MAX_JOULES * 100);

    const myMode = myCar.ersDeployMode ?? 0;
    const rvMode = rivalCar ? (rivalCar.ersDeployMode ?? 0) : 0;

    const sv = (id, val) => { const e = document.getElementById(id); if(e) e.textContent = val; };
    const sn = (id, name, classes) => { const e = document.getElementById(id); if(e) { e.textContent = name; e.className = classes; } };

    sn('gi-batt-my-ers-mode',    GI_ERS_MODE_NAMES[myMode] ?? 'NONE', `ers-mode-badge ${GI_ERS_MODE_CLASSES[myMode] ?? 'mode-none'}`);
    sn('gi-batt-rival-ers-mode', GI_ERS_MODE_NAMES[rvMode] ?? 'NONE', `ers-mode-badge ${GI_ERS_MODE_CLASSES[rvMode] ?? 'mode-none'}`);

    const myBar = document.getElementById('gi-batt-my-ers-bar');
    const rvBar = document.getElementById('gi-batt-rival-ers-bar');
    if (myBar) myBar.style.width = `${myErsPct}%`;
    if (rvBar) rvBar.style.width = `${rvErsPct}%`;

    sv('gi-batt-my-ers-pct',   `${myErsPct}%`);
    sv('gi-batt-rival-ers-pct', `${rvErsPct}%`);

    // ERS Deployed
    const myDep  = (myCar.ersDeployedThisLap ?? 0) / 1_000_000;
    const rvDep  = rivalCar ? ((rivalCar.ersDeployedThisLap ?? 0) / 1_000_000) : 0;
    const depDiff = myDep - rvDep;

    sv('gi-batt-my-dep',    `${myDep.toFixed(2)} MJ`);
    sv('gi-batt-rival-dep', `${rvDep.toFixed(2)} MJ`);

    const depDeltaEl = document.getElementById('gi-batt-dep-delta');
    if (depDeltaEl) {
        depDeltaEl.textContent = `Δ ${depDiff >= 0 ? '+' : ''}${depDiff.toFixed(2)} MJ`;
        depDeltaEl.style.color  = depDiff > 0 ? '#d97706' : (depDiff < 0 ? '#059669' : '#64748b');
    }

    // MGU-K
    const myMGUK = ((myCar.ersHarvestedThisLapMGUK ?? 0) / 1_000_000).toFixed(2);
    const rvMGUK = ((rivalCar?.ersHarvestedThisLapMGUK ?? 0) / 1_000_000).toFixed(2);
    sv('gi-batt-my-mguk',    `${myMGUK} MJ`);
    sv('gi-batt-rival-mguk', `${rvMGUK} MJ`);
}

// ── Engineer Insights Rule Engine ─────────────────────────────────────────────

function giGenerateInsights(myCar, rivalCar, myIdx, rivalIdx, data) {
    const feed = document.getElementById('gi-insight-feed');
    if (!feed || !myCar || !rivalCar) return;

    const insights = [];
    const myName   = (myCar.name    || 'DRIVER').toUpperCase().split(' ').pop();
    const rvName   = (rivalCar.name || 'RIVAL').toUpperCase().split(' ').pop();
    const totalLaps = data.totalLaps ?? 0;
    const myLap   = myCar.currentLapNum ?? 0;
    const lapsLeft = totalLaps > 0 ? totalLaps - myLap : 0;

    // ── SECTOR ANALYSIS ───────────────────────────────────────────────────────

    const sectors = [
        { key: 's1', label: 'S1', myMs: myCar.lastLapSector1TimeInMS ?? 0, rvMs: rivalCar.lastLapSector1TimeInMS ?? 0 },
        { key: 's2', label: 'S2', myMs: myCar.lastLapSector2TimeInMS ?? 0, rvMs: rivalCar.lastLapSector2TimeInMS ?? 0 },
        { key: 's3', label: 'S3', myMs: myCar.lastLapSector3TimeInMS ?? 0, rvMs: rivalCar.lastLapSector3TimeInMS ?? 0 }
    ];

    sectors.forEach(sec => {
        if (sec.myMs <= 0 || sec.rvMs <= 0) return;
        const delta = sec.myMs - sec.rvMs;
        const deltaS = (Math.abs(delta) / 1000).toFixed(3);

        if (delta < -300) {
            insights.push({
                priority: 'positive',
                icon: '🟢',
                tag: 'ADVANTAGE',
                text: `${myName} is ${deltaS}s faster through ${sec.label}`,
                sub: `Consistent attack — use this margin to pressure ${rvName} in next lap`
            });
        } else if (delta > 300) {
            insights.push({
                priority: 'high',
                icon: '🔴',
                tag: 'SECTOR LOSS',
                text: `Losing ${deltaS}s to ${rvName} in ${sec.label}`,
                sub: sec.key === 's1'
                    ? 'Check braking zones — late apex could recover time'
                    : sec.key === 's2'
                    ? 'Traction issue suspected — monitor rear wheel spin data'
                    : 'Look for DRS opportunity or earlier exit from final corner'
            });
        }
    });

    // ── ERS DEPLOYMENT ────────────────────────────────────────────────────────

    const myMode = myCar.ersDeployMode   ?? 0;
    const rvMode = rivalCar.ersDeployMode ?? 0;

    if (rvMode === 3 && myMode < 3) {
        insights.push({
            priority: 'critical',
            icon: '⚡',
            tag: 'CRITICAL',
            text: `${rvName} is on OVERTAKE ERS — ${myName} must respond immediately`,
            sub: 'Switch to OVERTAKE mode before DRS zone or risk losing position'
        });
    } else if (rvMode === 2 && myMode < 2) {
        insights.push({
            priority: 'high',
            icon: '⚡',
            tag: 'ERS GAP',
            text: `${rvName} using HOTLAP ERS — mode mismatch detected`,
            sub: 'Consider matching hotlap mode to maintain pace through DRS zones'
        });
    }

    // ERS Store gap
    const myErsJ  = myCar.ersStoreEnergy   ?? 0;
    const rvErsJ  = rivalCar.ersStoreEnergy ?? 0;
    const myPct   = Math.round(myErsJ  / GI_ERS_MAX_JOULES * 100);
    const rvPct   = Math.round(rvErsJ  / GI_ERS_MAX_JOULES * 100);
    const ersDiff = myPct - rvPct;

    if (ersDiff < -20) {
        insights.push({
            priority: 'high',
            icon: '🔋',
            tag: 'ERS DEFICIT',
            text: `ERS store ${Math.abs(ersDiff)}% lower than ${rvName}`,
            sub: 'Harvest more with MEDIUM mode for 2–3 laps before next attack zone'
        });
    } else if (ersDiff > 25) {
        insights.push({
            priority: 'positive',
            icon: '🔋',
            tag: 'ERS ADVANTAGE',
            text: `${myName} has ${ersDiff}% more ERS than ${rvName}`,
            sub: 'Deploy OVERTAKE mode at next DRS zone for attack opportunity'
        });
    }

    // ── TYRE ANALYSIS ─────────────────────────────────────────────────────────

    const myAge  = myCar.tyresAgeLaps   ?? 0;
    const rvAge  = rivalCar.tyresAgeLaps ?? 0;
    const ageDiff = myAge - rvAge;

    if (ageDiff > 10) {
        insights.push({
            priority: 'warning',
            icon: '🏎️',
            tag: 'TYRE CLIFF',
            text: `${myName}'s tyres are ${ageDiff} laps older than ${rvName}`,
            sub: lapsLeft > 0
                ? `Pit window: ${data.pitStopWindowIdealLap ? `Ideal L${data.pitStopWindowIdealLap}` : 'confirm strategy now'}`
                : 'Monitor degradation closely — manage pace'
        });
    } else if (ageDiff < -10) {
        insights.push({
            priority: 'positive',
            icon: '🏎️',
            tag: 'TYRE FRESH',
            text: `${myName} has ${Math.abs(ageDiff)} fresher laps of tyre life vs ${rvName}`,
            sub: 'Exploit tyre delta in final sector — rival may struggle on worn rubber'
        });
    }

    // Tyre wear asymmetry
    const myWear = myCar.tyreWear ?? [0,0,0,0];
    const maxWear = Math.max(...myWear);
    const minWear = Math.min(...myWear);
    if (maxWear - minWear > 15) {
        const corners = ['RL','RR','FL','FR'];
        const worstCorner = corners[myWear.indexOf(maxWear)];
        insights.push({
            priority: 'warning',
            icon: '⚠️',
            tag: 'WEAR IMBALANCE',
            text: `Tyre wear asymmetry: ${worstCorner} at ${Math.round(maxWear)}%`,
            sub: 'Check brake bias or consider cooling lap to rebalance thermal load'
        });
    }

    // ── FUEL STRATEGY ─────────────────────────────────────────────────────────

    const myFuelLaps = myCar.fuelRemainingLaps   ?? 0;
    const rvFuelLaps = rivalCar.fuelRemainingLaps ?? 0;
    const fuelDiff   = myFuelLaps - rvFuelLaps;

    if (fuelDiff < -1.5) {
        insights.push({
            priority: 'high',
            icon: '⛽',
            tag: 'FUEL DEFICIT',
            text: `${rvName} can run ${Math.abs(fuelDiff).toFixed(1)} laps longer before pitting`,
            sub: 'Rival has strategic undercut/overcut advantage — consider pitting earlier'
        });
    } else if (fuelDiff > 1.5) {
        insights.push({
            priority: 'info',
            icon: '⛽',
            tag: 'FUEL ADVANTAGE',
            text: `${myName} has ${fuelDiff.toFixed(1)} laps more fuel flexibility`,
            sub: 'Use overcut option: stay out longer when rival pits to gain track position'
        });
    }

    // ── GAP TREND ────────────────────────────────────────────────────────────

    if (giBattleGapHistory.length >= 2) {
        const first = giBattleGapHistory[Math.max(0, giBattleGapHistory.length - 3)];
        const last  = giBattleGapHistory[giBattleGapHistory.length - 1];
        const lapsChecked = (last.lapNum - first.lapNum) || 1;
        const gapChange = last.gapMs - first.gapMs; // > 0 means distance between cars grew
        const diffS = (Math.abs(gapChange) / 1000).toFixed(3);
        const myPos = myCar.position ?? 99;
        const rivalPos = rivalCar.position ?? 99;
        const isLeading = myPos < rivalPos;

        if (isLeading) {
            // We are ahead (e.g. Hamilton P1 vs Russell P2)
            if (gapChange > 300) {
                // We are pulling away from rival
                insights.push({
                    priority: 'positive',
                    icon: '📈',
                    tag: 'PULLING AWAY',
                    text: `Lead over ${rvName} extended by ${diffS}s over last ${lapsChecked > 1 ? lapsChecked + ' laps' : 'lap'}`,
                    sub: `Pace advantage verified — holding comfortable +${(last.gapMs / 1000).toFixed(3)}s lead margin`
                });
            } else if (gapChange < -300) {
                // Rival is catching up to us
                const lapsToCatch = last.gapMs > 1000 ? Math.ceil(last.gapMs / Math.abs(gapChange / (lapsChecked || 1))) : 1;
                insights.push({
                    priority: 'high',
                    icon: '⚠️',
                    tag: 'RIVAL CLOSING',
                    text: `${rvName} is closing the gap by ${diffS}s over last ${lapsChecked > 1 ? lapsChecked + ' laps' : 'lap'}`,
                    sub: `Lead margin reduced to ${(last.gapMs / 1000).toFixed(3)}s — prepare to defend DRS threat in ~${lapsToCatch} laps`
                });
            }
        } else {
            // We are chasing (e.g. P2 chasing P1)
            if (gapChange < -300) {
                // We are closing in on rival ahead
                const lapsToDRS = last.gapMs > 1000 ? Math.ceil((last.gapMs - 1000) / Math.abs(gapChange / (lapsChecked || 1))) : 1;
                insights.push({
                    priority: 'positive',
                    icon: '🎯',
                    tag: 'CLOSING IN',
                    text: `Gap to ${rvName} closing by ${diffS}s over last ${lapsChecked > 1 ? lapsChecked + ' laps' : 'lap'}`,
                    sub: `Current deficit: ${(last.gapMs / 1000).toFixed(3)}s — estimated DRS range in ~${Math.max(1, lapsToDRS)} laps`
                });
            } else if (gapChange > 300) {
                // Rival ahead is pulling away from us
                insights.push({
                    priority: 'high',
                    icon: '📉',
                    tag: 'FALLING BACK',
                    text: `Gap to ${rvName} growing — ${diffS}s lost over last ${lapsChecked > 1 ? lapsChecked + ' laps' : 'lap'}`,
                    sub: 'Pace deficit detected — check tyre degradation or deploy ERS to arrest the slide'
                });
            }
        }
    }

    // ── DRS ───────────────────────────────────────────────────────────────────

    const drsActivation = myCar.drsActivationDistance ?? 0;
    if (drsActivation > 0 && drsActivation < 200) {
        insights.push({
            priority: 'critical',
            icon: '🟣',
            tag: 'DRS READY',
            text: `DRS activation in ${drsActivation}m — attack opportunity imminent`,
            sub: `Deploy OVERTAKE ERS NOW for maximum speed delta against ${rvName}`
        });
    }

    // ── Render Insight Cards ──────────────────────────────────────────────────

    if (insights.length === 0) {
        feed.innerHTML = `<div class="gi-insight-card info">
            <div class="gi-insight-icon">📊</div>
            <div class="gi-insight-body">
                <div class="gi-insight-text">Pace appears evenly matched — monitoring live telemetry</div>
                <div class="gi-insight-subtext">No critical deltas detected this lap. Data will update each lap.</div>
            </div>
        </div>`;
        return;
    }

    // Priority order: critical → high → warning → positive → info
    const ORDER = { critical: 0, high: 1, warning: 2, positive: 3, info: 4 };
    insights.sort((a, b) => (ORDER[a.priority] ?? 9) - (ORDER[b.priority] ?? 9));

    feed.innerHTML = insights.slice(0, 7).map(ins => `
        <div class="gi-insight-card ${ins.priority}">
            <div class="gi-insight-icon">${ins.icon}</div>
            <div class="gi-insight-body">
                <span class="gi-insight-priority-tag ${ins.priority}">${ins.tag}</span>
                <div class="gi-insight-text">${ins.text}</div>
                ${ins.sub ? `<div class="gi-insight-subtext">${ins.sub}</div>` : ''}
            </div>
        </div>`
    ).join('');
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

    // Initialize tab: set analysis as default, hide battle
    const analysisTab = document.getElementById('gi-tab-analysis');
    const battleTab   = document.getElementById('gi-tab-battle');
    if (analysisTab) analysisTab.style.display = 'flex';
    if (battleTab)   battleTab.style.display   = 'none';

    giInitWebSocket();
});
