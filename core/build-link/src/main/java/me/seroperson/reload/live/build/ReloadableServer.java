package me.seroperson.reload.live.build;

import java.io.Closeable;

/** A server that can be reloaded or stopped. */
public interface ReloadableServer extends Closeable {

  void start();

  boolean isRunning();

  /** Reload the server if necessary. Returns true if was reloaded. */
  boolean reload();

  /** URL clients connect to (the proxy listener), e.g. {@code http://0.0.0.0:9000}. */
  String getProxyUrl();

  /** URL the proxy forwards to (the underlying application), e.g. {@code http://localhost:8080}. */
  String getApplicationUrl();
}
