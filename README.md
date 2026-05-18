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
or **GRPC** application (currently you can't yet use it with daemons) on the
JVM. It allows you to speed up your development cycle regardless of what
framework or library you're using. Read an article **[♾️ Live Reloading on
JVM][15]** for more information on the reloading topic and prerequisites for the
creation of this project.

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
  - [Tuning your webserver](#tuning-your-webserver)
- [GRPC](#grpc)
  - [Switching to GRPC mode](#switching-to-grpc-mode)
  - [Health checking](#health-checking)
  - [Streaming and reflection](#streaming-and-reflection)
  - [TLS](#tls)
- [Configuration](#configuration)
  - [Hooks](#hooks)
  - [Propagate environment](#propagate-environment)
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

Minimum required JDK is **17**.

<!-- prettier-ignore-start -->
> [!IMPORTANT]
> After making all the necessary changes, be sure to read the 
> [Configuration](#configuration) section to tweak default settings according 
> to your setup. By default the HTTP proxy starts at `:9000` and your 
> application is expected to listen at `:8080`. For GRPC the defaults are 
> `:9001` for the proxy and `:8081` for the target application. You must now 
> send requests to the proxy, not an application itself. See the 
> [GRPC](#grpc) section if you want to live-reload a GRPC server instead of 
> an HTTP one.
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

<!-- prettier-ignore-start -->
> [!NOTE]
> This plugin makes your application run in a non-forked JVM. If your application
> somehow relies on running inside an isolated JVM process, you should ensure that 
> it won’t cause any issues. In most cases, however, you probably won’t even
> notice a difference.
<!-- prettier-ignore-end -->

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
addSbtPlugin("me.seroperson" % "sbt-live-reload" % "0.1.1")
```

And enable the plugin on your web application:

```scala
enablePlugins(LiveReloadPlugin)
```

The command to run your application in live-reloading mode is `sbt run`.

### Gradle

Add a plugin to your `build.gradle.kts` using:

```kotlin
id("me.seroperson.reload.live.gradle") version "0.1.1"
```

The command to run your application in live-reloading mode is
`./gradlew liveReloadRun`.

### mill

Add plugin dependency at the top of `build.mill`:

```scala
//| mvnDeps:
//| - me.seroperson::mill-live-reload::0.1.1
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
export JDK_JAVA_OPTIONS="$JDK_JAVA_OPTIONS --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED"
```

### Tuning your webserver

Some webservers may have settings which can slow down your reloading speed. For
example, Netty has a `quietPeriodSeconds` parameter, which sets the period of
time before shutdown when the webserver would be idle. `zio-http` has this
parameter set by default to 2 seconds; other frameworks with Netty under the
hood may have other slow defaults. So be sure to tweak it if you notice some
lags during reloading.

For example, in case of `zio-http` you can use the predefined "fast shutdown"
Netty config:

```scala
import zio._
import zio.http._
import zio.http.netty.NettyConfig

object App extends ZIOAppDefault {
  val routes = Routes(/* ... */)

  def run = Server.serve(routes)
    .provide(
      Server.customized,
      ZLayer.succeed(Server.Config.default),
      ZLayer.succeed(NettyConfig.defaultWithFastShutdown)
    )
}
```

## GRPC

Besides HTTP, the plugin can also live-reload GRPC servers. The idea is exactly
the same: a reverse proxy sits in front of your application, routes all calls
into it and re-creates a `ClassLoader` when sources change. The difference is
that the proxy speaks HTTP/2 and the GRPC framing protocol instead of plain
HTTP, so unary, server-streaming, client-streaming and bidirectional calls all
keep working across reloads.

The proxy does not need to know about your `.proto` files. It forwards raw
messages by service and method name, so it works with any code generator and any
GRPC server implementation built on top of `io.grpc` (Netty, Netty-shaded,
Armeria, and so on).

### Switching to GRPC mode

For `sbt`, enable the plugin as usual and switch the server type:

```scala
enablePlugins(LiveReloadPlugin)

liveServerType := me.seroperson.reload.live.sbt.GrpcServerType
```

For `gradle`:

```kotlin
liveReload {
  serverType = me.seroperson.reload.live.gradle.ServerType.GRPC
}
```

For `mill`:

```scala
import me.seroperson.reload.live.mill.*

object app extends LiveReloadModule, ScalaModule {
  override def liveServerType = Task.Anon { GrpcServerType }
}
```

Once GRPC mode is on, the proxy listens on `:9001` and expects your application
to listen on `:8081`. The HTTP keys (`live.reload.proxy.http.*`,
`live.reload.http.*`) are ignored. Use `live.reload.proxy.grpc.*` and
`live.reload.grpc.*` to override hosts and ports - see the
[Configuration](#configuration) section for the full list.

The same requirements from
[Changes to the application code](#changes-to-the-application-code) still apply:
the `main` method must handle `InterruptedException`, gracefully shut down the
GRPC server and only return when everything is stopped. Most GRPC servers
already follow this pattern through `Server#awaitTermination` plus
`Server#shutdownNow`, so usually you just need to wrap the awaiting call into a
`try`/`catch`.

### Health checking

Instead of polling an HTTP `/health` endpoint, the GRPC mode uses the standard
[`grpc.health.v1.Health`][18] service to decide whether the application is ready
or fully stopped. So your application must expose this service. With most GRPC
distributions it is a one-liner using `HealthStatusManager` from
`grpc-services`:

```scala
import io.grpc.health.v1.HealthCheckResponse
import io.grpc.netty.NettyServerBuilder
import io.grpc.protobuf.services.HealthStatusManager

val health = new HealthStatusManager()
val server = NettyServerBuilder
  .forPort(8081)
  .addService(new GreeterImpl)
  .addService(health.getHealthService)
  .build()
  .start()

health.setStatus("", HealthCheckResponse.ServingStatus.SERVING)

try {
  server.awaitTermination()
} catch {
  case _: InterruptedException =>
    health.setStatus("", HealthCheckResponse.ServingStatus.NOT_SERVING)
    server.shutdownNow()
}
```

By default, the proxy queries the overall server status (empty service name). If
you want it to look at a specific service instead, set
`live.reload.grpc.health.service` to the fully qualified service name.

When the GRPC server type is selected, the plugin automatically picks the
`GrpcAppHookBundle`, which uses `GrpcHealthCheckStartupHook` and
`GrpcHealthCheckShutdownHook` in place of their HTTP counterparts. You can still
override the hook set manually through `liveStartupHooks` and
`liveShutdownHooks` - check the [Hooks](#hooks) section for details.

### Streaming and reflection

The proxy is fully transparent for all four RPC kinds (unary, server-streaming,
client-streaming, bidirectional) and forwards trailers, statuses and metadata as
is. It also passes through the standard [GRPC server reflection][19] service, so
tools like `grpcurl`, `evans` or BloomRPC keep working against the proxy as if
they were talking to your application directly. Just register
`ProtoReflectionService` in your server and point the tool at the proxy port.

### TLS

The proxy can listen with TLS and/or talk to the target server using TLS. Both
sides are independent, so the typical setup of a TLS application behind a
plaintext proxy is supported, as well as fully encrypted end-to-end traffic.

Enable TLS on the proxy listener by providing a PEM-encoded certificate chain
and private key:

```kotlin
liveReload {
  serverType = me.seroperson.reload.live.gradle.ServerType.GRPC
  settings = mapOf(
    "live.reload.grpc.proxy.tls.cert" to "/abs/path/to/cert.pem",
    "live.reload.grpc.proxy.tls.key" to "/abs/path/to/key.pem"
  )
}
```

Tell the proxy to use TLS for the upstream channel with
`live.reload.grpc.target.tls=true`. If the target uses a self-signed
certificate, point `live.reload.grpc.target.tls.trust` at the corresponding CA
or leaf certificate; otherwise the JVM default truststore is used.

Same goes for the health-check hooks - they reuse the upstream TLS settings, so
once `live.reload.grpc.target.tls` is enabled the readiness probe will also
connect over TLS automatically.

## Configuration

This plugin has defaults that should be suitable for most people, but you can
change them using environment variables or build configuration.

First, let's check the list of available options:

| Key                                 | Environment                         | Default     | Description                                                                                                                                         |
| ----------------------------------- | ----------------------------------- | ----------- | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| `live.reload.proxy.http.host`       | `LIVE_RELOAD_PROXY_HTTP_HOST`       | `0.0.0.0`   | The host for the proxy to start on                                                                                                                  |
| `live.reload.proxy.http.port`       | `LIVE_RELOAD_PROXY_HTTP_PORT`       | `9000`      | The port for the proxy to listen on                                                                                                                 |
| `live.reload.http.host`             | `LIVE_RELOAD_HTTP_HOST`             | `localhost` | The host on which your web application starts                                                                                                       |
| `live.reload.http.port`             | `LIVE_RELOAD_HTTP_PORT`             | `8080`      | The port your web application listens on                                                                                                            |
| `live.reload.http.health`           | `LIVE_RELOAD_HTTP_HEALTH`           | `/health`   | Path to your health-check endpoint                                                                                                                  |
| `live.reload.proxy.grpc.host`       | `LIVE_RELOAD_PROXY_GRPC_HOST`       | `localhost` | The host for the GRPC proxy to start on                                                                                                             |
| `live.reload.proxy.grpc.port`       | `LIVE_RELOAD_PROXY_GRPC_PORT`       | `9001`      | The port for the GRPC proxy to listen on                                                                                                            |
| `live.reload.grpc.host`             | `LIVE_RELOAD_GRPC_HOST`             | `localhost` | The host on which your GRPC application starts                                                                                                      |
| `live.reload.grpc.port`             | `LIVE_RELOAD_GRPC_PORT`             | `8081`      | The port your GRPC application listens on                                                                                                           |
| `live.reload.grpc.health.service`   | `LIVE_RELOAD_GRPC_HEALTH_SERVICE`   | `""`        | Service name to query through `grpc.health.v1.Health` (empty means overall server health)                                                           |
| `live.reload.grpc.target.tls`       | `LIVE_RELOAD_GRPC_TARGET_TLS`       | `false`     | Whether the proxy should connect to the target GRPC server using TLS                                                                                |
| `live.reload.grpc.target.tls.trust` | `LIVE_RELOAD_GRPC_TARGET_TLS_TRUST` | `""`        | Path to a PEM-encoded CA/leaf certificate used to verify the target server (empty falls back to the JVM default truststore)                         |
| `live.reload.grpc.proxy.tls.cert`   | `LIVE_RELOAD_GRPC_PROXY_TLS_CERT`   | `""`        | Path to a PEM-encoded certificate chain used by the proxy listener (paired with the key below)                                                      |
| `live.reload.grpc.proxy.tls.key`    | `LIVE_RELOAD_GRPC_PROXY_TLS_KEY`    | `""`        | Path to a PEM-encoded private key used by the proxy listener (when both this and the cert are set, the proxy listens with TLS instead of plaintext) |
| `live.reload.debug`                 | `LIVE_RELOAD_DEBUG`                 | `false`     | Whether to enable/disable debug output                                                                                                              |
| `live.reload.thread.interrupt.timeout` | `LIVE_RELOAD_THREAD_INTERRUPT_TIMEOUT` | `15000`  | How long (ms) `ThreadInterruptShutdownHook` waits for the application's main thread to exit after `Thread.interrupt()`. If it doesn't, the reload aborts with an unrecoverable error rather than continuing with a stale thread. |

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
    <td><a href="https://github.com/seroperson/jvm-live-reload/blob/main/core/webserver-grpc/src/main/java/me/seroperson/reload/live/webserver/grpc/hook/GrpcHealthCheckStartupHook.java">GrpcHealthCheckStartupHook</a></td>
    <td>Blocks until <code>grpc.health.v1.Health</code> reports <code>SERVING</code> on the target server. Reuses upstream TLS settings when enabled.</td>
  </tr>
  <tr>
    <td><a href="https://github.com/seroperson/jvm-live-reload/blob/main/core/webserver-grpc/src/main/java/me/seroperson/reload/live/webserver/grpc/hook/GrpcHealthCheckShutdownHook.java">GrpcHealthCheckShutdownHook</a></td>
    <td>Blocks until <code>grpc.health.v1.Health</code> stops reporting <code>SERVING</code> (or the channel fails).</td>
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
`ZioAppHookBundle`, `IoAppHookBundle`, `CaskAppHookBundle` and
`GrpcAppHookBundle` (picked automatically once `liveServerType` is set to
`GrpcServerType`). All available options are defined in [HookBundle.scala][4].
You can also override a set of startup/shutdown hooks using the
`liveStartupHooks` and `liveShutdownHooks` keys. For example:

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

### Propagate environment

This plugin provides a feature to propagate custom environment variables to a
reloadable application. While with `sbt` you can make use of [sbt-dotenv][16],
which is [compatible][17] with the plugin, propagating the environment with
`mill` and `gradle` can be tricky, as an application starts within the same
existing JVM process while reloading. So for such purposes there is the
`livePropagateEnv` setting (`propagateEnv` for `gradle`), which accepts
`Map<String, String>` (your custom environment) to pass to an application.

For example, for `mill` it would look like:

```scala
import me.seroperson.reload.live.mill.*

object app extends LiveReloadModule, ScalaModule {
  def forkEnv = Map("BASE_URL" -> "...")
  // Can reuse `forkEnv` or hardcode environment in place
  def livePropagateEnv = Task.Anon { forkEnv() }
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
    <td>See <code>zio-*</code> in <a href="https://github.com/seroperson/jvm-live-reload/tree/main/sbt/src/test/resources">sbt test resources</a> folder.</td>
    <td>Only <code>/health</code> endpoint.</td>
  </tr>
  <tr>
    <td><a href="https://github.com/http4s/http4s">http4s-ember-server</a> + <a href="https://github.com/typelevel/cats-effect">cats-effect</a> + <a href="https://github.com/pureconfig/pureconfig">pureconfig</a></td>
    <td><i>http4s-ember-server</i> <b>0.23.30</b>, <i>cats-effect</i> <b>3.6.1</b>, <i>pureconfig</i> <b>0.17.9</b></td>
    <td>See <code>http4s-*</code> in <a href="https://github.com/seroperson/jvm-live-reload/tree/main/sbt/src/test/resources">sbt test resources</a> folder.</td>
    <td>Only <code>/health</code> endpoint.</td>
  </tr>
  <tr>
    <td><a href="https://github.com/com-lihaoyi/cask">cask</a></td>
    <td><i>cask</i> <b>0.9.7</b></td>
    <td>See <code>cask</code> in <a href="https://github.com/seroperson/jvm-live-reload/tree/main/sbt/src/test/resources">sbt test resources</a> folder.</td>
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
    <td><i>http4k</i> <b>5.47.0.0</b>, but <b>6.x</b> should work too</td>
    <td><a href="https://github.com/seroperson/jvm-live-reload/blob/main/gradle/plugin/plugin/src/functionalTest/kotlin/me.seroperson.reload.live.gradle/LiveReloadHttp4kTest.kt">LiveReloadHttp4kTest.kt</a></td>
    <td>Everything from <a href="#changes-to-the-application-code">this section</a>.</td>
  </tr>
  <tr>
    <td><a href="https://github.com/javalin/javalin">javalin</a>, <a href="https://github.com/casid/jte">jte</a></td>
    <td><i>javalin</i> <b>6.7.0</b>, <i>jte</i> <b>3.2.2</b></td>
    <td><a href="https://github.com/seroperson/jvm-live-reload/blob/main/gradle/plugin/plugin/src/functionalTest/kotlin/me.seroperson.reload.live.gradle/LiveReloadJavalinTest.kt">LiveReloadJavalinTest.kt</a>, <a href="https://github.com/seroperson/jvm-live-reload/blob/main/gradle/plugin/plugin/src/functionalTest/kotlin/me.seroperson.reload.live.gradle/LiveReloadJavalinJteTest.kt">LiveReloadJavalinJteTest.kt</a></td>
    <td>Everything from <a href="#changes-to-the-application-code">this section</a>.</td>
  </tr>
  <tr>
    <td><a href="https://github.com/grpc/grpc-java">grpc-java</a> (Kotlin, unary + streaming + reflection + TLS)</td>
    <td><i>grpc-java</i> <b>1.72.0</b></td>
    <td><a href="https://github.com/seroperson/jvm-live-reload/blob/main/gradle/src/functionalTest/kotlin/me.seroperson.reload.live.gradle/LiveReloadGrpcTest.kt">LiveReloadGrpcTest.kt</a>, <a href="https://github.com/seroperson/jvm-live-reload/blob/main/gradle/src/functionalTest/kotlin/me.seroperson.reload.live.gradle/LiveReloadGrpcStreamingTest.kt">LiveReloadGrpcStreamingTest.kt</a>, <a href="https://github.com/seroperson/jvm-live-reload/blob/main/gradle/src/functionalTest/kotlin/me.seroperson.reload.live.gradle/LiveReloadGrpcReflectionTest.kt">LiveReloadGrpcReflectionTest.kt</a>, <a href="https://github.com/seroperson/jvm-live-reload/blob/main/gradle/src/functionalTest/kotlin/me.seroperson.reload.live.gradle/LiveReloadGrpcTlsTest.kt">LiveReloadGrpcTlsTest.kt</a>, <a href="https://github.com/seroperson/jvm-live-reload/blob/main/gradle/src/functionalTest/kotlin/me.seroperson.reload.live.gradle/LiveReloadGrpcMultiprojectTest.kt">LiveReloadGrpcMultiprojectTest.kt</a></td>
    <td>Expose <code>grpc.health.v1.Health</code> plus everything from <a href="#changes-to-the-application-code">this section</a>.</td>
  </tr>
  <tr>
    <td><a href="https://github.com/scalapb/ScalaPB">scalapb</a> + <a href="https://github.com/grpc/grpc-java">grpc-netty</a></td>
    <td><i>scalapb</i> <b>0.11.17</b> (sbt) / <b>1.0.0-alpha.3</b> (mill), <i>grpc-java</i> <b>1.72.0</b></td>
    <td>See <code>grpc-*</code> in <a href="https://github.com/seroperson/jvm-live-reload/tree/main/sbt/src/test/resources">sbt test resources</a> and <a href="https://github.com/seroperson/jvm-live-reload/tree/main/mill/integration/resources">mill integration resources</a>. Covers Scala 2.13, Scala 3, multiproject layouts, streaming and TLS.</td>
    <td>Expose <code>grpc.health.v1.Health</code> plus everything from <a href="#changes-to-the-application-code">this section</a>.</td>
  </tr>
</table>

Everything was tested under JDK 17, but later versions should work too.

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
[16]: https://github.com/Philippus/sbt-dotenv
[17]: https://github.com/seroperson/jvm-live-reload/tree/main/sbt/src/test/resources/http4s-dotenv
[18]: https://github.com/grpc/grpc/blob/master/doc/health-checking.md
[19]: https://github.com/grpc/grpc/blob/master/doc/server-reflection.md
<!-- prettier-ignore-end -->
