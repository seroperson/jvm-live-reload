package me.seroperson.reload.live.settings;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Configuration settings for the development server.
 *
 * <p>This class manages configuration parameters from multiple sources including Java options,
 * command line arguments, and plugin settings. It provides access to proxy settings, target server
 * settings, and debug configuration.
 *
 * <p>The development server acts as a proxy that sits between the client and the actual application
 * server, enabling live reload functionality.
 */
public final class DevServerSettings {

  private static final Pattern SYSTEM_PROPERTY = Pattern.compile("-D([^=]+)=(.*)");
  public static final String LiveReloadProxyHttpHost = "live.reload.proxy.http.host";
  public static final String LiveReloadProxyHttpPort = "live.reload.proxy.http.port";
  public static final String LiveReloadProxyGrpcHost = "live.reload.proxy.grpc.host";
  public static final String LiveReloadProxyGrpcPort = "live.reload.proxy.grpc.port";
  public static final String LiveReloadHttpHost = "live.reload.http.host";
  public static final String LiveReloadHttpPort = "live.reload.http.port";
  public static final String LiveReloadGrpcHost = "live.reload.grpc.host";
  public static final String LiveReloadGrpcPort = "live.reload.grpc.port";
  public static final String LiveReloadHealthPath = "live.reload.http.health";
  public static final String LiveReloadGrpcHealthService = "live.reload.grpc.health.service";
  public static final String LiveReloadGrpcTargetTls = "live.reload.grpc.target.tls";
  public static final String LiveReloadGrpcTargetTlsTrust = "live.reload.grpc.target.tls.trust";
  public static final String LiveReloadGrpcProxyTlsCert = "live.reload.grpc.proxy.tls.cert";
  public static final String LiveReloadGrpcProxyTlsKey = "live.reload.grpc.proxy.tls.key";
  public static final String LiveReloadIsDebug = "live.reload.debug";
  public static final String LiveReloadThreadInterruptTimeout =
      "live.reload.thread.interrupt.timeout";

  private final Map<String, String> javaOptionProperties;
  private final Map<String, String> argsProperties;
  private final Map<String, String> pluginSettings;

  private final DevParameter<Integer> proxyHttpPort =
      new DevParameter<>(
          LiveReloadProxyHttpPort,
          "LIVE_RELOAD_PROXY_HTTP_PORT",
          9000,
          String::valueOf,
          Integer::parseInt);
  private final DevParameter<String> proxyHttpHost =
      new DevParameter<>(
          LiveReloadProxyHttpHost,
          "LIVE_RELOAD_PROXY_HTTP_HOST",
          "localhost",
          String::valueOf,
          Function.identity());

  private final DevParameter<Integer> httpPort =
      new DevParameter<>(
          LiveReloadHttpPort, "LIVE_RELOAD_HTTP_PORT", 8080, String::valueOf, Integer::parseInt);
  private final DevParameter<String> httpHost =
      new DevParameter<>(
          LiveReloadHttpHost,
          "LIVE_RELOAD_HTTP_HOST",
          "localhost",
          String::valueOf,
          Function.identity());

  private final DevParameter<String> healthCheckPath =
      new DevParameter<>(
          LiveReloadHealthPath,
          "LIVE_RELOAD_HTTP_HEALTH",
          "/health",
          String::valueOf,
          Function.identity());

  private final DevParameter<Boolean> debug =
      new DevParameter<>(
          LiveReloadIsDebug, "LIVE_RELOAD_DEBUG", false, String::valueOf, Boolean::parseBoolean);

  private final DevParameter<Long> threadInterruptTimeoutMs =
      new DevParameter<>(
          LiveReloadThreadInterruptTimeout,
          "LIVE_RELOAD_THREAD_INTERRUPT_TIMEOUT",
          15000L,
          String::valueOf,
          Long::parseLong);

  private final DevParameter<Integer> proxyGrpcPort =
      new DevParameter<>(
          LiveReloadProxyGrpcPort,
          "LIVE_RELOAD_PROXY_GRPC_PORT",
          9001,
          String::valueOf,
          Integer::parseInt);
  private final DevParameter<String> proxyGrpcHost =
      new DevParameter<>(
          LiveReloadProxyGrpcHost,
          "LIVE_RELOAD_PROXY_GRPC_HOST",
          "localhost",
          String::valueOf,
          Function.identity());

  private final DevParameter<Integer> grpcPort =
      new DevParameter<>(
          LiveReloadGrpcPort, "LIVE_RELOAD_GRPC_PORT", 8081, String::valueOf, Integer::parseInt);
  private final DevParameter<String> grpcHost =
      new DevParameter<>(
          LiveReloadGrpcHost,
          "LIVE_RELOAD_GRPC_HOST",
          "localhost",
          String::valueOf,
          Function.identity());

  private final DevParameter<String> grpcHealthService =
      new DevParameter<>(
          LiveReloadGrpcHealthService,
          "LIVE_RELOAD_GRPC_HEALTH_SERVICE",
          "",
          String::valueOf,
          Function.identity());

  private final DevParameter<Boolean> grpcTargetTls =
      new DevParameter<>(
          LiveReloadGrpcTargetTls,
          "LIVE_RELOAD_GRPC_TARGET_TLS",
          false,
          String::valueOf,
          Boolean::parseBoolean);

  private final DevParameter<String> grpcTargetTlsTrust =
      new DevParameter<>(
          LiveReloadGrpcTargetTlsTrust,
          "LIVE_RELOAD_GRPC_TARGET_TLS_TRUST",
          "",
          String::valueOf,
          Function.identity());

  private final DevParameter<String> grpcProxyTlsCert =
      new DevParameter<>(
          LiveReloadGrpcProxyTlsCert,
          "LIVE_RELOAD_GRPC_PROXY_TLS_CERT",
          "",
          String::valueOf,
          Function.identity());

  private final DevParameter<String> grpcProxyTlsKey =
      new DevParameter<>(
          LiveReloadGrpcProxyTlsKey,
          "LIVE_RELOAD_GRPC_PROXY_TLS_KEY",
          "",
          String::valueOf,
          Function.identity());

  /**
   * Creates new development server settings.
   *
   * @param javaOptions list of Java options (e.g., -Dproperty=value)
   * @param args list of command line arguments
   * @param pluginSettings map of plugin-specific settings
   */
  public DevServerSettings(
      List<String> javaOptions, List<String> args, Map<String, String> pluginSettings) {
    this.javaOptionProperties = extractProperties(javaOptions);
    this.argsProperties = extractProperties(args);
    this.pluginSettings = pluginSettings;
  }

  /**
   * Gets all configuration properties merged from different sources.
   *
   * <p>Properties are combined in this specific order so that command line properties win over the
   * configured ones, making them more useful for development and debugging.
   *
   * @return a map containing all merged properties
   */
  public LinkedHashMap<String, String> getMergedProperties() {
    var merged = new LinkedHashMap<String, String>();
    merged.putAll(javaOptionProperties);
    merged.putAll(argsProperties);
    merged.putAll(pluginSettings);
    proxyHttpPort.putInto(merged);
    proxyHttpHost.putInto(merged);
    proxyGrpcPort.putInto(merged);
    proxyGrpcHost.putInto(merged);
    httpPort.putInto(merged);
    httpHost.putInto(merged);
    grpcPort.putInto(merged);
    grpcHost.putInto(merged);
    healthCheckPath.putInto(merged);
    grpcHealthService.putInto(merged);
    grpcTargetTls.putInto(merged);
    grpcTargetTlsTrust.putInto(merged);
    grpcProxyTlsCert.putInto(merged);
    grpcProxyTlsKey.putInto(merged);
    debug.putInto(merged);
    threadInterruptTimeoutMs.putInto(merged);
    return merged;
  }

  /**
   * Gets the HTTP port for the proxy server.
   *
   * @return the proxy server port (default: 9000)
   */
  public Integer getProxyHttpPort() {
    return proxyHttpPort.getValueOrDefault(javaOptionProperties, argsProperties, pluginSettings);
  }

  /**
   * Gets the HTTP host for the proxy server.
   *
   * @return the proxy server host (default: "localhost")
   */
  public String getProxyHttpHost() {
    return proxyHttpHost.getValueOrDefault(javaOptionProperties, argsProperties, pluginSettings);
  }

  /**
   * Gets the HTTP port for the target application server.
   *
   * @return the target server port (default: 8080)
   */
  public Integer getHttpPort() {
    return httpPort.getValueOrDefault(javaOptionProperties, argsProperties, pluginSettings);
  }

  /**
   * Gets the HTTP host for the target application server.
   *
   * @return the target server host (default: "localhost")
   */
  public String getHttpHost() {
    return httpHost.getValueOrDefault(javaOptionProperties, argsProperties, pluginSettings);
  }

  /**
   * Gets the Health Check path for the target application server.
   *
   * @return the health check path (default: "/health")
   */
  public String getHealthCheckPath() {
    return healthCheckPath.getValueOrDefault(javaOptionProperties, argsProperties, pluginSettings);
  }

  /**
   * Checks if debug mode is enabled.
   *
   * @return true if debug mode is enabled, false otherwise (default: false)
   */
  public boolean isDebug() {
    return debug.getValueOrDefault(javaOptionProperties, argsProperties, pluginSettings);
  }

  /**
   * Maximum time, in milliseconds, that {@code ThreadInterruptShutdownHook} will wait for the
   * application's main thread to terminate after sending it an interrupt. If the thread is still
   * alive when the timeout elapses, the hook throws an {@code UnrecoverableException} so the proxy
   * tears the reload down instead of continuing with a stale thread.
   *
   * @return interrupt-join timeout in ms (default: 15000)
   */
  public Long getThreadInterruptTimeoutMs() {
    return threadInterruptTimeoutMs.getValueOrDefault(
        javaOptionProperties, argsProperties, pluginSettings);
  }

  /**
   * Gets the GRPC port for the proxy server.
   *
   * @return the proxy server GRPC port (default: 9001)
   */
  public Integer getProxyGrpcPort() {
    return proxyGrpcPort.getValueOrDefault(javaOptionProperties, argsProperties, pluginSettings);
  }

  /**
   * Gets the GRPC host for the proxy server.
   *
   * @return the proxy server GRPC host (default: "localhost")
   */
  public String getProxyGrpcHost() {
    return proxyGrpcHost.getValueOrDefault(javaOptionProperties, argsProperties, pluginSettings);
  }

  /**
   * Gets the GRPC port for the target application server.
   *
   * @return the target server GRPC port (default: 8081)
   */
  public Integer getGrpcPort() {
    return grpcPort.getValueOrDefault(javaOptionProperties, argsProperties, pluginSettings);
  }

  /**
   * Gets the GRPC host for the target application server.
   *
   * @return the target server GRPC host (default: "localhost")
   */
  public String getGrpcHost() {
    return grpcHost.getValueOrDefault(javaOptionProperties, argsProperties, pluginSettings);
  }

  /**
   * Gets the GRPC Health Check service name for the target application server.
   *
   * @return the GRPC health service name (default: "" - overall server health)
   */
  public String getGrpcHealthService() {
    return grpcHealthService.getValueOrDefault(
        javaOptionProperties, argsProperties, pluginSettings);
  }

  /**
   * Whether the proxy should connect to the target GRPC server using TLS.
   *
   * @return true if the proxy-to-target channel must use transport security (default: false)
   */
  public boolean isGrpcTargetTls() {
    return grpcTargetTls.getValueOrDefault(javaOptionProperties, argsProperties, pluginSettings);
  }

  /**
   * Path to a PEM-encoded trust manager (CA cert) for verifying the target server's certificate.
   * Useful when the backend uses a self-signed cert in development. Empty falls back to the JVM
   * default truststore.
   *
   * @return trust material path, or empty for JVM default (default: "")
   */
  public String getGrpcTargetTlsTrust() {
    return grpcTargetTlsTrust.getValueOrDefault(
        javaOptionProperties, argsProperties, pluginSettings);
  }

  /**
   * Path to a PEM-encoded certificate chain used by the proxy server. When both this and {@link
   * #getGrpcProxyTlsKey()} are non-empty, the proxy listens with TLS instead of plaintext.
   *
   * @return certificate chain path, or empty string when TLS is disabled (default: "")
   */
  public String getGrpcProxyTlsCert() {
    return grpcProxyTlsCert.getValueOrDefault(javaOptionProperties, argsProperties, pluginSettings);
  }

  /**
   * Path to a PEM-encoded private key used by the proxy server. Paired with {@link
   * #getGrpcProxyTlsCert()} to enable TLS on the proxy listener.
   *
   * @return private key path, or empty string when TLS is disabled (default: "")
   */
  public String getGrpcProxyTlsKey() {
    return grpcProxyTlsKey.getValueOrDefault(javaOptionProperties, argsProperties, pluginSettings);
  }

  /**
   * Parses a string value as a port number.
   *
   * @param portValue the string representation of the port
   * @return the parsed port number
   * @throws IllegalArgumentException if the port value is not a valid integer
   */
  public static Integer parsePort(String portValue) {
    try {
      return Integer.parseInt(portValue);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid port argument: " + portValue);
    }
  }

  /**
   * Extracts system properties from command line arguments.
   *
   * <p>Takes all the options of the format "-Dfoo=bar" and returns them as key-value pairs. If
   * multiple values are provided for the same key, the latest value wins.
   *
   * @param args list of command line arguments
   * @return map of extracted properties
   */
  private static LinkedHashMap<String, String> extractProperties(List<String> args) {
    return args.stream()
        .map(SYSTEM_PROPERTY::matcher)
        .filter(Matcher::matches)
        .collect(
            Collectors.toMap(
                m -> m.group(1),
                m -> m.group(2),
                (existing, newValue) -> newValue,
                LinkedHashMap::new));
  }
}
