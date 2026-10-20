plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose") // compiles @Composable functions
}

android {
    namespace = "io.github.aziztw.sacuma"
    compileSdk = 37    // API stubs we compile against (Compose 1.12 needs 37)

    defaultConfig {
        applicationId = "io.github.aziztw.sacuma"
        minSdk = 26        // oldest Android that may install the app
        targetSdk = 36     // Android behavior version we opt into
        versionCode = 1
        versionName = "0.1"

        ndk {
            // The Thor (and nearly every modern phone) is 64-bit ARM.
            abiFilters += "arm64-v8a"
        }
    }

    ndkVersion = "28.2.13676358"

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // The BOM pins every Compose library to versions that work together.
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    // ComponentActivity + setContent { } (hosts Compose in an Activity).
    implementation("androidx.activity:activity-compose:1.13.0")
}
