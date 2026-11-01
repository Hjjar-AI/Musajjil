package com.musajjil.app.ui.components

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.musajjil.app.R
import com.musajjil.app.domain.DateText
import java.time.LocalDate
import java.util.Calendar

val LocalFormEnabled = staticCompositionLocalOf { true }

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val supporting = error ?: hint
    OutlinedTextField(value, onValueChange, modifier.fillMaxWidth(), enabled = LocalFormEnabled.current, label = { Text(label) },
        singleLine = singleLine, minLines = if (singleLine) 1 else 3, isError = error != null,
        supportingText = if (supporting != null) ({ Text(supporting) }) else null,
        trailingIcon = trailingIcon, shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = if (singleLine) ImeAction.Next else ImeAction.Default))
}

@Composable
fun AppDateField(value: String, onValueChange: (String) -> Unit, label: String, error: String? = null, optional: Boolean = false) {
    val context = LocalContext.current
    AppTextField(value, onValueChange, label, error = error,
        hint = if (optional) stringResource(R.string.optional) else stringResource(R.string.date_hint),
        trailingIcon = {
            IconButton(enabled = LocalFormEnabled.current,
                onClick = { showDatePicker(context, DateText.parse(value) ?: LocalDate.now()) { onValueChange(DateText.format(it)) } }) {
                Icon(Icons.Outlined.CalendarMonth, stringResource(R.string.choose_date))
            }
        })
}

fun showDatePicker(context: Context, date: LocalDate, onPicked: (LocalDate) -> Unit) {
    DatePickerDialog(context, { _, year, month, day -> onPicked(LocalDate.of(year, month + 1, day)) },
        date.year, date.monthValue - 1, date.dayOfMonth).apply {
        datePicker.firstDayOfWeek = Calendar.SUNDAY
    }.show()
}

data class ChoiceOption(val key: String, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppChoiceField(options: List<ChoiceOption>, selectedKey: String?, onSelected: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val enabled = LocalFormEnabled.current
    ExposedDropdownMenuBox(expanded, { if (enabled) expanded = !expanded }, modifier.fillMaxWidth()) {
        OutlinedTextField(options.firstOrNull { it.key == selectedKey }?.label.orEmpty(), {},
            Modifier.menuAnchor().fillMaxWidth(), readOnly = true, enabled = enabled, label = { Text(label) },
            shape = MaterialTheme.shapes.medium,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) })
        ExposedDropdownMenu(expanded, { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option.label) }, onClick = { onSelected(option.key); expanded = false })
            }
        }
    }
}
