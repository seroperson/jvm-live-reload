package me.seroperson.reload.live.sbt

import java.io.Closeable
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.io.OutputStream
import jline.console.ConsoleReader
import scala.annotation.tailrec
import scala.concurrent.duration.*

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
  def waitForCancel(): Unit

  /** Executes code with console echo disabled.
    *
    * Enables and disables console echo (or does nothing if no console). This
    * ensures console echo is enabled on exception thrown in the given code
    * block, providing a clean user experience during interaction.
    *
    * @param f
    *   the code block to execute without echo
    */
  def doWithoutEcho(f: => Unit): Unit

}

/** Marker trait to signify a non-blocking interaction mode.
  *
  * This is provided, rather than adding a new flag to InteractionMode, to
  * preserve binary compatibility.
  */
trait NonBlockingInteractionMode extends InteractionMode {
  override def waitForCancel() = ()
  override def doWithoutEcho(f: => Unit) = f

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

  private final class SystemInWrapper() extends InputStream {
    override def read(): Int = System.in.read()
  }

  private final class SystemOutWrapper extends OutputStream {
    override def write(b: Int): Unit = System.out.write(b)
    override def write(b: Array[Byte]): Unit = write(b, 0, b.length)
    override def write(b: Array[Byte], off: Int, len: Int): Unit = {
      System.out.write(b, off, len)
      System.out.flush()
    }
    override def flush(): Unit = System.out.flush()
  }

  private def createReader: ConsoleReader =
    // sbt 1.4+ (class name is "sbt.internal.util.Terminal$proxyInputStream$"):
    // sbt makes System.in non-blocking starting with 1.4.0, therefore we shouldn't
    // create a non-blocking input stream reader ourselves, but just wrap System.in
    // and System.out (otherwise we end up in a deadlock, console will hang, not accepting inputs)
    new ConsoleReader(new SystemInWrapper(), new SystemOutWrapper())

  private def withConsoleReader[T](f: ConsoleReader => T): T = {
    val consoleReader = createReader
    try f(consoleReader)
    finally consoleReader.close()
  }

  private def waitForKey(): Unit = {
    withConsoleReader { consoleReader =>
      @tailrec def waitEOF(): Unit = {
        consoleReader.readCharacter() match {
          case 4 | 13 => // STOP on Ctrl-D or Enter
          case 11     => consoleReader.clearScreen(); waitEOF()
          case 10     => println(); waitEOF()
          case _      => waitEOF()
        }
      }
      doWithoutEcho(waitEOF())
    }
  }

  override def doWithoutEcho(f: => Unit): Unit = {
    withConsoleReader { consoleReader =>
      val terminal = consoleReader.getTerminal
      terminal.setEchoEnabled(false)
      try f
      finally terminal.restore()
    }
  }

  override def waitForCancel(): Unit = waitForKey()

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
      case None => println("Not stopping server since none is started")
      case Some(server) =>
        println("Stopping server")
        server.close()
        current = None
    }
  }
}
