package me.seroperson.reload.live.sbt

import java.nio.file.Path as NioPath
import sbt.*
import sbt.Def.Classpath
import xsbti.FileConverter
import xsbti.VirtualFileRef

object SbtCompat:
  type FileRef = xsbti.HashedVirtualFileRef

  export sbt.Def.uncached

  private def execValue[T](t: T) = sbt.Result.Value(t)

  /** Shim for runTask. Project.runTask is removed in sbt 2.0.
    *
    * This will be replaced when Extracted.runTask with the same signature is
    * supported in sbt 2.0.
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
