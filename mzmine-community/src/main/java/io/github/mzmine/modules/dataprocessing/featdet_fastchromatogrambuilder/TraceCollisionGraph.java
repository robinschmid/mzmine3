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

import it.unimi.dsi.fastutil.longs.LongArrays;
import java.util.Arrays;
import org.jetbrains.annotations.NotNull;

/**
 * Collision counts between recorded traces as adjacency lists (compressed sparse rows). Two traces
 * collide in a scan if both received a data point while being within the m/z tolerance of each
 * other. Traces of the same ion rarely collide, co-eluting signals resolved by the instrument
 * collide in every scan they share.
 */
final class TraceCollisionGraph {

  private final int[] offsets;
  private final int[] partners;
  private final int[] partnerCounts;
  private final int numPairs;

  private TraceCollisionGraph(@NotNull int[] offsets, @NotNull int[] partners,
      @NotNull int[] partnerCounts, int numPairs) {
    this.offsets = offsets;
    this.partners = partners;
    this.partnerCounts = partnerCounts;
    this.numPairs = numPairs;
  }

  /**
   * @param traces    recorded traces and collision events of the first pass
   * @param sortedIds trace ids of the records sorted ascending
   * @param byId      record index for each position in sortedIds
   */
  @NotNull
  static TraceCollisionGraph create(@NotNull TraceSummaryCollector traces,
      @NotNull long[] sortedIds, @NotNull int[] byId) {
    final int n = sortedIds.length;
    final int events = traces.getNumCollisionEvents();
    // pack the record indices of both traces into one long, lower index in the upper bits
    final long[] packed = new long[events];
    int numPacked = 0;
    for (int e = 0; e < events; e++) {
      final int a = findRecord(sortedIds, byId, traces.collisionTraceA.getLong(e));
      final int b = findRecord(sortedIds, byId, traces.collisionTraceB.getLong(e));
      if (a < 0 || b < 0) {
        // one of the traces was not recorded, e.g., too few data points
        continue;
      }
      final long lo = Math.min(a, b);
      final long hi = Math.max(a, b);
      packed[numPacked++] = (lo << 32) | hi;
    }
    LongArrays.radixSort(packed, 0, numPacked);

    // unique pairs with their number of collision events
    final int[] pairA = new int[numPacked];
    final int[] pairB = new int[numPacked];
    final int[] pairCount = new int[numPacked];
    int numPairs = 0;
    for (int i = 0; i < numPacked; i++) {
      if (numPairs > 0 && packed[i] == packed[i - 1]) {
        pairCount[numPairs - 1]++;
        continue;
      }
      pairA[numPairs] = (int) (packed[i] >>> 32);
      pairB[numPairs] = (int) packed[i];
      pairCount[numPairs] = 1;
      numPairs++;
    }

    final int[] offsets = new int[n + 1];
    for (int i = 0; i < numPairs; i++) {
      offsets[pairA[i] + 1]++;
      offsets[pairB[i] + 1]++;
    }
    for (int i = 0; i < n; i++) {
      offsets[i + 1] += offsets[i];
    }
    final int[] fill = Arrays.copyOf(offsets, n);
    final int[] partners = new int[numPairs * 2];
    final int[] partnerCounts = new int[numPairs * 2];
    for (int i = 0; i < numPairs; i++) {
      partners[fill[pairA[i]]] = pairB[i];
      partnerCounts[fill[pairA[i]]++] = pairCount[i];
      partners[fill[pairB[i]]] = pairA[i];
      partnerCounts[fill[pairB[i]]++] = pairCount[i];
    }
    return new TraceCollisionGraph(offsets, partners, partnerCounts, numPairs);
  }

  private static int findRecord(@NotNull long[] sortedIds, @NotNull int[] byId, long traceId) {
    final int index = Arrays.binarySearch(sortedIds, traceId);
    return index >= 0 ? byId[index] : -1;
  }

  /**
   * @param trace                record index of the trace
   * @param channel              the candidate channel
   * @param channelOf            channel of each record or -1
   * @param counts               number of data points of each record
   * @param maxCollisionFraction collisions tolerated as fraction of the smaller trace
   * @return false if the trace collided with a trace of the channel more often than tolerated
   */
  boolean isCompatible(int trace, int channel, @NotNull int[] channelOf, @NotNull int[] counts,
      double maxCollisionFraction) {
    for (int i = offsets[trace]; i < offsets[trace + 1]; i++) {
      final int partner = partners[i];
      if (channelOf[partner] != channel) {
        continue;
      }
      // decision: a single collision is always tolerated, e.g., noise that won one scan
      final int allowed = Math.max(1,
          (int) (maxCollisionFraction * Math.min(counts[trace], counts[partner])));
      if (partnerCounts[i] > allowed) {
        return false;
      }
    }
    return true;
  }

  /**
   * @return true if the trace never collided with a trace of the channel
   */
  boolean hasNoCollision(int trace, int channel, @NotNull int[] channelOf) {
    for (int i = offsets[trace]; i < offsets[trace + 1]; i++) {
      if (channelOf[partners[i]] == channel) {
        return false;
      }
    }
    return true;
  }

  /**
   * @return number of trace pairs that collided at least once
   */
  int numPairs() {
    return numPairs;
  }
}
