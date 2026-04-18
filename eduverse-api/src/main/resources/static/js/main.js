/**
 * Eduverse Core JS - Auth & API Management
 */

const API_BASE = '/api';

const EduverseAuth = {
    // 1. Token Management
    saveAuth(data) {
        localStorage.setItem('accessToken', data.accessToken);
        localStorage.setItem('refreshToken', data.refreshToken);
        localStorage.setItem('userEmail', data.email);
        localStorage.setItem('userRole', data.role);
        localStorage.setItem('tenantId', data.tenantId);
    },

    async clearAuth() {
        const refreshToken = this.getRefreshToken();
        if (refreshToken) {
            try {
                await fetch(`${API_BASE}/auth/logout`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ refreshToken })
                });
            } catch (e) { console.error('Logout failed', e); }
        }
        localStorage.clear();
        window.location.href = '/login.html';
    },

    getAccessToken() { return localStorage.getItem('accessToken'); },
    getRefreshToken() { return localStorage.getItem('refreshToken'); },

    // 2. Refresh Token Logic
    async refreshTokens() {
        const refreshToken = this.getRefreshToken();
        if (!refreshToken) return false;

        try {
            const response = await fetch(`${API_BASE}/auth/refresh`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ refreshToken })
            });

            if (response.ok) {
                const data = await response.json();
                this.saveAuth(data);
                return true;
            }
        } catch (err) {
            console.error('Refresh token failed', err);
        }
        return false;
    },

    // 3. API Wrapper with Auto-Retry
    async apiFetch(endpoint, options = {}) {
        let accessToken = this.getAccessToken();
        
        const tenantId = localStorage.getItem('tenantId');
        
        const headers = {
            'Content-Type': 'application/json',
            'X-TenantID': tenantId || 'public',
            ...(options.headers || {}),
        };

        if (accessToken) {
            headers['Authorization'] = `Bearer ${accessToken}`;
        }

        let response = await fetch(`${API_BASE}${endpoint}`, { ...options, headers });

        // Handle Expired Token (401)
        if (response.status === 401 || response.status === 403) {
            const refreshed = await this.refreshTokens();
            if (refreshed) {
                // Retry once with new token
                headers['Authorization'] = `Bearer ${this.getAccessToken()}`;
                response = await fetch(`${API_BASE}${endpoint}`, { ...options, headers });
            } else {
                // If refresh fails, go to login
                this.clearAuth();
            }
        }

        return response;
    }
};

// --- Page Initialization ---
document.addEventListener('DOMContentLoaded', () => {
    // 1. Handle Login
    const loginForm = document.getElementById('loginForm');
    if (loginForm) {
        loginForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const email = document.getElementById('email').value;
            const password = document.getElementById('password').value;
            const statusDiv = document.getElementById('status');
            
            statusDiv.style.display = 'block';
            statusDiv.innerHTML = '<p style="color: var(--primary);">Authenticating...</p>';

            try {
                const response = await fetch(`${API_BASE}/auth/login`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ email, password })
                });

                if (response.ok) {
                    const data = await response.json();
                    EduverseAuth.saveAuth(data);
                    window.location.href = '/dashboard.html';
                } else {
                    statusDiv.innerHTML = '<p style="color: #dc2626;">Invalid email or password.</p>';
                }
            } catch (err) {
                statusDiv.innerHTML = '<p style="color: #dc2626;">Connection failed.</p>';
            }
        });
    }

    // 2. Handle Logout
    const logoutBtn = document.getElementById('logoutBtn');
    if (logoutBtn) {
        logoutBtn.addEventListener('click', () => EduverseAuth.clearAuth());
    }

    // 3. Populate Dashboard Data & Init Management
    if (window.location.pathname === '/dashboard.html') {
        const email = localStorage.getItem('userEmail');
        const role = localStorage.getItem('userRole');

        if (!email) { EduverseAuth.clearAuth(); return; }

        document.getElementById('welcomeMsg').textContent = `Welcome, ${email.split('@')[0]}!`;
        document.getElementById('userRole').textContent = role;
        document.getElementById('userEmail').textContent = email;

        // Show Management for Admin/Teacher
        if (role === 'ADMIN' || role === 'TEACHER') {
            document.getElementById('managementSection').style.display = 'block';
            initManagement(role);
            loadUsers();
        }
    }

    // 4. Registration handling (from before)
    const registerForm = document.getElementById('registerForm');
    if (registerForm) {
        registerForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const statusDiv = document.getElementById('status');
            const formData = new FormData(registerForm);
            
            statusDiv.style.display = 'block';
            statusDiv.innerHTML = '<p style="color: var(--primary);">Setting up platform...</p>';

            const params = new URLSearchParams(formData);
            const response = await fetch(`/api/register`, { 
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: params.toString()
            });

            if (response.ok) {
                const data = await response.json();
                statusDiv.innerHTML = `<p style="color: #059669;">Success! Your school is ready. <br><br> <a href="/login.html" style="color: var(--primary);">Login here</a></p>`;
            } else {
                statusDiv.innerHTML = '<p style="color: #dc2626;">Registration failed.</p>';
            }
        });
    }
});

async function loadUsers() {
    const tableBody = document.getElementById('userTableBody');
    const response = await EduverseAuth.apiFetch('/users');
    if (response.ok) {
        const users = await response.json();
        tableBody.innerHTML = users.map(u => `
            <tr style="border-bottom: 1px solid #f1f5f9;">
                <td style="padding: 1rem;">${u.firstName} ${u.lastName}</td>
                <td style="padding: 1rem;"><span style="background: #f1f5f9; padding: 0.25rem 0.5rem; border-radius: 0.25rem; font-size: 0.75rem;">${u.role}</span></td>
                <td style="padding: 1rem;">${u.email}</td>
                <td style="padding: 1rem;">${u.active ? '✅ Active' : '❌ Inactive'}</td>
            </tr>
        `).join('');
    }
}

function initManagement(myRole) {
    const roleSelect = document.getElementById('roleSelect');
    const tenantGroup = document.getElementById('tenantGroup');
    const addUserFormContainer = document.getElementById('addUserFormContainer');
    
    // Set Roles based on Hierarchy
    let roles = [];
    if (myRole === 'ADMIN') {
        roles = ['ADMIN', 'TEACHER', 'ASSISTANT', 'STUDENT', 'PARENT'];
        tenantGroup.style.display = 'block'; // Admin must specific tenant for non-admin users
    } else if (myRole === 'TEACHER') {
        roles = ['ASSISTANT', 'STUDENT', 'PARENT'];
    }

    roleSelect.innerHTML = roles.map(r => `<option value="${r}">${r}</option>`).join('');

    // Toggle Form
    document.getElementById('showAddUser').onclick = () => addUserFormContainer.style.display = 'block';
    document.getElementById('cancelAddUser').onclick = () => addUserFormContainer.style.display = 'none';

    // Handle Submit
    const addUserForm = document.getElementById('addUserForm');
    addUserForm.onsubmit = async (e) => {
        e.preventDefault();
        const formData = new Object();
        new FormData(addUserForm).forEach((value, key) => formData[key] = value);

        const response = await EduverseAuth.apiFetch('/users', {
            method: 'POST',
            body: JSON.stringify(formData)
        });

        if (response.ok) {
            alert('User created successfully!');
            addUserForm.reset();
            addUserFormContainer.style.display = 'none';
            loadUsers();
        } else {
            const err = await response.text();
            alert('Error: ' + err);
        }
    };
}
