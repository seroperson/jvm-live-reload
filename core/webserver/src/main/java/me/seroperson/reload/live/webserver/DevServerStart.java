package me.seroperson.reload.live.webserver;

import me.seroperson.reload.live.build.BuildLogger;
import me.seroperson.reload.live.build.BuildLink;
import me.seroperson.reload.live.build.ReloadableServer;
import me.seroperson.reload.live.hook.Hook;
import me.seroperson.reload.live.settings.DevServerSettings;

import java.time.Duration;
import java.io.Closeable;
import java.util.List;
import java.util.Map;
import java.net.URI;
import java.net.ServerSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Objects;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.proxy.ProxyClient;
import io.undertow.server.handlers.proxy.ProxyHandler;
import io.undertow.server.handlers.proxy.SimpleProxyClientProvider;
import io.undertow.server.handlers.builder.PredicatedHandler;
import io.undertow.server.handlers.accesslog.AccessLogHandler;
import io.undertow.server.handlers.accesslog.JBossLoggingAccessLogReceiver;
import io.undertow.predicate.Predicate;
import io.undertow.predicate.PredicatesHandler;
import io.undertow.util.AttachmentKey;

public class DevServerStart implements ReloadableServer {

  private Undertow server;
  private Thread appThread;

  private ClassLoader classLoader;
  private String mainClass;

  private List<Hook> startupHooks;
  private List<Hook> shutdownHooks;

  private final DevServerSettings settings;
  private final BuildLogger logger;
  private final BuildLink buildLink;

  public DevServerStart(DevServerSettings settings, BuildLink buildLink, BuildLogger logger,
      String mainClass, List<String> startupHookClasses, List<String> shutdownHookClasses) {
    this.settings = settings;
    this.mainClass = mainClass;
    this.buildLink = buildLink;
    this.logger = logger;

    startupHooks = startupHookClasses.stream().map(this::initHook).filter(Objects::nonNull)
        .filter(Hook::isAvailable).toList();
    shutdownHooks = shutdownHookClasses.stream().map(this::initHook).filter(Objects::nonNull)
        .filter(Hook::isAvailable).toList();

    logger.info("Found " + startupHooks.size() + " startup hooks:");
    startupHooks.stream().map((v) -> "- " + v.getClass().getSimpleName() + ": " + v.description())
        .forEach(logger::info);
    logger.info("Found " + shutdownHooks.size() + " shutdown hooks:");
    shutdownHooks.stream().map((v) -> "- " + v.getClass().getSimpleName() + ": " + v.description())
        .forEach(logger::info);

    var proxyClientProvider = new ReloadableProxyClient(
        URI.create("http://" + settings.getHttpHost() + ":" + settings.getHttpPort()));
    var proxyHandler = new ProxyHandler(proxyClientProvider, 30000, ResponseCodeHandler.HANDLE_404,
        false, false, /* retries */ 2);

    var handler = new ReloadHandler(this, proxyHandler);

    server =
        Undertow.builder().addHttpListener(settings.getProxyHttpPort(), settings.getProxyHttpHost())
            .setHandler(handler).build();
    server.start();
  }

  private Hook initHook(String className) {
    try {
      return (Hook) Class.forName(className).newInstance();
    } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
      logger.error("Unable to initialize hook: " + className);
      return null;
    }
  }

  private void startInternal(ClassLoader classLoader) {
    this.classLoader = classLoader;
    this.appThread = new Thread(() -> {
      Thread.currentThread().setName("main");
      logger.info("🚀 Starting " + mainClass);
      try {
        Class<?> clazz = classLoader.loadClass(mainClass);
        var mainMethod = clazz.getMethod("main", String[].class);
        mainMethod.invoke(null, (Object) new String[0]);
      } catch (Exception e) {
        if (e.getCause() instanceof InterruptedException) {
          logger.debug("Application thread was interrupted");
        } else {
          logger.error("Failed to invoke main method on " + mainClass + ".");
          stopInternal();
          throw new RuntimeException(e);
        }
      }
    });
    appThread.start();

    runHooks(startupHooks);
  }

  private synchronized void stopInternal() {
    if (appThread != null) {
      logger.info("Stopping " + mainClass);
      appThread.interrupt();

      runHooks(shutdownHooks);

      appThread = null;
    }

    if (classLoader != null) {
      logger.debug("Cleaning up old ClassLoader");
      if (classLoader instanceof Closeable) {
        try {
          ((Closeable) classLoader).close();
        } catch (Exception e) {
          logger.error("Failed to close class loader: " + e.getMessage());
        }
      }
      classLoader = null;
      System.gc();
    }
  }

  private void runHooks(List<Hook> hooks) {
    hooks.forEach((v) -> {
      long start = System.currentTimeMillis();
      v.hook(settings, logger);
      long time = System.currentTimeMillis() - start;
      logger.debug("Running " + v.getClass().getSimpleName() + " took " + time + "ms");
    });
  }

  @Override
  public void stop() {
    server.stop();
    stopInternal();
  }

  @Override
  public boolean reload() {
    var reloadResult = buildLink.reload();
    if (reloadResult instanceof ClassLoader) {
      // New application classes
      logger.info("🔃 Reloading an application");
      stopInternal();
      startInternal((ClassLoader) reloadResult);
      logger.debug("Finished reloading");
      return true;
    } else if (reloadResult == null) {
      // No change in the application classes
      logger.debug("No change in the application classes");
      return false;
    } else if (reloadResult instanceof Throwable) {
      // case NonFatal(t) => Failure(t) // An error we can display
      // case t: Throwable => throw t // An error that we can't handle
      throw new RuntimeException((Throwable) reloadResult);
    }
    return false;
  }

}
