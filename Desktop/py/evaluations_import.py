# py/evaluations_import.py
import io
import csv
import datetime
import pandas as pd
from flask import render_template, request, redirect, url_for, flash, session
from werkzeug.utils import secure_filename
from .database import get_db
from .employee_helpers import get_dynamic_fields
from .evaluation_import_helpers import allowed_file, read_file_data, find_column, get_employee_key_field, get_available_criteria, ImportResult
from .evaluations_core import evaluations_bp

@evaluations_bp.route('/import/wizard', methods=['GET', 'POST'])
def import_wizard():
    import pandas as pd
    db = get_db()
    classification_id = request.args.get('classification_id', type=int)
    
    if request.method == 'POST':
        if 'file' not in request.files:
            flash('الرجاء اختيار ملف', 'error')
            return redirect(request.url)
        file = request.files['file']
        if file.filename == '' or not allowed_file(file.filename):
            flash('نوع الملف غير مدعوم. استخدم Excel أو CSV', 'error')
            return redirect(request.url)
        file_type = 'excel' if file.filename.endswith(('.xlsx', '.xls')) else 'csv'
        try:
            df = read_file_data(file, file_type)
        except Exception as e:
            flash(f'خطأ في قراءة الملف: {str(e)}', 'error')
            return redirect(request.url)
        if df.empty:
            flash('الملف فارغ أو لا يحتوي على بيانات', 'error')
            return redirect(request.url)
        session['import_df'] = df.to_json(orient='split')
        session['import_filename'] = secure_filename(file.filename)
        session['import_file_type'] = file_type
        preview = df.head(5).to_html(classes='table table-sm', border=0, index=False)
        key_column = get_employee_key_field()
        criteria_map = get_available_criteria(db, classification_id)
        expected_columns = ['الرقم الوظيفي', 'تاريخ التقييم', 'اسم المقيم'] + list(criteria_map.keys())
        detected = {}
        for exp in expected_columns:
            found = find_column(df, [exp, exp.replace(' ', ''), exp.replace(' ', '_')])
            detected[exp] = found
        return render_template('evaluations_import_wizard.html',
                               preview=preview,
                               columns=df.columns.tolist(),
                               detected=detected,
                               expected_columns=expected_columns,
                               classification_id=classification_id,
                               filename=session['import_filename'])
    classifications = db.execute('SELECT * FROM classifications').fetchall()
    return render_template('evaluations_import_upload.html', classifications=classifications)

@evaluations_bp.route('/import/do', methods=['POST'])
def import_do():
    import pandas as pd
    if 'import_df' not in session:
        flash('لم يتم رفع أي ملف أو انتهت الجلسة', 'error')
        return redirect(url_for('evaluations.import_wizard'))
    df = pd.read_json(session['import_df'], orient='split')
    column_mapping = {}
    for key in request.form:
        if key.startswith('col_'):
            field = key[4:]
            column_mapping[field] = request.form[key]
    employee_col = column_mapping.get('الرقم الوظيفي')
    date_col = column_mapping.get('تاريخ التقييم')
    evaluator_col = column_mapping.get('اسم المقيم')
    criterion_cols = {k: v for k, v in column_mapping.items() if k not in ['الرقم الوظيفي', 'تاريخ التقييم', 'اسم المقيم']}
    if not employee_col:
        flash('يرجى تحديد عمود الرقم الوظيفي', 'error')
        return redirect(request.referrer)
    db = get_db()
    key_field = get_employee_key_field()
    result = ImportResult()
    result.total_rows = len(df)
    valid_criteria = {}
    for crit_name, col_name in criterion_cols.items():
        crit = db.execute('SELECT id FROM evaluation_criteria WHERE name=?', (crit_name,)).fetchone()
        if crit:
            valid_criteria[crit_name] = {'id': crit['id'], 'column': col_name}
        else:
            result.add_error(0, f'المعيار "{crit_name}" غير موجود في النظام')
    dry_run = 'dry_run' in request.form
    for idx, row in df.iterrows():
        row_num = idx + 2
        job_num = str(row[employee_col]).strip() if pd.notna(row[employee_col]) else ''
        if not job_num:
            result.add_error(row_num, 'الرقم الوظيفي فارغ')
            continue
        emp = db.execute(f'SELECT id, classification_id FROM employees WHERE {key_field}=? AND deleted_at IS NULL', (job_num,)).fetchone()
        if not emp:
            result.add_error(row_num, f'الموظف ذو الرقم {job_num} غير موجود')
            continue
        eval_date = None
        if date_col and pd.notna(row[date_col]):
            try:
                if isinstance(row[date_col], (pd.Timestamp, datetime.datetime)):
                    eval_date = row[date_col].strftime('%Y-%m-%d')
                else:
                    eval_date = datetime.datetime.strptime(str(row[date_col]), '%Y-%m-%d').strftime('%Y-%m-%d')
            except:
                result.add_error(row_num, 'تاريخ التقييم غير صالح')
                continue
        else:
            eval_date = datetime.date.today().strftime('%Y-%m-%d')
        evaluator = str(row[evaluator_col]).strip() if evaluator_col and pd.notna(row[evaluator_col]) else 'غير محدد'
        if dry_run:
            result.add_success()
            continue
        try:
            cur = db.execute('''
                INSERT INTO evaluations (employee_id, evaluator_name, evaluation_date, notes)
                VALUES (?, ?, ?, ?)
            ''', (emp['id'], evaluator, eval_date, 'استيراد متقدم'))
            eval_id = cur.lastrowid
            for crit_name, crit_info in valid_criteria.items():
                col_name = crit_info['column']
                score = row[col_name] if pd.notna(row[col_name]) else None
                if score is not None:
                    try:
                        score = float(score)
                        if 1 <= score <= 5:
                            db.execute('''
                                INSERT INTO evaluation_scores (evaluation_id, criterion_id, score)
                                VALUES (?, ?, ?)
                            ''', (eval_id, crit_info['id'], score))
                        else:
                            result.add_error(row_num, f'درجة المعيار "{crit_name}" خارج المجال (1-5): {score}')
                    except:
                        result.add_error(row_num, f'قيمة غير رقمية للمعيار "{crit_name}": {score}')
            result.add_success()
            db.commit()
        except Exception as e:
            db.rollback()
            result.add_error(row_num, f'خطأ في قاعدة البيانات: {str(e)}')
    session.pop('import_df', None)
    session.pop('import_filename', None)
    session.pop('import_file_type', None)
    if result.success_count > 0:
        flash(f'تم استيراد {result.success_count} تقييم بنجاح', 'success')
    if result.error_count > 0:
        for row_num, err in result.errors[:10]:
            flash(f'خطأ في الصف {row_num}: {err}', 'error')
        if len(result.errors) > 10:
            flash(f'و {len(result.errors) - 10} أخطاء أخرى...', 'warning')
    return redirect(url_for('evaluations.general'))

@evaluations_bp.route('/import', methods=['GET'])
def import_evaluations_redirect():
    return redirect(url_for('evaluations.import_wizard'))