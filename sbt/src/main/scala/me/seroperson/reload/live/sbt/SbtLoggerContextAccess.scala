package sbt

import sbt.util.LoggerContext

object SbtLoggerContextAccess {

  def apply(useLog4J: Boolean): LoggerContext = LoggerContext(useLog4J)

  def loggerContextKey: AttributeKey[LoggerContext] = Keys.loggerContext

}
