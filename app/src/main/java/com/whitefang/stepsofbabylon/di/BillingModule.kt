package com.whitefang.stepsofbabylon.di

import com.whitefang.stepsofbabylon.BuildConfig
import com.whitefang.stepsofbabylon.data.billing.BillingManagerImpl
import com.whitefang.stepsofbabylon.data.billing.StubBillingManager
import com.whitefang.stepsofbabylon.data.billing.internal.BillingClientAdapter
import com.whitefang.stepsofbabylon.data.billing.internal.RealBillingClientAdapter
import com.whitefang.stepsofbabylon.domain.repository.BillingManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Hilt wiring for [BillingManager]. C.5 PR 2 introduced the
 * [BuildConfig.USE_REAL_BILLING] flag and the runtime switch between
 * [StubBillingManager] (debug + any build type without a Play Store account)
 * and [BillingManagerImpl] (release, real Play Billing v8).
 *
 * **Why `@Provides` + [Provider] instead of `@Binds`.** Hilt's `@Binds` is
 * resolved at compile time and cannot branch on a runtime value like
 * `BuildConfig.USE_REAL_BILLING`. Two `@Provides` methods marked with the same
 * return type would collide. Injecting both candidates as [Provider]s defers
 * construction — whichever branch is not selected is never instantiated, so the
 * stub's `PlayerRepository` observer never attaches in a release build and the
 * real impl's Play Billing client never starts in a debug build.
 *
 * **Why `internal`.** [BillingManagerImpl] and [RealBillingClientAdapter] are
 * `internal` (they expose internal adapter types through their constructors), so
 * a `public` provider method cannot legally reference them. Making the whole
 * module `internal` is simpler than annotating every member and matches Hilt's
 * "single Gradle module" assumption for this app.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object BillingModule {

    @Provides
    @Singleton
    fun provideBillingManager(
        stub: Provider<StubBillingManager>,
        real: Provider<BillingManagerImpl>,
    ): BillingManager = if (BuildConfig.USE_REAL_BILLING) real.get() else stub.get()
}

/**
 * Internal binding for the SDK adapter layer. Separate module from [BillingModule]
 * because [BillingModule] is an `object` (for `@Provides`) and this uses `@Binds`
 * (which requires an abstract class). Both are `internal` so they can reference
 * [RealBillingClientAdapter], which is itself `internal`.
 *
 * Flow:
 * - The `@Provides provideBillingManager` method in [BillingModule] asks Hilt for
 *   `Provider<BillingManagerImpl>` even in debug builds (where the Provider is
 *   never invoked). Dagger therefore needs a route from
 *   `BillingManagerImpl.adapter: BillingClientAdapter` to a concrete type, which
 *   this binding supplies.
 * - Unit tests mock [BillingClientAdapter] directly and construct
 *   [BillingManagerImpl] by hand — this module is not consulted there.
 *
 * C.5 PR 2 / ADR-0005.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class BillingInternalModule {

    @Binds
    @Singleton
    abstract fun bindBillingClientAdapter(impl: RealBillingClientAdapter): BillingClientAdapter
}
