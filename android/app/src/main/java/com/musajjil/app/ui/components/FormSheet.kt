package com.musajjil.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.musajjil.app.R
import com.musajjil.app.ui.theme.LocalAppDimensions

/** Shared sheet owns chrome/scroll/keyboard behavior; callers own all form values and validation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormSheet(title: String, onDismiss: () -> Unit, onSave: (() -> Unit)? = null, busy: Boolean = false,
    saveEnabled: Boolean = true, saveLabel: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val space = LocalAppDimensions.current
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().widthIn(max = space.maxContentWidth).imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = space.page),
            verticalArrangement = Arrangement.spacedBy(space.medium)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss, enabled = !busy) { Icon(Icons.Outlined.Close, stringResource(R.string.close_form)) }
            }
            CompositionLocalProvider(LocalFormEnabled provides !busy) { content() }
            if (onSave != null) {
                Button(onClick = onSave, enabled = saveEnabled && !busy, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                    if (busy) CircularProgressIndicator(Modifier.size(space.large), strokeWidth = space.tiny / 2)
                    else Text(saveLabel ?: stringResource(R.string.save))
                }
            }
            Spacer(Modifier.height(space.section))
        }
    }
}
