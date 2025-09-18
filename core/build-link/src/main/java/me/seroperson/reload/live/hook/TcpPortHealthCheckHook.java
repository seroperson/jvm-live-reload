package me.seroperson.reload.live.hook;

import java.net.ServerSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import me.seroperson.reload.live.build.BuildLogger;

interface TcpPortHealthCheckHook extends HealthCheckHook {

  // https://stackoverflow.com/a/48828373/5246998
  default boolean isHealthy(String host, int port) {
    try (ServerSocket serverSocket = new ServerSocket()) {
      // setReuseAddress(false) is required only on macOS,
      // otherwise the code will not work correctly on that platform
      serverSocket.setReuseAddress(false);
      serverSocket.bind(new InetSocketAddress(InetAddress.getByName(host), port), 1);
      return true;
    } catch (Exception ex) {
      return false;
    }
  }

}
