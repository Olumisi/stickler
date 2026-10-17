package com.isedalabs.stickler.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.isedalabs.stickler.R
import com.isedalabs.stickler.reminder.ReminderPermissions

/**
 * Permission status with grant buttons, plus a warning for each missing permission. Reminders
 * still work without them, just degraded (late, or not shown), so this never blocks the screen.
 *
 * @param showStatusRows also list granted permissions (debug screen); otherwise only warnings show.
 */
@Composable
fun PermissionSection(
    permissions: ReminderPermissions,
    onPermissionResult: () -> Unit,
    modifier: Modifier = Modifier,
    showStatusRows: Boolean = false,
) {
    val context = LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { onPermissionResult() }

    // Android 13+: ask for notification permission when the screen first opens.
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !permissions.notifications) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val requestNotifications = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
    }
    val requestExactAlarms = {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()),
        )
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showStatusRows) {
            PermissionRow(
                label = stringResource(R.string.perm_notifications),
                granted = permissions.notifications,
                actionLabel = stringResource(R.string.perm_grant),
                onAction = requestNotifications,
            )
            PermissionRow(
                label = stringResource(R.string.perm_exact_alarms),
                granted = permissions.exactAlarms,
                actionLabel = stringResource(R.string.perm_allow),
                onAction = requestExactAlarms,
            )
        }
        if (!permissions.exactAlarms) {
            WarningBanner(
                text = stringResource(R.string.perm_exact_denied_warning),
                actionLabel = stringResource(R.string.perm_allow).takeUnless { showStatusRows },
                onAction = requestExactAlarms,
            )
        }
        if (!permissions.notifications) {
            WarningBanner(
                text = stringResource(R.string.perm_notifications_denied_warning),
                actionLabel = stringResource(R.string.perm_grant).takeUnless { showStatusRows },
                onAction = requestNotifications,
            )
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, actionLabel: String, onAction: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label: " + stringResource(if (granted) R.string.perm_granted else R.string.perm_denied),
            modifier = Modifier.weight(1f),
        )
        if (!granted) TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun WarningBanner(text: String, actionLabel: String?, onAction: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text,
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (actionLabel != null) TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
