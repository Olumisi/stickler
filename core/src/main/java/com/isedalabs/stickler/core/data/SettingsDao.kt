package com.isedalabs.stickler.core.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = ${Settings.ID}")
    suspend fun get(): Settings?

    @Upsert
    suspend fun upsert(settings: Settings)
}
