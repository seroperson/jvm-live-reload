package me.seroperson.reload.live.gradle

import me.seroperson.reload.live.build.BuildLogger
import org.slf4j.LoggerFactory

class LiveReloadLogger : BuildLogger {

    private val logger: org.slf4j.Logger = LoggerFactory.getLogger(LiveReloadLogger::class.java)

    override fun debug(message: String?) {
        logger.debug(message)
    }

    override fun info(message: String?) {
        logger.info(message)
    }

    override fun warn(message: String?) {
        logger.warn(message)
    }

    override fun error(t: Throwable?) {
        logger.error("Error in LiveReload plugin", t)
    }

    override fun error(message: String?, t: Throwable?) {
        logger.error(message, t)
    }

    override fun error(message: String?) {
        logger.error(message)
    }
}