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

import io.github.mzmine.util.ArrayUtils;
import java.awt.geom.Rectangle2D;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.renderer.xy.XYItemRendererState;

/**
 * @author Robin Schmid (https://github.com/robinschmid)
 */
public class BinnedMapRendererState extends XYItemRendererState {

  private final Rectangle2D dataArea;
  /**
   * pixels grid [x][y] -> intensity
   */
  private final double[][] pixels;
  private final int startX;
  private final int startY;

  public BinnedMapRendererState(PlotRenderingInfo info, Rectangle2D dataArea) {
    super(info);
    this.dataArea = dataArea;
    // disable visible items optimisation - it doesn't work for this
    // renderer...
    setProcessVisibleItemsOnly(false);

    // start
    startX = (int) dataArea.getX();
    startY = (int) dataArea.getY();

    // create data array
    int w = (int) dataArea.getWidth()+2;
    int h = (int) dataArea.getHeight()+2;
    this.pixels = new double[w][h];

    // fill all with NaN
    ArrayUtils.fill2D(pixels, Double.NaN);
  }

  /**
   * pixels grid [x][y] -> intensity
   * @return intensity or NaN in array if no value
   */
  public double[][] getPixels() {
    return pixels;
  }

  /**
   * block coordinates and width and height
   * @param z intensity
   * @param bx x
   * @param by y
   * @param bw block width
   * @param bh block height
   */
  public void addValue(double z, double bx, double by, double bw, double bh) {
    double endX = bx+bw;
    // fill pixel array from - to
    for(int xi=(int)bx; xi<bx+bw; xi++) {

    }
  }
}
