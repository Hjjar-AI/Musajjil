package com.musajjil.app.ui.forms

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import com.musajjil.app.R
import com.musajjil.app.domain.*
import com.musajjil.app.ui.components.*

@Composable
fun BulkEmployeeForm(data: RegisterData, busy: Boolean, onDismiss: () -> Unit, onSave: (BulkEmployeeChange) -> Unit) {
    var phase by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var department by rememberSaveable { mutableStateOf("__keep__") }
    var classification by rememberSaveable { mutableStateOf("__keep__") }
    var addTags by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var removeTags by rememberSaveable { mutableStateOf(emptySet<String>()) }
    fun chosen(value: String): String? = if (value == "__keep__") null else if (value == "__clear__") "" else value
    val change = BulkEmployeeChange(selected, chosen(department), chosen(classification), addTags, removeTags)
    val previews = data.employees.filter { it.id in selected }.map { before -> before to before.copy(
        departmentId = change.departmentId ?: before.departmentId,
        department = if (change.departmentId != null) data.groups.firstOrNull { it.id == change.departmentId }?.name.orEmpty() else before.department,
        classificationId = change.classificationId ?: before.classificationId,
        tagIds = (before.tagIds + addTags) - removeTags) }
    val hasChanges = previews.any { it.first != it.second }
    val invalid = previews.any { EmployeeValidation.invalidFields(it.second, data.fields).isNotEmpty() }
    val filtered = data.employees.filter { listOf(it.displayName, it.number, it.department).any { text -> text.contains(query.trim(), true) } }
    val saveEnabled = when (phase) { 0 -> selected.isNotEmpty(); 1 -> hasChanges; else -> hasChanges && !invalid }
    FormSheet(stringResource(R.string.bulk_employees), onDismiss, onSave = { if (phase < 2) phase++ else onSave(change) }, busy = busy,
        saveEnabled = saveEnabled, saveLabel = stringResource(if (phase == 2) R.string.bulk_apply else R.string.bulk_review)) {
        Text(stringResource(R.string.bulk_selected, selected.size), style = MaterialTheme.typography.titleMedium)
        if (phase > 0) TextButton({ phase-- }, enabled = !busy) { Text(stringResource(R.string.previous_step)) }
        when (phase) {
            0 -> {
                Text(stringResource(R.string.bulk_choose))
                AppTextField(query, { query = it }, stringResource(R.string.search_employees))
                Row {
                    TextButton({ selected = selected + filtered.map { it.id } }, enabled = !busy) { Text(stringResource(R.string.select_all)) }
                    TextButton({ selected = emptySet() }, enabled = !busy) { Text(stringResource(R.string.select_none)) }
                }
                filtered.forEach { employee -> CheckField(listOf(employee.displayName, employee.number, employee.department)
                    .filter(String::isNotBlank).joinToString(" · "), employee.id in selected,
                    { checked -> selected = if (checked) selected + employee.id else selected - employee.id }) }
            }
            1 -> {
                @Composable fun options(kind: GroupKind) = listOf(ChoiceOption("__keep__", stringResource(R.string.unchanged)),
                    ChoiceOption("__clear__", stringResource(R.string.clear_value))) + data.groups.filter { it.kind == kind && !it.archived }.map { ChoiceOption(it.id, it.name) }
                AppChoiceField(options(GroupKind.Department), department, { department = it }, stringResource(R.string.department))
                AppChoiceField(options(GroupKind.Classification), classification, { classification = it }, stringResource(R.string.classification))
                Text(stringResource(R.string.bulk_add_tags), style = MaterialTheme.typography.titleMedium)
                TagFields(data.groups, addTags) { addTags = it; removeTags = removeTags - it }
                Text(stringResource(R.string.bulk_remove_tags), style = MaterialTheme.typography.titleMedium)
                TagFields(data.groups.map { it.copy(archived = false) }, removeTags) { removeTags = it; addTags = addTags - it }
            }
            else -> {
                Text(stringResource(R.string.bulk_review), style = MaterialTheme.typography.titleMedium)
                if (invalid) Text(stringResource(R.string.bulk_validation), color = MaterialTheme.colorScheme.error)
                previews.forEach { (before, after) -> SectionCard(title = before.displayName) {
                    fun classificationName(id: String) = data.groups.firstOrNull { it.id == id }?.name.orEmpty()
                    fun tagsName(ids: Set<String>) = data.groups.filter { it.id in ids }.joinToString("، ") { it.name }
                    if (before.departmentId != after.departmentId) {
                        DetailValue(stringResource(R.string.history_before) + " · " + stringResource(R.string.department), before.department)
                        DetailValue(stringResource(R.string.history_after) + " · " + stringResource(R.string.department), after.department)
                    }
                    if (before.classificationId != after.classificationId) {
                        DetailValue(stringResource(R.string.history_before) + " · " + stringResource(R.string.classification), classificationName(before.classificationId))
                        DetailValue(stringResource(R.string.history_after) + " · " + stringResource(R.string.classification), classificationName(after.classificationId))
                    }
                    if (before.tagIds != after.tagIds) {
                        DetailValue(stringResource(R.string.history_before) + " · " + stringResource(R.string.tags), tagsName(before.tagIds))
                        DetailValue(stringResource(R.string.history_after) + " · " + stringResource(R.string.tags), tagsName(after.tagIds))
                    }
                    val missing = EmployeeValidation.invalidFields(after, data.fields)
                    if (missing.isNotEmpty()) Text(missing.joinToString("، ") { it.label }, color = MaterialTheme.colorScheme.error)
                    if (before == after) Text(stringResource(R.string.unchanged), style = MaterialTheme.typography.bodyMedium)
                } }
            }
        }
    }
}
