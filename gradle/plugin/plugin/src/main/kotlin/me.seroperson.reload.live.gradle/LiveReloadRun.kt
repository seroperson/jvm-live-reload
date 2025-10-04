package me.seroperson.reload.live.gradle

import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.deployment.internal.DeploymentHandle;
import org.gradle.deployment.internal.DeploymentRegistry;
import org.gradle.deployment.internal.DeploymentRegistry.ChangeBehavior;
import org.gradle.work.DisableCachingByDefault;
import org.gradle.work.Incremental;
import org.gradle.work.InputChanges;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Incubating
@DisableCachingByDefault(because = "Application should always run")
abstract class LiveReloadRun @Inject constructor(private val deploymentRegistry: DeploymentRegistry) : DefaultTask() {

    @get:Classpath
    abstract val runtimeClasspath: ConfigurableFileCollection

    @get:Incremental
    @get:Classpath
    abstract val classes: ConfigurableFileCollection

    @get:Input
    abstract val mainClass: Property<String>

    @get:Input
    abstract val settings: MapProperty<String, String>

    @get:Input
    abstract val startupHooks: ListProperty<String>

    @get:Input
    abstract val shutdownHooks: ListProperty<String>

    @get:Internal
    val isUpToDate: Boolean
        get() {
            val deploymentHandle = deploymentRegistry.get(path, DeploymentHandle::class.java)
            return deploymentHandle != null
        }

    @TaskAction
    fun run(changes: InputChanges) {
        val id = path
        val runHandle: LiveReloadRunHandle? = deploymentRegistry.get(id, LiveReloadRunHandle::class.java)
        if (runHandle == null) {
            val params = LiveReloadRunParams(
                this.runtimeClasspath.files,
                this.classes.files,
                this.settings.get(),
                this.mainClass.get(),
                this.startupHooks.get(),
                this.shutdownHooks.get()
            )
            deploymentRegistry.start(id, ChangeBehavior.BLOCK, LiveReloadRunHandle::class.java, params)
        } else {
            if (!changes.isIncremental) {
                logger.info("Reload application by no incremental changes")
            } else if (changes.getFileChanges(this.classes).iterator().hasNext()) {
                logger.info("Reload application by incremental changes in application classpath")
            } else {
                logger.info("Incremental changes in Assets")
            }
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(LiveReloadRun::class.java)
    }
}
