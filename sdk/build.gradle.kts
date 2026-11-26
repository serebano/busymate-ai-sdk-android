plugins { id("com.android.library") }
android {
    namespace = "ai.busymate.sdk"
    compileSdk = 35
    defaultConfig { minSdk = 23; consumerProguardFiles("consumer-rules.pro") }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    api("androidx.activity:activity:1.9.3")
    api("androidx.webkit:webkit:1.12.1")
    implementation("androidx.core:core:1.15.0")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    testImplementation("junit:junit:4.13.2")
}
