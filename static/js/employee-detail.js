// Employee detail page: edit leave modal, recalc days, and form submission
(function() {
    function recalcDays() {
        const start = document.getElementById('edit_start_date').value;
        const end = document.getElementById('edit_end_date').value;
        if (start && end && start <= end) {
            fetch(`/api/calculate_days?start=${start}&end=${end}`)
                .then(r => r.json())
                .then(data => {
                    if (data.days) document.getElementById('edit_days_taken').value = data.days;
                })
                .catch(e => console.warn(e));
        }
    }
    
    function initEditLeaveModal() {
        const btns = document.querySelectorAll('.edit-leave-btn');
        if (!btns.length) return;
        
        btns.forEach(btn => {
            btn.addEventListener('click', function() {
                document.getElementById('edit_leave_id').value = this.dataset.id;
                document.getElementById('edit_type').value = this.dataset.type;
                document.getElementById('edit_start_date').value = this.dataset.start;
                document.getElementById('edit_end_date').value = this.dataset.end;
                document.getElementById('edit_days_taken').value = this.dataset.days;
                document.getElementById('edit_notes').value = this.dataset.note;
                const modal = new bootstrap.Modal(document.getElementById('editLeaveModal'));
                modal.show();
            });
        });
        
        const startDateInput = document.getElementById('edit_start_date');
        const endDateInput = document.getElementById('edit_end_date');
        if (startDateInput && endDateInput) {
            startDateInput.addEventListener('change', recalcDays);
            endDateInput.addEventListener('change', recalcDays);
        }
        
        const editForm = document.getElementById('editLeaveForm');
        if (editForm) {
            editForm.addEventListener('submit', function(e) {
                const leaveId = document.getElementById('edit_leave_id').value;
                this.action = this.action.replace('/0', `/${leaveId}`);
            });
        }
    }
    
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initEditLeaveModal);
    } else {
        initEditLeaveModal();
    }
})();