// Root build file. Kotlin 2.4.21 is put on the plugin classpath here (kotlin-jvm, apply false) so that
// Gradle resolves it over the Kotlin Gradle plugin 2.2.10 that AGP 9.4.1 depends on.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}
