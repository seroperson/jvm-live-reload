set shell := ["bash", "-c"]

# Use sbtn if available, otherwise fallback to sbt
sbt := "sbt --client"

default: compile-sbt

calculate-version:
  {{ sbt }} catVersion

is-release: calculate-version
  [[ `cat version.txt` != *"SNAPSHOT"* ]]

compile-sbt:
  {{ sbt }} compile

test-sbt:
  {{ sbt }} scripted

publish-local-sbt:
  {{ sbt }} publishM2

publish-sbt:
  {{ sbt }} ci-release

publish-gradle: is-release
  cd gradle && ./gradlew :plugin:plugin:publishPlugins --validate-only \
    -Pgradle.publish.key=$GRADLE_PUBLISH_KEY \
    -Pgradle.publish.secret=$GRADLE_SECRET_KEY

code-format-check-sbt:
  # https://github.com/sbt/sbt/issues/5969
  {{ sbt }} javafmtCheckAll && {{ sbt }} scalafmtCheckAll

code-format-check-gradle: calculate-version
  cd gradle && ./gradlew spotlessCheck

