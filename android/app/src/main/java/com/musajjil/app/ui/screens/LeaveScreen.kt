package com.musajjil.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.musajjil.app.R
import com.musajjil.app.domain.*
import com.musajjil.app.ui.components.*
import com.musajjil.app.ui.theme.LocalAppDimensions
import java.time.LocalDate

@Composable
fun LeaveScreen(data: RegisterData, today: LocalDate, onAddLeave: () -> Unit, onAddHours: () -> Unit, onEditLeave: (DayLeave) -> Unit) {
    val space = LocalAppDimensions.current
    var hoursTab by rememberSaveable { mutableStateOf(false) }
    var year by rememberSaveable { mutableIntStateOf(today.year) }
    val years = (data.leaves.flatMap { it.allocations.keys } + data.hours.map { it.date.year } + today.year + year).distinct().sortedDescending()
    val leaves = data.leaves.filter { (it.allocations[year] ?: 0) > 0 }
    val hours = data.hours.filter { it.date.year == year }
    val employeeNames = data.employees.associate { it.id to it.name }
    val canAdd = data.employees.any { !it.archived }
    LazyColumn(contentPadding = PaddingValues(space.page), verticalArrangement = Arrangement.spacedBy(space.medium)) {
        item { ScreenHeading(stringResource(R.string.leave), action = {
            FilledTonalIconButton(onClick = { if (hoursTab) onAddHours() else onAddLeave() }, enabled = canAdd) {
                Icon(Icons.Outlined.Add, stringResource(if (hoursTab) R.string.record_hours else R.string.record_leave))
            }
        }) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(space.small)) {
                FilterChip(!hoursTab, { hoursTab = false }, label = { Text(stringResource(R.string.day_leave)) })
                FilterChip(hoursTab, { hoursTab = true }, label = { Text(stringResource(R.string.hour_entries)) })
            }
        }
        item { AppChoiceField(years.map { ChoiceOption(it.toString(), it.toString()) }, year.toString(), { year = it.toInt() }, stringResource(R.string.year)) }
        if (hoursTab) {
            item { SectionCard(title = stringResource(R.string.hour_entries)) {
                DetailValue(stringResource(R.string.recorded_hours), hours.sumOf { it.hours.toLong() }.toString())
                DetailValue(stringResource(R.string.converted_days), data.conversions.filter { it.year == year }.sumOf { it.days }.toString())
                DetailValue(stringResource(if (year == today.year) R.string.pending_hours else R.string.historical_unconverted),
                    hours.sumOf { (it.hours - it.consumedHours).toLong() }.toString())
                Text(stringResource(R.string.conversion_rule, data.threshold), color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium)
            } }
            if (hours.isEmpty()) item { EmptyState(stringResource(R.string.no_hours), stringResource(R.string.no_hours_body), Icons.Outlined.Schedule) }
            items(hours, key = { it.id }) { hour -> SectionCard {
                Row(horizontalArrangement = Arrangement.spacedBy(space.medium)) {
                    Column(Modifier.weight(1f)) {
                        Text(employeeNames[hour.employeeId].orEmpty(), style = MaterialTheme.typography.titleMedium)
                        Text(DateText.format(hour.date), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StatusChip(stringResource(R.string.hours_value, hour.hours))
                }
            } }
        } else {
            item { SectionCard { DetailValue(stringResource(R.string.recorded_days), leaves.sumOf { (it.allocations[year] ?: 0).toLong() }.toString()) } }
            if (leaves.isEmpty()) item { EmptyState(stringResource(R.string.no_leave), stringResource(R.string.no_leave_body), Icons.Outlined.EventNote) }
            items(leaves, key = { it.id }) { leave ->
                SectionCard { DayLeaveRow(leave.copy(days = leave.allocations.getValue(year)), employeeNames[leave.employeeId].orEmpty()) { onEditLeave(leave) } }
            }
        }
    }
}
