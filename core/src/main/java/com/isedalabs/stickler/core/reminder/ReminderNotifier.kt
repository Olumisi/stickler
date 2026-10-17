package com.isedalabs.stickler.core.reminder

import com.isedalabs.stickler.core.data.Task

/** Shows and clears the reminder notification for a task; implemented in `:app`. */
interface ReminderNotifier {
    /** Posts or updates the reminder; [silent] updates it without sound, vibration or heads-up. */
    fun show(task: Task, canSnooze: Boolean, silent: Boolean = false)

    fun cancel(taskId: Long)
}
