package com.isedalabs.stickler.core.di

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

class CoreModuleTest {

    @Test
    fun providedClock_isUtc() {
        assertEquals(ZoneOffset.UTC, CoreModule.provideClock().zone)
    }
}
