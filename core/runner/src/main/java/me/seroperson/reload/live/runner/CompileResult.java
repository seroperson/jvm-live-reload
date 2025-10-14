package me.seroperson.reload.live.runner;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import java.io.File;
import java.util.List;

/**
 * Represents the result of a compilation operation.
 *
 * <p>This sealed interface has two implementations:
 *
 * <ul>
 *   <li>{@link CompileSuccess} - indicates successful compilation with resulting classpath
 *   <li>{@link CompileFailure} - indicates compilation failure with exception details
 * </ul>
 */
public interface CompileResult {

  /**
   * Represents a successful compilation result.
   *
   * <p>Contains the classpath entries that resulted from the compilation process. The classpath
   * includes all compiled class files and resources needed to run the application.
   */
  class CompileSuccess implements CompileResult {
    private final List<File> classpath;

    /**
     * Creates a new successful compilation result.
     *
     * @param classpath the list of classpath entries from compilation, or null for empty list
     */
    public CompileSuccess(List<File> classpath) {
      this.classpath = requireNonNullElse(classpath, List.of());
    }

    /**
     * Gets the classpath entries from compilation.
     *
     * @return the list of classpath files
     */
    public List<File> getClasspath() {
      return classpath;
    }
  }

  /**
   * Represents a failed compilation result.
   *
   * <p>Contains the exception that caused the compilation to fail. This allows the caller to access
   * detailed error information.
   */
  class CompileFailure implements CompileResult {
    private final Throwable exception;

    /**
     * Creates a new failed compilation result.
     *
     * @param exception the exception that caused the compilation failure
     * @throws NullPointerException if exception is null
     */
    public CompileFailure(Throwable exception) {
      this.exception = requireNonNull(exception);
    }

    /**
     * Gets the exception that caused the compilation failure.
     *
     * @return the exception that caused the failure
     */
    public Throwable getException() {
      return exception;
    }
  }
}
