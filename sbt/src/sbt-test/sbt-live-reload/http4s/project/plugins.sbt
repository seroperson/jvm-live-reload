updateOptions := updateOptions.value.withLatestSnapshots(false)

resolvers += Resolver.mavenLocal

addSbtPlugin("me.seroperson" % "sbt-live-reload" % "0.0.1")
libraryDependencies += "com.softwaremill.sttp.client4" %% "core" % "4.0.12"
