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
import me.seroperson.reload.live.runner.classloader.DelegatingClassLoader;
import me.seroperson.reload.live.runner.classloader.NamedURLClassLoader;

public final class DevServerRunner {

  private DevServerReloader reloader;

  public DevServer run(DevServerSettings settings, ClassLoader commonClassLoader,
      List<File> dependencyClasspath, Supplier<CompileResult> reloadCompile,
      Function<ClassLoader, ClassLoader> assetsClassLoader, Supplier<Boolean> triggerReload,
      List<File> monitoredFiles, FileWatchService fileWatchService, String mainClassName,
      String internalMainClassName, Object reloadLock, List<String> startupHookClasses,
      List<String> shutdownHookClasses, BuildLogger logger) {
    if (reloader != null) {
      throw new IllegalStateException(
          "Cannot run a dev server because another one is already running!");
    }

    // Set Java system properties
    // settings.getMergedProperties().forEach(System::setProperty);

    /*
     * We need to do a bit of classloader magic to run the application.
     *
     * There are six classloaders:
     *
     * 1. buildLoader, the classloader of sbt and the sbt plugin.
     *
     * 2. commonLoader, a classloader that persists across calls to run. This classloader is stored
     * inside the liveCommonClassloader task. This classloader will load the classes for the H2
     * database if it finds them in the user's classpath. This allows H2's in-memory database state
     * to survive across calls to run.
     *
     * 3. delegatingLoader, a special classloader that overrides class loading to delegate shared
     * classes for build link to the buildLoader, and accesses the
     * reloader.currentApplicationClassLoader for resource loading to make user resources available
     * to dependency classes. Has the commonLoader as its parent.
     *
     * 4. applicationLoader, contains the application dependencies. Has the delegatingLoader as its
     * parent. Classes from the commonLoader and the delegatingLoader are checked for loading first.
     *
     * 5. liveAssetsClassLoader, serves assets from all projects, prefixed as configured. It does no
     * caching, and doesn't need to be reloaded each time the assets are rebuilt.
     *
     * 6. reloader.currentApplicationClassLoader, contains the user classes and resources. Has
     * applicationLoader as its parent, where the application dependencies are found, and which will
     * delegate through to the buildLoader via the delegatingLoader for the shared link. Resources
     * are actually loaded by the delegatingLoader, where they are available to both the reloader
     * and v the applicationLoader. This classloader is recreated on reload.
     *
     * Someone working on this code in the future might want to tidy things up by splitting some of
     * the custom logic out of the URLClassLoaders and into their own simpler ClassLoader
     * implementations. The curious cycle between applicationLoader and
     * reloader.currentApplicationClassLoader could also use some attention.
     */

    var buildLoader = this.getClass().getClassLoader();

    try {
      /*
       * ClassLoader that delegates loading of shared build link classes to the buildLoader. Also
       * accesses the reloader resources to make these available to the applicationLoader, creating
       * a full circle for resource loading.
       */
      var sharedClasses = List.of(me.seroperson.reload.live.build.BuildLink.class.getName(),
          me.seroperson.reload.live.build.BuildLogger.class.getName(),
          me.seroperson.reload.live.settings.DevServerSettings.class.getName(),
          me.seroperson.reload.live.hook.Hook.class.getName(),
          me.seroperson.reload.live.build.ReloadableServer.class.getName());
      ClassLoader delegatingLoader = new DelegatingClassLoader(commonClassLoader, sharedClasses,
          buildLoader, () -> reloader.getClassLoader());

      var applicationLoader = new NamedURLClassLoader("DependencyClassLoader",
          urls(dependencyClasspath), delegatingLoader);

      // Need to call the assetsClassLoader function _after_ (!) the beforeStarted run
      // hooks ran
      var assetsLoader = assetsClassLoader.apply(applicationLoader);

      reloader = new DevServerReloader(assetsLoader, reloadCompile, triggerReload, monitoredFiles,
          fileWatchService, reloadLock);

      var mainClass = applicationLoader.loadClass(mainClassName);
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
