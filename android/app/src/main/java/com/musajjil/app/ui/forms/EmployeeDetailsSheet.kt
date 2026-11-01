package com.musajjil.app.ui.forms

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.musajjil.app.R
import com.musajjil.app.domain.*
import com.musajjil.app.ui.components.*
import java.time.LocalDate

@Composable
fun EmployeeDetailsSheet(employee: Employee, data: RegisterData, busy: Boolean, onDismiss: () -> Unit,
    onEdit: () -> Unit, onArchive: () -> Unit, onRecordLeave: () -> Unit, onEditLeave: (DayLeave) -> Unit,
    onService: () -> Unit, onHistory: () -> Unit, onEditHours: (HourEntry) -> Unit) {
    FormSheet(stringResource(R.string.employee_details), onDismiss, busy = busy) {
        Text(employee.displayName, style = MaterialTheme.typography.headlineMedium)
        if (employee.archived) StatusChip(stringResource(R.string.archived_label))
        data.fields.filterNot { it.archived }.sortedBy { it.position }.forEach { field -> DetailValue(field.label, EmployeeValidation.value(employee, field)) }
        DetailValue(stringResource(R.string.classification), data.groups.firstOrNull { it.id == employee.classificationId }?.name.orEmpty())
        DetailValue(stringResource(R.string.tags), data.groups.filter { it.id in employee.tagIds }.joinToString("، ") { it.name })
        data.serviceProfiles.firstOrNull { it.employeeId == employee.id }?.let { ContractStatus(it, LocalDate.now()) }
        Row(Modifier.fillMaxWidth()) {
            TextButton(onEdit, enabled = !busy) { Text(stringResource(R.string.edit_employee)) }
            TextButton(onArchive, enabled = !busy) { Text(stringResource(if (employee.archived) R.string.restore_employee else R.string.archive_employee)) }
        }
        NavigationRow(stringResource(R.string.service), onService)
        NavigationRow(stringResource(R.string.history), onHistory)
        if (!employee.archived) OutlinedButton(onRecordLeave, Modifier.fillMaxWidth(), enabled = !busy) { Text(stringResource(R.string.record_leave)) }
        HorizontalDivider()
        Text(stringResource(R.string.leave), style = MaterialTheme.typography.titleMedium)
        val leaves = data.leaves.filter { it.employeeId == employee.id }
        if (leaves.isEmpty()) Text(stringResource(R.string.no_leave), style = MaterialTheme.typography.bodyMedium)
        leaves.forEach { DayLeaveRow(it, it.typeName, showType = false) { onEditLeave(it) } }
        Text(stringResource(R.string.hour_entries), style = MaterialTheme.typography.titleMedium)
        data.hours.filter { it.employeeId == employee.id }.forEach { entry ->
            NavigationRow(DateText.format(entry.date) + " · " + stringResource(R.string.hours_value, entry.hours), { onEditHours(entry) })
        }
    }
}
