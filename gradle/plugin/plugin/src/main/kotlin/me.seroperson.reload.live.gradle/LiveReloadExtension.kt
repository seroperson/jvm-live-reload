package me.seroperson.reload.live.gradle

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property;

interface LiveReloadExtension {

    val mainClass: Property<String>
    val startupHooks: ListProperty<String>
    val shutdownHooks: ListProperty<String>

}
