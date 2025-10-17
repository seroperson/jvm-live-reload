plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "2.0.0"
    id("com.github.gmazzo.buildconfig") version "5.7.0"
}

repositories {
    maven("https://central.sonatype.com/repository/maven-snapshots/")
    mavenLocal()
    mavenCentral()
}

dependencies { implementation("me.seroperson:jvm-live-reload-runner:${readVersion()}") }

fun readVersion() =
    listOf(
        "$projectDir/version.txt",
        "$projectDir/../version.txt",
        "$projectDir/../../version.txt",
        "$projectDir/../../../version.txt",
    ).map { file(it) }
        .firstOrNull { it.exists() }
        ?.readText() ?: "0.0.1-SNAPSHOT"

group = "me.seroperson"

version = readVersion()

buildConfig {
    packageName("me.seroperson.reload.live.gradle")
    buildConfigField("VERSION", readVersion())
}

testing {
    suites {
        // Create a new test suite
        val functionalTest by
            registering(JvmTestSuite::class) {
                // Use Kotlin Test test framework
                useKotlinTest("2.2.0")

                dependencies {
                    // functionalTest test suite depends on the production code in tests
                    implementation(project())
                    implementation("com.squareup.okhttp3:okhttp:5.2.1")
                }
            }
    }
}

gradlePlugin.testSourceSets.add(sourceSets["functionalTest"])

tasks.withType<Test> {
    maxParallelForks = 1
    forkEvery = 1
}

// https://github.com/gradle/gradle/issues/5431
tasks.withType<Test> {
    addTestOutputListener { descriptor, event -> println("$descriptor: ${event.message}") }
}

tasks.named<Task>("check") {
    // Include functionalTest as part of the check lifecycle
    dependsOn(testing.suites.named("functionalTest"))
}

gradlePlugin {
    website = "https://github.com/seroperson/jvm-live-reload"
    vcsUrl = "https://github.com/seroperson/jvm-live-reload"
    plugins {
        create("liveReloadPlugin") {
            id = "me.seroperson.reload.live.gradle"
            displayName = "Live Reload for Web Applications"
            description =
                "Provides an universal Live Reload experience for web applications built with Gradle"
            tags = listOf("liveReload", "hotReload", "reload")
            implementationClass = "me.seroperson.reload.live.gradle.LiveReloadPlugin"
        }
    }
}
