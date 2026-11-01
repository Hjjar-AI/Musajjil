package com.musajjil.app.ui.forms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.musajjil.app.R
import com.musajjil.app.domain.*
import com.musajjil.app.ui.components.*
import java.time.LocalDate
import java.util.UUID

@Composable
fun ContractStatus(profile: ServiceProfile, today: LocalDate) {
    val end = profile.contractEnd ?: return
    if (end <= today.plusDays(30)) {
        Text(stringResource(if (end < today) R.string.contract_expired else R.string.contract_due, DateText.format(end)),
            color = if (end < today) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.contract_warning_only), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ServiceDirectorySheet(data: RegisterData, busy: Boolean, onDismiss: () -> Unit, onSelect: (Employee) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    FormSheet(stringResource(R.string.service), onDismiss, busy = busy) {
        AppTextField(query, { query = it }, stringResource(R.string.search_employees))
        data.employees.filter { listOf(it.displayName, it.number, it.department).any { text -> text.contains(query.trim(), true) } }.forEach { employee ->
            SectionCard {
                EmployeeRow(employee, { onSelect(employee) })
                data.serviceProfiles.firstOrNull { it.employeeId == employee.id }?.let { ContractStatus(it, LocalDate.now()) }
            }
        }
        if (data.employees.isEmpty()) Text(stringResource(R.string.no_employees))
    }
}

@Composable
fun ServiceSheet(employee: Employee, data: RegisterData, today: LocalDate, busy: Boolean, onDismiss: () -> Unit,
    onProfile: () -> Unit, onAddPeriod: () -> Unit, onPeriod: (ServicePeriod) -> Unit, onHistory: () -> Unit) {
    val periods = data.servicePeriods.filter { it.employeeId == employee.id }
    val profile = data.serviceProfiles.firstOrNull { it.employeeId == employee.id } ?: ServiceProfile(employee.id)
    val summary = ServiceRules.summarize(periods, profile, today)
    FormSheet(stringResource(R.string.service), onDismiss, busy = busy) {
        Text(employee.displayName, style = MaterialTheme.typography.headlineMedium)
        SectionCard {
            DetailValue(stringResource(R.string.service_counted), summary.countedDays.toString())
            DetailValue(stringResource(R.string.service_deduction), profile.deduction.toString())
            DetailValue(stringResource(R.string.service_effective), summary.effectiveDays.toString())
            if (summary.effectiveDays < 0) Text(stringResource(R.string.service_deduction_high), color = MaterialTheme.colorScheme.error)
            DetailValue(stringResource(R.string.service_target), summary.targetDays?.toString() ?: stringResource(R.string.service_no_target))
            summary.remainingDays?.let { DetailValue(stringResource(R.string.service_remaining), it.toString()) }
            summary.estimatedCompletion?.let { DetailValue(stringResource(R.string.service_estimate), DateText.format(it)) }
            Text(stringResource(R.string.service_estimate_hint), style = MaterialTheme.typography.bodyMedium)
        }
        SectionCard {
            DetailValue(stringResource(R.string.contract_end), profile.contractEnd?.let(DateText::format).orEmpty())
            ContractStatus(profile, today)
            OutlinedButton(onProfile, enabled = !busy) { Text(stringResource(R.string.service_profile)) }
        }
        Row {
            TextButton(onAddPeriod, enabled = !busy) { Text(stringResource(R.string.service_add_period)) }
            TextButton(onHistory, enabled = !busy) { Text(stringResource(R.string.history)) }
        }
        if (periods.isEmpty()) Text(stringResource(R.string.service_no_periods))
        periods.forEach { period -> SectionCard(Modifier.clickable(enabled = !busy) { onPeriod(period) }) {
            DetailValue(stringResource(R.string.start_date), DateText.format(period.start))
            DetailValue(stringResource(R.string.end_date), period.end?.let(DateText::format) ?: stringResource(R.string.service_ongoing))
        } }
    }
}

@Composable
fun ServicePeriodForm(employee: Employee, existing: ServicePeriod?, periods: List<ServicePeriod>, busy: Boolean,
    onDismiss: () -> Unit, onSave: (ServicePeriod, String) -> Unit, onDelete: (String) -> Unit) {
    val id = rememberSaveable { existing?.id ?: UUID.randomUUID().toString() }
    var startText by rememberSaveable { mutableStateOf(DateText.format(existing?.start ?: LocalDate.now())) }
    var endText by rememberSaveable { mutableStateOf(DateText.format(existing?.end ?: LocalDate.now())) }
    var ongoing by rememberSaveable { mutableStateOf(existing?.let { it.end == null } ?: !employee.archived) }
    var reason by rememberSaveable { mutableStateOf("") }
    var overlap by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    val start = DateText.parse(startText)
    val end = if (ongoing) null else DateText.parse(endText)
    val valid = start != null && (ongoing || end != null && end >= start) &&
        (!employee.archived || end != null && end <= LocalDate.now())
    fun candidate() = ServicePeriod(id, employee.id, requireNotNull(start), end)
    fun overlaps() = start != null && periods.any { it.id != id && it.employeeId == employee.id &&
        it.start <= (end ?: LocalDate.MAX) && (it.end ?: LocalDate.MAX) >= start }
    FormSheet(stringResource(if (existing == null) R.string.service_add_period else R.string.service_edit_period), onDismiss,
        onSave = { if (overlaps()) overlap = true else onSave(candidate(), reason) }, busy = busy, saveEnabled = valid) {
        Text(employee.displayName, style = MaterialTheme.typography.titleMedium)
        AppDateField(startText, { startText = it }, stringResource(R.string.start_date),
            error = if (start == null) stringResource(R.string.invalid_date) else null)
        if (!employee.archived) SwitchField(stringResource(R.string.service_ongoing), ongoing, { ongoing = it })
        if (!ongoing) AppDateField(endText, { endText = it }, stringResource(R.string.end_date), error = when {
            end == null -> stringResource(R.string.invalid_date)
            start != null && end < start -> stringResource(R.string.invalid_date_range)
            else -> null
        })
        AppTextField(reason, { reason = it }, stringResource(R.string.reason_optional), singleLine = false)
        if (existing != null) TextButton({ deleting = true }, enabled = !busy) { Text(stringResource(R.string.delete_record)) }
    }
    if (overlap) AlertDialog(onDismissRequest = { overlap = false }, title = { Text(stringResource(R.string.overlap_title)) },
        text = { Text(stringResource(R.string.service_overlap)) },
        confirmButton = { TextButton({ overlap = false; onSave(candidate(), reason) }) { Text(stringResource(R.string.save_anyway)) } },
        dismissButton = { TextButton({ overlap = false }) { Text(stringResource(R.string.cancel)) } })
    if (deleting) DeleteRecordDialog(busy, { deleting = false }, { deleting = false; onDelete(it) }, initialReason = reason)
}

@Composable
fun ServiceProfileForm(employee: Employee, existing: ServiceProfile?, periods: List<ServicePeriod>, busy: Boolean,
    onDismiss: () -> Unit, onSave: (ServiceProfile, String) -> Unit) {
    val initial = existing ?: ServiceProfile(employee.id, anchor = periods.filter { it.employeeId == employee.id }.minOfOrNull { it.start })
    var deduction by rememberSaveable { mutableStateOf(initial.deduction.toString()) }
    var years by rememberSaveable { mutableStateOf(initial.targetYears.toString()) }
    var months by rememberSaveable { mutableStateOf(initial.targetMonths.toString()) }
    var days by rememberSaveable { mutableStateOf(initial.targetDays.toString()) }
    var anchorText by rememberSaveable { mutableStateOf(initial.anchor?.let(DateText::format).orEmpty()) }
    var contractText by rememberSaveable { mutableStateOf(initial.contractEnd?.let(DateText::format).orEmpty()) }
    var reason by rememberSaveable { mutableStateOf("") }
    fun number(value: String) = DateText.normalizeDigits(value).toIntOrNull()?.takeIf { it >= 0 }
    val anchor = DateText.parse(anchorText)
    val contract = DateText.parse(contractText)
    val candidate = ServiceProfile(employee.id, number(deduction) ?: -1, number(years) ?: -1, number(months) ?: -1,
        number(days) ?: -1, anchor, contract)
    val derived = runCatching { ServiceRules.targetDays(candidate) }
    val valid = derived.isSuccess && (anchorText.isBlank() || anchor != null) && (contractText.isBlank() || contract != null)
    FormSheet(stringResource(R.string.service_profile), onDismiss, onSave = { onSave(candidate, reason) }, busy = busy, saveEnabled = valid) {
        Text(employee.displayName, style = MaterialTheme.typography.titleMedium)
        AppTextField(deduction, { deduction = it }, stringResource(R.string.service_deduction), keyboardType = KeyboardType.Number,
            hint = stringResource(R.string.service_deduction_hint))
        AppTextField(years, { years = it }, stringResource(R.string.service_target_years), keyboardType = KeyboardType.Number)
        AppTextField(months, { months = it }, stringResource(R.string.service_target_months), keyboardType = KeyboardType.Number)
        AppTextField(days, { days = it }, stringResource(R.string.service_target_days), keyboardType = KeyboardType.Number)
        AppDateField(anchorText, { anchorText = it }, stringResource(R.string.service_anchor), optional = true,
            error = if (anchorText.isNotBlank() && anchor == null) stringResource(R.string.invalid_date) else null)
        Text(stringResource(R.string.service_target_rule), style = MaterialTheme.typography.bodyMedium)
        derived.getOrNull()?.let { Text(stringResource(R.string.service_target_derived, it), style = MaterialTheme.typography.titleMedium) }
        if (derived.isFailure) Text(stringResource(R.string.invalid_data), color = MaterialTheme.colorScheme.error)
        Text(stringResource(R.string.review_anchor), style = MaterialTheme.typography.bodyMedium)
        AppDateField(contractText, { contractText = it }, stringResource(R.string.contract_end), optional = true,
            error = if (contractText.isNotBlank() && contract == null) stringResource(R.string.invalid_date) else null)
        Text(stringResource(R.string.contract_warning_only), style = MaterialTheme.typography.bodyMedium)
        AppTextField(reason, { reason = it }, stringResource(R.string.reason_optional), singleLine = false)
    }
}

@Composable
fun ArchiveEmployeeForm(employee: Employee, periods: List<ServicePeriod>, busy: Boolean, onDismiss: () -> Unit, onSave: (LocalDate) -> Unit) {
    var dateText by rememberSaveable { mutableStateOf(DateText.format(LocalDate.now())) }
    var overlap by rememberSaveable { mutableStateOf(false) }
    val date = DateText.parse(dateText)
    val closing = periods.filter { it.employeeId == employee.id && (it.end == null || date != null && it.end > date) }
    val valid = date != null && (employee.archived || date <= LocalDate.now() && closing.none { it.start > date })
    FormSheet(stringResource(if (employee.archived) R.string.restore_employee else R.string.archive_employee), onDismiss,
        onSave = {
            if (employee.archived && periods.any { it.employeeId == employee.id && (it.end ?: LocalDate.MAX) >= requireNotNull(date) }) overlap = true
            else onSave(requireNotNull(date))
        }, busy = busy, saveEnabled = valid) {
        Text(employee.displayName, style = MaterialTheme.typography.titleMedium)
        AppDateField(dateText, { dateText = it }, stringResource(if (employee.archived) R.string.return_working_date else R.string.final_working_date))
        Text(stringResource(if (employee.archived) R.string.restore_service_hint else R.string.archive_service_hint), style = MaterialTheme.typography.bodyMedium)
        if (!valid) Text(stringResource(R.string.archive_date_invalid), color = MaterialTheme.colorScheme.error)
    }
    if (overlap) AlertDialog(onDismissRequest = { overlap = false }, title = { Text(stringResource(R.string.overlap_title)) },
        text = { Text(stringResource(R.string.service_overlap)) },
        confirmButton = { TextButton({ overlap = false; onSave(requireNotNull(date)) }) { Text(stringResource(R.string.save_anyway)) } },
        dismissButton = { TextButton({ overlap = false }) { Text(stringResource(R.string.cancel)) } })
}
