plugins {
    id("core-java-library")
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        named<MavenPublication>("mavenJava") {
            artifactId = "jvm-live-reload-build-link"
            pom {
                name = "jvm-live-reload-build-link"
                description = "Contains classes which shared between build system and application runtime"
            }
        }
    }
}
