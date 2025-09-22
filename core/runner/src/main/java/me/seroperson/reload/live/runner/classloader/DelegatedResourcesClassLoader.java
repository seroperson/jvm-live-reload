package me.seroperson.reload.live.runner.classloader;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Objects;

/**
 * A ClassLoader that delegates resource loading to its parent ClassLoader.
 *
 * <p>
 * This ClassLoader extends NamedURLClassLoader but overrides resource loading to always delegate to
 * the parent ClassLoader. This ensures that resources are loaded from the parent's context rather
 * than from the URLs provided to this ClassLoader, which is useful in live reload scenarios where
 * resource consistency is important.
 */
public class DelegatedResourcesClassLoader extends NamedURLClassLoader {

  /**
   * Creates a new DelegatedResourcesClassLoader.
   *
   * @param name the name of this ClassLoader for debugging purposes
   * @param urls the URLs to search for classes (not used for resources)
   * @param parent the parent ClassLoader to delegate resource loading to
   * @throws NullPointerException if parent is null
   */
  public DelegatedResourcesClassLoader(String name, URL[] urls, ClassLoader parent) {
    super(name, urls, parent);
    Objects.requireNonNull(parent);
  }

  /**
   * Delegates resource loading to the parent ClassLoader.
   *
   * @param name the name of the resource
   * @return an enumeration of URLs for the resource from the parent ClassLoader
   * @throws IOException if an I/O error occurs
   */
  @Override
  public Enumeration<URL> getResources(String name) throws IOException {
    return getParent().getResources(name);
  }
}
