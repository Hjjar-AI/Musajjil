package com.musajjil.app.ui.forms

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.musajjil.app.R
import com.musajjil.app.domain.*
import com.musajjil.app.ui.components.*

@Composable
fun EmployeeDetailsSheet(employee: Employee, data: RegisterData, busy: Boolean, onDismiss: () -> Unit,
    onEdit: () -> Unit, onArchive: () -> Unit, onRecordLeave: () -> Unit, onEditLeave: (DayLeave) -> Unit) {
    var confirmArchive by rememberSaveable { mutableStateOf(false) }
    FormSheet(stringResource(R.string.employee_details), onDismiss, busy = busy) {
        Text(employee.name, style = MaterialTheme.typography.headlineMedium)
        if (employee.archived) StatusChip(stringResource(R.string.archived))
        DetailValue(stringResource(R.string.employee_number), employee.number)
        DetailValue(stringResource(R.string.national_id), employee.nationalId)
        DetailValue(stringResource(R.string.department), employee.department)
        DetailValue(stringResource(R.string.job_title), employee.jobTitle)
        DetailValue(stringResource(R.string.phone), employee.phone)
        DetailValue(stringResource(R.string.employment_start), employee.employmentStart?.let(DateText::format).orEmpty())
        Row(Modifier.fillMaxWidth()) {
            TextButton(onEdit, enabled = !busy) { Text(stringResource(R.string.edit_employee)) }
            TextButton({ if (employee.archived) onArchive() else confirmArchive = true }, enabled = !busy) {
                Text(stringResource(if (employee.archived) R.string.restore_employee else R.string.archive_employee))
            }
        }
        if (!employee.archived) OutlinedButton(onRecordLeave, Modifier.fillMaxWidth(), enabled = !busy) {
            Text(stringResource(R.string.record_leave))
        }
        HorizontalDivider()
        Text(stringResource(R.string.leave), style = MaterialTheme.typography.titleMedium)
        val leaves = data.leaves.filter { it.employeeId == employee.id }
        if (leaves.isEmpty()) Text(stringResource(R.string.no_leave), style = MaterialTheme.typography.bodyMedium)
        leaves.forEach { DayLeaveRow(it, stringResource(R.string.administrative)) { onEditLeave(it) } }
    }
    if (confirmArchive) AlertDialog(onDismissRequest = { confirmArchive = false },
        title = { Text(stringResource(R.string.archive_employee)) }, text = { Text(stringResource(R.string.archive_message)) },
        confirmButton = { TextButton({ confirmArchive = false; onArchive() }) { Text(stringResource(R.string.archive)) } },
        dismissButton = { TextButton({ confirmArchive = false }) { Text(stringResource(R.string.cancel)) } })
}
