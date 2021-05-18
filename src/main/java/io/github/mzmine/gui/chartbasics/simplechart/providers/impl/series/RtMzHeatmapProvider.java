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

package io.github.mzmine.gui.chartbasics.simplechart.providers.impl.series;

import io.github.mzmine.datamodel.MassSpectrum;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.data_access.EfficientDataAccess;
import io.github.mzmine.datamodel.data_access.EfficientDataAccess.ScanDataType;
import io.github.mzmine.datamodel.data_access.ScanDataAccess;
import io.github.mzmine.datamodel.data_access.ScanDataAccessInfo;
import io.github.mzmine.gui.chartbasics.simplechart.providers.MassSpectrumProvider;
import io.github.mzmine.gui.chartbasics.simplechart.providers.PlotXYZDataProvider;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.exceptions.MissingMassListException;
import io.github.mzmine.util.javafx.FxColorUtil;
import java.awt.Color;
import java.util.LinkedHashMap;
import javafx.beans.property.SimpleObjectProperty;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.jfree.chart.renderer.PaintScale;

/**
 * @author Robin Schmid (https://github.com/robinschmid)
 */
public class RtMzHeatmapProvider implements PlotXYZDataProvider,
    MassSpectrumProvider<MassSpectrum> {

  @Nonnull
  private final RawDataFile raw;
  @Nullable
  private final ScanSelection scanSelection;
  @Nonnull
  private final ScanDataType type;
  @Nonnull
  private final ScanDataAccess data;
  private final String seriesKey;
  private final javafx.scene.paint.Color color;
  int numValues = 0;
  // scan info map holds info about the number of data points for a filtered list of scans etc
  private LinkedHashMap<Scan, ScanDataAccessInfo> scanInfoMap;
  private double progress;

  public RtMzHeatmapProvider(@Nonnull final RawDataFile raw) {
    this(raw, null, ScanDataType.CENTROID);
  }

  public RtMzHeatmapProvider(@Nonnull final RawDataFile raw,
      @Nullable final ScanSelection scanSelection, @Nonnull ScanDataType type) {
    this(raw, scanSelection, type, raw.getName(), raw.getColor());
  }

  public RtMzHeatmapProvider(@Nonnull final RawDataFile raw,
      @Nullable final ScanSelection scanSelection, @Nonnull ScanDataType type,
      final String seriesKey, final javafx.scene.paint.Color color) {
    this.raw = raw;
    this.seriesKey = seriesKey;
    this.type = type;
    data = EfficientDataAccess.of(raw, type, scanSelection);
    this.color = color;
    this.scanSelection = scanSelection;
    progress = 1d;
  }

  @Override
  public Color getAWTColor() {
    return FxColorUtil.fxColorToAWT(color);
  }

  @Override
  public javafx.scene.paint.Color getFXColor() {
    return color;
  }

  @Override
  public String getLabel(int index) {
    return null;
  }

  @Nullable
  @Override
  public PaintScale getPaintScale() {
    return null;
  }

  @Override
  public Comparable<?> getSeriesKey() {
    return seriesKey;
  }

  @Override
  public String getToolTipText(int itemIndex) {
    return null;
  }

  @Override
  public void computeValues(SimpleObjectProperty<TaskStatus> status) {
    try {
      numValues = data.getTotalNumberOfDataPointsAcrossScans();
      scanInfoMap = data.getScanInfoMap();
    } catch (MissingMassListException e) {
      e.printStackTrace();
    }
    progress = 1;
  }

  /**
   * The scan that contains a specific data point
   *
   * @param index the data point index
   * @return the scan that contains dataPoint[index] or null
   */
  @Nullable
  public Scan getScanForDataPoint(int index) {
    for (var entry : scanInfoMap.entrySet()) {
      ScanDataAccessInfo info = entry.getValue();
      if (index >= info.numDataPoints()) {
        index -= info.numDataPoints();
      } else {
        return entry.getKey();
      }
    }
    return null;
  }

  /**
   * Returns the retention time of the data point at index
   *
   * @param index data point index in the total number of signals across all filtered scans or mass
   *              lists
   * @return retention time
   */
  @Override
  public double getDomainValue(int index) {
    Scan scan = getScanForDataPoint(index);
    return scan == null ? 0 : scan.getRetentionTime();
  }

  /**
   * Returns the mass to charge ratio (m/z) of the data point at index
   *
   * @param index data point index in the total number of signals across all filtered scans or mass
   *              lists
   * @return m/z
   */
  @Override
  public double getRangeValue(int index) {
    for (var entry : scanInfoMap.entrySet()) {
      ScanDataAccessInfo info = entry.getValue();
      if (index >= info.numDataPoints()) {
        index -= info.numDataPoints();
      } else {
        // needs to access data through the dataAccess to use either scans or mass lists
        // Will read data more efficiently if the data points are accessed in sequential order
        // for example during a chart draw call
        Scan scan = entry.getKey();
        return data.getMzValue(scan, index);
      }
    }
    return 0;
  }


  @Override
  public double getZValue(int index) {
    for (var entry : scanInfoMap.entrySet()) {
      ScanDataAccessInfo info = entry.getValue();
      if (index >= info.numDataPoints()) {
        index -= info.numDataPoints();
      } else {
        // needs to access data through the dataAccess to use either scans or mass lists
        // Will read data more efficiently if the data points are accessed in sequential order
        // for example during a chart draw call
        Scan scan = entry.getKey();
        return data.getIntensityValue(scan, index);
      }
    }
    return 0;
  }

  @Override
  public int getValueCount() {
    return numValues;
  }

  @Override
  public double getComputationFinishedPercentage() {
    return progress;
  }

  @Nullable
  @Override
  public Double getBoxHeight() {
    return null;
  }

  @Nullable
  @Override
  public Double getBoxWidth() {
    return null;
  }

  /**
   * Return either Scan or MassList depending on {@link ScanDataType}
   *
   * @param index data point index
   * @return either Scan or MassList
   */
  @Nullable
  @Override
  public MassSpectrum getSpectrum(int index) {
    Scan scan = getScanForDataPoint(index);
    return scan == null ? null : type.getMassSpectrum(scan);
  }
}
