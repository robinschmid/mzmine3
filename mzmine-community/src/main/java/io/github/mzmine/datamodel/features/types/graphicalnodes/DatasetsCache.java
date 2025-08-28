/*
 * Copyright (c) 2004-2025 The mzmine Development Team
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

package io.github.mzmine.datamodel.features.types.graphicalnodes;

import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.ColoredXYDataset;
import io.github.mzmine.javafx.concurrent.threading.FxThread;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import java.util.logging.Logger;
import javafx.beans.property.ListProperty;
import javafx.beans.property.ObjectProperty;
import javafx.collections.FXCollections;

public class DatasetsCache {

  private static final Logger logger = Logger.getLogger(DatasetsCache.class.getName());

  public final ConcurrentHashMap<Integer, List<ColoredXYDataset>> rowIdDatasets = new ConcurrentHashMap<>();

  public void getOrComputeDatasets(ObjectProperty<ModularFeatureListRow> row,
      ListProperty<ColoredXYDataset> target,
      Function<ModularFeatureListRow, List<ColoredXYDataset>> computer) {
    final ModularFeatureListRow currentRow = row.get();
    if (currentRow == null) {
      return;
    }
    List<ColoredXYDataset> datasets = rowIdDatasets.get(currentRow.getID());
    if (datasets != null) {
      target.set(FXCollections.observableArrayList(datasets));
    } else {

      ForkJoinPool.commonPool().execute(() -> {
        final ModularFeatureListRow nowRow = row.get();
        try {
          if (nowRow != currentRow) {
            return;
          }
          List<ColoredXYDataset> computed = computer.apply(nowRow);
          rowIdDatasets.put(nowRow.getID(), computed);
          FxThread.runLater(() -> {
            // only set if still recent
            if (row.get() != currentRow) {
              target.set(FXCollections.observableArrayList(computed));
            }
          });
        } catch (Exception e) {
          logger.warning(
              "Error computing datasets for row " + nowRow.getID() + ": " + e.getMessage());
        }
      });
    }
  }

}
