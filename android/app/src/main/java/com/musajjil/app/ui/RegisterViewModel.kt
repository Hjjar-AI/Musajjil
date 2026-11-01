package com.musajjil.app.ui

import android.util.Log
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musajjil.app.R
import com.musajjil.app.data.RegisterRepository
import com.musajjil.app.backup.BackupArchive
import com.musajjil.app.backup.BackupManager
import com.musajjil.app.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RegisterViewModel(private val repository: RegisterRepository, private val backups: BackupManager) : ViewModel() {
    private val messages = Channel<Int>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    val backupStatus = backups.status
    private val _restorePreview = MutableStateFlow<BackupArchive?>(null)
    val restorePreview = _restorePreview.asStateFlow()
    val data = repository.data.catch { error ->
        Log.e("Musajjil", "Could not open local register", error)
        messages.send(R.string.load_failed)
        emit(RegisterData(loading = false, loadFailed = true))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RegisterData())

    fun saveEmployee(value: Employee, onSaved: () -> Unit) = perform(onSaved) { repository.saveEmployee(value) }
    fun saveLeave(value: DayLeave, reason: String = "", onSaved: () -> Unit) = perform(onSaved) { repository.saveLeave(value, reason) }
    fun addHours(value: HourEntry, onSaved: () -> Unit) = perform(onSaved) { repository.addHours(value) }
    fun updateThreshold(value: Int, onSaved: () -> Unit) = perform(onSaved) { repository.updateThreshold(value) }
    fun updateLeavePolicy(value: LeavePolicy, onSaved: () -> Unit) = perform(onSaved) { repository.updateLeavePolicy(value) }
    fun addHoliday(value: Holiday, onSaved: () -> Unit) = perform(onSaved) { repository.addHoliday(value) }
    fun deleteHoliday(id: String, reason: String, onSaved: () -> Unit) = perform(onSaved) { repository.deleteHoliday(id, reason) }
    fun archiveEmployee(id: String, date: java.time.LocalDate, onSaved: () -> Unit) = perform(onSaved) { repository.archiveEmployee(id, date) }
    fun saveGroup(value: EmployeeGroup, onSaved: () -> Unit) = perform(onSaved) { repository.saveGroup(value) }
    fun saveField(value: FieldDefinition, onSaved: () -> Unit) = perform(onSaved) { repository.saveField(value) }
    fun bulkEmployees(value: BulkEmployeeChange, onSaved: () -> Unit) = perform(onSaved) { repository.bulkEmployees(value) }
    fun saveLeaveType(value: LeaveType, onSaved: () -> Unit) = perform(onSaved) { repository.saveLeaveType(value) }
    fun deleteLeave(id: String, reason: String, onSaved: () -> Unit) = perform(onSaved) { repository.deleteLeave(id, reason) }
    fun correctHours(value: HourEntry, reason: String, delete: Boolean, onSaved: () -> Unit) = perform(onSaved) { repository.correctHours(value, reason, delete) }
    fun saveServicePeriod(value: ServicePeriod, reason: String, onSaved: () -> Unit) = perform(onSaved) { repository.saveServicePeriod(value, reason) }
    fun deleteServicePeriod(id: String, reason: String, onSaved: () -> Unit) = perform(onSaved) { repository.deleteServicePeriod(id, reason) }
    fun saveServiceProfile(value: ServiceProfile, reason: String, onSaved: () -> Unit) = perform(onSaved) { repository.saveServiceProfile(value, reason) }
    fun chooseBackupFolder(uri: Uri) = perform({}, failure = R.string.backup_failed) { backups.chooseFolder(uri) }
    fun backupNow() = perform({}, success = R.string.backup_success, failure = R.string.backup_failed) { backups.manualBackup() }
    fun previewRestore(uri: Uri) = perform({}, success = null, failure = R.string.backup_invalid) { _restorePreview.value = backups.readArchive(uri) }
    fun cancelRestore() { if (!_busy.value) _restorePreview.value = null }
    fun restoreReviewed() {
        val archive = _restorePreview.value ?: return
        perform({ _restorePreview.value = null }, success = R.string.backup_restored, failure = R.string.backup_restore_failed) { backups.restore(archive) }
    }

    private fun perform(onSaved: () -> Unit, success: Int? = R.string.saved, failure: Int? = null, action: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                action()
                onSaved()
                if (success != null) messages.send(success)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("Musajjil", "Local transaction failed", error)
                messages.send(failure ?: if (error is IllegalArgumentException) R.string.invalid_data else R.string.save_failed)
            } finally {
                _busy.value = false
            }
        }
    }

    class Factory(private val repository: RegisterRepository, private val backups: BackupManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(RegisterViewModel::class.java))
            return RegisterViewModel(repository, backups) as T
        }
    }
}
