package com.isedalabs.stickler.di

import com.isedalabs.stickler.core.reminder.AlarmScheduler
import com.isedalabs.stickler.core.reminder.ReminderNotifier
import com.isedalabs.stickler.reminder.AndroidAlarmScheduler
import com.isedalabs.stickler.reminder.AndroidReminderNotifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Phone implementations of the `:core` reminder platform interfaces. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderModule {

    @Binds
    abstract fun bindAlarmScheduler(impl: AndroidAlarmScheduler): AlarmScheduler

    @Binds
    abstract fun bindReminderNotifier(impl: AndroidReminderNotifier): ReminderNotifier
}
