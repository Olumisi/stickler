package com.isedalabs.stickler.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import java.time.Instant

@Dao
interface PlanDao {
    @Insert
    suspend fun insert(plan: Plan): Long

    @Query("SELECT * FROM `plan` WHERE id = :id")
    suspend fun getById(id: Long): Plan?

    @Query("SELECT * FROM `plan` WHERE startDate = :start AND endDate = :end LIMIT 1")
    suspend fun findByWindow(start: Instant, end: Instant): Plan?

    @Transaction
    suspend fun getOrCreate(plan: Plan): Long =
        findByWindow(plan.startDate, plan.endDate)?.id ?: insert(plan)
}
