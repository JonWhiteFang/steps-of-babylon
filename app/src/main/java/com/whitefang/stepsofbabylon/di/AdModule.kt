package com.whitefang.stepsofbabylon.di

import com.whitefang.stepsofbabylon.data.ads.RewardAdManagerImpl
import com.whitefang.stepsofbabylon.data.ads.internal.ConsentManager
import com.whitefang.stepsofbabylon.data.ads.internal.RealConsentManager
import com.whitefang.stepsofbabylon.data.ads.internal.RealRewardedAdAdapter
import com.whitefang.stepsofbabylon.data.ads.internal.RewardedAdAdapter
import com.whitefang.stepsofbabylon.domain.repository.RewardAdManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt wiring for [RewardAdManager].
 *
 * **History.**
 *
 * - **Plan 26** introduced the seam with a stub (`StubRewardAdManager`) so the UI could
 *   be built without real AdMob provisioning.
 * - **C.6 PR 1** landed the real [RewardAdManagerImpl] + adapter + UMP consent. `@Binds`
 *   still pointed at the stub pending PR 2.
 * - **C.6 PR 2** introduced a runtime [com.whitefang.stepsofbabylon.BuildConfig.USE_REAL_ADS]
 *   switch between stub (debug) and real (release) via a `@Provides` + [javax.inject.Provider]
 *   pair. That shape was required only while two implementations coexisted.
 * - **C.6 PR 3 (this file)** deleted `StubRewardAdManager` after the C.6 PR 2 internal-track
 *   verification passed across two sessions / two placements (`DAILY_FREE_CARD_PACK` →
 *   AdMob `NO_FILL`; `POST_ROUND_GEM` → DNS failure — both paths mapped correctly to
 *   [com.whitefang.stepsofbabylon.domain.model.AdResult.Error]). With only one implementation
 *   left, the Provider-switch is unnecessary and this module collapses back to a plain
 *   `@Binds`.
 *
 * **Why still `internal`.** [RewardAdManagerImpl] is `internal` (it takes internal adapter
 * + consent types through its constructor), so a `public` binding method cannot legally
 * reference it. Matching the module visibility to the impl visibility is simpler than
 * annotating every member.
 *
 * **[BuildConfig.USE_REAL_ADS] outlives this module.** The flag no longer gates the binding
 * but still gates the UMP consent prefetch in
 * [com.whitefang.stepsofbabylon.presentation.MainActivity.onResume]: debug emulators
 * without Play Services don't pay the UMP init cost, and release builds prefetch consent
 * before the first reward-ad tap. (The previously-symmetrical `USE_REAL_BILLING` flag was
 * removed in C.5 PR 3 once `StubBillingManager` was deleted.)
 *
 * C.6 PR 3 / ADR-0006.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class AdModule {

    @Binds
    @Singleton
    abstract fun bindRewardAdManager(impl: RewardAdManagerImpl): RewardAdManager
}

/**
 * Internal bindings for the ad SDK adapter layer. Unchanged by C.6 PR 3.
 *
 * Flow:
 *
 * - [RewardAdManagerImpl]'s constructor takes [RewardedAdAdapter] + [ConsentManager];
 *   these bindings supply the concrete implementations.
 * - [com.whitefang.stepsofbabylon.presentation.MainActivity] also injects [ConsentManager]
 *   directly for the release-build consent prefetch on resume — this binding resolves
 *   that dependency too.
 * - Unit tests mock [RewardedAdAdapter] + [ConsentManager] directly and construct
 *   [RewardAdManagerImpl] by hand — this module is not consulted there.
 *
 * C.6 PR 2 / C.6 PR 3 / ADR-0006.
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
