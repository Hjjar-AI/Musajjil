package com.musajjil.app.ui.forms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.musajjil.app.R
import com.musajjil.app.domain.*
import com.musajjil.app.ui.components.*
import java.time.DayOfWeek
import java.util.UUID

enum class ManagementKind { Groups, Fields, LeaveTypes }

@Composable
fun ManagementSheet(kind: ManagementKind, data: RegisterData, busy: Boolean, onDismiss: () -> Unit,
    onAdd: () -> Unit, onGroup: (EmployeeGroup) -> Unit, onField: (FieldDefinition) -> Unit, onType: (LeaveType) -> Unit) {
    var archived by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(when (kind) {
        ManagementKind.Groups -> R.string.groups
        ManagementKind.Fields -> R.string.employee_fields
        ManagementKind.LeaveTypes -> R.string.leave_types
    })
    FormSheet(title, onDismiss, busy = busy) {
        Row {
            FilterChip(!archived, { archived = false }, { Text(stringResource(R.string.active)) })
            FilterChip(archived, { archived = true }, { Text(stringResource(R.string.archived)) })
            Spacer(Modifier.weight(1f))
            IconButton(onAdd, enabled = !busy) { Icon(Icons.Outlined.Add, stringResource(when (kind) {
                ManagementKind.Groups -> R.string.add_group
                ManagementKind.Fields -> R.string.add_field
                ManagementKind.LeaveTypes -> R.string.add_leave_type
            })) }
        }
        when (kind) {
            ManagementKind.Groups -> {
                if (data.groups.none { it.archived == archived }) Text(stringResource(R.string.empty_list))
                GroupKind.entries.forEach { groupKind ->
                    val groups = data.groups.filter { it.kind == groupKind && it.archived == archived }
                    if (groups.isNotEmpty()) Text(groupKindLabel(groupKind), style = MaterialTheme.typography.titleMedium)
                    groups.forEach { group -> SectionCard(Modifier.clickable(enabled = !busy) { onGroup(group) }) {
                        Text(group.name, style = MaterialTheme.typography.titleMedium)
                        val count = data.employees.count { when (group.kind) {
                            GroupKind.Department -> it.departmentId == group.id
                            GroupKind.Classification -> it.classificationId == group.id
                            GroupKind.Tag -> group.id in it.tagIds
                        } }
                        Text(stringResource(R.string.group_members, count), style = MaterialTheme.typography.bodyMedium)
                    } }
                }
            }
            ManagementKind.Fields -> data.fields.filter { it.archived == archived }.sortedBy { it.position }.forEach { field ->
                SectionCard(Modifier.clickable(enabled = !busy) { onField(field) }) {
                    Text(field.label, style = MaterialTheme.typography.titleMedium)
                    DetailValue(stringResource(R.string.field_type), fieldTypeLabel(field.type))
                    if (field.required) {
                        StatusChip(stringResource(R.string.required_field))
                        Text(stringResource(R.string.field_missing_count, data.employees.count { EmployeeValidation.value(it, field).isBlank() }),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            ManagementKind.LeaveTypes -> data.leaveTypes.filter { it.archived == archived }.forEach { type ->
                SectionCard(Modifier.clickable(enabled = !busy) { onType(type) }) {
                    Text(type.name, style = MaterialTheme.typography.titleMedium)
                    DetailValue(stringResource(R.string.exclude_holidays), stringResource(if (type.policy.excludeHolidays) R.string.enabled else R.string.disabled))
                    DetailValue(stringResource(R.string.excluded_weekdays), type.policy.excludedWeekdays.size.toString())
                }
            }
        }
    }
}

@Composable
fun GroupForm(existing: EmployeeGroup?, busy: Boolean, onDismiss: () -> Unit, onSave: (EmployeeGroup) -> Unit) {
    val id = rememberSaveable { existing?.id ?: UUID.randomUUID().toString() }
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var kind by rememberSaveable { mutableStateOf(existing?.kind ?: GroupKind.Department) }
    var archived by rememberSaveable { mutableStateOf(existing?.archived ?: false) }
    FormSheet(stringResource(if (existing == null) R.string.add_group else R.string.edit_group), onDismiss,
        onSave = { onSave(EmployeeGroup(id, name, kind, archived)) }, busy = busy, saveEnabled = name.isNotBlank()) {
        AppTextField(name, { name = it }, stringResource(R.string.group_name))
        if (existing == null) AppChoiceField(GroupKind.entries.map { ChoiceOption(it.name, groupKindLabel(it)) }, kind.name,
            { kind = GroupKind.valueOf(it) }, stringResource(R.string.group_kind))
        else DetailValue(stringResource(R.string.group_kind), groupKindLabel(kind))
        SwitchField(stringResource(R.string.group_archive), archived, { archived = it })
        Text(stringResource(R.string.group_keep_members), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun FieldForm(existing: FieldDefinition?, data: RegisterData, busy: Boolean, onDismiss: () -> Unit, onSave: (FieldDefinition) -> Unit) {
    val id = rememberSaveable { existing?.id ?: UUID.randomUUID().toString() }
    var label by rememberSaveable { mutableStateOf(existing?.label.orEmpty()) }
    var type by rememberSaveable { mutableStateOf(existing?.type ?: FieldType.Text) }
    var required by rememberSaveable { mutableStateOf(existing?.required ?: false) }
    var archived by rememberSaveable { mutableStateOf(existing?.archived ?: false) }
    var position by rememberSaveable { mutableStateOf((existing?.position ?: ((data.fields.maxOfOrNull { it.position } ?: -1) + 1)).toString()) }
    var choices by rememberSaveable { mutableStateOf(existing?.choices?.joinToString("\n").orEmpty()) }
    val order = DateText.normalizeDigits(position).toIntOrNull()?.takeIf { it >= 0 }
    val options = choices.lines().map(String::trim).filter(String::isNotBlank).distinct()
    FormSheet(stringResource(if (existing == null) R.string.add_field else R.string.edit_field), onDismiss,
        onSave = { onSave(FieldDefinition(id, label, type, required, requireNotNull(order), options, archived, existing?.builtinKey.orEmpty())) },
        busy = busy, saveEnabled = label.isNotBlank() && order != null && (type != FieldType.Dropdown || options.isNotEmpty())) {
        AppTextField(label, { label = it }, stringResource(R.string.field_label))
        if (existing == null) AppChoiceField(FieldType.entries.map { ChoiceOption(it.name, fieldTypeLabel(it)) }, type.name,
            { type = FieldType.valueOf(it) }, stringResource(R.string.field_type))
        else DetailValue(stringResource(R.string.field_type), fieldTypeLabel(type))
        AppTextField(position, { position = it }, stringResource(R.string.field_position), keyboardType = KeyboardType.Number)
        if (type == FieldType.Dropdown) AppTextField(choices, { choices = it }, stringResource(R.string.field_choices), singleLine = false)
        SwitchField(stringResource(R.string.field_required), required, { required = it })
        SwitchField(stringResource(R.string.field_archive), archived, { archived = it })
        Text(stringResource(R.string.field_keep_values), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun LeaveTypeForm(existing: LeaveType?, data: RegisterData, busy: Boolean, onDismiss: () -> Unit, onSave: (LeaveType) -> Unit) {
    val id = rememberSaveable { existing?.id ?: UUID.randomUUID().toString() }
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var archived by rememberSaveable { mutableStateOf(existing?.archived ?: false) }
    var weekdays by rememberSaveable { mutableStateOf(existing?.policy?.excludedWeekdays?.map { it.value } ?: emptyList()) }
    var holidays by rememberSaveable { mutableStateOf(existing?.policy?.excludeHolidays ?: false) }
    val policy = LeavePolicy(weekdays.map(DayOfWeek::of).toSet(), holidays)
    val canArchive = data.leaveTypes.any { it.id != id && !it.archived }
    FormSheet(stringResource(if (existing == null) R.string.add_leave_type else R.string.edit_leave_type), onDismiss,
        onSave = { onSave(LeaveType(id, name, policy, archived)) }, busy = busy, saveEnabled = name.isNotBlank() && (!archived || canArchive)) {
        AppTextField(name, { name = it }, stringResource(R.string.leave_type_name))
        PolicyControls(policy) { weekdays = it.excludedWeekdays.map { day -> day.value }; holidays = it.excludeHolidays }
        SwitchField(stringResource(R.string.leave_type_archive), archived, { archived = it })
        if (archived && !canArchive) Text(stringResource(R.string.leave_type_last_active), color = MaterialTheme.colorScheme.error)
        Text(stringResource(R.string.leave_type_keep_history), style = MaterialTheme.typography.bodyMedium)
    }
}
