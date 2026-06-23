package com.whitefang.stepsofbabylon.di

import com.whitefang.stepsofbabylon.BuildConfig
import com.whitefang.stepsofbabylon.data.billing.BillingManagerImpl
import com.whitefang.stepsofbabylon.data.billing.internal.BillingClientAdapter
import com.whitefang.stepsofbabylon.data.billing.internal.PurchaseVerifier
import com.whitefang.stepsofbabylon.data.billing.internal.RealBillingClientAdapter
import com.whitefang.stepsofbabylon.data.billing.internal.RealPurchaseVerifier
import com.whitefang.stepsofbabylon.domain.repository.BillingManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt wiring for [BillingManager].
 *
 * **History.**
 *
 * - **Plan 26** introduced the seam with a stub (`StubBillingManager`) so the Store UI
 *   could be built without real Play Billing provisioning.
 * - **C.5 PR 1** landed the real [BillingManagerImpl] + adapter + receipt-table
 *   idempotency + reconciliation. `@Binds` still pointed at the stub pending PR 2.
 * - **C.5 PR 2** introduced a runtime [com.whitefang.stepsofbabylon.BuildConfig.USE_REAL_BILLING]
 *   switch between stub (debug) and real (release) via a `@Provides` + [javax.inject.Provider]
 *   pair. That shape was required only while two implementations coexisted.
 * - **C.5 PR 3 (this file)** deleted `StubBillingManager` after the C.5 PR 2 internal-track
 *   verification passed on a real device (`gem_pack_small/medium/large`, `ad_removal`, and
 *   `season_pass` all credited the wallet correctly through real Play Billing v8 with the
 *   test card on the rolled-out v3 internal-track AAB). With only one implementation left,
 *   the Provider-switch is unnecessary, the `USE_REAL_BILLING` flag is dead, and this
 *   module collapses back to a plain `@Binds`. Mirrors the C.6 PR 3 collapse of `AdModule`.
 *
 * **Why `internal`.** [BillingManagerImpl] and [RealBillingClientAdapter] are `internal`
 * (they expose internal adapter types through their constructors), so a `public` binding
 * method cannot legally reference them. Matching the module visibility to the impl
 * visibility is simpler than annotating every member.
 *
 * C.5 PR 3 / ADR-0005.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class BillingModule {
    @Binds
    @Singleton
    abstract fun bindBillingManager(impl: BillingManagerImpl): BillingManager
}

/**
 * Internal binding for the SDK adapter layer. Unchanged by C.5 PR 3.
 *
 * Flow:
 *
 * - [BillingManagerImpl]'s constructor takes [BillingClientAdapter]; this binding supplies
 *   the concrete [RealBillingClientAdapter].
 * - [BillingManagerImpl] also takes [PurchaseVerifier]; the companion `@Provides` constructs
 *   the concrete [RealPurchaseVerifier] with the build-time Play license key (#124).
 * - Unit tests mock [BillingClientAdapter] / substitute a fake [PurchaseVerifier] directly and
 *   construct [BillingManagerImpl] by hand — this module is not consulted there.
 *
 * C.5 PR 1 / C.5 PR 2 / C.5 PR 3 / #124 / ADR-0005.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class BillingInternalModule {
    @Binds
    @Singleton
    abstract fun bindBillingClientAdapter(impl: RealBillingClientAdapter): BillingClientAdapter

    internal companion object {
        /**
         * Provides the client-side [PurchaseVerifier] (#124) seeded with the Base64 Play
         * "Licensing" public key baked into the build at configure-time. Debug / CI builds get
         * the empty default → verification is disabled (fail-open); a correctly-configured
         * release embeds the real key (from gitignored `local.properties`) and rejects forged
         * purchases. A `@Provides` (not `@Binds`) because the impl is built from a constant,
         * not bound from another type. See `app/build.gradle.kts` `PLAY_LICENSE_KEY`.
         */
        @Provides
        @Singleton
        fun providePurchaseVerifier(): PurchaseVerifier = RealPurchaseVerifier(BuildConfig.PLAY_LICENSE_KEY)
    }
}
