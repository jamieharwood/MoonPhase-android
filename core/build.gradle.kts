plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// Shared by the phone (:app) and watch (:wear) apps: astronomy maths and live-data feeds.
android {
    namespace = "io.github.jamieharwood.moonphase.core"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

kotlin { jvmToolchain(21) }

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303") // real org.json for JVM unit tests
}
