package me.seroperson.reload.live.sbt

import java.io.File
import java.net.URL
import play.dev.filewatch.FileWatchService
import sbt.Keys.Classpath
import sbt.internal.inc.Analysis
import sbt.settingKey
import sbt.taskKey

object Keys {

  type ClassLoaderCreator = (String, Array[URL], ClassLoader) => ClassLoader

  val liveFileWatchService =
    settingKey[FileWatchService]("The watch service to catch file changes.")

  val liveStartupHooks = settingKey[Seq[String]]("Startup hooks")
  val liveShutdownHooks = settingKey[Seq[String]]("Shutdown hooks")

  val liveDevSettings =
    settingKey[Seq[(String, String)]]("Development server settings.")

  val liveMonitoredFiles =
    taskKey[Seq[File]]("The list of files to be monitored for changes.")

  val liveDependencyClasspath = taskKey[Classpath](
    "The classpath containing all the jar dependencies of the project."
  )
  val liveReloaderClasspath = taskKey[Classpath](
    "The application classpath, containing all projects in this build that are dependencies of this project, including this project."
  )
  val liveCommonClassloader = taskKey[ClassLoader](
    "The common classloader, is used to hold H2 to ensure in memory databases don't get lost between invocations of run."
  )
  val liveDependencyClassLoader = taskKey[ClassLoaderCreator](
    "A function to create the dependency classloader from a name, set of URLs and parent classloader."
  )
  val liveReloaderClassLoader = taskKey[ClassLoaderCreator](
    "A function to create the application classloader from a name, set of URLs and parent classloader."
  )
  val liveAssetsClassLoader = taskKey[ClassLoader => ClassLoader](
    "Function that creates a classloader from a given parent that contains all the assets."
  )

  val liveReload = taskKey[Analysis](
    "Executed when sources of changed, to recompile (and possibly reload) the app"
  )
  val liveCompileEverything = taskKey[Seq[Analysis]](
    "Compiles this project and every project it depends on."
  )
  val liveAssetsWithCompilation = taskKey[Analysis](
    "The task that's run on a particular project to compile it. By default, builds assets and runs compile."
  )
}
