module io.github.mzmine.utils {
  requires com.google.common;
  requires it.unimi.dsi.fastutil;
  requires java.desktop;
  requires java.logging;
  requires java.net.http;
  requires javafx.graphics;
  requires org.apache.commons.io;
  requires static org.jetbrains.annotations;
//  requires static io.mzio.global.events;
//  requires static semver4j;

  exports io.github.mzmine.util;
  exports io.github.mzmine.util.io;
  exports io.github.mzmine.util.concurrent;
  exports io.github.mzmine.util.web;
  exports io.github.mzmine.util.date;
  exports io.github.mzmine.util.collections;
  exports io.github.mzmine.util.exceptions;
  exports io.github.mzmine.util.files;
  exports io.github.mzmine.util.math;
  exports io.github.mzmine.util.objects;
}