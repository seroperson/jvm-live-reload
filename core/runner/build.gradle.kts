plugins {
    id("core-java-library")
}

dependencies {
    api(project(":core:build-link"))
    api("org.playframework:play-file-watch:3.0.0-M4")
    implementation("org.jline:jline:3.30.6")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // ShutdownHook reflection needs java.lang to be open; matches CI's JDK_JAVA_OPTIONS.
    jvmArgs(
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
    )
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
