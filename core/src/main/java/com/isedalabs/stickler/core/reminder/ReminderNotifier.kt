package com.isedalabs.stickler.core.reminder

import com.isedalabs.stickler.core.data.Task

/** Shows and clears the reminder notification for a task; implemented in `:app`. */
interface ReminderNotifier {
    fun show(task: Task, canSnooze: Boolean)

    fun cancel(taskId: Long)
}
