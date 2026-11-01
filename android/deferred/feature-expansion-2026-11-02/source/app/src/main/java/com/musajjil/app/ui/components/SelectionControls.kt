package com.musajjil.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.musajjil.app.R
import com.musajjil.app.domain.*
import com.musajjil.app.ui.theme.LocalAppDimensions
import java.time.DayOfWeek

@Composable
fun CheckField(label: String, checked: Boolean, onChanged: (Boolean) -> Unit, enabled: Boolean = LocalFormEnabled.current) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, enabled, Role.Checkbox, onChanged),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LocalAppDimensions.current.small)) {
        Checkbox(checked, null, enabled = enabled)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun SwitchField(label: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    val enabled = LocalFormEnabled.current
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, enabled, Role.Switch, onChanged),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, null, enabled = enabled)
    }
}

@Composable
fun PolicyControls(policy: LeavePolicy, onChange: (LeavePolicy) -> Unit) {
    val labels = stringArrayResource(R.array.weekdays_sunday_first)
    val days = listOf(DayOfWeek.SUNDAY) + DayOfWeek.values().filter { it != DayOfWeek.SUNDAY }
    Text(stringResource(R.string.leave_settings_hint), style = MaterialTheme.typography.bodyMedium)
    days.forEachIndexed { index, day ->
        CheckField(labels[index], day in policy.excludedWeekdays, { checked -> onChange(policy.copy(
            excludedWeekdays = if (checked) policy.excludedWeekdays + day else policy.excludedWeekdays - day)) })
    }
    SwitchField(stringResource(R.string.exclude_holidays), policy.excludeHolidays, { onChange(policy.copy(excludeHolidays = it)) })
}

@Composable
fun TagFields(groups: List<EmployeeGroup>, selected: Set<String>, onChange: (Set<String>) -> Unit) {
    groups.filter { it.kind == GroupKind.Tag && (!it.archived || it.id in selected) }.forEach { group ->
        CheckField(group.name + if (group.archived) " (${stringResource(R.string.archived_label)})" else "", group.id in selected,
            { checked -> onChange(if (checked) selected + group.id else selected - group.id) })
    }
}

@Composable
fun groupKindLabel(kind: GroupKind): String = stringResource(when (kind) {
    GroupKind.Department -> R.string.department
    GroupKind.Classification -> R.string.classification
    GroupKind.Tag -> R.string.tags
})

@Composable
fun fieldTypeLabel(type: FieldType): String = stringResource(when (type) {
    FieldType.Text -> R.string.field_text
    FieldType.Number -> R.string.field_number
    FieldType.Date -> R.string.field_date
    FieldType.Dropdown -> R.string.field_dropdown
})

@Composable
fun groupChoices(groups: List<EmployeeGroup>, kind: GroupKind, selected: String = ""): List<ChoiceOption> =
    listOf(ChoiceOption("", stringResource(R.string.none))) + groups.filter { it.kind == kind && (!it.archived || it.id == selected) }
        .map { ChoiceOption(it.id, it.name + if (it.archived) " (${stringResource(R.string.archived_label)})" else "") }
