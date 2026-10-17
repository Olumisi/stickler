package com.isedalabs.stickler.core.data

import javax.inject.Inject

/** App-wide settings; an interface so the reminder engine can be tested with a fake. */
interface SettingsRepository {
    /** The stored settings, or the defaults if none have been saved. */
    suspend fun get(): Settings
}

class RoomSettingsRepository @Inject constructor(private val dao: SettingsDao) : SettingsRepository {
    override suspend fun get(): Settings = dao.get() ?: Settings()
}
