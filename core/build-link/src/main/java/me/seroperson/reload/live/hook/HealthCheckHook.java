package me.seroperson.reload.live.hook;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient;
import me.seroperson.reload.live.build.BuildLogger;
import me.seroperson.reload.live.settings.DevServerSettings;

/**
 * Base interface for hooks that perform health checks on a server.
 *
 * <p>
 * Health check hooks are used to verify if a server is running and responding correctly.
 * Implementations can use different methods such as HTTP requests, TCP port checks, or other
 * mechanisms to determine server health.
 */
interface HealthCheckHook extends Hook {

  /**
   * Checks if the server at the specified host and port is healthy.
   *
   * @param path the health check path
   * @param host the hostname or IP address to check
   * @param port the port number to check
   * @return true if the server is healthy, false otherwise
   */
  boolean isHealthy(String path, String host, int port);

}
