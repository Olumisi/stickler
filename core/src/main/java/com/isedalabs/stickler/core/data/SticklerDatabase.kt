package com.isedalabs.stickler.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [Task::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class SticklerDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        const val NAME = "stickler.db"
    }
}
