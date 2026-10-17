package com.isedalabs.stickler.core.reminder

import com.isedalabs.stickler.core.data.MissedReason
import com.isedalabs.stickler.core.data.SettingsRepository
import com.isedalabs.stickler.core.data.Task
import com.isedalabs.stickler.core.data.TaskRepository
import com.isedalabs.stickler.core.data.TaskStatus
import com.isedalabs.stickler.core.data.canDelete
import com.isedalabs.stickler.core.data.canEditTime
import com.isedalabs.stickler.core.data.canEditTitle
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Debug: the task [ReminderEngine.createTestTask] created and when its alarm fires. */
data class TestTaskScheduled(val taskId: Long, val at: Instant, val result: ScheduleResult)

/** Outcome of adding or editing a task. */
sealed interface EditResult {
    /** Stored; [schedule] is the alarm result, or null if the alarm didn't change (title-only edit). */
    data class Saved(val taskId: Long, val schedule: ScheduleResult?) : EditResult

    /** The requested time isn't in the future. */
    data object TimeInPast : EditResult

    /** The task's state doesn't allow this change (see `canEditTitle`, `canEditTime`). */
    data object NotAllowed : EditResult

    data object NotFound : EditResult
}

/**
 * Owns every reminder state change. Handlers reload the task and ignore events that no longer
 * apply (e.g. a repost alarm for a task already marked Done), so stale intents are harmless.
 *
 * Alarms and notification actions (receivers, on a background thread) and the UI can call in at
 * the same moment, so each public method holds [mutex] for its whole read-modify-write. The lock
 * isn't reentrant: public methods call only private `...Locked` bodies, never each other.
 */
@Singleton
class ReminderEngine @Inject constructor(
    private val tasks: TaskRepository,
    private val scheduler: AlarmScheduler,
    private val notifier: ReminderNotifier,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    /** Debug: creates a task due [delay] from now and schedules it. */
    suspend fun createTestTask(
        delay: Duration = ReminderPolicy.TEST_TASK_DELAY,
        title: String = "Test task",
    ): TestTaskScheduled = mutex.withLock {
        val at = clock.instant().plus(delay)
        val id = tasks.insert(Task(title = title, scheduledAt = at, remindAt = at))
        TestTaskScheduled(id, at, scheduler.schedule(id, at, AlarmKind.TaskDue))
    }

    /** Stores a new task and schedules its alarm. Only future times are accepted. */
    suspend fun addTask(task: Task): EditResult = mutex.withLock {
        if (!task.scheduledAt.isAfter(clock.instant())) return@withLock EditResult.TimeInPast
        val id = tasks.insert(
            task.copy(id = 0, title = task.title.trim(), status = TaskStatus.Scheduled, snoozeCount = 0, remindAt = task.scheduledAt),
        )
        EditResult.Saved(id, scheduler.schedule(id, task.scheduledAt, AlarmKind.TaskDue))
    }

    /**
     * Changes a task's title and time. The title can change while the task is active; the time
     * only before its time has come (otherwise moving it would bypass the snooze limit).
     */
    suspend fun editTask(taskId: Long, title: String, scheduledAt: Instant): EditResult = mutex.withLock {
        val task = tasks.get(taskId) ?: return@withLock EditResult.NotFound
        if (!task.canEditTitle) return@withLock EditResult.NotAllowed
        if (scheduledAt == task.scheduledAt) {
            val renamed = task.copy(title = title.trim())
            tasks.update(renamed)
            // A title fix isn't a new alert, so update the notification silently.
            if (renamed.status == TaskStatus.Due) show(renamed, silent = true)
            return@withLock EditResult.Saved(taskId, null)
        }
        val now = clock.instant()
        if (!task.canEditTime(now)) return@withLock EditResult.NotAllowed
        if (!scheduledAt.isAfter(now)) return@withLock EditResult.TimeInPast
        tasks.update(task.copy(title = title.trim(), scheduledAt = scheduledAt, remindAt = scheduledAt))
        // Same (task, kind) PendingIntent, so this replaces the old alarm.
        EditResult.Saved(taskId, scheduler.schedule(taskId, scheduledAt, AlarmKind.TaskDue))
    }

    /** Deletes a task whose time hasn't come yet, with its alarms. Returns false if not allowed. */
    suspend fun deleteTask(taskId: Long): Boolean = mutex.withLock {
        val task = tasks.get(taskId) ?: return@withLock false
        if (!task.canDelete(clock.instant())) return@withLock false
        cancelReminders(taskId)
        tasks.delete(task)
        true
    }

    suspend fun onTaskDue(taskId: Long) = mutex.withLock { taskDueLocked(taskId) }

    suspend fun onDone(taskId: Long) = mutex.withLock {
        resolveLocked(taskId) { it.copy(status = TaskStatus.Done, completedAt = clock.instant(), remindAt = null) }
    }

    suspend fun onMissed(taskId: Long) = mutex.withLock {
        resolveLocked(taskId) { it.copy(status = TaskStatus.Missed, missedReason = MissedReason.UserMarked, remindAt = null) }
    }

    /** Returns false if the task isn't Due or has used all its snoozes. */
    suspend fun onSnooze(taskId: Long): Boolean = mutex.withLock {
        val task = tasks.get(taskId) ?: return@withLock false
        if (task.status != TaskStatus.Due || !canSnooze(task)) return@withLock false
        val until = clock.instant().plus(ReminderPolicy.SNOOZE)
        tasks.update(task.copy(status = TaskStatus.Snoozed, snoozeCount = task.snoozeCount + 1, remindAt = until))
        scheduler.cancel(taskId, AlarmKind.Repost)
        notifier.cancel(taskId)
        scheduler.schedule(taskId, until, AlarmKind.TaskDue)
        true
    }

    /** The user swiped the notification away: bring it back after [ReminderPolicy.REPOST_DELAY]. */
    suspend fun onDismissed(taskId: Long) = mutex.withLock {
        val task = tasks.get(taskId) ?: return@withLock
        if (task.status != TaskStatus.Due) return@withLock
        scheduler.schedule(taskId, clock.instant().plus(ReminderPolicy.REPOST_DELAY), AlarmKind.Repost)
    }

    suspend fun onRepost(taskId: Long) = mutex.withLock {
        val task = tasks.get(taskId) ?: return@withLock
        if (task.status == TaskStatus.Due) show(task)
    }

    /** After boot, app update or an exact-alarm permission change: restore alarms and notifications. */
    suspend fun rescheduleAll() = mutex.withLock {
        val now = clock.instant()
        for (task in tasks.getActive()) {
            when (task.status) {
                TaskStatus.Scheduled, TaskStatus.Snoozed -> {
                    val at = task.remindAt ?: task.scheduledAt
                    if (at.isAfter(now)) scheduler.schedule(task.id, at, AlarmKind.TaskDue) else taskDueLocked(task.id)
                }
                TaskStatus.Due -> show(task)
                TaskStatus.Done, TaskStatus.Missed -> Unit
            }
        }
    }

    private suspend fun taskDueLocked(taskId: Long) {
        val task = tasks.get(taskId) ?: return
        when (task.status) {
            TaskStatus.Scheduled, TaskStatus.Snoozed -> {
                val due = task.copy(status = TaskStatus.Due, remindAt = null)
                tasks.update(due)
                show(due)
            }
            TaskStatus.Due -> show(task)
            TaskStatus.Done, TaskStatus.Missed -> Unit
        }
    }

    private suspend fun resolveLocked(taskId: Long, transform: (Task) -> Task) {
        val task = tasks.get(taskId)
        if (task == null) {
            // Done/Missed from a notification whose task no longer exists: still clear it.
            cancelReminders(taskId)
            return
        }
        if (!task.status.isActive) return
        tasks.update(transform(task))
        cancelReminders(taskId)
    }

    private fun cancelReminders(taskId: Long) {
        scheduler.cancel(taskId, AlarmKind.TaskDue)
        scheduler.cancel(taskId, AlarmKind.Repost)
        notifier.cancel(taskId)
    }

    /** Snoozes per task come from the app-wide setting (default 2). */
    private suspend fun canSnooze(task: Task) = task.snoozeCount < settings.get().maxSnoozes

    private suspend fun show(task: Task, silent: Boolean = false) = notifier.show(task, canSnooze(task), silent)
}
