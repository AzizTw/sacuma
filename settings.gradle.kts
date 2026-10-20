// settings.gradle.kts — read first by Gradle. Says where to download plugins and
// libraries from, and which modules make up the project.

pluginManagement {
    repositories {
        google()            // Android Gradle Plugin lives here
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "sacuma"
include(":app")
