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

import org.jetbrains.annotations.NotNull;

/**
 * Internal tuning options of the {@link FastChromatogramBuilder}. They are not user parameters, the
 * defaults were chosen with the benchmark in ChromatogramBuilderBenchmark (test sources).
 *
 * @param maxGapScans                  scans without data point after which a trace is closed.
 *                                     Longer gaps split traces, which are joined again in the
 *                                     channel consolidation. Small values keep the number of active
 *                                     traces low.
 * @param singleDataPointMaxGapScans   same for traces with a single data point. Most of these are
 *                                     noise, closing them early keeps the active traces few. The
 *                                     data point of a real signal is not lost, it fills its
 *                                     chromatogram as loose data point in the second pass.
 * @param intensityJumpFactor          intensity ratio between neighboring scans of a trace that is
 *                                     expected within a peak and matched without extra cost
 * @param intensityJumpWeight          weight of the matching cost for larger intensity jumps, 0
 *                                     matches on the m/z distance only
 * @param separateCollidingTraces      traces within the tolerance that repeatedly receive data
 *                                     points in the same scans are kept in separate channels
 * @param maxCollisionFraction         collisions tolerated between traces of one channel as
 *                                     fraction of the smaller trace
 * @param complementaryToleranceFactor traces up to this multiple of the tolerance away from a
 *                                     channel join it if they overlap in time without ever sharing
 *                                     a scan, the typical pattern of one ion with a large m/z
 *                                     scatter. Values <= 1 disable this.
 */
record FastChromatogramBuilderOptions(int maxGapScans, int singleDataPointMaxGapScans,
                                      double intensityJumpFactor, double intensityJumpWeight,
                                      boolean separateCollidingTraces, double maxCollisionFraction,
                                      double complementaryToleranceFactor) {

  static final FastChromatogramBuilderOptions DEFAULT = new FastChromatogramBuilderOptions(3, 0, 5d,
      0.25d, true, 0.1d, 2d);

  FastChromatogramBuilderOptions {
    if (maxGapScans < 0 || singleDataPointMaxGapScans < 0) {
      throw new IllegalArgumentException("Gaps must be >= 0");
    }
  }

  @NotNull FastChromatogramBuilderOptions withIntensityJumpWeight(double weight) {
    return new FastChromatogramBuilderOptions(maxGapScans, singleDataPointMaxGapScans,
        intensityJumpFactor, weight, separateCollidingTraces, maxCollisionFraction,
        complementaryToleranceFactor);
  }

  @NotNull FastChromatogramBuilderOptions withSeparateCollidingTraces(boolean separate) {
    return new FastChromatogramBuilderOptions(maxGapScans, singleDataPointMaxGapScans,
        intensityJumpFactor, intensityJumpWeight, separate, maxCollisionFraction,
        complementaryToleranceFactor);
  }

  @NotNull FastChromatogramBuilderOptions withComplementaryToleranceFactor(double factor) {
    return new FastChromatogramBuilderOptions(maxGapScans, singleDataPointMaxGapScans,
        intensityJumpFactor, intensityJumpWeight, separateCollidingTraces, maxCollisionFraction,
        factor);
  }

  @NotNull FastChromatogramBuilderOptions withMaxGapScans(int gapScans,
      int singleDataPointGapScans) {
    return new FastChromatogramBuilderOptions(gapScans, singleDataPointGapScans,
        intensityJumpFactor, intensityJumpWeight, separateCollidingTraces, maxCollisionFraction,
        complementaryToleranceFactor);
  }

  /**
   * @return true if the first pass needs to record collisions
   */
  boolean needsCollisions() {
    return separateCollidingTraces || complementaryToleranceFactor > 1d;
  }

  /**
   * @return the m/z window for collisions as multiple of the tolerance
   */
  double collisionToleranceFactor() {
    return Math.max(1d, complementaryToleranceFactor);
  }
}
