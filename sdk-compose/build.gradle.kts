// Optional Jetpack Compose components for the follow-ups channel (BusymateLauncherButton, BusymateTeaserBubble).
// Kept apart from :sdk so an app that does not use Compose takes on no Compose dependency.
plugins { id("com.android.library"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "ai.busymate.sdk.compose"
    compileSdk = 35
    defaultConfig { minSdk = 23 }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    // In this repository the core module is :sdk; a host that vendors it names it :busymate-sdk (README).
    api(project(if (findProject(":busymate-sdk") != null) ":busymate-sdk" else ":sdk"))
    api(platform("androidx.compose:compose-bom:2026.11.01"))
    api("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
}
