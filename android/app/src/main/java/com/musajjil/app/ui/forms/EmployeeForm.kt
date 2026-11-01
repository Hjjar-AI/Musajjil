package com.musajjil.app.ui.forms

import androidx.annotation.StringRes
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.musajjil.app.R
import com.musajjil.app.domain.*
import com.musajjil.app.ui.components.*
import java.util.UUID

enum class EmployeeField { Name, Number, NationalId, Department, JobTitle, Phone, EmploymentStart }

data class EmployeeFieldSpec(val key: EmployeeField, @param:StringRes val label: Int,
    val required: Boolean = false, val keyboard: KeyboardType = KeyboardType.Text, val date: Boolean = false)

/** Initial form order, labels and input behavior are defined in one place. */
object EmployeeFormSchema {
    val fields = listOf(
        EmployeeFieldSpec(EmployeeField.Name, R.string.employee_name, required = true),
        EmployeeFieldSpec(EmployeeField.Number, R.string.employee_number),
        EmployeeFieldSpec(EmployeeField.NationalId, R.string.national_id),
        EmployeeFieldSpec(EmployeeField.Department, R.string.department),
        EmployeeFieldSpec(EmployeeField.JobTitle, R.string.job_title),
        EmployeeFieldSpec(EmployeeField.Phone, R.string.phone, keyboard = KeyboardType.Phone),
        EmployeeFieldSpec(EmployeeField.EmploymentStart, R.string.employment_start, date = true),
    )
}

@Composable
fun EmployeeForm(existing: Employee?, data: RegisterData, busy: Boolean, onDismiss: () -> Unit, onSave: (Employee) -> Unit) {
    val id = rememberSaveable { existing?.id ?: UUID.randomUUID().toString() }
    var values by rememberSaveable { mutableStateOf(mapOf(
        "Name" to existing?.name.orEmpty(), "Number" to existing?.number.orEmpty(), "NationalId" to existing?.nationalId.orEmpty(),
        "JobTitle" to existing?.jobTitle.orEmpty(), "Phone" to existing?.phone.orEmpty(),
        "EmploymentStart" to existing?.employmentStart?.let(DateText::format).orEmpty())) }
    var departmentId by rememberSaveable { mutableStateOf(existing?.departmentId.orEmpty()) }
    var classificationId by rememberSaveable { mutableStateOf(existing?.classificationId.orEmpty()) }
    var tagIds by rememberSaveable { mutableStateOf(existing?.tagIds ?: emptySet()) }
    var customValues by rememberSaveable { mutableStateOf(existing?.customValues ?: emptyMap()) }
    var showDuplicate by rememberSaveable { mutableStateOf(false) }
    fun field(key: String) = values[key].orEmpty().trim()
    val dateInput = field("EmploymentStart")
    val department = data.groups.firstOrNull { it.id == departmentId }?.name.orEmpty()
    fun candidate() = Employee(id, field("Name"), field("Number"), field("NationalId"), department, field("JobTitle"), field("Phone"),
        dateInput.takeIf(String::isNotBlank)?.let(DateText::parse), existing?.archived ?: false, departmentId, classificationId, tagIds, customValues)
    val definitions = data.fields.filterNot { it.archived }.sortedWith(compareBy<FieldDefinition> { it.position }.thenBy { it.id })
    val invalid = EmployeeValidation.invalidFields(candidate(), definitions)
    val valid = invalid.isEmpty() && (dateInput.isBlank() || DateText.parse(dateInput) != null)
    fun duplicates(value: Employee) = data.employees.any { other -> other.id != value.id &&
        ((value.name.isNotBlank() && other.name.equals(value.name, ignoreCase = true)) ||
            (value.number.isNotBlank() && value.number == other.number) || (value.nationalId.isNotBlank() && value.nationalId == other.nationalId)) }

    FormSheet(stringResource(if (existing == null) R.string.add_employee else R.string.edit_employee), onDismiss,
        onSave = { val value = candidate(); if (duplicates(value)) showDuplicate = true else onSave(value) }, busy = busy, saveEnabled = valid) {
        definitions.forEach { definition ->
            val builtin = definition.builtinKey.isNotBlank()
            val value = if (builtin) values[definition.builtinKey].orEmpty() else customValues[definition.id].orEmpty()
            val onChange: (String) -> Unit = { next ->
                if (builtin) values = values + (definition.builtinKey to next) else customValues = customValues + (definition.id to next)
            }
            val hint = stringResource(if (definition.required) R.string.required_field else R.string.optional)
            val error = if (definition in invalid && value.isNotBlank()) stringResource(when (definition.type) {
                FieldType.Date -> R.string.invalid_date
                FieldType.Number -> R.string.field_number_invalid
                else -> R.string.required_field
            }) else null
            when {
                definition.builtinKey == "Department" -> AppChoiceField(groupChoices(data.groups, GroupKind.Department, departmentId),
                    departmentId, { departmentId = it }, definition.label)
                definition.type == FieldType.Date -> AppDateField(value, onChange, definition.label, error, optional = !definition.required)
                definition.type == FieldType.Dropdown -> AppChoiceField(
                    listOf(ChoiceOption("", stringResource(R.string.none))) + (definition.choices + value.takeIf(String::isNotBlank).orEmpty())
                        .filter(String::isNotBlank).distinct().map { ChoiceOption(it, it) }, value, onChange, definition.label)
                else -> AppTextField(value, onChange, definition.label, hint = hint, error = error, keyboardType =
                    if (definition.type == FieldType.Number) KeyboardType.Decimal
                    else EmployeeFormSchema.fields.firstOrNull { it.key.name == definition.builtinKey }?.keyboard ?: KeyboardType.Text)
            }
        }
        if (data.groups.any { it.kind == GroupKind.Classification }) AppChoiceField(
            groupChoices(data.groups, GroupKind.Classification, classificationId), classificationId,
            { classificationId = it }, stringResource(R.string.classification))
        if (data.groups.any { it.kind == GroupKind.Tag }) {
            Text(stringResource(R.string.tags), style = MaterialTheme.typography.titleMedium)
            TagFields(data.groups, tagIds) { tagIds = it }
        }
    }
    if (showDuplicate) AlertDialog(onDismissRequest = { showDuplicate = false },
        title = { Text(stringResource(R.string.duplicate_employee)) }, text = { Text(stringResource(R.string.duplicate_employee_body)) },
        confirmButton = { TextButton(onClick = { showDuplicate = false; onSave(candidate()) }) { Text(stringResource(R.string.save_anyway)) } },
        dismissButton = { TextButton(onClick = { showDuplicate = false }) { Text(stringResource(R.string.cancel)) } })
}
