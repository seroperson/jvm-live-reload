plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

dependencies {
    implementation("me.seroperson:jvm-live-reload-runner:0.0.1")
}

group = "me.seroperson"
version = "0.0.1-SNAPSHOT"

gradlePlugin {
    plugins {
        create("me.seroperson.reload.live.gradle") {
            id = "me.seroperson.reload.live.gradle"
            implementationClass = "me.seroperson.reload.live.gradle.LiveReloadPlugin"
        }
    }
}