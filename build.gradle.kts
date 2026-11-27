// The Kotlin plugin aliases here also lift the Kotlin Gradle plugin that AGP 9 bundles
// (2.2.10) to the version in the catalogue. Do not add org.jetbrains.kotlin.android:
// AGP 9 has Kotlin support built in and rejects that plugin.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
