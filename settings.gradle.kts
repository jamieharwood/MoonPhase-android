pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    // Lets Gradle download a JDK 21 if the machine only has a JRE (or no JDK 21).
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "MoonPhase"
include(":app", ":core", ":wear", ":watchface")
