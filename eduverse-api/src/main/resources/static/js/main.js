/**
 * Eduverse Core JS - Auth, API Management & UI Controls
 * v5 - Premium Rebuild
 */

const API_BASE = '/api';

const EduverseAuth = {
    saveAuth(data) {
        localStorage.setItem('accessToken', data.accessToken);
        localStorage.setItem('refreshToken', data.refreshToken);
        localStorage.setItem('userEmail', data.email);
        localStorage.setItem('userRole', data.role);
        localStorage.setItem('tenantId', data.tenantId);
    },

    async clearAuth() {
        const refreshToken = this.getRefreshToken();
        const tenantId = localStorage.getItem('tenantId');
        if (refreshToken) {
            try {
                await fetch(`${API_BASE}/auth/logout`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json', 'X-TenantID': tenantId || 'public' },
                    body: JSON.stringify({ refreshToken })
                });
            } catch (e) { /* ignore */ }
        }
        localStorage.clear();
        window.location.href = '/login.html';
    },

    getAccessToken() { return localStorage.getItem('accessToken'); },
    getRefreshToken() { return localStorage.getItem('refreshToken'); },

    async refreshTokens() {
        const refreshToken = this.getRefreshToken();
        const tenantId = localStorage.getItem('tenantId');
        if (!refreshToken) return false;
        try {
            const response = await fetch(`${API_BASE}/auth/refresh`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'X-TenantID': tenantId || 'public' },
                body: JSON.stringify({ refreshToken })
            });
            if (response.ok) {
                this.saveAuth(await response.json());
                return true;
            }
        } catch (err) { console.error('Refresh failed', err); }
        return false;
    },

    async apiFetch(endpoint, options = {}) {
        let accessToken = this.getAccessToken();
        const tenantId = localStorage.getItem('tenantId');
        const headers = {
            'Content-Type': 'application/json',
            'X-TenantID': tenantId || 'public',
            ...(options.headers || {}),
        };
        if (accessToken) headers['Authorization'] = `Bearer ${accessToken}`;

        let response = await fetch(`${API_BASE}${endpoint}`, { ...options, headers });

        if (response.status === 401 || response.status === 403) {
            const refreshed = await this.refreshTokens();
            if (refreshed) {
                headers['Authorization'] = `Bearer ${this.getAccessToken()}`;
                response = await fetch(`${API_BASE}${endpoint}`, { ...options, headers });
            } else {
                this.clearAuth();
            }
        }
        return response;
    }
};

// ───────────────── Role Translations ─────────────────
const ROLE_AR = {
    'ADMIN': 'مسؤول المنصة',
    'TEACHER': 'معلم',
    'ASSISTANT': 'مساعد',
    'STUDENT': 'طالب',
    'PARENT': 'ولي أمر'
};

// ───────────────── Page Init ─────────────────
document.addEventListener('DOMContentLoaded', () => {

    // ── Mobile Sidebar Toggle ──
    const menuBtn = document.getElementById('mobileMenuBtn');
    const sidebar = document.getElementById('sidebar');
    const overlay = document.getElementById('sidebarOverlay');

    if (menuBtn && sidebar) {
        menuBtn.addEventListener('click', () => {
            sidebar.classList.toggle('open');
            if (overlay) overlay.classList.toggle('show');
        });
        if (overlay) {
            overlay.addEventListener('click', () => {
                sidebar.classList.remove('open');
                overlay.classList.remove('show');
            });
        }
    }

    // ── User Avatar ──
    const email = localStorage.getItem('userEmail');
    const avatarEl = document.getElementById('userAvatar');
    const emailEl = document.getElementById('userEmail');
    if (avatarEl && email) {
        avatarEl.textContent = email.charAt(0).toUpperCase();
    }
    if (emailEl && email) {
        emailEl.textContent = email;
    }

    // ── Logout ──
    const logoutBtn = document.getElementById('logoutBtn');
    if (logoutBtn) {
        logoutBtn.addEventListener('click', () => {
            if (confirm('هل أنت متأكد من تسجيل الخروج؟')) EduverseAuth.clearAuth();
        });
    }

    // ── Role-Based Access ──
    const currentRole = localStorage.getItem('userRole');
    if (currentRole === 'STUDENT') {
        // Hide admin-only elements
        document.querySelectorAll('.admin-only').forEach(el => el.style.display = 'none');
        // Hide sidebar links except courses
        document.querySelectorAll('.sidebar-link').forEach(link => {
            if (!link.href.includes('courses.html')) link.style.display = 'none';
        });
        document.querySelectorAll('.sidebar-label').forEach(el => el.style.display = 'none');
        // Redirect from admin pages
        const path = window.location.pathname;
        if (['/dashboard.html', '/students.html', '/youtube-settings.html'].includes(path)) {
            window.location.href = '/courses.html';
        }
    }

    // ── Login Form ──
    const loginForm = document.getElementById('loginForm');
    if (loginForm) {
        loginForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const emailVal = document.getElementById('email').value;
            const password = document.getElementById('password').value;
            const statusDiv = document.getElementById('status');

            let tenantId = new URLSearchParams(window.location.search).get('tenant');
            if (!tenantId) {
                const hostname = window.location.hostname;
                // Check if hostname is NOT an IP address and has subdomains
                const isIP = /^(?:[0-9]{1,3}\.){3}[0-9]{1,3}$/.test(hostname);
                if (!isIP && hostname !== 'localhost' && hostname !== '127.0.0.1') {
                    const parts = hostname.split('.');
                    if (parts.length >= 3) tenantId = parts[0];
                    else if (parts.length === 2 && parts[1] === 'localhost') tenantId = parts[0];
                }
            }

            statusDiv.style.display = 'block';
            statusDiv.style.background = 'var(--primary-light)';
            statusDiv.style.color = 'var(--primary)';
            statusDiv.innerHTML = 'جاري التحقق من البيانات...';

            try {
                const headers = { 'Content-Type': 'application/json' };
                if (tenantId) headers['X-TenantID'] = tenantId;

                const response = await fetch(`${API_BASE}/auth/login`, {
                    method: 'POST', headers,
                    body: JSON.stringify({ email: emailVal, password })
                });

                if (response.ok) {
                    EduverseAuth.saveAuth(await response.json());
                    window.location.href = '/dashboard.html';
                } else {
                    statusDiv.style.background = 'var(--danger-light)';
                    statusDiv.style.color = 'var(--danger)';
                    statusDiv.innerHTML = 'البريد الإلكتروني أو كلمة المرور غير صحيحة.';
                }
            } catch (err) {
                statusDiv.style.background = 'var(--danger-light)';
                statusDiv.style.color = 'var(--danger)';
                statusDiv.innerHTML = 'فشل الاتصال بالخادم.';
            }
        });
    }

    // ── Dashboard Init ──
    if (window.location.pathname === '/dashboard.html') {
        const role = localStorage.getItem('userRole');
        if (!email) { EduverseAuth.clearAuth(); return; }

        const welcomeEl = document.getElementById('welcomeMsg');
        const roleEl = document.getElementById('userRole');
        if (welcomeEl) welcomeEl.textContent = `مرحباً، ${email.split('@')[0]}!`;
        if (roleEl) roleEl.textContent = ROLE_AR[role] || role;

        if (role === 'ADMIN' || role === 'TEACHER') {
            const mgmt = document.getElementById('managementSection');
            if (mgmt) { mgmt.style.display = 'block'; initManagement(role); loadUsers(); }
        }
    }

    // ── Register Form ──
    const registerForm = document.getElementById('registerForm');
    if (registerForm) {
        registerForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const statusDiv = document.getElementById('status');
            const formData = new FormData(registerForm);

            statusDiv.style.display = 'block';
            statusDiv.style.background = 'var(--primary-light)';
            statusDiv.style.color = 'var(--primary)';
            statusDiv.innerHTML = 'جاري إنشاء منصتك التعليمية...';

            try {
                const response = await fetch('/api/register', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                    body: new URLSearchParams(formData).toString()
                });

                if (response.ok) {
                    statusDiv.style.background = 'var(--secondary-light)';
                    statusDiv.style.color = '#0a7d58';
                    statusDiv.innerHTML = 'تم إنشاء المنصة بنجاح! 🎉 <a href="/login.html" style="color: var(--primary); text-decoration: underline; margin-right: 0.5rem;">سجّل الدخول الآن</a>';
                    registerForm.reset();
                } else {
                    const err = await response.text();
                    statusDiv.style.background = 'var(--danger-light)';
                    statusDiv.style.color = 'var(--danger)';
                    statusDiv.innerHTML = 'فشل التسجيل: ' + err;
                }
            } catch (err) {
                statusDiv.style.background = 'var(--danger-light)';
                statusDiv.style.color = 'var(--danger)';
                statusDiv.innerHTML = 'خطأ في الاتصال بالخادم.';
            }
        });
    }

    // ── Student Self-Registration ──
    const studentRegisterForm = document.getElementById('studentRegisterForm');
    if (studentRegisterForm) {
        studentRegisterForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const statusDiv = document.getElementById('status');
            const data = Object.fromEntries(new FormData(studentRegisterForm));

            statusDiv.style.display = 'block';
            statusDiv.style.background = 'var(--primary-light)';
            statusDiv.style.color = 'var(--primary)';
            statusDiv.innerHTML = 'جاري التسجيل...';

            try {
                const response = await fetch('/api/auth/student-register', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(data)
                });

                if (response.ok) {
                    statusDiv.style.background = 'var(--secondary-light)';
                    statusDiv.style.color = '#0a7d58';
                    statusDiv.innerHTML = `تم تسجيلك بنجاح! 🎉 <a href="/login.html?tenant=${data.tenantId}" style="color: var(--primary); text-decoration: underline;">سجّل الدخول الآن</a>`;
                    studentRegisterForm.reset();
                } else {
                    statusDiv.style.background = 'var(--danger-light)';
                    statusDiv.style.color = 'var(--danger)';
                    statusDiv.innerHTML = 'فشل التسجيل. تأكد من البيانات ومعرف المنصة.';
                }
            } catch (error) {
                statusDiv.style.background = 'var(--danger-light)';
                statusDiv.style.color = 'var(--danger)';
                statusDiv.innerHTML = 'خطأ في الاتصال.';
            }
        });
    }
});

// ───────────────── Dashboard: User Management ─────────────────

async function loadUsers() {
    const tableBody = document.getElementById('userTableBody');
    if (!tableBody) return;
    try {
        const response = await EduverseAuth.apiFetch('/users');
        if (!response.ok) return;
        const users = await response.json();
        tableBody.innerHTML = users.map(u => `
            <tr>
                <td style="font-weight: 600;">${u.firstName} ${u.lastName}</td>
                <td><span class="badge badge-info">${ROLE_AR[u.role] || u.role}</span></td>
                <td>${u.email}</td>
                <td>${u.active ? '<span class="badge badge-success">نشط</span>' : '<span class="badge badge-danger">معطل</span>'}</td>
            </tr>
        `).join('');
    } catch (e) { /* ignore */ }
}

function initManagement(myRole) {
    const roleSelect = document.getElementById('roleSelect');
    if (!roleSelect) return;
    const tenantGroup = document.getElementById('tenantGroup');
    const formContainer = document.getElementById('addUserFormContainer');

    const roleMap = { ADMIN: 'مسؤول', TEACHER: 'معلم', ASSISTANT: 'مساعد', STUDENT: 'طالب', PARENT: 'ولي أمر' };
    let allowed = myRole === 'ADMIN' ? ['ADMIN', 'TEACHER', 'ASSISTANT', 'STUDENT', 'PARENT'] : ['ASSISTANT', 'STUDENT', 'PARENT'];
    if (myRole === 'ADMIN' && tenantGroup) tenantGroup.style.display = 'block';

    roleSelect.innerHTML = allowed.map(r => `<option value="${r}">${roleMap[r]}</option>`).join('');

    document.getElementById('showAddUser').onclick = () => formContainer.style.display = 'block';
    document.getElementById('cancelAddUser').onclick = () => formContainer.style.display = 'none';

    document.getElementById('addUserForm').onsubmit = async (e) => {
        e.preventDefault();
        const data = Object.fromEntries(new FormData(e.target));
        const response = await EduverseAuth.apiFetch('/users', { method: 'POST', body: JSON.stringify(data) });
        if (response.ok) {
            alert('تم إنشاء المستخدم بنجاح!');
            e.target.reset();
            formContainer.style.display = 'none';
            loadUsers();
        } else {
            alert('خطأ: ' + await response.text());
        }
    };
}
