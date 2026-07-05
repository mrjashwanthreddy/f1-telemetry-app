let isLoginMode = true;

// Check if user is already logged in on page load
document.addEventListener('DOMContentLoaded', () => {
    const token = localStorage.getItem('jwtToken');
    if (token) {
        // Assume token is valid for now, show dashboard
        showDashboard();
    }
});

function toggleAuthMode() {
    isLoginMode = !isLoginMode;
    document.getElementById('auth-submit-btn').textContent = isLoginMode ? 'LOGIN' : 'SIGN UP';
    document.getElementById('auth-switch-text').textContent = isLoginMode ? 'New engineer?' : 'Already have an account?';
    document.getElementById('auth-switch-link').textContent = isLoginMode ? 'Sign up here' : 'Log in here';
    hideError();
}

async function handleAuth(event) {
    event.preventDefault();
    const username = document.getElementById('username').value;
    const password = document.getElementById('password').value;
    
    if (!username || !password) {
        showError("Username and password are required.");
        return;
    }
    
    const endpoint = isLoginMode ? '/api/auth/login' : '/api/auth/register';
    const payload = { username, password, role: 'ENGINEER' };
    
    try {
        const response = await fetch(endpoint, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(payload)
        });
        
        if (!response.ok) {
            const errorMsg = await response.text();
            showError(errorMsg || "Authentication failed.");
            return;
        }
        
        if (isLoginMode) {
            const data = await response.json();
            if (data.token) {
                localStorage.setItem('jwtToken', data.token);
                showDashboard();
            } else {
                showError("No token received from server.");
            }
        } else {
            // Registration successful! It returns plain text, not JSON.
            // Let's automatically switch to login mode and pre-fill the username
            toggleAuthMode();
            document.getElementById('auth-error').style.display = 'block';
            document.getElementById('auth-error').style.backgroundColor = 'rgba(0, 255, 0, 0.2)';
            document.getElementById('auth-error').style.borderLeftColor = '#00ff00';
            document.getElementById('auth-error').textContent = "Registration successful! Please log in.";
        }
    } catch (err) {
        console.error(err);
        showError("Network error. Please try again.");
    }
}

async function showDashboard() {
    document.getElementById('auth-overlay').style.display = 'none';
    document.getElementById('main-app').style.display = 'block';
    
    // We should trigger a connect if not already connected
    if (typeof connect === 'function') {
        connect();
    }
    
    // Register this user as the Active Player for background UDP processing
    const token = localStorage.getItem('jwtToken');
    if (token) {
        try {
            await fetch('/api/session/start', {
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${token}`
                }
            });
        } catch (e) {
            console.error("Failed to start telemetry session", e);
        }
    }
}

function logout() {
    localStorage.removeItem('jwtToken');
    document.getElementById('auth-overlay').style.display = 'flex';
    document.getElementById('main-app').style.display = 'none';
    
    // Reset tabs so it doesn't get stuck on "Loading..." when logging back in
    if (typeof switchTab === 'function') {
        const liveBtn = document.querySelector('.tabs button');
        if (liveBtn) switchTab('live-tab', liveBtn);
    }
    
    if (typeof disconnect === 'function') {
        disconnect();
    }
}

function showError(msg) {
    const errorDiv = document.getElementById('auth-error');
    errorDiv.textContent = msg;
    errorDiv.style.display = 'block';
}

function hideError() {
    document.getElementById('auth-error').style.display = 'none';
}
