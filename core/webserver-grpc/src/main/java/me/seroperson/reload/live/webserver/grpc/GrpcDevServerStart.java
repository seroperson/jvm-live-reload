package me.seroperson.reload.live.webserver.grpc;

import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.ServerCredentials;
import io.grpc.TlsServerCredentials;
import java.io.File;
import java.io.IOException;
import java.util.List;
import me.seroperson.reload.live.BaseDevServerStart;
import me.seroperson.reload.live.UnrecoverableException;
import me.seroperson.reload.live.build.BuildLink;
import me.seroperson.reload.live.build.BuildLogger;
import me.seroperson.reload.live.settings.DevServerSettings;

/**
 * Main entry point for the GRPC proxy development server.
 *
 * <p>This class manages the lifecycle of a GRPC proxy server that sits between GRPC clients and the
 * actual application server. It handles automatic reloading of the application when code changes
 * are detected.
 */
public class GrpcDevServerStart extends BaseDevServerStart<Server> {

  private ReloadableGrpcProxyHandler proxyHandler;

  /**
   * Creates a new GRPC development server.
   *
   * @param settings the development server settings
   * @param buildLink the build link for triggering recompilation
   * @param logger the logger for outputting messages
   * @param mainClass the main class to run
   * @param startupHookClasses list of startup hook class names
   * @param shutdownHookClasses list of shutdown hook class names
   */
  public GrpcDevServerStart(
      DevServerSettings settings,
      BuildLink buildLink,
      BuildLogger logger,
      String mainClass,
      List<String> startupHookClasses,
      List<String> shutdownHookClasses) {
    super(settings, buildLink, logger, mainClass, startupHookClasses, shutdownHookClasses);
  }

  @Override
  public void start() {
    if (settings.isDebug()) {
      dumpHooks();
    }

    String targetHost = settings.getGrpcHost();
    int targetPort = settings.getGrpcPort();
    boolean targetTls = settings.isGrpcTargetTls();
    String targetTlsTrust = settings.getGrpcTargetTlsTrust();

    this.proxyHandler =
        new ReloadableGrpcProxyHandler(
            logger, this, targetHost, targetPort, targetTls, targetTlsTrust);

    ServerCredentials proxyCredentials = buildProxyServerCredentials();
    boolean proxyTls = !(proxyCredentials instanceof InsecureServerCredentials);

    try {
      proxyServer =
          Grpc.newServerBuilderForPort(settings.getProxyGrpcPort(), proxyCredentials)
              .fallbackHandlerRegistry(new GrpcProxyHandlerRegistry(logger, proxyHandler))
              .build()
              .start();

      logger.info(
          "🚀 GRPC proxy server started on port "
              + settings.getProxyGrpcPort()
              + (proxyTls ? " (TLS)" : "")
              + " -> "
              + targetHost
              + ":"
              + targetPort
              + (targetTls ? " (TLS)" : ""));
    } catch (IOException e) {
      logger.error("Failed to start GRPC proxy server", e);
      throw new RuntimeException(e);
    }

    appThreadGroup = new ThreadGroup("app");
    isRunning.set(true);
  }

  @Override
  protected void prepareServerForNewGeneration() {
    // Refresh the proxy channel to the new instance
    proxyHandler.refreshChannel();
  }

  @Override
  protected void cleanupServerForOldGeneration() {
    // Close the proxy channel
    proxyHandler.closeChannel();
  }

  private ServerCredentials buildProxyServerCredentials() {
    String certPath = settings.getGrpcProxyTlsCert();
    String keyPath = settings.getGrpcProxyTlsKey();
    boolean certSet = certPath != null && !certPath.isEmpty();
    boolean keySet = keyPath != null && !keyPath.isEmpty();
    if (!certSet && !keySet) {
      return InsecureServerCredentials.create();
    }
    if (certSet ^ keySet) {
      throw new UnrecoverableException(
          "Both '"
              + DevServerSettings.LiveReloadGrpcProxyTlsCert
              + "' and '"
              + DevServerSettings.LiveReloadGrpcProxyTlsKey
              + "' must be set to enable TLS on the proxy listener.");
    }
    try {
      return TlsServerCredentials.create(new File(certPath), new File(keyPath));
    } catch (IOException e) {
      throw new UnrecoverableException(
          "Failed to read GRPC proxy TLS material from "
              + certPath
              + " / "
              + keyPath
              + ": "
              + e.getMessage());
    }
  }

  @Override
  protected void stopProxyServer() {
    logger.info("🛑 Stopping the GRPC application");
    if (proxyServer != null) {
      proxyServer.shutdownNow();
    }
  }

  @Override
  public String getProxyUrl() {
    String cert = settings.getGrpcProxyTlsCert();
    String key = settings.getGrpcProxyTlsKey();
    boolean tls = cert != null && !cert.isEmpty() && key != null && !key.isEmpty();
    return (tls ? "grpcs://" : "grpc://")
        + settings.getProxyGrpcHost()
        + ":"
        + settings.getProxyGrpcPort();
  }

  @Override
  public String getApplicationUrl() {
    return (settings.isGrpcTargetTls() ? "grpcs://" : "grpc://")
        + settings.getGrpcHost()
        + ":"
        + settings.getGrpcPort();
  }
}
