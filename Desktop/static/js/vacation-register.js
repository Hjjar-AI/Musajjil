// Vacation register: employee search, daily/hourly tabs, manual days entry
(function() {
    let selectedEmployee = null;
    
    function initEmployeeSearch() {
        const searchInput = document.getElementById('employee_search');
        const resultsDiv = document.getElementById('search_results');
        if (!searchInput) return;
        
        searchInput.addEventListener('input', function() {
            const q = this.value.trim();
            if (q.length < 1) {
                resultsDiv.innerHTML = '';
                return;
            }
            fetch('/api/employees/search?q=' + encodeURIComponent(q))
                .then(r => r.json())
                .then(data => {
                    resultsDiv.innerHTML = data.map(emp => `<a href="#" class="list-group-item list-group-item-action" data-id="${emp.id}">${emp.name}</a>`).join('');
                    document.querySelectorAll('#search_results a').forEach(el => {
                        el.addEventListener('click', function(e) {
                            e.preventDefault();
                            selectedEmployee = {id: this.dataset.id, name: this.textContent};
                            searchInput.value = selectedEmployee.name;
                            document.getElementById('employee_id').value = selectedEmployee.id;
                            resultsDiv.innerHTML = '';
                        });
                    });
                });
        });
    }
    
    function setMode(isDaily) {
        const dailyDiv = document.getElementById('dailyTypes');
        const hourlyDiv = document.getElementById('hourlyTypes');
        const dailyTab = document.getElementById('dailyTab');
        const hourlyTab = document.getElementById('hourlyTab');
        const hiddenVtId = document.getElementById('selected_vt_id');
        const dailySelect = document.getElementById('vacation_type_daily');
        const hourlySelect = document.getElementById('vacation_type_hourly');
        const hoursField = document.getElementById('hoursField');
        
        if (isDaily) {
            if (dailyDiv) dailyDiv.style.display = 'block';
            if (hourlyDiv) hourlyDiv.style.display = 'none';
            if (dailyTab) dailyTab.classList.add('btn-primary', 'btn');
            if (dailyTab) dailyTab.classList.remove('btn-outline-primary');
            if (hourlyTab) hourlyTab.classList.add('btn-outline-secondary');
            if (hourlyTab) hourlyTab.classList.remove('btn-secondary');
            if (hiddenVtId) hiddenVtId.value = dailySelect?.value || '';
            if (hoursField) hoursField.style.display = 'none';
        } else {
            if (dailyDiv) dailyDiv.style.display = 'none';
            if (hourlyDiv) hourlyDiv.style.display = 'block';
            if (hourlyTab) hourlyTab.classList.add('btn-secondary');
            if (hourlyTab) hourlyTab.classList.remove('btn-outline-secondary');
            if (dailyTab) dailyTab.classList.add('btn-outline-primary');
            if (dailyTab) dailyTab.classList.remove('btn-primary');
            if (hiddenVtId) hiddenVtId.value = hourlySelect?.value || '';
            if (hoursField) hoursField.style.display = 'block';
        }
    }
    
    function initTabs() {
        const dailyTab = document.getElementById('dailyTab');
        const hourlyTab = document.getElementById('hourlyTab');
        if (!dailyTab || !hourlyTab) return;
        dailyTab.addEventListener('click', () => setMode(true));
        hourlyTab.addEventListener('click', () => setMode(false));
        setMode(true);
    }
    
    function initSelects() {
        const dailySelect = document.getElementById('vacation_type_daily');
        const hourlySelect = document.getElementById('vacation_type_hourly');
        const hiddenVtId = document.getElementById('selected_vt_id');
        if (dailySelect) {
            dailySelect.addEventListener('change', () => {
                if (hiddenVtId) hiddenVtId.value = dailySelect.value;
                // No auto‑calculation
            });
        }
        if (hourlySelect) {
            hourlySelect.addEventListener('change', () => {
                if (hiddenVtId) hiddenVtId.value = hourlySelect.value;
                // No auto‑calculation
            });
        }
        // Days field is entirely manual – no event listeners to auto‑fill
    }
    
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => {
            initEmployeeSearch();
            initTabs();
            initSelects();
        });
    } else {
        initEmployeeSearch();
        initTabs();
        initSelects();
    }
})();