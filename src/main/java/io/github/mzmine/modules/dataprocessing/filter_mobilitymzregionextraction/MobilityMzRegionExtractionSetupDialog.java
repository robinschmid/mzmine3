/*
 * Copyright 2006-2021 The MZmine Development Team
 *
 * This file is part of MZmine.
 *
 * MZmine is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MZmine; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */

package io.github.mzmine.modules.dataprocessing.filter_mobilitymzregionextraction;

import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.gui.chartbasics.listener.RegionSelectionListener;
import io.github.mzmine.gui.chartbasics.simplechart.RegionSelectionWrapper;
import io.github.mzmine.gui.chartbasics.simplechart.SimpleXYZScatterPlot;
import io.github.mzmine.gui.preferences.UnitFormat;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.visualization.ims_mobilitymzplot.CalculateDatasetsTask;
import io.github.mzmine.modules.visualization.ims_mobilitymzplot.PlotType;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.dialogs.ParameterSetupDialogWithPreview;
import io.github.mzmine.taskcontrol.TaskStatus;
import java.awt.Color;
import java.text.NumberFormat;
import java.util.Collection;
import java.util.List;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;

/**
 * @author https://github.com/SteffenHeu
 */
public class MobilityMzRegionExtractionSetupDialog extends ParameterSetupDialogWithPreview {

  private final SimpleXYZScatterPlot heatmap;
  private final RegionSelectionWrapper<SimpleXYZScatterPlot> wrapper;

  private final NumberFormat rtFormat;
  private final NumberFormat mzFormat;
  private final NumberFormat mobilityFormat;
  private final NumberFormat intensityFormat;
  private final NumberFormat ccsFormat;
  private final UnitFormat unitFormat;
  private final ComboBox<FeatureList> comboBox;

  public MobilityMzRegionExtractionSetupDialog(boolean valueCheckRequired,
      ParameterSet parameters) {
    this(valueCheckRequired, parameters, null);
  }

  public MobilityMzRegionExtractionSetupDialog(boolean valueCheckRequired, ParameterSet parameters,
      String message) {
    super(valueCheckRequired, parameters, message);
    rtFormat = MZmineCore.getConfiguration().getRTFormat();
    mzFormat = MZmineCore.getConfiguration().getMZFormat();
    mobilityFormat = MZmineCore.getConfiguration().getMobilityFormat();
    intensityFormat = MZmineCore.getConfiguration().getIntensityFormat();
    unitFormat = MZmineCore.getConfiguration().getUnitFormat();
    ccsFormat = MZmineCore.getConfiguration().getCCSFormat();

    heatmap = new SimpleXYZScatterPlot<>();
    heatmap.setDomainAxisLabel("m/z");
    heatmap.setDomainAxisNumberFormatOverride(mzFormat);
    heatmap.setRangeAxisLabel("Mobility");
    heatmap.setRangeAxisNumberFormatOverride(mobilityFormat);
    heatmap.setLegendAxisLabel(unitFormat.format("Intensity", "a.u."));
    heatmap.setLegendNumberFormatOverride(intensityFormat);
    heatmap.getXYPlot().setBackgroundPaint(Color.BLACK);
    heatmap.getXYPlot().setDomainCrosshairPaint(Color.LIGHT_GRAY);
    heatmap.getXYPlot().setRangeCrosshairPaint(Color.LIGHT_GRAY);

    wrapper = new RegionSelectionWrapper<>(heatmap);

    previewWrapperPane.setCenter(wrapper);

    FlowPane fp = new FlowPane(new Label("Feature list "));
    fp.setHgap(5);

    var featureLists = FXCollections.observableList(
        MZmineCore.getProjectManager().getCurrentProject().getCurrentFeatureLists());
    comboBox = new ComboBox<>(featureLists);
    comboBox.valueProperty().addListener(((observable, oldValue, newValue) -> parametersChanged()));

    wrapper.getFinishedRegionListeners()
        .addListener((ListChangeListener<RegionSelectionListener>) c -> {
          parameters.getParameter(MobilityMzRegionExtractionParameters.regions)
              .setValue(wrapper.getFinishedRegionsAsListOfPointLists());
        });

    fp.getChildren().add(comboBox);
    fp.setAlignment(Pos.TOP_CENTER);
    previewWrapperPane.setBottom(fp);
  }

  @Override
  protected void parametersChanged() {
    updateParameterSetFromComponents();

    List<? extends FeatureListRow> features = comboBox.getValue().getRows();
    PlotType pt = parameterSet.getParameter(MobilityMzRegionExtractionParameters.ccsOrMobility)
        .getValue();
    if (pt == PlotType.MOBILITY) {
      heatmap.setRangeAxisLabel("Mobility");
      heatmap.setRangeAxisNumberFormatOverride(mobilityFormat);
    } else {
      heatmap.setRangeAxisLabel(unitFormat.format("CCS", "A^2"));
      heatmap.setRangeAxisNumberFormatOverride(ccsFormat);
    }

    heatmap.removeAllDatasets();
    CalculateDatasetsTask calc = new CalculateDatasetsTask(
        (Collection<ModularFeatureListRow>) features, pt, false);
    MZmineCore.getTaskController().addTask(calc);
    calc.addTaskStatusListener((task, newStatus, oldStatus) -> {
      if (newStatus == TaskStatus.FINISHED) {
        Platform.runLater(() -> {
          var datasetsRenderers = calc.getDatasetsRenderers();
          heatmap.addDatasetsAndRenderers(datasetsRenderers);
          heatmap.setLegendPaintScale(calc.getPaintScale());
        });
      }
    });
  }
}
