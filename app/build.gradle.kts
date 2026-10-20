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
        // CI passes these from the git tag (v1.2.3 -> "1.2.3") and the run number.
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("versionName") as String?) ?: "0.1.0-dev"

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

    signingConfigs {
        // Release key comes from environment variables (GitHub Actions secrets);
        // it is never stored in the repository.
        create("release") {
            System.getenv("SACUMA_KEYSTORE")?.let { path ->
                storeFile = file(path)
                storePassword = System.getenv("SACUMA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SACUMA_KEY_ALIAS")
                keyPassword = System.getenv("SACUMA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8: drop unused code and resources for a much smaller APK.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Without the release key (local builds), sign with the debug key so the
            // APK can still be installed for testing.
            signingConfig = if (System.getenv("SACUMA_KEYSTORE") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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
