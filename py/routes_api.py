# py/routes_api.py
from flask import Blueprint, request, jsonify
from flask_jwt_extended import (
    create_access_token, create_refresh_token,
    jwt_required, get_jwt_identity, get_jwt
)
from werkzeug.security import check_password_hash
from .database import get_db
from .employee_helpers import get_dynamic_fields, get_name_column

api_bp = Blueprint('api', __name__, url_prefix='/api')

@api_bp.route('/login', methods=['POST'])
def api_login():
    data = request.get_json()
    username = data.get('username', '').strip()
    password = data.get('password', '').strip()
    if not username or not password:
        return jsonify({'error': 'Username and password required'}), 400
    db = get_db()
    user = db.execute('SELECT * FROM users WHERE username=?', (username,)).fetchone()
    if user and check_password_hash(user['password'], password):
        access_token = create_access_token(identity=str(user['id']), additional_claims={'role': user['role']})
        refresh_token = create_refresh_token(identity=str(user['id']))
        return jsonify({
            'access_token': access_token,
            'refresh_token': refresh_token,
            'role': user['role']
        }), 200
    return jsonify({'error': 'Invalid credentials'}), 401

@api_bp.route('/refresh', methods=['POST'])
@jwt_required(refresh=True)
def refresh():
    """Generate a new access token using a refresh token."""
    current_user_id = get_jwt_identity()
    # Optionally, you could fetch the latest role from the database
    db = get_db()
    user = db.execute('SELECT role FROM users WHERE id=?', (current_user_id,)).fetchone()
    role = user['role'] if user else 'viewer'
    new_access_token = create_access_token(identity=current_user_id, additional_claims={'role': role})
    return jsonify({'access_token': new_access_token}), 200

@api_bp.route('/employees', methods=['GET'])
@jwt_required()
def api_get_employees():
    db = get_db()
    name_col = get_name_column() or 'f_1'
    # Exclude soft-deleted employees
    employees = db.execute(f'SELECT id, {name_col} AS name FROM employees WHERE deleted_at IS NULL').fetchall()
    return jsonify([{'id': e['id'], 'name': e['name']} for e in employees])

@api_bp.route('/employees/<int:emp_id>', methods=['GET'])
@jwt_required()
def api_get_employee(emp_id):
    db = get_db()
    fields = get_dynamic_fields()
    emp = db.execute('SELECT * FROM employees WHERE id=? AND deleted_at IS NULL', (emp_id,)).fetchone()
    if not emp:
        return jsonify({'error': 'Employee not found'}), 404
    result = {'id': emp['id']}
    for f in fields:
        result[f['original_title']] = emp[f['column_name']]
    return jsonify(result)

@api_bp.route('/employees/<int:emp_id>/leaves', methods=['GET'])
@jwt_required()
def api_get_employee_leaves(emp_id):
    db = get_db()
    leaves = db.execute('''
        SELECT ll.*, vt.name as type_name FROM leave_log ll
        JOIN vacation_types vt ON ll.vacation_type_id = vt.id
        WHERE ll.employee_id = ? ORDER BY ll.start_date DESC
    ''', (emp_id,)).fetchall()
    return jsonify([dict(l) for l in leaves])

@api_bp.route('/leaves', methods=['POST'])
@jwt_required()
def api_create_leave():
    data = request.get_json()
    required = ['employee_id', 'vacation_type_id', 'start_date']
    for field in required:
        if field not in data:
            return jsonify({'error': f'Missing {field}'}), 400
    db = get_db()
    emp = db.execute('SELECT id FROM employees WHERE id=? AND deleted_at IS NULL', (data['employee_id'],)).fetchone()
    if not emp:
        return jsonify({'error': 'Employee not found'}), 404
    try:
        db.execute('''
            INSERT INTO leave_log (employee_id, vacation_type_id, start_date, end_date, days_taken, notes)
            VALUES (?, ?, ?, ?, ?, ?)
        ''', (data['employee_id'], data['vacation_type_id'], data['start_date'],
              data.get('end_date', data['start_date']), data.get('days_taken', 0), data.get('notes', '')))
        db.commit()
        return jsonify({'message': 'Leave created successfully'}), 201
    except Exception as e:
        db.rollback()
        return jsonify({'error': str(e)}), 500