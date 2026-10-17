package com.isedalabs.stickler.core.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A planned task (spec: Data model). All times are UTC instants.
 *
 * [planId] is null only for debug test tasks, which don't belong to a plan.
 * [remindAt] is when the next alarm for this task should fire (first fire, or the end of a
 * snooze); null once the task is Due or resolved. The boot receiver reschedules from it.
 */
@Entity(
    tableName = "task",
    foreignKeys = [
        ForeignKey(entity = Plan::class, parentColumns = ["id"], childColumns = ["planId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("status"), Index("planId"), Index("scheduledAt")],
)
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

/** The title can be fixed while the task is still active, even once it's due. */
val Task.canEditTitle: Boolean get() = status.isActive

/** The time is locked once the task is due, so editing can't bypass the snooze limit. */
val Task.canEditTime: Boolean get() = status == TaskStatus.Scheduled

/** Only a task that hasn't come due yet can be deleted; a due task is resolved with Done or Missed. */
val Task.canDelete: Boolean get() = status == TaskStatus.Scheduled

enum class TaskStatus {
    Scheduled, Due, Snoozed, Done, Missed;

    val isActive: Boolean get() = this == Scheduled || this == Due || this == Snoozed
}

enum class MissedReason { UserMarked, NextTaskDue, PlanEnded }
