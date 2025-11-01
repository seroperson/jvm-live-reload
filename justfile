set shell := ["bash", "-c"]

gradle := "./gradlew"
mill := "mill"
sbt := "sbt"

default: compile-sbt

clear-local-repos:
  rm -rf $HOME/.ivy2/local/me.seroperson/ $HOME/.m2/repository/

calculate-version:
  (if [ ! -e "version.txt" ]; then \
    {{ sbt }} catVersion; \
  fi) || true

is-release: calculate-version
  [[ `cat version.txt` != *"SNAPSHOT"* ]]

compile-sbt:
  {{ sbt }} compile

test-sbt: publish-local-if-unpublished-sbt
  {{ sbt }} scripted

publish-local-if-unpublished-sbt:
  (if [ ! -e "$HOME/.ivy2/local/me.seroperson/jvm-live-reload-build-link/" ]; then \
    {{ sbt }} publishM2 && {{ sbt }} publishLocal; \
  fi) || true

publish-local-sbt:
  {{ sbt }} publishM2 && {{ sbt }} publishLocal

publish-sbt:
  {{ sbt }} ci-release

test-gradle: publish-local-if-unpublished-sbt calculate-version
  cd gradle && {{ gradle }} :plugin:plugin:check

publish-gradle: publish-local-if-unpublished-sbt is-release
  cd gradle && {{ gradle }} :plugin:plugin:publishPlugins --validate-only \
    -Pgradle.publish.key=$GRADLE_PUBLISH_KEY \
    -Pgradle.publish.secret=$GRADLE_SECRET_KEY

test-mill: publish-local-if-unpublished-sbt calculate-version
  cd mill && {{ mill }} mill-live-reload.publishLocal && {{ mill }} mill-live-reload.integration.testLocal

code-format-check-sbt:
  # https://github.com/sbt/sbt/issues/5969
  {{ sbt }} javafmtCheckAll && {{ sbt }} scalafmtCheckAll

code-format-apply-sbt:
  {{ sbt }} javafmtAll && {{ sbt }} scalafmtAll

code-format-check-mill:
  cd mill && {{ mill }} mill-live-reload.checkFormat

code-format-apply-mill:
  cd mill && {{ mill }} mill-live-reload.reformat

code-format-check-gradle: publish-local-if-unpublished-sbt calculate-version
  cd gradle && {{ gradle }} spotlessCheck

code-format-apply-gradle: publish-local-if-unpublished-sbt calculate-version
  cd gradle && {{ gradle }} spotlessApply

code-format-check-all: code-format-check-sbt code-format-check-mill code-format-check-gradle
  @echo "SUCCESS"

code-format-apply-all: code-format-apply-sbt code-format-apply-mill code-format-apply-gradle
  @echo "SUCCESS"

