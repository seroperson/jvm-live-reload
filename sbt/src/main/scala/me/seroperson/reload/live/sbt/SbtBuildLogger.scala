package me.seroperson.reload.live.sbt

import me.seroperson.reload.live.build.BuildLogger
import sbt.util.Logger

class SbtBuildLogger(private val logger: Logger) extends BuildLogger {

  override def info(message: String): Unit = {
    logger.info(message)
  }

  override def warn(message: String): Unit = {
    logger.warn(message)
  }

  override def error(message: String): Unit = {
    logger.error(message)
  }

}
