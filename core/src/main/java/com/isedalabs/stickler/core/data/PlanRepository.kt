package com.isedalabs.stickler.core.data

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Plans by local day; an interface so callers can be tested with a fake. */
interface PlanRepository {
    /** The plan covering local [date] in [zone], created if it doesn't exist yet. */
    suspend fun getOrCreateDayPlan(date: LocalDate, zone: ZoneId): Long
}

class RoomPlanRepository @Inject constructor(
    private val dao: PlanDao,
    private val clock: Clock,
) : PlanRepository {
    override suspend fun getOrCreateDayPlan(date: LocalDate, zone: ZoneId): Long {
        val window = dayWindow(date, zone)
        return dao.getOrCreate(
            Plan(title = date.toString(), startDate = window.start, endDate = window.end, createdAt = clock.instant()),
        )
    }
}
