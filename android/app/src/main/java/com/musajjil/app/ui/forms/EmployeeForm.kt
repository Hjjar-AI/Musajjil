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

data class EmployeeFieldSpec(val key: EmployeeField, @StringRes val label: Int,
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
fun EmployeeForm(existing: Employee?, employees: List<Employee>, busy: Boolean, onDismiss: () -> Unit, onSave: (Employee) -> Unit) {
    val id = rememberSaveable { existing?.id ?: UUID.randomUUID().toString() }
    var values by rememberSaveable { mutableStateOf(mapOf(
        EmployeeField.Name.name to existing?.name.orEmpty(), EmployeeField.Number.name to existing?.number.orEmpty(),
        EmployeeField.NationalId.name to existing?.nationalId.orEmpty(), EmployeeField.Department.name to existing?.department.orEmpty(),
        EmployeeField.JobTitle.name to existing?.jobTitle.orEmpty(), EmployeeField.Phone.name to existing?.phone.orEmpty(),
        EmployeeField.EmploymentStart.name to existing?.employmentStart?.let(DateText::format).orEmpty())) }
    var showDuplicate by rememberSaveable { mutableStateOf(false) }
    fun field(key: EmployeeField) = values[key.name].orEmpty().trim()
    val dateInput = field(EmployeeField.EmploymentStart)
    val valid = EmployeeFormSchema.fields.none { it.required && field(it.key).isBlank() } &&
        (dateInput.isBlank() || DateText.parse(dateInput) != null)
    fun candidate() = Employee(id, field(EmployeeField.Name), field(EmployeeField.Number), field(EmployeeField.NationalId),
        field(EmployeeField.Department), field(EmployeeField.JobTitle), field(EmployeeField.Phone),
        dateInput.takeIf(String::isNotBlank)?.let(DateText::parse), existing?.archived ?: false)
    fun duplicates(value: Employee) = employees.any { other -> other.id != value.id &&
        (other.name.equals(value.name, ignoreCase = true) || (value.number.isNotBlank() && value.number == other.number) ||
            (value.nationalId.isNotBlank() && value.nationalId == other.nationalId)) }

    FormSheet(stringResource(if (existing == null) R.string.add_employee else R.string.edit_employee), onDismiss,
        onSave = { val value = candidate(); if (duplicates(value)) showDuplicate = true else onSave(value) }, busy = busy, saveEnabled = valid) {
        EmployeeFormSchema.fields.forEach { spec ->
            val value = values[spec.key.name].orEmpty()
            val onChange: (String) -> Unit = { values = values + (spec.key.name to it) }
            if (spec.date) AppDateField(value, onChange, stringResource(spec.label), optional = !spec.required,
                error = if (value.isNotBlank() && DateText.parse(value) == null) stringResource(R.string.invalid_date) else null)
            else AppTextField(value, onChange, stringResource(spec.label), keyboardType = spec.keyboard,
                hint = stringResource(if (spec.required) R.string.required_field else R.string.optional))
        }
    }
    if (showDuplicate) AlertDialog(onDismissRequest = { showDuplicate = false },
        title = { Text(stringResource(R.string.duplicate_employee)) }, text = { Text(stringResource(R.string.duplicate_employee_body)) },
        confirmButton = { TextButton(onClick = { showDuplicate = false; onSave(candidate()) }) { Text(stringResource(R.string.save_anyway)) } },
        dismissButton = { TextButton(onClick = { showDuplicate = false }) { Text(stringResource(R.string.cancel)) } })
}
