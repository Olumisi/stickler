package com.isedalabs.stickler.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.isedalabs.stickler.core.reminder.ReminderEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Alarms (task due, repost) and notification actions (Done, Missed, Snooze, swipe-away). */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var engine: ReminderEngine

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = ReminderIntents.taskId(intent) ?: return
        goAsync {
            when (intent.action) {
                ReminderIntents.ACTION_TASK_DUE -> engine.onTaskDue(taskId)
                ReminderIntents.ACTION_REPOST -> engine.onRepost(taskId)
                ReminderIntents.ACTION_DONE -> engine.onDone(taskId)
                ReminderIntents.ACTION_MISSED -> engine.onMissed(taskId)
                ReminderIntents.ACTION_SNOOZE -> engine.onSnooze(taskId)
                ReminderIntents.ACTION_DISMISSED -> engine.onDismissed(taskId)
            }
        }
    }
}
