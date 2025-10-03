pluginManagement {
    includeBuild("../plugin") {
        repositories {
            mavenLocal()
            mavenCentral()
        }
    }
}

include("app")
