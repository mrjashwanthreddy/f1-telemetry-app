let authMode = 'LOGIN'; // 'LOGIN', 'REGISTER', 'RESET'
let selectedRole = 'DRIVER'; // 'DRIVER' or 'ENGINEER'

// Check if user is already logged in on page load
document.addEventListener('DOMContentLoaded', () => {
    const token = localStorage.getItem('jwtToken');
    const role = localStorage.getItem('userRole');
    if (token) {
        if (role === 'ROLE_ENGINEER') {
            window.location.href = '/engineer.html';
            return;
        }
        showDashboard();
    }
});

function selectAuthRole(role) {
    selectedRole = role;
    const btns = document.querySelectorAll('.role-option-btn');
    btns.forEach(btn => {
        const btnRole = btn.getAttribute('data-role');
        if (btnRole === role) {
            btn.classList.add('active');
        } else {
            btn.classList.remove('active');
        }
    });
}

function setAuthMode(mode) {
    authMode = mode;
    hideError();

    const submitBtn = document.getElementById('auth-submit-btn');
    const switchText = document.getElementById('auth-switch-text');
    const switchLink = document.getElementById('auth-switch-link');
    const forgotP = document.getElementById('auth-forgot-p');
    const roleContainer = document.getElementById('auth-role-selector');
    const labelPass = document.getElementById('label-password');
    const confirmPassGroup = document.getElementById('group-confirm-password');

    if (authMode === 'LOGIN') {
        if (submitBtn) submitBtn.textContent = 'LOGIN';
        if (switchText) switchText.textContent = 'New user?';
        if (switchLink) {
            switchLink.textContent = 'Sign up here';
            switchLink.onclick = (e) => { e.preventDefault(); setAuthMode('REGISTER'); };
        }
        if (forgotP) forgotP.style.display = 'block';
        if (roleContainer) roleContainer.style.display = 'none';
        if (labelPass) labelPass.textContent = 'Password';
        if (confirmPassGroup) confirmPassGroup.style.display = 'none';
    } else if (authMode === 'REGISTER') {
        if (submitBtn) submitBtn.textContent = 'SIGN UP';
        if (switchText) switchText.textContent = 'Already have an account?';
        if (switchLink) {
            switchLink.textContent = 'Log in here';
            switchLink.onclick = (e) => { e.preventDefault(); setAuthMode('LOGIN'); };
        }
        if (forgotP) forgotP.style.display = 'none';
        if (roleContainer) roleContainer.style.display = 'flex';
        if (labelPass) labelPass.textContent = 'Password';
        if (confirmPassGroup) confirmPassGroup.style.display = 'none';
    } else if (authMode === 'RESET') {
        if (submitBtn) submitBtn.textContent = 'RESET PASSWORD';
        if (switchText) switchText.textContent = 'Remembered your password?';
        if (switchLink) {
            switchLink.textContent = 'Log in here';
            switchLink.onclick = (e) => { e.preventDefault(); setAuthMode('LOGIN'); };
        }
        if (forgotP) forgotP.style.display = 'none';
        if (roleContainer) roleContainer.style.display = 'flex';
        if (labelPass) labelPass.textContent = 'New Password';
        if (confirmPassGroup) confirmPassGroup.style.display = 'block';
    }
}

function toggleAuthMode(event) {
    if (event) event.preventDefault();
    setAuthMode(authMode === 'LOGIN' ? 'REGISTER' : 'LOGIN');
}

function toggleResetMode(event) {
    if (event) event.preventDefault();
    setAuthMode('RESET');
}

async function handleAuth(event) {
    event.preventDefault();
    const username = document.getElementById('username').value.trim();
    const password = document.getElementById('password').value;

    if (!username || !password) {
        showError("Username and password are required.");
        return;
    }

    if (authMode === 'RESET') {
        const confirmPass = document.getElementById('confirm-password')?.value;
        if (password !== confirmPass) {
            showError("Passwords do not match. Please verify.");
            return;
        }

        try {
            const res = await fetch('/api/auth/reset-password', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    username: username,
                    newPassword: password,
                    role: selectedRole
                })
            });

            if (res.ok) {
                const msg = await res.text();
                setAuthMode('LOGIN');
                document.getElementById('password').value = '';
                if (document.getElementById('confirm-password')) document.getElementById('confirm-password').value = '';
                const errBox = document.getElementById('auth-error');
                if (errBox) {
                    errBox.style.display = 'block';
                    errBox.style.backgroundColor = 'rgba(16, 185, 129, 0.18)';
                    errBox.style.borderLeftColor = '#10b981';
                    errBox.style.color = '#065f46';
                    errBox.textContent = msg || 'Password reset successfully! Please log in with your new password.';
                }
            } else {
                const err = await res.text();
                showError(err || 'Failed to reset password.');
            }
        } catch (e) {
            showError("Network error resetting password.");
        }
        return;
    }

    const endpoint = (authMode === 'LOGIN') ? '/api/auth/login' : '/api/auth/register';
    const payload = { username, password, role: selectedRole };

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

        if (authMode === 'LOGIN') {
            const data = await response.json();
            if (data.token) {
                localStorage.setItem('jwtToken', data.token);
                localStorage.setItem('userRole', data.role || 'ROLE_DRIVER');
                localStorage.setItem('username', data.username || username);
                if (data.teamPin) {
                    localStorage.setItem('teamPin', data.teamPin);
                }

                // Notify the desktop .exe relay agent so it can authenticate
                // its UDP relay requests to the OCI server.
                fetch('http://127.0.0.1:17777/token', {
                    method: 'POST',
                    headers: { 'Content-Type': 'text/plain' },
                    body: data.token
                }).catch(() => { /* running in browser-only mode — expected */ });

                if (data.role === 'ROLE_ENGINEER') {
                    window.location.href = '/engineer.html';
                } else {
                    showDashboard();
                }
            } else {
                showError("No token received from server.");
            }
        } else {
            // Registration successful! Automatically log in immediately:
            try {
                const loginRes = await fetch('/api/auth/login', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ username, password })
                });
                if (loginRes.ok) {
                    const data = await loginRes.json();
                    if (data.token) {
                        localStorage.setItem('jwtToken', data.token);
                        localStorage.setItem('userRole', data.role || (selectedRole === 'ENGINEER' ? 'ROLE_ENGINEER' : 'ROLE_DRIVER'));
                        localStorage.setItem('username', data.username || username);
                        if (data.teamPin) {
                            localStorage.setItem('teamPin', data.teamPin);
                        }
                        if (data.role === 'ROLE_ENGINEER' || selectedRole === 'ENGINEER') {
                            window.location.href = '/engineer.html';
                        } else {
                            showDashboard();
                        }
                        return;
                    }
                }
            } catch (loginErr) {
                console.warn("Auto-login error", loginErr);
            }

            // Fallback: switch to login mode with success banner
            document.getElementById('username').value = '';
            document.getElementById('password').value = '';
            setAuthMode('LOGIN');
            const errBox = document.getElementById('auth-error');
            if (errBox) {
                errBox.style.display = 'block';
                errBox.style.backgroundColor = 'rgba(16, 185, 129, 0.18)';
                errBox.style.borderLeftColor = '#10b981';
                errBox.style.color = '#065f46';
                errBox.textContent = "Registration successful! Please log in.";
            }
        }
    } catch (err) {
        console.error(err);
        showError("Network error. Please try again.");
    }
}

async function showDashboard() {
    document.getElementById('auth-overlay').style.display = 'none';
    document.getElementById('main-app').style.display = 'block';

    const pin = localStorage.getItem('teamPin');
    const pinBadge = document.getElementById('driver-pin-badge');
    if (pinBadge && pin) {
        pinBadge.textContent = pin;
    }

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

    // Immediately load preferences and AI usage stats so wallet balance is populated
    if (typeof loadPreferences === 'function') {
        loadPreferences();
    }
    if (typeof loadAiUsageStats === 'function') {
        loadAiUsageStats();
    }
}

function logout() {
    localStorage.removeItem('jwtToken');
    localStorage.removeItem('userRole');
    localStorage.removeItem('username');
    localStorage.removeItem('teamPin');
    localStorage.removeItem('pairedDriverName');
    localStorage.removeItem('pairedDriverId');
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
