package me.seroperson.reload.live.sbt

import java.nio.file.Path as NioPath
import sbt.*
import sbt.Def.Classpath
import xsbti.FileConverter
import xsbti.VirtualFileRef

/** Compatibility layer for SBT 2 (newer SBT versions).
  *
  * This object provides a unified API for operations that differ between SBT
  * versions. It allows the live reload plugin to work across different SBT
  * versions by abstracting away version-specific implementation details.
  *
  * This version targets newer SBT versions that use the virtual file system and
  * updated project APIs. It includes compatibility shims for deprecated APIs
  * and provides inline methods for performance.
  */
object SbtCompat:
  type FileRef = xsbti.HashedVirtualFileRef

  export sbt.Def.uncached

  private def execValue[T](t: T) = sbt.Result.Value(t)

  /** Shim for runTask. Project.runTask is removed in sbt 2.0.
    *
    * This will be replaced when Extracted.runTask with the same signature is
    * supported in sbt 2.0. For now, it uses Project.extract to access the newer
    * API and wraps the result in the expected format.
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
    Some(
      Project.extract(state).runTask(taskKey, state) match {
        case (state, t) => (state, execValue(t))
      }
    )

  inline def getFiles(c: Classpath)(implicit fc: FileConverter): Seq[File] =
    c.files.map(_.toFile)

  inline def toNioPath(hvf: VirtualFileRef)(using fc: FileConverter): NioPath =
    fc.toPath(hvf)

  inline def fileName(file: FileRef): String = file.name

end SbtCompat
