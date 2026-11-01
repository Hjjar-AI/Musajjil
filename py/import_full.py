# py/import_full.py
import json
from flask import Blueprint, render_template, request, redirect, url_for, flash
from .database import get_db
from .export_employees import export_import_bp

@export_import_bp.route('/import/full', methods=['GET', 'POST'])
def import_full():
    if request.method == 'POST':
        file = request.files['file']
        if not file:
            flash('الرجاء اختيار ملف', 'error')
            return render_template('import_full_form.html')
        try:
            data = json.load(file)
            db = get_db()
            db.execute("PRAGMA foreign_keys = OFF")
            for table in reversed(['evaluation_scores', 'evaluations', 'employee_tags', 'employee_work_periods',
                                   'leave_log', 'employee_entitlements', 'classification_entitlements',
                                   'employees', 'tags', 'vacation_types', 'classifications', 'evaluation_criteria', 'backup_log']):
                if table in data:
                    db.execute(f'DELETE FROM {table}')
            for table in ['classifications', 'vacation_types', 'tags', 'evaluation_criteria',
                          'employees', 'employee_work_periods', 'leave_log', 'employee_entitlements',
                          'classification_entitlements', 'employee_tags', 'evaluations', 'evaluation_scores', 'backup_log']:
                if table in data:
                    for row in data[table]:
                        columns = ', '.join(row.keys())
                        placeholders = ', '.join(['?']*len(row))
                        db.execute(f'INSERT INTO {table} ({columns}) VALUES ({placeholders})', tuple(row.values()))
            db.execute("PRAGMA foreign_keys = ON")
            db.commit()
            flash('تم استعادة قاعدة البيانات بنجاح', 'success')
        except Exception as e:
            flash(f'خطأ: {e}', 'error')
        return redirect(url_for('dashboard.dashboard'))
    return render_template('import_full_form.html')