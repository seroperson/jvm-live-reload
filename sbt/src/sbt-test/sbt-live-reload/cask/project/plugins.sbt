updateOptions := updateOptions.value.withLatestSnapshots(false)

resolvers += Resolver.mavenLocal

addSbtPlugin("me.seroperson" % "sbt-live-reload" % "0.0.1")
addSbtPlugin("org.playframework" % "sbt-scripted-tools" % "3.0.9")
