package com.isedalabs.stickler.core.reminder

import com.isedalabs.stickler.core.data.MissedReason
import com.isedalabs.stickler.core.data.Task
import com.isedalabs.stickler.core.data.TaskStatus
import kotlinx.coroutines.runBlocking
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
    private val engine = ReminderEngine(repo, scheduler, notifier, clock)

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
        repeat(ReminderPolicy.DEFAULT_MAX_SNOOZES) {
            assertEquals(true, notifier.showing[id]) // Snooze action offered
            assertTrue(engine.onSnooze(id))
            clock.advance(ReminderPolicy.SNOOZE)
            engine.onTaskDue(id)
        }

        assertEquals(false, notifier.showing[id]) // Snooze action no longer offered
        assertFalse(engine.onSnooze(id))
        assertEquals(TaskStatus.Due, repo.require(id).status)
        assertEquals(ReminderPolicy.DEFAULT_MAX_SNOOZES, repo.require(id).snoozeCount)
    }

    @Test
    fun snooze_whenNotDue_isRefused() = test {
        engine.createTestTask()

        assertFalse(engine.onSnooze(1))
        assertEquals(TaskStatus.Scheduled, repo.require(1).status)
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
