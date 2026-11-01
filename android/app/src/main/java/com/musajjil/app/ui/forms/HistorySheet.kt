package com.musajjil.app.ui.forms

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.musajjil.app.R
import com.musajjil.app.data.*
import com.musajjil.app.domain.*
import com.musajjil.app.ui.components.*
import kotlinx.serialization.json.*
import java.time.LocalDate

@StringRes
private fun operationLabel(operation: String): Int = when (operation) {
    "employee.create" -> R.string.history_employee_create
    "employee.update" -> R.string.history_employee_update
    "employee.archive" -> R.string.history_employee_archive
    "employee.restore" -> R.string.history_employee_restore
    "employee.bulk_update" -> R.string.history_bulk
    "leave.create" -> R.string.history_leave_create
    "leave.update" -> R.string.history_leave_update
    "leave.delete" -> R.string.history_leave_delete
    "hours.create" -> R.string.history_hours_create
    "hours.update" -> R.string.history_hours_update
    "hours.delete" -> R.string.history_hours_delete
    "hours.convert" -> R.string.history_conversion
    "hours.reverse" -> R.string.history_reversal
    "hours.recalculate" -> R.string.history_recalculation
    "service.create" -> R.string.history_service_create
    "service.update" -> R.string.history_service_update
    "service.delete" -> R.string.history_service_delete
    "service.profile" -> R.string.history_service_profile
    "group.update" -> R.string.history_group
    "field.update" -> R.string.history_field
    "leave_type.update" -> R.string.history_type
    "holiday.update", "holiday.delete" -> R.string.history_holiday
    "workspace.restore" -> R.string.history_restore
    else -> R.string.history_settings
}
private fun objectValue(text: String?) = text?.let { runCatching { StorageJson.parseToJsonElement(it) as? JsonObject }.getOrNull() }
private fun employeeFor(event: AuditEvent, data: RegisterData): Employee? {
    val employeeId = (objectValue(event.after)?.get("employeeId") as? JsonPrimitive)?.contentOrNull
        ?: (objectValue(event.before)?.get("employeeId") as? JsonPrimitive)?.contentOrNull ?: event.entityId
    return data.employees.firstOrNull { it.id == employeeId }
}

@Composable
fun HistorySheet(data: RegisterData, initialEmployee: String?, busy: Boolean, onDismiss: () -> Unit) {
    var employeeId by rememberSaveable { mutableStateOf(initialEmployee.orEmpty()) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var limit by rememberSaveable { mutableIntStateOf(100) }
    val context = LocalContext.current
    val selected = data.history.firstOrNull { it.id == selectedId }
    val filtered = data.history.filter { event ->
        val employee = employeeFor(event, data)
        (employeeId.isBlank() || employee?.id == employeeId) && listOf(context.getString(operationLabel(event.operation)),
            employee?.displayName.orEmpty(), event.reason, timestampText(event.createdAt)).any { it.contains(query.trim(), true) }
    }
    FormSheet(stringResource(R.string.history), onDismiss, busy = busy) {
        if (selected != null) {
            TextButton({ selectedId = null }) { Text(stringResource(R.string.previous_step)) }
            AuditDetails(selected, data)
        } else {
            AppChoiceField(listOf(ChoiceOption("", stringResource(R.string.all_employees))) + employeeChoices(data.employees), employeeId,
                { employeeId = it; limit = 100 }, stringResource(R.string.employee))
            AppTextField(query, { query = it; limit = 100 }, stringResource(R.string.search_history))
            if (filtered.isEmpty()) Text(stringResource(R.string.history_empty))
            filtered.take(limit).forEach { event -> SectionCard(Modifier.clickable { selectedId = event.id }) {
                Text(stringResource(operationLabel(event.operation)), style = MaterialTheme.typography.titleMedium)
                employeeFor(event, data)?.let { Text(it.displayName, style = MaterialTheme.typography.bodyMedium) }
                Text(timestampText(event.createdAt), style = MaterialTheme.typography.bodyMedium)
                if (event.reason.isNotBlank()) Text(event.reason, style = MaterialTheme.typography.bodyMedium)
            } }
            if (filtered.size > limit) TextButton({ limit += 100 }) { Text(stringResource(R.string.load_more)) }
        }
    }
}

private val labels = mapOf(
    "name" to R.string.employee_name, "number" to R.string.employee_number, "nationalId" to R.string.national_id,
    "department" to R.string.department, "jobTitle" to R.string.job_title, "phone" to R.string.phone,
    "employmentStart" to R.string.employment_start, "archived" to R.string.archived_label,
    "classificationId" to R.string.classification, "tagIds" to R.string.tags, "customValues" to R.string.field_values,
    "start" to R.string.start_date, "end" to R.string.end_date, "date" to R.string.entry_date,
    "days" to R.string.day_count, "hours" to R.string.hours_count, "consumedHours" to R.string.consumed_hours,
    "hoursConsumed" to R.string.consumed_hours, "notes" to R.string.notes, "typeName" to R.string.leave_type,
    "allocations" to R.string.year_allocation, "suggestedAllocations" to R.string.suggested_allocation,
    "threshold" to R.string.conversion_threshold, "sources" to R.string.source_hours,
    "deleted" to R.string.is_deleted, "reversed" to R.string.is_reversed, "year" to R.string.year,
    "deduction" to R.string.service_deduction, "targetYears" to R.string.service_target_years,
    "targetMonths" to R.string.service_target_months, "targetDays" to R.string.service_target_days,
    "anchor" to R.string.service_anchor, "contractEnd" to R.string.contract_end,
    "label" to R.string.field_label, "type" to R.string.field_type, "required" to R.string.field_required,
    "position" to R.string.field_position, "choices" to R.string.field_choices, "kind" to R.string.group_kind,
    "excludedMask" to R.string.excluded_weekdays, "excludeHolidays" to R.string.exclude_holidays,
    "employees" to R.string.employees, "leaves" to R.string.leave,
    "dayLeaves" to R.string.leave, "hourEntries" to R.string.hour_records,
)

@Composable
private fun AuditDetails(event: AuditEvent, data: RegisterData) {
    val before = objectValue(event.before)
    val after = objectValue(event.after)
    Text(stringResource(operationLabel(event.operation)), style = MaterialTheme.typography.titleLarge)
    employeeFor(event, data)?.let { Text(it.displayName, style = MaterialTheme.typography.titleMedium) }
    DetailValue(stringResource(R.string.entry_date), timestampText(event.createdAt))
    Text(if (event.actor == "local-owner") stringResource(R.string.history_actor) else event.actor, style = MaterialTheme.typography.bodyMedium)
    if (event.reason.isNotBlank()) DetailValue(stringResource(R.string.history_reason), event.reason)
    val forced = if (event.operation.startsWith("hours.")) setOf("date", "hours", "threshold", "sources")
        else if (event.operation.endsWith("delete")) setOf("start", "end", "days", "typeName") else emptySet()
    val changed = ((before?.keys ?: emptySet()) + (after?.keys ?: emptySet())).filter {
        it in labels && (before?.get(it) != after?.get(it) || it in forced)
    }
    if (before == null && event.before != null) {
        Text(stringResource(R.string.history_legacy), style = MaterialTheme.typography.titleMedium)
        Text(event.before, style = MaterialTheme.typography.bodySmall)
    }
    changed.forEach { key -> SectionCard(title = stringResource(requireNotNull(labels[key]))) {
        DetailValue(stringResource(R.string.history_before), auditValue(key, before?.get(key), data))
        DetailValue(stringResource(R.string.history_after), auditValue(key, after?.get(key), data))
    } }
    if (event.before == null) Text(stringResource(R.string.history_created), style = MaterialTheme.typography.bodyMedium)
    if (after?.isEmpty() == true) Text(stringResource(R.string.history_removed), style = MaterialTheme.typography.bodyMedium)
    if (after == null && event.after.isNotBlank()) {
        Text(stringResource(R.string.history_legacy), style = MaterialTheme.typography.titleMedium)
        Text(event.after, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun auditValue(key: String, value: JsonElement?, data: RegisterData): String {
    if (value == null || value == JsonNull) return stringResource(R.string.no_value)
    val text = (value as? JsonPrimitive)?.contentOrNull ?: value.toString()
    return when (key) {
        "archived", "deleted", "reversed", "required", "excludeHolidays" -> stringResource(if (text == "true") R.string.yes else R.string.no)
        "classificationId" -> data.groups.firstOrNull { it.id == text }?.name.orEmpty()
        "tagIds" -> runCatching { restored<Set<String>>(text) }.getOrDefault(emptySet()).let { ids -> data.groups.filter { it.id in ids }.joinToString("، ") { it.name } }
        "customValues" -> runCatching { restored<Map<String, String>>(text) }.getOrDefault(emptyMap()).entries.joinToString("\n") {
            "${data.fields.firstOrNull { field -> field.id == it.key }?.label.orEmpty()}: ${it.value}"
        }
        "choices" -> runCatching { restored<List<String>>(text) }.getOrDefault(emptyList()).joinToString("، ")
        "allocations", "suggestedAllocations" -> runCatching { restored<Map<Int, Int>>(text) }.getOrDefault(emptyMap()).entries.joinToString("\n") { "${it.key}: ${it.value}" }
        "type" -> runCatching { FieldType.valueOf(text) }.getOrNull()?.let { fieldTypeLabel(it) } ?: text
        "kind" -> runCatching { GroupKind.valueOf(text) }.getOrNull()?.let { groupKindLabel(it) } ?: text
        "sources" -> {
            val sources = runCatching { restored<List<HourConsumption>>(text) }.getOrDefault(emptyList())
            sources.map { source ->
                val entry = data.hours.firstOrNull { it.id == source.entryId }
                if (entry == null) stringResource(R.string.source_previous, source.hours)
                else stringResource(R.string.source_entry, DateText.format(entry.date), source.hours)
            }.joinToString("\n")
        }
        "excludedMask" -> {
            val mask = text.toIntOrNull() ?: 0
            val weekdays = androidx.compose.ui.res.stringArrayResource(R.array.weekdays_sunday_first)
            val days = listOf(java.time.DayOfWeek.SUNDAY) + java.time.DayOfWeek.values().filter { it != java.time.DayOfWeek.SUNDAY }
            days.withIndex().filter { mask and (1 shl (it.value.value - 1)) != 0 }.joinToString("، ") { weekdays[it.index] }.ifBlank { stringResource(R.string.none) }
        }
        "start", "end", "date", "employmentStart", "anchor", "contractEnd" -> runCatching { DateText.format(LocalDate.parse(text)) }.getOrDefault(text)
        else -> text
    }.ifBlank { stringResource(R.string.no_value) }
}
