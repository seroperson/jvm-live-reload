package me.seroperson.reload.live;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import me.seroperson.reload.live.build.BuildLink;
import me.seroperson.reload.live.build.BuildLogger;
import me.seroperson.reload.live.build.ReloadableServer;
import me.seroperson.reload.live.hook.Hook;
import me.seroperson.reload.live.settings.DevServerSettings;

/**
 * Abstract base class for development servers that support hot-reloading.
 *
 * <p>This class provides common functionality for managing the lifecycle of a proxy server that
 * sits between clients and the actual application server, handling automatic reloading when code
 * changes are detected.
 *
 * @param <S> the type of the proxy server (e.g., io.grpc.Server, io.undertow.Undertow)
 */
public abstract class BaseDevServerStart<S> implements ReloadableServer {

  protected final AtomicBoolean isRunning = new AtomicBoolean(false);
  protected S proxyServer;
  protected ThreadGroup appThreadGroup;
  protected Thread appThread;

  protected ClassLoader classLoader;
  protected final String mainClass;

  protected final List<Hook> startupHooks;
  protected final List<Hook> shutdownHooks;

  protected final DevServerSettings settings;
  protected final BuildLogger logger;
  protected final BuildLink buildLink;

  /**
   * Creates a new development server.
   *
   * @param settings the development server settings
   * @param buildLink the build link for triggering recompilation
   * @param logger the logger for outputting messages
   * @param mainClass the main class to run
   * @param startupHookClasses list of startup hook class names
   * @param shutdownHookClasses list of shutdown hook class names
   */
  protected BaseDevServerStart(
      DevServerSettings settings,
      BuildLink buildLink,
      BuildLogger logger,
      String mainClass,
      List<String> startupHookClasses,
      List<String> shutdownHookClasses) {
    this.settings = settings;
    this.mainClass = mainClass;
    this.buildLink = buildLink;
    this.logger = logger;

    startupHooks =
        startupHookClasses.stream()
            .map(this::initHook)
            .filter(Objects::nonNull)
            .filter(Hook::isAvailable)
            .toList();
    shutdownHooks =
        shutdownHookClasses.stream()
            .map(this::initHook)
            .filter(Objects::nonNull)
            .filter(Hook::isAvailable)
            .toList();
  }

  /**
   * Initializes a hook from its class name.
   *
   * @param className the fully qualified class name of the hook
   * @return the initialized hook, or null if initialization failed
   */
  protected Hook initHook(String className) {
    try {
      return (Hook) Class.forName(className).getDeclaredConstructor().newInstance();
    } catch (ClassNotFoundException
        | InstantiationException
        | InvocationTargetException
        | IllegalAccessException
        | NoSuchMethodException e) {
      logger.error("Unable to initialize hook: " + className, e);
      return null;
    }
  }

  /**
   * Starts the underlying application server with the given generation.
   *
   * @param generation the reload generation containing the new class loader
   */
  protected synchronized void startInternal(ReloadGeneration generation) {
    if (!isRunning.get()) {
      throw new UnrecoverableException(
          "Unable to start underlying application without a running proxy.");
    }

    try {
      // Perform server-specific preparation before starting the application
      prepareServerForNewGeneration();

      this.classLoader = generation.getReloadedClassLoader();
      this.appThread =
          new Thread(
              appThreadGroup,
              () -> {
                logger.info("🚀 Starting " + mainClass);
                try {
                  Class<?> clazz = classLoader.loadClass(mainClass);
                  var mainMethod = clazz.getMethod("main", String[].class);
                  var currentThread = Thread.currentThread();
                  logger.debug(
                      "Running with Context ClassLoader: "
                          + currentThread.getContextClassLoader()
                          + " in thread "
                          + currentThread);
                  mainMethod.invoke(null, (Object) new String[0]);
                  logger.debug("After Application.main(String[]) execution");
                } catch (ClassNotFoundException
                    | NoSuchMethodException
                    | IllegalAccessException e) {
                  logger.error("Failed to invoke main method on " + mainClass, e);
                  stopInternal();
                  throw new RuntimeException(e);
                } catch (InvocationTargetException e) {
                  // Don't log InterruptedException, as likely they're intended
                  if (!(e.getCause() instanceof InterruptedException)) {
                    logger.error("Error in application main thread", e);
                  }
                }
              },
              "main");
      appThread.setContextClassLoader(classLoader);
      appThread.start();

      runHooks(appThread, classLoader, startupHooks);
    } catch (RuntimeException | Error t) {
      try {
        stopInternal();
      } catch (Throwable cleanupErr) {
        t.addSuppressed(cleanupErr);
      }
      throw t;
    }
  }

  /** Stops the currently running application instance. */
  protected synchronized void stopInternal() {
    if (appThread == null && classLoader == null) {
      return;
    }

    // Perform server-specific cleanup before stopping the application
    cleanupServerForOldGeneration();

    Thread th = appThread;
    ClassLoader cl = classLoader;
    appThread = null;
    classLoader = null;

    try {
      if (th != null) {
        logger.debug("Stopping " + mainClass);
        runHooks(th, cl, shutdownHooks);
      }
    } finally {
      if (cl != null) {
        logger.debug("Cleaning up old ClassLoader");
        if (cl instanceof Closeable) {
          try {
            ((Closeable) cl).close();
          } catch (Exception e) {
            logger.error("Failed to close class loader", e);
          }
        }
        System.gc();
      }
    }
  }

  /**
   * Runs the specified hooks on the given thread with the given class loader.
   *
   * @param th the application thread
   * @param cl the class loader
   * @param hooks the list of hooks to run
   */
  protected void runHooks(Thread th, ClassLoader cl, List<Hook> hooks) {
    hooks.forEach(
        (v) -> {
          var hookClassName = v.getClass().getSimpleName();
          logger.debug("Running " + hookClassName);
          long start = System.currentTimeMillis();
          v.hook(th, cl, settings, logger);
          long time = System.currentTimeMillis() - start;
          logger.debug(hookClassName + " took " + time + "ms");
        });
  }

  @Override
  public boolean reload() {
    var reloadResult = buildLink.reload();
    if (reloadResult instanceof ReloadGeneration) {
      var casted = (ReloadGeneration) reloadResult;
      // New application classes
      logger.info("🔃 Reloading an application");
      stopInternal();
      try {
        startInternal(casted);
      } catch (RuntimeException | Error t) {
        throw new UnrecoverableException(
            "Reload failed; restart `liveReload` after fixing the cause", t);
      }
      logger.debug("Finished reloading");
      return true;
    } else if (reloadResult == null) {
      // No change in the application classes
      logger.debug("No change in the application classes");
      return false;
    } else if (reloadResult instanceof Throwable) {
      throw new RuntimeException((Throwable) reloadResult);
    }
    return false;
  }

  @Override
  public boolean isRunning() {
    return isRunning.get();
  }

  /** Dumps information about configured hooks to the logger. */
  protected void dumpHooks() {
    logger.debug("Found " + startupHooks.size() + " startup hooks:");
    startupHooks.stream()
        .map((v) -> "- " + v.getClass().getSimpleName() + ": " + v.description())
        .forEach(logger::debug);
    logger.debug("Found " + shutdownHooks.size() + " shutdown hooks:");
    shutdownHooks.stream()
        .map((v) -> "- " + v.getClass().getSimpleName() + ": " + v.description())
        .forEach(logger::debug);
  }

  /**
   * Prepares the proxy server for a new generation of the application. This is called before
   * starting a new instance of the application.
   */
  protected abstract void prepareServerForNewGeneration();

  /**
   * Cleans up server resources for the old generation. This is called before stopping the current
   * instance of the application.
   */
  protected abstract void cleanupServerForOldGeneration();

  /** Stops the proxy server. This is called when closing the development server. */
  protected abstract void stopProxyServer();

  @Override
  public synchronized void close() throws IOException {
    if (isRunning.get()) {
      logger.info("🛑 Stopping the application");
      stopProxyServer();
      if (appThread != null) {
        stopInternal();
      }
      buildLink.close();
      isRunning.set(false);
      logger.debug("Application and proxy server were successfully stopped");
    }
  }
}
