package me.seroperson.reload.live.webserver;

import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.proxy.ProxyHandler;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import me.seroperson.reload.live.BaseDevServerStart;
import me.seroperson.reload.live.UnrecoverableException;
import me.seroperson.reload.live.build.BuildLink;
import me.seroperson.reload.live.build.BuildLogger;
import me.seroperson.reload.live.settings.DevServerSettings;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

public class DevServerStart extends BaseDevServerStart<Undertow> {

  private XnioWorker currentGenerationWorker;
  private ReloadableProxyClient proxyClientProvider;

  public DevServerStart(
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
    } else {
      silenceJboss();
    }

    // Track resources we have already allocated so we can release them if a later
    // step throws. Set to null after isRunning flips true; BaseDevServerStart.close()
    // takes over from there.
    XnioWorker workerToCleanup = null;
    Undertow proxyToCleanup = null;
    try {
      this.currentGenerationWorker = createWorker();
      workerToCleanup = this.currentGenerationWorker;

      this.proxyClientProvider =
          new ReloadableProxyClient(
              logger,
              URI.create("http://" + settings.getHttpHost() + ":" + settings.getHttpPort()));
      this.proxyClientProvider.setCurrentGenerationWorker(currentGenerationWorker);

      // @formatter:off
      var proxyHandler =
          new ProxyHandler(
              proxyClientProvider,
              /* maxRequestTime */ -1,
              ResponseCodeHandler.HANDLE_404,
              /* rewriteHostHeader */ false,
              /* reuseXForwarded */ false,
              2);
      // @formatter:on

      var handler = new ReloadHandler(logger, this, proxyHandler);

      proxyServer =
          Undertow.builder()
              .addHttpListener(settings.getProxyHttpPort(), settings.getProxyHttpHost())
              .setHandler(handler)
              .setServerOption(UndertowOptions.SHUTDOWN_TIMEOUT, 1000)
              .build();
      proxyToCleanup = proxyServer;
      proxyServer.start();

      appThreadGroup = new ThreadGroup("app");

      isRunning.set(true);
      // Ownership transferred to the running server; do not clean up below.
      workerToCleanup = null;
      proxyToCleanup = null;
    } finally {
      if (workerToCleanup != null) {
        // start() failed after the worker was created. Release its threads, and
        // null out the field so cleanupServerForOldGeneration() does not later try
        // to shutdownNow() on an already-shutdown worker.
        try {
          workerToCleanup.shutdownNow();
        } catch (Throwable shutdownErr) {
          logger.error("Failed to shutdown XnioWorker after start() failure", shutdownErr);
        }
        this.currentGenerationWorker = null;
      }
      if (proxyToCleanup != null) {
        try {
          proxyToCleanup.stop();
        } catch (Throwable stopErr) {
          logger.error("Failed to stop Undertow listener after start() failure", stopErr);
        }
      }
    }
  }

  @Override
  protected void prepareServerForNewGeneration() {
    // cleanupServerForOldGeneration() has already shut down (and nulled) the previous
    // worker; if createWorker() throws here, the field stays null and the exception
    // propagates up through BaseDevServerStart.reload(), rather than silently leaving
    // a dead worker installed in the proxy client.
    this.currentGenerationWorker = createWorker();
    proxyClientProvider.setCurrentGenerationWorker(currentGenerationWorker);
  }

  @Override
  protected void cleanupServerForOldGeneration() {
    if (currentGenerationWorker != null) {
      currentGenerationWorker.shutdownNow();
      currentGenerationWorker = null;
    }
  }

  @Override
  protected void stopProxyServer() {
    proxyServer.stop();
  }

  @Override
  public String getProxyUrl() {
    return "http://" + settings.getProxyHttpHost() + ":" + settings.getProxyHttpPort();
  }

  @Override
  public String getApplicationUrl() {
    return "http://" + settings.getHttpHost() + ":" + settings.getHttpPort();
  }

  private XnioWorker createWorker() {
    var ioThreads = Math.max(Runtime.getRuntime().availableProcessors(), 2);
    try {
      return Xnio.getInstance(getClass().getClassLoader())
          .createWorker(
              OptionMap.builder()
                  .set(Options.WORKER_IO_THREADS, ioThreads)
                  .set(Options.CONNECTION_HIGH_WATER, 1000000)
                  .set(Options.CONNECTION_LOW_WATER, 1000000)
                  .set(Options.WORKER_TASK_CORE_THREADS, ioThreads * 8)
                  .set(Options.WORKER_TASK_MAX_THREADS, ioThreads * 8)
                  .set(Options.TCP_NODELAY, true)
                  .set(Options.CORK, true)
                  .getMap());
    } catch (IOException e) {
      throw new UnrecoverableException(
          "Failed to create XnioWorker for the HTTP proxy listener", e);
    }
  }

  // Shameful copy-n-paste from cask.main.Main.silenceJboss
  private void silenceJboss() {
    // Some jboss classes litter logs from their static initializers. This is a
    // workaround to stop this rather annoying behavior.
    var tmp = System.out;
    System.setOut(null);
    org.jboss.threads.Version.getVersionString(); // this causes the static initializer to be run
    System.setOut(tmp);

    // Other loggers print way too much information. Set them to only print
    // interesting stuff.
    var level = java.util.logging.Level.WARNING;
    java.util.logging.Logger.getLogger("org.jboss").setLevel(level);
    java.util.logging.Logger.getLogger("org.xnio").setLevel(level);
    java.util.logging.Logger.getLogger("io.undertow").setLevel(level);
  }
}
