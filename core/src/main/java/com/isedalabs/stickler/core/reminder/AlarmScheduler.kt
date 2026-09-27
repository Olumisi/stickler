package com.isedalabs.stickler.core.reminder

import java.time.Instant

/** Platform alarm scheduling; implemented over AlarmManager in `:app`. */
interface AlarmScheduler {
    /** Schedules (or replaces) the [kind] alarm for [taskId]. Falls back to inexact if exact isn't allowed. */
    fun schedule(taskId: Long, at: Instant, kind: AlarmKind): ScheduleResult

    fun cancel(taskId: Long, kind: AlarmKind)
}

enum class AlarmKind {
    /** The task's time (first fire, or the end of a snooze). */
    TaskDue,

    /** Re-post after the user swiped the notification away. */
    Repost,
}

enum class ScheduleResult { Exact, Inexact }
