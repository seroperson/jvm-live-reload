package me.seroperson.reload.live.gradle

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.ApplicationPlugin.APPLICATION_GROUP
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.plugins.JavaPlugin.CLASSES_TASK_NAME
import org.gradle.api.plugins.JavaPlugin.COMPILE_JAVA_TASK_NAME
import org.gradle.api.plugins.JavaPlugin.PROCESS_RESOURCES_TASK_NAME
import org.gradle.api.plugins.JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSet.MAIN_SOURCE_SET_NAME
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.language.jvm.tasks.ProcessResources
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class LiveReloadPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = createExtension(project)
        val liveReloadRuntime = createLiveReloadRuntimeConfiguration(project)
        createRunTask(project, extension)

        project.afterEvaluate {
            val dependency =
                when (extension.serverType.get()) {
                    ServerType.HTTP -> "me.seroperson:jvm-live-reload-webserver:${BuildConfig.VERSION}"
                    ServerType.GRPC -> "me.seroperson:jvm-live-reload-webserver-grpc:${BuildConfig.VERSION}"
                }
            project.dependencies.add(liveReloadRuntime.name, dependency)
            val liveReloadRun = project.tasks.getByName("liveReloadRun") as LiveReloadRun
            liveReloadRun.runtimeClasspath.from(liveReloadRuntime)
        }
    }

    private fun findClasspathDirectories(project: Project?): ConfigurableFileCollection? {
        if (project == null) return null
        val sourceSet = mainSourceSet(project)
        val processResources =
            project.tasks.findByName(PROCESS_RESOURCES_TASK_NAME) as ProcessResources?
        val compileJava = project.tasks.findByName(COMPILE_JAVA_TASK_NAME) as AbstractCompile?
        return project.files(
            processResources?.destinationDir,
            compileJava?.destinationDirectory,
            listOf("scala", "kotlin")
                .mapNotNull { source: String ->
                    sourceSet.extensions.findByName(source) as SourceDirectorySet?
                }.map { obj: SourceDirectorySet -> obj.classesDirectory },
        )
    }

    private fun createExtension(project: Project): LiveReloadExtension =
        project.extensions.create("liveReload", LiveReloadExtension::class.java)

    private fun createLiveReloadRuntimeConfiguration(project: Project): Configuration =
        project.configurations.create("liveReloadRuntime").apply {
            isCanBeConsumed = false
            isCanBeResolved = true
            isVisible = false
            description = "Task-local runtime for the live-reload proxy implementation."
        }

    private fun createRunTask(
        project: Project,
        extension: LiveReloadExtension,
    ) {
        project.tasks.register(
            "liveReloadRun",
            LiveReloadRun::class.java,
            object : Action<LiveReloadRun> {
                override fun execute(t: LiveReloadRun) {
                    t.description = "Runs the application with the live-reload wrapper."
                    t.group = APPLICATION_GROUP
                    t.dependsOn(project.tasks.findByName(CLASSES_TASK_NAME)!!)
                    t.outputs.upToDateWhen(Spec { task: Task -> (task as LiveReloadRun).isUpToDate })
                    val runtime = project.configurations.getByName(RUNTIME_CLASSPATH_CONFIGURATION_NAME)
                    val runtimeProjectClasspath =
                        runtime.incoming
                            .artifactView {
                                componentFilter { componentIdentifier ->
                                    componentIdentifier is ProjectComponentIdentifier
                                }
                            }.files
                    val runtimeDependencyClasspath =
                        runtime.incoming
                            .artifactView {
                                componentFilter { componentIdentifier ->
                                    componentIdentifier !is ProjectComponentIdentifier
                                }
                            }.files
                    t.classes.from(findClasspathDirectories(project))
                    t.classes.from(runtimeProjectClasspath)
                    t.classes.from(runtimeDependencyClasspath.filter { it.isDirectory })
                    t.settings.convention(extension.settings)
                    t.mainClass.convention(javaApplicationExtension(project).mainClass)
                    t.startupHooks.convention(extension.startupHooks)
                    t.shutdownHooks.convention(extension.shutdownHooks)
                    t.propagateEnv.convention(extension.propagateEnv)
                    t.serverType.convention(extension.serverType)

                    t.runtimeClasspath.from(runtimeDependencyClasspath.filter { it.isFile })
                }
            },
        )
    }

    private fun javaPluginExtension(project: Project): JavaPluginExtension = extensionOf(project, JavaPluginExtension::class.java)

    private fun javaApplicationExtension(project: Project): JavaApplication = extensionOf(project, JavaApplication::class.java)

    private fun mainSourceSet(project: Project): SourceSet = javaPluginExtension(project).sourceSets.getByName(MAIN_SOURCE_SET_NAME)

    private fun <T : Any> extensionOf(
        extensionAware: ExtensionAware,
        type: Class<T>,
    ): T = extensionAware.extensions.getByType<T>(type)!!

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(LiveReloadPlugin::class.java)
    }
}
