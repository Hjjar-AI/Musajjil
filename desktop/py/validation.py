# py/validation.py
from marshmallow import Schema, fields, validate, ValidationError, validates_schema
import datetime

def validate_date_format(value):
    if not value:
        return
    try:
        datetime.datetime.strptime(value, '%Y-%m-%d')
    except ValueError:
        raise ValidationError('تاريخ غير صالح، استخدم الصيغة YYYY-MM-DD')

def validate_date_range(start, end):
    if start and end and start > end:
        raise ValidationError('تاريخ البداية يجب أن يكون قبل أو يساوي تاريخ النهاية')

class EmployeeSchema(Schema):
    id = fields.Int(dump_only=True)
    classification_id = fields.Int(allow_none=True)
    extra_data = fields.Dict(allow_none=True)
    target_service_days = fields.Float(allow_none=True)
    contract_end_date = fields.Str(validate=validate_date_format, allow_none=True)
    last_updated = fields.Str(dump_only=True)
    uncompensated_days = fields.Float(allow_none=True)

class LeaveSchema(Schema):
    employee_id = fields.Int(required=True)
    vacation_type_id = fields.Int(required=True)
    start_date = fields.Str(required=True, validate=validate_date_format)
    end_date = fields.Str(required=True, validate=validate_date_format)
    days_taken = fields.Float(allow_none=True)
    notes = fields.Str(allow_none=True)
    
    @validates_schema
    def validate_dates(self, data, **kwargs):
        start = data.get('start_date')
        end = data.get('end_date')
        if start and end:
            validate_date_range(start, end)

class LatenessSchema(Schema):
    employee_id = fields.Int(required=True)
    date = fields.Str(required=True, validate=validate_date_format)
    hours = fields.Float(required=True, validate=validate.Range(min=0.5, max=24))
    notes = fields.Str(allow_none=True)
    vacation_type_id = fields.Int(required=True)

class EvaluationSchema(Schema):
    employee_id = fields.Int(required=True)
    evaluator_name = fields.Str(required=True, validate=validate.Length(min=2))
    evaluation_date = fields.Str(required=True, validate=validate_date_format)
    notes = fields.Str(allow_none=True)

class AdvancedEvaluationSchema(Schema):
    employee_id = fields.Int(required=True)
    tool_id = fields.Int(required=True)
    evaluator_name = fields.Str(required=True, validate=validate.Length(min=2))
    evaluation_date = fields.Str(required=True, validate=validate_date_format)
    developmental_stage = fields.Str(validate=validate.OneOf(['Basic', 'Proficient', 'Advanced']))
    what_done_well = fields.Str(allow_none=True)
    suggestions = fields.Str(allow_none=True)
    agreed_actions = fields.Str(allow_none=True)
    supervisor_name = fields.Str(allow_none=True)
    notes = fields.Str(allow_none=True)