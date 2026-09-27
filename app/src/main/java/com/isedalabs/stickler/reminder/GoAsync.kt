package com.isedalabs.stickler.reminder

import android.content.BroadcastReceiver
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** Runs short database work off the main thread, keeping the broadcast alive until it finishes. */
fun BroadcastReceiver.goAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try {
            block()
        } catch (e: Exception) {
            Log.e("ReminderReceiver", "Reminder event failed", e)
        } finally {
            pending.finish()
        }
    }
}
