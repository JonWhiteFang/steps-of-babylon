import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.androidx.baselineprofile)
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
    compileSdk = 37

    defaultConfig {
        applicationId = "com.whitefang.stepsofbabylon"
        minSdk = 34
        targetSdk = 36
        versionCode = 26
        versionName = "1.0.10"

        // V1X-08 Phase 1A: instrumented tests use a custom AndroidJUnitRunner that swaps
        // StepsOfBabylonApp → HiltTestApplication so Hilt-rooted DI works in androidTest.
        // The runner class lives in app/src/androidTest/java/com/whitefang/stepsofbabylon/HiltTestRunner.kt.
        testInstrumentationRunner = "com.whitefang.stepsofbabylon.HiltTestRunner"

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

        // V1X-17: Play Store URL for text-share buttons (share-sheet templates).
        buildConfigField("String", "PLAY_STORE_URL", "\"https://play.google.com/store/apps/details?id=com.whitefang.stepsofbabylon\"")

        // #124: Google Play "Licensing" RSA public key (Base64) for client-side purchase
        // signature verification. Default is empty → verification is disabled (fail-open),
        // which is the correct debug/CI behaviour: those builds use Play Console license-test
        // accounts whose signatures we can't verify offline. Release overrides this from
        // gitignored local.properties (`play.licenseKey`) below. See PurchaseVerifier + ADR-0005.
        buildConfigField("String", "PLAY_LICENSE_KEY", "\"\"")

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

            // #124: real Play "Licensing" public key from gitignored local.properties. A
            // correctly-configured release embeds the key and rejects forged purchases. A BLANK
            // key would make RealPurchaseVerifier fail-open (verification disabled) — which must
            // never ship — so the `whenReady` guard below FAILS the build when a release artifact
            // is assembled with a blank key. (Debug / CI `assembleDebug` keep the "" fallback and
            // fail-open by design; this field still needs a value here for them to compile.)
            val realLicenseKey = localProperties.getProperty("play.licenseKey") ?: ""
            buildConfigField("String", "PLAY_LICENSE_KEY", "\"$realLicenseKey\"")
        }
    }

    // #124 fail-closed guard: refuse to PRODUCE a release AAB/APK with verification fail-open.
    // Scoped to the task graph so it fires only when a release-producing task actually runs —
    // debug builds, unit tests, and the PR gate's `assembleDebug` are unaffected (they configure
    // the release block above but never assemble it). The release CI lane injects the key from
    // the PLAY_LICENSE_KEY secret (see .github/workflows/release.yml).
    gradle.taskGraph.whenReady {
        // Match ANY release-artifact-producing task, not just the two the current lane uses, so a
        // future product flavor (`bundleProdRelease`), `packageRelease`, or the umbrella `bundle`
        // task can't silently bypass the guard. `assembleDebug` / `testDebugUnitTest` / the PR gate
        // never match (they don't end in "Release"), so those lanes stay unaffected.
        val releaseTask = Regex("^(bundle|assemble|package).*Release$")
        // #124 + #26: keep the BROAD release-task match (so a future product-flavor release task such as
        // `bundleProdRelease` is still caught — see the comment below), but exclude the AndroidX benchmark /
        // baseline-profile variant tasks (`assembleBenchmarkRelease`, `bundleNonMinifiedRelease`, …). The
        // androidx.baselineprofile plugin auto-generates `benchmarkRelease`/`nonMinifiedRelease` from `release`,
        // so they inherit the blank-by-default play.licenseKey and would otherwise false-trip this fail-closed
        // guard on every benchmark build. The exclusion is PER TASK: a graph containing BOTH `bundleRelease`
        // AND `assembleBenchmarkRelease` still hard-fails on a blank key, because the shippable `bundleRelease`
        // task matches the regex and carries neither excluded token. `generate*BaselineProfile` tasks end in
        // `Profile` and never match the regex, so they need no exclusion.
        val buildsRelease = allTasks.any { t ->
            releaseTask.matches(t.name) &&
                !t.name.contains("Benchmark") &&
                !t.name.contains("NonMinified")
        }
        if (buildsRelease && localProperties.getProperty("play.licenseKey").isNullOrBlank()) {
            throw GradleException(
                "Release build requires a non-blank 'play.licenseKey' in local.properties " +
                    "(CI: the PLAY_LICENSE_KEY secret). A blank key makes #124 purchase-signature " +
                    "verification fail-open — refusing to ship a release with it disabled.",
            )
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

    lint {
        // V1X-13 / ADR-0014 i18n guard: promote HardcodedText from warning to error so a
        // hardcoded android:text/contentDescription/hint in an XML resource fails the build.
        // LIMITATION: HardcodedText is an XML-only check — it does NOT flag Compose
        // `Text("literal")` (verified empirically: this build passes despite ~110 hardcoded
        // Compose strings still present on phase-2 screens). Compose string discipline is held
        // by the phase-1 migration + review until a dedicated Compose lint rule lands later.
        error += "HardcodedText"
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

// #26 Gate G — baseline-profile consumer config. We pin benchmark/baselineprofile to 1.5.0-alpha06
// (the line that adds AGP-9 support); the stable 1.4.1 plugin throws at apply time on AGP 9.0.1, so the
// old `newDsl = false` workaround is neither needed nor usable here. `automaticGenerationDuringBuild =
// false` keeps profile generation OUT of the ordinary assemble/bundleRelease graph — generation is a
// deliberate local-device step (see the plan / docs), so the shipping lane stays clean and the #124
// guard's task-graph reasoning stays simple.
baselineProfile {
    automaticGenerationDuringBuild = false
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

    // Coroutines — #257: pin the runtime explicitly (was floating transitively at 1.9.0 via
    // Room-ktx/Lifecycle/Hilt). Aligned with kotlinx-coroutines-test via the shared `coroutines` ref.
    implementation(libs.coroutines.android)

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

    // #26 Gate G: installs the committed baseline-prof.txt at runtime so the most-used path
    // (Home → Workshop → Battle) is AOT-compiled on first launch.
    implementation(libs.androidx.profileinstaller)

    // #26 Gate G: consumes the generated profile from :baselineprofile (added in a later task).
    "baselineProfile"(project(":baselineprofile"))

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

    // Security: transitive-dependency floor. guava is NOT a direct dependency — it ships
    // transitively via kotlinx-coroutines-guava + Play Services at 31.1-android, which is
    // flagged by Dependabot (CVE-2023-2976 / CVE-2020-8908, insecure temp-dir). A constraint
    // raises the resolved version without adding a direct dependency. (Plan 32 security
    // follow-up, 2026-06-10.)
    constraints {
        implementation(libs.guava) {
            because("Force transitive guava >=32-android to clear CVE-2023-2976 / CVE-2020-8908 (default is 31.1-android)")
        }
    }

    // Testing
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly(libs.junit.vintage.engine)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.workmanager.testing)
    // TestNavHostController for the #161 nav-restore regression guard (BottomNavRestoreTest) —
    // drives the real shared NavOptions on the JVM, no Compose UI rule / activity needed.
    testImplementation(libs.navigation.testing)
    // Compose UI tests (#253) on the Robolectric/JVM lane — the BOM is otherwise only on
    // `implementation`, so re-apply it to the test classpath to version-align the ui-test artifacts.
    testImplementation(composeBom)
    testImplementation(libs.compose.ui.test.junit4)
    // ui-test-manifest must be `debugImplementation` (not testImplementation): it contributes the
    // `androidx.activity.ComponentActivity` declaration that createComposeRule() launches, and that
    // only merges into the debug manifest Robolectric reads when applied to the variant, not the test set.
    debugImplementation(libs.compose.ui.test.manifest)

    // Instrumented testing (androidTest source set) — V1X-08 Phase 1A.
    // Hilt instrumented DI requires kspAndroidTest so the @HiltAndroidTest classes get
    // their components generated; the regular ksp config does not cover androidTest.
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}
