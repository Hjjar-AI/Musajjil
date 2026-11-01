import datetime
from flask import render_template, request, jsonify
from .database import get_db
from .employee_helpers import get_dynamic_fields, get_name_column, get_search_columns
from .employees_core import employees_bp
from .utils import parse_date_robust
from .config import PAGINATION_PER_PAGE

@employees_bp.route('/employees')
def list():
    db = get_db()
    fields = get_dynamic_fields()
    page = request.args.get('page', 1, type=int)
    per_page = PAGINATION_PER_PAGE
    offset = (page - 1) * per_page
    tag_ids = request.args.getlist('tag', type=int)
    sort_by = request.args.get('sort', 'id')
    order = request.args.get('order', 'asc')
    search_text = request.args.get('search', '').strip()
    department = request.args.get('department', '').strip()
    classification_id = request.args.get('classification', type=int)

    age_min = request.args.get('age_min', type=int)
    age_max = request.args.get('age_max', type=int)
    hire_from = request.args.get('hire_date_from', '').strip()
    hire_to = request.args.get('hire_date_to', '').strip()

    birth_col = None
    hire_col = None
    for f in fields:
        if f['original_title'] == 'تاريخ الولادة':
            birth_col = f['column_name']
        elif f['original_title'] == 'تاريخ المباشرة الأخيرة':
            hire_col = f['column_name']

    allowed_sort = {'id', 'service_days'} | {f['column_name'] for f in fields}
    if sort_by not in allowed_sort:
        sort_by = 'id'

    query = 'SELECT employees.id'
    for f in fields:
        query += f', {f["column_name"]}'
    query += ', classification_id, target_service_days, contract_end_date, last_updated, (SELECT COALESCE(SUM(julianday(end_date)-julianday(start_date)+1),0) FROM employee_work_periods WHERE employee_id=employees.id) AS service_days FROM employees WHERE deleted_at IS NULL'
    params = []

    search_cols = get_search_columns()
    if search_text and search_cols:
        conditions = []
        normalized = search_text.replace('أ', 'ا').replace('إ', 'ا')
        for col in search_cols:
            conditions.append(f"REPLACE(REPLACE({col}, 'أ', 'ا'), 'إ', 'ا') LIKE ?")
            params.append(f'%{normalized}%')
        query += ' AND (' + ' OR '.join(conditions) + ')'

    if department:
        for f in fields:
            if f['original_title'].strip() == 'القسم':
                query += f' AND {f["column_name"]} = ?'
                params.append(department)
                break

    if classification_id:
        query += ' AND classification_id = ?'
        params.append(classification_id)

    if tag_ids:
        placeholders = ','.join('?' * len(tag_ids))
        query += f' AND id IN (SELECT employee_id FROM employee_tags WHERE tag_id IN ({placeholders}) GROUP BY employee_id HAVING COUNT(DISTINCT tag_id)=?)'
        params.extend(tag_ids)
        params.append(len(tag_ids))

    if birth_col and (age_min is not None or age_max is not None):
        today = datetime.date.today()
        if age_min is not None:
            min_birth = today.replace(year=today.year - (age_max if age_max else age_min))
            query += f' AND date({birth_col}) <= ?'
            params.append(min_birth.strftime('%Y-%m-%d'))
        if age_max is not None:
            max_birth = today.replace(year=today.year - (age_min if age_min else age_max))
            query += f' AND date({birth_col}) >= ?'
            params.append(max_birth.strftime('%Y-%m-%d'))

    if hire_col:
        if hire_from:
            hire_from_parsed = parse_date_robust(hire_from)
            if hire_from_parsed:
                query += f' AND date({hire_col}) >= ?'
                params.append(hire_from_parsed)
        if hire_to:
            hire_to_parsed = parse_date_robust(hire_to)
            if hire_to_parsed:
                query += f' AND date({hire_col}) <= ?'
                params.append(hire_to_parsed)

    count_query = 'SELECT COUNT(*) FROM employees WHERE deleted_at IS NULL'
    count_params = []
    if search_text and search_cols:
        count_conditions = []
        normalized = search_text.replace('أ', 'ا').replace('إ', 'ا')
        for col in search_cols:
            count_conditions.append(f"REPLACE(REPLACE({col}, 'أ', 'ا'), 'إ', 'ا') LIKE ?")
            count_params.append(f'%{normalized}%')
        count_query += ' AND (' + ' OR '.join(count_conditions) + ')'
    if department:
        for f in fields:
            if f['original_title'].strip() == 'القسم':
                count_query += f' AND {f["column_name"]} = ?'
                count_params.append(department)
                break
    if classification_id:
        count_query += ' AND classification_id = ?'
        count_params.append(classification_id)
    if tag_ids:
        placeholders = ','.join('?' * len(tag_ids))
        count_query += f' AND id IN (SELECT employee_id FROM employee_tags WHERE tag_id IN ({placeholders}) GROUP BY employee_id HAVING COUNT(DISTINCT tag_id)=?)'
        count_params.extend(tag_ids)
        count_params.append(len(tag_ids))
    if birth_col and (age_min is not None or age_max is not None):
        today = datetime.date.today()
        if age_min is not None:
            min_birth = today.replace(year=today.year - (age_max if age_max else age_min))
            count_query += f' AND date({birth_col}) <= ?'
            count_params.append(min_birth.strftime('%Y-%m-%d'))
        if age_max is not None:
            max_birth = today.replace(year=today.year - (age_min if age_min else age_max))
            count_query += f' AND date({birth_col}) >= ?'
            count_params.append(max_birth.strftime('%Y-%m-%d'))
    if hire_col:
        if hire_from:
            hire_from_parsed = parse_date_robust(hire_from)
            if hire_from_parsed:
                count_query += f' AND date({hire_col}) >= ?'
                count_params.append(hire_from_parsed)
        if hire_to:
            hire_to_parsed = parse_date_robust(hire_to)
            if hire_to_parsed:
                count_query += f' AND date({hire_col}) <= ?'
                count_params.append(hire_to_parsed)

    total = db.execute(count_query, count_params).fetchone()[0]
    total_pages = max(1, (total + per_page - 1) // per_page)

    query += f' ORDER BY {sort_by} {order} LIMIT ? OFFSET ?'
    params.extend([per_page, offset])

    employees = db.execute(query, params).fetchall()
    all_tags = db.execute('SELECT * FROM tags').fetchall()
    departments_list = []
    for f in fields:
        if f['original_title'].strip() == 'القسم':
            dept_col = f['column_name']
            departments_list = [r[0] for r in db.execute(f'SELECT DISTINCT {dept_col} FROM employees WHERE {dept_col} != "" AND {dept_col} IS NOT NULL AND deleted_at IS NULL').fetchall()]
            break
    classifications = db.execute('SELECT * FROM classifications').fetchall()

    # تمرير معاملات البحث بدون sort و order لتجنب التضارب
    query_args = request.args.to_dict(flat=True)
    base_args = {k: v for k, v in query_args.items() if k not in ('sort', 'order')}

    return render_template('employees_list.html', employees=employees, fields=fields,
                           all_tags=all_tags, selected_tags=tag_ids, sort_by=sort_by,
                           order=order, page=page, total_pages=total_pages,
                           departments=departments_list, classifications=classifications,
                           base_args=base_args)

@employees_bp.route('/api/employees/search')
def employee_search():
    q = request.args.get('q', '').strip()
    if not q:
        return jsonify([])
    db = get_db()
    search_cols = get_search_columns()
    if not search_cols:
        return jsonify([])
    normalized_q = q.replace('أ', 'ا').replace('إ', 'ا')
    conditions = []
    params = []
    for col in search_cols:
        conditions.append(f"REPLACE(REPLACE({col}, 'أ', 'ا'), 'إ', 'ا') LIKE ?")
        params.append(f'%{normalized_q}%')
    query = f"SELECT id, {search_cols[0]} AS name FROM employees WHERE deleted_at IS NULL AND ({' OR '.join(conditions)}) LIMIT 10"
    rows = db.execute(query, params).fetchall()
    return jsonify([{'id': r['id'], 'name': r['name']} for r in rows])