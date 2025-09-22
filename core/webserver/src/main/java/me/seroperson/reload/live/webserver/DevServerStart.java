package me.seroperson.reload.live.webserver;

import me.seroperson.reload.live.ReloadGeneration;
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
import java.security.AccessControlContext;
import java.security.AccessController;
import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Objects;
import java.lang.reflect.InvocationTargetException;
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

  private static final AccessControlContext accessControlContext = AccessController.getContext();

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

    if (!settings.isDebug()) {
      silenceJboss();
    }

    var proxyClientProvider = new ReloadableProxyClient(logger,
        URI.create("http://" + settings.getHttpHost() + ":" + settings.getHttpPort()));
    var proxyHandler = new ProxyHandler(proxyClientProvider, 30000, ResponseCodeHandler.HANDLE_404,
        false, false, /* retries */ 2);

    var handler = new ReloadHandler(logger, this, proxyHandler);

    server =
        Undertow.builder().addHttpListener(settings.getProxyHttpPort(), settings.getProxyHttpHost())
            .setHandler(handler).build();
    server.start();
  }

  private Hook initHook(String className) {
    try {
      return (Hook) Class.forName(className).newInstance();
    } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
      logger.error("Unable to initialize hook: " + className, e);
      e.printStackTrace();
      return null;
    }
  }

  private synchronized void startInternal(ReloadGeneration generation) {
    this.classLoader = generation.getReloadedClassLoader();
    this.appThread = new Thread(() -> {
      var currentThread = Thread.currentThread();
      currentThread.setName("main");
      logger.info("🚀 Starting " + mainClass);
      try {
        Class<?> clazz = classLoader.loadClass(mainClass);
        var mainMethod = clazz.getMethod("main", String[].class);
        logger.debug("Running with Context ClassLoader: " + currentThread.getContextClassLoader()
            + " in thread " + currentThread);
        mainMethod.invoke(null, (Object) new String[0]);
        logger.debug("After Application.main(String[]) execution");
      } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
        logger.error("Failed to invoke main method on " + mainClass, e);
        stopInternal();
        throw new RuntimeException(e);
      } catch (InvocationTargetException e) {
        // logger.error("Got InvocationTargetException. Its' classloader: " +
        // e.getCause().getClass().getClassLoader());
        logger.debug(
            "Got InvocationTargetException: Its' cause: " + e.getCause().getClass().getName());
        // logger.error("Current classloader: " + getClass().getClassLoader());
        if (e.getCause().getClass().getName().equals("zio.FiberFailure")
            || e.getCause() instanceof InterruptedException) {
          // doing nothing
          logger.debug("Application thread was interrupted (cause: "
              + e.getCause().getClass().getName() + ")");
        } else {
          logger.error("Error in application main thread (cause: " + e.getCause() + ")",
              e.getCause());
        }
      }
    });
    appThread.setContextClassLoader(classLoader);
    appThread.start();

    runHooks(appThread, classLoader, startupHooks);
  }

  private synchronized void stopInternal() {
    if (appThread != null) {
      logger.info("Stopping " + mainClass);

      runHooks(appThread, classLoader, shutdownHooks);

      appThread = null;
    }

    if (classLoader != null) {
      logger.debug("Cleaning up old ClassLoader");
      if (classLoader instanceof Closeable) {
        try {
          ((Closeable) classLoader).close();
        } catch (Exception e) {
          logger.error("Failed to close class loader", e);
        }
      }
      classLoader = null;
      System.gc();
    }
  }

  private void runHooks(Thread th, ClassLoader cl, List<Hook> hooks) {
    hooks.forEach((v) -> {
      logger.debug("Running " + v.getClass().getSimpleName());
      long start = System.currentTimeMillis();
      v.hook(th, cl, settings, logger);
      long time = System.currentTimeMillis() - start;
      logger.debug(v.getClass().getSimpleName() + " took " + time + "ms");
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
    logger.info("Got reloadResult: " + reloadResult);
    if (reloadResult instanceof ReloadGeneration) {
      // New application classes
      logger.info("🔃 Reloading an application");
      stopInternal();
      startInternal((ReloadGeneration) reloadResult);
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
