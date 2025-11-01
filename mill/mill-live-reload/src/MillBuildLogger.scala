package me.seroperson.reload.live.mill

import me.seroperson.reload.live.build.BuildLogger
import me.seroperson.reload.live.settings.DevServerSettings
import mill.api.daemon.Logger

class MillBuildLogger(
    private val settings: DevServerSettings,
    private val logger: Logger
) extends BuildLogger {

  override def debug(message: String) = {
    if (settings.isDebug) {
      logger.debug(message)
    }
  }

  override def info(message: String) = {
    logger.info(message)
  }

  override def warn(message: String) = {
    logger.warn(message)
  }

  override def error(t: Throwable) = {
    if (settings.isDebug) {
      t.printStackTrace()
    }
    error("Got error " + t.getMessage)

  }

  override def error(message: String, t: Throwable) = {
    if (settings.isDebug) {
      t.printStackTrace()
    }
    logger.error(message)
  }

  override def error(message: String) = {
    logger.error(message)
  }
}
