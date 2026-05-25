import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Load keystore credentials from gitignored file (optional — debug builds work without it)
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) load(keystorePropertiesFile.inputStream())
}

// Load AdMob production IDs from gitignored local.properties. Loaded once at
// configure-time and consumed by the `release { }` block below. Debug build
// keeps Google's documented test IDs from defaultConfig. C.6 PR 2.
val localPropertiesFile = rootProject.file("local.properties")
val localProperties = Properties().apply {
    if (localPropertiesFile.exists()) load(localPropertiesFile.inputStream())
}
// Test-ad fallback constants. If local.properties is missing the AdMob keys
// (e.g. a CI build, a fresh clone), the release build falls back to these so
// it never mints revenue from accidental impressions on a misconfigured build.
val ADMOB_TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713"
val ADMOB_TEST_REWARDED_AD_UNIT = "ca-app-pub-3940256099942544/5224354917"

android {
    namespace = "com.whitefang.stepsofbabylon"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.whitefang.stepsofbabylon"
        minSdk = 34
        targetSdk = 36
        versionCode = 13
        versionName = "1.0.0"

        // Default USE_REAL_ADS value. Post-C.6 PR 3 the flag no longer gates the
        // `RewardAdManager` binding (there is only `RewardAdManagerImpl`). It still
        // gates the UMP consent prefetch in MainActivity.onResume so debug emulators
        // without Play Services do not pay the UMP init cost. Release builds prefetch
        // so the first reward-ad tap doesn't pay the ~200-500ms UMP init latency.
        // C.6 PR 1 / PR 2 / PR 3 / ADR-0006. (Symmetrical USE_REAL_BILLING flag was
        // removed in C.5 PR 3 once `StubBillingManager` was deleted.)
        buildConfigField("boolean", "USE_REAL_ADS", "false")

        // AdMob ad-unit IDs per AdPlacement. Debug uses Google's documented test-ad unit
        // (ca-app-pub-3940256099942544/5224354917) so a dev can exercise the real SDK path
        // without a production AdMob account. Release overrides source real IDs from
        // local.properties via keystore-style gitignored loading (wired in PR 2). The
        // defaults here are safe-for-debug test IDs so a misconfigured release still
        // doesn't mint revenue from unsigned builds. C.6 PR 1.
        buildConfigField("String", "AD_UNIT_POST_ROUND_GEM", "\"ca-app-pub-3940256099942544/5224354917\"")
        buildConfigField("String", "AD_UNIT_POST_ROUND_DOUBLE_PS", "\"ca-app-pub-3940256099942544/5224354917\"")
        buildConfigField("String", "AD_UNIT_DAILY_FREE_CARD_PACK", "\"ca-app-pub-3940256099942544/5224354917\"")

        // AdMob APPLICATION_ID manifest placeholder. Substituted into the
        // <meta-data android:name="com.google.android.gms.ads.APPLICATION_ID"/> entry in
        // AndroidManifest.xml at build time. Debug uses Google's documented test app ID
        // (ca-app-pub-3940256099942544~3347511713); release overrides with the real ID
        // once C.6 PR 2 wires local.properties. C.6 PR 1.
        manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Debug skips the MainActivity UMP consent prefetch so emulators without
            // Play Services don't log spurious UMP errors on every app start. The real
            // RewardAdManagerImpl is still bound; the first ad tap will initialise UMP
            // lazily (and most likely return AdResult.Error on a bare emulator). C.6 PR 3.
            buildConfigField("boolean", "USE_REAL_ADS", "false")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }

            // Bundle native debug symbols (SQLCipher .so files etc.) inside the AAB so
            // Play Console can deobfuscate native crash stack traces. Without this, the
            // Play Console upload step warns "This App Bundle contains native code, and
            // you've not uploaded debug symbols." Symbols ship in the AAB only — they're
            // stripped from the on-device APK, so end-user install size is unchanged.
            // FULL includes function names + line numbers; SYMBOL_TABLE drops line numbers
            // for a smaller upload. FULL is fine for v1 — the upload-side bloat is small.
            ndk {
                debugSymbolLevel = "FULL"
            }
            // Release enables the MainActivity UMP consent prefetch on first resume so
            // the first reward-ad tap doesn't pay the ~200-500ms UMP init latency. C.6 PR 3.
            buildConfigField("boolean", "USE_REAL_ADS", "true")

            // AdMob production IDs sourced from gitignored local.properties. Falls back
            // to Google's documented test IDs if local.properties is absent or missing
            // a key (e.g. CI build, fresh clone) so a misconfigured release still
            // doesn't mint revenue from accidental impressions. C.6 PR 2.
            val realAppId = localProperties.getProperty("admob.appId") ?: ADMOB_TEST_APP_ID
            val realPostRoundGem = localProperties.getProperty("admob.adUnit.postRoundGem") ?: ADMOB_TEST_REWARDED_AD_UNIT
            val realPostRoundDoublePs = localProperties.getProperty("admob.adUnit.postRoundDoublePs") ?: ADMOB_TEST_REWARDED_AD_UNIT
            val realDailyFreeCardPack = localProperties.getProperty("admob.adUnit.dailyFreeCardPack") ?: ADMOB_TEST_REWARDED_AD_UNIT
            buildConfigField("String", "AD_UNIT_POST_ROUND_GEM", "\"$realPostRoundGem\"")
            buildConfigField("String", "AD_UNIT_POST_ROUND_DOUBLE_PS", "\"$realPostRoundDoublePs\"")
            buildConfigField("String", "AD_UNIT_DAILY_FREE_CARD_PACK", "\"$realDailyFreeCardPack\"")
            manifestPlaceholders["admobAppId"] = realAppId
        }
    }

    buildFeatures {
        // Required because MainActivity reads BuildConfig.USE_REAL_ADS to gate the UMP
        // consent prefetch (debug emulators skip Play Services init). AGP 9 disables
        // BuildConfig by default; opt in explicitly. C.5 PR 2 / C.6 PR 3.
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    @Suppress("UnstableApiUsage")
    testOptions {
        unitTests.all { it.useJUnitPlatform() }
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // Compose
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    // AndroidX
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // WorkManager
    implementation(libs.workmanager)

    // Hilt WorkManager & Navigation
    implementation(libs.hilt.work)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.androidx.compiler)

    // Health Connect
    implementation(libs.health.connect.client)

    // SQLCipher
    implementation(libs.sqlcipher)
    implementation(libs.sqlite)

    // Google Play Billing v8 (C.5 PR 1 / PR 2 / PR 3 / ADR-0005). `BillingManagerImpl` is
    // the sole `BillingManager` binding post-PR 3 — `StubBillingManager` was deleted after
    // the C.5 PR 2 internal-track verification confirmed real-device wallet credit
    // end-to-end on the v3 internal-track AAB.
    implementation(libs.billing.ktx)

    // Google Mobile Ads SDK + UMP (C.6 PR 1 / PR 2 / PR 3 / ADR-0006). `RewardAdManagerImpl`
    // is the only `RewardAdManager` binding post-PR 3; debug + release both use it, with
    // the UMP consent prefetch in MainActivity gated by BuildConfig.USE_REAL_ADS so debug
    // emulators skip Play Services init.
    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)

    // Testing
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly(libs.junit.vintage.engine)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.test.core)
}
