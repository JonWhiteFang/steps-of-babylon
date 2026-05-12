# ============================================================
# Steps of Babylon — R8 / ProGuard Rules
# ============================================================

# --- Room ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-dontwarn androidx.room.paging.**

# --- Hilt / Dagger ---
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager { *; }

# --- WorkManager + Hilt Worker Factory ---
-keep class * extends androidx.work.ListenableWorker { *; }

# --- SQLCipher ---
-keep class net.zetetic.** { *; }

# --- Google Play Billing Library v8 (C.5 PR 1 / ADR-0005) ---
# Play Billing uses reflection/AIDL for cross-process Play Services communication; the keep
# rules below are the union of what Google's Play Billing sample apps and the "missing rules
# report" flag in R8 recommend. Over-broad on purpose — billing classes are a stable surface
# and R8 gets aggressive with `billingclient.*` without explicit keeps.
-keep class com.android.billingclient.** { *; }
-keep interface com.android.billingclient.** { *; }
-dontwarn com.android.billingclient.**

# --- Google Mobile Ads SDK + UMP (C.6 PR 1 / ADR-0006) ---
# AdMob's SDK relies on reflection for its ad-format parsing + listener callbacks, and UMP
# loads its privacy-form HTML via reflection-bound interfaces. Both SDKs ship with a -keep
# manifest bundled in their AARs, but we keep explicit rules here so R8 doesn't strip any
# of the callback/listener surfaces even when the bundled rules change between versions.
-keep class com.google.android.gms.ads.** { *; }
-keep interface com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**
-keep class com.google.android.ump.** { *; }
-keep interface com.google.android.ump.** { *; }
-dontwarn com.google.android.ump.**

# --- Health Connect SDK (uses reflection internally) ---
-keep class androidx.health.connect.** { *; }

# --- Sensor callbacks (invoked by framework via reflection) ---
-keep class * implements android.hardware.SensorEventListener {
    void onSensorChanged(android.hardware.SensorEvent);
    void onAccuracyChanged(android.hardware.Sensor, int);
}

# --- Game domain models (enums stored as names in Room) ---
-keep enum com.whitefang.stepsofbabylon.domain.model.** { *; }

# --- Room TypeConverters (uses org.json — Android framework, defensive keep) ---
-keep class org.json.** { *; }
