package com.musajjil.app.domain

object EmployeeValidation {
    fun number(value: String) = DateText.normalizeDigits(value).replace('٫', '.').toBigDecimalOrNull()
    fun value(employee: Employee, field: FieldDefinition): String = when (field.builtinKey) {
        "Name" -> employee.name
        "Number" -> employee.number
        "NationalId" -> employee.nationalId
        "Department" -> employee.department
        "JobTitle" -> employee.jobTitle
        "Phone" -> employee.phone
        "EmploymentStart" -> employee.employmentStart?.let(DateText::format).orEmpty()
        else -> employee.customValues[field.id].orEmpty()
    }
    fun invalidFields(employee: Employee, fields: List<FieldDefinition>): List<FieldDefinition> = fields.filter { field ->
        if (field.archived) false else {
            val value = value(employee, field).trim()
            (field.required && value.isBlank()) || (value.isNotBlank() && when (field.type) {
                FieldType.Date -> DateText.parse(value) == null
                FieldType.Number -> number(value) == null
                else -> false // Retain a saved dropdown value even after its choice was removed.
            })
        }
    }
}
