/*
 * Copyright (c) 2004-2024 The MZmine Development Team
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package io.github.mzmine.modules.dataanalysis.pca_new;

import io.github.mzmine.datamodel.features.FeatureAnnotationPriority;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.types.DataType;
import io.github.mzmine.datamodel.features.types.DataTypes;
import io.github.mzmine.datamodel.features.types.annotations.MissingValueType;
import io.github.mzmine.gui.chartbasics.simplechart.providers.PlotXYZDataProvider;
import io.github.mzmine.gui.chartbasics.simplechart.providers.SimpleXYProvider;
import io.github.mzmine.gui.chartbasics.simplechart.providers.XYItemObjectProvider;
import io.github.mzmine.gui.chartbasics.simplechart.providers.ZCategoryProvider;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.DataTypeUtils;
import io.github.mzmine.util.color.SimpleColorPalette;
import java.awt.Color;
import java.util.List;
import java.util.TreeMap;
import javafx.beans.property.Property;
import org.apache.commons.math3.linear.RealMatrix;
import org.jetbrains.annotations.Nullable;
import org.jfree.chart.renderer.LookupPaintScale;
import org.jfree.chart.renderer.PaintScale;

public class LoadingsProvider extends SimpleXYProvider implements PlotXYZDataProvider,
    ZCategoryProvider, XYItemObjectProvider<FeatureListRow> {

  private final PCARowsResult result;
  private final int loadingsY;
  private final int loadingsX;

  private int[] zCategories;
  private int numberOfCategories;
  private LookupPaintScale paintScale;
  private String[] legendNames;

  /**
   * @param loadingsX index of the principal component used for domain axis, subtract 1 from the
   *                  number since the pc matrix starts at 0.
   * @param loadingsY index of the principal component used for range axis, subtract 1 from the
   *                  number since the pc matrix starts at 0.
   */
  public LoadingsProvider(PCARowsResult result, String seriesKey, Color awt, int loadingsX,
      int loadingsY) {
    super(seriesKey, awt);
    this.result = result;
    this.loadingsX = loadingsX;
    this.loadingsY = loadingsY;
  }

  public LoadingsProvider(PCARowsResult result, String seriesKey, Color awt) {
    this(result, seriesKey, awt, 0, 1);
  }

  @Override
  public void computeValues(Property<TaskStatus> status) {
    final PCAResult pcaResult = result.pcaResult();

    final RealMatrix loadingsMatrix = pcaResult.getLoadingsMatrix();

    final TreeMap<DataType<?>, List<FeatureListRow>> groupedRows = (TreeMap<DataType<?>, List<FeatureListRow>>) DataTypeUtils.groupByBestDataType(
        result.rows(), true, FeatureAnnotationPriority.getDataTypesInOrder());
    final List<DataType<?>> typesInOrder = groupedRows.keySet().stream().toList();
    numberOfCategories = groupedRows.size();

    double[] domainData = new double[loadingsMatrix.getColumnDimension()];
    double[] rangeData = new double[loadingsMatrix.getColumnDimension()];
    zCategories = new int[loadingsMatrix.getColumnDimension()];
    assert result.rows().size() == loadingsMatrix.getColumnDimension();

    final MissingValueType missing = DataTypes.get(MissingValueType.class);
    for (int i = 0; i < loadingsMatrix.getColumnDimension(); i++) {
      domainData[i] = loadingsMatrix.getEntry(loadingsX, i);
      rangeData[i] = loadingsMatrix.getEntry(loadingsY, i);
      final DataType<?> bestTypeWithValue = DataTypeUtils.getBestTypeWithValue(result.rows().get(i),
          missing, FeatureAnnotationPriority.getDataTypesInOrder());
      zCategories[i] = typesInOrder.indexOf(bestTypeWithValue);
    }

    paintScale = new LookupPaintScale(0, numberOfCategories, Color.BLACK);
    final SimpleColorPalette colors = MZmineCore.getConfiguration().getDefaultColorPalette();
    for (int i = 0; i < numberOfCategories; i++) {
      paintScale.add(i, colors.getAWT(i));
    }

    legendNames = typesInOrder.stream()
        .map(type -> type instanceof MissingValueType _ ? "Not annotated" : type.getHeaderString())
        .toArray(String[]::new);

    setxValues(domainData);
    setyValues(rangeData);
  }

  @Override
  public @Nullable PaintScale getPaintScale() {
    return paintScale;
  }

  @Override
  public double getZValue(int index) {
    return zCategories[index];
  }

  @Override
  public @Nullable Double getBoxHeight() {
    return 5d;
  }

  @Override
  public @Nullable Double getBoxWidth() {
    return 5d;
  }

  @Override
  public int getNumberOfCategories() {
    return numberOfCategories;
  }

  @Override
  public String getLegendLabel(int category) {
    return legendNames[category];
  }

  @Override
  public FeatureListRow getItemObject(int item) {
    return result.rows().get(item);
  }

  @Override
  public String getToolTipText(int itemIndex) {
    return getItemObject(itemIndex).toString();
  }
}