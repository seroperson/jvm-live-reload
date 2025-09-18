object App extends cask.MainRoutes {

  @cask.get("/greet")
  def greet() = {
    "Hello World"
  }

  @cask.get("/health")
  def health(request: cask.Request) = {
    ""
  }

  initialize()
}
