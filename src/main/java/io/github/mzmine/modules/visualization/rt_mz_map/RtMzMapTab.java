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
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301
 * USA
 */

package io.github.mzmine.modules.visualization.rt_mz_map;

import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.data_access.EfficientDataAccess.ScanDataType;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.gui.chartbasics.chartutils.paintscales.PaintScale;
import io.github.mzmine.gui.mainwindow.MZmineTab;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.util.javafx.FxIconUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 2D visualizer using JFreeChart library
 */
public class RtMzMapTab extends MZmineTab {

  private static final Logger logger = Logger.getLogger(RtMzMapTab.class.getName());

  private static final Image paletteIcon =
      FxIconUtil.loadImageFromResources("icons/colorbaricon.png");
  private static final Image dataPointsIcon =
      FxIconUtil.loadImageFromResources("icons/datapointsicon.png");
  private static final Image axesIcon = FxIconUtil.loadImageFromResources("icons/axesicon.png");
  private static final Image centroidIcon =
      FxIconUtil.loadImageFromResources("icons/centroidicon.png");
  private static final Image continuousIcon =
      FxIconUtil.loadImageFromResources("icons/continuousicon.png");
  private static final Image tooltipsIcon =
      FxIconUtil.loadImageFromResources("icons/tooltips2dploticon.png");
  private static final Image notooltipsIcon =
      FxIconUtil.loadImageFromResources("icons/notooltips2dploticon.png");
  private static final Image logScaleIcon = FxIconUtil.loadImageFromResources("icons/logicon.png");

  private final BorderPane mainPane;
//  private final ToolBar toolBar;

  private final RawDataFile dataFile;
  @Nullable
  private final ScanSelection scanSelection;
  @Nonnull
  private final ScanDataType scanDataType;

  private final RtMzHeatmapChart chart;

  public RtMzMapTab(RawDataFile dataFile, ParameterSet parameters) {
    this(dataFile, parameters.getParameter(RtMzMapParameters.scanSelection).getValue(),
        parameters.getParameter(RtMzMapParameters.plotType).getValue(), parameters.getParameter(RtMzMapParameters.paintScale).getValue());
  }

  public RtMzMapTab(RawDataFile dataFile, @Nullable ScanSelection scanSelection, @Nonnull
      ScanDataType scanDataType,
      PaintScale paintScale) {
    super("Map", true, false);

    this.dataFile = dataFile;
    this.scanSelection = scanSelection;
    this.scanDataType = scanDataType;

    mainPane = new BorderPane();
    setContent(mainPane);

    this.chart = new RtMzHeatmapChart(dataFile, scanSelection, scanDataType, paintScale);
    mainPane.setCenter(chart);

//    toolBar = new ToolBar();
//    toolBar.setOrientation(Orientation.VERTICAL);

//    Button paletteBtn = new Button(null, new ImageView(paletteIcon));
//    paletteBtn.setTooltip(new Tooltip("Switch palette"));
//    paletteBtn.setOnAction(e -> {
//      chart.setPaint.getXYPlot().switchPalette();
//    });

//    toolBar.getItems().addAll(paletteBtn, toggleContinuousModeButton, axesButton,
//        centroidContinuousButton, toggleTooltipButton, logScaleButton);

//    mainPane.setRight(toolBar);

  }

  public RtMzHeatmapChart getChart() {
    return chart;
  }

  @Nonnull
  @Override
  public Collection<? extends RawDataFile> getRawDataFiles() {
    return new ArrayList<>(Collections.singletonList(dataFile));
  }

  @Nonnull
  @Override
  public Collection<? extends FeatureList> getFeatureLists() {
    return Collections.emptyList();
  }

  @Nonnull
  @Override
  public Collection<? extends FeatureList> getAlignedFeatureLists() {
    return Collections.emptyList();
  }

  @Override
  public void onRawDataFileSelectionChanged(Collection<? extends RawDataFile> rawDataFiles) {
  }

  @Override
  public void onFeatureListSelectionChanged(Collection<? extends FeatureList> featureLists) {
  }

  @Override
  public void onAlignedFeatureListSelectionChanged(
      Collection<? extends FeatureList> featureLists) {
  }
}
