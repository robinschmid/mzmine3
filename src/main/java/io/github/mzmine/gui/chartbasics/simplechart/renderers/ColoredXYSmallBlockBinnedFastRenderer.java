/*
 *  Copyright 2006-2020 The MZmine Development Team
 *
 *  This file is part of MZmine.
 *
 *  MZmine is free software; you can redistribute it and/or modify it under the terms of the GNU
 *  General Public License as published by the Free Software Foundation; either version 2 of the
 *  License, or (at your option) any later version.
 *
 *  MZmine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 *  the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 *  Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with MZmine; if not,
 *  write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301
 *  USA
 */

package io.github.mzmine.gui.chartbasics.simplechart.renderers;

import io.github.mzmine.gui.chartbasics.chartutils.paintscales.PaintScale;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.ColoredXYDataset;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.ColoredXYZDataset;
import io.github.mzmine.taskcontrol.TaskStatus;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.io.Serializable;
import java.util.Arrays;
import org.jfree.chart.LegendItem;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.event.RendererChangeEvent;
import org.jfree.chart.plot.CrosshairState;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.AbstractXYItemRenderer;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYItemRendererState;
import org.jfree.chart.ui.RectangleAnchor;
import org.jfree.chart.util.Args;
import org.jfree.chart.util.PublicCloneable;
import org.jfree.data.Range;
import org.jfree.data.general.DatasetUtils;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYZDataset;

/*
 * =========================================================== JFreeChart : a free chart library for
 * the Java(tm) platform ===========================================================
 *
 * (C) Copyright 2000-2017, by Object Refinery Limited and Contributors.
 *
 * Project Info: http://www.jfree.org/jfreechart/index.html
 *
 * This library is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this library;
 * if not, write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 *
 * [Oracle and Java are registered trademarks of Oracle and/or its affiliates. Other names may be
 * trademarks of their respective owners.]
 *
 * -------------------- XYBlockRenderer.java -------------------- (C) Copyright 2006-2017, by Object
 * Refinery Limited.
 *
 * Original Author: David Gilbert (for Object Refinery Limited); Contributor(s): -;
 *
 * Changes ------- 05-Jul-2006 : Version 1 (DG); 02-Feb-2007 : Added getPaintScale() method (DG);
 * 09-Mar-2007 : Fixed cloning (DG); 03-Aug-2007 : Fix for bug 1766646 (DG); 07-Apr-2008 : Added
 * entity collection code (DG); 22-Apr-2008 : Implemented PublicCloneable (DG); 03-Jul-2013 : Use
 * ParamChecks (DG); 20-Feb-2017 : Add update for crosshairs (DG); 18-Dec-2020 : allow smaller block
 * sizes for plots in MZmine (Ansgar Korf)
 */

public class ColoredXYSmallBlockBinnedFastRenderer extends AbstractXYItemRenderer
    implements XYItemRenderer, Cloneable, PublicCloneable, Serializable {

  /**
   * Minimum pixel size on screen for binning
   */
  private final int MIN_PIXEL_WIDTH = 2;
  private final int MIN_PIXEL_HEIGTH = 2;

  /**
   * The block width (defaults to 1.0).
   */
  private double blockWidth = 1.0;

  /**
   * The block height (defaults to 1.0).
   */
  private double blockHeight = 1.0;

  /**
   * The anchor point used to align each block to its (x, y) location. The default value is {@code
   * RectangleAnchor.CENTER}.
   */
  private RectangleAnchor blockAnchor = RectangleAnchor.CENTER;

  /**
   * Temporary storage for the x-offset used to align the block anchor.
   */
  private double xOffset;

  /**
   * Temporary storage for the y-offset used to align the block anchor.
   */
  private double yOffset;

  /**
   * The paint scale.
   */
  private PaintScale paintScale;

  /**
   * In some case a single paint scall shall be used for multiple datasets.
   */
  private boolean useDatasetPaintScale = true;

  /**
   * Creates a new {@code XYBlockRenderer} instance with default attributes. Item labels are
   * disabled by default.
   */
  public ColoredXYSmallBlockBinnedFastRenderer() {
    updateOffsets();
    setDefaultItemLabelsVisible(false);
  }

  /**
   * Returns the block width, in data/axis units.
   *
   * @return The block width.
   * @see #setBlockWidth(double)
   */
  public double getBlockWidth() {
    return this.blockWidth;
  }

  /**
   * Sets the width of the blocks used to represent each data item and sends a {@link
   * RendererChangeEvent} to all registered listeners.
   *
   * @param width the new width, in data/axis units (must be &gt; 0.0).
   * @see #getBlockWidth()
   */
  public void setBlockWidth(double width) {
    setBlockWidth(width, true);
  }

  public void setBlockWidth(double width, boolean notify) {
    this.blockWidth = width;
    updateOffsets();
    if (notify) {
      fireChangeEvent();
    }
  }

  /**
   * Returns the block height, in data/axis units.
   *
   * @return The block height.
   * @see #setBlockHeight(double)
   */
  public double getBlockHeight() {
    return this.blockHeight;
  }

  /**
   * Sets the height of the blocks used to represent each data item and sends a {@link
   * RendererChangeEvent} to all registered listeners.
   *
   * @param height the new height, in data/axis units (must be &gt; 0.0).
   * @see #getBlockHeight()
   */
  public void setBlockHeight(double height) {
    setBlockHeight(height, true);
  }

  public void setBlockHeight(double height, boolean notify) {
    this.blockHeight = height;
    updateOffsets();
    if (notify) {
      fireChangeEvent();
    }
  }

  /**
   * Returns the anchor point used to align a block at its (x, y) location. The default values is
   * {@link RectangleAnchor#CENTER}.
   *
   * @return The anchor point (never {@code null}).
   */
  public RectangleAnchor getBlockAnchor() {
    return this.blockAnchor;
  }

  /**
   * Returns the paint scale used by the renderer.
   *
   * @return The paint scale (never {@code null}).
   * @see #setPaintScale(PaintScale)
   * @since 1.0.4
   */
  public PaintScale getPaintScale() {
    return this.paintScale;
  }

  /**
   * Sets the paint scale used by the renderer and sends a {@link RendererChangeEvent} to all
   * registered listeners.
   *
   * @param scale the scale ({@code null} not permitted).
   * @see #getPaintScale()
   * @since 1.0.4
   */
  public void setPaintScale(PaintScale scale) {
    this.paintScale = scale;
  }

  /**
   * Updates the offsets to take into account the block width, height and anchor.
   */
  private void updateOffsets() {
    if (this.blockAnchor.equals(RectangleAnchor.BOTTOM_LEFT)) {
      this.xOffset = 0.0;
      this.yOffset = 0.0;
    } else if (this.blockAnchor.equals(RectangleAnchor.BOTTOM)) {
      this.xOffset = -this.blockWidth / 2.0;
      this.yOffset = 0.0;
    } else if (this.blockAnchor.equals(RectangleAnchor.BOTTOM_RIGHT)) {
      this.xOffset = -this.blockWidth;
      this.yOffset = 0.0;
    } else if (this.blockAnchor.equals(RectangleAnchor.LEFT)) {
      this.xOffset = 0.0;
      this.yOffset = -this.blockHeight / 2.0;
    } else if (this.blockAnchor.equals(RectangleAnchor.CENTER)) {
      this.xOffset = -this.blockWidth / 2.0;
      this.yOffset = -this.blockHeight / 2.0;
    } else if (this.blockAnchor.equals(RectangleAnchor.RIGHT)) {
      this.xOffset = -this.blockWidth;
      this.yOffset = -this.blockHeight / 2.0;
    } else if (this.blockAnchor.equals(RectangleAnchor.TOP_LEFT)) {
      this.xOffset = 0.0;
      this.yOffset = -this.blockHeight;
    } else if (this.blockAnchor.equals(RectangleAnchor.TOP)) {
      this.xOffset = -this.blockWidth / 2.0;
      this.yOffset = -this.blockHeight;
    } else if (this.blockAnchor.equals(RectangleAnchor.TOP_RIGHT)) {
      this.xOffset = -this.blockWidth;
      this.yOffset = -this.blockHeight;
    }
  }

  /**
   * Returns the lower and upper bounds (range) of the x-values in the specified dataset.
   *
   * @param dataset the dataset ({@code null} permitted).
   * @return The range ({@code null} if the dataset is {@code null} or empty).
   * @see #findRangeBounds(XYDataset)
   */
  @Override
  public Range findDomainBounds(XYDataset dataset) {
    if (dataset == null) {
      return null;
    }
    if (dataset instanceof ColoredXYDataset ds
        && ds.getStatus() == TaskStatus.FINISHED) {
      return new Range(ds.getDomainValueRange().lowerEndpoint() + this.xOffset,
          ds.getDomainValueRange().upperEndpoint() + this.blockWidth + this.xOffset);
    }
    Range r = DatasetUtils.findDomainBounds(dataset, false);
    if (r == null) {
      return null;
    }
    return new Range(r.getLowerBound() + this.xOffset,
        r.getUpperBound() + this.blockWidth + this.xOffset);
  }

  /**
   * Returns the range of values the renderer requires to display all the items from the specified
   * dataset.
   *
   * @param dataset the dataset ({@code null} permitted).
   * @return The range ({@code null} if the dataset is {@code null} or empty).
   * @see #findDomainBounds(XYDataset)
   */
  @Override
  public Range findRangeBounds(XYDataset dataset) {
    if (dataset != null) {
      if (dataset instanceof ColoredXYZDataset ds
          && ds.getStatus() == TaskStatus.FINISHED) {
        return new Range(ds.getRangeValueRange().lowerEndpoint() + this.yOffset,
            ds.getRangeValueRange().upperEndpoint() + this.blockHeight + this.yOffset);
      }
      Range r = DatasetUtils.findRangeBounds(dataset, false);
      if (r == null) {
        return null;
      } else {
        return new Range(r.getLowerBound() + this.yOffset,
            r.getUpperBound() + this.blockHeight + this.yOffset);
      }
    } else {
      return null;
    }
  }

  @Override
  public XYItemRendererState initialise(Graphics2D g2, Rectangle2D dataArea, XYPlot plot,
      XYDataset dataset, PlotRenderingInfo info) {
    // set block width etc
    if (dataset instanceof ColoredXYZDataset coloredData) {
      // dataset is not already computed
      if(coloredData.getStatus()!=TaskStatus.FINISHED) {
        return new DataNotReadyRendererState(info);
      }
      setDefaultPaint(coloredData.getAWTColor(), false);
      setPaintScale((PaintScale) coloredData.getPaintScale());
      setBlockWidth(coloredData.getBoxWidth(), false);
      setBlockHeight(coloredData.getBoxHeight(), false);
    }

    int dataIndex = plot.indexOf(dataset);
    ValueAxis domainAxis = plot.getDomainAxisForDataset(dataIndex);
    ValueAxis rangeAxis = plot.getRangeAxisForDataset(dataIndex);
    // block width and height
    double xx0 = domainAxis.valueToJava2D(0, dataArea, plot.getDomainAxisEdge());
    double yy0 = rangeAxis.valueToJava2D(0, dataArea, plot.getRangeAxisEdge());
    double xx1 = domainAxis.valueToJava2D(this.blockWidth, dataArea,
        plot.getDomainAxisEdge());
    double yy1 = rangeAxis.valueToJava2D(this.blockHeight, dataArea,
        plot.getRangeAxisEdge());

    // pixel size in absolute pixel - apply minimum size
    int pixelWidth = Math.max((int) Math.round(Math.abs(xx0 - xx1)), MIN_PIXEL_WIDTH);
    int pixelHeight = Math.max((int) Math.round(Math.abs(yy0 - yy1)), MIN_PIXEL_HEIGTH);

    // create state and flush graphics on finish
    BinnedMapRendererState state = new BinnedMapRendererState(info, dataArea, pixelWidth,
        pixelHeight, (rendererState) -> flushMapToScreen(g2, dataArea, rendererState));
    return state;
  }

  public void flushMapToScreen(Graphics2D g2, Rectangle2D dataArea,
      BinnedMapRendererState rendererState) {

    double[][] pixels = rendererState.getPixels();
    int pixelWidth = rendererState.getPixelWidth();
    int pixelHeight = rendererState.getPixelHeight();

    int startX = (int) dataArea.getX();
    int startY = (int) dataArea.getY();

    // default color
    g2.setPaint(getDefaultPaint());

    for (int x = 0; x < pixels.length; x++) {
      for (int y = 0; y < pixels[x].length; y++) {
        double z = pixels[x][y];
        // paintscale is only defined by the renderer - if the dataset changes - it has do update the renderer
        if(this.paintScale!=null) {
          g2.setPaint(paintScale.getPaint(z));
        }
        g2.fillRect(startX + x, startY + y, pixelWidth, pixelHeight);
      }
    }
  }

  /**
   * Draws the block representing the specified item.
   *
   * @param g2             the graphics device.
   * @param state          the state.
   * @param dataArea       the data area.
   * @param info           the plot rendering info.
   * @param plot           the plot.
   * @param domainAxis     the x-axis.
   * @param rangeAxis      the y-axis.
   * @param dataset        the dataset.
   * @param series         the series index.
   * @param item           the item index.
   * @param crosshairState the crosshair state.
   * @param pass           the pass index.
   */
  @Override
  public void drawItem(Graphics2D g2, XYItemRendererState state, Rectangle2D dataArea,
      PlotRenderingInfo info, XYPlot plot, ValueAxis domainAxis, ValueAxis rangeAxis,
      XYDataset dataset, int series, int item, CrosshairState crosshairState, int pass) {
    if (item == 0) {
      System.out.println("Drawing the first item");
    }
    if(state instanceof DataNotReadyRendererState) {
      return;
    }

    double x = dataset.getXValue(series, item);
    double y = dataset.getYValue(series, item);

    double xx = domainAxis.valueToJava2D(x + this.xOffset, dataArea, plot.getDomainAxisEdge());
    double yy = rangeAxis.valueToJava2D(y + this.yOffset, dataArea, plot.getRangeAxisEdge());

    // no need for rect
//    Rectangle2D block;
//    if (orientation.equals(PlotOrientation.HORIZONTAL)) {
//      block = new Rectangle2D.Double(Math.min(yy0, yy1), Math.min(xx0, xx1),
//          Math.abs(yy1 - yy0),
//          Math.abs(xx0 - xx1));
//    } else {
//      block = new Rectangle2D.Double(Math.min(xx0, xx1), Math.min(yy0, yy1),
//          Math.abs(xx1 - xx0),
//          Math.abs(yy1 - yy0));
//    }

    // check if visible
    double z = ((XYZDataset) dataset).getZValue(series, item);
    ((BinnedMapRendererState) state).addValue(z, xx, yy);

    // paintscale is only defined by the renderer - if the dataset changes - it has do update the renderer
//      g2.setPaint(this.paintScale.getPaint(z));
//      g2.fillRect((int) Math.floor(bx), (int) Math.floor(by), (int) Math.ceil(bw),
//          (int) Math.ceil(bh));
//    g2.fill(block);
//    g2.setStroke(new BasicStroke(1.0f));
//    g2.draw(block);

    // We should not use item labels in this type of dataset. Rather add another layer of data with labels for selected datapoints
//    if (isItemLabelVisible(series, item)) {
//      drawItemLabel(g2, orientation, dataset, series, item, block.getCenterX(), block.getCenterY(),
//          y < 0.0);
//    }

    // not needed?
//    int datasetIndex = plot.indexOf(dataset);
//    double transX = domainAxis.valueToJava2D(x, dataArea, plot.getDomainAxisEdge());
//    double transY = rangeAxis.valueToJava2D(y, dataArea, plot.getRangeAxisEdge());
//    updateCrosshairValues(crosshairState, x, y, datasetIndex, transX, transY, orientation);

    // do not add entity - its slow to create entities for all data points
//    EntityCollection entities = state.getEntityCollection();
//    if (entities != null) {
//      addEntity(entities, block, dataset, series, item, block.getCenterX(), block.getCenterY());
//    }

  }


  /**
   * Checks if at least one coordinate of x and y are in range and domain
   *
   * @param x0
   * @param y0
   * @param x1
   * @param y1
   * @param domain
   * @param range
   * @return
   */
  private boolean inRanges(double x0, double y0, double x1, double y1, Range domain, Range range) {
    return (domain.contains(x0) || domain.contains(x1))
           && (range.contains(y0) || range.contains(y1));
  }

  /**
   * Tests this {@code XYBlockRenderer} for equality with an arbitrary object. This method returns
   * {@code true} if and only if:
   * <ul>
   * <li>{@code obj} is an instance of {@code XYBlockRenderer} (not {@code null});</li>
   * <li>{@code obj} has the same field values as this {@code XYBlockRenderer};</li>
   * </ul>
   *
   * @param obj the object ({@code null} permitted).
   * @return A boolean.
   */
  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof ColoredXYSmallBlockBinnedFastRenderer)) {
      return false;
    }
    ColoredXYSmallBlockBinnedFastRenderer that = (ColoredXYSmallBlockBinnedFastRenderer) obj;
    if (this.blockHeight != that.blockHeight) {
      return false;
    }
    if (this.blockWidth != that.blockWidth) {
      return false;
    }
    if (!this.blockAnchor.equals(that.blockAnchor)) {
      return false;
    }
    if (!this.paintScale.equals(that.paintScale)) {
      return false;
    }
    return super.equals(obj);
  }

  public boolean isUseDatasetPaintScale() {
    return useDatasetPaintScale;
  }

  public void setUseDatasetPaintScale(boolean useDatasetPaintScale) {
    this.useDatasetPaintScale = useDatasetPaintScale;
  }

  /**
   * Returns a clone of this renderer.
   *
   * @return A clone of this renderer.
   * @throws CloneNotSupportedException if there is a problem creating the clone.
   */
  @Override
  public Object clone() throws CloneNotSupportedException {
    ColoredXYSmallBlockBinnedFastRenderer clone = (ColoredXYSmallBlockBinnedFastRenderer) super
        .clone();
    clone.setBlockHeight(this.blockHeight);
    clone.setBlockWidth(this.blockWidth);
    if (this.paintScale instanceof PublicCloneable) {
      PublicCloneable pc = (PublicCloneable) this.paintScale;
      clone.paintScale = (PaintScale) pc.clone();
    }
    return clone;
  }

  @Override
  public LegendItem getLegendItem(int datasetIndex, int series) {
    return null;
//    return super.getLegendItem(datasetIndex, series);
  }
}
