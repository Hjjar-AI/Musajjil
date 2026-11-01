package com.musajjil.app.ui.forms

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.musajjil.app.R
import com.musajjil.app.domain.*
import com.musajjil.app.ui.components.*
import java.time.LocalDate
import java.util.UUID

@Composable
fun HoursForm(data: RegisterData, existing: HourEntry?, initialEmployee: String?, busy: Boolean, onDismiss: () -> Unit,
    onSave: (HourEntry, String) -> Unit, onDelete: (String) -> Unit) {
    val id = rememberSaveable { existing?.id ?: UUID.randomUUID().toString() }
    var employeeId by rememberSaveable { mutableStateOf(existing?.employeeId ?: initialEmployee.orEmpty()) }
    var dateText by rememberSaveable { mutableStateOf(DateText.format(existing?.date ?: LocalDate.now())) }
    var hoursText by rememberSaveable { mutableStateOf(existing?.hours?.toString().orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(existing?.notes.orEmpty()) }
    var reason by rememberSaveable { mutableStateOf("") }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var showDuplicate by rememberSaveable { mutableStateOf(false) }
    val date = DateText.parse(dateText)
    val hours = DateText.positiveWholeNumber(hoursText)
    val valid = date != null && hours != null && data.employees.any { it.id == employeeId && (existing != null || !it.archived) }
    fun candidate() = HourEntry(id, employeeId, requireNotNull(date), requireNotNull(hours), notes = notes)
    FormSheet(stringResource(if (existing == null) R.string.record_hours else R.string.edit_hours), onDismiss,
        onSave = { if (data.hours.any { it.id != id && it.employeeId == employeeId && it.date == date }) showDuplicate = true else onSave(candidate(), reason) },
        busy = busy, saveEnabled = valid) {
        if (existing == null) AppChoiceField(employeeChoices(data.employees.filterNot { it.archived }), employeeId,
            { employeeId = it }, stringResource(R.string.employee))
        else DetailValue(stringResource(R.string.employee), data.employees.firstOrNull { it.id == employeeId }?.displayName.orEmpty())
        AppDateField(dateText, { dateText = it }, stringResource(R.string.entry_date),
            error = if (dateText.isNotBlank() && date == null) stringResource(R.string.invalid_date) else null)
        AppTextField(hoursText, { hoursText = it }, stringResource(R.string.hours_count), keyboardType = KeyboardType.Number,
            hint = stringResource(R.string.whole_hours_hint),
            error = if (hoursText.isNotBlank() && hours == null) stringResource(R.string.whole_number_error) else null)
        Text(stringResource(R.string.conversion_rule, data.threshold), color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.conversion_explanation), style = MaterialTheme.typography.bodyMedium)
        AppTextField(notes, { notes = it }, stringResource(R.string.notes), singleLine = false, hint = stringResource(R.string.optional))
        if (existing != null) {
            DetailValue(stringResource(R.string.consumed_hours), existing.consumedHours.toString())
            Text(stringResource(R.string.hour_correction_rule), style = MaterialTheme.typography.bodyMedium)
            AppTextField(reason, { reason = it }, stringResource(R.string.reason_optional), singleLine = false)
            TextButton({ deleting = true }, enabled = !busy) { Text(stringResource(R.string.delete_record)) }
        }
    }
    if (showDuplicate) AlertDialog(onDismissRequest = { showDuplicate = false },
        title = { Text(stringResource(R.string.overlap_title)) }, text = { Text(stringResource(R.string.overlap_body)) },
        confirmButton = { TextButton(onClick = { showDuplicate = false; onSave(candidate(), reason) }) { Text(stringResource(R.string.save_anyway)) } },
        dismissButton = { TextButton(onClick = { showDuplicate = false }) { Text(stringResource(R.string.cancel)) } })
    if (deleting) DeleteRecordDialog(busy, { deleting = false }, { reason = it; deleting = false; onDelete(it) }, initialReason = reason, hours = true)
}
