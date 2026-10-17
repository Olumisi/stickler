package com.isedalabs.stickler.core.di

import android.content.Context
import androidx.room.Room
import com.isedalabs.stickler.core.data.MIGRATION_1_2
import com.isedalabs.stickler.core.data.PlanDao
import com.isedalabs.stickler.core.data.PlanRepository
import com.isedalabs.stickler.core.data.ReminderEventDao
import com.isedalabs.stickler.core.data.RoomPlanRepository
import com.isedalabs.stickler.core.data.RoomSettingsRepository
import com.isedalabs.stickler.core.data.RoomTaskRepository
import com.isedalabs.stickler.core.data.SettingsDao
import com.isedalabs.stickler.core.data.SettingsRepository
import com.isedalabs.stickler.core.data.SticklerDatabase
import com.isedalabs.stickler.core.data.TaskDao
import com.isedalabs.stickler.core.data.TaskRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DatabaseModule {

    @Binds
    abstract fun bindTaskRepository(impl: RoomTaskRepository): TaskRepository

    @Binds
    abstract fun bindPlanRepository(impl: RoomPlanRepository): PlanRepository

    @Binds
    abstract fun bindSettingsRepository(impl: RoomSettingsRepository): SettingsRepository

    companion object {
        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context): SticklerDatabase =
            Room.databaseBuilder(context, SticklerDatabase::class.java, SticklerDatabase.NAME)
                .addMigrations(MIGRATION_1_2)
                .build()

        @Provides
        fun provideTaskDao(db: SticklerDatabase): TaskDao = db.taskDao()

        @Provides
        fun providePlanDao(db: SticklerDatabase): PlanDao = db.planDao()

        @Provides
        fun provideReminderEventDao(db: SticklerDatabase): ReminderEventDao = db.reminderEventDao()

        @Provides
        fun provideSettingsDao(db: SticklerDatabase): SettingsDao = db.settingsDao()
    }
}
