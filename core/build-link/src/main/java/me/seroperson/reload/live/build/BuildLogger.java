package me.seroperson.reload.live.build;

public interface BuildLogger {

  void debug(String message);

  void info(String message);

  void warn(String message);

  void error(Throwable t);

  void error(String message, Throwable t);

  void error(String message);

}
