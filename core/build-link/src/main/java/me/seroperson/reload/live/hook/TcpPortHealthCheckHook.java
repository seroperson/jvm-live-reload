package me.seroperson.reload.live.hook;

import java.net.ServerSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.DatagramSocket;
import java.io.IOException;
import me.seroperson.reload.live.build.BuildLogger;

interface TcpPortHealthCheckHook extends HealthCheckHook {

  // https://stackoverflow.com/a/48828373/5246998
  default boolean isHealthy(String host, int port) {
    ServerSocket ss = null;
    DatagramSocket ds = null;
    try {
      ss = new ServerSocket(port);
      ss.setReuseAddress(true);
      ds = new DatagramSocket(port);
      ds.setReuseAddress(true);
      return true;
    } catch (IOException e) {
    } finally {
      if (ds != null) {
        ds.close();
      }

      if (ss != null) {
        try {
          ss.close();
        } catch (IOException e) {
          /* should not be thrown */
        }
      }
    }

    return false;
  }

}
