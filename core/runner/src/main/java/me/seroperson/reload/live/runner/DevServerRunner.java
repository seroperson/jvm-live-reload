package me.seroperson.reload.live.runner;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import play.dev.filewatch.FileWatchService;
import me.seroperson.reload.live.build.BuildLogger;
import me.seroperson.reload.live.build.BuildLink;
import me.seroperson.reload.live.build.ReloadableServer;
import me.seroperson.reload.live.settings.DevServerSettings;
import me.seroperson.reload.live.runner.classloader.NamedURLClassLoader;
import me.seroperson.reload.live.runner.classloader.SharedClassesClassLoader;

public final class DevServerRunner {

  private DevServerReloader reloader;

  public DevServer run(DevServerSettings settings, List<File> dependencyClasspath,
      Supplier<CompileResult> reloadCompile, Supplier<Boolean> triggerReload,
      List<File> monitoredFiles, FileWatchService fileWatchService, String mainClassName,
      String internalMainClassName, Object reloadLock, List<String> startupHookClasses,
      List<String> shutdownHookClasses, BuildLogger logger) {
    if (reloader != null) {
      throw new IllegalStateException(
          "Cannot run a dev server because another one is already running!");
    }

    // Set Java system properties
    // settings.getMergedProperties().forEach(System::setProperty);

    /**
     * @formatter:off
     * rootClassLoader - system classes
     * └── buildLoader - sbt classes
     * └── sharedClassesLoader - delegates specific shared classes to buildLoader, everything else to rootClassLoader
     *     └── dependenciesClassLoader - all jars
     *         └── currentApplicationClassLoader - user code, recreated on reload
     * @formatter:on
     */

    var buildLoader = this.getClass().getClassLoader();
    var rootClassLoader = java.lang.ClassLoader.getSystemClassLoader().getParent();

    try {
      var sharedClasses = List.of(me.seroperson.reload.live.build.BuildLink.class.getName(),
          me.seroperson.reload.live.build.BuildLogger.class.getName(),
          me.seroperson.reload.live.ReloadGeneration.class.getName(),
          me.seroperson.reload.live.settings.DevServerSettings.class.getName(),
          me.seroperson.reload.live.hook.Hook.class.getName(),
          me.seroperson.reload.live.build.ReloadableServer.class.getName());
      ClassLoader sharedClassesLoader =
          new SharedClassesClassLoader(rootClassLoader, sharedClasses, buildLoader);

      var dependenciesClassLoader = new NamedURLClassLoader("DependencyClassLoader",
          urls(dependencyClasspath), sharedClassesLoader);

      reloader = new DevServerReloader(dependenciesClassLoader, reloadCompile, triggerReload,
          monitoredFiles, fileWatchService, reloadLock);

      var mainClass = dependenciesClassLoader.loadClass(mainClassName);
      var constructor = mainClass.getConstructor(DevServerSettings.class, BuildLink.class,
          BuildLogger.class, String.class, List.class, List.class);
      var server = (ReloadableServer) constructor.newInstance(settings, reloader, logger,
          internalMainClassName, startupHookClasses, shutdownHookClasses);

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
          settings.getMergedProperties().forEach((key, __) -> System.clearProperty(key));

          reloader = null;
        }
      };
    } catch (Throwable e) {
      reloader = null;
      logger.error("Error during proxy server initialization", e);
      throw new RuntimeException(e);
    }
  }

  public static URL[] urls(List<File> files) {
    return files.stream().map(__ -> {
      try {
        return __.toURI().toURL();
      } catch (MalformedURLException e) {
        throw new RuntimeException(e);
      }
    }).toArray(URL[]::new);
  }

  private DevServerRunner() {}

  private static class Holder {
    public static final DevServerRunner INSTANCE = new DevServerRunner();
  }

  public static DevServerRunner getInstance() {
    return Holder.INSTANCE;
  }
}
