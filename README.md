# ♾️ jvm-live-reload

[![Build Status](https://github.com/seroperson/jvm-live-reload/actions/workflows/ci.yml/badge.svg)](https://github.com/seroperson/jvm-live-reload/actions/workflows/ci.yml)
[![Sonatype Central Version](https://maven-badges.sml.io/sonatype-central/me.seroperson/sbt-live-reload_sbt2_3/badge.svg)](https://central.sonatype.com/artifact/me.seroperson/sbt-live-reload_sbt2_3)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/seroperson/jvm-live-reload/LICENSE)

<!-- prettier-ignore-start -->
> [!WARNING]
> This project is in an alpha-quality stage. Everything tends to change. If you
> encounter any issues or it doesn't play well with your setup, please file an 
> [issue][3].
<!-- prettier-ignore-end -->

This project aims to provide a consistent live reload experience for any **web**
application (currently you can't yet use it with daemons) on the JVM. It allows
you to speed up your development cycle regardless of what framework or library
you're using. Read an article **[♾️ Live Reloading on JVM][15]** for more
information on the reloading topic and prerequisites for the creation of this
project.

<p align="center">
  <img src=".github/preview.gif" alt="Preview" width="700px">
</p>

- [How it works](#how-it-works)
- [Installation](#installation)
  - [Changes to the application code](#changes-to-the-application-code)
  - [sbt](#sbt)
  - [Gradle](#gradle)
  - [mill](#mill)
  - [Fixing the InaccessibleObjectException error](#fixing-the-inaccessibleobjectexception-error)
- [Configuration](#configuration)
  - [Hooks](#hooks)
- [List of tested frameworks](#list-of-tested-frameworks)
- [License](#license)

## How it works

The core principle is widely known and already adopted by such giants as
[Spring][6], [Quarkus][7], [Play][5], [Apache Tapestry][8] (and probably more).
Basically, all of them work like this:

- Starting an application.
- Watching for project changes.
- When a change occurs, the application (but not the JVM itself) stops, and the
  underlying `ClassLoader` gets dropped.
- The application starts again with a new modified `ClassLoader`.

Such approach allows you to boost your development cycle by saving time on JVM
startup and system classes initialization. Concrete frameworks can also use some
additional boost depending on their own structure and lifecycle.

This project utilizes the general approach, but with minor tweaks to make it
framework-agnostic:

- When run task is called, it starts the reverse-proxy webserver.
- This proxy starts your underlying application and routes everything into it.
- When a change occurs, the next request to the proxy will reload the underlying
  code by re-creating a `ClassLoader` and stopping/starting an underlying
  application.

## Installation

To get started, first, you'll probably need to do some changes to the
application's code and also setup a plugin for your build system. Currently
supported build systems are `sbt`, `gradle` and `mill`. We want to cover as much
as we can, so more build systems will likely be added later.

<!-- prettier-ignore-start -->
> [!IMPORTANT]
> After making all the necessary changes, be sure to read the 
> [Configuration](#configuration) section to tweak default settings according 
> to your setup. By default live-reloading proxy will start at `:9000` port 
> and your application is expected to listen at `:8080`. You must now send 
> requests to the proxy, not an application itself.
<!-- prettier-ignore-end -->

### Changes to the application code

Besides the basic plugin installation flow, there are things you'll probably
(_check the [list of supported frameworks](#list-of-tested-frameworks) to find
exact changes which you must implement according to your framework_) need to
change in your application to make it live-reloading-ready:

- Implement a `/health` endpoint. It must respond successfully when the
  application is ready to receive requests; usually, you can leave it without
  any logic.
- The `main` method must handle `InterruptedException` by gracefully shutting
  down the webserver and release all initialized resources.
- The `main` method must only finish when your application is completely
  stopped.

Implementing this logic will also make your application lifecycle more
predictable in general, so they are just nice to have besides making an
application live-reloading-ready. Read an article **[⏹️ Making your JVM
application interruptible][13]** to know more about interrupting.

Worth to say, that if your framework doesn't support interrupting and/or doesn't
allow you to make these changes by yourself right in your codebase, probably it
should be supported by the plugin, like frameworks from Scala ecosystem, such as
`zio` or `cats-effect` (or a framework itself must be fixed, which is
preferable). Once again, you can take a look at the
[list of tested frameworks](#list-of-tested-frameworks), although, of course,
even if your framework isn't in the list, live-reloading may still work if it
implements interrupting and graceful shutdown correctly.

### sbt

Add a plugin to `project/plugins.sbt` using:

```scala
addSbtPlugin("me.seroperson" % "sbt-live-reload" % "0.0.1")
```

And enable the plugin on your web application:

```scala
enablePlugins(LiveReloadPlugin)
```

The command to run your application in live-reloading mode is `sbt run`.

### Gradle

Add a plugin to your `build.gradle.kts` using:

```kotlin
id("me.seroperson.reload.live.gradle") version "0.0.1"
```

The command to run your application in live-reloading mode is
`./gradlew liveReloadRun`.

### mill

Add plugin dependency at the top of `build.mill`:

```scala
//| mvnDeps:
//| - me.seroperson::mill-live-reload::0.0.1
```

And make your application module extend `LiveReloadModule`:

```scala
// ...
import me.seroperson.reload.live.mill.*

object app extends LiveReloadModule, ScalaModule {
  // ...
}
```

The command to run your application in live-reloading mode is
`mill app.liveReloadRun`.

### Fixing the InaccessibleObjectException error

As this plugin uses some internal classes that aren't available without extra
configuration, you may encounter errors like this during reloading (you can
enable stacktrace displaying using `live.reload.debug` property):

```text
java.lang.reflect.InaccessibleObjectException: Unable to make static void java.lang.ApplicationShutdownHooks.runHooks() accessible: module java.base does not "opens java.lang" to unnamed module @77e282b6
```

Then you need either tweak environment variable or add this option to [your
IDE's Java runtime][14]:

```sh
export JDK_JAVA_OPTIONS="$JDK_JAVA_OPTIONS --add-opens=java.base/java.lang=ALL-UNNAMED"
```

## Configuration

This plugin has defaults that should be suitable for most people, but you can
change them using environment variables or build configuration.

First, let's check the list of available options:

| Key                           | Environment                   | Default     | Description                                   |
| ----------------------------- | ----------------------------- | ----------- | --------------------------------------------- |
| `live.reload.proxy.http.host` | `LIVE_RELOAD_PROXY_HTTP_HOST` | `0.0.0.0`   | The host for the proxy to start on            |
| `live.reload.proxy.http.port` | `LIVE_RELOAD_PROXY_HTTP_PORT` | `9000`      | The port for the proxy to listen on           |
| `live.reload.http.host`       | `LIVE_RELOAD_HTTP_HOST`       | `localhost` | The host on which your web application starts |
| `live.reload.http.port`       | `LIVE_RELOAD_HTTP_PORT`       | `8080`      | The port your web application listens on      |
| `live.reload.http.health`     | `LIVE_RELOAD_HTTP_HEALTH`     | `/health`   | Path to your health-check endpoint            |
| `live.reload.debug`           | `LIVE_RELOAD_DEBUG`           | `false`     | Whether to enable/disable debug output        |

To change variables using build configuration, use the following key for `sbt`:

```scala
liveDevSettings := Seq[(String, String)](
  // Can be plain string or value from auto-imported `DevSettingsKeys` object
  DevSettingsKeys.LiveReloadProxyHttpPort -> "9001",
  DevSettingsKeys.LiveReloadHttpPort -> "8081"
)
```

And for `gradle`:

```kotlin
liveReload { settings = mapOf("live.reload.http.port" to "8081") }
```

And for `mill`:

```scala
import me.seroperson.reload.live.mill.*

object app extends LiveReloadModule, ScalaModule {
 def liveDevSettings: Task[Seq[(String, String)]] = Task.Anon {
    Seq(
      DevSettingsKeys.LiveReloadHttpPort -> "8081"
    )
  }
}
```

### Hooks

So far not every framework implements interrupting and graceful shutdown
correctly, which is necessary to be live-reloading-ready. That's why this plugin
introduces so-called "hooks". Hooks define how to start and shutdown your
application. When reloading occurs, the proxy will call all defined shutdown
hooks to stop it, and then it will call all startup hooks to start it again.
Both types of hooks are blocking. When shutdown hooks are finished, the
application is considered stopped and all its resources are cleaned. Similarly,
when startup hooks are finished, the application is considered ready to receive
requests.

For example, there is the built-in `RestApiHealthCheckStartupHook`, which polls
the `/health` endpoint until a successful response. This means that your
application will be considered started when its `/health` endpoint returns
`200`. Similarly, there is a `RestApiHealthCheckShutdownHook`, which polls the
endpoint until a failure.

The complete list of built-in hooks:

<table>
  <tr>
    <th>Class</th>
    <th>Description</th>
  </tr>
  <tr>
    <td><a href="https://github.com/seroperson/jvm-live-reload/blob/main/core/build-link/src/main/java/me/seroperson/reload/live/hook/RestApiHealthCheckStartupHook.java">RestApiHealthCheckStartupHook</a></td>
    <td>Blocks until success on <code>/health</code> endpoint.</td>
  </tr>
  <tr>
    <td><a href="https://github.com/seroperson/jvm-live-reload/blob/main/core/build-link/src/main/java/me/seroperson/reload/live/hook/RestApiHealthCheckShutdownHook.java">RestApiHealthCheckShutdownHook</a></td>
    <td>Blocks until failure on <code>/health</code> endpoint.</td>
  </tr>
  <tr>
    <td><a href="https://github.com/seroperson/jvm-live-reload/blob/main/core/build-link/src/main/java/me/seroperson/reload/live/hook/RuntimeShutdownHook.java">RuntimeShutdownHook</a></td>
    <td>Uses reflection to call all JVM shutdown hooks added by <code>Runtime.addShutdownHook</code>.</td>
  </tr>
  <tr>
    <td><a href="https://github.com/seroperson/jvm-live-reload/blob/main/core/build-link/src/main/java/me/seroperson/reload/live/hook/ThreadInterruptShutdownHook.java">ThreadInterruptShutdownHook</a></td>
    <td>Calls <code>Thread.interrupt()</code> on the main thread.</td>
  </tr>
  <tr>
    <td><a href="https://github.com/seroperson/jvm-live-reload/blob/main/core/hook-scala/src/main/scala/me/seroperson/reload/live/hook/io/IoAppStartupHook.scala">IoAppStartupHook</a></td>
    <td>(<i>Scala-only</i>) Starts a <code>cats.effect.IOApp</code>. Basically, it just sets an internal property to strip unnecessary logging.</td>
  </tr>
  <tr>
    <td><a href="https://github.com/seroperson/jvm-live-reload/blob/main/core/hook-scala/src/main/scala/me/seroperson/reload/live/hook/io/IoAppEffectShutdownHook.scala">IoAppShutdownHook</a></td>
    <td>(<i>Scala-only</i>) Stops a <code>cats.effect.IOApp</code>. Shuts down the underlying <code>cats.effect.unsafe.IORuntime</code> instances.</td>
  </tr>
  <tr>
    <td><a href="https://github.com/seroperson/jvm-live-reload/blob/main/core/hook-scala/src/main/scala/me/seroperson/reload/live/hook/zio/ZioAppStartupHook.scala">ZioAppStartupHook</a></td>
    <td>(<i>Scala-only</i>) Starts a <code>zio.ZIOApp</code>. Updates context class loader for <code>ZScheduler</code> threads which survive shutdown.</td>
  </tr>
  <tr>
    <td><a href="https://github.com/seroperson/jvm-live-reload/blob/main/core/hook-scala/src/main/scala/me/seroperson/reload/live/hook/zio/ZioZioAppShutdownHook.scala">ZioAppShutdownHook</a></td>
    <td>(<i>Scala-only</i>) Stops a <code>zio.ZIOApp</code>. Stops all internal executors if possible.</td>
  </tr>
</table>

The `sbt` and `mill` plugins also provide a set of predefined hooks, so-called
hook bundles, which will be automatically used when a plugin finds the
corresponding library in a classpath. Currently, supported sets are:
`ZioAppHookBundle`, `IoAppHookBundle` and `CaskAppHookBundle`. All available
options are defined in [HookBundle.scala][4]. You can also override a set of
startup/shutdown hooks using the `liveStartupHooks` and `liveShutdownHooks`
keys. For example:

```scala
// The order matters (!)
liveShutdownHooks := Seq[String](
  // Can be plain string or value from auto-imported `HookClassnames` object
  HookClassnames.RestApiHealthCheckShutdown,
  HookClassnames.ThreadInterruptShutdown
)
```

This way, you can also implement your own hooks. All you need to do is implement
the `me.seroperson.reload.live.hook.Hook` interface and specify it in the build
configuration. They will be instantiated automatically using reflection during
proxy webserver startup.

To change hooks for the `gradle` plugin, use the following settings:

```kotlin
liveReload {
  // these are default values
  startupHooks = listOf("me.seroperson.reload.live.hook.RestApiHealthCheckStartupHook")
  shutdownHooks = listOf(
    "me.seroperson.reload.live.hook.ThreadInterruptShutdownHook",
    "me.seroperson.reload.live.hook.RuntimeShutdownHook",
    "me.seroperson.reload.live.hook.RestApiHealthCheckShutdownHook",
  )
}
```

For `mill`:

```scala
import me.seroperson.reload.live.mill.*

object app extends LiveReloadModule, ScalaModule {
  def liveStartupHooks: Task[Seq[String]] = Task.Anon {
    Seq(
      HookClassnames.RestApiHealthCheckStartup
    )
  }
}
```

## List of tested frameworks

To minimize any unsuccessful experience, we'll maintain the list of officially
tested frameworks and libraries right here.

However, even if a framework isn't listed here, it still may play well. If you
have successfully used this plugin, I would appreciate if you could share your
project setup [in the relevant discussion][12], even if your setup fully
consists of libraries listed below. This would help other users to determine
whether their own setup will work.

<table>
  <tr>
    <th>Framework</th>
    <th>Version</th>
    <th>Confirmation</th>
    <th>Necessary changes to the application code</th>
  </tr>
  <tr>
    <td><a href="https://github.com/zio/zio">zio</a> + <a href="https://github.com/zio/zio-http">zio-http</a> + <a href="https://github.com/zio/zio-config">zio-config-typesafe</a></td>
    <td><i>zio</i> <b>2.1.21</b>, <i>zio-http</i> <b>3.5.1</b>, <i>zio-config</i> <b>4.0.5</b></td>
    <td>See <code>zio-*</code> in <a href="https://github.com/seroperson/jvm-live-reload/tree/main/sbt/src/sbt-test/sbt-live-reload">sbt-test</a> folder.</td>
    <td>Only <code>/health</code> endpoint.</td>
  </tr>
  <tr>
    <td><a href="https://github.com/http4s/http4s">http4s-ember-server</a> + <a href="https://github.com/typelevel/cats-effect">cats-effect</a> + <a href="https://github.com/pureconfig/pureconfig">pureconfig</a></td>
    <td><i>http4s-ember-server</i> <b>0.23.30</b>, <i>cats-effect</i> <b>3.6.1</b>, <i>pureconfig</i> <b>0.17.9</b></td>
    <td>See <code>http4s-*</code> in <a href="https://github.com/seroperson/jvm-live-reload/tree/main/sbt/src/sbt-test/sbt-live-reload">sbt-test</a> folder.</td>
    <td>Only <code>/health</code> endpoint.</td>
  </tr>
  <tr>
    <td><a href="https://github.com/com-lihaoyi/cask">cask</a></td>
    <td><i>cask</i> <b>0.9.7</b></td>
    <td>See <code>cask</code> in <a href="https://github.com/seroperson/jvm-live-reload/tree/main/sbt/src/sbt-test/sbt-live-reload">sbt-test</a> folder.</td>
    <td>Only <code>/health</code> endpoint.</td>
  </tr>
  <tr>
    <td><a href="https://github.com/ktorio/ktor">ktor</a></td>
    <td><i>ktor</i> <b>3.3.0</b></td>
    <td><a href="https://github.com/seroperson/jvm-live-reload/blob/main/gradle/plugin/plugin/src/functionalTest/kotlin/me.seroperson.reload.live.gradle/LiveReloadKtorTest.kt">LiveReloadKtorTest.kt</a></td>
    <td>Everything from <a href="#changes-to-the-application-code">this section</a>.</td>
  </tr>
  <tr>
    <td><a href="https://github.com/http4k/http4k">http4k</a></td>
    <td><i>http4k</i> <b>6.18.1.0</b></td>
    <td><a href="https://github.com/seroperson/jvm-live-reload/blob/main/gradle/plugin/plugin/src/functionalTest/kotlin/me.seroperson.reload.live.gradle/LiveReloadHttp4kTest.kt">LiveReloadHttp4kTest.kt</a></td>
    <td>Everything from <a href="#changes-to-the-application-code">this section</a>.</td>
  </tr>
  <tr>
    <td><a href="https://github.com/javalin/javalin">javalin</a></td>
    <td><i>javalin</i> <b>6.7.0</b></td>
    <td><a href="https://github.com/seroperson/jvm-live-reload/blob/main/gradle/plugin/plugin/src/functionalTest/kotlin/me.seroperson.reload.live.gradle/LiveReloadJavalinTest.kt">LiveReloadHttp4kTest.kt</a></td>
    <td>Everything from <a href="#changes-to-the-application-code">this section</a>.</td>
  </tr>
</table>

## License

A lot of code was initially copied from the [playframework][2] project. Many
thanks to all the contributors, as without them it would take much more time to
implement everything correctly.

```text
MIT License

Copyright (c) 2025 Daniil Sivak

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

<!-- prettier-ignore-start -->
[1]: https://github.com/jj-vcs/jj
[2]: https://github.com/playframework/playframework
[3]: https://github.com/seroperson/jvm-live-reload/issues/new?template=1-not_working_setup.yml
[4]: https://github.com/seroperson/jvm-live-reload/blob/main/sbt/src/main/scala/me/seroperson/reload/live/sbt/HookBundle.scala
[5]: https://jto.github.io/articles/play_anatomy_part2_sbt/
[6]: https://docs.spring.io/spring-boot/reference/using/devtools.html
[7]: https://quarkus.io/guides/class-loading-reference
[8]: https://tapestry.apache.org/class-reloading.html
[9]: https://spring.io
[10]: https://www.playframework.com
[11]: https://quarkus.io
[12]: https://github.com/seroperson/jvm-live-reload/discussions/1
[13]: https://seroperson.me/2025/10/20/interrupting-jvm-application/
[14]: https://www.jetbrains.com/help/idea/tuning-the-ide.html#procedure-jvm-options
[15]: https://seroperson.me/2025/11/28/jvm-live-reload/
<!-- prettier-ignore-end -->
