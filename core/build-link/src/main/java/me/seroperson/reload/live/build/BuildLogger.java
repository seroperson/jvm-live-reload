package me.seroperson.reload.live.build;

/**
 * Logger interface for build tools to communicate log messages to the build system.
 *
 * <p>This interface provides different log levels (debug, info, warn, error) and allows
 * the build tool to delegate logging to the appropriate build system logger.
 */
public interface BuildLogger {

  /**
   * Log a debug message.
   *
   * @param message the debug message to log
   */
  void debug(String message);

  /**
   * Log an info message.
   *
   * @param message the info message to log
   */
  void info(String message);

  /**
   * Log a warning message.
   *
   * @param message the warning message to log
   */
  void warn(String message);

  /**
   * Log an error with throwable.
   *
   * @param t the throwable to log
   */
  void error(Throwable t);

  /**
   * Log an error message with throwable.
   *
   * @param message the error message to log
   * @param t the throwable to log
   */
  void error(String message, Throwable t);

  /**
   * Log an error message.
   *
   * @param message the error message to log
   */
  void error(String message);

}
