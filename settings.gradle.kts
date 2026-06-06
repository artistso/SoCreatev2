pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        // Add the Android Gradle Plugin and Kotlin Android plugin versions here so the plugins DSL can resolve them.
        id("com.android.application") version "8.1.2" apply false
        id("com.android.library")     version "8.1.2" apply false
        id("org.jetbrains.kotlin.android") version "1.9.10" apply false
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SoCreate"
include(":app")
