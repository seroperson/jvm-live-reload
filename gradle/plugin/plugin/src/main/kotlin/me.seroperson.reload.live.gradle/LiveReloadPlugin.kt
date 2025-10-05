package me.seroperson.reload.live.gradle

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencyResolutionListener
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.artifacts.component.ComponentIdentifier
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

class LiveReloadPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = createExtension(project)
        createRunTask(project, extension)

        val runtimeDeps = project.configurations.getByName("runtimeOnly").dependencies
        project.gradle.addListener(
            object : DependencyResolutionListener {
                override fun beforeResolve(resolvableDependencies: ResolvableDependencies) {
                    runtimeDeps.add(
                        project.dependencies.create("me.seroperson:jvm-live-reload-webserver:0.0.1"),
                    )
                    project.gradle.removeListener(this)
                }

                override fun afterResolve(resolvableDependencies: ResolvableDependencies) {}
            },
        )
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
                    t.classes.from(findClasspathDirectories(project))
                    t.settings.convention(extension.settings)
                    t.mainClass.convention(javaApplicationExtension(project).mainClass)
                    t.startupHooks.convention(extension.startupHooks)
                    t.shutdownHooks.convention(extension.shutdownHooks)

                    val runtime = project.configurations.getByName(RUNTIME_CLASSPATH_CONFIGURATION_NAME)
                    t.runtimeClasspath.from(runtime.incoming.files)

                    filterProjectComponents(runtime).forEach { path ->
                        val child = project.findProject(path) ?: return@forEach
                        t.classes.from(findClasspathDirectories(child))
                        t.dependsOn(child.tasks.findByName(PROCESS_RESOURCES_TASK_NAME)!!)
                    }
                }
            },
        )
    }

    private fun isProjectComponent(component: ComponentIdentifier): Boolean = component is ProjectComponentIdentifier

    private fun filterProjectComponents(configuration: Configuration): List<String> =
    /*configuration.incoming
    .artifactView(
        object : Action<ArtifactView.ViewConfiguration> {
            override fun execute(t: ArtifactView.ViewConfiguration) {
                t.componentFilter { value -> isProjectComponent(value) }
            }
        },
    ).artifacts
    .map { (it.variant.owner as ProjectComponentIdentifier).projectPath }*/
        listOf()

    private fun javaPluginExtension(project: Project): JavaPluginExtension = extensionOf(project, JavaPluginExtension::class.java)

    private fun javaApplicationExtension(project: Project): JavaApplication = extensionOf(project, JavaApplication::class.java)

    private fun mainSourceSet(project: Project): SourceSet = javaPluginExtension(project).sourceSets.getByName(MAIN_SOURCE_SET_NAME)

    private fun <T : Any> extensionOf(
        extensionAware: ExtensionAware,
        type: Class<T>,
    ): T = extensionAware.extensions.getByType<T>(type)!!
}
