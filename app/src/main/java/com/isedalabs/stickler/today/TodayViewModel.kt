package com.isedalabs.stickler.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isedalabs.stickler.core.data.PlanRepository
import com.isedalabs.stickler.core.data.Task
import com.isedalabs.stickler.core.data.TaskRepository
import com.isedalabs.stickler.core.data.canDelete
import com.isedalabs.stickler.core.data.canEditTime
import com.isedalabs.stickler.core.data.canEditTitle
import com.isedalabs.stickler.core.data.dayWindow
import com.isedalabs.stickler.core.reminder.EditResult
import com.isedalabs.stickler.core.reminder.ReminderEngine
import com.isedalabs.stickler.core.reminder.ScheduleResult
import com.isedalabs.stickler.reminder.PermissionChecker
import com.isedalabs.stickler.reminder.ReminderPermissions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** The local day being shown; times are stored in UTC and converted with [zone] for display. */
data class Day(val date: LocalDate, val zone: ZoneId)

/**
 * The add/edit sheet. [taskId] and [originalAt] are null for a new task. While [time] still
 * equals [originalTime], saving keeps the stored instant, so a title-only edit never moves the alarm.
 */
data class TaskEditor(
    val taskId: Long?,
    val date: LocalDate,
    val title: String,
    val time: LocalTime,
    val canEditTime: Boolean,
    val canDelete: Boolean,
    val originalAt: Instant? = null,
    val originalTime: LocalTime? = null,
) {
    val canSave: Boolean get() = title.isNotBlank()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val engine: ReminderEngine,
    private val plans: PlanRepository,
    private val permissionChecker: PermissionChecker,
    private val clock: Clock,
    tasks: TaskRepository,
) : ViewModel() {

    enum class Message { ScheduledInexact, TimeInPast, TimeLocked, NotFound }

    private val _day = MutableStateFlow(currentDay())
    val day: StateFlow<Day> = _day.asStateFlow()

    val tasks: StateFlow<List<Task>> = _day
        .flatMapLatest { tasks.observeIn(dayWindow(it.date, it.zone)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _permissions = MutableStateFlow(permissionChecker.read())
    val permissions: StateFlow<ReminderPermissions> = _permissions.asStateFlow()

    private val _editor = MutableStateFlow<TaskEditor?>(null)
    val editor: StateFlow<TaskEditor?> = _editor.asStateFlow()

    private val _messages = Channel<Message>(Channel.BUFFERED)
    val messages: Flow<Message> = _messages.receiveAsFlow()

    /** Called on resume: the date, time zone or permissions may have changed while away. */
    fun refresh() {
        _day.value = currentDay()
        refreshPermissions()
    }

    fun refreshPermissions() {
        _permissions.value = permissionChecker.read()
    }

    fun openNew() {
        val day = _day.value
        _editor.value = TaskEditor(
            taskId = null,
            date = day.date,
            title = "",
            time = nextQuarterHour(day.zone),
            canEditTime = true,
            canDelete = false,
        )
    }

    fun openEdit(task: Task) {
        if (!task.canEditTitle) return
        val local = task.scheduledAt.atZone(_day.value.zone)
        val time = local.toLocalTime().truncatedTo(ChronoUnit.MINUTES)
        _editor.value = TaskEditor(
            taskId = task.id,
            date = local.toLocalDate(),
            title = task.title,
            time = time,
            canEditTime = task.canEditTime,
            canDelete = task.canDelete,
            originalAt = task.scheduledAt,
            originalTime = time,
        )
    }

    fun closeEditor() {
        _editor.value = null
    }

    fun setTitle(title: String) = _editor.update { it?.copy(title = title) }

    fun setTime(time: LocalTime) = _editor.update { if (it?.canEditTime == true) it.copy(time = time) else it }

    fun save() {
        val editor = _editor.value ?: return
        if (!editor.canSave) return
        viewModelScope.launch {
            val zone = _day.value.zone
            val result = if (editor.taskId == null) {
                val planId = plans.getOrCreateDayPlan(editor.date, zone)
                engine.addTask(Task(planId = planId, title = editor.title, scheduledAt = editor.instant(zone)))
            } else {
                val at = editor.originalAt?.takeIf { editor.time == editor.originalTime } ?: editor.instant(zone)
                engine.editTask(editor.taskId, editor.title, at)
            }
            when (result) {
                is EditResult.Saved -> {
                    _editor.value = null
                    if (result.schedule == ScheduleResult.Inexact) _messages.send(Message.ScheduledInexact)
                }
                EditResult.TimeInPast -> _messages.send(Message.TimeInPast)
                EditResult.NotAllowed -> {
                    // The task came due while the sheet was open: lock the time, keep the typed title.
                    _editor.update { it?.copy(canEditTime = false, canDelete = false) }
                    _messages.send(Message.TimeLocked)
                }
                EditResult.NotFound -> {
                    _editor.value = null
                    _messages.send(Message.NotFound)
                }
            }
        }
    }

    fun delete() {
        val taskId = _editor.value?.taskId ?: return
        viewModelScope.launch {
            if (engine.deleteTask(taskId)) {
                _editor.value = null
            } else {
                _editor.update { it?.copy(canEditTime = false, canDelete = false) }
                _messages.send(Message.TimeLocked)
            }
        }
    }

    private fun currentDay(): Day {
        val zone = ZoneId.systemDefault()
        return Day(clock.instant().atZone(zone).toLocalDate(), zone)
    }

    /** New tasks default to the next whole quarter hour. */
    private fun nextQuarterHour(zone: ZoneId): LocalTime {
        val now = ZonedDateTime.ofInstant(clock.instant(), zone).truncatedTo(ChronoUnit.MINUTES)
        return now.plusMinutes(15L - now.minute % 15).toLocalTime()
    }

    private fun TaskEditor.instant(zone: ZoneId): Instant = date.atTime(time).atZone(zone).toInstant()
}
