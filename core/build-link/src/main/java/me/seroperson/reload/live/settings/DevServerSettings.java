package me.seroperson.reload.live.settings;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DevServerSettings {

  private static final Pattern SYSTEM_PROPERTY = Pattern.compile("-D([^=]+)=(.*)");

  private final Map<String, String> javaOptionProperties;
  private final Map<String, String> argsProperties;
  private final Map<String, String> pluginSettings;

  private final DevParameter<Integer> proxyHttpPort =
      new DevParameter<Integer>("live.reload.proxy.http.port", "LIVE_RELOAD_PROXY_HTTP_PORT", 9000,
          String::valueOf, Integer::parseInt);
  private final DevParameter<String> proxyHttpHost =
      new DevParameter<String>("live.reload.proxy.http.host", "LIVE_RELOAD_PROXY_HTTP_HOST",
          "localhost", String::valueOf, Function.identity());

  private final DevParameter<Integer> httpPort = new DevParameter<Integer>("live.reload.http.port",
      "LIVE_RELOAD_HTTP_PORT", 8080, String::valueOf, Integer::parseInt);
  private final DevParameter<String> httpHost = new DevParameter<String>("live.reload.http.host",
      "LIVE_RELOAD_HTTP_HOST", "localhost", String::valueOf, Function.identity());

  private final DevParameter<Boolean> debug = new DevParameter<Boolean>("live.reload.debug",
      "LIVE_RELOAD_DEBUG", false, String::valueOf, Boolean::parseBoolean);

  public DevServerSettings(List<String> javaOptions, List<String> args,
      Map<String, String> pluginSettings) {
    this.javaOptionProperties = extractProperties(javaOptions);
    this.argsProperties = extractProperties(args);
    this.pluginSettings = pluginSettings;
  }

  public LinkedHashMap<String, String> getMergedProperties() {
    // Properties are combined in this specific order so that command line
    // properties win over the configured one, making them more useful.
    var merged = new LinkedHashMap<String, String>();
    merged.putAll(javaOptionProperties);
    merged.putAll(argsProperties);
    merged.putAll(pluginSettings);
    proxyHttpPort.putInto(merged);
    proxyHttpHost.putInto(merged);
    httpPort.putInto(merged);
    httpHost.putInto(merged);
    debug.putInto(merged);
    return merged;
  }

  public Integer getProxyHttpPort() {
    return proxyHttpPort.getValueOrDefault(javaOptionProperties, argsProperties, pluginSettings);
  }

  public String getProxyHttpHost() {
    return proxyHttpHost.getValueOrDefault(javaOptionProperties, argsProperties, pluginSettings);
  }

  public Integer getHttpPort() {
    return httpPort.getValueOrDefault(javaOptionProperties, argsProperties, pluginSettings);
  }

  public String getHttpHost() {
    return httpHost.getValueOrDefault(javaOptionProperties, argsProperties, pluginSettings);
  }

  public boolean isDebug() {
    return debug.getValueOrDefault(javaOptionProperties, argsProperties, pluginSettings);
  }

  public static Integer parsePort(String portValue) {
    try {
      return Integer.parseInt(portValue);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid port argument: " + portValue);
    }
  }

  /**
   * Take all the options of the format "-Dfoo=bar" and return them as a key value pairs
   */
  private static LinkedHashMap<String, String> extractProperties(List<String> args) {
    // latest value wins
    return args.stream().map(SYSTEM_PROPERTY::matcher).filter(Matcher::matches)
        .collect(Collectors.toMap(m -> m.group(1), m -> m.group(2),
            (existing, newValue) -> newValue, LinkedHashMap::new));
  }
}
