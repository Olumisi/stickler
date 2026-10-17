package com.isedalabs.stickler.debug

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.isedalabs.stickler.R
import com.isedalabs.stickler.core.data.Task
import com.isedalabs.stickler.core.reminder.ScheduleResult
import com.isedalabs.stickler.core.reminder.TestTaskScheduled
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val TIME_FORMAT = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM)

@Composable
fun DebugScreen(viewModel: DebugViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val permissions by viewModel.permissions.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val lastScheduled by viewModel.lastScheduled.collectAsState()

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refreshPermissions() }

    // Android 13+: ask for notification permission when the screen first opens.
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !permissions.notifications) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.debug_title), style = MaterialTheme.typography.headlineSmall)

            PermissionRow(
                label = stringResource(R.string.debug_perm_notifications),
                granted = permissions.notifications,
                actionLabel = stringResource(R.string.debug_grant),
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    }
                },
            )
            PermissionRow(
                label = stringResource(R.string.debug_perm_exact_alarms),
                granted = permissions.exactAlarms,
                actionLabel = stringResource(R.string.debug_allow),
                onAction = {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()),
                    )
                },
            )
            if (!permissions.exactAlarms) {
                WarningBanner(stringResource(R.string.debug_exact_denied_warning))
            }
            if (!permissions.notifications) {
                WarningBanner(stringResource(R.string.debug_notifications_denied_warning))
            }

            Text(stringResource(R.string.debug_schedule_header), style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                viewModel.presets.forEach { delay ->
                    Button(onClick = { viewModel.scheduleTestTask(delay) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.debug_preset_minutes, delay.toMinutes()))
                    }
                }
            }
            lastScheduled?.let { LastScheduled(it) }

            HorizontalDivider()
            Text(stringResource(R.string.debug_tasks_header), style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tasks, key = { it.id }) { TaskRow(it) }
            }
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, actionLabel: String, onAction: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label: " + stringResource(if (granted) R.string.debug_granted else R.string.debug_denied),
            modifier = Modifier.weight(1f),
        )
        if (!granted) TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun WarningBanner(text: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LastScheduled(scheduled: TestTaskScheduled) {
    Column {
        Text(
            stringResource(
                R.string.debug_scheduled_at,
                scheduled.taskId,
                scheduled.at.atZone(ZoneId.systemDefault()).format(TIME_FORMAT),
            ),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (scheduled.result == ScheduleResult.Inexact) {
            Text(
                stringResource(R.string.debug_scheduled_inexact_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun TaskRow(task: Task) {
    val zone = ZoneId.systemDefault()
    Column {
        Text("#${task.id} ${task.title} · ${task.status}", style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(
                R.string.debug_task_detail,
                task.scheduledAt.atZone(zone).format(TIME_FORMAT),
                task.remindAt?.atZone(zone)?.format(TIME_FORMAT) ?: "–",
                task.snoozeCount,
            ),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
