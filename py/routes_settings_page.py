from flask import Blueprint, render_template

settings_page_bp = Blueprint('settings_page', __name__, url_prefix='/user-settings')

@settings_page_bp.route('/')
def index():
    return render_template('user_settings.html')