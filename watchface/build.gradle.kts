import java.util.Properties

plugins {
    id("com.android.application")
}

// Watch Face Format (WFF) watch face: resources only, no code.
// Play requires it to be a separate app from the Wear OS app, so it has its own applicationId.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "io.github.jamieharwood.moonphase.watchface"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.jamieharwood.moonphase.watchface"
        minSdk = 33 // WFF version 1 = Wear OS 4 (Galaxy Watch4 and newer)
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (keystoreProps.containsKey("storeFile")) create("upload") {
            storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            // Play rejects watch faces that contain any dex. AGP still compiles the R classes,
            // so let R8 strip them: with nothing to keep, the bundle ships without classes.dex.
            isMinifyEnabled = true
            isShrinkResources = false // the watch face XML references resources by name
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
        }
    }
}
