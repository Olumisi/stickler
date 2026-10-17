package com.isedalabs.stickler.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class PlanAndSettingsTest {

    private lateinit var db: SticklerDatabase
    private val clock = Clock.fixed(Instant.parse("2026-10-16T08:00:00Z"), ZoneOffset.UTC)
    private val zone = ZoneId.of("Europe/London")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SticklerDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun getOrCreateDayPlan_createsOncePerDay() = runBlocking {
        val plans = RoomPlanRepository(db.planDao(), clock)
        val today = LocalDate.parse("2026-10-16")

        val first = plans.getOrCreateDayPlan(today, zone)
        val again = plans.getOrCreateDayPlan(today, zone)
        val tomorrow = plans.getOrCreateDayPlan(today.plusDays(1), zone)

        assertEquals(first, again)
        assertNotEquals(first, tomorrow)
        val plan = db.planDao().getById(first)!!
        assertEquals("2026-10-16", plan.title)
        assertEquals(dayWindow(today, zone), TimeWindow(plan.startDate, plan.endDate))
    }

    @Test
    fun deletingPlan_deletesItsTasks() = runBlocking {
        val plans = RoomPlanRepository(db.planDao(), clock)
        val planId = plans.getOrCreateDayPlan(LocalDate.parse("2026-10-16"), zone)
        val taskId = db.taskDao().insert(Task(planId = planId, title = "t", scheduledAt = clock.instant()))

        db.openHelper.writableDatabase.execSQL("DELETE FROM `plan` WHERE id = $planId")

        assertEquals(null, db.taskDao().getById(taskId))
    }

    @Test
    fun settings_defaultWhenMissing_storedValueOtherwise() = runBlocking {
        val settings = RoomSettingsRepository(db.settingsDao())

        assertEquals(Settings(), settings.get())

        db.settingsDao().upsert(Settings(maxSnoozes = 4))

        assertEquals(4, settings.get().maxSnoozes)
    }
}
