package me.seroperson.reload.live.gradle

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test

@Timeout(value = 5, unit = TimeUnit.MINUTES)
class LiveReloadMultiprojectTest : LiveReloadTestBase() {
    @field:TempDir lateinit var projectDir: File

    private val appCode by lazy {
        val kotlinSources = projectDir.resolve("project-a/src/main/kotlin")
        kotlinSources.mkdirs()
        kotlinSources.resolve("App.kt")
    }
    private val textCode by lazy {
        val kotlinSources = projectDir.resolve("project-b/src/main/kotlin")
        kotlinSources.mkdirs()
        kotlinSources.resolve("Text.kt")
    }
    private val buildAFile by lazy { projectDir.resolve("project-a/build.gradle.kts") }
    private val buildBFile by lazy { projectDir.resolve("project-b/build.gradle.kts") }
    private val settingsFile by lazy { projectDir.resolve("settings.gradle.kts") }

    @Test
    fun `reload multiproject ktor`() {
        appCode.writeText(APP_CODE_1)
        textCode.writeText(TEXT_CODE_1)

        settingsFile.writeText(SETTINGS_CONTENT)
        buildAFile.writeText(BUILD_A_CONTENT)
        buildBFile.writeText(BUILD_B_CONTENT)

        val runner = GradleRunner.create()
        runner.withPluginClasspath()
        runner.withProjectDir(projectDir)
        runner.withArguments(":project-a:liveReloadRun")
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
        textCode.writeText(TEXT_CODE_2)

        val greetReloaded = runUntil("http://localhost:9000/greet_reloaded", 200, "World Hello!")

        runThread.interrupt()

        assertTrue(greet && greetReloaded)
    }

    companion object {
        const val SETTINGS_CONTENT =
            """
include("project-a", "project-b")
"""

        const val BUILD_CONTENT =
            """
plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.0" apply false
}
"""
        const val BUILD_A_CONTENT =
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
    implementation(project(":project-b"))
}

liveReload { settings = mapOf("live.reload.http.port" to "8081") }

application { mainClass = "AppKt" }
"""

        const val BUILD_B_CONTENT =
            """
plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
}

repositories {
    mavenCentral()
}
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
                call.respondText(Text.response)
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
                call.respondText(Text.response + "!")
            }
            get("/health") {
                call.respond(HttpStatusCode.OK, null)
            }
        }
    }.start(wait = true)
}
"""

        const val TEXT_CODE_1 =
            """
class Text {
    companion object {
        const val response = "Hello World"
    }
}
"""

        const val TEXT_CODE_2 =
            """
class Text {
    companion object {
        const val response = "World Hello"
    }
}
"""
    }
}
