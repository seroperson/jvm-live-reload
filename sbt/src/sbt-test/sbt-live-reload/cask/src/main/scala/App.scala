
object App extends cask.MainRoutes {

  @cask.get("/greet")
  def greet() = {
    "World"
  }

  @cask.get("/health")
  def health(request: cask.Request) = {
    ""
  }

  initialize()
}
