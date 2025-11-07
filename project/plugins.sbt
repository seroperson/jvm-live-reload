resolvers += Resolver.sonatypeCentralSnapshots

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.13.1")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.6")
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.11.2")
addSbtPlugin(
  "com.github.sbt" % "sbt-java-formatter" % "0.10.0+35-c6ebdb0f-SNAPSHOT"
)
