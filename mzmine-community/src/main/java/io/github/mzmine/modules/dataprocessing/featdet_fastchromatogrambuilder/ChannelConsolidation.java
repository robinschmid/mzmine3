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
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import java.util.Arrays;
import java.util.BitSet;
import org.jetbrains.annotations.NotNull;

/**
 * Groups the traces of the first pass into m/z channels, one chromatogram per channel across all
 * scans. This is where duplicate chromatograms are avoided: the traces of one ion, e.g., split by a
 * gap or by m/z scatter at the leading edge of a peak, are combined into one channel.
 * <p>
 * Traces are visited from the highest to the lowest maximum intensity, similar to the seeding of
 * the ADAP chromatogram builder but on traces instead of single data points. A trace joins the
 * closest channel whose seed center is within the m/z tolerance. The seed trace defines the channel
 * position because its center is averaged over many intense data points and therefore the most
 * accurate. A trace without a channel starts a new channel if its maximum intensity reaches the
 * minimum height, otherwise its data points remain loose for the second pass.
 * <p>
 * Optionally, traces that repeatedly received data points in the same scans (collisions) are kept
 * in separate channels. They are signals resolved by the instrument within the tolerance, e.g.,
 * isobaric ions in complex samples.
 */
final class ChannelConsolidation {

  private ChannelConsolidation() {
  }

  /**
   * @param traces    the traces recorded in the first pass
   * @param tolerance max m/z distance between a trace center and a channel seed
   * @param minHeight min maximum intensity of a trace to start a channel
   * @param options   collision handling, see {@link FastChromatogramBuilderOptions}
   */
  @NotNull
  static ChannelPlan consolidate(@NotNull TraceSummaryCollector traces,
      @NotNull MZTolerance tolerance, double minHeight,
      @NotNull FastChromatogramBuilderOptions options) {
    final int n = traces.getNumRecordedTraces();
    if (n == 0) {
      return ChannelPlan.EMPTY;
    }
    final boolean separateCollisions = options.separateCollidingTraces();
    final double maxCollisionFraction = options.maxCollisionFraction();
    final double complementaryFactor = options.complementaryToleranceFactor();
    final long[] ids = traces.traceIds.toLongArray();
    final double[] centers = traces.centers.toDoubleArray();
    final double[] sumIntensities = traces.sumIntensities.toDoubleArray();
    final double[] sumMzIntensities = traces.sumMzIntensities.toDoubleArray();
    final double[] maxIntensities = traces.maxIntensities.toDoubleArray();
    final int[] counts = traces.counts.toIntArray();
    final int[] firstScans = traces.firstScans.toIntArray();
    final int[] lastScans = traces.lastScans.toIntArray();

    // record indices sorted by trace id, used to map collisions and to write the members
    final int[] byId = identity(n);
    IntArrays.quickSort(byId, 0, n, (a, b) -> Long.compare(ids[a], ids[b]));
    final long[] sortedIds = new long[n];
    for (int i = 0; i < n; i++) {
      sortedIds[i] = ids[byId[i]];
    }

    final TraceCollisionGraph collisions =
        options.needsCollisions() ? TraceCollisionGraph.create(traces, sortedIds, byId) : null;

    // most intense traces first, ties by trace id
    final int[] byMax = identity(n);
    IntArrays.quickSort(byMax, 0, n, (a, b) -> {
      final int result = Double.compare(maxIntensities[b], maxIntensities[a]);
      return result != 0 ? result : Long.compare(ids[a], ids[b]);
    });

    // position of each trace in m/z order, seeds are marked in this order
    final int[] byCenter = identity(n);
    IntArrays.quickSort(byCenter, 0, n, (a, b) -> {
      final int result = Double.compare(centers[a], centers[b]);
      return result != 0 ? result : Long.compare(ids[a], ids[b]);
    });
    final int[] rank = new int[n];
    final double[] sortedCenters = new double[n];
    for (int r = 0; r < n; r++) {
      rank[byCenter[r]] = r;
      sortedCenters[r] = centers[byCenter[r]];
    }

    final BitSet seedRanks = new BitSet(n);
    final int[] channelOf = new int[n];
    Arrays.fill(channelOf, -1);
    final DoubleArrayList channelSumIntensity = new DoubleArrayList();
    final DoubleArrayList channelSumMzIntensity = new DoubleArrayList();

    for (final int k : byMax) {
      final double center = centers[k];
      final double tol = tolerance.getMzToleranceForMass(center);
      final int lo = lowerBound(sortedCenters, center - tol);
      final int hi = upperBound(sortedCenters, center + tol);

      int bestChannel = -1;
      double bestDistance = Double.POSITIVE_INFINITY;
      for (int r = seedRanks.nextSetBit(lo); r >= 0 && r < hi; r = seedRanks.nextSetBit(r + 1)) {
        final int seed = byCenter[r];
        final int channel = channelOf[seed];
        final double distance = Math.abs(centers[seed] - center);
        if (distance < bestDistance && (!separateCollisions || collisions.isCompatible(k, channel,
            channelOf, counts, maxCollisionFraction))) {
          bestChannel = channel;
          bestDistance = distance;
        }
      }

      if (bestChannel < 0 && complementaryFactor > 1d) {
        // decision: one ion yields one data point per scan. A trace next to a channel that
        // overlaps in time with its seed, or continues it after a short gap, but never shares a
        // scan with it is the same ion with a larger m/z scatter or a jump of the centroid. Two
        // co-eluting ions would share most scans.
        final double wideTol = complementaryFactor * tol;
        final int wideLo = lowerBound(sortedCenters, center - wideTol);
        final int wideHi = upperBound(sortedCenters, center + wideTol);
        final int maxScanGap = options.maxGapScans() + 1;
        for (int r = seedRanks.nextSetBit(wideLo); r >= 0 && r < wideHi;
            r = seedRanks.nextSetBit(r + 1)) {
          final int seed = byCenter[r];
          final double distance = Math.abs(centers[seed] - center);
          // seeds within the tolerance were incompatible above
          if (distance <= tol || distance >= bestDistance
              || lastScans[k] + maxScanGap < firstScans[seed]
              || firstScans[k] > lastScans[seed] + maxScanGap) {
            continue;
          }
          final int channel = channelOf[seed];
          if (collisions.hasNoCollision(k, channel, channelOf)) {
            bestChannel = channel;
            bestDistance = distance;
          }
        }
      }

      if (bestChannel >= 0) {
        channelOf[k] = bestChannel;
        channelSumIntensity.set(bestChannel,
            channelSumIntensity.getDouble(bestChannel) + sumIntensities[k]);
        channelSumMzIntensity.set(bestChannel,
            channelSumMzIntensity.getDouble(bestChannel) + sumMzIntensities[k]);
      } else if (maxIntensities[k] >= minHeight) {
        // decision: like in the ADAP builder, only intense signals may start a chromatogram,
        // weaker traces can only join an existing one
        final int channel = channelSumIntensity.size();
        channelOf[k] = channel;
        seedRanks.set(rank[k]);
        channelSumIntensity.add(sumIntensities[k]);
        channelSumMzIntensity.add(sumMzIntensities[k]);
      }
    }

    return createPlan(n, sortedIds, byId, channelOf, channelSumIntensity, channelSumMzIntensity,
        collisions == null ? 0 : collisions.numPairs());
  }

  @NotNull
  private static ChannelPlan createPlan(int n, @NotNull long[] sortedIds, @NotNull int[] byId,
      @NotNull int[] channelOf, @NotNull DoubleArrayList channelSumIntensity,
      @NotNull DoubleArrayList channelSumMzIntensity, int numCollisionPairs) {
    final int numChannels = channelSumIntensity.size();
    final double[] channelCenters = new double[numChannels];
    for (int c = 0; c < numChannels; c++) {
      channelCenters[c] = channelSumMzIntensity.getDouble(c) / channelSumIntensity.getDouble(c);
    }
    // channel index = position in m/z order
    final int[] channelsByCenter = identity(numChannels);
    IntArrays.quickSort(channelsByCenter, 0, numChannels, (a, b) -> {
      final int result = Double.compare(channelCenters[a], channelCenters[b]);
      return result != 0 ? result : Integer.compare(a, b);
    });
    final int[] newChannelIndex = new int[numChannels];
    final double[] sortedChannelCenters = new double[numChannels];
    for (int i = 0; i < numChannels; i++) {
      newChannelIndex[channelsByCenter[i]] = i;
      sortedChannelCenters[i] = channelCenters[channelsByCenter[i]];
    }

    int numMembers = 0;
    for (int i = 0; i < n; i++) {
      if (channelOf[i] >= 0) {
        numMembers++;
      }
    }
    final long[] memberIds = new long[numMembers];
    final int[] memberChannels = new int[numMembers];
    int m = 0;
    for (int i = 0; i < n; i++) {
      final int k = byId[i];
      if (channelOf[k] >= 0) {
        memberIds[m] = sortedIds[i];
        memberChannels[m] = newChannelIndex[channelOf[k]];
        m++;
      }
    }
    return new ChannelPlan(memberIds, memberChannels, sortedChannelCenters, n, numCollisionPairs);
  }

  @NotNull
  static int[] identity(int n) {
    final int[] indices = new int[n];
    for (int i = 0; i < n; i++) {
      indices[i] = i;
    }
    return indices;
  }

  /**
   * @return first index with values[index] >= key
   */
  static int lowerBound(@NotNull double[] values, double key) {
    int lo = 0;
    int hi = values.length;
    while (lo < hi) {
      final int mid = (lo + hi) >>> 1;
      if (values[mid] < key) {
        lo = mid + 1;
      } else {
        hi = mid;
      }
    }
    return lo;
  }

  /**
   * @return first index with values[index] > key
   */
  static int upperBound(@NotNull double[] values, double key) {
    int lo = 0;
    int hi = values.length;
    while (lo < hi) {
      final int mid = (lo + hi) >>> 1;
      if (values[mid] <= key) {
        lo = mid + 1;
      } else {
        hi = mid;
      }
    }
    return lo;
  }
}
