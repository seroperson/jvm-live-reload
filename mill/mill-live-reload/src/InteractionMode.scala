package me.seroperson.reload.live.mill

import java.io.Closeable
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.io.OutputStream
import org.jline.reader.*
import org.jline.reader.impl.*
import org.jline.terminal.*
import org.jline.terminal.impl.*
import scala.concurrent.duration.*
import scala.util.Using

/** Defines how user interaction is handled during development server execution.
  *
  * This trait provides the interface for different ways to handle user input
  * and server lifecycle management during development. Implementations can
  * provide console-based interaction, GUI-based interaction, or non-blocking
  * background execution.
  */
trait InteractionMode {

  /** Blocks execution until the user indicates the application should stop.
    *
    * This is the primary means of keeping a `run` command active until the user
    * has indicated, via some interface (console or GUI), that the application
    * should no longer be running.
    */
  def waitForCancel(in: InputStream, out: OutputStream): Unit

}

/** Marker trait to signify a non-blocking interaction mode.
  *
  * This is provided, rather than adding a new flag to InteractionMode, to
  * preserve binary compatibility.
  */
trait NonBlockingInteractionMode extends InteractionMode {
  override def waitForCancel(in: InputStream, out: OutputStream): Unit = ()

  /** Start the server, if not already started
    *
    * @param server
    *   A callback to start the server, that returns a closeable to stop it
    *
    * @return
    *   A boolean indicating if the server was started (true) or not (false).
    */
  def start(server: => Closeable): Boolean

  /** Stop the server started by the last start request, if such a server exists
    */
  def stop(): Unit
}

/** Default behavior for interaction mode is to wait on JLine.
  */
object ConsoleInteractionMode extends InteractionMode {

  override def waitForCancel(in: InputStream, out: OutputStream): Unit = {
    val terminal = TerminalBuilder
      .builder()
      .streams(in, out)
      .build()

    Using(terminal) { t =>
      t.echo(false)

      val reader = t.reader()

      def waitEOF(): Unit = {
        reader.read() match {
          case 4 | 13 => // STOP on Ctrl-D or Enter
          case 11     => /*reader.clearScreen();*/ waitEOF()
          case 10     => println(); waitEOF()
          case _      => waitEOF()
        }
      }

      waitEOF()
    }
  }

  override def toString = "Console Interaction Mode"
}

/** Simple implementation of the non-blocking interaction mode that simply
  * stores the current application in a static variable.
  */
object StaticNonBlockingInteractionMode extends NonBlockingInteractionMode {
  private var current: Option[Closeable] = None

  /** Start the server, if not already started
    *
    * @param server
    *   A callback to start the server, that returns a closeable to stop it
    *
    * @return
    *   A boolean indicating if the server was started (true) or not (false).
    */
  def start(server: => Closeable): Boolean = synchronized {
    current match {
      case Some(_) =>
        println("Not starting server since one is already started")
        false
      case None =>
        println("Starting server")
        current = Some(server)
        true
    }
  }

  /** Stop the server started by the last start request, if such a server exists
    */
  def stop() = synchronized {
    current match {
      case None         => println("Not stopping server since none is started")
      case Some(server) =>
        println("Stopping server")
        server.close()
        current = None
    }
  }
}
