package com.isedalabs.stickler.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isedalabs.stickler.core.data.Task
import com.isedalabs.stickler.core.data.TaskRepository
import com.isedalabs.stickler.core.reminder.ReminderEngine
import com.isedalabs.stickler.core.reminder.TestTaskScheduled
import com.isedalabs.stickler.reminder.PermissionChecker
import com.isedalabs.stickler.reminder.ReminderPermissions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import javax.inject.Inject

/** Phase 1 debug screen: permission status, "schedule a test task", and the stored tasks. */
@HiltViewModel
class DebugViewModel @Inject constructor(
    private val engine: ReminderEngine,
    private val permissionChecker: PermissionChecker,
    tasks: TaskRepository,
) : ViewModel() {

    private val _permissions = MutableStateFlow(permissionChecker.read())
    val permissions: StateFlow<ReminderPermissions> = _permissions.asStateFlow()

    val tasks: StateFlow<List<Task>> = tasks.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Test task delays offered on the screen. */
    val presets: List<Duration> = listOf(1L, 5L, 15L).map(Duration::ofMinutes)

    private val _lastScheduled = MutableStateFlow<TestTaskScheduled?>(null)

    /** The most recent test task, so its fire time stays on screen. */
    val lastScheduled: StateFlow<TestTaskScheduled?> = _lastScheduled.asStateFlow()

    /** Called on resume: the user may have changed a permission in system settings. */
    fun refreshPermissions() {
        _permissions.value = permissionChecker.read()
    }

    fun scheduleTestTask(delay: Duration) {
        viewModelScope.launch {
            _lastScheduled.value = engine.createTestTask(delay)
        }
    }
}
