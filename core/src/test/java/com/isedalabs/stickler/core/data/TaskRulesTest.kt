package com.isedalabs.stickler.core.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** The editing rules (spec decision log, 2026-10-16) for every status, before and after the task's time. */
class TaskRulesTest {

    private val now = Instant.parse("2026-10-16T12:00:00Z")

    private data class Rules(val title: Boolean, val time: Boolean, val delete: Boolean)

    private fun rules(status: TaskStatus, scheduledAt: Instant): Rules {
        val task = Task(title = "t", scheduledAt = scheduledAt, status = status)
        return Rules(task.canEditTitle, task.canEditTime(now), task.canDelete(now))
    }

    @Test
    fun beforeItsTime() {
        val future = now.plusSeconds(60)
        assertEquals(Rules(title = true, time = true, delete = true), rules(TaskStatus.Scheduled, future))
        assertEquals(Rules(title = true, time = false, delete = false), rules(TaskStatus.Due, future))
        assertEquals(Rules(title = true, time = false, delete = false), rules(TaskStatus.Snoozed, future))
        assertEquals(Rules(title = false, time = false, delete = false), rules(TaskStatus.Done, future))
        assertEquals(Rules(title = false, time = false, delete = false), rules(TaskStatus.Missed, future))
    }

    @Test
    fun onceItsTimeHasCome() {
        for (at in listOf(now, now.minusSeconds(60))) {
            // Still Scheduled only because the (inexact) alarm is late: the time is locked anyway.
            assertEquals(Rules(title = true, time = false, delete = false), rules(TaskStatus.Scheduled, at))
            assertEquals(Rules(title = true, time = false, delete = false), rules(TaskStatus.Due, at))
            assertEquals(Rules(title = true, time = false, delete = false), rules(TaskStatus.Snoozed, at))
            assertEquals(Rules(title = false, time = false, delete = false), rules(TaskStatus.Done, at))
            assertEquals(Rules(title = false, time = false, delete = false), rules(TaskStatus.Missed, at))
        }
    }
}
