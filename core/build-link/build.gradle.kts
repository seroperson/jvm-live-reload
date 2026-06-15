plugins {
    id("core-java-library")
}

dependencies {
    implementation("io.grpc:grpc-netty-shaded:1.60.1")
    implementation("io.grpc:grpc-stub:1.60.1")
    implementation("io.grpc:grpc-services:1.60.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
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
