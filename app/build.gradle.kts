plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.aziztw.sacuma"
    compileSdk = 36                     // API stubs we compile against

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
}
