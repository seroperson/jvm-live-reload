package sbt

import sbt.util.LoggerContext

object SbtLoggerContextAccess {

  def apply(state: State): LoggerContext = LoggerContext(
    state.get(Keys.useLog4J.key).getOrElse(false)
  )

  def loggerContextKey: AttributeKey[LoggerContext] = Keys.loggerContext

}
