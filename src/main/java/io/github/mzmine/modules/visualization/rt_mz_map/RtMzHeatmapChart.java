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

package io.github.mzmine.modules.visualization.rt_mz_map;

import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.data_access.EfficientDataAccess.ScanDataType;
import io.github.mzmine.gui.chartbasics.ChartLogics;
import io.github.mzmine.gui.chartbasics.simplechart.SimpleXYZScatterPlot;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.ColoredXYZDataset;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.RunOption;
import io.github.mzmine.gui.chartbasics.simplechart.providers.impl.series.RtMzHeatmapProvider;
import io.github.mzmine.gui.chartbasics.simplechart.renderers.ColoredXYSmallBlockFastRenderer;
import io.github.mzmine.gui.preferences.UnitFormat;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.taskcontrol.TaskStatus;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.jfree.chart.axis.NumberAxis;

/**
 * @author Robin Schmid (https://github.com/robinschmid)
 */
public class RtMzHeatmapChart extends SimpleXYZScatterPlot<RtMzHeatmapProvider> {

  public RtMzHeatmapChart(@Nonnull final RawDataFile raw) {
    this(raw, null, ScanDataType.RAW);
  }

  public RtMzHeatmapChart(@Nonnull final RawDataFile raw,
      @Nullable final ScanSelection scanSelection, @Nonnull ScanDataType type) {
    this(raw, scanSelection, type, raw.getName(), raw.getColor());
  }

  public RtMzHeatmapChart(@Nonnull final RawDataFile raw,
      @Nullable final ScanSelection scanSelection, @Nonnull ScanDataType type,
      final String seriesKey, final javafx.scene.paint.Color color) {
    this(raw, scanSelection, type, seriesKey, color, RunOption.NEW_THREAD);
  }

  public RtMzHeatmapChart(@Nonnull final RawDataFile raw,
      @Nullable final ScanSelection scanSelection, @Nonnull ScanDataType type,
      final String seriesKey, final javafx.scene.paint.Color color, RunOption runOption) {
    super();
    RtMzHeatmapChart chartViwer = this;
    // fast renderer is updated by dataset
    final ColoredXYSmallBlockFastRenderer fastRenderer = new ColoredXYSmallBlockFastRenderer();
    final ColoredXYZDataset dataset = new ColoredXYZDataset(
        new RtMzHeatmapProvider(raw, scanSelection, type, seriesKey, color), runOption) {
      @Override
      protected void onCalculationsFinished() {
        super.onCalculationsFinished();
        // update renderer box width etc
        updateRenderer(this, fastRenderer);
      }
    };

    UnitFormat unitFormat = MZmineCore.getConfiguration().getUnitFormat();
    chartViwer.setRangeAxisLabel("m/z");
    chartViwer.setRangeAxisNumberFormatOverride(MZmineCore.getConfiguration().getMZFormat());
    chartViwer.setDomainAxisLabel(unitFormat.format("Retention time", "min"));
    chartViwer.setDomainAxisNumberFormatOverride(MZmineCore.getConfiguration().getRTFormat());
    chartViwer.setLegendNumberFormatOverride(MZmineCore.getConfiguration().getIntensityFormat());

    NumberAxis axis = (NumberAxis) chartViwer.getXYPlot().getRangeAxis();
    axis.setAutoRange(true);
    axis.setAutoRangeIncludesZero(false);
    axis.setAutoRangeStickyZero(false);
    axis.setAutoRangeMinimumSize(0.005);

    ChartLogics.setAxesTypesPositive(getChart());
//    chartViwer.getXYPlot().setBackgroundPaint(Color.BLACK);

    chartViwer.setDefaultRenderer(fastRenderer);
    chartViwer.setDataset(dataset);

    // rendering speed -> remove entity set
//    this.getRenderingInfo().setEntityCollection(null);

    // todo: save min/max values of dataset in dataset iself so jfreechart does not have to loop
    //  over all data points (also means the renderers have to support it)
//    chartViwer.getXYPlot().getDomainAxis()
//        .setRange(RangeUtils.guavaToJFree(dataset.getDomainValueRange()), false, true);
//    chartViwer.getXYPlot().getRangeAxis()
//        .setRange(RangeUtils.guavaToJFree(dataset.getRangeValueRange()), false, true);
  }

  private void updateRenderer(ColoredXYZDataset dataset,
      ColoredXYSmallBlockFastRenderer renderer) {
    renderer.setPaintScale(dataset.getPaintScale());
    renderer.setBlockWidth(dataset.getBoxWidth(), false);
    renderer.setBlockHeight(dataset.getBoxHeight(), false);
  }

}
