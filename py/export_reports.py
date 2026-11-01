# py/export_reports.py
import io
import csv
import json
import datetime
from flask import Blueprint, request, jsonify, send_file
from .database import get_db
from .export_employees import export_import_bp

@export_import_bp.route('/export/report')
def export_report():
    month = request.args.get('month', datetime.date.today().strftime('%Y-%m'))
    db = get_db()
    rows = db.execute('SELECT employees.id, leave_log.*, vacation_types.name as vt_name FROM leave_log JOIN employees ON leave_log.employee_id=employees.id JOIN vacation_types ON leave_log.vacation_type_id=vacation_types.id WHERE strftime("%Y-%m", start_date)=?', (month,)).fetchall()
    fmt = request.args.get('format', 'csv')
    if fmt == 'json':
        data = [dict(r) for r in rows]
        return jsonify(data)
    else:
        si = io.StringIO()
        writer = csv.writer(si)
        writer.writerow(['employee_id', 'vacation_type', 'start_date', 'end_date', 'days_taken', 'notes'])
        for r in rows:
            writer.writerow([r['employee_id'], r['vt_name'], r['start_date'], r['end_date'], r['days_taken'], r['notes']])
        output = si.getvalue().encode('utf-8-sig')
        return send_file(io.BytesIO(output), mimetype='text/csv', as_attachment=True, download_name=f'report_{month}.csv')

@export_import_bp.route('/export/full')
def export_full():
    db = get_db()
    tables = ['employee_fields', 'employees', 'classifications', 'classification_entitlements',
              'employee_entitlements', 'vacation_types', 'leave_log', 'tags', 'employee_tags',
              'employee_work_periods', 'evaluation_criteria', 'evaluations', 'evaluation_scores', 'backup_log']
    dump = {}
    for table in tables:
        rows = db.execute(f'SELECT * FROM {table}').fetchall()
        dump[table] = [dict(r) for r in rows]
    return jsonify(dump)