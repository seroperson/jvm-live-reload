plugins { alias(libs.plugins.spotless) }

repositories { mavenCentral() }

spotless {
    kotlin {
        target("**/kotlin/**/*.kt")
        ktfmt().googleStyle()
        ktlint()
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        ktfmt().googleStyle()
        ktlint()
    }
}
