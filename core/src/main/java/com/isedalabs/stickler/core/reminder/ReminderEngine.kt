package com.isedalabs.stickler.core.reminder

import com.isedalabs.stickler.core.data.MissedReason
import com.isedalabs.stickler.core.data.SettingsRepository
import com.isedalabs.stickler.core.data.Task
import com.isedalabs.stickler.core.data.TaskRepository
import com.isedalabs.stickler.core.data.TaskStatus
import com.isedalabs.stickler.core.data.canDelete
import com.isedalabs.stickler.core.data.canEditTime
import com.isedalabs.stickler.core.data.canEditTitle
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
 */
@Singleton
class ReminderEngine @Inject constructor(
    private val tasks: TaskRepository,
    private val scheduler: AlarmScheduler,
    private val notifier: ReminderNotifier,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {

    /** Debug: creates a task due [delay] from now and schedules it. */
    suspend fun createTestTask(
        delay: Duration = ReminderPolicy.TEST_TASK_DELAY,
        title: String = "Test task",
    ): TestTaskScheduled {
        val at = clock.instant().plus(delay)
        val id = tasks.insert(Task(title = title, scheduledAt = at, remindAt = at))
        return TestTaskScheduled(id, at, scheduler.schedule(id, at, AlarmKind.TaskDue))
    }

    /** Stores a new task and schedules its alarm. Only future times are accepted. */
    suspend fun addTask(task: Task): EditResult {
        if (!task.scheduledAt.isAfter(clock.instant())) return EditResult.TimeInPast
        val id = tasks.insert(
            task.copy(id = 0, title = task.title.trim(), status = TaskStatus.Scheduled, snoozeCount = 0, remindAt = task.scheduledAt),
        )
        return EditResult.Saved(id, scheduler.schedule(id, task.scheduledAt, AlarmKind.TaskDue))
    }

    /**
     * Changes a task's title and time. The title can change while the task is active; the time
     * only before the task is due (otherwise moving it would bypass the snooze limit).
     */
    suspend fun editTask(taskId: Long, title: String, scheduledAt: Instant): EditResult {
        val task = tasks.get(taskId) ?: return EditResult.NotFound
        if (!task.canEditTitle) return EditResult.NotAllowed
        if (scheduledAt == task.scheduledAt) {
            val renamed = task.copy(title = title.trim())
            tasks.update(renamed)
            if (renamed.status == TaskStatus.Due) show(renamed)
            return EditResult.Saved(taskId, null)
        }
        if (!task.canEditTime) return EditResult.NotAllowed
        if (!scheduledAt.isAfter(clock.instant())) return EditResult.TimeInPast
        tasks.update(task.copy(title = title.trim(), scheduledAt = scheduledAt, remindAt = scheduledAt))
        // Same (task, kind) PendingIntent, so this replaces the old alarm.
        return EditResult.Saved(taskId, scheduler.schedule(taskId, scheduledAt, AlarmKind.TaskDue))
    }

    /** Deletes a task that hasn't come due yet, with its alarms. Returns false if not allowed. */
    suspend fun deleteTask(taskId: Long): Boolean {
        val task = tasks.get(taskId) ?: return false
        if (!task.canDelete) return false
        scheduler.cancel(taskId, AlarmKind.TaskDue)
        scheduler.cancel(taskId, AlarmKind.Repost)
        notifier.cancel(taskId)
        tasks.delete(task)
        return true
    }

    suspend fun onTaskDue(taskId: Long) {
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

    suspend fun onDone(taskId: Long) = resolve(taskId) {
        it.copy(status = TaskStatus.Done, completedAt = clock.instant(), remindAt = null)
    }

    suspend fun onMissed(taskId: Long) = resolve(taskId) {
        it.copy(status = TaskStatus.Missed, missedReason = MissedReason.UserMarked, remindAt = null)
    }

    /** Returns false if the task isn't Due or has used all its snoozes. */
    suspend fun onSnooze(taskId: Long): Boolean {
        val task = tasks.get(taskId) ?: return false
        if (task.status != TaskStatus.Due || !canSnooze(task)) return false
        val until = clock.instant().plus(ReminderPolicy.SNOOZE)
        tasks.update(task.copy(status = TaskStatus.Snoozed, snoozeCount = task.snoozeCount + 1, remindAt = until))
        scheduler.cancel(taskId, AlarmKind.Repost)
        notifier.cancel(taskId)
        scheduler.schedule(taskId, until, AlarmKind.TaskDue)
        return true
    }

    /** The user swiped the notification away: bring it back after [ReminderPolicy.REPOST_DELAY]. */
    suspend fun onDismissed(taskId: Long) {
        val task = tasks.get(taskId) ?: return
        if (task.status != TaskStatus.Due) return
        scheduler.schedule(taskId, clock.instant().plus(ReminderPolicy.REPOST_DELAY), AlarmKind.Repost)
    }

    suspend fun onRepost(taskId: Long) {
        val task = tasks.get(taskId) ?: return
        if (task.status == TaskStatus.Due) show(task)
    }

    /** After boot, app update or an exact-alarm permission change: restore alarms and notifications. */
    suspend fun rescheduleAll() {
        val now = clock.instant()
        for (task in tasks.getActive()) {
            when (task.status) {
                TaskStatus.Scheduled, TaskStatus.Snoozed -> {
                    val at = task.remindAt ?: task.scheduledAt
                    if (at.isAfter(now)) scheduler.schedule(task.id, at, AlarmKind.TaskDue) else onTaskDue(task.id)
                }
                TaskStatus.Due -> show(task)
                TaskStatus.Done, TaskStatus.Missed -> Unit
            }
        }
    }

    private suspend fun resolve(taskId: Long, transform: (Task) -> Task) {
        val task = tasks.get(taskId) ?: return
        if (!task.status.isActive) return
        tasks.update(transform(task))
        scheduler.cancel(taskId, AlarmKind.TaskDue)
        scheduler.cancel(taskId, AlarmKind.Repost)
        notifier.cancel(taskId)
    }

    /** Snoozes per task come from the app-wide setting (default 2). */
    private suspend fun canSnooze(task: Task) = task.snoozeCount < settings.get().maxSnoozes

    private suspend fun show(task: Task) = notifier.show(task, canSnooze(task))
}
