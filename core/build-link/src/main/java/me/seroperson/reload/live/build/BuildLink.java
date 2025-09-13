package me.seroperson.reload.live.build;

import java.io.File;
import java.util.Map;

/**
 * Interface used by the Play build plugin to communicate with an embedded Play
 * server. BuildLink objects are created by the plugin's run command and
 * provided to Play's NettyServer devMode methods.
 *
 * <p>
 * This interface is written in Java and uses only Java types so that
 * communication can work even when the plugin and embedded Play server are
 * built with different versions of Scala.
 */
public interface BuildLink {

	/**
	 * Check if anything has changed, and if so, return an updated classloader.
	 *
	 * <p>
	 * This method is called multiple times on every request, so it is advised that
	 * change detection happens asynchronously to this call, and that this call just
	 * check a boolean.
	 *
	 * @return Either
	 *         <ul>
	 *         <li>Throwable - If something went wrong (eg, a compile error).
	 *         {@link play.api.PlayException} and its sub types can be used to
	 *         provide specific details on compile errors or other exceptions.
	 *         <li>ClassLoader - If the classloader has changed, and the application
	 *         should be reloaded.
	 *         <li>null - If nothing changed.
	 *         </ul>
	 */
	Object reload();

}
