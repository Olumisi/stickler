package com.isedalabs.stickler.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class TaskDaoTest {

    private lateinit var db: SticklerDatabase
    private lateinit var dao: TaskDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SticklerDatabase::class.java,
        ).build()
        dao = db.taskDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun roundTripsInstantsAndEnums() = runBlocking {
        val at = Instant.parse("2026-09-27T14:05:00.123Z")
        val id = dao.insert(
            Task(
                title = "t", scheduledAt = at, status = TaskStatus.Missed,
                missedReason = MissedReason.UserMarked, remindAt = at, completedAt = at,
            ),
        )
        val loaded = dao.getById(id)!!
        assertEquals(at, loaded.scheduledAt)
        assertEquals(at, loaded.remindAt)
        assertEquals(TaskStatus.Missed, loaded.status)
        assertEquals(MissedReason.UserMarked, loaded.missedReason)
    }

    @Test
    fun getActive_returnsOnlyScheduledDueAndSnoozed() = runBlocking {
        val at = Instant.parse("2026-09-27T14:00:00Z")
        TaskStatus.entries.forEach { dao.insert(Task(title = it.name, scheduledAt = at, status = it)) }

        val active = dao.getActive().map { it.status }.toSet()

        assertEquals(setOf(TaskStatus.Scheduled, TaskStatus.Due, TaskStatus.Snoozed), active)
    }
}
