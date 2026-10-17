package com.isedalabs.stickler.today

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.isedalabs.stickler.R
import com.isedalabs.stickler.core.data.Task
import com.isedalabs.stickler.core.data.TaskStatus
import com.isedalabs.stickler.core.data.canEditTitle
import com.isedalabs.stickler.ui.PermissionSection
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val DATE_FORMAT = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
private val TIME_FORMAT = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(viewModel: TodayViewModel, onOpenDebug: () -> Unit, modifier: Modifier = Modifier) {
    val day by viewModel.day.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val permissions by viewModel.permissions.collectAsState()
    val editor by viewModel.editor.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    val messageText = mapOf(
        EditorMessage.ScheduledInexact to stringResource(R.string.today_scheduled_inexact),
        EditorMessage.TimeInPast to stringResource(R.string.today_time_in_past),
        EditorMessage.TimeLocked to stringResource(R.string.today_time_locked),
        EditorMessage.NotFound to stringResource(R.string.today_task_gone),
    )
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(messageText.getValue(it)) }
    }

    // Follow midnight, clock changes and time-zone changes while the screen is showing.
    val context = LocalContext.current
    DisposableEffect(context, viewModel) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = viewModel.refreshDay()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        // System broadcasts are still delivered to a non-exported receiver.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.today_title))
                        Text(day.date.format(DATE_FORMAT), style = MaterialTheme.typography.bodyMedium)
                    }
                },
                actions = { TextButton(onClick = onOpenDebug) { Text(stringResource(R.string.today_debug)) } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = viewModel::openNew) { Text(stringResource(R.string.today_add_task)) }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            // Bottom padding keeps the last row clear of the FAB.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                PermissionSection(
                    permissions = permissions,
                    onPermissionResult = viewModel::refreshPermissions,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            if (tasks.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.today_empty),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(tasks, key = { it.id }) { task ->
                TaskRow(task, day.zone, onClick = { viewModel.openEdit(task) })
                HorizontalDivider()
            }
        }
    }

    editor?.let {
        TaskEditorSheet(
            editor = it,
            onTitleChange = viewModel::setTitle,
            onTimeChange = viewModel::setTime,
            onSave = viewModel::save,
            onDelete = viewModel::delete,
            onDismiss = viewModel::closeEditor,
        )
    }
}

@Composable
private fun TaskRow(task: Task, zone: ZoneId, onClick: () -> Unit) {
    ListItem(
        modifier = if (task.canEditTitle) Modifier.clickable(onClick = onClick) else Modifier,
        leadingContent = {
            Text(task.scheduledAt.atZone(zone).format(TIME_FORMAT), style = MaterialTheme.typography.titleMedium)
        },
        headlineContent = { Text(task.title) },
        supportingContent = { Text(stringResource(task.status.label)) },
    )
}

private val TaskStatus.label: Int
    get() = when (this) {
        TaskStatus.Scheduled -> R.string.status_scheduled
        TaskStatus.Due -> R.string.status_due
        TaskStatus.Snoozed -> R.string.status_snoozed
        TaskStatus.Done -> R.string.status_done
        TaskStatus.Missed -> R.string.status_missed
    }
