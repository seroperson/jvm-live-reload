package me.seroperson.reload.live.hook;

import java.net.HttpURLConnection;
import java.net.URI;

/**
 * Health check hook that uses REST API calls to determine server health.
 *
 * <p>This hook performs health checks by making HTTP GET requests to a "/health" endpoint on the
 * server. The server is considered healthy if the endpoint returns a 200 status code within the
 * timeout period.
 */
interface RestApiHealthCheckHook extends HealthCheckHook {

  default boolean isHealthy(String path, String host, int port) {
    try {
      // Create a neat value object to hold the URL
      var url = new URI("http://" + host + ":" + port + path).toURL();
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setReadTimeout(500);
      connection.setConnectTimeout(500);
      // Open a connection(?) on the URL(?) and cast the response(??)
      try (var stream = connection.getInputStream()) {
        return connection.getResponseCode() == 200;
      } catch (Exception e) {
        return false;
      }
    } catch (Exception e) {
      return false;
    }
  }
}
