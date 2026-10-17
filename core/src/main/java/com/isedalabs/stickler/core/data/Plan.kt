package com.isedalabs.stickler.core.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A time window that tasks are planned in (spec: Data model). In v1 each plan is one local day,
 * stored as the UTC window `[startDate, endDate)`; [title] is the ISO local date.
 */
@Entity(tableName = "plan", indices = [Index("startDate")])
data class Plan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startDate: Instant,
    val endDate: Instant,
    val createdAt: Instant,
)
