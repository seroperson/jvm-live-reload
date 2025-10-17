package me.seroperson.reload.live.gradle

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class LiveReloadJavalinTest : LiveReloadTestBase() {
    @field:TempDir lateinit var projectDir: File

    private val appCode by lazy {
        val kotlinSources = projectDir.resolve("src/main/kotlin")
        kotlinSources.mkdirs()
        kotlinSources.resolve("App.kt")
    }
    private val buildFile by lazy { projectDir.resolve("build.gradle.kts") }
    private val settingsFile by lazy { projectDir.resolve("settings.gradle.kts") }

    @Test
    fun `reload javalin`() {
        settingsFile.writeText(SETTINGS_CONTENT)
        buildFile.writeText(BUILD_CONTENT)
        appCode.writeText(APP_CODE_1)

        val runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withProjectDir(projectDir)
        runner.withArguments(":liveReloadRun")
        val runThread =
            Thread {
                try {
                    runner.build()
                } catch (_: InterruptedException) {
                    println("Interrupted")
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        runThread.start()

        val greet = runUntil("http://localhost:9000/greet", 200, "Hello World")

        appCode.writeText(APP_CODE_2)

        val greetReloaded = runUntil("http://localhost:9000/greet_reloaded", 200, "World Hello")

        runThread.interrupt()

        assertTrue(greet && greetReloaded)
    }

    companion object {
        const val SETTINGS_CONTENT = ""
        const val BUILD_CONTENT =
            """
plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    application
    id("me.seroperson.reload.live.gradle")
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("io.javalin:javalin:6.7.0")
}

liveReload { settings = mapOf("live.reload.http.port" to "8081") }

application { mainClass = "AppKt" }
"""
        const val APP_CODE_1 =
            """
import io.javalin.Javalin

fun main() {
    val server = Javalin.create()
        .get("/greet") {
            it.result("Hello World")
        }
        .get("/health") {
            it.status(200)
        }
    try {
        server.start(8081)
        Thread.currentThread().join()
    } catch (ex: InterruptedException) {
        server.stop()
    }
}
"""
        const val APP_CODE_2 =
            """
import io.javalin.Javalin

fun main() {
    val server = Javalin.create()
        .get("/greet_reloaded") {
            it.result("World Hello")
        }
        .get("/health") {
            it.status(200)
        }
    try {
        server.start(8081)
        Thread.currentThread().join()
    } catch (ex: InterruptedException) {
        server.stop()
    }
}
"""
    }
}
