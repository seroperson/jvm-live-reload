package me.seroperson.reload.live.sbt

import me.seroperson.reload.live.build.BuildLogger
import me.seroperson.reload.live.settings.DevServerSettings
import sbt.util.Logger

/** Implementation of BuildLogger that delegates to SBT's logging system.
  *
  * This logger bridges the live reload system's logging interface with SBT's
  * native logging infrastructure. It respects the debug settings from
  * DevServerSettings to provide additional console output when debug mode is
  * enabled.
  *
  * In debug mode, messages are written both to the console (println) and to the
  * SBT logger. In normal mode, only the SBT logger is used.
  *
  * @param settings
  *   the development server settings (used to check debug mode)
  * @param logger
  *   the underlying SBT logger instance
  */
class SbtBuildLogger(
    private val settings: DevServerSettings,
    private val logger: Logger
) extends BuildLogger {

  override def debug(message: String): Unit = {
    if (settings.isDebug) {
      println(message)
      logger.debug(message)
    }
  }

  override def info(message: String): Unit = {
    if (settings.isDebug) {
      println(message)
    }
    logger.info(message)
  }

  override def warn(message: String): Unit = {
    if (settings.isDebug) {
      println(message)
    }
    logger.warn(message)
  }

  override def error(t: Throwable): Unit = {
    if (settings.isDebug) {
      t.printStackTrace()
    }
    error("Got error " + t.getMessage)
  }

  override def error(message: String, t: Throwable): Unit = {
    if (settings.isDebug) {
      t.printStackTrace()
    }
    error(message + ", " + t.getMessage)
  }

  override def error(message: String): Unit = {
    if (settings.isDebug) {
      println(message)
    }
    logger.error(message)
  }

}
