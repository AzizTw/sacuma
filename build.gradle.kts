// Root build file: declares plugin versions once; modules (app/) apply them.
// AGP 9 has Kotlin support built in, so no separate Kotlin plugin is needed.
plugins {
    id("com.android.application") version "9.4.1" apply false
}
