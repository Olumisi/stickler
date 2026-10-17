package com.isedalabs.stickler.core.reminder

import com.isedalabs.stickler.core.data.MissedReason
import com.isedalabs.stickler.core.data.Settings
import com.isedalabs.stickler.core.data.Task
import com.isedalabs.stickler.core.data.TaskStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class ReminderEngineTest {

    private val start = Instant.parse("2026-09-27T14:00:00Z")
    private val clock = MutableClock(start)
    private val repo = FakeTaskRepository()
    private val scheduler = FakeAlarmScheduler()
    private val notifier = FakeNotifier()
    private val settings = FakeSettingsRepository()
    private val engine = ReminderEngine(repo, scheduler, notifier, settings, clock)

    private fun test(block: suspend () -> Unit) = runBlocking { block() }

    /** Creates a test task and fires its alarm, leaving it Due with its notification showing. */
    private suspend fun dueTask(): Long {
        engine.createTestTask()
        val id = repo.getActive().maxOf { it.id }
        clock.advance(ReminderPolicy.TEST_TASK_DELAY)
        engine.onTaskDue(id)
        return id
    }

    // --- Scheduling ---

    @Test
    fun createTestTask_storesScheduledTaskAndSchedulesAlarmOneMinuteAhead() = test {
        val result = engine.createTestTask()

        val task = repo.require(1)
        val expected = start.plus(Duration.ofMinutes(1))
        assertEquals(TestTaskScheduled(1, expected, ScheduleResult.Exact), result)
        assertEquals(TaskStatus.Scheduled, task.status)
        assertEquals(expected, task.scheduledAt)
        assertEquals(expected, task.remindAt)
        assertEquals(expected, scheduler.pending[1L to AlarmKind.TaskDue])
        assertTrue(notifier.showing.isEmpty())
    }

    @Test
    fun createTestTask_reportsInexactFallback() = test {
        scheduler.result = ScheduleResult.Inexact

        assertEquals(ScheduleResult.Inexact, engine.createTestTask().result)
    }

    @Test
    fun createTestTask_usesGivenDelay() = test {
        val result = engine.createTestTask(Duration.ofMinutes(15))

        val task = repo.require(result.taskId)
        val expected = start.plus(Duration.ofMinutes(15))
        assertEquals(expected, result.at)
        assertEquals(expected, task.scheduledAt)
        assertEquals(expected, task.remindAt)
        assertEquals(expected, scheduler.pending[result.taskId to AlarmKind.TaskDue])
    }

    @Test
    fun taskDue_marksDueAndShowsNotification() = test {
        val id = dueTask()

        val task = repo.require(id)
        assertEquals(TaskStatus.Due, task.status)
        assertNull(task.remindAt)
        assertEquals(true, notifier.showing[id])
    }

    @Test
    fun taskDue_twice_reshowsWithoutChangingState() = test {
        val id = dueTask()
        notifier.swipe(id)

        engine.onTaskDue(id)

        assertEquals(TaskStatus.Due, repo.require(id).status)
        assertTrue(id in notifier.showing)
    }

    @Test
    fun taskDue_forResolvedOrMissingTask_doesNothing() = test {
        val id = dueTask()
        engine.onDone(id)
        val shows = notifier.showCount

        engine.onTaskDue(id)
        engine.onTaskDue(99)

        assertEquals(TaskStatus.Done, repo.require(id).status)
        assertEquals(shows, notifier.showCount)
        assertTrue(notifier.showing.isEmpty())
    }

    // --- Done / Missed ---

    @Test
    fun done_resolvesTaskAndClearsAlarmsAndNotification() = test {
        val id = dueTask()
        notifier.swipe(id)
        engine.onDismissed(id) // leaves a pending repost

        engine.onDone(id)

        val task = repo.require(id)
        assertEquals(TaskStatus.Done, task.status)
        assertEquals(clock.now, task.completedAt)
        assertTrue(scheduler.pending.isEmpty())
        assertTrue(notifier.showing.isEmpty())
    }

    @Test
    fun done_beforeTaskIsDue_cancelsTheAlarm() = test {
        engine.createTestTask()

        engine.onDone(1)

        assertEquals(TaskStatus.Done, repo.require(1).status)
        assertTrue(scheduler.pending.isEmpty())
    }

    @Test
    fun missed_recordsUserMarkedAndClearsEverything() = test {
        val id = dueTask()

        engine.onMissed(id)

        val task = repo.require(id)
        assertEquals(TaskStatus.Missed, task.status)
        assertEquals(MissedReason.UserMarked, task.missedReason)
        assertNull(task.completedAt)
        assertTrue(scheduler.pending.isEmpty())
        assertTrue(notifier.showing.isEmpty())
    }

    @Test
    fun resolvingAnAlreadyResolvedTask_isIgnored() = test {
        val id = dueTask()
        engine.onMissed(id)

        engine.onDone(id)

        assertEquals(TaskStatus.Missed, repo.require(id).status)
        assertNull(repo.require(id).completedAt)
    }

    // --- Snooze ---

    @Test
    fun snooze_hidesNotificationAndSchedulesTenMinutesLater() = test {
        val id = dueTask()

        assertTrue(engine.onSnooze(id))

        val task = repo.require(id)
        val until = clock.now.plus(Duration.ofMinutes(10))
        assertEquals(TaskStatus.Snoozed, task.status)
        assertEquals(1, task.snoozeCount)
        assertEquals(until, task.remindAt)
        assertEquals(until, scheduler.pending[id to AlarmKind.TaskDue])
        assertTrue(notifier.showing.isEmpty())
    }

    @Test
    fun snooze_cancelsPendingRepost() = test {
        val id = dueTask()
        notifier.swipe(id)
        engine.onDismissed(id)

        engine.onSnooze(id)

        assertNull(scheduler.pending[id to AlarmKind.Repost])
    }

    @Test
    fun snoozedTask_becomesDueAgainWhenAlarmFires() = test {
        val id = dueTask()
        engine.onSnooze(id)
        clock.advance(ReminderPolicy.SNOOZE)

        engine.onTaskDue(id)

        assertEquals(TaskStatus.Due, repo.require(id).status)
        assertTrue(id in notifier.showing)
    }

    @Test
    fun snooze_isLimitedToMaxSnoozes() = test {
        val id = dueTask()
        repeat(Settings().maxSnoozes) {
            assertEquals(true, notifier.showing[id]) // Snooze action offered
            assertTrue(engine.onSnooze(id))
            clock.advance(ReminderPolicy.SNOOZE)
            engine.onTaskDue(id)
        }

        assertEquals(false, notifier.showing[id]) // Snooze action no longer offered
        assertFalse(engine.onSnooze(id))
        assertEquals(TaskStatus.Due, repo.require(id).status)
        assertEquals(Settings().maxSnoozes, repo.require(id).snoozeCount)
    }

    @Test
    fun snoozeLimit_comesFromSettings() = test {
        settings.settings = Settings(maxSnoozes = 1)
        val id = dueTask()

        assertTrue(engine.onSnooze(id))
        clock.advance(ReminderPolicy.SNOOZE)
        engine.onTaskDue(id)

        assertEquals(false, notifier.showing[id])
        assertFalse(engine.onSnooze(id))
    }

    @Test
    fun snooze_whenNotDue_isRefused() = test {
        engine.createTestTask()

        assertFalse(engine.onSnooze(1))
        assertEquals(TaskStatus.Scheduled, repo.require(1).status)
    }

    // --- Add, edit, delete ---

    private val inTenMinutes get() = clock.now.plus(Duration.ofMinutes(10))

    private suspend fun addTask(at: Instant = inTenMinutes): Long {
        val result = engine.addTask(Task(planId = 1, title = "Write report", scheduledAt = at))
        return (result as EditResult.Saved).taskId
    }

    @Test
    fun addTask_storesScheduledTaskAndSchedulesAlarm() = test {
        val at = inTenMinutes

        val result = engine.addTask(Task(planId = 1, title = "  Write report ", scheduledAt = at))

        assertEquals(EditResult.Saved(1, ScheduleResult.Exact), result)
        val task = repo.require(1)
        assertEquals("Write report", task.title)
        assertEquals(TaskStatus.Scheduled, task.status)
        assertEquals(at, task.remindAt)
        assertEquals(at, scheduler.pending[1L to AlarmKind.TaskDue])
    }

    @Test
    fun addTask_inThePast_isRejected() = test {
        assertEquals(EditResult.TimeInPast, engine.addTask(Task(title = "t", scheduledAt = clock.now)))
        assertTrue(repo.getActive().isEmpty())
        assertTrue(scheduler.pending.isEmpty())
    }

    @Test
    fun editTime_ofScheduledTask_movesAlarm() = test {
        val id = addTask()
        val newTime = clock.now.plus(Duration.ofMinutes(3))

        val result = engine.editTask(id, "Write report", newTime)

        assertEquals(EditResult.Saved(id, ScheduleResult.Exact), result)
        assertEquals(newTime, repo.require(id).scheduledAt)
        assertEquals(newTime, repo.require(id).remindAt)
        assertEquals(mapOf((id to AlarmKind.TaskDue) to newTime), scheduler.pending)
    }

    @Test
    fun editTime_toThePast_isRejected() = test {
        val at = inTenMinutes
        val id = addTask(at)

        assertEquals(EditResult.TimeInPast, engine.editTask(id, "Write report", clock.now.minusSeconds(1)))
        assertEquals(at, repo.require(id).scheduledAt)
        assertEquals(at, scheduler.pending[id to AlarmKind.TaskDue])
    }

    @Test
    fun editTitle_ofScheduledTask_keepsAlarm() = test {
        val at = inTenMinutes
        val id = addTask(at)

        assertEquals(EditResult.Saved(id, null), engine.editTask(id, "Send report", at))
        assertEquals("Send report", repo.require(id).title)
        assertEquals(at, scheduler.pending[id to AlarmKind.TaskDue])
        assertTrue(notifier.showing.isEmpty())
    }

    @Test
    fun editTitle_ofDueTask_updatesNotification() = test {
        val id = dueTask()
        val shown = notifier.showCount
        val alarms = scheduler.pending.toMap()

        val result = engine.editTask(id, "Renamed", repo.require(id).scheduledAt)

        assertEquals(EditResult.Saved(id, null), result)
        assertEquals("Renamed", repo.require(id).title)
        assertEquals(TaskStatus.Due, repo.require(id).status)
        assertEquals(shown + 1, notifier.showCount)
        assertEquals(true, notifier.lastSilent) // a title fix doesn't alert again
        assertEquals(alarms, scheduler.pending)
    }

    @Test
    fun alerts_areNotSilent() = test {
        val id = dueTask()
        assertEquals(false, notifier.lastSilent)

        notifier.swipe(id)
        engine.onDismissed(id)
        engine.onRepost(id)

        assertEquals(false, notifier.lastSilent)
    }

    @Test
    fun lateInexactAlarm_locksTimeAndDeleteOnceTimeHasPassed() = test {
        val at = inTenMinutes
        val id = addTask(at)
        clock.advance(Duration.ofMinutes(11)) // the alarm is late, so the task is still Scheduled

        assertEquals(TaskStatus.Scheduled, repo.require(id).status)
        assertEquals(EditResult.NotAllowed, engine.editTask(id, "Write report", clock.now.plus(Duration.ofHours(1))))
        assertFalse(engine.deleteTask(id))
        assertEquals(EditResult.Saved(id, null), engine.editTask(id, "Renamed", at))
        assertEquals(at, scheduler.pending[id to AlarmKind.TaskDue])
    }

    @Test
    fun rescheduleAll_afterEdit_usesEditedTime() = test {
        val id = addTask()
        val newTime = clock.now.plus(Duration.ofMinutes(25))
        engine.editTask(id, "Write report", newTime)
        scheduler.pending.clear() // a reboot drops all alarms

        engine.rescheduleAll()

        assertEquals(mapOf((id to AlarmKind.TaskDue) to newTime), scheduler.pending)
    }

    @Test
    fun done_forDeletedTask_stillClearsNotification() = test {
        val id = addTask()
        notifier.show(repo.require(id), canSnooze = true) // a leftover notification
        repo.delete(repo.require(id))

        engine.onDone(id)

        assertTrue(notifier.showing.isEmpty())
        assertTrue(scheduler.pending.isEmpty())
    }

    // --- Concurrent calls (alarm receiver vs UI) ---

    /** Runs [first] until it has read the task, then starts [second], then lets [first] finish. */
    private suspend fun <T> race(first: suspend () -> Unit, second: suspend () -> T): T = coroutineScope {
        val hold = CompletableDeferred<Unit>()
        repo.holdAfterGet = hold
        launch { first() }
        yield() // first() now holds a stale copy of the task
        val result = async { second() }
        yield()
        hold.complete(Unit)
        result.await()
    }

    @Test
    fun alarmFiringDuringDelete_neverLeavesNotificationForMissingTask() = test {
        val id = addTask()
        // The user acts a second before the task time, just as the alarm fires (e.g. a slightly early alarm).
        clock.advance(Duration.ofMinutes(10).minusSeconds(1))

        val deleted = race(first = { engine.onTaskDue(id) }, second = { engine.deleteTask(id) })

        // The alarm won: the task is Due and shown, and delete was refused.
        assertFalse(deleted)
        assertEquals(TaskStatus.Due, repo.require(id).status)
        assertTrue(id in notifier.showing)
    }

    @Test
    fun alarmFiringDuringTimeEdit_neverLosesTheEditSilently() = test {
        val at = inTenMinutes
        val id = addTask(at)
        // The user acts a second before the task time, just as the alarm fires (e.g. a slightly early alarm).
        clock.advance(Duration.ofMinutes(10).minusSeconds(1))
        val later = clock.now.plus(Duration.ofMinutes(20))

        val result = race(first = { engine.onTaskDue(id) }, second = { engine.editTask(id, "Write report", later) })

        // The alarm won, so the edit is reported as refused and no alarm is left at the new time.
        assertEquals(EditResult.NotAllowed, result)
        assertEquals(TaskStatus.Due, repo.require(id).status)
        assertEquals(at, repo.require(id).scheduledAt)
        assertTrue(scheduler.pending.values.none { it == later })
    }

    @Test
    fun editTime_ofDueTask_isNotAllowed() = test {
        val id = dueTask()
        val alarms = scheduler.pending.toMap()

        assertEquals(EditResult.NotAllowed, engine.editTask(id, "Test task", inTenMinutes))
        assertEquals(TaskStatus.Due, repo.require(id).status)
        assertEquals(alarms, scheduler.pending)
        assertTrue(id in notifier.showing)
    }

    @Test
    fun editTime_ofSnoozedTask_isNotAllowed() = test {
        val id = dueTask()
        engine.onSnooze(id)
        val snoozeEnd = repo.require(id).remindAt

        assertEquals(EditResult.NotAllowed, engine.editTask(id, "Test task", clock.now.plus(Duration.ofHours(1))))
        assertEquals(snoozeEnd, scheduler.pending[id to AlarmKind.TaskDue])
    }

    @Test
    fun edit_ofResolvedTask_isNotAllowed() = test {
        val id = dueTask()
        engine.onDone(id)

        assertEquals(EditResult.NotAllowed, engine.editTask(id, "Renamed", repo.require(id).scheduledAt))
        assertEquals("Test task", repo.require(id).title)
    }

    @Test
    fun edit_ofUnknownTask_isNotFound() = test {
        assertEquals(EditResult.NotFound, engine.editTask(42, "t", inTenMinutes))
    }

    @Test
    fun delete_ofScheduledTask_removesTaskAndAlarms() = test {
        val id = addTask()

        assertTrue(engine.deleteTask(id))
        assertFalse(repo.contains(id))
        assertTrue(scheduler.pending.isEmpty())
    }

    @Test
    fun delete_ofDueTask_isNotAllowed() = test {
        val id = dueTask()

        assertFalse(engine.deleteTask(id))
        assertTrue(repo.contains(id))
        assertTrue(id in notifier.showing)
    }

    @Test
    fun delete_ofUnknownTask_returnsFalse() = test {
        assertFalse(engine.deleteTask(42))
    }

    // --- Re-post on dismiss ---

    @Test
    fun dismiss_schedulesRepostAfterSixtySeconds() = test {
        val id = dueTask()
        notifier.swipe(id)

        engine.onDismissed(id)

        assertEquals(clock.now.plus(Duration.ofSeconds(60)), scheduler.pending[id to AlarmKind.Repost])
    }

    @Test
    fun repost_reshowsNotificationWhileDue() = test {
        val id = dueTask()
        notifier.swipe(id)
        engine.onDismissed(id)
        clock.advance(ReminderPolicy.REPOST_DELAY)

        engine.onRepost(id)

        assertTrue(id in notifier.showing)
        assertEquals(TaskStatus.Due, repo.require(id).status)
    }

    @Test
    fun repeatedDismissals_keepReposting() = test {
        val id = dueTask()
        repeat(3) {
            notifier.swipe(id)
            engine.onDismissed(id)
            clock.advance(ReminderPolicy.REPOST_DELAY)
            engine.onRepost(id)
            assertTrue(id in notifier.showing)
        }
    }

    @Test
    fun repost_afterDone_doesNothing() = test {
        val id = dueTask()
        notifier.swipe(id)
        engine.onDismissed(id)
        engine.onDone(id)

        engine.onRepost(id)

        assertTrue(notifier.showing.isEmpty())
    }

    @Test
    fun dismiss_ofResolvedTask_schedulesNothing() = test {
        val id = dueTask()
        engine.onDone(id)

        engine.onDismissed(id)

        assertTrue(scheduler.pending.isEmpty())
    }

    // --- Reboot / reschedule ---

    @Test
    fun rescheduleAll_restoresFutureAlarms() = test {
        val snoozed = dueTask()
        engine.onSnooze(snoozed)
        engine.createTestTask()
        val scheduled = repo.getActive().maxOf { it.id }
        scheduler.pending.clear() // a reboot drops all alarms

        engine.rescheduleAll()

        assertEquals(repo.require(scheduled).remindAt, scheduler.pending[scheduled to AlarmKind.TaskDue])
        assertEquals(repo.require(snoozed).remindAt, scheduler.pending[snoozed to AlarmKind.TaskDue])
        assertTrue(notifier.showing.isEmpty())
    }

    @Test
    fun rescheduleAll_firesOverdueTasksImmediately() = test {
        engine.createTestTask()
        scheduler.pending.clear()
        clock.advance(Duration.ofMinutes(5)) // phone was off when the task came due

        engine.rescheduleAll()

        assertEquals(TaskStatus.Due, repo.require(1).status)
        assertTrue(1L in notifier.showing)
        assertTrue(scheduler.pending.isEmpty())
    }

    @Test
    fun rescheduleAll_reshowsDueTasks() = test {
        val id = dueTask()
        notifier.showing.clear() // a reboot clears notifications

        engine.rescheduleAll()

        assertTrue(id in notifier.showing)
    }

    @Test
    fun rescheduleAll_ignoresResolvedTasks() = test {
        repo.insert(Task(title = "done", scheduledAt = start.plusSeconds(600), status = TaskStatus.Done))
        repo.insert(Task(title = "missed", scheduledAt = start.minusSeconds(600), status = TaskStatus.Missed))

        engine.rescheduleAll()

        assertTrue(scheduler.pending.isEmpty())
        assertTrue(notifier.showing.isEmpty())
    }
}
