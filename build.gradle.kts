plugins {
    // Declare plugin versions here so subprojects can apply them without specifying versions
    id("com.android.application") version "8.4.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}

// Common tasks
import org.gradle.api.tasks.Delete

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
