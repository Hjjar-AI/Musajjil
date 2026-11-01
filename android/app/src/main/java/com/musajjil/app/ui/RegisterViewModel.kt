package com.musajjil.app.ui

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musajjil.app.R
import com.musajjil.app.data.RegisterRepository
import com.musajjil.app.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RegisterViewModel(private val repository: RegisterRepository) : ViewModel() {
    private val messages = Channel<Int>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    val data = repository.data.catch { error ->
        Log.e("Musajjil", "Could not open local register", error)
        messages.send(R.string.load_failed)
        emit(RegisterData(loading = false))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RegisterData())

    fun saveEmployee(value: Employee, onSaved: () -> Unit) = perform(onSaved) { repository.saveEmployee(value) }
    fun saveLeave(value: DayLeave, onSaved: () -> Unit) = perform(onSaved) { repository.saveLeave(value) }
    fun addHours(value: HourEntry, onSaved: () -> Unit) = perform(onSaved) { repository.addHours(value) }
    fun updateThreshold(value: Int, onSaved: () -> Unit) = perform(onSaved) { repository.updateThreshold(value) }
    fun updateLeavePolicy(value: LeavePolicy, onSaved: () -> Unit) = perform(onSaved) { repository.updateLeavePolicy(value) }
    fun addHoliday(value: Holiday, onSaved: () -> Unit) = perform(onSaved) { repository.addHoliday(value) }

    private fun perform(onSaved: () -> Unit, action: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                action()
                onSaved()
                messages.send(R.string.saved)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("Musajjil", "Local transaction failed", error)
                messages.send(if (error is IllegalArgumentException) R.string.invalid_data else R.string.save_failed)
            } finally {
                _busy.value = false
            }
        }
    }

    class Factory(private val repository: RegisterRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(RegisterViewModel::class.java))
            return RegisterViewModel(repository) as T
        }
    }
}
