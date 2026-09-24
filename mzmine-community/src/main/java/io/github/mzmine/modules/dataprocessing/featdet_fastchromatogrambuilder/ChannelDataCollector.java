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
import java.util.Arrays;
import org.jetbrains.annotations.NotNull;

/**
 * Second pass of {@link FastChromatogramBuilder}: replays the trace detection and routes every data
 * point into the channel of its trace. Data points of traces without a channel are loose. At the
 * end of each scan, a loose data point fills the closest channel within the m/z tolerance that has
 * no data point in this scan yet. This fills holes, e.g., from signals that scattered out of their
 * trace, and keeps the low level signals of the full chromatogram like the ADAP builder.
 */
final class ChannelDataCollector implements MassTraceSweepListener {

  private static final int NONE = -1;
  private static final byte EMPTY = 0;
  private static final byte MEMBER = 1;
  private static final byte LOOSE = 2;

  // decision: 5 mDa bins hold few channels, so a lookup scans only a few values
  private static final double CHANNEL_LOOKUP_BIN_WIDTH = 0.005;

  private final MZTolerance tolerance;
  private final long[] memberIds;
  private final int[] memberChannels;
  private final double[] channelCenters;
  private final BinnedLowerBound channelLookup;
  private final ChannelBuffer[] buffers;

  // channel of the trace in each slot of the sweeper or NONE
  private int[] slotChannel = new int[1024];
  private int memberPointer = 0;

  // pending data point of each channel in the current scan
  private final byte[] pendingType;
  private final double[] pendingMz;
  private final double[] pendingIntensity;
  private final double[] pendingDistance;
  private final int[] touchedChannels;
  private int numTouched = 0;

  // loose data points of the current scan
  private double[] looseMz = new double[256];
  private double[] looseIntensity = new double[256];
  private int numLoose = 0;

  // statistics
  private long numMemberDataPoints = 0;
  private long numMemberConflicts = 0;
  private long numLooseDataPoints = 0;
  private long numLooseAssigned = 0;

  ChannelDataCollector(@NotNull ChannelPlan plan, @NotNull MZTolerance tolerance) {
    this.tolerance = tolerance;
    this.memberIds = plan.memberTraceIds();
    this.memberChannels = plan.memberChannels();
    this.channelCenters = plan.channelCenters();
    this.channelLookup = new BinnedLowerBound(channelCenters, CHANNEL_LOOKUP_BIN_WIDTH);
    final int numChannels = channelCenters.length;
    buffers = new ChannelBuffer[numChannels];
    pendingType = new byte[numChannels];
    pendingMz = new double[numChannels];
    pendingIntensity = new double[numChannels];
    pendingDistance = new double[numChannels];
    touchedChannels = new int[numChannels];
  }

  @Override
  public void onTraceCreated(@NotNull MassTraceSweeper sweeper, int slot) {
    if (slot >= slotChannel.length) {
      slotChannel = Arrays.copyOf(slotChannel, Math.max(slot + 1, slotChannel.length * 2));
    }
    // trace ids increase with every new trace, the member ids are sorted
    final long traceId = sweeper.getTraceId(slot);
    while (memberPointer < memberIds.length && memberIds[memberPointer] < traceId) {
      memberPointer++;
    }
    slotChannel[slot] = memberPointer < memberIds.length && memberIds[memberPointer] == traceId
        ? memberChannels[memberPointer] : NONE;
  }

  @Override
  public void onDataPointAssigned(@NotNull MassTraceSweeper sweeper, int slot, int scanIndex,
      double mz, double intensity) {
    final int channel = slotChannel[slot];
    if (channel == NONE) {
      addLoose(mz, intensity);
      return;
    }
    numMemberDataPoints++;
    if (pendingType[channel] == MEMBER) {
      // two traces of one channel in the same scan, keep the more intense signal like ADAP
      numMemberConflicts++;
      if (intensity > pendingIntensity[channel]) {
        pendingMz[channel] = mz;
        pendingIntensity[channel] = intensity;
      }
      return;
    }
    setPending(channel, MEMBER, mz, intensity, 0d);
  }

  @Override
  public void onScanFinished(@NotNull MassTraceSweeper sweeper, int scanIndex) {
    for (int i = 0; i < numLoose; i++) {
      assignLoose(looseMz[i], looseIntensity[i]);
    }
    numLoose = 0;

    for (int i = 0; i < numTouched; i++) {
      final int channel = touchedChannels[i];
      ChannelBuffer buffer = buffers[channel];
      if (buffer == null) {
        buffer = new ChannelBuffer(16);
        buffers[channel] = buffer;
      }
      buffer.add(scanIndex, pendingMz[channel], pendingIntensity[channel]);
      pendingType[channel] = EMPTY;
    }
    numTouched = 0;
  }

  /**
   * Visits the channels in the order of their distance to the m/z and fills the first free one
   * within the tolerance. A closer loose data point replaces a farther one.
   */
  private void assignLoose(double mz, double intensity) {
    numLooseDataPoints++;
    final int numChannels = channelCenters.length;
    int right = channelLookup.lowerBound(mz);
    int left = right - 1;
    while (left >= 0 || right < numChannels) {
      final double leftDistance = left >= 0 ? mz - channelCenters[left] : Double.POSITIVE_INFINITY;
      final double rightDistance =
          right < numChannels ? channelCenters[right] - mz : Double.POSITIVE_INFINITY;
      final int channel;
      final double distance;
      if (leftDistance <= rightDistance) {
        channel = left--;
        distance = leftDistance;
      } else {
        channel = right++;
        distance = rightDistance;
      }
      if (distance > tolerance.getMzToleranceForMass(channelCenters[channel])) {
        // all remaining channels are farther away
        return;
      }
      final byte type = pendingType[channel];
      if (type == MEMBER || (type == LOOSE && pendingDistance[channel] <= distance)) {
        continue;
      }
      if (type == EMPTY) {
        numLooseAssigned++;
      }
      setPending(channel, LOOSE, mz, intensity, distance);
      return;
    }
  }

  private void setPending(int channel, byte type, double mz, double intensity, double distance) {
    if (pendingType[channel] == EMPTY) {
      touchedChannels[numTouched++] = channel;
    }
    pendingType[channel] = type;
    pendingMz[channel] = mz;
    pendingIntensity[channel] = intensity;
    pendingDistance[channel] = distance;
  }

  private void addLoose(double mz, double intensity) {
    if (numLoose == looseMz.length) {
      looseMz = Arrays.copyOf(looseMz, numLoose * 2);
      looseIntensity = Arrays.copyOf(looseIntensity, numLoose * 2);
    }
    looseMz[numLoose] = mz;
    looseIntensity[numLoose] = intensity;
    numLoose++;
  }

  int numChannels() {
    return channelCenters.length;
  }

  /**
   * @return the buffer of channel or null if no data point was added
   */
  ChannelBuffer getBuffer(int channel) {
    return buffers[channel];
  }

  double getChannelCenter(int channel) {
    return channelCenters[channel];
  }

  long getNumMemberDataPoints() {
    return numMemberDataPoints;
  }

  long getNumMemberConflicts() {
    return numMemberConflicts;
  }

  long getNumLooseDataPoints() {
    return numLooseDataPoints;
  }

  long getNumLooseAssigned() {
    return numLooseAssigned;
  }
}
