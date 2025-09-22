package me.seroperson.reload.live.runner.classloader;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

/**
 * A ClassLoader that delegates specific classes to the build loader and resources to the
 * application loader.
 *
 * <p>
 * This ClassLoader is designed to bridge different ClassLoader contexts in a live reload
 * environment:
 * <ul>
 * <li>Shared classes (build link interfaces) are loaded from the build loader</li>
 * <li>Resources are primarily loaded from the application loader when available</li>
 * <li>All other classes are loaded from the parent ClassLoader</li>
 * </ul>
 *
 * <p>
 * This setup enables communication between the build system and the running application while
 * maintaining proper ClassLoader isolation.
 */
public class DelegatingClassLoader extends ClassLoader {

  private List<String> sharedClasses;
  private ClassLoader buildLoader;
  private ApplicationClassLoaderProvider applicationClassLoaderProvider;

  /**
   * Creates a new DelegatingClassLoader.
   *
   * @param commonLoader the parent ClassLoader for general class loading
   * @param sharedClasses list of class names that should be loaded from the build loader
   * @param buildLoader the ClassLoader containing build system classes
   * @param applicationClassLoaderProvider provider for the current application ClassLoader
   */
  public DelegatingClassLoader(ClassLoader commonLoader, List<String> sharedClasses,
      ClassLoader buildLoader, ApplicationClassLoaderProvider applicationClassLoaderProvider) {
    super(commonLoader);
    this.sharedClasses = sharedClasses;
    this.buildLoader = buildLoader;
    this.applicationClassLoaderProvider = applicationClassLoaderProvider;
  }

  /**
   * Loads a class, delegating shared classes to the build loader.
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

  /**
   * Gets a resource, prioritizing the application ClassLoader.
   *
   * @param name the name of the resource
   * @return the resource URL, or null if not found
   */
  @Override
  public URL getResource(String name) {
    URLClassLoader appClassLoader = applicationClassLoaderProvider.get();
    URL resource = null;
    if (appClassLoader != null) {
      resource = appClassLoader.findResource(name);
    }
    return resource != null ? resource : super.getResource(name);
  }

  /**
   * Gets all resources with the given name, combining application and parent resources.
   *
   * @param name the name of the resources
   * @return an enumeration of resource URLs
   * @throws IOException if an I/O error occurs
   */
  @SuppressWarnings("unchecked")
  @Override
  public Enumeration<URL> getResources(String name) throws IOException {
    URLClassLoader appClassLoader = applicationClassLoaderProvider.get();
    Enumeration<URL> resources1;
    if (appClassLoader != null) {
      resources1 = appClassLoader.findResources(name);
    } else {
      resources1 = new Vector<URL>().elements();
    }
    Enumeration<URL> resources2 = super.getResources(name);
    return combineResources(resources1, resources2);
  }

  /**
   * Combines two resource enumerations into a single enumeration, removing duplicates.
   *
   * @param resources1 the first enumeration of resources
   * @param resources2 the second enumeration of resources
   * @return a combined enumeration without duplicates
   */
  private Enumeration<URL> combineResources(Enumeration<URL> resources1,
      Enumeration<URL> resources2) {
    Set<URL> set = new HashSet<>();
    while (resources1.hasMoreElements()) {
      set.add(resources1.nextElement());
    }
    while (resources2.hasMoreElements()) {
      set.add(resources2.nextElement());
    }
    return new Vector<>(set).elements();
  }

  @Override
  public String toString() {
    return "DelegatingClassLoader, using parent: " + getParent();
  }
}
