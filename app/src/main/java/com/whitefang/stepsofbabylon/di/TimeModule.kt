package com.whitefang.stepsofbabylon.di

import com.whitefang.stepsofbabylon.data.anticheat.AntiCheatPreferences
import com.whitefang.stepsofbabylon.data.time.SystemTimeProvider
import com.whitefang.stepsofbabylon.domain.time.TimeBaselineSource
import com.whitefang.stepsofbabylon.domain.time.TimeProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds [TimeProvider] to its production implementation.
 *
 * Tests construct ViewModels / use cases manually with `FakeTimeProvider`
 * and do not go through Hilt, so no test-specific override module is needed.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TimeModule {
    @Binds @Singleton
    abstract fun bindTimeProvider(impl: SystemTimeProvider): TimeProvider

    @Binds @Singleton
    abstract fun bindTimeBaselineSource(impl: AntiCheatPreferences): TimeBaselineSource
}
