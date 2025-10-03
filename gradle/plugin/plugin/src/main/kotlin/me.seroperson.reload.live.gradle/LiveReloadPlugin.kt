package me.seroperson.reload.live.gradle

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.*
import org.gradle.api.plugins.ApplicationPlugin.APPLICATION_GROUP
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.plugins.JavaPlugin.*
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
            listOf("scala", "kotlin").mapNotNull { source: String ->
                sourceSet.extensions.findByName(source) as SourceDirectorySet?
            }
                .map { obj: SourceDirectorySet -> obj.classesDirectory }
        )
    }

    private fun createExtension(project: Project): LiveReloadExtension {
        return project.extensions.create("liveReload", LiveReloadExtension::class.java)
    }

    private fun createRunTask(project: Project, extension: LiveReloadExtension) {
        project
            .tasks
            .register(
                "liveReloadRun",
                LiveReloadRun::class.java,
                object: Action<LiveReloadRun> {
                    override fun execute(t: LiveReloadRun) {
                        t.description = "Runs the application with the live-reload wrapper."
                        t.group = APPLICATION_GROUP
                        t.dependsOn(project.tasks.findByName(CLASSES_TASK_NAME)!!)
                        t.outputs.upToDateWhen(Spec { task: Task -> (task as LiveReloadRun).isUpToDate })
                        t.workingDir.convention(project.layout.projectDirectory)
                        t.classes.from(findClasspathDirectories(project))
                        t.devSettings.convention(
                                project.objects
                                    .mapProperty(String::class.java, String::class.java)
                            )
                        t.mainClass.convention(javaApplicationExtension(project).mainClass)

                        val runtime = project.configurations.getByName(RUNTIME_CLASSPATH_CONFIGURATION_NAME)
                        t.runtimeClasspath.from(runtime.incoming.files)

                        filterProjectComponents(runtime)
                            .forEach { path ->
                                val child = project.findProject(path) ?: return@forEach
                                t.classes.from(findClasspathDirectories(child))
                                t.dependsOn(child.tasks.findByName(PROCESS_RESOURCES_TASK_NAME)!!)
                            }
                    }
                }
            )
    }

    fun isProjectComponent(component: ComponentIdentifier): Boolean {
        return component is ProjectComponentIdentifier
    }

    fun filterProjectComponents(configuration: Configuration): List<String> {
        return synchronized(this) {
            configuration
                .incoming
                .artifactView(object : Action<ArtifactView.ViewConfiguration> {
                    override fun execute(t: ArtifactView.ViewConfiguration) {
                        t.componentFilter { value -> isProjectComponent(value) }
                    }
                })
                .artifacts
            listOf()
                /*.map {
                    (it.variant.owner as ProjectComponentIdentifier)
                        .projectPath
                }*/
        }
    }

    fun javaPluginExtension(project: Project): JavaPluginExtension {
        return extensionOf(project, JavaPluginExtension::class.java)
    }

    fun javaApplicationExtension(project: Project): JavaApplication {
        return extensionOf(project, JavaApplication::class.java)
    }

    fun mainSourceSet(project: Project): SourceSet {
        return javaPluginExtension(project).sourceSets.getByName(MAIN_SOURCE_SET_NAME)
    }

    fun <T:Any> extensionOf(extensionAware: ExtensionAware, type: Class<T>): T {
        return extensionAware.extensions.getByType<T>(type)!!
    }
}