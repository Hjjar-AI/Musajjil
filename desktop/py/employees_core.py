# py/employees_core.py
import datetime
import re
from flask import Blueprint, redirect, url_for
from .database import get_db
from .employee_helpers import (
    get_dynamic_fields, get_classification_id, compute_service_days,
    get_employee_tags, get_name_column, get_search_columns,
    compute_target_days_from_years_months, breakdown_days_to_ym, breakdown_days_to_years_days
)
from .vacation_balance import get_cached_balance, invalidate_balance_cache
from .vacation_holidays import parse_date_robust
from .config import PAGINATION_PER_PAGE

employees_bp = Blueprint('employees', __name__)

# ============================================================
# IMPORTANT: Do NOT add a route for '/' here.
# The root URL is handled exclusively by app.route('/') in app.py
# to avoid infinite redirect loops.
# ============================================================

# No routes defined in this file – all employee routes are in separate modules.
# This file only contains the blueprint definition and shared helper imports.
# The actual routes are split across:
#   - employees_list.py
#   - employees_detail.py
#   - employees_manage.py
#   - employees_leave.py
#   - employees_entitlements.py
#   - employees_bulk.py