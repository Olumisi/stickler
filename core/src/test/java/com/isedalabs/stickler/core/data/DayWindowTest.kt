package com.isedalabs.stickler.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DayWindowTest {

    private val london = ZoneId.of("Europe/London")

    @Test
    fun normalDay_isTwentyFourHoursFromLocalMidnight() {
        val window = dayWindow(LocalDate.parse("2026-10-16"), london)

        assertEquals(Instant.parse("2026-10-15T23:00:00Z"), window.start) // BST, UTC+1
        assertEquals(Duration.ofHours(24), Duration.between(window.start, window.end))
    }

    @Test
    fun springForwardDay_isTwentyThreeHours() {
        val window = dayWindow(LocalDate.parse("2026-03-29"), london)

        assertEquals(Duration.ofHours(23), Duration.between(window.start, window.end))
    }

    @Test
    fun fallBackDay_isTwentyFiveHours() {
        val window = dayWindow(LocalDate.parse("2026-10-25"), london)

        assertEquals(Duration.ofHours(25), Duration.between(window.start, window.end))
    }

    @Test
    fun contains_isStartInclusiveEndExclusive() {
        val window = dayWindow(LocalDate.parse("2026-10-16"), london)

        assertTrue(window.start in window)
        assertTrue(window.end.minusMillis(1) in window)
        assertFalse(window.end in window)
        assertFalse(window.start.minusMillis(1) in window)
    }
}
