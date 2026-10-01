pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo1.maven.org/maven2")
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories {
        maven("https://repo1.maven.org/maven2")
        mavenCentral()
    }
}
rootProject.name = "NanoGone"
include(":core:imaging")
