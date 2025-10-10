package me.seroperson.reload.live.sbt

import java.nio.file.Path as NioPath
import sbt.*
import sbt.Def.Classpath
import xsbti.FileConverter

/** Compatibility layer for SBT 2.12 (older SBT versions).
  *
  * This object provides a unified API for operations that differ between SBT
  * versions. It allows the live reload plugin to work across different SBT
  * versions by abstracting away version-specific implementation details.
  *
  * This version targets SBT versions that use the older project API and file
  * handling mechanisms.
  */
object SbtCompat {

  type FileRef = File

  /** Runs an SBT task and returns the result with state.
    *
    * @param taskKey
    *   the task to run
    * @param state
    *   the current SBT state
    * @return
    *   optional tuple of new state and task result
    */
  def runTask[T](
      taskKey: TaskKey[T],
      state: State
  ): Option[(State, Result[T])] =
    Project.runTask(taskKey, state)

  def uncached[T](value: T): T = value

  def getFiles(c: Classpath)(implicit conv: FileConverter): Seq[File] = c.files

  def toNioPath(f: File)(implicit conv: FileConverter): NioPath = f.toPath

  def fileName(file: FileRef): String = file.getName
}
