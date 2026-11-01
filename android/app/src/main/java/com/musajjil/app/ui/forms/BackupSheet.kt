package com.musajjil.app.ui.forms

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.musajjil.app.R
import com.musajjil.app.backup.*
import com.musajjil.app.ui.components.*
import com.musajjil.app.ui.theme.LocalAppDimensions
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun timestampText(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("dd/MM/uuuu · HH:mm", Locale("ar")))

@Composable
fun BackupSheet(status: BackupStatus, busy: Boolean, onDismiss: () -> Unit, onFolder: (Uri) -> Unit,
    onBackup: () -> Unit, onRestoreFile: (Uri) -> Unit) {
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { if (it != null) onFolder(it) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { if (it != null) onRestoreFile(it) }
    FormSheet(stringResource(R.string.backups), onDismiss, busy = busy) {
        SectionCard {
            DetailValue(stringResource(R.string.backup_folder), status.folderName)
            OutlinedButton({ folder.launch(null) }, enabled = !busy) { Text(stringResource(R.string.choose_backup_folder)) }
        }
        Text(stringResource(R.string.backup_schedule), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.backup_timing), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.backup_contents), style = MaterialTheme.typography.bodyMedium)
        if (status.folderUri == null) Text(stringResource(R.string.backup_folder_first), style = MaterialTheme.typography.bodyMedium)
        if (status.error.isNotBlank()) Text(status.error, color = MaterialTheme.colorScheme.error)
        SectionCard {
            DetailValue(stringResource(R.string.backup_last_auto), if (status.lastAutomatic == 0L) stringResource(R.string.backup_never) else timestampText(status.lastAutomatic))
            DetailValue(stringResource(R.string.backup_last_manual), if (status.lastManual == 0L) stringResource(R.string.backup_never) else timestampText(status.lastManual))
        }
        Button(onBackup, enabled = status.folderUri != null && !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_now)) }
        OutlinedButton({ restore.launch(arrayOf("*/*")) }, enabled = status.folderUri != null && !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.backup_restore))
        }
        status.lastSafetyUri?.let { uri -> TextButton({ onRestoreFile(Uri.parse(uri)) }, enabled = !busy) { Text(stringResource(R.string.backup_restore_safety)) } }
        if (busy) CircularProgressIndicator()
    }
}

@Composable
fun RestorePreviewDialog(archive: BackupArchive, busy: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(stringResource(R.string.backup_review)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(LocalAppDimensions.current.medium)) {
            Text(stringResource(R.string.backup_replace_message))
            DetailValue(stringResource(R.string.backup_created), timestampText(archive.manifest.createdAt))
            DetailValue(stringResource(R.string.employees), archive.snapshot.employees.size.toString())
            DetailValue(stringResource(R.string.leave), archive.snapshot.leaves.count { !it.deleted }.toString())
            DetailValue(stringResource(R.string.hour_entries), archive.snapshot.hours.count { !it.deleted }.toString())
            DetailValue(stringResource(R.string.service_periods), archive.snapshot.periods.size.toString())
            DetailValue(stringResource(R.string.history), archive.snapshot.history.size.toString())
        } },
        confirmButton = { TextButton(onConfirm, enabled = !busy) { Text(stringResource(R.string.backup_replace)) } },
        dismissButton = { TextButton(onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) } })
}
