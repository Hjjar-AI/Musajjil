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
fun HoursForm(data: RegisterData, initialEmployee: String?, busy: Boolean, onDismiss: () -> Unit, onSave: (HourEntry) -> Unit) {
    val id = rememberSaveable { UUID.randomUUID().toString() }
    var employeeId by rememberSaveable { mutableStateOf(initialEmployee.orEmpty()) }
    var dateText by rememberSaveable { mutableStateOf(DateText.format(LocalDate.now())) }
    var hoursText by rememberSaveable { mutableStateOf("") }
    var showDuplicate by rememberSaveable { mutableStateOf(false) }
    val date = DateText.parse(dateText)
    val hours = DateText.positiveWholeNumber(hoursText)
    val valid = date != null && hours != null && data.employees.any { it.id == employeeId && !it.archived }
    fun candidate() = HourEntry(id, employeeId, requireNotNull(date), requireNotNull(hours))
    FormSheet(stringResource(R.string.record_hours), onDismiss,
        onSave = { if (data.hours.any { it.employeeId == employeeId && it.date == date }) showDuplicate = true else onSave(candidate()) },
        busy = busy, saveEnabled = valid) {
        AppChoiceField(employeeChoices(data.employees.filterNot { it.archived }), employeeId,
            { employeeId = it }, stringResource(R.string.employee))
        AppDateField(dateText, { dateText = it }, stringResource(R.string.entry_date),
            error = if (dateText.isNotBlank() && date == null) stringResource(R.string.invalid_date) else null)
        AppTextField(hoursText, { hoursText = it }, stringResource(R.string.hours_count), keyboardType = KeyboardType.Number,
            hint = stringResource(R.string.whole_hours_hint),
            error = if (hoursText.isNotBlank() && hours == null) stringResource(R.string.whole_number_error) else null)
        Text(stringResource(R.string.conversion_rule, data.threshold), color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.conversion_explanation), style = MaterialTheme.typography.bodyMedium)
    }
    if (showDuplicate) AlertDialog(onDismissRequest = { showDuplicate = false },
        title = { Text(stringResource(R.string.overlap_title)) }, text = { Text(stringResource(R.string.overlap_body)) },
        confirmButton = { TextButton(onClick = { showDuplicate = false; onSave(candidate()) }) { Text(stringResource(R.string.save_anyway)) } },
        dismissButton = { TextButton(onClick = { showDuplicate = false }) { Text(stringResource(R.string.cancel)) } })
}
