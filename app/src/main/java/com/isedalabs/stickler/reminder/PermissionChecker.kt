package com.isedalabs.stickler.reminder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** What the reminder needs from the user; screens show a warning when either is missing. */
data class ReminderPermissions(val notifications: Boolean, val exactAlarms: Boolean)

class PermissionChecker @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val scheduler: AndroidAlarmScheduler,
) {
    fun read() = ReminderPermissions(
        notifications = notificationsAllowed(),
        exactAlarms = scheduler.canScheduleExact(),
    )

    private fun notificationsAllowed(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
