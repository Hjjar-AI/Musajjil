// Confirmation handler for forms with data-confirm-message attribute
(function() {
    function initConfirmHandler() {
        document.querySelectorAll('form[data-confirm-message]').forEach(form => {
            form.addEventListener('submit', function(e) {
                const msg = this.getAttribute('data-confirm-message');
                if (!confirm(msg)) {
                    e.preventDefault();
                }
            });
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initConfirmHandler);
    } else {
        initConfirmHandler();
    }
})();