package com.whitefang.stepsofbabylon.data.billing.internal

import android.app.Activity
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton holder for the currently foregrounded [Activity], used by
 * [com.whitefang.stepsofbabylon.data.billing.BillingManagerImpl] to obtain an Activity
 * reference for `BillingClient.launchBillingFlow()` — which requires an Activity and cannot
 * accept a Context.
 *
 * **Lifecycle wiring.** `MainActivity.onResume()` calls [set] and `onPause()` calls [clear]
 * (landed in C.5 PR 2). The provider is consulted by [BillingManagerImpl.purchase] just
 * before `BillingClient.launchBillingFlow()` so the user always launches a purchase against
 * the foregrounded Activity.
 *
 * **Memory safety.** Uses a [WeakReference] so a missed [clear] cannot leak the Activity
 * past its finalizer. Callers of [current] MUST handle the `null` case gracefully —
 * purchases attempted while no Activity is foregrounded return
 * [com.whitefang.stepsofbabylon.data.billing.internal.StartPurchaseResult.NotCompleted].
 *
 * Introduced by C.5 PR 1 / ADR-0005. Also reused by `data/ads/RewardAdManagerImpl` from
 * C.6 PR 1 onwards.
 */
@Singleton
internal class ActivityProvider
    @Inject
    constructor() {
        @Volatile
        private var activityRef: WeakReference<Activity>? = null

        /** Registers [activity] as the current foregrounded Activity. Idempotent. */
        fun set(activity: Activity) {
            activityRef = WeakReference(activity)
        }

        /** Clears the registered Activity reference. Safe to call when nothing is registered. */
        fun clear() {
            activityRef = null
        }

        /**
         * Returns the currently foregrounded Activity, or `null` if nothing is registered or the
         * registered Activity has been garbage-collected.
         */
        fun current(): Activity? = activityRef?.get()
    }
