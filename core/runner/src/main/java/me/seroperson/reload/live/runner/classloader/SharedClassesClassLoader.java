package me.seroperson.reload.live.runner.classloader;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A ClassLoader that delegates specific shared classes to a build system ClassLoader.
 *
 * <p>This ClassLoader is designed to bridge different ClassLoader contexts in a live reload
 * environment by ensuring that specific shared classes (build link interfaces) are loaded from the
 * build system ClassLoader rather than from the normal parent delegation chain.
 */
public class SharedClassesClassLoader extends ClassLoader {

  private final Set<String> sharedClasses;
  private final ClassLoader buildLoader;

  /**
   * Creates a new SharedClassesClassLoader.
   *
   * @param parent the parent ClassLoader for general class loading
   * @param sharedClasses list of class names that should be loaded from the build loader
   * @param buildLoader the ClassLoader containing build system classes
   */
  public SharedClassesClassLoader(
      ClassLoader parent, List<String> sharedClasses, ClassLoader buildLoader) {
    super(parent);
    this.sharedClasses = new HashSet<>(sharedClasses);
    this.buildLoader = buildLoader;
  }

  @Override
  public URL findResource(String name) {
    // if (System.out != null) {
    // System.out.println("In SharedClassesClassLoader: trying to get via findResource: " + name);
    // }
    return super.findResource(name);
  }

  @Override
  public Enumeration<URL> getResources(String name) throws IOException {
    // if (System.out != null) {
    // System.out.println("In SharedClassesClassLoader: trying to get via getResources: " + name);
    // }
    return super.getResources(name);
  }

  /**
   * Loads a class, delegating shared classes to the build loader. All other classes follow normal
   * parent delegation.
   *
   * @param name the name of the class to load
   * @param resolve whether to resolve the class
   * @return the loaded class
   * @throws ClassNotFoundException if the class cannot be found
   */
  @Override
  public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    if (sharedClasses.contains(name)) {
      return buildLoader.loadClass(name);
    } else {
      return super.loadClass(name, resolve);
    }
  }

  @Override
  public String toString() {
    return "SharedClassesClassLoader[shared="
        + sharedClasses.size()
        + ", parent="
        + getParent()
        + "]";
  }
}
