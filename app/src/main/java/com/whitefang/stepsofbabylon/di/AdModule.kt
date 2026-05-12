package com.whitefang.stepsofbabylon.di

import com.whitefang.stepsofbabylon.BuildConfig
import com.whitefang.stepsofbabylon.data.ads.RewardAdManagerImpl
import com.whitefang.stepsofbabylon.data.ads.StubRewardAdManager
import com.whitefang.stepsofbabylon.data.ads.internal.ConsentManager
import com.whitefang.stepsofbabylon.data.ads.internal.RealConsentManager
import com.whitefang.stepsofbabylon.data.ads.internal.RealRewardedAdAdapter
import com.whitefang.stepsofbabylon.data.ads.internal.RewardedAdAdapter
import com.whitefang.stepsofbabylon.domain.repository.RewardAdManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Hilt wiring for [RewardAdManager]. C.6 PR 2 introduced the
 * [BuildConfig.USE_REAL_ADS] flag and the runtime switch between
 * [StubRewardAdManager] (debug + any build type without AdMob provisioning)
 * and [RewardAdManagerImpl] (release, real Google Mobile Ads SDK v25 + UMP).
 *
 * Mirrors the [BillingModule] shape landed in C.5 PR 2. See that module's KDoc
 * for the full `@Provides` + [Provider] rationale; the recap:
 *
 * **Why `@Provides` + [Provider] instead of `@Binds`.** Hilt's `@Binds` is
 * resolved at compile time and cannot branch on a runtime value like
 * `BuildConfig.USE_REAL_ADS`. Two `@Provides` methods marked with the same
 * return type would collide. Injecting both candidates as [Provider]s defers
 * construction — whichever branch is not selected is never instantiated, so the
 * stub's `delay(1000)` never fires in a release build and the real impl's
 * AdMob + UMP clients never start in a debug build.
 *
 * **Why `internal`.** [RewardAdManagerImpl], [RealRewardedAdAdapter], and
 * [RealConsentManager] are all `internal` (they expose internal adapter types
 * through their constructors), so a `public` provider method cannot legally
 * reference them. Making the whole module `internal` matches Hilt's
 * "single Gradle module" assumption for this app.
 *
 * C.6 PR 2 / ADR-0006.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object AdModule {

    @Provides
    @Singleton
    fun provideRewardAdManager(
        stub: Provider<StubRewardAdManager>,
        real: Provider<RewardAdManagerImpl>,
    ): RewardAdManager = if (BuildConfig.USE_REAL_ADS) real.get() else stub.get()
}

/**
 * Internal bindings for the ad SDK adapter layer. Separate module from [AdModule]
 * because [AdModule] is an `object` (for `@Provides`) and this uses `@Binds`
 * (which requires an abstract class). Both are `internal` so they can reference
 * [RealRewardedAdAdapter] and [RealConsentManager], which are themselves `internal`.
 *
 * Flow (mirrors the `BillingInternalModule` precedent from C.5 PR 2):
 *
 * - The `@Provides provideRewardAdManager` method in [AdModule] asks Hilt for
 *   `Provider<RewardAdManagerImpl>` even in debug builds (where the Provider is
 *   never invoked). Dagger therefore needs routes from
 *   `RewardAdManagerImpl.adapter: RewardedAdAdapter` and
 *   `RewardAdManagerImpl.consentManager: ConsentManager` to concrete types,
 *   which these bindings supply.
 * - [MainActivity][com.whitefang.stepsofbabylon.presentation.MainActivity] also
 *   injects [ConsentManager] directly for the release-build consent prefetch
 *   on resume — this binding is what resolves that dependency.
 * - Unit tests mock [RewardedAdAdapter] + [ConsentManager] directly and
 *   construct [RewardAdManagerImpl] by hand — this module is not consulted there.
 *
 * C.6 PR 2 / ADR-0006.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class AdInternalModule {

    @Binds
    @Singleton
    abstract fun bindRewardedAdAdapter(impl: RealRewardedAdAdapter): RewardedAdAdapter

    @Binds
    @Singleton
    abstract fun bindConsentManager(impl: RealConsentManager): ConsentManager
}
