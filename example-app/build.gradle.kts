plugins { id("com.android.application"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "ai.busymate.sample"
    compileSdk = 35
    defaultConfig { applicationId = "ai.busymate.sample"; minSdk = 23; targetSdk = 35; versionCode = 1; versionName = "1.0"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies { implementation(project(":sdk")); implementation(project(":sdk-compose")); implementation("androidx.activity:activity-compose:1.9.3"); testImplementation("junit:junit:4.13.2"); androidTestImplementation("androidx.test:runner:1.6.2"); androidTestImplementation("androidx.test.ext:junit:1.2.1"); androidTestImplementation("androidx.test.uiautomator:uiautomator:2.4.1") }
