// Setup wizard: add classification and criteria dynamically
(function() {
    let classIndex = 1;
    function addClassification() {
        const container = document.getElementById('classifications-container');
        if (!container) return;
        const vacTypes = JSON.parse(container.dataset.vacTypes || '[]');
        const row = document.createElement('div');
        row.className = 'row mb-3 classification-row g-2';
        let innerHtml = `<div class="col-md-3"><input type="text" class="form-control" name="classification_name" placeholder="اسم التصنيف"></div><div class="col-md-9"><div class="row g-2">`;
        for (const vt of vacTypes) {
            innerHtml += `<div class="col-md-3"><label>${vt.name}</label><input type="number" step="0.5" class="form-control" name="ent_${classIndex}_${vt.id}" value="0"></div>`;
        }
        innerHtml += `</div></div>`;
        row.innerHTML = innerHtml;
        container.appendChild(row);
        classIndex++;
    }
    
    function addCriteria() {
        const container = document.getElementById('criteria-container');
        if (!container) return;
        const div = document.createElement('div');
        div.className = 'mb-2';
        div.innerHTML = '<input type="text" class="form-control" name="general_criteria" placeholder="معيار تقييم">';
        container.appendChild(div);
    }
    
    function initWizard() {
        const addClassBtn = document.querySelector('[data-action="add-classification"]');
        const addCriteriaBtn = document.querySelector('[data-action="add-criteria"]');
        if (addClassBtn) addClassBtn.addEventListener('click', addClassification);
        if (addCriteriaBtn) addCriteriaBtn.addEventListener('click', addCriteria);
    }
    
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initWizard);
    } else {
        initWizard();
    }
})();