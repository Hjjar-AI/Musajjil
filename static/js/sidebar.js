// Sidebar toggle functionality
(function() {
    function initSidebar() {
        var sidebar = document.getElementById('mainSidebar');
        var toggleBtn = document.getElementById('sidebarToggle');
        var closeBtn = document.getElementById('closeSidebar');
        var backdrop = document.getElementById('sidebarBackdrop');

        if (!sidebar || !toggleBtn) return;

        function openSidebar() {
            sidebar.classList.add('open');
            if (backdrop) backdrop.classList.add('show');
            document.body.style.overflow = 'hidden';
            toggleBtn.setAttribute('aria-expanded', 'true');
        }

        function closeSidebar() {
            sidebar.classList.remove('open');
            if (backdrop) backdrop.classList.remove('show');
            document.body.style.overflow = '';
            toggleBtn.setAttribute('aria-expanded', 'false');
        }

        toggleBtn.addEventListener('click', function(e) {
            e.preventDefault();
            if (sidebar.classList.contains('open')) closeSidebar();
            else openSidebar();
        });

        if (closeBtn) closeBtn.addEventListener('click', closeSidebar);
        if (backdrop) backdrop.addEventListener('click', closeSidebar);
        document.addEventListener('keydown', function(e) {
            if (e.key === 'Escape' && sidebar.classList.contains('open')) closeSidebar();
        });
        closeSidebar(); // ensure closed initially
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initSidebar);
    } else {
        initSidebar();
    }
})();