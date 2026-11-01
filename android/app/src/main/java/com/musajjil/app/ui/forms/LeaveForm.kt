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
fun LeaveForm(data: RegisterData, existing: DayLeave?, initialEmployee: String?, busy: Boolean,
    onDismiss: () -> Unit, onSave: (DayLeave) -> Unit) {
    val today = LocalDate.now()
    val initialDate = existing?.start ?: today
    val id = rememberSaveable { existing?.id ?: UUID.randomUUID().toString() }
    var employeeId by rememberSaveable { mutableStateOf(existing?.employeeId ?: initialEmployee.orEmpty()) }
    var startText by rememberSaveable { mutableStateOf(DateText.format(initialDate)) }
    var endText by rememberSaveable { mutableStateOf(DateText.format(existing?.end ?: today)) }
    var notes by rememberSaveable { mutableStateOf(existing?.notes.orEmpty()) }
    var department by rememberSaveable { mutableStateOf(existing?.department ?: data.employees.firstOrNull { it.id == employeeId }?.department.orEmpty()) }
    val start = DateText.parse(startText)
    val end = DateText.parse(endText)
    val sameDates = existing != null && start == existing.start && end == existing.end
    val policy = if (sameDates) existing!!.policy else data.leavePolicy
    val suggestion = if (sameDates) existing!!.suggestedAllocations else if (start != null && end != null && !end.isBefore(start))
        LeaveRules.suggest(start, end, policy, data.holidays.map { it.date }.toSet()) else emptyMap()
    val initialSuggestion = existing?.allocations ?: LeaveRules.suggest(today, today, data.leavePolicy, data.holidays.map { it.date }.toSet())
    var daysText by rememberSaveable { mutableStateOf((existing?.days ?: initialSuggestion.values.sum()).toString()) }
    var allocations by rememberSaveable { mutableStateOf(initialSuggestion.mapValues { it.value.toString() }) }
    var manuallyAdjusted by rememberSaveable { mutableStateOf(existing != null && existing.allocations != existing.suggestedAllocations) }
    var lastRange by rememberSaveable { mutableStateOf("$startText|$endText") }
    var showOverlap by rememberSaveable { mutableStateOf(false) }
    val days = DateText.positiveWholeNumber(daysText)
    val allocationValues = allocations.mapValues { DateText.normalizeDigits(it.value).toIntOrNull() ?: -1 }
    val valid = start != null && end != null && days != null &&
        data.employees.any { it.id == employeeId && (!it.archived || existing != null) } &&
        LeaveRules.validAllocation(days, allocationValues, start, end)

    LaunchedEffect(employeeId) {
        if (employeeId != existing?.employeeId) department = data.employees.firstOrNull { it.id == employeeId }?.department.orEmpty()
    }
    LaunchedEffect(startText, endText) {
        val key = "$startText|$endText"
        if (start != null && end != null && !end.isBefore(start) && key != lastRange) {
            allocations = if (!manuallyAdjusted) suggestion.mapValues { it.value.toString() }
                else if (start.year == end.year) mapOf(start.year to daysText)
                else suggestion.keys.associateWith { allocations[it] ?: "0" }
            if (!manuallyAdjusted) daysText = suggestion.values.sum().toString()
            lastRange = key
        }
    }
    fun candidate() = DayLeave(id, employeeId, requireNotNull(start), requireNotNull(end), requireNotNull(days),
        allocationValues, suggestion, department, notes, policy)
    fun overlapping() = start != null && end != null && data.leaves.any {
        it.id != id && it.employeeId == employeeId && !it.start.isAfter(end) && !it.end.isBefore(start)
    }

    FormSheet(stringResource(if (existing == null) R.string.record_leave else R.string.edit_leave), onDismiss,
        onSave = { if (overlapping()) showOverlap = true else onSave(candidate()) }, busy = busy, saveEnabled = valid) {
        AppChoiceField(employeeChoices(data.employees.filter { !it.archived || it.id == existing?.employeeId }),
            employeeId, { employeeId = it }, stringResource(R.string.employee))
        DetailValue(stringResource(R.string.leave_type), stringResource(R.string.administrative))
        AppDateField(startText, { startText = it }, stringResource(R.string.start_date),
            error = if (startText.isNotBlank() && start == null) stringResource(R.string.invalid_date) else null)
        AppDateField(endText, { endText = it }, stringResource(R.string.end_date), error = when {
            endText.isNotBlank() && end == null -> stringResource(R.string.invalid_date)
            start != null && end != null && end.isBefore(start) -> stringResource(R.string.invalid_date_range)
            else -> null
        })
        AppTextField(daysText, { value ->
            daysText = value; manuallyAdjusted = true
            if (start != null && end != null && start.year == end.year) allocations = mapOf(start.year to value)
        }, stringResource(R.string.day_count), keyboardType = KeyboardType.Number,
            hint = stringResource(R.string.suggested_days, suggestion.values.sum()),
            error = if (days == null) stringResource(R.string.whole_number_error) else null)
        if (start != null && end != null && end.year > start.year) {
            Text(stringResource(R.string.year_allocation), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.year_allocation_hint), style = MaterialTheme.typography.bodyMedium)
            (start.year..end.year).forEach { year ->
                AppTextField(allocations[year].orEmpty(), { allocations = allocations + (year to it); manuallyAdjusted = true },
                    stringResource(R.string.days_in_year, year), keyboardType = KeyboardType.Number)
            }
            if (days != null && !LeaveRules.validAllocation(days, allocationValues, start, end))
                Text(stringResource(R.string.allocation_error), color = MaterialTheme.colorScheme.error)
        }
        AppTextField(department, { department = it }, stringResource(R.string.department), hint = stringResource(R.string.optional))
        AppTextField(notes, { notes = it }, stringResource(R.string.notes), singleLine = false, hint = stringResource(R.string.optional))
    }
    if (showOverlap) AlertDialog(onDismissRequest = { showOverlap = false },
        title = { Text(stringResource(R.string.overlap_title)) }, text = { Text(stringResource(R.string.overlap_body)) },
        confirmButton = { TextButton(onClick = { showOverlap = false; onSave(candidate()) }) { Text(stringResource(R.string.save_anyway)) } },
        dismissButton = { TextButton(onClick = { showOverlap = false }) { Text(stringResource(R.string.cancel)) } })
}
