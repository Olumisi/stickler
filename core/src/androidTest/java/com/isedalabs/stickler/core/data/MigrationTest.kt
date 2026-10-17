package com.isedalabs.stickler.core.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SticklerDatabase::class.java,
    )

    @Test
    fun migrate1To2_keepsTasksWithoutPlan() = runBlocking {
        val at = Instant.parse("2026-09-27T14:05:00Z")
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO task (id, planId, title, note, scheduledAt, durationMin, status, snoozeCount, " +
                    "missedReason, completedAt, remindAt) VALUES (7, NULL, 'Test task', NULL, ${at.toEpochMilli()}, " +
                    "NULL, 'Snoozed', 1, NULL, NULL, ${at.toEpochMilli()})",
            )
        }

        // Checks the migrated schema against schemas/.../2.json.
        helper.runMigrationsAndValidate(DB, 2, true, MIGRATION_1_2).close()

        val room = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SticklerDatabase::class.java,
            DB,
        ).addMigrations(MIGRATION_1_2).build()
        try {
            val task = room.taskDao().getById(7)!!
            assertEquals("Test task", task.title)
            assertEquals(TaskStatus.Snoozed, task.status)
            assertEquals(1, task.snoozeCount)
            assertEquals(at, task.remindAt)
            assertNull(task.planId)
            assertEquals(Settings(), RoomSettingsRepository(room.settingsDao()).get())
        } finally {
            room.close()
        }
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
