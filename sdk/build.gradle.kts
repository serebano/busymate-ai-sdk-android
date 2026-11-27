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
    // Busymate.unread is a StateFlow.
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation("junit:junit:4.13.2")
    // android.jar stubs org.json in JVM unit tests; the bridge schema tests need the real parser.
    testImplementation("org.json:json:20250517")
}
