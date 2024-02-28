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

package io.github.mzmine.modules.io.import_rawdata_all.spectral_processor.processors;

import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.modules.io.import_rawdata_all.spectral_processor.MsProcessor;
import io.github.mzmine.modules.io.import_rawdata_all.spectral_processor.SimpleSpectralArrays;
import io.github.mzmine.util.DataPointSorter;
import io.github.mzmine.util.DataPointUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SortByMzMsProcessor implements MsProcessor {

  /**
   * Maybe add this option later. Scans are usually sorted so we should avoid sorting and generating
   * datapoints if its already sorted.
   */
  private final boolean checkAlreadySorted = true;

  public SortByMzMsProcessor() {
  }

  public @NotNull SimpleSpectralArrays processScan(final @Nullable Scan metadataOnlyScan,
      final @NotNull SimpleSpectralArrays spectrum) {
    // scans are usually sorted so check sorting first
    if (checkAlreadySorted) {
      return DataPointUtils.ensureSortingMzAscendingDefault(spectrum);
    } else {
      // directly apply sorting
      return DataPointUtils.sort(spectrum, DataPointSorter.DEFAULT_MZ_ASCENDING);
    }
  }

  @Override
  public @NotNull String description() {
    return "Sort by m/z";
  }
}