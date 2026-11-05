// The Windows app is its own Gradle build, so the Android build is never touched by it.
// Run with the repository's wrapper: ../gradlew -p desktop run
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}
rootProject.name = "NeonTokyoDesktop"
