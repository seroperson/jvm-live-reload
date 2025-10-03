package me.seroperson.reload.live.gradle

import java.io.File

class LiveReloadRunParams(
    val dependencyClasspath: Set<File>,
    val applicationClasspath: Set<File>,
    val devSettings: Map<String, String>,
    val mainClass: String
)