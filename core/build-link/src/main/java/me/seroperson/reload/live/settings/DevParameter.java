package me.seroperson.reload.live.settings;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Represents a configurable parameter for the development server.
 *
 * <p>This class encapsulates a parameter that can be configured through multiple sources with a
 * defined precedence order. It supports type-safe parsing and string conversion of parameter
 * values.
 *
 * @param <T> the type of the parameter value
 */
public class DevParameter<T> {

  private final String argKey;
  private final String envKey;
  private final T defaultValue;
  private final Function<T, String> toString;
  private final Function<String, T> parseValue;
  private volatile Optional<T> value;

  /**
   * Creates a new development parameter.
   *
   * @param argKey the key used in command line arguments and system properties
   * @param envKey the key used in environment variables
   * @param defaultValue the default value if no configuration is found
   * @param toString function to convert the value to string representation
   * @param parseValue function to parse string value to the target type
   */
  public DevParameter(
      String argKey,
      String envKey,
      T defaultValue,
      Function<T, String> toString,
      Function<String, T> parseValue) {
    this.argKey = argKey;
    this.envKey = envKey;
    this.defaultValue = defaultValue;
    this.toString = toString;
    this.parseValue = parseValue;
  }

  /**
   * Gets the parameter value from various configuration sources.
   *
   * <p>Checking order (highest to lowest precedence):
   *
   * <ol>
   *   <li>Command line arguments
   *   <li>Java options
   *   <li>System properties
   *   <li>Plugin settings overrides
   *   <li>Environment variables
   * </ol>
   *
   * @param javaOptionProperties Java option properties map
   * @param args command line arguments map
   * @param pluginSettings plugin configuration settings map
   * @return Optional containing the parsed value, or empty if not found
   */
  public Optional<T> getValue(
      Map<String, String> javaOptionProperties,
      Map<String, String> args,
      Map<String, String> pluginSettings) {
    if (value == null) {
      value =
          Optional.ofNullable(args.get(argKey))
              .or(() -> Optional.ofNullable(javaOptionProperties.get(argKey)))
              .or(() -> Optional.ofNullable(System.getProperty(argKey)))
              .or(() -> Optional.ofNullable(pluginSettings.get(argKey)))
              .or(() -> Optional.ofNullable(System.getenv(envKey)))
              .map(parseValue);
    }
    return value;
  }

  /**
   * Gets the parameter value or returns the default value if not configured.
   *
   * @param javaOptionProperties Java option properties map
   * @param args command line arguments map
   * @param pluginSettings plugin configuration settings map
   * @return the configured value or the default value
   */
  public T getValueOrDefault(
      Map<String, String> javaOptionProperties,
      Map<String, String> args,
      Map<String, String> pluginSettings) {
    var value = getValue(javaOptionProperties, args, pluginSettings);
    if (value == null) return defaultValue;
    else return value.orElse(defaultValue);
  }

  /**
   * Puts the current value (or default) into the provided map as a string.
   *
   * @param map the map to put the value into
   */
  public void putInto(Map<String, String> map) {
    if (value == null) {
      map.put(argKey, toString.apply(defaultValue));
    } else {
      map.put(argKey, toString.apply(value.orElse(defaultValue)));
    }
  }
}
