object App extends cask.MainRoutes {

  override def port: Int = me.seroperson.BuildInfo.port

  @cask.get("/greet")
  def greet() = {
    "Hello World"
  }

  @cask.get("/health")
  def health(request: cask.Request) = {
    ""
  }

  initialize()

  override def main(args: Array[String]): Unit = {
    super.main(args)
    // Simulate a main thread that never honours an interrupt.
    while (true) {
      try {
        Thread.sleep(Long.MaxValue)
      } catch {
        case _: InterruptedException => ()
      }
    }
  }
}
