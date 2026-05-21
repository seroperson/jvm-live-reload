plugins {
    id("core-java-library")
}

dependencies {
    api(project(":core:build-link"))
    api("org.playframework:play-file-watch:3.0.0-M4")
    implementation("org.jline:jline:3.30.6")
}

publishing {
    publications {
        named<MavenPublication>("mavenJava") {
            artifactId = "jvm-live-reload-runner"
            pom {
                name = "jvm-live-reload-runner"
                description = "Contains an universal Live Reload webserver initialization and reloading logic"
            }
        }
    }
}
