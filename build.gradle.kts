plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    // #26 Gate G — declare here with `apply false` so the version is pinned ONCE on the root
    // classpath; the :baselineprofile / :macrobenchmark modules + :app then apply them by alias
    // without re-resolving (otherwise `com.android.test` clashes with AGP already on the classpath).
    // NOTE: no kotlin.android plugin — AGP 9.0 provides built-in Kotlin support, and applying
    // org.jetbrains.kotlin.android to a com.android.* module is an ERROR under AGP 9.
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
}
