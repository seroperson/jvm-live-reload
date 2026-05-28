package me.seroperson.reload.live.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import me.seroperson.reload.live.ReloadGeneration;
import me.seroperson.reload.live.runner.CompileResult.CompileSuccess;
import me.seroperson.reload.live.settings.DevServerSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DevServerReloaderTest {

  @TempDir File tempDir;

  @Test
  void reloadUsesPrefetchedCompileResult() throws Exception {
    var compileDir = Files.createDirectory(tempDir.toPath().resolve("classes")).toFile();
    var compileCount = new AtomicInteger();
    var reloadRequested = new AtomicBoolean(true);
    var settings =
        new DevServerSettings(
            List.of(),
            List.of(
                "-D" + DevServerSettings.LiveReloadCompileOnChange + "=true",
                "-D" + DevServerSettings.LiveReloadCompileDebounceMs + "=50"),
            Map.of());

    try (var reloader =
        new DevServerReloader(
            ClassLoader.getSystemClassLoader(),
            () -> {
              compileCount.incrementAndGet();
              return new CompileSuccess(List.of(compileDir));
            },
            () -> reloadRequested.getAndSet(false),
            List.of(),
            null,
            settings)) {

      reloader.scheduleBackgroundCompile();
      Thread.sleep(200);

      var first = reloader.reload();
      assertInstanceOf(ReloadGeneration.class, first);
      assertEquals(1, compileCount.get());

      compileDir.setLastModified(System.currentTimeMillis() + 1000);
      reloadRequested.set(true);
      reloader.scheduleBackgroundCompile();
      Thread.sleep(200);

      var second = reloader.reload();
      assertInstanceOf(ReloadGeneration.class, second);
      assertEquals(2, compileCount.get());
    }
  }

  @Test
  void compileOnChangeDisabledAlwaysCompilesOnReload() throws Exception {
    var compileDir = Files.createDirectory(tempDir.toPath().resolve("classes")).toFile();
    var compileCount = new AtomicInteger();
    var reloadRequested = new AtomicBoolean(true);
    var settings =
        new DevServerSettings(
            List.of(),
            List.of("-D" + DevServerSettings.LiveReloadCompileOnChange + "=false"),
            Map.of());

    try (var reloader =
        new DevServerReloader(
            ClassLoader.getSystemClassLoader(),
            () -> {
              compileCount.incrementAndGet();
              return new CompileSuccess(List.of(compileDir));
            },
            () -> reloadRequested.getAndSet(false),
            List.of(),
            null,
            settings)) {

      var first = reloader.reload();
      assertInstanceOf(ReloadGeneration.class, first);
      assertEquals(1, compileCount.get());

      compileDir.setLastModified(System.currentTimeMillis() + 1000);
      reloadRequested.set(true);
      var second = reloader.reload();
      assertInstanceOf(ReloadGeneration.class, second);
      assertEquals(2, compileCount.get());
    }
  }

  @Test
  void noReloadWhenClasspathUnchanged() throws Exception {
    var compileDir = Files.createDirectory(tempDir.toPath().resolve("classes")).toFile();
    var settings = new DevServerSettings(List.of(), List.of(), Map.of());

    try (var reloader =
        new DevServerReloader(
            ClassLoader.getSystemClassLoader(),
            () -> new CompileSuccess(List.of(compileDir)),
            null,
            List.of(),
            null,
            settings)) {

      assertInstanceOf(ReloadGeneration.class, reloader.reload());
      assertNull(reloader.reload());
    }
  }
}
