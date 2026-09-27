package com.isedalabs.stickler.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.util.Log
import com.isedalabs.stickler.core.reminder.AlarmKind
import com.isedalabs.stickler.core.reminder.AlarmScheduler
import com.isedalabs.stickler.core.reminder.ScheduleResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exact alarms when the user has granted `SCHEDULE_EXACT_ALARM`; otherwise an inexact
 * while-idle alarm, which the system may deliver late. The caller surfaces the warning.
 */
@Singleton
class AndroidAlarmScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AlarmScheduler {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean = alarmManager.canScheduleExactAlarms()

    override fun schedule(taskId: Long, at: Instant, kind: AlarmKind): ScheduleResult {
        val operation = requireNotNull(ReminderIntents.pendingIntent(context, kind.action, taskId))
        val triggerAt = at.toEpochMilli()
        if (canScheduleExact()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
                return ScheduleResult.Exact
            } catch (e: SecurityException) {
                // Permission revoked between the check and the call; fall through to inexact.
                Log.w(TAG, "Exact alarm refused, falling back to inexact", e)
            }
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        return ScheduleResult.Inexact
    }

    override fun cancel(taskId: Long, kind: AlarmKind) {
        val operation = ReminderIntents.pendingIntent(context, kind.action, taskId, PendingIntent.FLAG_NO_CREATE)
            ?: return
        alarmManager.cancel(operation)
        operation.cancel()
    }

    private val AlarmKind.action: String
        get() = when (this) {
            AlarmKind.TaskDue -> ReminderIntents.ACTION_TASK_DUE
            AlarmKind.Repost -> ReminderIntents.ACTION_REPOST
        }

    private companion object {
        const val TAG = "AlarmScheduler"
    }
}
