plugins {
    alias(libs.plugins.kotlin.jvm)
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
