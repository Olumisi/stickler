package com.isedalabs.stickler.today

import com.isedalabs.stickler.core.reminder.EditResult
import com.isedalabs.stickler.core.reminder.ScheduleResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class TaskEditorReducerTest {

    private val editor = TaskEditor(
        taskId = 7,
        date = LocalDate.parse("2026-10-16"),
        title = "Write report (typed)",
        time = LocalTime.of(14, 30),
        canEditTime = true,
        canDelete = true,
    )

    @Test
    fun saved_closesEditor() {
        assertEquals(EditorOutcome(null, null), editor.after(EditResult.Saved(7, ScheduleResult.Exact)))
        assertEquals(EditorOutcome(null, null), editor.after(EditResult.Saved(7, null)))
    }

    @Test
    fun savedInexact_closesEditorWithWarning() {
        assertEquals(
            EditorOutcome(null, EditorMessage.ScheduledInexact),
            editor.after(EditResult.Saved(7, ScheduleResult.Inexact)),
        )
    }

    @Test
    fun timeInPast_keepsEditorOpenUnchanged() {
        assertEquals(EditorOutcome(editor, EditorMessage.TimeInPast), editor.after(EditResult.TimeInPast))
    }

    @Test
    fun notAllowed_taskCameDueWhileOpen_locksTimeAndDeleteButKeepsTitle() {
        val outcome = editor.after(EditResult.NotAllowed)

        assertEquals(EditorMessage.TimeLocked, outcome.message)
        val locked = outcome.editor!!
        assertEquals(false, locked.canEditTime)
        assertEquals(false, locked.canDelete)
        assertEquals("Write report (typed)", locked.title)
        assertEquals(true, locked.canSave) // a title-only save is still possible
    }

    @Test
    fun notFound_closesEditorWithMessage() {
        assertEquals(EditorOutcome(null, EditorMessage.NotFound), editor.after(EditResult.NotFound))
    }

    @Test
    fun delete_closesEditor_orLocksItWhenRefused() {
        assertEquals(EditorOutcome(null, null), editor.afterDelete(deleted = true))

        val refused = editor.afterDelete(deleted = false)
        assertEquals(EditorMessage.TimeLocked, refused.message)
        assertEquals(false, refused.editor!!.canDelete)
        assertEquals(false, refused.editor!!.canEditTime)
    }

    // --- Default time for a new task ---

    private val zone = ZoneId.of("Europe/London")

    private fun at(time: String) = ZonedDateTime.of(LocalDate.parse("2026-10-16"), LocalTime.parse(time), zone)

    @Test
    fun defaultTime_isNextQuarterHour() {
        assertEquals(LocalTime.of(14, 15), defaultNewTaskTime(at("14:01:30")))
        assertEquals(LocalTime.of(14, 30), defaultNewTaskTime(at("14:15:00")))
        assertEquals(LocalTime.of(15, 0), defaultNewTaskTime(at("14:59:59")))
    }

    @Test
    fun defaultTime_nearMidnight_staysOnTheSameDay() {
        assertEquals(LocalTime.of(23, 45), defaultNewTaskTime(at("23:30:10")))
        assertEquals(LocalTime.of(23, 59), defaultNewTaskTime(at("23:46:00")))
        assertEquals(LocalTime.of(23, 59), defaultNewTaskTime(at("23:58:30")))
    }

    @Test
    fun outcome_editorNullMeansClosed() {
        assertNull(editor.after(EditResult.Saved(7, null)).editor)
    }
}
