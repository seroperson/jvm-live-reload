package me.seroperson.reload.live.sbt

import me.seroperson.reload.live.build.BuildLogger
import me.seroperson.reload.live.settings.DevServerSettings
import sbt.util.Logger

class SbtBuildLogger(
    private val settings: DevServerSettings,
    private val logger: Logger
) extends BuildLogger {

  override def debug(message: String): Unit = {
    if (settings.isDebug()) {
      println(message)
      logger.debug(message)
    }
  }

  override def info(message: String): Unit = {
    if (settings.isDebug()) {
      println(message)
    }
    logger.info(message)
  }

  override def warn(message: String): Unit = {
    if (settings.isDebug()) {
      println(message)
    }
    logger.warn(message)
  }

  override def error(t: Throwable): Unit = {
    if (settings.isDebug()) {
      t.printStackTrace()
    }
    error("Got error " + t.getMessage())
  }

  override def error(message: String, t: Throwable): Unit = {
    if (settings.isDebug()) {
      t.printStackTrace()
    }
    error(message + ", " + t.getMessage())
  }

  override def error(message: String): Unit = {
    if (settings.isDebug()) {
      println(message)
    }
    logger.error(message)
  }

}
