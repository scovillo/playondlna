import java.io.File

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"
}

allprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        debug.set(false)
        verbose.set(true)
        android.set(true)
        outputToConsole.set(true)
        ignoreFailures.set(false)
        filter {
            include("**/*.kt")
            include("**/*.kts")
            exclude { it.file.path.contains("build/") }
            exclude {
                it.file.path.startsWith(".") || it.file.path.contains(File.separator + ".")
            }
        }
    }
}
