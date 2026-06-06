plugins {
    // Make the Android and Kotlin plugins available to subprojects without applying them to the root.
    id("com.android.application") version "8.3.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.10" apply false
}

subprojects {
    repositories {
        google()
        mavenCentral()
    }
}
