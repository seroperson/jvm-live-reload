package me.seroperson.reload.live.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test

@Timeout(value = 5, unit = TimeUnit.MINUTES)
class LiveReloadDependencyLeakTest : LiveReloadTestBase() {
    @field:TempDir lateinit var projectDir: File

    private val buildFile by lazy { projectDir.resolve("build.gradle.kts") }
    private val settingsFile by lazy { projectDir.resolve("settings.gradle.kts") }
    private val appCode by lazy {
        val javaSources = projectDir.resolve("src/main/java/example")
        javaSources.mkdirs()
        javaSources.resolve("App.java")
    }

    @Test
    fun `plugin does not leak live reload runtime into app runtime classpath or pom`() {
        settingsFile.writeText("rootProject.name = \"dependency-leak-test\"")
        buildFile.writeText(BUILD_CONTENT)
        appCode.writeText(APP_CODE)

        val dependenciesResult =
            initGradleRunner("dependencies", projectDir)
                .withArguments("dependencies", "--configuration", "runtimeClasspath")
                .build()
        val runtimeOutput = dependenciesResult.output

        assertFalse(
            runtimeOutput.contains("me.seroperson:jvm-live-reload-webserver"),
            "runtimeClasspath should not contain the HTTP live-reload proxy",
        )
        assertFalse(
            runtimeOutput.contains("me.seroperson:jvm-live-reload-webserver-grpc"),
            "runtimeClasspath should not contain the gRPC live-reload proxy",
        )

        val pomResult =
            initGradleRunner("generatePomFileForMavenJavaPublication", projectDir)
                .withArguments("generatePomFileForMavenJavaPublication")
                .build()
        val pomFile = projectDir.resolve("build/publications/mavenJava/pom-default.xml")
        val pom = pomFile.readText()

        assertFalse(
            pom.contains("jvm-live-reload-webserver"),
            "published POM should not contain the HTTP live-reload proxy",
        )
        assertFalse(
            pom.contains("jvm-live-reload-webserver-grpc"),
            "published POM should not contain the gRPC live-reload proxy",
        )
        kotlin.test.assertEquals(
            TaskOutcome.SUCCESS,
            pomResult.task(":generatePomFileForMavenJavaPublication")?.outcome,
        )
    }

    companion object {
        const val BUILD_CONTENT =
            """
plugins {
    java
    application
    `maven-publish`
    id("me.seroperson.reload.live.gradle")
}

group = "example"
version = "1.0.0"

repositories {
    mavenLocal()
    mavenCentral()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}

application { mainClass = "example.App" }
"""

        const val APP_CODE =
            """
package example;

public class App {}
"""
    }
}
