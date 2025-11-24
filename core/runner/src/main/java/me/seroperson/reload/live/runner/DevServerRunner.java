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
import org.jline.utils.NonBlockingReader;
import play.dev.filewatch.FileWatchService;

/**
 * Singleton runner for managing development server instances with live reload capabilities.
 *
 * <p>This class provides functionality to start and manage development servers that support
 * automatic reloading when source code changes are detected. It uses a custom classloader hierarchy
 * to enable hot-reloading of application code while maintaining shared classes between reloads.
 *
 * <p>The classloader hierarchy is structured as:
 *
 * <pre>
 * rootClassLoader (system classes)
 * └── buildLoader (build tool classes)
 *     └── sharedClassesLoader (delegates specific shared classes)
 *         └── dependenciesClassLoader (application dependencies)
 *             └── currentApplicationClassLoader (user code, recreated on reload)
 * </pre>
 *
 * <p>This class follows the singleton pattern and should be accessed via {@link #getInstance()}.
 */
public final class DevServerRunner {

  private DevServerReloader reloader;

  /**
   * Starts a development server in background mode without blocking the current thread.
   *
   * <p>This method initializes a development server with live reload capabilities, setting up the
   * necessary classloader hierarchy and file watching mechanisms. The server runs in the background
   * and can be controlled via the returned {@link DevServer} instance.
   *
   * @param params the startup parameters containing server configuration and classpath information
   * @param reloadCompile a supplier that triggers compilation and returns the compilation result
   * @param triggerReload a supplier that triggers a reload check and returns whether reload is
   *     needed
   * @param fileWatchService the file watching service for detecting source code changes
   * @param logger the build logger for outputting server status and error messages
   * @return a {@link DevServer} instance that can be used to control the running server
   * @throws IllegalStateException if another dev server is already running
   * @throws RuntimeException if server initialization fails
   */
  public DevServer runBackground(
      StartParams params,
      Supplier<CompileResult> reloadCompile,
      Supplier<Boolean> triggerReload,
      FileWatchService fileWatchService,
      BuildLogger logger) {
    if (reloader != null) {
      throw new IllegalStateException(
          "Cannot run a dev server because another one is already running!");
    }

    // Set Java system properties
    // settings.getMergedProperties().forEach(System::setProperty);

    /*
      rootClassLoader - system classes
      └── buildLoader - sbt classes
      └── sharedClassesLoader - delegates specific shared classes to buildLoader, everything else to rootClassLoader
          └── dependenciesClassLoader - all jars
          └── currentApplicationClassLoader - user code, recreated on reload
    */
    var buildLoader = this.getClass().getClassLoader();
    var rootClassLoader = java.lang.ClassLoader.getSystemClassLoader().getParent();

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

      reloader =
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

      return new DevServer() {
        @Override
        public BuildLink buildLink() {
          return reloader;
        }

        @Override
        public boolean reload() {
          return server.reload();
        }

        @Override
        public void close() {
          logger.debug("Running DevServerRunner.close()");
          server.stop();
          reloader.close();

          // Remove Java system properties
          params
              .getSettings()
              .getMergedProperties()
              .forEach((key, __) -> System.clearProperty(key));

          reloader = null;
        }
      };
    } catch (Throwable e) {
      reloader = null;
      logger.error("Error during proxy server initialization", e);
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
   * <p>This method displays:
   *
   * <ul>
   *   <li>The proxy server URL where the application is accessible
   *   <li>The underlying server URL being proxied
   *   <li>Instructions for stopping the server
   * </ul>
   *
   * @param params the startup parameters containing server configuration and classpath information
   * @param reloadCompile a supplier that triggers compilation and returns the compilation result
   * @param triggerReload a supplier that triggers a reload check and returns whether reload is
   *     needed
   * @param fileWatchService the file watching service for detecting source code changes
   * @param logger the build logger for outputting server status and error messages
   * @param in the input stream for reading user commands (typically System.in)
   * @param out the output stream for displaying server information (typically System.out)
   * @return a {@link DevServer} instance representing the server that was running
   * @throws RuntimeException if terminal initialization or server startup fails
   */
  public DevServer runBlocking(
      StartParams params,
      Supplier<CompileResult> reloadCompile,
      Supplier<Boolean> triggerReload,
      FileWatchService fileWatchService,
      BuildLogger logger,
      InputStream in,
      OutputStream out) {
    try (var devServer =
        runBackground(params, reloadCompile, triggerReload, fileWatchService, logger)) {
      var GREEN = "\u001b[32m";
      var RESET = "\u001b[0m";
      var UNDERLINED = "\u001b[4m";

      var settings = params.getSettings();

      logger.info("");
      logger.info("🎉 Development Live Reload server successfully started!");
      logger.info(
          "🚀 Serving at:    "
              + GREEN
              + "http://"
              + settings.getProxyHttpHost()
              + ":"
              + settings.getProxyHttpPort()
              + RESET);
      logger.info(
          "   Proxifying to: "
              + GREEN
              + "http://"
              + settings.getHttpHost()
              + ":"
              + settings.getHttpPort()
              + RESET);
      logger.info("ℹ️ Perform a first request to start the underlying server");
      logger.info("   Use " + UNDERLINED + "Enter" + RESET + " to stop and exit");

      try (var terminal = TerminalBuilder.builder().streams(in, out).build()) {
        terminal.echo(false);
        waitEOF(terminal.reader());
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      return devServer;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void waitEOF(NonBlockingReader reader) throws IOException {
    var symbol = reader.read();
    // 4, 13: STOP on Ctrl-D or Enter
    // 11: Clear screen
    // 10: Enter
    if (symbol == 4 || symbol == 13 || symbol == 11) {
      waitEOF(reader);
    } else if (symbol == 10) {
      System.out.println("🛑 Stopping the application");
    } else {
      waitEOF(reader);
    }
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
   * <p>This method uses the initialization-on-demand holder pattern to ensure thread-safe lazy
   * initialization of the singleton instance.
   *
   * @return the singleton DevServerRunner instance
   */
  public static DevServerRunner getInstance() {
    return Holder.INSTANCE;
  }
}
