package me.seroperson.reload.live.runner;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.function.Supplier;
import me.seroperson.reload.live.build.BuildLink;
import me.seroperson.reload.live.build.BuildLogger;
import me.seroperson.reload.live.build.ReloadableServer;
import me.seroperson.reload.live.runner.classloader.NamedURLClassLoader;
import me.seroperson.reload.live.runner.classloader.SharedClassesClassLoader;
import me.seroperson.reload.live.settings.DevServerSettings;
import org.jline.terminal.TerminalBuilder;
import play.dev.filewatch.FileWatchService;

/**
 * Singleton runner for managing development server instances with live reload capabilities.
 *
 * <p>This class provides functionality to start and manage development servers that support
 * automatic reloading when source code changes are detected. It uses a custom classloader hierarchy
 * to enable hot-reloading of application code while maintaining shared classes between reloads.
 *
 * <p>This class follows the singleton pattern and should be accessed via {@link #getInstance()}.
 */
public final class DevServerRunner {

  /**
   * Starts a development server in background mode without blocking the current thread.
   *
   * <p>This method initializes a development server with live reload capabilities, setting up the
   * necessary classloader hierarchy and file watching mechanisms. The server runs in the background
   * and can be controlled via the returned {@link ReloadableServer} instance.
   *
   * @param params the startup parameters containing server configuration and classpath information
   * @param reloadCompile a supplier that triggers compilation and returns the compilation result
   * @param triggerReload a supplier that triggers a reload check and returns whether reload is
   *     needed
   * @param fileWatchService the file watching service for detecting source code changes
   * @param logger the build logger for outputting server status and error messages
   * @return a {@link ReloadableServer} instance that can be used to control the running server
   * @throws IllegalStateException if another dev server is already running
   * @throws RuntimeException if server initialization fails
   */
  public ReloadableServer runBackground(
      StartParams params,
      Supplier<CompileResult> reloadCompile,
      Supplier<Boolean> triggerReload,
      FileWatchService fileWatchService,
      BuildLogger logger) {
    /*
     * @formatter:off
     * rootClassLoader - system classes
     * └── buildLoader - sbt classes
     * └── sharedClassesLoader - delegates specific shared classes to buildLoader, everything else to rootClassLoader
     *     └── dependenciesClassLoader - all jars
     *     └── currentApplicationClassLoader - user code, recreated on reload
     * @formatter:on
     */
    var buildLoader = this.getClass().getClassLoader();
    var rootClassLoader = ClassLoader.getSystemClassLoader().getParent();

    DevServerWrapper wrapper = null;
    try {
      var sharedClasses =
          List.of(
              me.seroperson.reload.live.build.BuildLink.class.getName(),
              me.seroperson.reload.live.build.BuildLogger.class.getName(),
              me.seroperson.reload.live.ReloadGeneration.class.getName(),
              me.seroperson.reload.live.settings.DevServerSettings.class.getName(),
              me.seroperson.reload.live.hook.Hook.class.getName(),
              me.seroperson.reload.live.build.ReloadableServer.class.getName());
      ClassLoader sharedClassesLoader =
          new SharedClassesClassLoader(rootClassLoader, sharedClasses, buildLoader);

      var dependenciesClassLoader =
          new NamedURLClassLoader(
              "DependencyClassLoader", urls(params.getDependencyClasspath()), sharedClassesLoader);

      var reloader =
          new DevServerReloader(
              dependenciesClassLoader,
              reloadCompile,
              triggerReload,
              params.getMonitoredFiles(),
              fileWatchService);

      var mainClass = dependenciesClassLoader.loadClass(params.getMainClassName());
      var constructor =
          mainClass.getConstructor(
              DevServerSettings.class,
              BuildLink.class,
              BuildLogger.class,
              String.class,
              List.class,
              List.class);
      var server =
          (ReloadableServer)
              constructor.newInstance(
                  params.getSettings(),
                  reloader,
                  logger,
                  params.getInternalMainClassName(),
                  params.getStartupHookClasses(),
                  params.getShutdownHookClasses());
      wrapper = new DevServerWrapper(params, logger, server);
      wrapper.start();
      return wrapper;
    } catch (Throwable e) {
      logger.error("Error during proxy server initialization", e);
      if (wrapper != null) {
        try {
          wrapper.close();
          logger.info("Restored build process state after failed start()");
        } catch (Throwable closeErr) {
          e.addSuppressed(closeErr);
        }
      }
      throw new RuntimeException(e);
    }
  }

  /**
   * Starts a development server in blocking mode, waiting for user input to stop.
   *
   * <p>This method starts a development server similar to {@link #runBackground}, but blocks the
   * current thread and displays server information to the user. The server will continue running
   * until the user presses Enter or sends EOF signal.
   *
   * @param params the startup parameters containing server configuration and classpath information
   * @param reloadCompile a supplier that triggers compilation and returns the compilation result
   * @param triggerReload a supplier that triggers a reload check and returns whether reload is
   *     needed
   * @param fileWatchService the file watching service for detecting source code changes
   * @param logger the build logger for outputting server status and error messages
   * @param in the input stream for reading user commands (typically System.in)
   * @param out the output stream for displaying server information (typically System.out)
   * @return a {@link ReloadableServer} instance representing the server that was running
   * @throws RuntimeException if terminal initialization or server startup fails
   */
  public ReloadableServer runBlocking(
      StartParams params,
      Supplier<CompileResult> reloadCompile,
      Supplier<Boolean> triggerReload,
      FileWatchService fileWatchService,
      BuildLogger logger,
      InputStream in,
      OutputStream out) {
    try (var devServer =
        runBackground(params, reloadCompile, triggerReload, fileWatchService, logger)) {

      printBanner(devServer, logger);
      logger.info("   Use " + UNDERLINED + "Enter" + RESET + " to stop and exit");

      var terminalBuilder = TerminalBuilder.builder().streams(in, out).system(true).dumb(true);
      try (var terminal = terminalBuilder.build()) {
        var reader = terminal.reader();
        while (devServer.isRunning()) {
          // 10: Enter
          if (reader.read(100) == 10) {
            break;
          }
        }
      }
      return devServer;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static final String GREEN = "\u001b[32m";
  private static final String RESET = "\u001b[0m";
  private static final String UNDERLINED = "\u001b[4m";

  /** Prints beautiful banner with basic plugin settings */
  public static void printBanner(ReloadableServer server, BuildLogger logger) {
    logger.info("");
    logger.info("🎉 Development Live Reload server successfully started!");
    logger.info("🚀 Serving at:    " + GREEN + server.getProxyUrl() + RESET);
    logger.info("   Proxifying to: " + GREEN + server.getApplicationUrl() + RESET);
    logger.info("ℹ️ Perform a first request to start the underlying server");
  }

  /**
   * Converts a list of files to an array of URLs.
   *
   * <p>This utility method transforms File objects to URL objects suitable for use with
   * URLClassLoader. Each file is converted to a URI and then to a URL.
   *
   * @param files the list of files to convert to URLs
   * @return an array of URLs corresponding to the input files
   * @throws RuntimeException if any file cannot be converted to a valid URL
   */
  public static URL[] urls(List<File> files) {
    return files.stream()
        .map(
            __ -> {
              try {
                return __.toURI().toURL();
              } catch (MalformedURLException e) {
                throw new RuntimeException(e);
              }
            })
        .toArray(URL[]::new);
  }

  private DevServerRunner() {}

  private static class Holder {
    public static final DevServerRunner INSTANCE = new DevServerRunner();
  }

  /**
   * Returns the singleton instance of DevServerRunner.
   *
   * @return the singleton DevServerRunner instance
   */
  public static DevServerRunner getInstance() {
    return Holder.INSTANCE;
  }
}
