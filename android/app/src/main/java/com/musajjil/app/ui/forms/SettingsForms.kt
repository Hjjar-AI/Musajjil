package com.musajjil.app.ui.forms

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.musajjil.app.R
import com.musajjil.app.domain.*
import com.musajjil.app.ui.components.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

@Composable
fun ThresholdForm(threshold: Int, busy: Boolean, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var value by rememberSaveable { mutableStateOf(threshold.toString()) }
    val number = DateText.positiveWholeNumber(value)
    FormSheet(stringResource(R.string.hourly_settings), onDismiss, onSave = { onSave(requireNotNull(number)) },
        busy = busy, saveEnabled = number != null) {
        AppTextField(value, { value = it }, stringResource(R.string.conversion_threshold), keyboardType = KeyboardType.Number,
            error = if (number == null) stringResource(R.string.whole_number_error) else null)
        Text(stringResource(R.string.conversion_explanation), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun LeavePolicyForm(policy: LeavePolicy, busy: Boolean, onDismiss: () -> Unit, onSave: (LeavePolicy) -> Unit) {
    var excluded by rememberSaveable { mutableStateOf(policy.excludedWeekdays.map { it.value }) }
    var holidays by rememberSaveable { mutableStateOf(policy.excludeHolidays) }
    val selected = LeavePolicy(excluded.map(DayOfWeek::of).toSet(), holidays)
    FormSheet(stringResource(R.string.leave_settings), onDismiss, onSave = { onSave(selected) }, busy = busy) {
        PolicyControls(selected) { excluded = it.excludedWeekdays.map { day -> day.value }; holidays = it.excludeHolidays }
    }
}

@Composable
fun HolidayForm(existing: Holiday?, busy: Boolean, onDismiss: () -> Unit, onSave: (Holiday) -> Unit, onDelete: (String) -> Unit) {
    val id = rememberSaveable { existing?.id ?: UUID.randomUUID().toString() }
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var dateText by rememberSaveable { mutableStateOf(DateText.format(existing?.date ?: LocalDate.now())) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    val date = DateText.parse(dateText)
    FormSheet(stringResource(if (existing == null) R.string.add_holiday else R.string.edit_holiday), onDismiss,
        onSave = { onSave(Holiday(id, name, requireNotNull(date))) }, busy = busy, saveEnabled = name.isNotBlank() && date != null) {
        AppTextField(name, { name = it }, stringResource(R.string.holiday_name))
        AppDateField(dateText, { dateText = it }, stringResource(R.string.entry_date),
            error = if (date == null) stringResource(R.string.invalid_date) else null)
        if (existing != null) TextButton({ deleting = true }, enabled = !busy) { Text(stringResource(R.string.delete_record)) }
    }
    if (deleting) DeleteRecordDialog(busy, { deleting = false }, { deleting = false; onDelete(it) })
}
