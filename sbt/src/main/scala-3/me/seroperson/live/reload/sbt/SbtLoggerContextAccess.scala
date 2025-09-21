package sbt

import sbt.util.LoggerContext

object SbtLoggerContextAccess {

  def apply(state: State): LoggerContext = LoggerContext()

  def loggerContextKey: AttributeKey[LoggerContext] = Keys.loggerContext

}
