(function () {
    function ensureToastRoot() {
        let root = document.getElementById('adminToastRoot');
        if (root) return root;

        root = document.createElement('div');
        root.id = 'adminToastRoot';
        root.style.position = 'fixed';
        root.style.right = '22px';
        root.style.bottom = '22px';
        root.style.zIndex = '9999';
        root.style.display = 'grid';
        root.style.gap = '10px';
        root.style.maxWidth = '360px';
        document.body.appendChild(root);
        return root;
    }

    function notify(message, type = 'info') {
        const root = ensureToastRoot();
        const toast = document.createElement('div');
        const palette = {
            success: ['#ecfdf5', '#047857', '#a7f3d0'],
            warning: ['#fffbeb', '#92400e', '#fde68a'],
            danger: ['#fef2f2', '#b91c1c', '#fecaca'],
            info: ['#f8fafc', '#0f172a', '#e2e8f0']
        }[type] || ['#f8fafc', '#0f172a', '#e2e8f0'];

        toast.textContent = message;
        toast.style.padding = '13px 15px';
        toast.style.borderRadius = '12px';
        toast.style.background = palette[0];
        toast.style.color = palette[1];
        toast.style.border = `1px solid ${palette[2]}`;
        toast.style.boxShadow = '0 16px 38px rgba(15, 23, 42, 0.14)';
        toast.style.fontSize = '13px';
        toast.style.fontWeight = '850';
        toast.style.lineHeight = '1.55';
        toast.style.transform = 'translateY(8px)';
        toast.style.opacity = '0';
        toast.style.transition = 'opacity 0.2s ease, transform 0.2s ease';
        root.appendChild(toast);

        requestAnimationFrame(() => {
            toast.style.opacity = '1';
            toast.style.transform = 'translateY(0)';
        });

        window.setTimeout(() => {
            toast.style.opacity = '0';
            toast.style.transform = 'translateY(8px)';
            window.setTimeout(() => toast.remove(), 220);
        }, 3600);
    }

    const ROLE_META = {
        ADMIN: {
            label: '主任',
            badge: '主任權限',
            icon: 'fa-user-tie'
        },
        TEACHER: {
            label: '老師',
            badge: '老師工作台',
            icon: 'fa-chalkboard-user'
        }
    };

    const ADMIN_NAV_ITEMS = [
        { href: 'admin-dashboard.html', icon: 'fa-house', label: '主任主控台', group: 'DAILY', adminOnly: true },
        { href: 'teacher-dashboard.html', icon: 'fa-chalkboard-user', label: '老師工作台', group: 'DAILY' },
        { href: 'admin-schedule.html', icon: 'fa-calendar-days', label: '數位互動課表', group: 'DAILY' },
        { href: 'admin-learning.html', icon: 'fa-book-open-reader', label: '學習內容', group: 'DAILY' },
        { href: 'admin-attendance.html', icon: 'fa-user-check', label: '班級點名系統', group: 'DAILY' },
        { href: 'mobile-rollcall.html', icon: 'fa-mobile-screen', label: '手機點名', group: 'DAILY' },
        { href: 'admin-registration.html', icon: 'fa-clipboard-list', label: '招生諮詢', group: 'STUDENTS', adminOnly: true },
        { href: 'admin-students.html', icon: 'fa-users-gear', label: '學生資料中心', group: 'STUDENTS', adminOnly: true },
        { href: 'admin-contacts.html', icon: 'fa-address-book', label: '家長聯絡資料', group: 'STUDENTS', adminOnly: true },
        { href: 'admin-leave.html', icon: 'fa-clipboard-user', label: '學生請假審核', group: 'STUDENTS', adminOnly: true },
        { href: 'admin-line.html', icon: 'fa-brands fa-line', label: 'LINE 推播管理', group: 'OPERATIONS', adminOnly: true, brandIcon: true },
        { href: 'admin-payroll.html', icon: 'fa-file-invoice-dollar', label: '薪資管理', group: 'OPERATIONS', adminOnly: true },
        { href: 'admin-grades.html', icon: 'fa-ranking-star', label: '成績與榮譽榜', group: 'OPERATIONS', adminOnly: true },
        { href: 'admin-system.html', icon: 'fa-shield-halved', label: '系統檢查', group: 'SYSTEM', adminOnly: true }
    ];

    const NAV_GROUP_LABELS = {
        DAILY: '每日教務',
        STUDENTS: '學生管理',
        OPERATIONS: '營運管理',
        SYSTEM: '系統設定'
    };

    async function fetchJson(url, options = {}) {
        const response = await fetch(url, {
            credentials: 'same-origin',
            ...options,
            headers: {
                ...(options.headers || {})
            }
        });

        if (response.status === 401 || response.status === 403) {
            throw new Error('SESSION_EXPIRED');
        }

        if (!response.ok) {
            throw new Error(`HTTP_${response.status}`);
        }

        return response.json();
    }

    function localDateValue(date = new Date()) {
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        return `${year}-${month}-${day}`;
    }

    window.BrocaAdmin = Object.assign(window.BrocaAdmin || {}, { notify, fetchJson, localDateValue });

    function ready(callback) {
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', callback);
            return;
        }
        callback();
    }

    function text(selector, value) {
        document.querySelectorAll(selector).forEach((node) => {
            node.textContent = value;
        });
    }

    function setHiddenForRole(role) {
        if (role === 'ADMIN') {
            document.querySelectorAll('[data-teacher-only]').forEach((node) => {
                node.hidden = true;
            });
            return;
        }

        document.querySelectorAll('[data-admin-only]').forEach((node) => {
            node.hidden = true;
        });
    }

    function mountRoleBadge(role) {
        const topbarRight = document.querySelector('.topbar-right');
        if (!topbarRight || topbarRight.querySelector('.session-role-chip')) return;

        const meta = ROLE_META[role] || ROLE_META.TEACHER;
        const chip = document.createElement('span');
        chip.className = `session-role-chip ${String(role).toLowerCase()}`;
        chip.innerHTML = `<i class="fa-solid ${meta.icon}"></i><span>${meta.badge}</span>`;
        topbarRight.prepend(chip);
    }

    function mountSystemChip() {
        const topbarRight = document.querySelector('.topbar-right');
        if (!topbarRight || topbarRight.querySelector('.session-system-chip')) return;

        const chip = document.createElement('span');
        chip.className = 'session-system-chip';
        chip.innerHTML = '<i class="fa-solid fa-shield-halved"></i><span>Broca 後台</span>';
        topbarRight.prepend(chip);
    }

    function normalizeAdminNav(role) {
        const navMenu = document.querySelector('.sidebar .nav-menu');
        if (!navMenu) return;

        const page = (window.location.pathname.split('/').pop() || 'admin-dashboard.html').toLowerCase();
        const visibleItems = ADMIN_NAV_ITEMS.filter((item) => role === 'ADMIN' || !item.adminOnly);
        let currentGroup = '';
        navMenu.innerHTML = visibleItems.map((item) => {
            const active = item.href.toLowerCase() === page ? ' active' : '';
            const adminAttr = item.adminOnly ? ' data-admin-only' : '';
            const iconClass = item.brandIcon ? item.icon : `fa-solid ${item.icon}`;
            const groupHeading = item.group !== currentGroup
                ? `<li class="nav-section-label">${NAV_GROUP_LABELS[item.group] || ''}</li>`
                : '';
            currentGroup = item.group;
            return `${groupHeading}
                <li class="nav-item"${adminAttr}>
                    <a href="${item.href}" class="nav-link${active}"><i class="${iconClass}"></i> ${item.label}</a>
                </li>`;
        }).join('');
    }

    function syncActiveNav() {
        const page = (window.location.pathname.split('/').pop() || 'admin-dashboard.html').toLowerCase();
        document.querySelectorAll('.sidebar .nav-link').forEach((link) => {
            const href = (link.getAttribute('href') || '').split('?')[0].toLowerCase();
            if (!href) return;
            link.classList.toggle('active', href === page);
        });
    }

    function mountMobileNav() {
        const topbar = document.querySelector('.topbar');
        const mainWrapper = document.querySelector('.main-wrapper');
        if (!topbar || !mainWrapper || document.querySelector('.mobile-admin-nav')) return;

        const links = Array.from(document.querySelectorAll('.sidebar .nav-link'))
                .filter((link) => !link.closest('[hidden]'));
        if (!links.length) return;

        const activeLink = links.find((link) => link.classList.contains('active')) || links[0];
        const nav = document.createElement('nav');
        nav.className = 'mobile-admin-nav';

        const toggle = document.createElement('button');
        toggle.className = 'mobile-admin-nav-toggle';
        toggle.type = 'button';
        toggle.innerHTML = `<span><i class="fa-solid fa-bars"></i>${activeLink.textContent.trim()}</span><i class="fa-solid fa-chevron-down"></i>`;

        const panel = document.createElement('div');
        panel.className = 'mobile-admin-nav-links';

        links.forEach((link) => {
            const clone = link.cloneNode(true);
            clone.classList.remove('nav-link');
            clone.classList.add('mobile-admin-nav-link');
            panel.appendChild(clone);
        });

        toggle.addEventListener('click', () => {
            nav.classList.toggle('open');
        });

        document.addEventListener('click', (event) => {
            if (!nav.contains(event.target)) nav.classList.remove('open');
        });

        nav.append(toggle, panel);
        topbar.insertAdjacentElement('afterend', nav);
    }

    function enforceRoleRoute(role) {
        if (role !== 'ADMIN' && document.body.matches('[data-admin-page="true"]')) {
            window.location.replace('/teacher-dashboard.html');
        }
    }

    function prepareShell(user, role) {
        const safeRole = role === 'ADMIN' ? 'ADMIN' : 'TEACHER';
        document.body.dataset.role = safeRole.toLowerCase();
        document.documentElement.dataset.role = safeRole.toLowerCase();
        window.BrocaAdmin.currentUser = user || null;
        window.BrocaAdmin.currentRole = safeRole;

        text('[data-user-name]', user?.displayName || user?.username || '請重新登入');
        text('[data-user-role]', (ROLE_META[safeRole] || ROLE_META.TEACHER).label);
        text('[data-user-initial]', (user?.displayName || user?.username || 'B').trim().slice(0, 1).toUpperCase());
        normalizeAdminNav(safeRole);
        setHiddenForRole(safeRole);
        syncActiveNav();
        mountRoleBadge(safeRole);
        mountMobileNav();
        enforceRoleRoute(safeRole);
        document.body.classList.add('admin-shell-ready');
        document.dispatchEvent(new CustomEvent('broca:session-ready', { detail: { user, role: safeRole } }));
    }

    ready(async () => {
        try {
            const response = await fetch('/api/admin/session/me', { credentials: 'same-origin' });
            if (!response.ok) throw new Error(`HTTP_${response.status}`);
            const user = await response.json();
            const role = String(user.role || '').toUpperCase();
            prepareShell(user, role);
        } catch (error) {
            const fallbackRole = document.body.matches('[data-admin-page="true"]') ? 'ADMIN' : 'TEACHER';
            prepareShell(null, fallbackRole);
        }
    });
})();
