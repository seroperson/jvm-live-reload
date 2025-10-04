plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies { implementation(libs.jvm.live.reload.runner) }

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
