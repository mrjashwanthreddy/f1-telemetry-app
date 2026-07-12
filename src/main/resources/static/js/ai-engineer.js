/**
 * AI Race Engineer — Frontend Module (Phase 10)
 *
 * Handles:
 *  1. LAP_DEBRIEF WebSocket events → rich toast + TTS radio call
 *  2. VOICE_TRIGGER events → Web Speech API mic activation
 *  3. Session Debrief UI → full post-session analysis card
 *  4. Chat panel → session Q&A with voice support
 */

// ── TTS Engine (Web Speech API) ────────────────────────────────────────────────

let ttsEnabled = true;
let currentUtterance = null;

/**
 * Speaks text aloud in a British male voice — sounds like an F1 race engineer.
 * Cancels any currently speaking utterance so new messages always play.
 */
function engineerSpeak(text, urgent = false) {
    if (!ttsEnabled || !window.speechSynthesis) return;

    // Cancel in-progress speech so urgent messages cut through
    window.speechSynthesis.cancel();

    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = 'en-GB';
    utterance.rate = urgent ? 1.15 : 1.05;    // Slightly faster for urgency
    utterance.pitch = 0.88;                    // Lower pitch = authoritative
    utterance.volume = 0.9;

    // Pick best available English voice — prefer British male
    const voices = window.speechSynthesis.getVoices();
    const preferred = voices.find(v => v.lang === 'en-GB' && v.name.toLowerCase().includes('male'))
                   || voices.find(v => v.lang === 'en-GB')
                   || voices.find(v => v.lang.startsWith('en'));
    if (preferred) utterance.voice = preferred;

    currentUtterance = utterance;
    window.speechSynthesis.speak(utterance);
}

function toggleTts() {
    ttsEnabled = !ttsEnabled;
    const btn = document.getElementById('tts-toggle-btn');
    if (btn) {
        btn.textContent = ttsEnabled ? '🔊 Voice ON' : '🔇 Voice OFF';
        btn.classList.toggle('active', ttsEnabled);
    }
    if (!ttsEnabled) window.speechSynthesis.cancel();
}

// ── LAP DEBRIEF Handler ────────────────────────────────────────────────────────

/**
 * Called when a LAP_DEBRIEF WebSocket event arrives.
 * Renders a rich toast card and speaks the AI verdict aloud.
 */
function handleLapDebrief(alertData) {
    let detail = {};
    try { detail = JSON.parse(alertData.detail || '{}'); } catch (e) {}

    const severity = (alertData.severity || 'info').toLowerCase();
    const isPB = detail.isPB;
    const lapTime = detail.lapTime || '';
    const delta = detail.delta || '';
    const aiVerdict = detail.aiVerdict || '';
    const s1 = detail.s1 || '—';
    const s2 = detail.s2 || '—';
    const s3 = detail.s3 || '—';
    const s1d = detail.s1Delta ? `<span class="sector-delta ${detail.s1Delta.startsWith('+') ? 'slower' : 'faster'}">${detail.s1Delta}</span>` : '';
    const s2d = detail.s2Delta ? `<span class="sector-delta ${detail.s2Delta.startsWith('+') ? 'slower' : 'faster'}">${detail.s2Delta}</span>` : '';
    const s3d = detail.s3Delta ? `<span class="sector-delta ${detail.s3Delta.startsWith('+') ? 'slower' : 'faster'}">${detail.s3Delta}</span>` : '';

    // Build the rich lap debrief toast
    const container = document.getElementById('toast-container');
    const toast = document.createElement('div');
    toast.className = `toast ${severity} lap-debrief-toast`;
    toast.innerHTML = `
        <div class="lap-debrief-header">
            <span class="lap-debrief-icon">${isPB ? '🏆' : severity === 'info' ? '🟢' : '🔴'}</span>
            <span class="lap-debrief-title">${alertData.message}</span>
            ${delta ? `<span class="lap-debrief-delta ${severity}">${delta}</span>` : ''}
        </div>
        <div class="lap-debrief-sectors">
            <div class="sector-cell"><span class="sector-label">S1</span>${s1}${s1d}</div>
            <div class="sector-cell"><span class="sector-label">S2</span>${s2}${s2d}</div>
            <div class="sector-cell"><span class="sector-label">S3</span>${s3}${s3d}</div>
        </div>
        ${aiVerdict ? `<div class="lap-debrief-verdict">🎙️ ${aiVerdict}</div>` : ''}
    `;

    container.appendChild(toast);

    // Dismiss after 10s (longer than normal toasts — more content to read)
    setTimeout(() => {
        toast.style.animation = 'slideOut 0.35s ease-in forwards';
        setTimeout(() => toast.remove(), 350);
    }, 10000);

    // Also log to event feed
    if (window.appendEventLog) appendEventLog(alertData);

    // Speak the AI verdict via TTS — the "radio call"
    if (aiVerdict) {
        const lapNum = detail.lapNum || '';
        const ttsText = `Lap ${lapNum} complete. ${aiVerdict}`;
        engineerSpeak(ttsText, isPB);
    }
}

// ── VOICE QUERY (Web Speech API STT) ──────────────────────────────────────────

let recognition = null;
let voiceQueryActive = false;

/**
 * Called by GlobalHotkeyService (ScrollLock) via WebSocket VOICE_TRIGGER event.
 * Activates the browser microphone for a voice question.
 */
function activateVoiceQuery() {
    if (!('webkitSpeechRecognition' in window) && !('SpeechRecognition' in window)) {
        engineerSpeak("Voice recognition is not supported in this browser.");
        return;
    }

    if (voiceQueryActive) {
        recognition?.stop();
        return;
    }

    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    recognition = new SpeechRecognition();
    recognition.lang = 'en-US';
    recognition.interimResults = false;
    recognition.maxAlternatives = 1;

    voiceQueryActive = true;
    showVoiceIndicator(true);
    engineerSpeak("Go ahead.");

    recognition.onresult = (event) => {
        const question = event.results[0][0].transcript;
        console.log('[Voice Query]', question);
        handleVoiceQuestion(question);
    };

    recognition.onerror = (e) => {
        console.error('[Voice Query] Error:', e.error);
        engineerSpeak("Sorry, I didn't catch that.");
    };

    recognition.onend = () => {
        voiceQueryActive = false;
        showVoiceIndicator(false);
    };

    recognition.start();
}

/**
 * Processes a voice question — sends to AI chat endpoint with current session context.
 */
async function handleVoiceQuestion(question) {
    const token = localStorage.getItem('jwtToken');
    const sessionId = window.currentChatSessionId; // Set when chat panel opens

    if (!sessionId) {
        // No session selected — answer from live state context
        engineerSpeak("Please select a session in the history tab first, then I can answer questions about it.");
        return;
    }

    try {
        const response = await fetch('/api/ai/chat', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify({ question, sessionId })
        });

        if (response.ok) {
            const data = await response.json();
            engineerSpeak(data.answer, false);
            // Also show in chat panel if open
            if (window.appendChatMessage) {
                appendChatMessage('driver', question);
                appendChatMessage('engineer', data.answer);
            }
        } else {
            engineerSpeak("I couldn't get an answer right now. Try again.");
        }
    } catch (e) {
        console.error('[Voice Query] Fetch error:', e);
        engineerSpeak("Communication error. Check your connection.");
    }
}

function showVoiceIndicator(active) {
    let indicator = document.getElementById('voice-indicator');
    if (!indicator) {
        indicator = document.createElement('div');
        indicator.id = 'voice-indicator';
        indicator.innerHTML = '🎙️ Listening...';
        document.body.appendChild(indicator);
    }
    indicator.style.display = active ? 'flex' : 'none';
}

// ── SESSION DEBRIEF UI ─────────────────────────────────────────────────────────

/**
 * Fetches and renders a full session debrief.
 * Called from the history tab "AI Debrief" button.
 */
async function showSessionDebrief(sessionId) {
    const token = localStorage.getItem('jwtToken');
    const modal = document.getElementById('debrief-modal');
    const content = document.getElementById('debrief-content');

    if (!modal || !content) return;

    content.innerHTML = '<div class="debrief-loading"><div class="spinner"></div><span>Calling your engineer...</span></div>';
    modal.style.display = 'flex';

    try {
        const response = await fetch(`/api/ai/session-debrief/${sessionId}`, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${token}` }
        });

        if (!response.ok) {
            const err = await response.json();
            content.innerHTML = `<div class="debrief-error">⚠️ ${err.error || 'Failed to load debrief'}</div>`;
            return;
        }

        const data = await response.json();
        renderDebriefCard(data, content);

        // Speak a summary
        const firstLines = data.debrief.split('\n').slice(0, 3).join(' ');
        engineerSpeak(`Session debrief for ${data.track}. ${firstLines}`);

    } catch (e) {
        content.innerHTML = `<div class="debrief-error">⚠️ Could not reach AI service.</div>`;
    }
}

function renderDebriefCard(data, container) {
    // Format the markdown-style debrief text into HTML
    const formattedDebrief = data.debrief
        .replace(/\n\n/g, '</p><p>')
        .replace(/\n/g, '<br>')
        .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
        .replace(/^(\d+\.)/gm, '<span class="debrief-number">$1</span>');

    // Build lap time table from laps array
    const lapRows = (data.laps || []).map(lap => {
        const total = formatMs(lap.total);
        const s1 = formatMs(lap.sector1);
        const s2 = formatMs(lap.sector2);
        const s3 = formatMs(lap.sector3);
        const wear = lap.tyreWearFL > 0
            ? `FL:${lap.tyreWearFL.toFixed(1)}% FR:${lap.tyreWearFR.toFixed(1)}%`
            : '—';
        const fuel = lap.fuelRemaining > 0 ? `${lap.fuelRemaining.toFixed(2)}kg` : '—';
        return `<tr>
            <td>Lap ${lap.lapNumber}</td>
            <td class="lap-time-cell">${total}</td>
            <td>${s1}</td><td>${s2}</td><td>${s3}</td>
            <td class="wear-cell">${wear}</td>
            <td class="fuel-cell">${fuel}</td>
        </tr>`;
    }).join('');

    container.innerHTML = `
        <div class="debrief-card">
            <div class="debrief-header">
                <div class="debrief-track-info">
                    <h2>🏎️ ${data.track}</h2>
                    <span class="debrief-session-badge">${data.sessionType}</span>
                    <span class="debrief-lap-count">${data.lapCount} laps</span>
                </div>
                <button class="debrief-speak-btn" onclick="engineerSpeak(document.getElementById('debrief-text').innerText)">
                    🔊 Read Aloud
                </button>
            </div>

            <div class="debrief-lap-table-wrapper">
                <table class="debrief-lap-table">
                    <thead>
                        <tr><th>Lap</th><th>Time</th><th>S1</th><th>S2</th><th>S3</th><th>Tyre Wear</th><th>Fuel</th></tr>
                    </thead>
                    <tbody>${lapRows}</tbody>
                </table>
            </div>

            <div class="debrief-ai-section">
                <div class="debrief-ai-label">🤖 Engineer Analysis</div>
                <div class="debrief-text" id="debrief-text"><p>${formattedDebrief}</p></div>
            </div>

            ${data.tyreContext ? `
            <div class="debrief-tyre-section">
                <div class="debrief-ai-label">🔬 Tyre & Fuel Detail</div>
                <pre class="tyre-context-text">${data.tyreContext}</pre>
            </div>` : ''}

            ${data.cornerAnalysis ? `
            <div class="debrief-corner-section" style="margin-top: 15px;">
                <div class="debrief-ai-label">🏁 Corner Telemetry (Best vs Slowest Lap Deltas)</div>
                <pre class="corner-context-text">${data.cornerAnalysis}</pre>
            </div>` : ''}
        </div>
    `;
}

// ── CHAT PANEL ─────────────────────────────────────────────────────────────────

window.currentChatSessionId = null;

/**
 * Opens the chat panel for a specific session.
 */
function openChatPanel(sessionId, trackName, sessionType) {
    window.currentChatSessionId = sessionId;

    const panel = document.getElementById('chat-panel');
    const chatHeader = document.getElementById('chat-session-label');
    const chatMessages = document.getElementById('chat-messages');

    if (!panel) return;

    if (chatHeader) chatHeader.textContent = `${trackName} — ${sessionType}`;
    if (chatMessages) chatMessages.innerHTML = `
        <div class="chat-bubble engineer-bubble">
            <span class="chat-avatar">🏎️</span>
            <div class="chat-text">I'm reviewing your <strong>${trackName}</strong> ${sessionType}. 
            Ask me anything — lap times, sectors, tyre wear, what to improve next session.</div>
        </div>
    `;

    panel.classList.add('open');
}

function closeChatPanel() {
    const panel = document.getElementById('chat-panel');
    if (panel) panel.classList.remove('open');
    window.currentChatSessionId = null;
}

async function sendChatMessage() {
    const input = document.getElementById('chat-input');
    if (!input || !input.value.trim()) return;

    const question = input.value.trim();
    input.value = '';

    appendChatMessage('driver', question);

    const token = localStorage.getItem('jwtToken');
    const sessionId = window.currentChatSessionId;
    if (!sessionId) {
        appendChatMessage('engineer', 'No session selected. Go to History and click "Chat" on a session.');
        return;
    }

    // Typing indicator
    const typingId = 'typing-' + Date.now();
    const chatMessages = document.getElementById('chat-messages');
    chatMessages.insertAdjacentHTML('beforeend', `
        <div id="${typingId}" class="chat-bubble engineer-bubble typing-indicator">
            <span class="chat-avatar">🏎️</span>
            <div class="chat-text"><span class="dot"></span><span class="dot"></span><span class="dot"></span></div>
        </div>
    `);
    chatMessages.scrollTop = chatMessages.scrollHeight;

    try {
        const response = await fetch('/api/ai/chat', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify({ question, sessionId })
        });

        document.getElementById(typingId)?.remove();

        if (response.ok) {
            const data = await response.json();
            appendChatMessage('engineer', data.answer);
            engineerSpeak(data.answer);
        } else {
            appendChatMessage('engineer', '⚠️ Could not reach AI. Check your API key configuration.');
        }
    } catch (e) {
        document.getElementById(typingId)?.remove();
        appendChatMessage('engineer', '⚠️ Network error reaching AI service.');
    }
}

function appendChatMessage(role, text) {
    const chatMessages = document.getElementById('chat-messages');
    if (!chatMessages) return;

    const isEngineer = role === 'engineer';
    const div = document.createElement('div');
    div.className = `chat-bubble ${isEngineer ? 'engineer-bubble' : 'driver-bubble'}`;
    div.innerHTML = isEngineer
        ? `<span class="chat-avatar">🏎️</span><div class="chat-text">${text}</div>`
        : `<div class="chat-text">${text}</div><span class="chat-avatar">👤</span>`;

    chatMessages.appendChild(div);
    chatMessages.scrollTop = chatMessages.scrollHeight;
}

// Chat voice input button
function startChatVoiceInput() {
    if (!('webkitSpeechRecognition' in window) && !('SpeechRecognition' in window)) {
        appendChatMessage('engineer', 'Voice input is not supported in this browser.');
        return;
    }

    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    const rec = new SpeechRecognition();
    rec.lang = 'en-US';
    rec.interimResults = false;
    rec.maxAlternatives = 1;

    const micBtn = document.getElementById('chat-mic-btn');
    if (micBtn) micBtn.classList.add('listening');

    rec.onresult = (e) => {
        const transcript = e.results[0][0].transcript;
        const input = document.getElementById('chat-input');
        if (input) input.value = transcript;
        sendChatMessage();
    };

    rec.onerror = () => {
        if (micBtn) micBtn.classList.remove('listening');
    };

    rec.onend = () => {
        if (micBtn) micBtn.classList.remove('listening');
    };

    rec.start();
}

// ── Utility ────────────────────────────────────────────────────────────────────

function formatMs(ms) {
    if (!ms || ms <= 0) return 'N/A';
    const minutes = Math.floor(ms / 60000);
    const secs = Math.floor((ms % 60000) / 1000);
    const millis = ms % 1000;
    return minutes > 0
        ? `${minutes}:${String(secs).padStart(2, '0')}.${String(millis).padStart(3, '0')}`
        : `${secs}.${String(millis).padStart(3, '0')}`;
}

// Ensure voices are loaded (some browsers need a user gesture first)
if (window.speechSynthesis) {
    window.speechSynthesis.onvoiceschanged = () => { window.speechSynthesis.getVoices(); };
}

// Export to global scope for use in app.js and history.js
window.handleLapDebrief = handleLapDebrief;
window.activateVoiceQuery = activateVoiceQuery;
window.showSessionDebrief = showSessionDebrief;
window.openChatPanel = openChatPanel;
window.closeChatPanel = closeChatPanel;
window.sendChatMessage = sendChatMessage;
window.appendChatMessage = appendChatMessage;
window.startChatVoiceInput = startChatVoiceInput;
window.engineerSpeak = engineerSpeak;
window.toggleTts = toggleTts;
