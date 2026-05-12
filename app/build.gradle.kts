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

android {
    namespace = "com.whitefang.stepsofbabylon"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.whitefang.stepsofbabylon"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        // Default USE_REAL_BILLING value for any build type that doesn't override it
        // (e.g. custom flavours). Debug overrides to `false` for local iteration without a
        // Play Store account; release overrides to `true` so production builds bind the
        // real BillingManagerImpl. See di/BillingModule.kt for the Provider-based switch.
        // C.5 PR 2 / ADR-0005.
        buildConfigField("boolean", "USE_REAL_BILLING", "false")

        // Default USE_REAL_ADS value. Debug binds StubRewardAdManager; release binds
        // RewardAdManagerImpl once C.6 PR 2 lands (PR 1 leaves @Binds at stub; the flag
        // is read by nothing until PR 2). C.6 PR 1 / ADR-0006.
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
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Explicit false to keep grep-friendly symmetry with the release override.
            // Debug builds bind StubBillingManager so running on a device without a Play
            // Store account (e.g. emulator cold-start) still exercises the Store UI.
            buildConfigField("boolean", "USE_REAL_BILLING", "false")
            // C.6 PR 1: debug binds StubRewardAdManager pending PR 2. Flag is read by
            // nothing yet but symmetry with USE_REAL_BILLING makes the PR 2 diff smaller.
            buildConfigField("boolean", "USE_REAL_ADS", "false")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            // Release builds bind BillingManagerImpl (real Play Billing v8). C.5 PR 2.
            buildConfigField("boolean", "USE_REAL_BILLING", "true")
            // Release flag is true so C.6 PR 2's Provider switch picks RewardAdManagerImpl
            // automatically once wired. C.6 PR 1 leaves @Binds at stub either way.
            buildConfigField("boolean", "USE_REAL_ADS", "true")
        }
    }

    buildFeatures {
        // Required because we read BuildConfig.USE_REAL_BILLING in di/BillingModule.kt.
        // AGP 9 disables BuildConfig by default; opt in explicitly. C.5 PR 2.
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

    // Google Play Billing (C.5 PR 1 / ADR-0005). Real impl exists but @Binds still points at
    // StubBillingManager; PR 2 introduces the BuildConfig.USE_REAL_BILLING flag + binding swap.
    implementation(libs.billing.ktx)

    // Google Mobile Ads SDK + UMP (C.6 PR 1 / ADR-0006). Real RewardAdManagerImpl exists but
    // @Binds still points at StubRewardAdManager; C.6 PR 2 introduces the
    // BuildConfig.USE_REAL_ADS flag + binding swap + MainActivity consent-flow wiring.
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
