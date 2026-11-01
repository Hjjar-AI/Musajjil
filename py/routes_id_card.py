# py/routes_id_card.py
import io
import qrcode
from flask import Blueprint, render_template, send_file, url_for
from .database import get_db
from .employee_helpers import get_dynamic_fields, get_name_column

id_card_bp = Blueprint('id_card', __name__, url_prefix='/id-card')

@id_card_bp.route('/<int:employee_id>')
def generate(employee_id):
    db = get_db()
    fields = get_dynamic_fields()
    emp = db.execute('SELECT * FROM employees WHERE id=?', (employee_id,)).fetchone()
    if not emp:
        return "موظف غير موجود", 404
    
    # جمع البيانات
    data = {f['original_title']: emp[f['column_name']] or '' for f in fields}
    name = f"{data.get('الاسم الأول', '')} {data.get('الكنية', '')}".strip()
    job_num = data.get('الرقم الوظيفي', employee_id)
    dept = data.get('القسم', '')
    classification = ''
    if emp['classification_id']:
        cls = db.execute('SELECT name FROM classifications WHERE id=?', (emp['classification_id'],)).fetchone()
        if cls:
            classification = cls['name']
    
    # إنشاء QR Code
    qr = qrcode.QRCode(box_size=3, border=1)
    qr.add_data(f"موظف: {name}\nرقم: {job_num}\nقسم: {dept}")
    qr.make(fit=True)
    qr_img = qr.make_image(fill_color="black", back_color="white")
    img_io = io.BytesIO()
    qr_img.save(img_io, 'PNG')
    img_io.seek(0)
    import base64
    qr_base64 = base64.b64encode(img_io.getvalue()).decode()
    
    return render_template('id_card.html', name=name, job_num=job_num, dept=dept,
                           classification=classification, qr_base64=qr_base64,
                           employee_id=employee_id)