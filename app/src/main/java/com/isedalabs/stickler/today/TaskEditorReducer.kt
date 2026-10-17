package com.isedalabs.stickler.today

import com.isedalabs.stickler.core.reminder.EditResult
import com.isedalabs.stickler.core.reminder.ScheduleResult
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** Snackbar messages from the Today screen's editor. */
enum class EditorMessage { ScheduledInexact, TimeInPast, TimeLocked, NotFound }

/** What the editor shows after a save or delete: [editor] null means the sheet closes. */
data class EditorOutcome(val editor: TaskEditor?, val message: EditorMessage?)

/** The editor state after the engine answered a save. */
fun TaskEditor.after(result: EditResult): EditorOutcome = when (result) {
    is EditResult.Saved -> EditorOutcome(
        editor = null,
        message = if (result.schedule == ScheduleResult.Inexact) EditorMessage.ScheduledInexact else null,
    )
    EditResult.TimeInPast -> EditorOutcome(this, EditorMessage.TimeInPast)
    // The task's time came while the sheet was open: lock the time, keep the typed title.
    EditResult.NotAllowed -> EditorOutcome(copy(canEditTime = false, canDelete = false), EditorMessage.TimeLocked)
    EditResult.NotFound -> EditorOutcome(null, EditorMessage.NotFound)
}

/** The editor state after the engine answered a delete. */
fun TaskEditor.afterDelete(deleted: Boolean): EditorOutcome =
    if (deleted) {
        EditorOutcome(null, null)
    } else {
        EditorOutcome(copy(canEditTime = false, canDelete = false), EditorMessage.TimeLocked)
    }

/**
 * Default time for a new task: the next whole quarter hour, capped at 23:59 so it stays on the
 * same day (quick add is today only).
 */
fun defaultNewTaskTime(now: ZonedDateTime): LocalTime {
    val minute = now.truncatedTo(ChronoUnit.MINUTES)
    val next = minute.plusMinutes(15L - minute.minute % 15)
    return if (next.toLocalDate() == now.toLocalDate()) next.toLocalTime() else LocalTime.of(23, 59)
}
