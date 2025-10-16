package me.seroperson.reload.live.gradle

import okhttp3.OkHttpClient
import okhttp3.Request
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class LiveReloadHttp4kTest {

    @field:TempDir/*(cleanup = CleanupMode.ON_SUCCESS)*/
    lateinit var projectDir: File

    private val appCode by lazy {
        val kotlinSources = projectDir.resolve("src/main/kotlin")
        kotlinSources.mkdirs()
        kotlinSources.resolve("App.kt")
    }
    private val buildFile by lazy {
        projectDir.resolve("build.gradle.kts")
    }
    private val settingsFile by lazy { projectDir.resolve("settings.gradle.kts") }
    private val gradlePropertiesFile by lazy { projectDir.resolve("gradle.properties") }

    private val client = OkHttpClient()

    private fun runUntil(url: String, expectedStatus: Int, expectedBody: String): Boolean {
        val request: Request = Request.Builder()
            .url(url)
            .build()

        try {
            val (code, body) = (client.newCall(request).execute().use { response ->
                response.code to response.body.string()
            })
            println("Requesting ${url}, got $code and $body")
            if(expectedStatus == code && expectedBody == body) {
                return true
            } else {
                Thread.sleep(500)
                return runUntil(url, expectedStatus, expectedBody)
            }
        } catch (x: Exception) {
            println("Got exception: ${x.message}")
            Thread.sleep(500)
            return runUntil(url, expectedStatus, expectedBody)
        }
    }

    @Test
    fun `reload http4k`() {
        gradlePropertiesFile.writeText("")
        settingsFile.writeText("")
        buildFile.writeText(
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

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

liveReload { settings = mapOf("live.reload.http.port" to "8081") }

application { mainClass = "AppKt" }
"""
        )

        appCode.writeText(
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
        )

        val runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withProjectDir(projectDir)
        runner.withArguments(":liveReloadRun")
        val runThread = Thread {
            try {
                runner.build()
            } catch (ex: InterruptedException) {
                println("Interrupted")
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
        runThread.start()

        val greet = runUntil("http://localhost:9000/greet", 200, "Hello World")

        appCode.writeText(
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
        )

        val greetReloaded = runUntil("http://localhost:9000/greet_reloaded", 200, "World Hello")

        runThread.interrupt()
        runThread.join()

        assertTrue(greet && greetReloaded)
    }
}
