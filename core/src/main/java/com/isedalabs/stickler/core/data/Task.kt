package com.isedalabs.stickler.core.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A planned task (spec: Data model). All times are UTC instants.
 *
 * [planId] is nullable until the `Plan` table arrives in Phase 2.
 * [remindAt] is when the next alarm for this task should fire (first fire, or the end of a
 * snooze); null once the task is Due or resolved. The boot receiver reschedules from it.
 */
@Entity(tableName = "task", indices = [Index("status")])
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long? = null,
    val title: String,
    val note: String? = null,
    val scheduledAt: Instant,
    val durationMin: Int? = null,
    val status: TaskStatus = TaskStatus.Scheduled,
    val snoozeCount: Int = 0,
    val missedReason: MissedReason? = null,
    val completedAt: Instant? = null,
    val remindAt: Instant? = null,
)

enum class TaskStatus {
    Scheduled, Due, Snoozed, Done, Missed;

    val isActive: Boolean get() = this == Scheduled || this == Due || this == Snoozed
}

enum class MissedReason { UserMarked, NextTaskDue, PlanEnded }
