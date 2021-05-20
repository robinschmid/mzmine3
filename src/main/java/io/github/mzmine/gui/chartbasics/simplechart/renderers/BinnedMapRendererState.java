/*
 * Copyright 2006-2020 The MZmine Development Team
 *
 * This file is part of MZmine.
 *
 * MZmine is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MZmine; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA
 */

package io.github.mzmine.gui.chartbasics.simplechart.renderers;

import java.awt.geom.Rectangle2D;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.renderer.xy.XYItemRendererState;
import org.jfree.data.xy.XYDataset;

/**
 * @author Robin Schmid (https://github.com/robinschmid)
 */
public class BinnedMapRendererState extends XYItemRendererState {

  private static final Logger logger = Logger.getLogger(BinnedMapRendererState.class.getName());

  private final Rectangle2D dataArea;
  /**
   * Called after all data points were entered into the grid
   */
  private final Consumer<BinnedMapRendererState> onFinished;

  /**
   * The pixel width and height
   */
  private final int pixelWidth;
  private final int pixelHeight;
  /**
   * pixels grid [x][y] -> intensity
   */
  private final double[][] pixels;
  /**
   * Start and end coordinates in Java2D of the dataArea
   */
  private final int firstX;
  private final int firstY;
  private final int lastX;
  private final int lastY;


  public BinnedMapRendererState(PlotRenderingInfo info, Rectangle2D dataArea,
      int pixelWidth, int pixelHeight, Consumer<BinnedMapRendererState> onFinished) {
    super(info);
    assert pixelWidth > 0;
    assert pixelHeight > 0;

    this.dataArea = dataArea;
    this.pixelWidth = pixelWidth;
    this.pixelHeight = pixelHeight;
    this.onFinished = onFinished;
    // disable visible items optimisation - it doesn't work for this
    // renderer...
    setProcessVisibleItemsOnly(false);

    // start
    firstX = (int) dataArea.getX();
    firstY = (int) dataArea.getY();

    // create data array
    int w = (int) dataArea.getWidth() / pixelWidth + 2;
    int h = (int) dataArea.getHeight() / pixelHeight + 2;
    this.pixels = new double[w][h];

    lastX = firstX + w;
    lastY = firstY + h;
    // fill all with NaN
//    ArrayUtils.fill2D(pixels, Double.NaN);
  }

  @Override
  public void endSeriesPass(XYDataset dataset, int series, int firstItem, int lastItem, int pass,
      int passCount) {
    // trigger the rendering after all data points were passed
    onFinished.accept(this);
  }

  /**
   * pixels grid [x][y] -> intensity
   *
   * @return intensity or NaN in array if no value
   */
  public double[][] getPixels() {
    return pixels;
  }

  /**
   * block coordinates and width and height
   *
   * @param z  intensity
   * @param bx block x
   * @param by block y
   */
  public void addValue(double z, double bx, double by) {
    double xi = (bx - firstX) / pixelWidth;
    double yi = (by - firstY) / pixelHeight;

    // in bounds
    if (xi >= 0 && yi >= 0 && xi < pixels.length && yi < pixels[0].length) {
      pixels[(int) xi][(int) yi] += z;
    }
    // bilinear
//      for(int w=-1; w<=1; w++) {
//        for (int h = -1; h <= 1; h++) {
//          int px = (int) xi+w;
//          int py = (int) xi+w;
//          if(px>=0 && py>=0 && px<pixels.length && py<pixels[0].length) {
//            // calculate factor depending on position of data point within this bin and next neighbors
//            pixels[px][py] += z * (xi-(int)xi)
//          }
//        }
//      }
//      }
  }

  public int getPixelWidth() {
    return pixelWidth;
  }

  public int getPixelHeight() {
    return pixelHeight;
  }
}
