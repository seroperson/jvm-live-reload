package me.seroperson.reload.live.sbt

import java.nio.file.{Path => NioPath}
import sbt.*
import sbt.Def.Classpath
import xsbti.FileConverter

object SbtCompat {

  type FileRef = File

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
