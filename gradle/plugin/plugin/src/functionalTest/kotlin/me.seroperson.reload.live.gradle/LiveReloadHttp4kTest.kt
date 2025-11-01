package me.seroperson.reload.live.gradle

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test

@Timeout(value = 5, unit = TimeUnit.MINUTES)
class LiveReloadHttp4kTest : LiveReloadTestBase() {
    @field:TempDir lateinit var projectDir: File

    private val appCode by lazy {
        val kotlinSources = projectDir.resolve("src/main/kotlin")
        kotlinSources.mkdirs()
        kotlinSources.resolve("App.kt")
    }
    private val buildFile by lazy { projectDir.resolve("build.gradle.kts") }
    private val settingsFile by lazy { projectDir.resolve("settings.gradle.kts") }

    @Test
    fun `reload http4k`() {
        settingsFile.writeText(SETTINGS_CONTENT)
        buildFile.writeText(BUILD_CONTENT)
        appCode.writeText(APP_CODE_1)

        val runner = GradleRunner.create()
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
                    println("Got exception ${ex.message}")
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
    implementation(platform("org.http4k:http4k-bom:6.18.1.0"))
    implementation("org.http4k:http4k-core")
}

liveReload { settings = mapOf("live.reload.http.port" to "8081") }

application { mainClass = "AppKt" }
"""
        const val APP_CODE_1 =
            """
import org.http4k.core.*
import org.http4k.core.Status.Companion.OK
import org.http4k.routing.*
import org.http4k.server.*

fun main() {
    val endpoints =
        listOf(
            "/greet" bind Method.GET to { Response(OK).body("Hello World") },
            "/health" bind Method.GET to { Response(OK) },
        )
    routes(endpoints).asServer(SunHttp(8081)).use {
        it.start()
        it.block()
    }
}
"""
        const val APP_CODE_2 =
            """
import org.http4k.core.*
import org.http4k.core.Status.Companion.OK
import org.http4k.routing.*
import org.http4k.server.*

fun main() {
    val endpoints =
        listOf(
            "/greet_reloaded" bind Method.GET to { Response(OK).body("World Hello") },
            "/health" bind Method.GET to { Response(OK) },
        )
    routes(endpoints).asServer(SunHttp(8081)).use {
        it.start()
        it.block()
    }
}
"""
    }
}
