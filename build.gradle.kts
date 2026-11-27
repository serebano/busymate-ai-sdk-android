buildscript {
    // AGP 9 built-in Kotlin: the Kotlin Gradle plugin version is pinned here, same as busymate-ai-app-android.
    dependencies { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21") }
}
plugins {
    id("com.android.library") version "9.5.3" apply false
    id("com.android.application") version "9.5.3" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
