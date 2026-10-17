package com.isedalabs.stickler.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Plan::class, Task::class, ReminderEvent::class, Settings::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SticklerDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun planDao(): PlanDao
    abstract fun reminderEventDao(): ReminderEventDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        const val NAME = "stickler.db"
    }
}

/**
 * v1 -> v2: adds the plan, reminder_event and settings tables, and rebuilds task with its plan
 * foreign key (SQLite can't add one in place). Existing tasks keep their rows with planId null.
 * SQL matches schemas/.../2.json.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `plan` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                "`startDate` INTEGER NOT NULL, `endDate` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_plan_startDate` ON `plan` (`startDate`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `task_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `planId` INTEGER, " +
                "`title` TEXT NOT NULL, `note` TEXT, `scheduledAt` INTEGER NOT NULL, `durationMin` INTEGER, " +
                "`status` TEXT NOT NULL, `snoozeCount` INTEGER NOT NULL, `missedReason` TEXT, `completedAt` INTEGER, " +
                "`remindAt` INTEGER, FOREIGN KEY(`planId`) REFERENCES `plan`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        // No v1 task has a plan, so planId is carried over as null.
        db.execSQL(
            "INSERT INTO `task_new` (`id`, `planId`, `title`, `note`, `scheduledAt`, `durationMin`, `status`, " +
                "`snoozeCount`, `missedReason`, `completedAt`, `remindAt`) " +
                "SELECT `id`, NULL, `title`, `note`, `scheduledAt`, `durationMin`, `status`, " +
                "`snoozeCount`, `missedReason`, `completedAt`, `remindAt` FROM `task`",
        )
        db.execSQL("DROP TABLE `task`")
        db.execSQL("ALTER TABLE `task_new` RENAME TO `task`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_status` ON `task` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_planId` ON `task` (`planId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_scheduledAt` ON `task` (`scheduledAt`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `reminder_event` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`taskId` INTEGER NOT NULL, `type` TEXT NOT NULL, `at` INTEGER NOT NULL, `device` TEXT NOT NULL, " +
                "FOREIGN KEY(`taskId`) REFERENCES `task`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminder_event_taskId` ON `reminder_event` (`taskId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `settings` (`id` INTEGER NOT NULL, `realertIntervalMin` INTEGER NOT NULL, " +
                "`escalateAfter` INTEGER NOT NULL, `snoozeMin` INTEGER NOT NULL, `maxSnoozes` INTEGER NOT NULL, " +
                "`fullScreenEnabled` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
    }
}
