package me.seroperson.reload.live.runner;

import java.io.IOException;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import me.seroperson.reload.live.build.BuildLogger;
import me.seroperson.reload.live.build.ReloadableServer;
import me.seroperson.reload.live.reflect.Environment;
import me.seroperson.reload.live.reflect.ShutdownHook;

public class DevServerWrapper implements ReloadableServer {

  private final StartParams params;
  private final BuildLogger logger;
  private final ReloadableServer server;

  // Preserving initial environment state
  private Map<String, String> initialEnv;
  // Preserving build-system shutdown hooks
  private Map<Thread, Thread> buildSystemShutdownHooks;
  private Set<Long> buildSystemHookThreadIds;

  public DevServerWrapper(StartParams params, BuildLogger logger, ReloadableServer server) {
    this.params = params;
    this.logger = logger;
    this.server = server;
  }

  @Override
  public void start() {
    // Capture every snapshot before mutating anything, so close() can roll back
    // even if a later step (putEnv, unregisterShutdownHooks, server.start) throws.
    this.initialEnv = new HashMap<>(System.getenv());
    this.buildSystemShutdownHooks = new IdentityHashMap<>(ShutdownHook.getRegistredShutdownHooks());
    this.buildSystemHookThreadIds =
        buildSystemShutdownHooks.keySet().stream().map(Thread::getId).collect(Collectors.toSet());

    Environment.putEnv(params.getPropagateEnv());

    logger.debug("Preserving shutdown hooks:");
    ShutdownHook.logShutdownHooks(buildSystemShutdownHooks, logger);
    ShutdownHook.unregisterShutdownHooks(buildSystemHookThreadIds);

    server.start();
  }

  @Override
  public boolean isRunning() {
    return server.isRunning();
  }

  @Override
  public boolean reload() {
    return server.reload();
  }

  @Override
  public String getProxyUrl() {
    return server.getProxyUrl();
  }

  @Override
  public String getApplicationUrl() {
    return server.getApplicationUrl();
  }

  @Override
  public void close() throws IOException {
    try {
      server.close();
    } finally {
      if (initialEnv != null) {
        Environment.setEnv(initialEnv);
      }
      if (buildSystemShutdownHooks != null) {
        ShutdownHook.setShutdownHooks(new IdentityHashMap<>(buildSystemShutdownHooks));
      }
    }
  }
}
