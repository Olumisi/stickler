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
import com.isedalabs.stickler.core.reminder.ReminderEngine
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

    private val _day = MutableStateFlow(currentDay())
    val day: StateFlow<Day> = _day.asStateFlow()

    val tasks: StateFlow<List<Task>> = _day
        .flatMapLatest { tasks.observeIn(dayWindow(it.date, it.zone)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _permissions = MutableStateFlow(permissionChecker.read())
    val permissions: StateFlow<ReminderPermissions> = _permissions.asStateFlow()

    private val _editor = MutableStateFlow<TaskEditor?>(null)
    val editor: StateFlow<TaskEditor?> = _editor.asStateFlow()

    private val _messages = Channel<EditorMessage>(Channel.BUFFERED)
    val messages: Flow<EditorMessage> = _messages.receiveAsFlow()

    /** Called on resume: the date, time zone or permissions may have changed while away. */
    fun refresh() {
        refreshDay()
        refreshPermissions()
    }

    /**
     * Recomputes the local day. Called every minute while Today is on screen and on time or
     * time-zone changes; an unchanged day is ignored by the StateFlow, so nothing re-queries.
     */
    fun refreshDay() {
        _day.value = currentDay()
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
            time = defaultNewTaskTime(ZonedDateTime.ofInstant(clock.instant(), day.zone)),
            canEditTime = true,
            canDelete = false,
        )
    }

    fun openEdit(task: Task) {
        if (!task.canEditTitle) return
        val local = task.scheduledAt.atZone(_day.value.zone)
        val time = local.toLocalTime().truncatedTo(ChronoUnit.MINUTES)
        val now = clock.instant()
        _editor.value = TaskEditor(
            taskId = task.id,
            date = local.toLocalDate(),
            title = task.title,
            time = time,
            canEditTime = task.canEditTime(now),
            canDelete = task.canDelete(now),
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
            // Apply to the current state so anything typed while saving is kept.
            apply((_editor.value ?: editor).after(result))
        }
    }

    fun delete() {
        val editor = _editor.value ?: return
        val taskId = editor.taskId ?: return
        viewModelScope.launch {
            apply((_editor.value ?: editor).afterDelete(engine.deleteTask(taskId)))
        }
    }

    private suspend fun apply(outcome: EditorOutcome) {
        _editor.value = outcome.editor
        outcome.message?.let { _messages.send(it) }
    }

    private fun currentDay(): Day {
        val zone = ZoneId.systemDefault()
        return Day(clock.instant().atZone(zone).toLocalDate(), zone)
    }

    private fun TaskEditor.instant(zone: ZoneId): Instant = date.atTime(time).atZone(zone).toInstant()
}
