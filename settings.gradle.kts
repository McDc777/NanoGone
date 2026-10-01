pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        maven("https://repo1.maven.org/maven2")
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        maven("https://repo1.maven.org/maven2")
        mavenCentral()
    }
}
rootProject.name = "NanoGone"
include(":core:imaging")

// The Android app needs the Android SDK. Machines without it (like the Linux cloud box, where
// Google's servers are blocked) still build and test the pure Kotlin core.
val hasAndroidSdk = System.getenv("ANDROID_HOME") != null || System.getenv("ANDROID_SDK_ROOT") != null ||
    file("local.properties").let { it.exists() && it.readText().contains("sdk.dir") }
if (hasAndroidSdk) include(":app")
