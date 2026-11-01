# py/role_permissions.py

ROLE_ALLOWED_PREFIXES = {
    'admin': {'*'},
    'registerar': {
        'employees.',
        'tags.',
        'classifications.',
        'vacation_types.',
        'departments.',
        'holidays.',
        'calendar.',
        'export_import.',
        'reports.',
        'dashboard.',
        'auth.',
        'settings.',
        'settings_page.',
        'maintenance.',
        'bulk_leave.',
        'scenario.',
        'manual.',
        'excel.',
        'employee_import.',
        'id_card.',
        'okr.',
        'training.',
        'smart_holidays.',
        'wizard.',
    },
    'supervisor': {
        'evaluations.',
        'eval_criteria.list',
        'employees.list',
        'employees.detail',
        'employees.search',
        'dashboard.',
        'auth.',
        'settings_page.',
        'manual.',
        'reports.individual',
        'reports.individual_pdf',
        'reports.individual_word',
        'calendar.show',
        'calendar.print_view',
        'training.list_courses',
        'training.enrollments',
        'okr.list_objectives',
        'okr.add_objective',
        'okr.add_key_result',
        'okr.update_key_result',
    },
    'viewer': {
        'employees.list',
        'employees.detail',
        'dashboard.',
        'reports.individual',
        'reports.individual_select',
        'calendar.show',
        'calendar.print_view',
        'auth.',
        'settings_page.',
        'manual.',
        'training.list_courses',
        'training.enrollments',
        'okr.list_objectives',
    }
}

ROLE_BLACKLIST = {
    'registerar': {
        'maintenance.lock_period',
        'evaluations.import_evaluations',
    },
    'supervisor': {},
    'viewer': {
        'maintenance.lock_period',
        'evaluations.add',
        'evaluations.delete',
        'evaluations.import_wizard',
        'evaluations.import_do',
    }
}

def is_endpoint_allowed(role, endpoint):
    if role not in ROLE_ALLOWED_PREFIXES:
        return False
    allowed = ROLE_ALLOWED_PREFIXES[role]
    if '*' in allowed:
        return True
    blacklist = ROLE_BLACKLIST.get(role, set())
    for bl in blacklist:
        if endpoint.startswith(bl):
            return False
    for prefix in allowed:
        if endpoint.startswith(prefix):
            return True
    return False