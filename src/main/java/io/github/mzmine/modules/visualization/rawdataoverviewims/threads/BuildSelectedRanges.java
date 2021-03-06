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

package io.github.mzmine.modules.visualization.rawdataoverviewims.threads;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.Frame;
import io.github.mzmine.datamodel.IMSRawDataFile;
import io.github.mzmine.datamodel.impl.SimpleMobilogram;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.FastColoredXYDataset;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.dataprocessing.featdet_mobilogramsmoothing.PreviewMobilogram;
import io.github.mzmine.modules.visualization.chromatogram.TICDataSet;
import io.github.mzmine.modules.visualization.rawdataoverviewims.IMSRawDataOverviewPane;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.util.MobilogramUtils;
import java.awt.Color;
import java.text.NumberFormat;
import java.util.Set;
import javafx.application.Platform;
import javax.annotation.Nonnull;

public class BuildSelectedRanges implements Runnable {

  private final Range<Double> mzRange;
  private final Set<Frame> frames;
  private final IMSRawDataOverviewPane pane;
  private final IMSRawDataFile file;
  private final ScanSelection scanSelection;
  private final Float rtWidth;

  public BuildSelectedRanges(@Nonnull Range<Double> mzRange, @Nonnull Set<Frame> frames,
      @Nonnull IMSRawDataFile file, @Nonnull ScanSelection scanSelection,
      @Nonnull IMSRawDataOverviewPane pane, Float rtWidth) {
    this.mzRange = mzRange;
    this.frames = frames;
    this.pane = pane;
    this.file = file;
    this.scanSelection = scanSelection;
    this.rtWidth = rtWidth;
  }

  @Override
  public void run() {
    Frame frame = frames.stream().findAny().orElse(null);
    if (frame == null) {
      return;
    }
    NumberFormat mzFormat = MZmineCore.getConfiguration().getMZFormat();
    Color color = MZmineCore.getConfiguration().getDefaultColorPalette().getPositiveColorAWT();
    final String seriesKey =
        "m/z " + mzFormat.format(mzRange.lowerEndpoint()) + " - " + mzFormat
            .format(mzRange.upperEndpoint());
    FastColoredXYDataset dataset = null;

    SimpleMobilogram mobilogram = MobilogramUtils.buildMobilogramForMzRange(frames, mzRange);
    if (mobilogram != null) {
      PreviewMobilogram prev = new PreviewMobilogram(mobilogram, seriesKey, true);
      dataset = new FastColoredXYDataset(prev);
      dataset.setColor(color);
    }
    ScanSelection scanSel = new ScanSelection(scanSelection.getScanNumberRange(),
        scanSelection.getBaseFilteringInteger(),
        Range.closed(frame.getRetentionTime() - rtWidth / 2,
            frame.getRetentionTime() + rtWidth / 2), scanSelection.getScanMobilityRange(),
        scanSelection.getPolarity(), scanSelection.getSpectrumType(),
        scanSelection.getMsLevel(), scanSelection.getScanDefinition());
    TICDataSet ticDataSet = new TICDataSet(file, scanSel.getMatchingScans(file),
        mzRange, null);
    ticDataSet.setCustomSeriesKey(seriesKey);
    final FastColoredXYDataset finalDataset = dataset;
    Platform.runLater(() -> pane.setSelectedRangesToChart(finalDataset, ticDataSet, color));
  }
}
