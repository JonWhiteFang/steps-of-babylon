plugins {
    // No kotlin.android — AGP 9 provides built-in Kotlin; applying it to a com.android.test module errors.
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.whitefang.stepsofbabylon.baselineprofile"
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

    // The app variant this module generates a profile against (the baselineprofile plugin
    // auto-creates `nonMinifiedRelease` on :app).
    targetProjectPath = ":app"
}

// Run the generator on a real device / non-rooted physical device or an AOSP/Play-image emulator.
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
