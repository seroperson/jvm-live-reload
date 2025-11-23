package me.seroperson.reload.live.runner;

import java.io.File;
import java.util.List;
import me.seroperson.reload.live.settings.DevServerSettings;

/**
 * Immutable container for development server startup parameters.
 *
 * <p>This class encapsulates all configuration and classpath information required to start a
 * development server with live reload capabilities. It provides access to server settings,
 * dependency classpath, files to monitor for changes, and lifecycle hook configurations.
 *
 * <p>Instances of this class are created during server initialization and passed to {@link
 * DevServerRunner} methods for starting the development server.
 */
public final class StartParams {

  private final DevServerSettings settings;
  private final List<File> dependencyClasspath;
  private final List<File> monitoredFiles;
  private final String mainClassName;
  private final String internalMainClassName;
  private final List<String> startupHookClasses;
  private final List<String> shutdownHookClasses;

  /**
   * Constructs a new StartParams instance with all required configuration.
   *
   * @param settings the development server settings containing host, port, and other configurations
   * @param dependencyClasspath the list of files representing the application's dependency
   *     classpath
   * @param monitoredFiles the list of files to monitor for changes that trigger reloads
   * @param mainClassName the fully qualified name of the main server wrapper class to load
   * @param internalMainClassName the fully qualified name of the actual application main class
   * @param startupHookClasses the list of fully qualified class names for startup hooks
   * @param shutdownHookClasses the list of fully qualified class names for shutdown hooks
   */
  public StartParams(
      DevServerSettings settings,
      List<File> dependencyClasspath,
      List<File> monitoredFiles,
      String mainClassName,
      String internalMainClassName,
      List<String> startupHookClasses,
      List<String> shutdownHookClasses) {
    this.settings = settings;
    this.dependencyClasspath = dependencyClasspath;
    this.monitoredFiles = monitoredFiles;
    this.mainClassName = mainClassName;
    this.internalMainClassName = internalMainClassName;
    this.startupHookClasses = startupHookClasses;
    this.shutdownHookClasses = shutdownHookClasses;
  }

  /**
   * Returns the development server settings.
   *
   * @return the {@link DevServerSettings} containing server configuration
   */
  public DevServerSettings getSettings() {
    return settings;
  }

  /**
   * Returns the list of dependency classpath files.
   *
   * @return the list of files representing application dependencies to include in classpath
   */
  public List<File> getDependencyClasspath() {
    return dependencyClasspath;
  }

  /**
   * Returns the list of files to monitor for changes.
   *
   * @return the list of files that will trigger a reload when modified
   */
  public List<File> getMonitoredFiles() {
    return monitoredFiles;
  }

  /**
   * Returns the fully qualified name of the main server wrapper class.
   *
   * @return the class name of the wrapper that manages the reloadable server
   */
  public String getMainClassName() {
    return mainClassName;
  }

  /**
   * Returns the fully qualified name of the actual application main class.
   *
   * @return the class name of the user's application entry point
   */
  public String getInternalMainClassName() {
    return internalMainClassName;
  }

  /**
   * Returns the list of startup hook class names.
   *
   * @return the list of fully qualified class names for hooks to execute during server startup
   */
  public List<String> getStartupHookClasses() {
    return startupHookClasses;
  }

  /**
   * Returns the list of shutdown hook class names.
   *
   * @return the list of fully qualified class names for hooks to execute during server shutdown
   */
  public List<String> getShutdownHookClasses() {
    return shutdownHookClasses;
  }
}
