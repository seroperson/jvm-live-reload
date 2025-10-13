pluginManagement {
    includeBuild("../plugin") {
        repositories {
            maven("https://central.sonatype.com/repository/maven-snapshots/")
            mavenLocal()
            mavenCentral()
        }
    }
}

include("app")
