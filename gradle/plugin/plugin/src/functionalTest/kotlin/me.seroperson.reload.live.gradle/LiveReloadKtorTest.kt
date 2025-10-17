package me.seroperson.reload.live.gradle

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class LiveReloadKtorTest : LiveReloadTestBase() {
    @field:TempDir lateinit var projectDir: File

    private val appCode by lazy {
        val kotlinSources = projectDir.resolve("src/main/kotlin")
        kotlinSources.mkdirs()
        kotlinSources.resolve("App.kt")
    }
    private val buildFile by lazy { projectDir.resolve("build.gradle.kts") }
    private val settingsFile by lazy { projectDir.resolve("settings.gradle.kts") }

    @Test
    fun `reload ktor`() {
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
    implementation("io.ktor:ktor-server-core-jvm:3.3.0")
    implementation("io.ktor:ktor-server-netty:3.3.0")
}

liveReload { settings = mapOf("live.reload.http.port" to "8081") }

application { mainClass = "AppKt" }
"""
        const val APP_CODE_1 =
            """
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.*

fun main() {
    embeddedServer(Netty, port = 8081) {
        routing {
            get("/greet") {
                call.respondText("Hello World")
            }
            get("/health") {
                call.respond(HttpStatusCode.OK, null)
            }
        }
    }.start(wait = true)
}
"""
        const val APP_CODE_2 =
            """
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.*

fun main() {
    embeddedServer(Netty, port = 8081) {
        routing {
            get("/greet_reloaded") {
                call.respondText("World Hello")
            }
            get("/health") {
                call.respond(HttpStatusCode.OK, null)
            }
        }
    }.start(wait = true)
}
"""
    }
}
