// DOM Elements
const elements = {
    status: document.getElementById('connection-status'),
    
    // Inputs
    throttleBar: document.getElementById('throttle-bar'),
    throttleVal: document.getElementById('throttle-val'),
    brakeBar: document.getElementById('brake-bar'),
    brakeVal: document.getElementById('brake-val'),
    
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
    
    // Tyres
    tempFL: document.getElementById('temp-fl'),
    tempFR: document.getElementById('temp-fr'),
    tempRL: document.getElementById('temp-rl'),
    tempRR: document.getElementById('temp-rr'),
    
    wearFL: document.getElementById('wear-fl'),
    wearFR: document.getElementById('wear-fr'),
    wearRL: document.getElementById('wear-rl'),
    wearRR: document.getElementById('wear-rr')
};

// State handling
let stompClient = null;
const MAX_RPM = 13500; // Approx max RPM for F1

function connect() {
    const socket = new SockJS('/telemetry-websocket');
    stompClient = Stomp.over(socket);
    
    // Disable debug logging to avoid console spam at 30Hz
    stompClient.debug = null; 
    
    stompClient.connect({}, function (frame) {
        setConnected(true);
        console.log('Connected: ' + frame);
        
        stompClient.subscribe('/topic/live-telemetry', function (message) {
            const data = JSON.parse(message.body);
            updateDashboard(data);
        });

        stompClient.subscribe('/topic/live-alerts', function (message) {
            const alertData = JSON.parse(message.body);
            showToast(alertData);
        });

        stompClient.subscribe('/topic/raw-packets', function (message) {
            const terminal = document.getElementById('raw-terminal');
            if (terminal && document.getElementById('raw-tab').style.display === 'block') {
                const pre = document.createElement('div');
                pre.style.borderBottom = '1px solid #30363d';
                pre.style.paddingBottom = '10px';
                pre.style.marginBottom = '10px';
                pre.textContent = message.body;
                terminal.appendChild(pre);
                
                // Auto-scroll
                terminal.scrollTop = terminal.scrollHeight;
                
                // Limit to last 50 entries to avoid crashing the browser
                if (terminal.childNodes.length > 50) {
                    terminal.removeChild(terminal.firstChild);
                }
            }
        });
    }, function(error) {
        setConnected(false);
        console.error("STOMP error", error);
        // Attempt to reconnect after 3 seconds
        setTimeout(connect, 3000);
    });
}

function showToast(alertData) {
    const container = document.getElementById('toast-container');
    const toast = document.createElement('div');
    toast.className = `toast ${alertData.severity.toLowerCase()}`;
    toast.innerHTML = `<strong>${alertData.type}</strong><br/>${alertData.message}`;
    
    container.appendChild(toast);
    
    // Remove toast after 5 seconds
    setTimeout(() => {
        toast.remove();
    }, 5000);
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

    // 3. Update Race Status
    elements.lapNum.textContent = playerCar.currentLapNum;
    elements.position.textContent = `P${playerCar.position}`;
    elements.fuel.textContent = `${playerCar.fuelInTank.toFixed(1)}L`;
    
    // Assuming ERS max is ~4,000,000 Joules for F1 23/24/25
    const ersPercent = Math.min(100, (playerCar.ersStoreEnergy / 4000000) * 100);
    elements.ers.textContent = `${Math.round(ersPercent)}%`;

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
}

// Start connection when page loads
window.onload = connect;
