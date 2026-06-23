package com.whitefang.stepsofbabylon.data.sensor

import android.content.Context
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * #261: reports whether this app is exempt from battery optimization (Doze / App-Standby). The
 * GDD's highest-rated risk is the foreground [com.whitefang.stepsofbabylon.service.StepCounterService]
 * being killed on aggressive-OEM devices, which silently stops step counting. Onboarding uses this to
 * decide whether to offer the battery-exemption prompt (the documented mitigation in
 * `docs/step-tracking.md`), skipping it when already exempt.
 *
 * A thin injectable wrapper around [PowerManager] so the decision is unit-testable without Android —
 * mirrors [StepSensorDataSource.isSensorAvailable] (the #193 pattern). `isIgnoringBatteryOptimizations`
 * is API 23+; the project minSdk is 34, so no guard is needed.
 */
@Singleton
class BatteryOptimizationStatus
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /** True when the app is whitelisted from battery optimization (the foreground service can run freely). */
        fun isIgnoring(): Boolean {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return pm.isIgnoringBatteryOptimizations(context.packageName)
        }
    }
