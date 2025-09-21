updateOptions := updateOptions.value.withLatestSnapshots(false)

resolvers += Resolver.mavenLocal

addSbtPlugin("me.seroperson" % "sbt-live-reload" % "0.0.1")
