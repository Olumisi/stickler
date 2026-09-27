package com.isedalabs.stickler.debug

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isedalabs.stickler.core.data.Task
import com.isedalabs.stickler.core.data.TaskRepository
import com.isedalabs.stickler.core.reminder.ReminderEngine
import com.isedalabs.stickler.core.reminder.ScheduleResult
import com.isedalabs.stickler.reminder.AndroidAlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Phase 1 debug screen: permission status, "schedule a test task", and the stored tasks. */
@HiltViewModel
class DebugViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val engine: ReminderEngine,
    private val scheduler: AndroidAlarmScheduler,
    tasks: TaskRepository,
) : ViewModel() {

    data class Permissions(val notifications: Boolean, val exactAlarms: Boolean)

    private val _permissions = MutableStateFlow(readPermissions())
    val permissions: StateFlow<Permissions> = _permissions.asStateFlow()

    val tasks: StateFlow<List<Task>> = tasks.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = Channel<Message>(Channel.BUFFERED)
    val messages: Flow<Message> = _messages.receiveAsFlow()

    enum class Message { ScheduledExact, ScheduledInexact }

    /** Called on resume: the user may have changed a permission in system settings. */
    fun refreshPermissions() {
        _permissions.value = readPermissions()
    }

    fun scheduleTestTask() {
        viewModelScope.launch {
            val result = engine.createTestTask()
            _messages.send(if (result == ScheduleResult.Exact) Message.ScheduledExact else Message.ScheduledInexact)
        }
    }

    private fun readPermissions() = Permissions(
        notifications = notificationsAllowed(),
        exactAlarms = scheduler.canScheduleExact(),
    )

    private fun notificationsAllowed(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
