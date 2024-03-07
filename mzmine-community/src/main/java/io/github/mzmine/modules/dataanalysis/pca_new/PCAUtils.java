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

import io.github.mzmine.datamodel.AbundanceMeasure;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.modules.dataanalysis.significance.StatisticUtils;
import java.util.List;
import java.util.logging.Logger;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;

public class PCAUtils {

  private static final Logger logger = Logger.getLogger(PCAUtils.class.getName());

  public static PCAResult calculatePCA(RealMatrix data) {

    logger.finest(() -> "Performing scaling mean centering");
    final RealMatrix centeredMatrix = StatisticUtils.scaleAndCenter(data, false);

    logger.finest(() -> "Performing singular value decomposition. This may take a while");
    SingularValueDecomposition svd = new SingularValueDecomposition(centeredMatrix);

    return new PCAResult(data, centeredMatrix, svd);
  }

  public static PCARowsResult performPCAOnRows(List<FeatureListRow> rows,
      AbundanceMeasure measure) {
    final List<RawDataFile> files = rows.stream().flatMap(row -> row.getRawDataFiles().stream())
        .distinct().toList();
    final RealMatrix data = StatisticUtils.createDatasetFromRows(rows, files, measure);
    StatisticUtils.imputeMissingValues(data, true, StatisticUtils.zeroImputer);
    final PCAResult pcaResult = calculatePCA(data);
    return new PCARowsResult(pcaResult, rows, files);
  }
}