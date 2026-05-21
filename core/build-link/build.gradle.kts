plugins {
    id("core-java-library")
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
