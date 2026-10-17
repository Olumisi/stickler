package com.isedalabs.stickler.core.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A half-open time range `[start, end)`. */
data class TimeWindow(val start: Instant, val end: Instant) {
    operator fun contains(at: Instant): Boolean = !at.isBefore(start) && at.isBefore(end)
}

/** The UTC window covering local [date] in [zone]; 23 or 25 hours long on daylight-saving days. */
fun dayWindow(date: LocalDate, zone: ZoneId): TimeWindow =
    TimeWindow(date.atStartOfDay(zone).toInstant(), date.plusDays(1).atStartOfDay(zone).toInstant())
