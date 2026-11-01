import datetime
import io
from flask import Blueprint, render_template, request, redirect, url_for, flash, send_file
from .database import get_db
from .employee_helpers import get_name_column, get_dynamic_fields
from .vacation_balance import compute_vacation_balance
from .utils import parse_date_robust

try:
    from docx import Document
    from docx.shared import Inches, Pt
    from docx.enum.text import WD_ALIGN_PARAGRAPH
    DOCX_AVAILABLE = True
except ImportError:
    DOCX_AVAILABLE = False

reports_bp = Blueprint('reports', __name__, url_prefix='/reports')

def _format_date_ar(date_str):
    if not date_str:
        return ''
    dt = parse_date_robust(date_str)
    if dt:
        return datetime.datetime.strptime(dt, '%Y-%m-%d').strftime('%d/%m/%Y')
    return date_str

@reports_bp.route('/individual-select')
def individual_select():
    db = get_db()
    name_col = get_name_column()
    employees = []
    if name_col:
        employees = db.execute(f'SELECT id, {name_col} AS name FROM employees WHERE deleted_at IS NULL ORDER BY {name_col}').fetchall()
    return render_template('reports_individual_select.html', employees=employees)

@reports_bp.route('/individual')
def individual():
    employee_id = request.args.get('employee_id', type=int)
    include_previous = request.args.get('include_previous', 'false').lower() == 'true'
    db = get_db()
    if employee_id:
        emp = db.execute('SELECT * FROM employees WHERE id=? AND deleted_at IS NULL', (employee_id,)).fetchone()
        if not emp:
            flash('الموظف غير موجود', 'error')
            return redirect(url_for('reports.individual_select'))
        fields = get_dynamic_fields()
        emp_data = {f['original_title']: (emp[f['column_name']] or '') for f in fields}
        leave_log_rows = db.execute(
            'SELECT ll.*, vt.name as vt_name FROM leave_log ll JOIN vacation_types vt ON ll.vacation_type_id = vt.id WHERE ll.employee_id=? ORDER BY ll.start_date DESC',
            (employee_id,)
        ).fetchall()
        leave_log = [dict(row) for row in leave_log_rows]
        vac_types = db.execute('SELECT * FROM vacation_types').fetchall()
        current_year = datetime.date.today().year

        balances = []
        for vt in vac_types:
            ent, taken, bal = compute_vacation_balance(employee_id, vt['id'], current_year)
            balances.append({'type': vt['name'], 'entitled': ent, 'carried': 0, 'taken': taken, 'balance': bal})

        yearly_breakdown = {}
        if include_previous:
            years = db.execute('SELECT DISTINCT strftime("%Y", start_date) as yr FROM leave_log WHERE employee_id=? ORDER BY yr DESC', (employee_id,)).fetchall()
            for year_row in years:
                yr = int(year_row['yr'])
                year_data = []
                for vt in vac_types:
                    total = db.execute('''
                        SELECT COALESCE(SUM(days_taken), 0) FROM leave_log
                        WHERE employee_id=? AND vacation_type_id=? AND strftime("%Y", start_date)=?
                    ''', (employee_id, vt['id'], str(yr))).fetchone()[0]
                    year_data.append({'type': vt['name'], 'total': total})
                yearly_breakdown[yr] = year_data

        return render_template('reports_individual.html',
                               emp_data=emp_data,
                               balances=balances,
                               leave_log=leave_log,
                               include_previous=include_previous,
                               yearly_breakdown=yearly_breakdown,
                               employee_id=employee_id)
    else:
        return redirect(url_for('reports.individual_select'))

# PDF generation removed – only Word export remains
@reports_bp.route('/individual/pdf')
def individual_pdf():
    flash('عذراً، ميزة تصدير PDF غير متاحة حالياً. الرجاء استخدام تصدير Word أو طباعة الصفحة من المتصفح.', 'warning')
    return redirect(url_for('reports.individual', employee_id=request.args.get('employee_id', type=int),
                            include_previous=request.args.get('include_previous', 'false')))

@reports_bp.route('/individual/word')
def individual_word():
    if not DOCX_AVAILABLE:
        flash('مكتبة python-docx غير مثبتة. قم بتثبيتها باستخدام pip install python-docx', 'error')
        return redirect(url_for('reports.individual_select'))
    employee_id = request.args.get('employee_id', type=int)
    include_previous = request.args.get('include_previous', 'false').lower() == 'true'
    if not employee_id:
        flash('الرجاء اختيار موظف أولاً', 'error')
        return redirect(url_for('reports.individual_select'))
    db = get_db()
    emp = db.execute('SELECT * FROM employees WHERE id=? AND deleted_at IS NULL', (employee_id,)).fetchone()
    if not emp:
        flash('الموظف غير موجود', 'error')
        return redirect(url_for('reports.individual_select'))
    fields = get_dynamic_fields()
    emp_data = {f['original_title']: (emp[f['column_name']] or '') for f in fields}
    leave_log_rows = db.execute(
        'SELECT ll.*, vt.name as vt_name FROM leave_log ll JOIN vacation_types vt ON ll.vacation_type_id = vt.id WHERE ll.employee_id=? ORDER BY ll.start_date DESC',
        (employee_id,)
    ).fetchall()
    leave_log = [dict(row) for row in leave_log_rows]
    vac_types = db.execute('SELECT * FROM vacation_types').fetchall()
    current_year = datetime.date.today().year
    balances = []
    for vt in vac_types:
        ent, taken, bal = compute_vacation_balance(employee_id, vt['id'], current_year)
        balances.append({'type': vt['name'], 'entitled': ent, 'carried': 0, 'taken': taken, 'balance': bal})

    yearly_breakdown = {}
    if include_previous:
        years = db.execute('SELECT DISTINCT strftime("%Y", start_date) as yr FROM leave_log WHERE employee_id=? ORDER BY yr DESC', (employee_id,)).fetchall()
        for year_row in years:
            yr = int(year_row['yr'])
            year_data = []
            for vt in vac_types:
                total = db.execute('''
                    SELECT COALESCE(SUM(days_taken), 0) FROM leave_log
                    WHERE employee_id=? AND vacation_type_id=? AND strftime("%Y", start_date)=?
                ''', (employee_id, vt['id'], str(yr))).fetchone()[0]
                year_data.append({'type': vt['name'], 'total': total})
            yearly_breakdown[yr] = year_data

    # Create Word document
    doc = Document()
    title = doc.add_heading(f'تقرير الموظف: {emp_data.get("الاسم الأول", "")} {emp_data.get("الكنية", "")}', 0)
    title.alignment = WD_ALIGN_PARAGRAPH.CENTER
    doc.add_paragraph(f'تاريخ التقرير: {datetime.datetime.now().strftime("%d/%m/%Y %H:%M")}')
    doc.add_paragraph()
    doc.add_heading('البيانات الأساسية', level=1)
    table = doc.add_table(rows=1, cols=2)
    table.style = 'Light Grid Accent 1'
    for key, val in emp_data.items():
        row = table.add_row()
        row.cells[0].text = key
        row.cells[1].text = str(val)
    classification_name = ''
    if emp['classification_id']:
        cls = db.execute('SELECT name FROM classifications WHERE id=?', (emp['classification_id'],)).fetchone()
        if cls:
            classification_name = cls['name']
    row = table.add_row()
    row.cells[0].text = 'التصنيف'
    row.cells[1].text = classification_name
    from .employee_helpers import get_employee_tags
    tags = ', '.join(get_employee_tags(employee_id))
    row = table.add_row()
    row.cells[0].text = 'الوسوم'
    row.cells[1].text = tags
    doc.add_paragraph()
    doc.add_heading('أرصدة الإجازات الحالية', level=1)
    table = doc.add_table(rows=1, cols=5)
    table.style = 'Light Grid Accent 1'
    headers = ['نوع الإجازة', 'الاستحقاق', 'المرحَّل', 'المستخدم', 'المتبقي']
    for i, header in enumerate(headers):
        table.rows[0].cells[i].text = header
    for b in balances:
        row = table.add_row()
        row.cells[0].text = b['type']
        row.cells[1].text = str(b['entitled'])
        row.cells[2].text = str(b['carried'])
        row.cells[3].text = str(b['taken'])
        row.cells[4].text = str(b['balance'])

    if include_previous and yearly_breakdown:
        doc.add_paragraph()
        doc.add_heading('إجمالي الإجازات المستهلكة حسب السنة', level=1)
        for year, year_data in sorted(yearly_breakdown.items(), reverse=True):
            doc.add_heading(f'سنة {year}', level=2)
            table = doc.add_table(rows=1, cols=2)
            table.style = 'Light Grid Accent 1'
            table.rows[0].cells[0].text = 'نوع الإجازة'
            table.rows[0].cells[1].text = 'إجمالي الأيام'
            for vt in year_data:
                if vt['total'] > 0:
                    row = table.add_row()
                    row.cells[0].text = vt['type']
                    row.cells[1].text = str(vt['total'])

    doc.add_paragraph()
    doc.add_heading('سجل الإجازات', level=1)
    table = doc.add_table(rows=1, cols=5)
    table.style = 'Light Grid Accent 1'
    headers = ['النوع', 'من', 'إلى', 'الأيام', 'ملاحظات']
    for i, header in enumerate(headers):
        table.rows[0].cells[i].text = header
    for l in leave_log:
        row = table.add_row()
        row.cells[0].text = l['vt_name']
        row.cells[1].text = _format_date_ar(l['start_date'])
        row.cells[2].text = _format_date_ar(l['end_date'])
        row.cells[3].text = str(l['days_taken'])
        row.cells[4].text = l['notes'] or ''

    doc.add_paragraph()
    doc.add_paragraph(f'تم إنشاء هذا التقرير بواسطة نظام إدارة الموظفين')
    file_stream = io.BytesIO()
    doc.save(file_stream)
    file_stream.seek(0)
    return send_file(file_stream, mimetype='application/vnd.openxmlformats-officedocument.wordprocessingml.document',
                     as_attachment=True, download_name=f'employee_{employee_id}_report.docx')