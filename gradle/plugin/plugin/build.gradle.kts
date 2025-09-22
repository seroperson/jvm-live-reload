plugins {
    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

group = "me.seroperson"
version = "0.0.1-SNAPSHOT"

gradlePlugin {
    plugins {
        create("me.seroperson.reload.live.gradle") {
            id = "me.seroperson.reload.live.gradle"
            implementationClass= "me.seroperson.reload.live.gradle.LiveReloadPlugin"
        }
    }
}