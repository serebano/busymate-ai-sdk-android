plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "ai.busymate.sample"
    compileSdk = 35
    defaultConfig { applicationId = "ai.busymate.sample"; minSdk = 23; targetSdk = 35; versionCode = 1; versionName = "1.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { implementation(project(":sdk")); testImplementation("junit:junit:4.13.2") }
