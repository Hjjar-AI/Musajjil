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
import com.musajjil.app.domain.Employee
import com.musajjil.app.ui.components.*
import com.musajjil.app.ui.theme.LocalAppDimensions

@Composable
fun EmployeesScreen(employees: List<Employee>, onAdd: () -> Unit, onSelect: (Employee) -> Unit) {
    val space = LocalAppDimensions.current
    var query by rememberSaveable { mutableStateOf("") }
    var department by rememberSaveable { mutableStateOf("") }
    var archived by rememberSaveable { mutableStateOf(false) }
    val departments = employees.map { it.department }.filter(String::isNotBlank).distinct().sorted()
    val filtered = employees.filter { employee -> employee.archived == archived &&
        (department.isEmpty() || employee.department == department) &&
        listOf(employee.displayName, employee.number, employee.nationalId, employee.department).any { it.contains(query.trim(), ignoreCase = true) } }
    LazyColumn(contentPadding = PaddingValues(space.page), verticalArrangement = Arrangement.spacedBy(space.small)) {
        item { ScreenHeading(stringResource(R.string.employees), action = {
            FilledTonalIconButton(onAdd) { Icon(Icons.Outlined.PersonAddAlt, stringResource(R.string.add_employee)) }
        }) }
        item {
            AppTextField(query, { query = it }, stringResource(R.string.search_employees), trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, stringResource(R.string.clear_search)) }
                else Icon(Icons.Outlined.Search, contentDescription = null)
            })
        }
        item { AppChoiceField(listOf(ChoiceOption("", stringResource(R.string.all_departments))) + departments.map { ChoiceOption(it, it) },
            department, { department = it }, stringResource(R.string.department)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(space.small)) {
                FilterChip(!archived, { archived = false }, label = { Text(stringResource(R.string.active)) })
                FilterChip(archived, { archived = true }, label = { Text(stringResource(R.string.archived)) })
            }
        }
        if (filtered.isEmpty()) item {
            val emptyRegister = employees.isEmpty()
            EmptyState(stringResource(if (emptyRegister) R.string.no_employees else if (archived && query.isBlank()) R.string.no_archived else R.string.no_results),
                stringResource(if (emptyRegister) R.string.no_employees_body else if (archived && query.isBlank()) R.string.no_archived_body else R.string.no_results_body),
                Icons.Outlined.PeopleAlt, actionLabel = if (emptyRegister) stringResource(R.string.add_employee) else null,
                onAction = if (emptyRegister) onAdd else null)
        }
        items(filtered, key = { it.id }) { employee ->
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                EmployeeRow(employee, { onSelect(employee) }, Modifier.padding(horizontal = space.large))
            }
        }
    }
}
