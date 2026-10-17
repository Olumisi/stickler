package com.isedalabs.stickler.core.reminder

import java.time.Duration

/** Reminder timings (spec: Reminder behaviour design). Becomes user settings in Phase 3. */
object ReminderPolicy {
    val SNOOZE: Duration = Duration.ofMinutes(10)
    val REPOST_DELAY: Duration = Duration.ofSeconds(60)

    /** Phase 1 debug screen: how far ahead the test task is scheduled. */
    val TEST_TASK_DELAY: Duration = Duration.ofMinutes(1)
}
