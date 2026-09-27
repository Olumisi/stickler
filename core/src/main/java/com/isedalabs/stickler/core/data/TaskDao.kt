package com.isedalabs.stickler.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Insert
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Query("SELECT * FROM task WHERE id = :id")
    suspend fun getById(id: Long): Task?

    @Query("SELECT * FROM task ORDER BY scheduledAt DESC, id DESC")
    fun observeAll(): Flow<List<Task>>

    @Query("SELECT * FROM task WHERE status IN ('Scheduled', 'Due', 'Snoozed') ORDER BY scheduledAt")
    suspend fun getActive(): List<Task>
}
