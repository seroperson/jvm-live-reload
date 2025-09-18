package me.seroperson.reload.live.hook;

import me.seroperson.reload.live.build.BuildLogger;
import me.seroperson.reload.live.settings.DevServerSettings;

public interface Hook {

  String description();

  boolean isAvailable();

  void hook(DevServerSettings settings, BuildLogger logger);

}
