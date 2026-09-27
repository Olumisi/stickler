package com.isedalabs.stickler.reminder

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.isedalabs.stickler.core.reminder.ReminderEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Alarms are cleared on reboot and app update, and a newly granted exact-alarm permission should
 * upgrade inexact alarms; in each case restore alarms and due notifications from the database.
 */
@AndroidEntryPoint
class SystemEventsReceiver : BroadcastReceiver() {

    @Inject lateinit var engine: ReminderEngine

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        goAsync { engine.rescheduleAll() }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
