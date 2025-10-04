package me.seroperson.reload.live.gradle

import java.io.File

class LiveReloadRunParams(
    val dependencyClasspath: Set<File>,
    val applicationClasspath: Set<File>,
    val settings: Map<String, String>,
    val mainClass: String,
    val startupHooks: List<String>,
    val shutdownHooks: List<String>,
)
