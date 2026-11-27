import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Signing: one key for debug and release, so every build replaces the installed one without
// losing the assistant role, permissions or the voice-command default.
// Local builds read .local/keystore/keystore.properties (never committed); CI uses LARA_* variables.
val keystoreProps = Properties().apply {
    val file = rootProject.file(".local/keystore/keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(name: String): String? =
    keystoreProps.getProperty(name) ?: System.getenv("LARA_" + name.uppercase())

val laraVersionName = (findProperty("lara.versionName") as String?) ?: "0.1.0-dev"

// versionCode = number of commits. Android refuses to install a lower code over a higher one;
// counting commits makes every later build installable over every earlier one, whether it was
// built here, in CI or for a release (.github/workflows/release.yml uses the same rule).
val commitCount: Int? = runCatching {
    providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }
        .standardOutput.asText.get().trim().toInt()
}.getOrNull()
val laraVersionCode = (findProperty("lara.versionCode") as String?)?.toInt() ?: commitCount ?: 1

// 36 keeps Android 17's background-audio rules at the lenient level. Build with
// -Plara.targetSdk=37 to test the strict level.
val laraTargetSdk = ((findProperty("lara.targetSdk") as String?) ?: "36").toInt()

android {
    namespace = "io.github.domicc.lara"

    // Compose 1.13 needs SDK 37.1 or newer; plain "37" would mean 37.0.
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "io.github.domicc.lara"
        minSdk = 36
        targetSdk = laraTargetSdk
        versionCode = laraVersionCode
        versionName = laraVersionName
    }

    signingConfigs {
        create("lara") {
            val store = signingValue("storeFile")
            if (store != null) {
                storeFile = rootProject.file(store)
                storePassword = signingValue("storePassword")
                keyAlias = signingValue("keyAlias")
                keyPassword = signingValue("keyPassword")
            }
        }
    }

    buildTypes {
        val signing =
            if (signingValue("storeFile") != null) signingConfigs.getByName("lara")
            else signingConfigs.getByName("debug")
        getByName("debug") {
            signingConfig = signing
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signing
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
}
