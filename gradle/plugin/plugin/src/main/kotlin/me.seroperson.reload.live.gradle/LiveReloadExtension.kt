package me.seroperson.reload.live.gradle

import org.gradle.api.Project
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property;

abstract class LiveReloadExtension(project: Project) {

    abstract val mainClass: Property<String>
    abstract val settings: MapProperty<String, String>
    abstract val startupHooks: ListProperty<String>
    abstract val shutdownHooks: ListProperty<String>

    init {
        settings.convention(mapOf())
        startupHooks.convention(listOf("me.seroperson.reload.live.hook.RestApiHealthCheckStartupHook"))
        shutdownHooks.convention(listOf(
            "me.seroperson.reload.live.hook.ThreadInterruptShutdownHook",
            "me.seroperson.reload.live.hook.RuntimeShutdownHook",
            "me.seroperson.reload.live.hook.RestApiHealthCheckShutdownHook"
        ))
    }
}
