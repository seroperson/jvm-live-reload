package me.seroperson.reload.live.runner;

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
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;
import me.seroperson.reload.live.ReloadGeneration;
import me.seroperson.reload.live.build.BuildLink;
import me.seroperson.reload.live.runner.CompileResult.CompileFailure;
import me.seroperson.reload.live.runner.CompileResult.CompileSuccess;
import me.seroperson.reload.live.runner.classloader.NamedURLClassLoader;
import me.seroperson.reload.live.settings.DevServerSettings;
import play.dev.filewatch.FileWatchService;
import play.dev.filewatch.FileWatcher;

final class DevServerReloader implements BuildLink, Closeable {

  private static final AccessControlContext accessControlContext = AccessController.getContext();

  private final Supplier<CompileResult> compile;

  private final Supplier<Boolean> triggerReload;

  private final ClassLoader dependenciesClassLoader;

  private final boolean compileOnChange;

  private final long compileDebounceMs;

  private final ScheduledExecutorService prefetchScheduler;

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

  private volatile CompileResult prefetchedCompileResult;

  private volatile boolean prefetchReady = false;

  private volatile ScheduledFuture<?> pendingPrefetch;

  DevServerReloader(
      ClassLoader dependenciesClassLoader,
      Supplier<CompileResult> compile,
      Supplier<Boolean> triggerReload,
      List<File> monitoredFiles,
      FileWatchService fileWatchService,
      DevServerSettings settings) {
    this.dependenciesClassLoader = dependenciesClassLoader;
    this.compile = compile;
    this.triggerReload = triggerReload;
    this.compileOnChange = settings.isCompileOnChange();
    this.compileDebounceMs = settings.getCompileDebounceMs();
    if (compileOnChange) {
      prefetchScheduler =
          Executors.newSingleThreadScheduledExecutor(
              r -> {
                Thread thread = new Thread(r, "jvm-live-reload-prefetch");
                thread.setDaemon(true);
                return thread;
              });
    } else {
      prefetchScheduler = null;
    }
    if (!monitoredFiles.isEmpty() && fileWatchService != null) {
      // Create the watcher, updates the changed boolean when a file has changed:
      this.watcher =
          fileWatchService.watch(
              monitoredFiles,
              () -> {
                changed = true;
                scheduleBackgroundCompile();
                return null;
              });
    } else {
      this.watcher = null;
    }
  }

  /** Execute f with context ClassLoader of Reloader */
  private static <T> T withReloaderContextClassLoader(Supplier<T> f) {
    var thread = Thread.currentThread();
    var oldLoader = thread.getContextClassLoader();
    // we use accessControlContext & AccessController to avoid a ClassLoader leak
    // (ProtectionDomain class)
    return AccessController.doPrivileged(
        (PrivilegedAction<T>)
            () -> {
              try {
                thread.setContextClassLoader(DevServerReloader.class.getClassLoader());
                return f.get();
              } finally {
                thread.setContextClassLoader(oldLoader);
              }
            },
        accessControlContext);
  }

  void scheduleBackgroundCompile() {
    if (!compileOnChange || prefetchScheduler == null) {
      return;
    }
    synchronized (this) {
      prefetchReady = false;
      prefetchedCompileResult = null;
      if (pendingPrefetch != null) {
        pendingPrefetch.cancel(false);
      }
      pendingPrefetch =
          prefetchScheduler.schedule(
              this::runBackgroundCompile, compileDebounceMs, TimeUnit.MILLISECONDS);
    }
  }

  private void runBackgroundCompile() {
    withReloaderContextClassLoader(
        () -> {
          CompileResult result = compile.get();
          synchronized (DevServerReloader.this) {
            prefetchedCompileResult = result;
            prefetchReady = true;
            pendingPrefetch = null;
          }
          return null;
        });
  }

  private CompileResult resolveCompileResult() {
    CompileResult prefetched = null;
    synchronized (this) {
      if (compileOnChange && prefetchReady && prefetchedCompileResult != null) {
        prefetched = prefetchedCompileResult;
        prefetchedCompileResult = null;
        prefetchReady = false;
      }
    }
    if (prefetched != null) {
      return prefetched;
    }
    return withReloaderContextClassLoader(compile::get);
  }

  private Object reload(boolean shouldReload) {
    CompileResult compileResult = resolveCompileResult();
    if (compileResult instanceof CompileFailure result) {
      // We force reload next time because compilation failed this time
      forceReloadNextTime = true;
      return result.getException();
    } else if (compileResult instanceof CompileSuccess result) {
      var cp = result.getClasspath();

      // We only want to reload if the classpath has changed.
      // Assets don't live on the classpath, so they won't trigger a reload.
      long newLastModified =
          cp.stream()
              .filter(File::exists)
              .mapToLong(DevServerReloader::maxLastModified)
              .max()
              .orElse(0L);
      var triggered = newLastModified > lastModified;
      lastModified = newLastModified;

      if (triggered || shouldReload || currentApplicationClassLoader == null) {
        int iteration = classLoaderVersion.incrementAndGet();
        // Create a new classloader
        currentApplicationClassLoader =
            new NamedURLClassLoader(
                "iteration(" + iteration + ")", urls(cp), dependenciesClassLoader);

        return new ReloadGeneration(iteration, currentApplicationClassLoader);
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
   * <p>Since this communicates across classloaders, it must return only simple objects.
   *
   * @return Either<br>
   *     - {@link Throwable} - If something went wrong (eg, a compile error). <br>
   *     - {@link ClassLoader} - If the classloader has changed, and the application should be
   *     reloaded.<br>
   *     - {@code null} - If nothing changed.
   */
  @Override
  public synchronized Object reload() {
    if (changed
        || (triggerReload != null && triggerReload.get())
        || forceReloadNextTime
        || currentApplicationClassLoader == null) {
      var shouldReload = forceReloadNextTime;
      changed = false;
      forceReloadNextTime = false;
      return reload(shouldReload);
    } else {
      return null; // null means nothing changed
    }
  }

  private static long maxLastModified(File file) {
    try (Stream<Path> s = Files.walk(file.toPath())) {
      return s.filter(p -> !(Files.isDirectory(p) && p.equals(file.toPath())))
          .mapToLong(p -> p.toFile().lastModified())
          .max()
          .orElse(0L);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void close() {
    if (prefetchScheduler != null) {
      synchronized (this) {
        if (pendingPrefetch != null) {
          pendingPrefetch.cancel(false);
          pendingPrefetch = null;
        }
      }
      prefetchScheduler.shutdown();
      try {
        prefetchScheduler.awaitTermination(2, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        prefetchScheduler.shutdownNow();
      }
    }
    var cl = currentApplicationClassLoader;
    currentApplicationClassLoader = null;
    if (cl != null) {
      try {
        cl.close();
      } catch (IOException e) {
        // best-effort cleanup on shutdown
      }
    }
    if (watcher != null) watcher.stop();
  }
}
