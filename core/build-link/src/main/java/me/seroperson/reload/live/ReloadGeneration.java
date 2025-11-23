package me.seroperson.reload.live;

/**
 * Represents a successful reload iteration. Has an index and a ClassLoader with new reloaded code.
 */
public final class ReloadGeneration {

  private final int iteration;
  private final ClassLoader reloadedClassLoader;

  /**
   * Creates an object.
   *
   * @param iteration current index
   * @param reloadedClassLoader ClassLoader with new code
   */
  public ReloadGeneration(int iteration, ClassLoader reloadedClassLoader) {
    this.iteration = iteration;
    this.reloadedClassLoader = reloadedClassLoader;
  }

  /**
   * @return current index
   */
  public int getIteration() {
    return iteration;
  }

  /**
   * @return ClassLoader with reloaded code
   */
  public ClassLoader getReloadedClassLoader() {
    return reloadedClassLoader;
  }
}
