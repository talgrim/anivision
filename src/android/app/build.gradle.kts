import java.io.StringReader
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// release-please bumps this line; versionCode is derived so it always grows with the version.
val appVersion = "1.2.0" // x-release-please-version
val appVersionCode = appVersion.split(".").map { it.toInt() }.let { (major, minor, patch) -> major * 10000 + minor * 100 + patch }

// Release signing secrets live in src/android/keystore.properties (git-ignored, see RELEASING.md).
// Without that file assembleRelease still works but leaves the APK unsigned; bundleRelease (the
// Play Store upload) fails instead, so an unsigned bundle cannot be produced by accident.
val keystoreProperties: Properties? = providers
    .fileContents(rootProject.layout.projectDirectory.file("keystore.properties"))
    .asText
    .map { text -> Properties().apply { load(StringReader(text)) } }
    .orNull

gradle.taskGraph.whenReady {
    if (keystoreProperties == null && hasTask(":app:bundleRelease")) {
        throw GradleException(
            "bundleRelease needs release signing: create src/android/keystore.properties " +
                "(copy keystore.properties.example, see RELEASING.md).",
        )
    }
}

android {
    namespace = "com.anivision.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.anivision.app"
        minSdk = 29
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        keystoreProperties?.let { props ->
            create("release") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // R8 shrinks and obfuscates the release build. The Kotlin stdlib's names are kept (see
            // src/main/keepRules/rules.keep): without that the APK crashed at start-up, because R8 moved
            // the stdlib's CollectionsKt__* classes into different packages (IllegalAccessError). If a
            // signed release build misbehaves, set enable = false and say so in RELEASING.md.
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    sourceSets {
        // The prototype's exported golden vectors, read by the on-device pipeline test.
        getByName("androidTest").assets.directories.add("../../prototype/export")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}