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
import com.musajjil.app.domain.*
import com.musajjil.app.ui.components.AppBrand
import com.musajjil.app.ui.components.EmptyState
import com.musajjil.app.ui.forms.*
import com.musajjil.app.ui.screens.*
import com.musajjil.app.ui.theme.LocalAppDimensions
import java.time.LocalDate
import kotlinx.coroutines.delay

private enum class Destination(@param:StringRes val label: Int, val icon: ImageVector) {
    Home(R.string.home, Icons.Outlined.Home), Employees(R.string.employees, Icons.Outlined.PeopleAlt),
    Leave(R.string.leave, Icons.AutoMirrored.Outlined.EventNote), Calendar(R.string.calendar, Icons.Outlined.CalendarMonth),
    More(R.string.more, Icons.Outlined.MoreHoriz),
}
private enum class Overlay {
    EmployeeDetails, EmployeeForm, Archive, LeaveForm, HoursForm, Threshold, Holiday,
    Groups, GroupForm, Fields, FieldForm, Types, TypeForm, Bulk,
    ServiceDirectory, Service, ServicePeriod, ServiceProfile, History, Backups,
}

/** Screens receive data and actions; all mutations run through the shared repository. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterApp(viewModel: RegisterViewModel) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val backup by viewModel.backupStatus.collectAsStateWithLifecycle()
    val restorePreview by viewModel.restorePreview.collectAsStateWithLifecycle()
    var destination by rememberSaveable { mutableStateOf(Destination.Home) }
    var overlay by rememberSaveable { mutableStateOf<Overlay?>(null) }
    var employeeId by rememberSaveable { mutableStateOf<String?>(null) }
    var recordId by rememberSaveable { mutableStateOf<String?>(null) }
    var recordParent by rememberSaveable { mutableStateOf<Overlay?>(null) }
    var serviceParent by rememberSaveable { mutableStateOf<Overlay?>(null) }
    var historyParent by rememberSaveable { mutableStateOf<Overlay?>(null) }
    val employee = data.employees.firstOrNull { it.id == employeeId }
    val selectedLeave = data.leaves.firstOrNull { it.id == recordId }
    val selectedHour = data.hours.firstOrNull { it.id == recordId }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val stateHolder = rememberSaveableStateHolder()
    val today = currentDate()
    fun dismiss() {
        overlay = when (overlay) {
            Overlay.GroupForm -> Overlay.Groups
            Overlay.FieldForm -> Overlay.Fields
            Overlay.TypeForm -> Overlay.Types
            Overlay.ServicePeriod, Overlay.ServiceProfile -> Overlay.Service
            Overlay.Archive -> Overlay.EmployeeDetails
            Overlay.EmployeeForm -> if (employeeId != null) Overlay.EmployeeDetails else null
            Overlay.Service -> serviceParent
            Overlay.History -> historyParent
            Overlay.LeaveForm, Overlay.HoursForm -> recordParent
            else -> null
        }
        recordId = null
    }
    fun newEmployee() { employeeId = null; recordId = null; overlay = Overlay.EmployeeForm }
    fun newLeave(id: String? = null) { recordParent = overlay; employeeId = id; recordId = null; overlay = Overlay.LeaveForm }
    fun newHours() { recordParent = overlay; employeeId = null; recordId = null; overlay = Overlay.HoursForm }
    fun editLeave(value: DayLeave) { recordParent = overlay; recordId = value.id; overlay = Overlay.LeaveForm }
    fun editHours(value: HourEntry) { recordParent = overlay; recordId = value.id; overlay = Overlay.HoursForm }
    fun openService(value: Employee) { serviceParent = overlay; employeeId = value.id; recordId = null; overlay = Overlay.Service }
    fun openHistory(id: String? = null) { historyParent = overlay; employeeId = id; recordId = null; overlay = Overlay.History }
    fun manage(value: Overlay) { recordId = null; overlay = value }

    LaunchedEffect(viewModel, context) { viewModel.events.collect { snackbar.showSnackbar(context.getString(it)) } }
    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { AppBrand() },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)) },
        bottomBar = { NavigationBar { Destination.entries.forEach { tab -> NavigationBarItem(
            selected = destination == tab, onClick = { destination = tab }, icon = { Icon(tab.icon, contentDescription = null) },
            label = { Text(stringResource(tab.label), maxLines = 1) }) } } },
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
                                { destination = Destination.Leave }, ::editLeave, backup, { manage(Overlay.Backups) }, ::openService,
                                { employeeId = null; manage(Overlay.ServiceDirectory) })
                            Destination.Employees -> EmployeesScreen(data.employees, ::newEmployee,
                                { employeeId = it.id; manage(Overlay.EmployeeDetails) })
                            Destination.Leave -> LeaveScreen(data, today, { newLeave() }, ::newHours, ::editLeave, ::editHours)
                            Destination.Calendar -> CalendarScreen(data, today, ::editLeave)
                            Destination.More -> MoreScreen(data, backup,
                                onThreshold = { manage(Overlay.Threshold) }, onTypes = { manage(Overlay.Types) },
                                onHoliday = { manage(Overlay.Holiday) }, onEditHoliday = { recordId = it.id; overlay = Overlay.Holiday },
                                onGroups = { manage(Overlay.Groups) }, onFields = { manage(Overlay.Fields) }, onBulk = { manage(Overlay.Bulk) },
                                onService = { employeeId = null; manage(Overlay.ServiceDirectory) }, onHistory = { openHistory() }, onBackups = { manage(Overlay.Backups) })
                        }
                    }
                }
            }
        }
    }
    if (!data.loading && !data.loadFailed) key(overlay, employeeId, recordId) {
        when (overlay) {
            Overlay.EmployeeDetails -> employee?.let {
                EmployeeDetailsSheet(it, data, busy, ::dismiss, { overlay = Overlay.EmployeeForm }, { overlay = Overlay.Archive },
                    { newLeave(it.id) }, ::editLeave, { openService(it) }, { openHistory(it.id) }, ::editHours)
            }
            Overlay.EmployeeForm -> EmployeeForm(employee, data, busy, ::dismiss) { viewModel.saveEmployee(it, ::dismiss) }
            Overlay.Archive -> employee?.let { value -> ArchiveEmployeeForm(value, data.servicePeriods, busy, ::dismiss) {
                viewModel.archiveEmployee(value.id, it, ::dismiss)
            } }
            Overlay.LeaveForm -> LeaveForm(data, selectedLeave, employeeId, busy, ::dismiss,
                { value, reason -> viewModel.saveLeave(value, reason, ::dismiss) },
                { reason -> selectedLeave?.let { viewModel.deleteLeave(it.id, reason, ::dismiss) } })
            Overlay.HoursForm -> HoursForm(data, selectedHour, employeeId, busy, ::dismiss,
                { value, reason -> if (selectedHour == null) viewModel.addHours(value, ::dismiss)
                    else viewModel.correctHours(value, reason, false, ::dismiss) },
                { reason -> selectedHour?.let { viewModel.correctHours(it, reason, true, ::dismiss) } })
            Overlay.Threshold -> ThresholdForm(data.threshold, busy, ::dismiss) { viewModel.updateThreshold(it, ::dismiss) }
            Overlay.Holiday -> HolidayForm(data.holidays.firstOrNull { it.id == recordId }, busy, ::dismiss,
                { viewModel.addHoliday(it, ::dismiss) }, { reason -> recordId?.let { viewModel.deleteHoliday(it, reason, ::dismiss) } })
            Overlay.Groups, Overlay.Fields, Overlay.Types -> {
                val kind = when (overlay) { Overlay.Groups -> ManagementKind.Groups; Overlay.Fields -> ManagementKind.Fields; else -> ManagementKind.LeaveTypes }
                ManagementSheet(kind, data, busy, ::dismiss,
                    onAdd = { manage(when (kind) { ManagementKind.Groups -> Overlay.GroupForm; ManagementKind.Fields -> Overlay.FieldForm; ManagementKind.LeaveTypes -> Overlay.TypeForm }) },
                    onGroup = { recordId = it.id; overlay = Overlay.GroupForm }, onField = { recordId = it.id; overlay = Overlay.FieldForm },
                    onType = { recordId = it.id; overlay = Overlay.TypeForm })
            }
            Overlay.GroupForm -> GroupForm(data.groups.firstOrNull { it.id == recordId }, busy, ::dismiss) { viewModel.saveGroup(it, ::dismiss) }
            Overlay.FieldForm -> FieldForm(data.fields.firstOrNull { it.id == recordId }, data, busy, ::dismiss) { viewModel.saveField(it, ::dismiss) }
            Overlay.TypeForm -> LeaveTypeForm(data.leaveTypes.firstOrNull { it.id == recordId }, data, busy, ::dismiss) { viewModel.saveLeaveType(it, ::dismiss) }
            Overlay.Bulk -> BulkEmployeeForm(data, busy, ::dismiss) { viewModel.bulkEmployees(it, ::dismiss) }
            Overlay.ServiceDirectory -> ServiceDirectorySheet(data, busy, ::dismiss, ::openService)
            Overlay.Service -> employee?.let { ServiceSheet(it, data, today, busy, ::dismiss,
                onProfile = { manage(Overlay.ServiceProfile) }, onAddPeriod = { manage(Overlay.ServicePeriod) },
                onPeriod = { recordId = it.id; overlay = Overlay.ServicePeriod }, onHistory = { openHistory(it.id) }) }
            Overlay.ServicePeriod -> employee?.let { value -> ServicePeriodForm(value, data.servicePeriods.firstOrNull { it.id == recordId },
                data.servicePeriods, busy, ::dismiss, { period, reason -> viewModel.saveServicePeriod(period, reason, ::dismiss) },
                { reason -> recordId?.let { viewModel.deleteServicePeriod(it, reason, ::dismiss) } }) }
            Overlay.ServiceProfile -> employee?.let { value -> ServiceProfileForm(value, data.serviceProfiles.firstOrNull { it.employeeId == value.id },
                data.servicePeriods, busy, ::dismiss) { profile, reason -> viewModel.saveServiceProfile(profile, reason, ::dismiss) } }
            Overlay.History -> HistorySheet(data, employeeId, busy, ::dismiss)
            Overlay.Backups -> BackupSheet(backup, busy, ::dismiss, viewModel::chooseBackupFolder, viewModel::backupNow, viewModel::previewRestore)
            null -> Unit
        }
    }
    restorePreview?.let { RestorePreviewDialog(it, busy, viewModel::cancelRestore, viewModel::restoreReviewed) }
}

@Composable
private fun currentDate(): LocalDate {
    var date by remember { mutableStateOf(LocalDate.now()) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) { owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        while (true) { date = LocalDate.now(); delay(30_000) }
    } }
    return date
}
