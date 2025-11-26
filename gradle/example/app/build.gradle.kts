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
    implementation("org.slf4j:slf4j-simple:2.0.16")

    implementation(platform("org.http4k:http4k-bom:6.18.1.0"))
    implementation("org.http4k:http4k-core")

    implementation("io.ktor:ktor-server-core-jvm:3.3.0")
    implementation("io.ktor:ktor-server-netty:3.3.0")

    implementation("io.javalin:javalin:6.7.0")

    implementation(project(":subproject"))
}

liveReload { settings = mapOf("live.reload.http.port" to "8081") }

application { mainClass = "AppHttp4kKt" }
