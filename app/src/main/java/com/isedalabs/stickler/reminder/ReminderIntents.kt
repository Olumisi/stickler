package com.isedalabs.stickler.reminder

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/** Intents sent to [ReminderReceiver] by alarms and notification actions. */
object ReminderIntents {
    const val ACTION_TASK_DUE = "com.isedalabs.stickler.action.TASK_DUE"
    const val ACTION_REPOST = "com.isedalabs.stickler.action.REPOST"
    const val ACTION_DONE = "com.isedalabs.stickler.action.DONE"
    const val ACTION_MISSED = "com.isedalabs.stickler.action.MISSED"
    const val ACTION_SNOOZE = "com.isedalabs.stickler.action.SNOOZE"
    const val ACTION_DISMISSED = "com.isedalabs.stickler.action.DISMISSED"

    private const val EXTRA_TASK_ID = "taskId"

    /**
     * A broadcast PendingIntent for [action] on [taskId]. The data URI makes each (task, action)
     * pair a distinct PendingIntent, so alarms and actions for different tasks never replace each other.
     */
    fun pendingIntent(context: Context, action: String, taskId: Long, flags: Int = 0): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            0,
            intent(context, action, taskId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT or flags,
        )

    fun taskId(intent: Intent): Long? =
        intent.getLongExtra(EXTRA_TASK_ID, -1L).takeIf { it >= 0 }

    private fun intent(context: Context, action: String, taskId: Long) =
        Intent(context, ReminderReceiver::class.java)
            .setAction(action)
            .setData("stickler://task/$taskId/${action.substringAfterLast('.')}".toUri())
            .putExtra(EXTRA_TASK_ID, taskId)
}
