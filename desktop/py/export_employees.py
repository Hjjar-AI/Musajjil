# py/export_employees.py
import io
import csv
import json
from flask import Blueprint, request, jsonify, send_file
from .database import get_db
from .employee_helpers import get_dynamic_fields, get_employee_tags

export_import_bp = Blueprint('export_import', __name__)

@export_import_bp.route('/export/employees')
def export_employees():
    fmt = request.args.get('format', 'csv')
    db = get_db()
    fields = get_dynamic_fields()
    employees = db.execute('SELECT * FROM employees').fetchall()
    if fmt == 'json':
        data = []
        for emp in employees:
            entry = {'id': emp['id']}
            for f in fields:
                entry[f['original_title']] = emp[f['column_name']]
            entry['target_service_days'] = emp['target_service_days']
            entry['extra_data'] = emp['extra_data']
            entry['tags'] = get_employee_tags(emp['id'])
            data.append(entry)
        return jsonify(data)
    else:
        si = io.StringIO()
        writer = csv.writer(si)
        headers = ['id'] + [f['original_title'] for f in fields] + ['target_service_days', 'tags']
        writer.writerow(headers)
        for emp in employees:
            row = [emp['id']] + [emp[f['column_name']] for f in fields]
            row.append(emp['target_service_days'])
            row.append(', '.join(get_employee_tags(emp['id'])))
            writer.writerow(row)
        output = si.getvalue().encode('utf-8-sig')
        return send_file(io.BytesIO(output), mimetype='text/csv', as_attachment=True, download_name='employees.csv')

@export_import_bp.route('/export/employee/<int:id>')
def export_employee(id):
    fmt = request.args.get('format', 'csv')
    db = get_db()
    emp = db.execute('SELECT * FROM employees WHERE id=?', (id,)).fetchone()
    if not emp:
        return 'Not found', 404
    fields = get_dynamic_fields()
    if fmt == 'json':
        entry = {'id': emp['id']}
        for f in fields:
            entry[f['original_title']] = emp[f['column_name']]
        entry['target_service_days'] = emp['target_service_days']
        entry['extra_data'] = emp['extra_data']
        entry['tags'] = get_employee_tags(id)
        return jsonify(entry)
    else:
        si = io.StringIO()
        writer = csv.writer(si)
        headers = ['id'] + [f['original_title'] for f in fields] + ['target_service_days', 'tags']
        writer.writerow(headers)
        row = [emp['id']] + [emp[f['column_name']] for f in fields]
        row.append(emp['target_service_days'])
        row.append(', '.join(get_employee_tags(id)))
        writer.writerow(row)
        output = si.getvalue().encode('utf-8-sig')
        return send_file(io.BytesIO(output), mimetype='text/csv', as_attachment=True, download_name=f'employee_{id}.csv')