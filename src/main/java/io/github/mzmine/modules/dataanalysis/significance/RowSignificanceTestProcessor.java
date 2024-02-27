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

package io.github.mzmine.modules.dataanalysis.significance;

import io.github.mzmine.datamodel.AbundanceMeasure;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.taskcontrol.operations.AbstractTaskSubFunction;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;

public class RowSignificanceTestProcessor extends
    AbstractTaskSubFunction<List<FeatureListRow>, List<RowSignificanceTestResult>> {

  private static final Logger logger = Logger.getLogger(
      RowSignificanceTestProcessor.class.getName());

  private final AbundanceMeasure abundanceMeasure;
  private final RowSignificanceTest test;
  private final AtomicLong processedRows = new AtomicLong(0);
  private final AtomicLong totalRows = new AtomicLong(0);


  public RowSignificanceTestProcessor(AbundanceMeasure abundanceMeasure, RowSignificanceTest test) {
    this.abundanceMeasure = abundanceMeasure;
    this.test = test;
  }

  @Override
  public @NotNull String getTaskDescription() {
    return "Performing statistical test";
  }

  @Override
  public double getFinishedPercentage() {
    return totalRows.get() == 0 ? 0 : (double) processedRows.get() / totalRows.get();
  }

  @Override
  public List<RowSignificanceTestResult> apply(final List<FeatureListRow> rows) {
    totalRows.set(rows.size());

    return rows.stream().map(r -> {
      processedRows.getAndIncrement();
      if (isCanceled()) {
        return null;
      }
      return test.test(r, abundanceMeasure);
    }).filter(Objects::nonNull).toList();
  }

}
