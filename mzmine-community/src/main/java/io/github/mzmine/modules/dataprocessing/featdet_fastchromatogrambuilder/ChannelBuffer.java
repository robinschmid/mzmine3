/*
 * Copyright (c) 2004-2026 The mzmine Development Team
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

package io.github.mzmine.modules.dataprocessing.featdet_fastchromatogrambuilder;

import java.util.Arrays;
import org.jetbrains.annotations.NotNull;

/**
 * Growable primitive storage of the data points of one channel, appended in scan order.
 */
final class ChannelBuffer {

  private int[] scanIndices;
  private double[] mzs;
  private double[] intensities;
  private int size = 0;

  ChannelBuffer(int initialCapacity) {
    final int capacity = Math.max(4, initialCapacity);
    scanIndices = new int[capacity];
    mzs = new double[capacity];
    intensities = new double[capacity];
  }

  void add(int scanIndex, double mz, double intensity) {
    if (size == scanIndices.length) {
      final int capacity = size + (size >> 1) + 1;
      scanIndices = Arrays.copyOf(scanIndices, capacity);
      mzs = Arrays.copyOf(mzs, capacity);
      intensities = Arrays.copyOf(intensities, capacity);
    }
    scanIndices[size] = scanIndex;
    mzs[size] = mz;
    intensities[size] = intensity;
    size++;
  }

  int size() {
    return size;
  }

  @NotNull int[] scanIndices() {
    return scanIndices;
  }

  @NotNull double[] intensities() {
    return intensities;
  }

  @NotNull BuiltChromatogram toChromatogram(double centerMz) {
    return new BuiltChromatogram(centerMz, Arrays.copyOf(scanIndices, size),
        Arrays.copyOf(mzs, size), Arrays.copyOf(intensities, size));
  }
}
