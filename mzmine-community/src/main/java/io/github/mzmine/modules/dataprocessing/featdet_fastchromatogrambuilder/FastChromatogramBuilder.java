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

import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Detects chromatograms, i.e., one extracted ion chromatogram per m/z channel across all scans, in
 * three steps without sorting or storing all data points:
 * <ol>
 *   <li>First pass over the scans: {@link MassTraceSweeper} connects data points of consecutive
 *   scans to mass traces, locally in retention time. Only trace statistics are kept.</li>
 *   <li>{@link ChannelConsolidation} groups the traces into m/z channels. The traces of one ion
 *   form one channel, which avoids duplicate chromatograms.</li>
 *   <li>Second pass: the identical trace detection is replayed and each data point is routed into
 *   the channel of its trace by {@link ChannelDataCollector}. Remaining data points fill empty
 *   scans of the closest channel within the tolerance, which avoids holes.</li>
 * </ol>
 * Finally, channels need a minimum number of consecutive scans above the group intensity and
 * within this segment a minimum height, the same filters as in the ADAP chromatogram builder.
 * <p>
 * Memory is proportional to the active traces and the detected chromatograms, not to all data
 * points, so there is no limit on the number of data points.
 */
public final class FastChromatogramBuilder {

  private final @NotNull MZTolerance tolerance;
  private final int minConsecutiveScans;
  private final double minGroupIntensity;
  private final double minHeight;
  private final @NotNull FastChromatogramBuilderOptions options;

  private @Nullable FastChromatogramBuilderStatistics statistics;

  /**
   * @param tolerance           scan to scan m/z tolerance
   * @param minConsecutiveScans min number of consecutive scans with an intensity of at least
   *                            minGroupIntensity
   * @param minGroupIntensity   min intensity of the consecutive scans
   * @param minHeight           min height within the consecutive scans. Only traces reaching this
   *                            height can start a chromatogram.
   */
  public FastChromatogramBuilder(@NotNull MZTolerance tolerance, int minConsecutiveScans,
      double minGroupIntensity, double minHeight) {
    this(tolerance, minConsecutiveScans, minGroupIntensity, minHeight,
        FastChromatogramBuilderOptions.DEFAULT);
  }

  /**
   * Constructor with the internal tuning options.
   */
  FastChromatogramBuilder(@NotNull MZTolerance tolerance, int minConsecutiveScans,
      double minGroupIntensity, double minHeight, @NotNull FastChromatogramBuilderOptions options) {
    this.tolerance = tolerance;
    this.minConsecutiveScans = minConsecutiveScans;
    this.minGroupIntensity = minGroupIntensity;
    this.minHeight = minHeight;
    this.options = options;
  }

  /**
   * @param scans      the scans in retention time order, each sorted by m/z
   * @param isCanceled checked regularly, may be null
   * @param progress   receives the progress from 0 to 1, may be null
   * @return the chromatograms sorted by center m/z or null if canceled
   */
  @Nullable
  public List<BuiltChromatogram> build(@NotNull MzIntensityScans scans,
      @Nullable BooleanSupplier isCanceled, @Nullable DoubleConsumer progress) {
    // decision: single data point traces, and short traces that cannot start a channel, keep their
    // data points loose. Loose data points still fill empty scans in the second pass, so this
    // only reduces the work for noise.
    final int minTraceDataPoints = minConsecutiveScans <= 1 ? 1 : 2;
    final int minTraceDataPointsWithoutHeight = Math.max(minTraceDataPoints, minConsecutiveScans);

    long start = System.nanoTime();
    final TraceSummaryCollector summaries = new TraceSummaryCollector(minTraceDataPoints,
        minTraceDataPointsWithoutHeight, minHeight, options.needsCollisions());
    final MassTraceSweeper firstPass = createSweeper(summaries);
    if (!sweep(scans, firstPass, isCanceled, progress, 0d, 0.45d)) {
      return null;
    }
    final long firstPassNanos = System.nanoTime() - start;

    start = System.nanoTime();
    final ChannelPlan plan = ChannelConsolidation.consolidate(summaries, tolerance, minHeight,
        options);
    final long consolidationNanos = System.nanoTime() - start;
    if (progress != null) {
      progress.accept(0.5d);
    }

    start = System.nanoTime();
    final ChannelDataCollector channels = new ChannelDataCollector(plan, tolerance);
    // replay: the second sweeper produces the identical traces and trace ids
    if (!sweep(scans, createSweeper(channels), isCanceled, progress, 0.5d, 0.45d)) {
      return null;
    }
    final long secondPassNanos = System.nanoTime() - start;

    start = System.nanoTime();
    final List<BuiltChromatogram> chromatograms = new ArrayList<>();
    for (int c = 0; c < channels.numChannels(); c++) {
      final ChannelBuffer buffer = channels.getBuffer(c);
      if (buffer != null && passesFilters(buffer)) {
        chromatograms.add(buffer.toChromatogram(channels.getChannelCenter(c)));
      }
    }
    final long filterNanos = System.nanoTime() - start;
    if (progress != null) {
      progress.accept(1d);
    }

    statistics = new FastChromatogramBuilderStatistics(scans.getNumberOfScans(),
        firstPass.getNumDataPoints(), firstPass.getNumTracesCreated(),
        firstPass.getMaxActiveTraces(), plan.numRecordedTraces(), summaries.getNumCollisionEvents(),
        plan.numCollisionPairs(), plan.numChannels(), channels.getNumMemberDataPoints(),
        channels.getNumMemberConflicts(), channels.getNumLooseDataPoints(),
        channels.getNumLooseAssigned(), chromatograms.size(), firstPassNanos, consolidationNanos,
        secondPassNanos, filterNanos);
    return chromatograms;
  }

  @NotNull
  private MassTraceSweeper createSweeper(@NotNull MassTraceSweepListener listener) {
    return new MassTraceSweeper(tolerance, options, listener);
  }

  private static boolean sweep(@NotNull MzIntensityScans scans, @NotNull MassTraceSweeper sweeper,
      @Nullable BooleanSupplier isCanceled, @Nullable DoubleConsumer progress, double progressStart,
      double progressRange) {
    final int numScans = Math.max(1, scans.getNumberOfScans());
    scans.reset();
    int processed = 0;
    while (scans.nextScan()) {
      if (isCanceled != null && isCanceled.getAsBoolean()) {
        return false;
      }
      sweeper.processScan(scans);
      processed++;
      if (progress != null && (processed & 63) == 0) {
        progress.accept(progressStart + progressRange * processed / numScans);
      }
    }
    sweeper.finish();
    return true;
  }

  /**
   * At least {@link #minConsecutiveScans} consecutive scans with an intensity of at least
   * {@link #minGroupIntensity} and within this segment a data point of at least
   * {@link #minHeight}.
   */
  private boolean passesFilters(@NotNull ChannelBuffer buffer) {
    return passesFilters(buffer.scanIndices(), buffer.intensities(), buffer.size(),
        minConsecutiveScans, minGroupIntensity, minHeight);
  }

  static boolean passesFilters(@NotNull int[] scanIndices, @NotNull double[] intensities, int size,
      int minConsecutiveScans, double minGroupIntensity, double minHeight) {
    if (minConsecutiveScans <= 1) {
      for (int i = 0; i < size; i++) {
        if (intensities[i] >= minHeight) {
          return true;
        }
      }
      return false;
    }
    int consecutive = 0;
    int lastScan = Integer.MIN_VALUE;
    double segmentMax = 0d;
    for (int i = 0; i < size; i++) {
      final double intensity = intensities[i];
      if (intensity < minGroupIntensity) {
        consecutive = 0;
        continue;
      }
      if (consecutive > 0 && scanIndices[i] == lastScan + 1) {
        consecutive++;
        segmentMax = Math.max(segmentMax, intensity);
      } else {
        // decision: the height is checked within the consecutive segment, the ADAP builder
        // documents this but never resets its maximum between segments
        consecutive = 1;
        segmentMax = intensity;
      }
      lastScan = scanIndices[i];
      if (consecutive >= minConsecutiveScans && segmentMax >= minHeight) {
        return true;
      }
    }
    return false;
  }

  /**
   * @return the statistics of the last {@link #build} call or null
   */
  @Nullable
  public FastChromatogramBuilderStatistics getStatistics() {
    return statistics;
  }
}
