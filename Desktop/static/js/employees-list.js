// Employees list: bulk edit checkbox handling
(function() {
    function updateBulkButton() {
        const checkboxes = document.querySelectorAll('.empCheckbox');
        const bulkBtn = document.getElementById('bulkEditBtn');
        if (!bulkBtn) return;
        const anyChecked = Array.from(checkboxes).some(cb => cb.checked);
        bulkBtn.disabled = !anyChecked;
    }
    
    function initBulkEdit() {
        const selectAll = document.getElementById('selectAll');
        if (!selectAll) return;
        const checkboxes = document.querySelectorAll('.empCheckbox');
        const bulkBtn = document.getElementById('bulkEditBtn');
        
        selectAll.addEventListener('change', function() {
            checkboxes.forEach(cb => cb.checked = this.checked);
            updateBulkButton();
        });
        checkboxes.forEach(cb => cb.addEventListener('change', updateBulkButton));
        
        if (bulkBtn) {
            bulkBtn.addEventListener('click', function() {
                const selected = Array.from(checkboxes).filter(cb => cb.checked).map(cb => cb.value);
                if (selected.length === 0) return;
                const url = new URL(window.location.href);
                const baseUrl = url.origin + url.pathname.replace(/\/$/, '') + '/bulk_edit_form';
                const params = new URLSearchParams();
                selected.forEach(id => params.append('ids', id));
                window.location.href = baseUrl + '?' + params.toString();
            });
        }
    }
    
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initBulkEdit);
    } else {
        initBulkEdit();
    }
})();