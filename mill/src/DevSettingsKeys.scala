package me.seroperson.reload.live.mill

import me.seroperson.reload.live.settings.DevServerSettings

object DevSettingsKeys {
  // format: off
  val LiveReloadProxyHttpHost: String = DevServerSettings.LiveReloadProxyHttpHost
  val LiveReloadProxyHttpPort: String = DevServerSettings.LiveReloadProxyHttpPort
  val LiveReloadProxyGrpcHost: String = DevServerSettings.LiveReloadProxyGrpcHost
  val LiveReloadProxyGrpcPort: String = DevServerSettings.LiveReloadProxyGrpcPort
  val LiveReloadHttpHost: String = DevServerSettings.LiveReloadHttpHost
  val LiveReloadHttpPort: String = DevServerSettings.LiveReloadHttpPort
  val LiveReloadGrpcHost: String = DevServerSettings.LiveReloadGrpcHost
  val LiveReloadGrpcPort: String = DevServerSettings.LiveReloadGrpcPort
  val LiveReloadHealthPath: String = DevServerSettings.LiveReloadHealthPath
  val LiveReloadGrpcHealthService: String = DevServerSettings.LiveReloadGrpcHealthService
  val LiveReloadGrpcTargetTls: String = DevServerSettings.LiveReloadGrpcTargetTls
  val LiveReloadGrpcTargetTlsTrust: String = DevServerSettings.LiveReloadGrpcTargetTlsTrust
  val LiveReloadGrpcProxyTlsCert: String = DevServerSettings.LiveReloadGrpcProxyTlsCert
  val LiveReloadGrpcProxyTlsKey: String = DevServerSettings.LiveReloadGrpcProxyTlsKey
  val LiveReloadIsDebug: String = DevServerSettings.LiveReloadIsDebug
  val LiveReloadThreadInterruptTimeout: String = DevServerSettings.LiveReloadThreadInterruptTimeout
  // format: on
}
