package com.whitefang.stepsofbabylon.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import com.whitefang.stepsofbabylon.data.NotificationPreferences
import com.whitefang.stepsofbabylon.data.sensor.DailyStepManager
import com.whitefang.stepsofbabylon.data.sensor.StepIngestionPreferences
import com.whitefang.stepsofbabylon.data.sensor.StepSensorDataSource
import com.whitefang.stepsofbabylon.domain.repository.PlayerRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Folds a freshly-read step balance (null = the read failed) against the last-known-good value
 * for the always-on notification (#43). A successful read (including a genuine `0`) is taken as
 * the new value; a failed read retains [lastKnown]; a failed read with no prior good value yields
 * `null` so the caller skips that notification refresh rather than inventing a "0". Pure +
 * top-level for unit coverage (StepBalanceDisplayTest).
 */
internal fun resolveDisplayBalance(fresh: Long?, lastKnown: Long?): Long? = fresh ?: lastKnown

@AndroidEntryPoint
class StepCounterService : Service() {

    @Inject lateinit var sensorDataSource: StepSensorDataSource
    @Inject lateinit var dailyStepManager: DailyStepManager
    @Inject lateinit var notificationManager: StepNotificationManager
    @Inject lateinit var playerRepository: PlayerRepository
    @Inject lateinit var stepIngestionPrefs: StepIngestionPreferences
    @Inject lateinit var sensorManager: SensorManager
    @Inject lateinit var notificationPreferences: NotificationPreferences

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // #43: last successfully-read step balance, so a transient read failure retains the last
    // good value on the notification instead of coercing to 0 ("Balance: 0" looks like total loss).
    private var lastKnownBalance: Long? = null

    override fun onCreate() {
        super.onCreate()
        val notification = if (notificationPreferences.isPersistentEnabled()) {
            notificationManager.buildNotification(0, 0)
        } else {
            notificationManager.buildMinimalNotification()
        }
        startForeground(
            StepNotificationManager.NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH,
        )

        // Establish day-start counter baseline on startup
        scope.launch(Dispatchers.IO) {
            initDayStartCounter()
        }

        scope.launch {
            sensorDataSource.stepDeltas.collect { delta ->
                val now = System.currentTimeMillis()
                dailyStepManager.recordSteps(delta, now)
                stepIngestionPrefs.updateServiceHeartbeat(now)

                // #43: a transient balance-read failure must NOT coerce the displayed balance to 0
                // (that reads as "you lost all your Steps" on the always-on notification). Read into
                // a nullable (null = failed) and fold against the last good value. The daily-step
                // count is independent of this read, so the notification ALWAYS refreshes — only the
                // balance falls back: to the last good value, or 0 on a cold start before any
                // successful read (matching the initial onCreate notification, so it never regresses
                // a known non-zero balance to 0).
                val fresh = try { playerRepository.observeProfile().first().stepBalance } catch (_: Exception) { null }
                val balance = resolveDisplayBalance(fresh = fresh, lastKnown = lastKnownBalance)
                if (balance != null) lastKnownBalance = balance
                notificationManager.updateNotification(
                    dailySteps = dailyStepManager.getDailyCredited(),
                    balance = balance ?: 0L,
                )
            }
        }
    }

    /**
     * Sets the day-start cumulative counter if not already set for today.
     * This ensures the worker has a valid baseline when it takes over after service death.
     */
    private fun initDayStartCounter() {
        val today = LocalDate.now().toString()
        if (stepIngestionPrefs.getCounterAtDayStart(today) != null) return

        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
        var value: Long? = null
        val latch = CountDownLatch(1)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                value = event.values[0].toLong()
                sensorManager.unregisterListener(this)
                latch.countDown()
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        latch.await(5, TimeUnit.SECONDS)
        sensorManager.unregisterListener(listener)

        value?.let { stepIngestionPrefs.setCounterAtDayStart(today, it) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
