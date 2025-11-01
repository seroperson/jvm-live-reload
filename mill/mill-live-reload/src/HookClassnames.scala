package me.seroperson.reload.live.mill

object HookClassnames {
  // format: off
  val IoAppStartup = "me.seroperson.reload.live.hook.io.IoAppStartupHook"
  val ZioAppStartup = "me.seroperson.reload.live.hook.zio.ZioAppStartupHook"
  val RestApiHealthCheckStartup = "me.seroperson.reload.live.hook.RestApiHealthCheckStartupHook"

  val IoAppShutdown = "me.seroperson.reload.live.hook.io.IoAppShutdownHook"
  val ZioAppShutdown = "me.seroperson.reload.live.hook.zio.ZioAppShutdownHook"
  val RuntimeShutdown = "me.seroperson.reload.live.hook.RuntimeShutdownHook"
  val RestApiHealthCheckShutdown = "me.seroperson.reload.live.hook.RestApiHealthCheckShutdownHook"
  val ThreadInterruptShutdown = "me.seroperson.reload.live.hook.ThreadInterruptShutdownHook"
  // format: on
}
