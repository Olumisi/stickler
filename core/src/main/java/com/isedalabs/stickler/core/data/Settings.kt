package com.isedalabs.stickler.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * App-wide reminder settings (spec: Data model). A single row with [id] = [ID]; when the row
 * hasn't been written yet the defaults apply.
 */
@Entity(tableName = "settings")
data class Settings(
    @PrimaryKey val id: Int = ID,
    val realertIntervalMin: Int = 5,
    val escalateAfter: Int = 3,
    val snoozeMin: Int = 10,
    val maxSnoozes: Int = 2,
    val fullScreenEnabled: Boolean = false,
) {
    companion object {
        const val ID = 1
    }
}
