package com.isedalabs.stickler.core.di

import android.content.Context
import androidx.room.Room
import com.isedalabs.stickler.core.data.RoomTaskRepository
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

    companion object {
        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context): SticklerDatabase =
            Room.databaseBuilder(context, SticklerDatabase::class.java, SticklerDatabase.NAME).build()

        @Provides
        fun provideTaskDao(db: SticklerDatabase): TaskDao = db.taskDao()
    }
}
