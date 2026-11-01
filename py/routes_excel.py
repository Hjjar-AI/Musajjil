import io
from flask import Blueprint, render_template, request, redirect, url_for, flash, send_file
from .database import get_db
from .employee_helpers import get_dynamic_fields, get_employee_tags

try:
    import openpyxl
except ImportError:
    openpyxl = None

excel_bp = Blueprint('excel', __name__, url_prefix='/excel')

@excel_bp.route('/export')
def export_employees_excel():
    if openpyxl is None:
        flash('مكتبة openpyxl غير مثبتة. استخدم pip install openpyxl', 'error')
        return redirect(url_for('dashboard.dashboard'))
    db = get_db()
    fields = get_dynamic_fields()
    employees = db.execute('SELECT * FROM employees').fetchall()

    wb = openpyxl.Workbook()
    ws = wb.active
    ws.title = "الموظفين"

    headers = ['id'] + [f['original_title'] for f in fields] + ['target_service_days', 'contract_end_date', 'tags']
    for col_num, header in enumerate(headers, 1):
        ws.cell(row=1, column=col_num, value=header)

    for row_num, emp in enumerate(employees, 2):
        ws.cell(row=row_num, column=1, value=emp['id'])
        for col_idx, f in enumerate(fields, 2):
            ws.cell(row=row_num, column=col_idx, value=emp[f['column_name']])
        ws.cell(row=row_num, column=len(fields) + 2, value=emp['target_service_days'])
        ws.cell(row=row_num, column=len(fields) + 3, value=emp['contract_end_date'])
        tags = ', '.join(get_employee_tags(emp['id']))
        ws.cell(row=row_num, column=len(fields) + 4, value=tags)

    output = io.BytesIO()
    wb.save(output)
    output.seek(0)
    return send_file(output, mimetype='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
                     as_attachment=True, download_name='employees.xlsx')

@excel_bp.route('/import', methods=['GET', 'POST'])
def import_employees_excel():
    if request.method == 'POST':
        if openpyxl is None:
            flash('مكتبة openpyxl غير مثبتة', 'error')
            return redirect(url_for('dashboard.dashboard'))
        file = request.files.get('file')
        if not file:
            flash('الرجاء اختيار ملف', 'error')
            return render_template('import_excel_form.html')
        try:
            wb = openpyxl.load_workbook(file)
            ws = wb.active
            headers = [cell.value for cell in ws[1]]
            fields = get_dynamic_fields()
            db = get_db()
            from .routes_export_import import import_employee_row
            key_title = fields[0]['original_title'] if fields else None
            key_col = fields[0]['column_name'] if fields else None
            for row in ws.iter_rows(min_row=2, values_only=True):
                if not any(row):
                    continue
                row_dict = dict(zip(headers, row))
                import_employee_row(db, row_dict, key_title, key_col, fields)
            db.commit()
            flash('تم استيراد الموظفين بنجاح', 'success')
        except Exception as e:
            flash(f'خطأ: {e}', 'error')
        return redirect(url_for('employees.list'))
    return render_template('import_excel_form.html')