from flask import Blueprint, render_template

manual_bp = Blueprint('manual', __name__, url_prefix='/manual')

@manual_bp.route('/')
def index():
    return render_template('manual.html')