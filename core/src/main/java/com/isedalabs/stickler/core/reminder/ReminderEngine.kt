package com.isedalabs.stickler.core.reminder

import com.isedalabs.stickler.core.data.MissedReason
import com.isedalabs.stickler.core.data.Task
import com.isedalabs.stickler.core.data.TaskRepository
import com.isedalabs.stickler.core.data.TaskStatus
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns every reminder state change. Handlers reload the task and ignore events that no longer
 * apply (e.g. a repost alarm for a task already marked Done), so stale intents are harmless.
 */
@Singleton
class ReminderEngine @Inject constructor(
    private val tasks: TaskRepository,
    private val scheduler: AlarmScheduler,
    private val notifier: ReminderNotifier,
    private val clock: Clock,
) {
    private val maxSnoozes = ReminderPolicy.DEFAULT_MAX_SNOOZES

    /** Debug: creates a task due [ReminderPolicy.TEST_TASK_DELAY] from now and schedules it. */
    suspend fun createTestTask(title: String = "Test task"): ScheduleResult {
        val at = clock.instant().plus(ReminderPolicy.TEST_TASK_DELAY)
        val id = tasks.insert(Task(title = title, scheduledAt = at, remindAt = at))
        return scheduler.schedule(id, at, AlarmKind.TaskDue)
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

    private fun canSnooze(task: Task) = task.snoozeCount < maxSnoozes

    private fun show(task: Task) = notifier.show(task, canSnooze(task))
}
