plugins {
    // No kotlin.android — AGP 9 provides built-in Kotlin (matches the :baselineprofile module).
    alias(libs.plugins.android.test)
}

android {
    namespace = "com.whitefang.stepsofbabylon.macrobenchmark"
    compileSdk = 37

    defaultConfig {
        minSdk = 34
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // The benchmark runs against the profileable, non-debuggable `benchmarkRelease` variant the
    // baselineprofile plugin auto-creates on :app — selected via this targetProjectPath + variant
    // matching.
    targetProjectPath = ":app"

    // Standard Android-Studio macrobenchmark-template flag. NOTE: this is UNVERIFIED on-device by this
    // PR (only `assemble`/type-check runs in CI); if on-device instrumentation can't locate the target
    // app in Task 9, this flag is the first thing to revisit (the conventional value with a separate
    // targetProjectPath app is `false` — test APK instruments a separate process).
    @Suppress("UnstableApiUsage")
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

// #378: pin the compiler JDK to 17 via local toolchain detection (no foojay resolver).
// Orthogonal to compileOptions above (which sets the bytecode/target level, not the compiler JDK).
kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
