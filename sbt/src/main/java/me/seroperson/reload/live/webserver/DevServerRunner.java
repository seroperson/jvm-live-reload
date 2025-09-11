package me.seroperson.reload.live.webserver;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import play.dev.filewatch.FileWatchService;
import me.seroperson.reload.live.build.BuildLink;
import me.seroperson.reload.live.build.ReloadableServer;
import me.seroperson.reload.live.webserver.classloader.DelegatingClassLoader;
import me.seroperson.reload.live.webserver.classloader.NamedURLClassLoader;

public final class DevServerRunner {

	private DevServerReloader reloader;

	private ReloadableServer getReloadableServer(ClassLoader applicationLoader, String mainClassName,
			String internalMainClassName, DevServerSettings settings, List<String> shutdownHookClasses)
			throws ReflectiveOperationException {
		var httpPort = settings.getHttpPort();
		var httpsPort = settings.getHttpsPort();
		var httpAddress = settings.getHttpAddress();

		ReloadableServer server;

		var mainClass = applicationLoader.loadClass(mainClassName);

		/*
		 * if (settings.isHttpPortDefined() && settings.isHttpsPortDefined()) { var m =
		 * mainClass.getMethod("mainDevHttpAndHttpsMode", BuildLink.class, int.class,
		 * int.class, String.class); server = (ReloadableServer) m.invoke(null,
		 * reloader, httpPort, httpsPort, httpAddress); } else if
		 * (settings.isHttpPortDefined()) { var m =
		 * mainClass.getMethod("mainDevHttpMode", BuildLink.class, int.class,
		 * String.class); server = (ReloadableServer) m.invoke(null, reloader, httpPort,
		 * httpAddress); } else {
		 */

		System.out.println("Shutdown hook classes: " + shutdownHookClasses);
		System.out.println("Loading main class: " + mainClassName);
		System.out.println(java.util.Arrays.deepToString(mainClass.getMethods()));

		// var m = mainClass.getMethod("start", BuildLink.class, Integer.class,
		// String.class);
		// server = (ReloadableServer) m.invoke(null, reloader, httpPort, httpAddress);
		var m = mainClass.getMethod("start", BuildLink.class, String.class, List.class);
		server = (ReloadableServer) m.invoke(null, reloader, internalMainClassName, shutdownHookClasses);
		// }
		return server;
	}

	private DevServer run(List<String> javaOptions, ClassLoader commonClassLoader, List<File> dependencyClasspath,
			Supplier<CompileResult> reloadCompile, Function<ClassLoader, ClassLoader> assetsClassLoader,
			Supplier<Boolean> triggerReload, List<File> monitoredFiles, FileWatchService fileWatchService,
			File projectPath, Map<String, String> devSettings, List<String> args, String mainClassName,
			String internalMainClassName, Object reloadLock, List<String> shutdownHookClasses) {
		if (reloader != null) {
			throw new IllegalStateException("Cannot run a dev server because another one is already running!");
		}

		var settings = DevServerSettings.parse(javaOptions, args, devSettings, 9000, "0.0.0.0");
		if (!settings.isAnyPortDefined()) {
			throw new IllegalArgumentException("You have to specify https.port when http.port is disabled");
		}
		// Set Java system properties
		settings.getMergedProperties().forEach(System::setProperty);

		System.out.println();

		/*
		 * We need to do a bit of classloader magic to run the application.
		 *
		 * There are six classloaders:
		 *
		 * 1. buildLoader, the classloader of sbt and the sbt plugin. 2. commonLoader, a
		 * classloader that persists across calls to run. This classloader is stored
		 * inside the PlayInternalKeys.playCommonClassloader task. This classloader will
		 * load the classes for the H2 database if it finds them in the user's
		 * classpath. This allows H2's in-memory database state to survive across calls
		 * to run. 3. delegatingLoader, a special classloader that overrides class
		 * loading to delegate shared classes for build link to the buildLoader, and
		 * accesses the reloader.currentApplicationClassLoader for resource loading to
		 * make user resources available to dependency classes. Has the commonLoader as
		 * its parent. 4. applicationLoader, contains the application dependencies. Has
		 * the delegatingLoader as its parent. Classes from the commonLoader and the
		 * delegatingLoader are checked for loading first. 5. playAssetsClassLoader,
		 * serves assets from all projects, prefixed as configured. It does no caching,
		 * and doesn't need to be reloaded each time the assets are rebuilt. 6.
		 * reloader.currentApplicationClassLoader, contains the user classes and
		 * resources. Has applicationLoader as its parent, where the application
		 * dependencies are found, and which will delegate through to the buildLoader
		 * via the delegatingLoader for the shared link. Resources are actually loaded
		 * by the delegatingLoader, where they are available to both the reloader and
		 * the applicationLoader. This classloader is recreated on reload. See
		 * PlayReloader.
		 *
		 * Someone working on this code in the future might want to tidy things up by
		 * splitting some of the custom logic out of the URLClassLoaders and into their
		 * own simpler ClassLoader implementations. The curious cycle between
		 * applicationLoader and reloader.currentApplicationClassLoader could also use
		 * some attention.
		 */

		var buildLoader = this.getClass().getClassLoader();

		try {
			// Now we're about to start, let's call the hooks:
			// RunHooksRunner.run(runHooks, RunHook::beforeStarted);

			/*
			 * ClassLoader that delegates loading of shared build link classes to the
			 * buildLoader. Also accesses the reloader resources to make these available to
			 * the applicationLoader, creating a full circle for resource loading.
			 */
			var sharedClasses = List.of(me.seroperson.reload.live.build.BuildLink.class.getName(),
					me.seroperson.reload.live.hook.Hook.class.getName(),
					me.seroperson.reload.live.build.ReloadableServer.class.getName());
			ClassLoader delegatingLoader = new DelegatingClassLoader(commonClassLoader, sharedClasses, buildLoader,
					() -> reloader.getClassLoader());

			var applicationLoader = new NamedURLClassLoader("DependencyClassLoader", urls(dependencyClasspath),
					delegatingLoader);

			// Need to call the assetsClassLoader function _after_ (!) the beforeStarted run
			// hooks ran
			var assetsLoader = assetsClassLoader.apply(applicationLoader);

			// Need to initialize the reloader _after_ (!) the beforeStarted run hooks ran,
			// because the
			// DevServerReloader constructor eagerly initializes and already starts a file
			// watch service
			// (Originally this was Scala code, where reloader was defined lazy and wasn't
			// accessed (and
			// therefore initialized) before creating the ReloadableServer below)
			reloader = new DevServerReloader(projectPath, assetsLoader, reloadCompile, devSettings, triggerReload,
					monitoredFiles, fileWatchService, reloadLock);

			ReloadableServer server = getReloadableServer(applicationLoader, mainClassName, internalMainClassName,
					settings, shutdownHookClasses);

			// Notify hooks
			// RunHooksRunner.run(runHooks, RunHook::afterStarted);

			return new DevServer() {
				public BuildLink buildLink() {
					return reloader;
				}

				public void reload() {
					server.reload();
				}

				public void close() {
					server.stop();
					reloader.close();

					// Notify hooks
					// RunHooksRunner.run(runHooks, RunHook::afterStopped);

					// Remove Java system properties
					settings.getMergedProperties().forEach((key, __) -> System.clearProperty(key));

					reloader = null;
				}
			};
		} catch (Throwable e) {
			// Let hooks clean up
			/*
			 * runHooks.forEach(hook -> { try { hook.onError(); } catch (Throwable ignore) {
			 * // Swallow any exceptions so that all `onError`s get called. } });
			 */
			reloader = null;
			Throwable rootCause = e;
			while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
				rootCause = rootCause.getCause();
			}
			// Convert play-server exceptions to our ServerStartException
			// if
			// (rootCause.getClass().getName().equals("play.core.server.ServerListenException"))
			// {
			// throw e; // ServerStartException(e);
			// }
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

	/**
	 * Start the server in DEV-mode
	 *
	 * @return A closeable that can be closed to stop the server
	 */
	public static DevServer startDevMode(List<String> javaOptions, ClassLoader commonClassLoader,
			List<File> dependencyClasspath, Supplier<CompileResult> reloadCompile,
			Function<ClassLoader, ClassLoader> assetsClassLoader, Supplier<Boolean> triggerReload,
			List<File> monitoredFiles, FileWatchService fileWatchService, File projectPath,
			Map<String, String> devSettings, List<String> args, String mainClassName, String internalClassName,
			Object reloadLock, List<String> shutdownHookClasses) {
		return getInstance().run(javaOptions, commonClassLoader, dependencyClasspath, reloadCompile, assetsClassLoader,
				triggerReload, monitoredFiles, fileWatchService, projectPath, devSettings, args, mainClassName,
				internalClassName, reloadLock, shutdownHookClasses);
	}

	private DevServerRunner() {
	}

	private static class Holder {
		public static final DevServerRunner INSTANCE = new DevServerRunner();
	}

	public static DevServerRunner getInstance() {
		return Holder.INSTANCE;
	}
}
