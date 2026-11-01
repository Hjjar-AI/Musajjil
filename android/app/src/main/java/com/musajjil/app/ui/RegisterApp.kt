package com.musajjil.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musajjil.app.R
import com.musajjil.app.domain.DayLeave
import com.musajjil.app.ui.components.EmptyState
import com.musajjil.app.ui.forms.*
import com.musajjil.app.ui.screens.*
import com.musajjil.app.ui.theme.LocalAppDimensions
import java.time.LocalDate
import kotlinx.coroutines.delay

/** Navigation and persistence are composed here; screens can be previewed without a database. */
private enum class Destination(@param:StringRes val label: Int, val icon: ImageVector) {
    Home(R.string.home, Icons.Outlined.Home),
    Employees(R.string.employees, Icons.Outlined.PeopleAlt),
    Leave(R.string.leave, Icons.AutoMirrored.Outlined.EventNote),
    Calendar(R.string.calendar, Icons.Outlined.CalendarMonth),
    More(R.string.more, Icons.Outlined.MoreHoriz),
}
private enum class Overlay { EmployeeDetails, EmployeeForm, LeaveForm, HoursForm, Threshold, Policy, Holiday }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterApp(viewModel: RegisterViewModel) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var destination by rememberSaveable { mutableStateOf(Destination.Home) }
    var overlay by rememberSaveable { mutableStateOf<Overlay?>(null) }
    var employeeId by rememberSaveable { mutableStateOf<String?>(null) }
    var leaveId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedEmployee = data.employees.firstOrNull { it.id == employeeId }
    val selectedLeave = data.leaves.firstOrNull { it.id == leaveId }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val stateHolder = rememberSaveableStateHolder()
    val today = currentDate()
    val dismiss = { overlay = null }
    fun newEmployee() { employeeId = null; overlay = Overlay.EmployeeForm }
    fun newLeave(id: String? = null) { employeeId = id; leaveId = null; overlay = Overlay.LeaveForm }
    fun newHours() { employeeId = null; overlay = Overlay.HoursForm }
    fun editLeave(value: DayLeave) { leaveId = value.id; overlay = Overlay.LeaveForm }

    LaunchedEffect(viewModel, context) {
        viewModel.events.collect { snackbar.showSnackbar(context.getString(it)) }
    }
    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge) },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)) },
        bottomBar = { NavigationBar {
            Destination.entries.forEach { tab -> NavigationBarItem(
                selected = destination == tab, onClick = { destination = tab },
                icon = { Icon(tab.icon, contentDescription = null) }, label = { Text(stringResource(tab.label), maxLines = 1) }) }
        } },
        snackbarHost = { SnackbarHost(snackbar) }, containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = LocalAppDimensions.current.maxContentWidth).fillMaxSize()) {
                when {
                    data.loading -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(LocalAppDimensions.current.large)) {
                        CircularProgressIndicator(); Text(stringResource(R.string.loading))
                    }
                    data.loadFailed -> EmptyState(stringResource(R.string.load_failed), "", Icons.Outlined.ErrorOutline,
                        Modifier.align(Alignment.Center).padding(LocalAppDimensions.current.page))
                    else -> stateHolder.SaveableStateProvider(destination.name) {
                        when (destination) {
                            Destination.Home -> HomeScreen(data, today, { newLeave() }, ::newHours, ::newEmployee,
                                { destination = Destination.Leave }, ::editLeave)
                            Destination.Employees -> EmployeesScreen(data.employees, ::newEmployee,
                                { employeeId = it.id; overlay = Overlay.EmployeeDetails })
                            Destination.Leave -> LeaveScreen(data, today, { newLeave() }, ::newHours, ::editLeave)
                            Destination.Calendar -> CalendarScreen(data, today, ::editLeave)
                            Destination.More -> MoreScreen(data, { overlay = Overlay.Threshold }, { overlay = Overlay.Policy }, { overlay = Overlay.Holiday })
                        }
                    }
                }
            }
        }
    }
    if (!data.loading && !data.loadFailed) key(overlay, employeeId, leaveId) {
        when (overlay) {
            Overlay.EmployeeDetails -> selectedEmployee?.let { employee ->
                EmployeeDetailsSheet(employee, data, busy, dismiss,
                    { overlay = Overlay.EmployeeForm },
                    { viewModel.saveEmployee(employee.copy(archived = !employee.archived), dismiss) },
                    { newLeave(employee.id) }, ::editLeave)
            }
            Overlay.EmployeeForm -> EmployeeForm(selectedEmployee, data.employees, busy, dismiss) { viewModel.saveEmployee(it, dismiss) }
            Overlay.LeaveForm -> LeaveForm(data, selectedLeave, employeeId, busy, dismiss) { viewModel.saveLeave(it, dismiss) }
            Overlay.HoursForm -> HoursForm(data, employeeId, busy, dismiss) { viewModel.addHours(it, dismiss) }
            Overlay.Threshold -> ThresholdForm(data.threshold, busy, dismiss) { viewModel.updateThreshold(it, dismiss) }
            Overlay.Policy -> LeavePolicyForm(data.leavePolicy, busy, dismiss) { viewModel.updateLeavePolicy(it, dismiss) }
            Overlay.Holiday -> HolidayForm(busy, dismiss) { viewModel.addHoliday(it, dismiss) }
            null -> Unit
        }
    }
}

/** Refresh on resume and across midnight, including after a phone date change. */
@Composable
private fun currentDate(): LocalDate {
    var date by remember { mutableStateOf(LocalDate.now()) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) { date = LocalDate.now(); delay(30_000) }
        }
    }
    return date
}
