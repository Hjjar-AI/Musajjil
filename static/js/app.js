/* global flatpickr */

// ============================================
// 1. FLATPICKR INITIALIZATION
// ============================================

(function() {
    function formatDateToDisplay(dateStr) {
        if (!dateStr) return '';
        var parts = dateStr.split('-');
        if (parts.length === 3) return parts[2] + '/' + parts[1] + '/' + parts[0];
        return dateStr;
    }

    const fpConfig = {
        dateFormat: "d/m/Y",
        locale: flatpickr.l10ns.ar,
        disableMobile: true,
        allowInput: true
    };

    function initFlatpickr(input) {
        if (input.classList.contains('flatpickr-input')) return;
        var currentVal = input.value;
        if (currentVal && currentVal.match(/^\d{4}-\d{2}-\d{2}$/)) {
            var displayVal = formatDateToDisplay(currentVal);
            input.value = displayVal;
        }
        const fp = flatpickr(input, fpConfig);
        if (currentVal && currentVal.match(/^\d{4}-\d{2}-\d{2}$/)) {
            const dateObj = new Date(currentVal);
            if (!isNaN(dateObj)) fp.setDate(dateObj);
        }
    }

    function enhanceAll() {
        document.querySelectorAll('input[type="date"]').forEach(input => {
            input.type = "text";
            input.placeholder = "يوم/شهر/سنة";
            initFlatpickr(input);
        });
    }

    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', enhanceAll);
    else enhanceAll();

    const observer = new MutationObserver(mutations => {
        for (const mutation of mutations) {
            for (const node of mutation.addedNodes) {
                if (node.nodeType === 1) {
                    if (node.matches && node.matches('input[type="date"]')) {
                        node.type = "text";
                        node.placeholder = "يوم/شهر/سنة";
                        initFlatpickr(node);
                    } else if (node.querySelectorAll) {
                        node.querySelectorAll('input[type="date"]').forEach(inp => {
                            inp.type = "text";
                            inp.placeholder = "يوم/شهر/سنة";
                            initFlatpickr(inp);
                        });
                    }
                }
            }
        }
    });
    observer.observe(document.body, { childList: true, subtree: true });
})();

// ============================================
// 2. DETECT FORM MODIFICATIONS
// ============================================

(function() {
    let formModified = false;
    document.addEventListener('DOMContentLoaded', () => {
        document.querySelectorAll('form').forEach(form => {
            form.addEventListener('change', () => { formModified = true; });
            form.addEventListener('submit', () => { formModified = false; });
        });
    });
    window.addEventListener('beforeunload', (e) => {
        if (formModified) {
            e.preventDefault();
            e.returnValue = 'لديك تغييرات غير محفوظة. هل أنت متأكد من المغادرة؟';
        }
    });
})();

// ============================================
// 3. DARK MODE TOGGLE & AUTO DETECTION
// ============================================

(function() {
    const html = document.documentElement;
    const toggleBtn = document.getElementById('darkModeToggle');
    function setTheme(theme) {
        html.setAttribute('data-bs-theme', theme);
        localStorage.setItem('bsTheme', theme);
        if (toggleBtn) {
            const icon = toggleBtn.querySelector('i');
            if (icon) icon.className = theme === 'dark' ? 'bi bi-sun-fill' : 'bi bi-moon-stars';
        }
    }
    const autoDarkMode = document.cookie.includes('auto_dark_mode=true');
    if (autoDarkMode) {
        const hour = new Date().getHours();
        setTheme(hour >= 18 || hour < 6 ? 'dark' : 'light');
        setInterval(() => {
            const nowHour = new Date().getHours();
            const shouldDark = nowHour >= 18 || nowHour < 6;
            const current = html.getAttribute('data-bs-theme');
            if (shouldDark && current !== 'dark') setTheme('dark');
            else if (!shouldDark && current !== 'light') setTheme('light');
        }, 3600000);
    } else {
        const saved = localStorage.getItem('bsTheme');
        if (saved === 'dark' || saved === 'light') setTheme(saved);
    }
    if (toggleBtn) {
        toggleBtn.addEventListener('click', () => {
            const current = html.getAttribute('data-bs-theme');
            setTheme(current === 'dark' ? 'light' : 'dark');
        });
    }
})();

// ============================================
// 4. PRESERVE SCROLL POSITION
// ============================================

(function() {
    document.querySelectorAll('form').forEach(form => {
        form.addEventListener('submit', () => {
            sessionStorage.setItem('scrollX', window.scrollX);
            sessionStorage.setItem('scrollY', window.scrollY);
        });
    });
    if (sessionStorage.getItem('scrollY')) {
        window.scrollTo(parseInt(sessionStorage.getItem('scrollX') || '0'), parseInt(sessionStorage.getItem('scrollY') || '0'));
        sessionStorage.removeItem('scrollX');
        sessionStorage.removeItem('scrollY');
    }
})();

// ============================================
// 5. ACTIVE NAV LINK HIGHLIGHTING
// ============================================

(function() {
    const currentPath = window.location.pathname;
    document.querySelectorAll('.navbar-nav .nav-link, .offcanvas .nav-link, .sidebar__nav-link').forEach(link => {
        const href = link.getAttribute('href');
        if (href && href !== '#' && currentPath === href) {
            link.classList.add('active');
        }
    });
})();

// ============================================
// 6. RESPONSIVE TABLES (ADD DATA-LABEL)
// ============================================

(function() {
    const tables = document.querySelectorAll('.table--responsive');
    tables.forEach(table => {
        const headers = Array.from(table.querySelectorAll('thead th')).map(th => th.innerText.trim());
        table.querySelectorAll('tbody td').forEach(td => {
            const colIndex = td.cellIndex;
            if (headers[colIndex]) td.setAttribute('data-label', headers[colIndex]);
        });
    });
})();

// ============================================
// 7. NOTIFICATION BELL ANIMATION
// ============================================

(function() {
    const bellIcon = document.querySelector('.bi-bell-fill');
    if (bellIcon && document.querySelector('.badge.bg-danger')) {
        bellIcon.classList.add('notification-bell-animation');
        setTimeout(() => bellIcon.classList.remove('notification-bell-animation'), 1000);
    }
})();

// ============================================
// 8. GLOBAL SPINNER FOR FETCH REQUESTS
// ============================================

(function() {
    const spinner = document.createElement('div');
    spinner.className = 'global-spinner';
    spinner.innerHTML = '<div class="spinner-border text-primary" role="status"><span class="visually-hidden">جاري التحميل...</span></div>';
    spinner.style.cssText = 'position:fixed; top:50%; left:50%; transform:translate(-50%,-50%); z-index:9999; display:none;';
    document.body.appendChild(spinner);
    window.addEventListener('load', () => { spinner.style.display = 'none'; });
})();

function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/[&<>]/g, function(m) {
        if (m === '&') return '&amp;';
        if (m === '<') return '&lt;';
        if (m === '>') return '&gt;';
        return m;
    });
}

function showSpinner() {
    const spinner = document.querySelector('.global-spinner');
    if (spinner) spinner.style.display = 'block';
}
function hideSpinner() {
    const spinner = document.querySelector('.global-spinner');
    if (spinner) spinner.style.display = 'none';
}
const originalFetch = window.fetch;
window.fetch = function(...args) {
    showSpinner();
    return originalFetch(...args).finally(hideSpinner);
};

// ============================================
// 9. WEEKDAY UTILITIES (UNIFIED CALENDAR)
// ============================================

window.WeekdayUtils = {
    // Convert JS getDay() (0=Sunday) to Arabic weekday name
    getArabicDayName: function(jsDay, long = false) {
        const shortNames = ['الأحد', 'الإثنين', 'الثلاثاء', 'الأربعاء', 'الخميس', 'الجمعة', 'السبت'];
        const longNames = ['الأحد', 'الإثنين', 'الثلاثاء', 'الأربعاء', 'الخميس', 'الجمعة', 'السبت'];
        return long ? longNames[jsDay] : shortNames[jsDay];
    },
    // Get index for calendar display where first column is Saturday
    getDisplayColumnIndex: function(jsDay) {
        return (jsDay + 1) % 7;
    },
    // Convert display column index (0=Saturday) back to JS day (0=Sunday)
    getJsDayFromDisplayIndex: function(displayIndex) {
        return (displayIndex + 6) % 7;
    }
};

// ============================================
// END OF app.js
// ============================================