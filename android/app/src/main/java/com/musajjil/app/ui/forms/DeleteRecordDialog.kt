package com.musajjil.app.ui.forms

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.musajjil.app.R
import com.musajjil.app.ui.components.AppTextField
import com.musajjil.app.ui.theme.LocalAppDimensions

@Composable
fun DeleteRecordDialog(busy: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit,
    initialReason: String = "", hours: Boolean = false) {
    var reason by rememberSaveable { mutableStateOf(initialReason) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(stringResource(R.string.delete_record)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(LocalAppDimensions.current.medium)) {
            Text(stringResource(if (hours) R.string.delete_hours_message else R.string.delete_record_message))
            AppTextField(reason, { reason = it }, stringResource(R.string.reason_optional), singleLine = false)
        } },
        confirmButton = { TextButton({ onConfirm(reason) }, enabled = !busy) { Text(stringResource(R.string.delete_record)) } },
        dismissButton = { TextButton(onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) } })
}
