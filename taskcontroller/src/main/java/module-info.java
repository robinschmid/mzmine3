module io.github.mzmine.taskcontroller {
  requires com.google.common;
  requires java.logging;
  requires javafx.base;
  requires static io.mzio.memory.management;
  requires static org.jetbrains.annotations;

  exports io.github.mzmine.taskcontrol;
  exports io.github.mzmine.taskcontrol.impl;
  exports io.github.mzmine.taskcontrol.listeners;
  exports io.github.mzmine.taskcontrol.operations;
  exports io.github.mzmine.taskcontrol.progress;
}