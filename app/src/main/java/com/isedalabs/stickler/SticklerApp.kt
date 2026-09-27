package com.isedalabs.stickler

import android.app.Application
import com.isedalabs.stickler.reminder.AndroidReminderNotifier
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SticklerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidReminderNotifier.createChannel(this)
    }
}
