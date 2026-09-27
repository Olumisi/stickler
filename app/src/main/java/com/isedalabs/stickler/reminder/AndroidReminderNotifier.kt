package com.isedalabs.stickler.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.isedalabs.stickler.MainActivity
import com.isedalabs.stickler.R
import com.isedalabs.stickler.core.data.Task
import com.isedalabs.stickler.core.reminder.ReminderNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The ongoing reminder notification. From Android 14 the user can still swipe it away while
 * unlocked; the delete intent reports that so the engine can re-post it.
 */
@Singleton
class AndroidReminderNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ReminderNotifier {

    private val manager = NotificationManagerCompat.from(context)

    override fun show(task: Task, canSnooze: Boolean) {
        if (!canPost()) {
            Log.w(TAG, "Notifications not allowed; task ${task.id} is due but can't be shown")
            return
        }
        val dueAt = task.scheduledAt.atZone(ZoneId.systemDefault()).format(TIME_FORMAT)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(task.title)
            .setContentText(context.getString(R.string.reminder_due_at, dueAt))
            .setWhen(task.scheduledAt.toEpochMilli())
            .setShowWhen(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppIntent())
            .setDeleteIntent(action(ReminderIntents.ACTION_DISMISSED, task.id))
            .addAction(0, context.getString(R.string.reminder_action_done), action(ReminderIntents.ACTION_DONE, task.id))
            .addAction(0, context.getString(R.string.reminder_action_missed), action(ReminderIntents.ACTION_MISSED, task.id))
        if (canSnooze) {
            builder.addAction(0, context.getString(R.string.reminder_action_snooze), action(ReminderIntents.ACTION_SNOOZE, task.id))
        }
        try {
            manager.notify(task.id.notificationId, builder.build())
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission revoked", e)
        }
    }

    override fun cancel(taskId: Long) = manager.cancel(taskId.notificationId)

    private fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return manager.areNotificationsEnabled()
    }

    private fun action(action: String, taskId: Long) =
        ReminderIntents.pendingIntent(context, action, taskId)

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private val Long.notificationId: Int get() = toInt()

    companion object {
        private const val TAG = "ReminderNotifier"
        const val CHANNEL_ID = "reminders"
        private val TIME_FORMAT = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

        fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_reminders_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.channel_reminders_description)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
