package me.seroperson.reload.live.settings;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class DevParameter<T> {

  private String argKey;
  private String envKey;
  private T defaultValue;
  private Function<T, String> toString;
  private Function<String, T> parseValue;
  private volatile Optional<T> value;

  public DevParameter(String argKey, String envKey, T defaultValue, Function<T, String> toString,
      Function<String, T> parseValue) {
    this.argKey = argKey;
    this.envKey = envKey;
    this.defaultValue = defaultValue;
    this.toString = toString;
    this.parseValue = parseValue;
  }

  // Checking order:
  // - args
  // - javaOptions
  // - System.getProperty
  // - Overrides via plugin setttings
  // - Environment variables
  public Optional<T> getValue(Map<String, String> javaOptionProperties, Map<String, String> args,
      Map<String, String> pluginSettings) {
    if (value == null) {
      value = Optional.ofNullable(args.get(argKey))
          .or(() -> Optional.ofNullable(javaOptionProperties.get(argKey)))
          .or(() -> Optional.ofNullable(System.getProperty(argKey)))
          .or(() -> Optional.ofNullable(pluginSettings.get(argKey)))
          .or(() -> Optional.ofNullable(System.getenv(envKey))).map(parseValue);
    }
    return value;
  }

  public T getValueOrDefault(Map<String, String> javaOptionProperties, Map<String, String> args,
      Map<String, String> pluginSettings) {
    return getValue(javaOptionProperties, args, pluginSettings).orElse(defaultValue);
  }

  public void putInto(Map<String, String> map) {
    map.put(argKey, toString.apply(value.orElse(defaultValue)));
  }

}
