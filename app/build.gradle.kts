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
