package com.musajjil.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.musajjil.app.R
import com.musajjil.app.domain.*
import com.musajjil.app.ui.components.*
import com.musajjil.app.ui.theme.LocalAppDimensions
import java.time.LocalDate

@Composable
fun HomeScreen(data: RegisterData, today: LocalDate, onRecordLeave: () -> Unit, onRecordHours: () -> Unit,
    onAddEmployee: () -> Unit, onAllLeaves: () -> Unit, onEditLeave: (DayLeave) -> Unit) {
    val space = LocalAppDimensions.current
    val active = data.employees.filterNot { it.archived }
    val onLeave = data.leaves.filter { !today.isBefore(it.start) && !today.isAfter(it.end) }
        .map { it.employeeId }.distinct().count { id -> active.any { it.id == id } }
    val days = data.leaves.sumOf { (it.allocations[today.year] ?: 0).toLong() }
    val pending = data.hours.filter { it.date.year == today.year }.sumOf { (it.hours - it.consumedHours).toLong() }
    val employeeNames = data.employees.associate { it.id to it.name }
    LazyColumn(contentPadding = PaddingValues(space.page), verticalArrangement = Arrangement.spacedBy(space.large)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(space.small)) {
                StatusChip(stringResource(R.string.local_register))
                Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineLarge)
                Text(stringResource(R.string.home_subtitle), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SectionCard {
                PrimaryAction(stringResource(R.string.record_leave), Icons.Outlined.AddCircleOutline,
                    onRecordLeave, Modifier.fillMaxWidth(), enabled = active.isNotEmpty())
                Row(horizontalArrangement = Arrangement.spacedBy(space.small)) {
                    OutlinedButton(onClick = onRecordHours, enabled = active.isNotEmpty(), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Schedule, null); Spacer(Modifier.width(space.tiny)); Text(stringResource(R.string.record_hours))
                    }
                    OutlinedButton(onClick = onAddEmployee, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.PersonAddAlt, null); Spacer(Modifier.width(space.tiny)); Text(stringResource(R.string.add_employee))
                    }
                }
                if (active.isEmpty()) Text(stringResource(R.string.add_employee_first), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { Text(stringResource(R.string.year_summary, today.year), style = MaterialTheme.typography.titleMedium) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(space.medium)) {
                SummaryTile(active.size.toString(), stringResource(R.string.active_employees), Icons.Outlined.PeopleAlt, Modifier.weight(1f))
                SummaryTile(onLeave.toString(), stringResource(R.string.on_leave_today), Icons.Outlined.EventAvailable, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(space.medium)) {
                SummaryTile(days.toString(), stringResource(R.string.recorded_days), Icons.AutoMirrored.Outlined.EventNote, Modifier.weight(1f))
                SummaryTile(pending.toString(), stringResource(R.string.pending_hours), Icons.Outlined.Schedule, Modifier.weight(1f))
            }
        }
        item {
            SectionCard(title = stringResource(R.string.recent_leave), action = {
                if (data.leaves.isNotEmpty()) TextButton(onAllLeaves) { Text(stringResource(R.string.view_all)) }
            }) {
                if (data.leaves.isEmpty()) EmptyState(stringResource(R.string.no_leave), stringResource(R.string.no_leave_body), Icons.AutoMirrored.Outlined.EventNote)
                else data.leaves.take(3).forEachIndexed { index, leave ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DayLeaveRow(leave, employeeNames[leave.employeeId].orEmpty()) { onEditLeave(leave) }
                }
            }
        }
    }
}
