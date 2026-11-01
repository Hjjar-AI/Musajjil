// Bulk edit form: show/hide field specific sections
(function() {
    function initBulkEditForm() {
        const fieldSelect = document.getElementById('fieldSelect');
        if (!fieldSelect) return;
        const deptDiv = document.getElementById('deptDiv');
        const classDiv = document.getElementById('classDiv');
        const tagsDiv = document.getElementById('tagsDiv');
        
        function updateFields() {
            if (deptDiv) deptDiv.style.display = 'none';
            if (classDiv) classDiv.style.display = 'none';
            if (tagsDiv) tagsDiv.style.display = 'none';
            if (fieldSelect.value === 'department') {
                if (deptDiv) deptDiv.style.display = 'block';
            } else if (fieldSelect.value === 'classification') {
                if (classDiv) classDiv.style.display = 'block';
            } else if (fieldSelect.value === 'tags') {
                if (tagsDiv) tagsDiv.style.display = 'block';
            }
        }
        
        fieldSelect.addEventListener('change', updateFields);
        updateFields();
    }
    
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initBulkEditForm);
    } else {
        initBulkEditForm();
    }
})();