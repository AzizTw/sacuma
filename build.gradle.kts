// Root build file: declares plugin versions once; modules (app/) apply them.
// AGP 9 has Kotlin support built in. Listing the Kotlin plugin here only raises
// the Kotlin version it uses (default 2.2.10); the Compose compiler must match it.
plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
