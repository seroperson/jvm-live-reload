package me.seroperson.reload.live.settings;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Configuration settings for the development server.
 *
 * <p>
 * This class manages configuration parameters from multiple sources including Java options, command
 * line arguments, and plugin settings. It provides access to proxy settings, target server
 * settings, and debug configuration.
 *
 * <p>
 * The development server acts as a proxy that sits between the client and the actual application
 * server, enabling live reload functionality.
 */
public class DevServerSettings {

    private static final Pattern SYSTEM_PROPERTY = Pattern.compile("-D([^=]+)=(.*)");
    public static final String LiveReloadProxyHttpHost = "live.reload.proxy.http.host";
    public static final String LiveReloadProxyHttpPort = "live.reload.proxy.http.port";
    public static final String LiveReloadHttpHost = "live.reload.http.host";
    public static final String LiveReloadHttpPort = "live.reload.http.port";
    public static final String LiveReloadHealthPath = "live.reload.http.health";
    public static final String LiveReloadIsDebug = "live.reload.debug";

    private final Map<String, String> javaOptionProperties;
    private final Map<String, String> argsProperties;
    private final Map<String, String> pluginSettings;

    private final DevParameter<Integer> proxyHttpPort =
            new DevParameter<>(LiveReloadProxyHttpPort, "LIVE_RELOAD_PROXY_HTTP_PORT", 9000,
                    String::valueOf, Integer::parseInt);
    private final DevParameter<String> proxyHttpHost =
            new DevParameter<>(LiveReloadProxyHttpHost, "LIVE_RELOAD_PROXY_HTTP_HOST",
                    "0.0.0.0", String::valueOf, Function.identity());

    private final DevParameter<Integer> httpPort = new DevParameter<>(LiveReloadHttpPort,
            "LIVE_RELOAD_HTTP_PORT", 8080, String::valueOf, Integer::parseInt);
    private final DevParameter<String> httpHost = new DevParameter<>(LiveReloadHttpHost,
            "LIVE_RELOAD_HTTP_HOST", "localhost", String::valueOf, Function.identity());

    private final DevParameter<String> healthCheckPath =
            new DevParameter<>(LiveReloadHealthPath, "LIVE_RELOAD_HTTP_HEALTH_CHECK_PATH",
                    "/health", String::valueOf, Function.identity());

    private final DevParameter<Boolean> debug = new DevParameter<>(LiveReloadIsDebug,
            "LIVE_RELOAD_DEBUG", false, String::valueOf, Boolean::parseBoolean);

    /**
     * Creates new development server settings.
     *
     * @param javaOptions    list of Java options (e.g., -Dproperty=value)
     * @param args           list of command line arguments
     * @param pluginSettings map of plugin-specific settings
     */
    public DevServerSettings(List<String> javaOptions, List<String> args,
                             Map<String, String> pluginSettings) {
        this.javaOptionProperties = extractProperties(javaOptions);
        this.argsProperties = extractProperties(args);
        this.pluginSettings = pluginSettings;
    }

    /**
     * Gets all configuration properties merged from different sources.
     *
     * <p>
     * Properties are combined in this specific order so that command line properties win over the
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
        httpPort.putInto(merged);
        httpHost.putInto(merged);
        healthCheckPath.putInto(merged);
        debug.putInto(merged);
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
     * <p>
     * Takes all the options of the format "-Dfoo=bar" and returns them as key-value pairs. If
     * multiple values are provided for the same key, the latest value wins.
     *
     * @param args list of command line arguments
     * @return map of extracted properties
     */
    private static LinkedHashMap<String, String> extractProperties(List<String> args) {
        return args.stream().map(SYSTEM_PROPERTY::matcher).filter(Matcher::matches)
                .collect(Collectors.toMap(m -> m.group(1), m -> m.group(2),
                        (existing, newValue) -> newValue, LinkedHashMap::new));
    }
}
