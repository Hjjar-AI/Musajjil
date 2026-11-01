package com.musajjil.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.musajjil.app.R
import com.musajjil.app.domain.*
import com.musajjil.app.ui.theme.LocalAppDimensions

@Composable
fun EmployeeRow(employee: Employee, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val space = LocalAppDimensions.current
    Row(modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = space.medium),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.medium)) {
        Surface(Modifier.size(space.avatar), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
            Box(contentAlignment = Alignment.Center) {
                Text(employee.name.take(1), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(employee.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val number = employee.number.takeIf(String::isNotBlank)?.let { stringResource(R.string.employee_number_value, it) }
            val department = employee.department.ifBlank { stringResource(R.string.no_department) }
            Text(listOfNotNull(number, department).joinToString(" · "), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Outlined.ChevronLeft, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun DayLeaveRow(leave: DayLeave, employeeName: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = LocalAppDimensions.current.medium),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LocalAppDimensions.current.medium)) {
        Column(Modifier.weight(1f)) {
            Text(employeeName, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.date_range, DateText.format(leave.start), DateText.format(leave.end)),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        StatusChip(stringResource(R.string.days_value, leave.days))
    }
}

@Composable
fun DetailValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LocalAppDimensions.current.large)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { stringResource(R.string.no_value) }, Modifier.weight(1.3f), style = MaterialTheme.typography.bodyMedium)
    }
}
