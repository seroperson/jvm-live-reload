package me.seroperson.reload.live.mill

import me.seroperson.reload.live.settings.DevServerSettings

object DevSettingsKeys {
  // format: off
  val LiveReloadProxyHttpHost: String = DevServerSettings.LiveReloadProxyHttpHost
  val LiveReloadProxyHttpPort: String = DevServerSettings.LiveReloadProxyHttpPort
  val LiveReloadHttpHost: String = DevServerSettings.LiveReloadHttpHost
  val LiveReloadHttpPort: String = DevServerSettings.LiveReloadHttpPort
  val LiveReloadHealthPath: String = DevServerSettings.LiveReloadHealthPath
  val LiveReloadIsDebug: String = DevServerSettings.LiveReloadIsDebug
  // format: on
}
