package me.seroperson.reload.live;

public class ReloadGeneration {

  private final int iteration;
  private final ClassLoader reloadedClassLoader;

  public ReloadGeneration(int iteration, ClassLoader reloadedClassLoader) {
    this.iteration = iteration;
    this.reloadedClassLoader = reloadedClassLoader;
  }

  public int getIteration() {
    return iteration;
  }

  public ClassLoader getReloadedClassLoader() {
    return reloadedClassLoader;
  }

}
