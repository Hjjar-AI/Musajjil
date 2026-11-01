// Holidays page: toggle between fixed and variable date inputs
(function() {
    function toggleHolidayType() {
        const typeSelect = document.getElementById('holiday_type');
        if (!typeSelect) return;
        const variableGroup = document.getElementById('variable_date_group');
        const fixedMonth = document.getElementById('fixed_date_group');
        const fixedDay = document.getElementById('fixed_day_group');
        
        function update() {
            if (typeSelect.value === '1') {
                if (variableGroup) variableGroup.style.display = 'none';
                if (fixedMonth) fixedMonth.style.display = 'block';
                if (fixedDay) fixedDay.style.display = 'block';
            } else {
                if (variableGroup) variableGroup.style.display = 'block';
                if (fixedMonth) fixedMonth.style.display = 'none';
                if (fixedDay) fixedDay.style.display = 'none';
            }
        }
        
        typeSelect.addEventListener('change', update);
        update();
    }
    
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', toggleHolidayType);
    } else {
        toggleHolidayType();
    }
})();