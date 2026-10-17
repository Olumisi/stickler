package com.isedalabs.stickler.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ReminderEventDao {
    @Insert
    suspend fun insert(event: ReminderEvent): Long

    @Query("SELECT * FROM reminder_event WHERE taskId = :taskId ORDER BY at, id")
    suspend fun getForTask(taskId: Long): List<ReminderEvent>
}
