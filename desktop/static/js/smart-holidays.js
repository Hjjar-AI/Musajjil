// Smart holidays: update placeholder based on rule type
(function() {
    function initRuleTypeHint() {
        const ruleType = document.getElementById('ruleType');
        if (!ruleType) return;
        const ruleValue = document.getElementById('ruleValue');
        const hints = {
            weekday: 'أدخل رقم اليوم: 0=الإثنين...6=الأحد',
            date: 'أدخل أرقام الأيام مفصولة بفواصل (مثال: 1,15)',
            formula: '"last Friday of month" أو "first Monday of month"'
        };
        function updatePlaceholder() {
            if (ruleValue) ruleValue.placeholder = hints[ruleType.value] || '';
        }
        ruleType.addEventListener('change', updatePlaceholder);
        updatePlaceholder();
    }
    
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initRuleTypeHint);
    } else {
        initRuleTypeHint();
    }
})();