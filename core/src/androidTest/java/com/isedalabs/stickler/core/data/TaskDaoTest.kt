package com.isedalabs.stickler.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun observeBetween_includesStartAndExcludesEnd() = runBlocking {
        val start = Instant.parse("2026-10-16T00:00:00Z")
        val end = Instant.parse("2026-10-17T00:00:00Z")
        dao.insert(Task(title = "before", scheduledAt = start.minusMillis(1)))
        dao.insert(Task(title = "late", scheduledAt = end.minusMillis(1)))
        dao.insert(Task(title = "start", scheduledAt = start))
        dao.insert(Task(title = "end", scheduledAt = end))

        val titles = dao.observeBetween(start, end).first().map { it.title }

        assertEquals(listOf("start", "late"), titles)
    }

    @Test
    fun deletingTask_deletesItsEvents() = runBlocking {
        val at = Instant.parse("2026-10-16T09:00:00Z")
        val id = dao.insert(Task(title = "t", scheduledAt = at))
        val events = db.reminderEventDao()
        events.insert(ReminderEvent(taskId = id, type = ReminderEventType.Fired, at = at, device = Device.Phone))

        dao.delete(dao.getById(id)!!)

        assertNull(dao.getById(id))
        assertTrue(events.getForTask(id).isEmpty())
    }
}
