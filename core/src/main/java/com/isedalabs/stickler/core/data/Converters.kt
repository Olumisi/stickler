package com.isedalabs.stickler.core.data

import androidx.room.TypeConverter
import java.time.Instant

/** Instants are stored as UTC epoch millis. Enums use Room's built-in name mapping. */
class Converters {
    @TypeConverter
    fun instantToMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun millisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
}
