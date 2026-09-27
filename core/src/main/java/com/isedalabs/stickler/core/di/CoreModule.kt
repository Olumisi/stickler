package com.isedalabs.stickler.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/**
 * App-wide bindings owned by `:core`.
 *
 * Times are stored in UTC (see CLAUDE.md), so the shared [Clock] is UTC; convert to the
 * device zone only for display. Injecting it lets reminder logic be tested with a fixed clock.
 */
@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()
}
