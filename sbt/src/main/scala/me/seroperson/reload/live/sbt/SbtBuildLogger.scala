package me.seroperson.reload.live.sbt

import me.seroperson.reload.live.build.BuildLogger
import sbt.util.Logger

class SbtBuildLogger(private val logger: Logger) extends BuildLogger {

  override def debug(message: String): Unit = {
    println(message)
    logger.debug(message)
  }

  override def info(message: String): Unit = {
    println(message)
    logger.info(message)
  }

  override def warn(message: String): Unit = {
    println(message)
    logger.warn(message)
  }

  override def error(message: String): Unit = {
    println(message)
    logger.error(message)
  }

}
