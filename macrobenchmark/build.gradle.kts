plugins {
    // No kotlin.android — AGP 9 provides built-in Kotlin (matches the :baselineprofile module).
    alias(libs.plugins.android.test)
}

android {
    namespace = "com.whitefang.stepsofbabylon.macrobenchmark"
    compileSdk = 36

    defaultConfig {
        minSdk = 34
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"

    // Macrobenchmark runs against the profileable, non-debuggable `benchmarkRelease` variant the
    // baselineprofile plugin auto-creates on :app.
    @Suppress("UnstableApiUsage")
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
