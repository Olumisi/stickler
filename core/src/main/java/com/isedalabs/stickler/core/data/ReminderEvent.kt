package com.isedalabs.stickler.core.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/** One entry in a task's reminder history; drives escalation and the end-of-day review (Phase 3). */
@Entity(
    tableName = "reminder_event",
    foreignKeys = [
        ForeignKey(entity = Task::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("taskId")],
)
data class ReminderEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val type: ReminderEventType,
    val at: Instant,
    val device: Device,
)

enum class ReminderEventType {
    Fired, Realerted, Escalated, Snoozed, Dismissed, Reposted, Done, MarkedMissed, AutoMissed, Undone, DoneLate,
}

enum class Device { Phone, Watch }
