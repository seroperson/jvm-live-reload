set shell := ["bash", "-c"]

# Use sbtn if available, otherwise fallback to sbt
sbt := `which sbtn || which sbt`

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

test-gradle: calculate-version
  cd gradle && ./gradlew :plugin:plugin:check

publish-gradle: is-release
  cd gradle && ./gradlew :plugin:plugin:publishPlugins --validate-only \
    -Pgradle.publish.key=$GRADLE_PUBLISH_KEY \
    -Pgradle.publish.secret=$GRADLE_SECRET_KEY

code-format-check-sbt:
  # https://github.com/sbt/sbt/issues/5969
  {{ sbt }} javafmtCheckAll && {{ sbt }} scalafmtCheckAll

code-format-apply-sbt:
  {{ sbt }} javafmtAll && {{ sbt }} scalafmtAll

code-format-check-gradle: calculate-version
  cd gradle && ./gradlew spotlessCheck

code-format-apply-gradle: calculate-version
  cd gradle && ./gradlew spotlessApply

