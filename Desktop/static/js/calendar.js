// Calendar drag & drop, filtering, and navigation
(function() {
    function initCalendar() {
        const events = document.querySelectorAll('.calendar__event');
        const cells = document.querySelectorAll('.calendar__day');
        
        events.forEach(el => {
            el.setAttribute('draggable', 'true');
            el.addEventListener('dragstart', function(e) {
                e.dataTransfer.setData('text/plain', this.dataset.leaveId);
                this.style.opacity = '0.5';
            });
            el.addEventListener('dragend', function(e) {
                this.style.opacity = '1';
            });
        });
        
        cells.forEach(cell => {
            cell.addEventListener('dragenter', function(e) {
                e.preventDefault();
                this.classList.add('drag-over');
            });
            cell.addEventListener('dragleave', function() {
                this.classList.remove('drag-over');
            });
            cell.addEventListener('dragover', function(e) {
                e.preventDefault();
            });
            cell.addEventListener('drop', async function(e) {
                e.preventDefault();
                this.classList.remove('drag-over');
                const leaveId = e.dataTransfer.getData('text/plain');
                const dayElement = this.querySelector('strong');
                if (!leaveId || !dayElement) return;
                const day = parseInt(dayElement.textContent.trim());
                if (isNaN(day)) return;
                const table = this.closest('table');
                if (!table) return;
                const year = parseInt(table.dataset.year);
                const month = parseInt(table.dataset.month);
                const newStart = `${year}-${String(month).padStart(2,'0')}-${String(day).padStart(2,'0')}`;
                const response = await fetch('/calendar/update_leave_date', {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({ id: leaveId, start: newStart, end: newStart })
                });
                if (response.ok) location.reload();
                else alert('فشل تحديث التاريخ');
            });
        });
        
        // Initialize tooltips if needed
        const tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'));
        tooltipTriggerList.map(el => new bootstrap.Tooltip(el));
    }
    
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initCalendar);
    } else {
        initCalendar();
    }
})();