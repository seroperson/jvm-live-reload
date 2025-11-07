import sbt._
import sbt.Keys._

// Copied from playframework repository
object Commands {
  val quickPublish = Command.command(
    "quickPublish",
    Help.more(
      "quickPublish",
      "Toggles quick publish mode, disabling/enabling build of documentation/source jars"
    )
  ) { state =>
    val projectExtract = Project.extract(state)
    import projectExtract._

    val quickPublishToggle = AttributeKey[Boolean]("quickPublishToggle")

    val toggle = !state.get(quickPublishToggle).getOrElse(true)

    val filtered = session.mergeSettings.filter { setting =>
      setting.key match {
        case Def.ScopedKey(Scope(_, Zero, Zero, Zero), key)
            if key == publishArtifact.key =>
          false
        case other => true
      }
    }

    if (toggle) {
      state.log.info("Turning off quick publish")
    } else {
      state.log.info("Turning on quick publish")
    }

    projectExtract.appendWithoutSession(
      filtered ++ Seq(
        GlobalScope / packageDoc / publishArtifact := toggle,
        GlobalScope / packageSrc / publishArtifact := toggle,
        GlobalScope / publishArtifact := true
      ),
      state.put(quickPublishToggle, toggle)
    )
  }

  // `mill` and `gradle` read current version from this file
  val catVersion = Command.command(
    "catVersion",
    Help.more("catVersion", "Prints current version to `version.txt` file.")
  ) { state =>
    val projectExtract = Project.extract(state)
    import projectExtract._

    val versionFile = file("version.txt")
    state.log.info(s"Writing version to ${versionFile.getAbsolutePath}")
    IO.write(versionFile, state.getSetting(version).get)
    state
  }
}
