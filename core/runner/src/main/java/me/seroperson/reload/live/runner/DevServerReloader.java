package me.seroperson.reload.live.runner;

import static java.util.stream.Collectors.joining;
import static me.seroperson.reload.live.runner.DevServerRunner.urls;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.stream.Stream;
import me.seroperson.reload.live.build.BuildLink;
import play.dev.filewatch.FileWatchService;
import play.dev.filewatch.FileWatcher;
import me.seroperson.reload.live.runner.CompileResult.CompileFailure;
import me.seroperson.reload.live.runner.CompileResult.CompileSuccess;
import me.seroperson.reload.live.runner.classloader.DelegatedResourcesClassLoader;

class DevServerReloader implements BuildLink, Closeable {

  private static final AccessControlContext accessControlContext = AccessController.getContext();

  private final Object reloadLock;

  private final Supplier<CompileResult> compile;

  private final Supplier<Boolean> triggerReload;

  private final ClassLoader baseClassLoader;

  // The current classloader for the application
  private volatile URLClassLoader currentApplicationClassLoader;

  // Flag to force a reload on the next request.
  // This is set if a compile error occurs.
  private volatile boolean forceReloadNextTime = false;

  // Whether any source files have changed since the last request.
  private volatile boolean changed = false;

  // Last time the classpath was modified in millis. Used to determine whether
  // anything on the
  // classpath has changed as a result of compilation, and therefore a new
  // classloader is needed
  // and the app needs to be reloaded.
  private volatile long lastModified = 0L;

  private final FileWatcher watcher;

  private final AtomicInteger classLoaderVersion = new AtomicInteger(0);

  DevServerReloader(ClassLoader baseClassLoader, Supplier<CompileResult> compile,
      Supplier<Boolean> triggerReload, List<File> monitoredFiles, FileWatchService fileWatchService,
      Object reloadLock) {
    this.baseClassLoader = baseClassLoader;
    this.compile = compile;
    this.triggerReload = triggerReload;
    if (!monitoredFiles.isEmpty() && fileWatchService != null) {
      // Create the watcher, updates the changed boolean when a file has changed:
      Callable<Void> callable = () -> {
        changed = true;
        return null;
      };
      this.watcher = fileWatchService.watch(monitoredFiles, callable);
    } else {
      this.watcher = null;
    }
    this.reloadLock = reloadLock;
  }

  /** Execute f with context ClassLoader of Reloader */
  private static <T> T withReloaderContextClassLoader(Supplier<T> f) {
    var thread = Thread.currentThread();
    var oldLoader = thread.getContextClassLoader();
    // we use accessControlContext & AccessController to avoid a ClassLoader leak
    // (ProtectionDomain class)
    return AccessController.doPrivileged((PrivilegedAction<T>) () -> {
      try {
        thread.setContextClassLoader(DevServerReloader.class.getClassLoader());
        return f.get();
      } finally {
        thread.setContextClassLoader(oldLoader);
      }
    }, accessControlContext);
  }

  private Object reload(boolean shouldReload) {
    // Run the reload task, which will trigger everything to compile
    CompileResult compileResult = compile.get();
    if (compileResult instanceof CompileFailure) {
      var result = (CompileFailure) compileResult;
      // We force reload next time because compilation failed this time
      forceReloadNextTime = true;
      return result.getException();
    } else if (compileResult instanceof CompileSuccess) {
      var result = (CompileSuccess) compileResult;
      var cp = result.getClasspath();

      // We only want to reload if the classpath has changed.
      // Assets don't live on the classpath, so they won't trigger a reload.
      long newLastModified =
          cp.stream().filter(File::exists).flatMap(DevServerReloader::listRecursively)
              .mapToLong(File::lastModified).max().orElse(0L);
      var triggered = newLastModified > lastModified;
      lastModified = newLastModified;

      if (triggered || shouldReload || currentApplicationClassLoader == null) {
        // Create a new classloader
        currentApplicationClassLoader = new DelegatedResourcesClassLoader(
            "ReloadableClassLoader(v" + classLoaderVersion.incrementAndGet() + ")", urls(cp),
            baseClassLoader);
        return currentApplicationClassLoader;
      }
      return null; // null means nothing changed
    } else {
      return null; // null means nothing changed
    }
  }

  /**
   * Contrary to its name, this doesn't necessarily reload the app. It is invoked on every request,
   * and will only trigger a reload of the app if something has changed.
   *
   * <p>
   * Since this communicates across classloaders, it must return only simple objects.
   *
   * @return Either<br>
   *         - {@link Throwable} - If something went wrong (eg, a compile error). <br>
   *         - {@link ClassLoader} - If the classloader has changed, and the application should be
   *         reloaded.<br>
   *         - {@code null} - If nothing changed.
   */
  @Override
  public Object reload() {
    synchronized (reloadLock) {
      if (changed || (triggerReload != null && triggerReload.get()) || forceReloadNextTime
          || currentApplicationClassLoader == null) {
        var shouldReload = forceReloadNextTime;
        changed = false;
        forceReloadNextTime = false;
        // use Reloader context ClassLoader to avoid ClassLoader leaks in
        // sbt/scala-compiler threads
        return withReloaderContextClassLoader(() -> reload(shouldReload));
      } else {
        return null; // null means nothing changed
      }
    }
  }

  private static Stream<File> listRecursively(File file) {
    try {
      return Files.walk(file.toPath())
          .filter(path -> !(Files.isDirectory(path) && path.equals(file.toPath())))
          .map(Path::toFile);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void close() {
    currentApplicationClassLoader = null;
    if (watcher != null)
      watcher.stop();
  }

  public URLClassLoader getClassLoader() {
    return currentApplicationClassLoader;
  }
}
