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

import io.github.mzmine.modules.dataprocessing.featdet_fastchromatogrambuilder.GroundTruthEvaluator.Summary;
import io.github.mzmine.modules.dataprocessing.featdet_fastchromatogrambuilder.SyntheticLcmsData.Ion;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class FastChromatogramBuilderTest {

  private static final MZTolerance TOLERANCE = new MZTolerance(0.002, 10);
  private static final int MIN_CONSECUTIVE = 5;
  private static final double MIN_GROUP_INTENSITY = 1E3;
  private static final double MIN_HEIGHT = 1E4;

  @NotNull
  private static FastChromatogramBuilder builder() {
    return new FastChromatogramBuilder(TOLERANCE, MIN_CONSECUTIVE, MIN_GROUP_INTENSITY, MIN_HEIGHT);
  }

  @NotNull
  private static List<BuiltChromatogram> build(@NotNull SyntheticLcmsData data) {
    return build(builder(), data);
  }

  @NotNull
  private static List<BuiltChromatogram> build(@NotNull FastChromatogramBuilder builder,
      @NotNull SyntheticLcmsData data) {
    final List<BuiltChromatogram> chromatograms = builder.build(data.scans(), null, null);
    Assertions.assertNotNull(chromatograms);
    return chromatograms;
  }

  @NotNull
  private static Summary evaluate(@NotNull SyntheticLcmsData data,
      @NotNull List<BuiltChromatogram> chromatograms) {
    return GroundTruthEvaluator.evaluate(data, EvaluatedChromatogram.of(chromatograms),
        MIN_CONSECUTIVE, MIN_GROUP_INTENSITY, MIN_HEIGHT);
  }

  private static double ppm(double mz, double ppm) {
    return mz * (1 + ppm * 1E-6);
  }

  @Test
  void singleIonWithScatterFormsOneCompleteChromatogram() {
    final SyntheticLcmsData data = SyntheticLcmsData.builder(60).ion(new Ion(500, 30, 4, 1E6, 2, 0))
        .detectionThreshold(500).maxErrorFactor(2).build();
    final List<BuiltChromatogram> chromatograms = build(data);
    Assertions.assertEquals(1, chromatograms.size());
    final Summary summary = evaluate(data, chromatograms);
    Assertions.assertEquals(1, summary.foundIons());
    Assertions.assertEquals(1d, summary.meanCompleteness(), 1E-9);
    Assertions.assertEquals(0, summary.holes());
  }

  @Test
  void isobaricIonsAtDifferentRetentionTimesShareOneChromatogram() {
    // 6 ppm apart, within the tolerance but eluting at different times
    final SyntheticLcmsData data = SyntheticLcmsData.builder(120)
        .ion(new Ion(500, 25, 4, 1E6, 1, 0)).ion(new Ion(ppm(500, 6), 85, 4, 5E5, 1, 0))
        .detectionThreshold(500).maxErrorFactor(2).build();
    final List<BuiltChromatogram> chromatograms = build(data);
    Assertions.assertEquals(1, chromatograms.size(),
        "No duplicate chromatogram within the tolerance");
    final Summary summary = evaluate(data, chromatograms);
    Assertions.assertEquals(2, summary.foundIons());
    Assertions.assertEquals(1d, summary.meanCompleteness(), 1E-9);
    Assertions.assertEquals(0, summary.splitIons());
  }

  @Test
  void coelutingResolvedIonsStaySeparate() {
    // both ions are resolved in every scan, 7 ppm apart
    final SyntheticLcmsData data = SyntheticLcmsData.builder(60)
        .ion(new Ion(500, 30, 4, 1E6, 0.5, 0)).ion(new Ion(ppm(500, 7), 31, 4, 6E5, 0.5, 0))
        .detectionThreshold(500).maxErrorFactor(2).build();
    final List<BuiltChromatogram> chromatograms = build(data);
    Assertions.assertEquals(2, chromatograms.size());
    final Summary summary = evaluate(data, chromatograms);
    Assertions.assertEquals(2, summary.foundIons());
    Assertions.assertEquals(0, summary.splitIons());
    Assertions.assertEquals(0, summary.foreign(), "No signal of the other ion in a chromatogram");
    Assertions.assertTrue(summary.meanCompleteness() > 0.99, summary.toString());
  }

  @Test
  void coelutingIonsWithinToleranceMergeWithoutCollisionSeparation() {
    final SyntheticLcmsData data = SyntheticLcmsData.builder(60)
        .ion(new Ion(500, 30, 4, 1E6, 0.5, 0)).ion(new Ion(ppm(500, 7), 31, 4, 6E5, 0.5, 0))
        .detectionThreshold(500).maxErrorFactor(2).build();
    final FastChromatogramBuilder merging = new FastChromatogramBuilder(TOLERANCE, MIN_CONSECUTIVE,
        MIN_GROUP_INTENSITY, MIN_HEIGHT,
        FastChromatogramBuilderOptions.DEFAULT.withSeparateCollidingTraces(false));
    final List<BuiltChromatogram> chromatograms = build(merging, data);
    Assertions.assertEquals(1, chromatograms.size());
    // the more intense data point wins each scan like in the ADAP builder
    final BuiltChromatogram chromatogram = chromatograms.getFirst();
    for (int i = 0; i < chromatogram.getNumberOfDataPoints(); i++) {
      final int scan = chromatogram.getScanIndex(i);
      double max = 0;
      for (final double intensity : data.intensities[scan]) {
        max = Math.max(max, intensity);
      }
      Assertions.assertEquals(max, chromatogram.getIntensity(i));
    }
  }

  @Test
  void leadingEdgeScatterDoesNotSplitTheChromatogram() {
    // the leading edge scatters to +6 ppm, the first data point after it is 11 ppm away and starts
    // a new trace. Both traces are within the tolerance of the final center and form one channel.
    final double mz = 500;
    final SyntheticLcmsData.Builder builder = SyntheticLcmsData.builder(40);
    final double[] leading = {3E3, 6E3, 1.2E4};
    for (int s = 0; s < leading.length; s++) {
      builder.dataPoint(10 + s, ppm(mz, 6), leading[s], 0);
    }
    builder.dataPoint(13, ppm(mz, -5), 3E4, 0);
    final double[] rest = {1E5, 3E5, 6E5, 1E6, 6E5, 3E5, 1E5, 3E4, 1E4, 3E3};
    for (int s = 0; s < rest.length; s++) {
      builder.dataPoint(14 + s, ppm(mz, s % 2 == 0 ? -1 : -2), rest[s], 0);
    }
    // the ion definition with zero height only labels the explicit data points
    final SyntheticLcmsData data = builder.ion(new Ion(mz, 17, 3, 0)).build();

    final List<BuiltChromatogram> chromatograms = build(data);
    Assertions.assertEquals(1, chromatograms.size(), "The scattered leading edge is no duplicate");
    Assertions.assertEquals(leading.length + 1 + rest.length,
        chromatograms.getFirst().getNumberOfDataPoints());
  }

  @Test
  void centroidJumpBeyondToleranceIsJoinedByComplementaryTraces() {
    // the centroid of one ion jumps from +6 to -6 ppm within the peak, 12 ppm is more than the
    // tolerance. Both halves are traces that never share a scan and form one chromatogram.
    final double mz = 500;
    final double[] profile = {5E3, 2E4, 1E5, 4E5, 8E5, 1E6, 8E5, 4E5, 1E5, 2E4, 5E3};
    final SyntheticLcmsData.Builder builder = SyntheticLcmsData.builder(40);
    for (int s = 0; s < profile.length; s++) {
      builder.dataPoint(10 + s, ppm(mz, s < 5 ? 6 : -6), profile[s], 0);
    }
    final SyntheticLcmsData data = builder.ion(new Ion(mz, 15, 2, 0)).build();
    final List<BuiltChromatogram> chromatograms = build(data);
    Assertions.assertEquals(1, chromatograms.size(), "No duplicate from the centroid jump");
    Assertions.assertEquals(profile.length, chromatograms.getFirst().getNumberOfDataPoints());

    final FastChromatogramBuilder withoutComplementary = new FastChromatogramBuilder(TOLERANCE,
        MIN_CONSECUTIVE, MIN_GROUP_INTENSITY, MIN_HEIGHT,
        FastChromatogramBuilderOptions.DEFAULT.withComplementaryToleranceFactor(1));
    Assertions.assertEquals(2, build(withoutComplementary, data).size(),
        "Without joining, both halves are chromatograms");
  }

  @Test
  void alternatingScatterIsJoinedWithSingleDataPointGap() {
    // one ion alternates between -6 and +6 ppm, two interleaved traces that never share a scan.
    // Each trace alone has no consecutive scans, only joined they form the chromatogram. Traces
    // with one data point need to survive one scan without data point to form the traces.
    final double mz = 500;
    final double[] profile = {5E3, 2E4, 1E5, 4E5, 8E5, 1E6, 8E5, 4E5, 1E5, 2E4, 5E3};
    final SyntheticLcmsData.Builder builder = SyntheticLcmsData.builder(40);
    for (int s = 0; s < profile.length; s++) {
      builder.dataPoint(10 + s, ppm(mz, s % 2 == 0 ? -6 : 6), profile[s], 0);
    }
    final SyntheticLcmsData data = builder.ion(new Ion(mz, 15, 2, 0)).build();
    final FastChromatogramBuilder gapBuilder = new FastChromatogramBuilder(TOLERANCE,
        MIN_CONSECUTIVE, MIN_GROUP_INTENSITY, MIN_HEIGHT,
        FastChromatogramBuilderOptions.DEFAULT.withMaxGapScans(3, 1));
    final List<BuiltChromatogram> chromatograms = build(gapBuilder, data);
    Assertions.assertEquals(1, chromatograms.size());
    Assertions.assertEquals(profile.length, chromatograms.getFirst().getNumberOfDataPoints());
  }

  @Test
  void coelutingIonsBeyondToleranceStaySeparate() {
    // 15 ppm apart and present in the same scans, complementary joining must not merge them
    final SyntheticLcmsData data = SyntheticLcmsData.builder(60)
        .ion(new Ion(500, 30, 4, 1E6, 0.5, 0)).ion(new Ion(ppm(500, 15), 30, 4, 8E5, 0.5, 0))
        .detectionThreshold(500).maxErrorFactor(2).build();
    final List<BuiltChromatogram> chromatograms = build(data);
    Assertions.assertEquals(2, chromatograms.size());
    final Summary summary = evaluate(data, chromatograms);
    Assertions.assertEquals(2, summary.foundIons());
    Assertions.assertEquals(0, summary.foreign());
  }

  @Test
  void noiseCloseToCenterDoesNotReplaceTheSignal() {
    final double mz = 500;
    final SyntheticLcmsData.Builder builder = SyntheticLcmsData.builder(40);
    final double[] profile = {3E3, 1E4, 5E4, 2E5, 6E5, 1E6, 6E5, 2E5, 5E4, 1E4, 3E3};
    for (int s = 0; s < profile.length; s++) {
      // the apex scatters to +4 ppm
      builder.dataPoint(10 + s, ppm(mz, s == 5 ? 4 : 0), profile[s], 0);
    }
    // noise very close to the trace center in the apex scan
    builder.dataPoint(15, ppm(mz, 0.5), 1E3, SyntheticLcmsData.NOISE);
    final SyntheticLcmsData data = builder.ion(new Ion(mz, 15, 2, 0)).build();

    final List<BuiltChromatogram> chromatograms = build(data);
    Assertions.assertEquals(1, chromatograms.size());
    final BuiltChromatogram chromatogram = chromatograms.getFirst();
    Assertions.assertEquals(1E6, chromatogram.getMaxIntensity(),
        "Apex must not be replaced by noise");
    Assertions.assertEquals(0, evaluate(data, chromatograms).replaced());
  }

  @Test
  void driftingIonFormsOneChromatogram() {
    // drifts 0.5 ppm per scan, the edges are further apart than the tolerance
    final SyntheticLcmsData data = SyntheticLcmsData.builder(80)
        .ion(new Ion(500, 40, 6, 1E6, 0.5, 0.5)).detectionThreshold(1E3).maxErrorFactor(2).build();
    final List<BuiltChromatogram> chromatograms = build(data);
    final Summary summary = evaluate(data, chromatograms);
    Assertions.assertEquals(1, summary.foundIons());
    Assertions.assertEquals(0, summary.splitIons(), summary.toString());
    Assertions.assertTrue(summary.meanCompleteness() > 0.95, summary.toString());
  }

  @Test
  void missingScansInsidePeakKeepOneChromatogram() {
    final double mz = 300;
    final SyntheticLcmsData.Builder builder = SyntheticLcmsData.builder(40);
    final double[] profile = {5E3, 2E4, 1E5, 4E5, 8E5, 1E6, 8E5, 4E5, 1E5, 2E4, 5E3};
    for (int s = 0; s < profile.length; s++) {
      // two missing scans, the scans 3 to 7 remain consecutive
      if (s == 2 || s == 8) {
        continue;
      }
      builder.dataPoint(10 + s, mz, profile[s], 0);
    }
    final SyntheticLcmsData data = builder.ion(new Ion(mz, 15, 2, 0)).build();
    final List<BuiltChromatogram> chromatograms = build(data);
    Assertions.assertEquals(1, chromatograms.size());
    Assertions.assertEquals(profile.length - 2, chromatograms.getFirst().getNumberOfDataPoints());
  }

  @Test
  void noisyRandomDataMatchesTheGroundTruth() {
    final SyntheticLcmsData data = randomData(300, 400, 150, 7);
    final List<BuiltChromatogram> chromatograms = build(data);
    final Summary summary = evaluate(data, chromatograms);
    Assertions.assertTrue(summary.detectableIons() > 250, summary.toString());
    Assertions.assertTrue(summary.foundIons() >= summary.detectableIons() * 0.99,
        summary.toString());
    Assertions.assertTrue(summary.meanCompleteness() > 0.98, summary.toString());
    Assertions.assertTrue(summary.splitIons() <= summary.detectableIons() * 0.01,
        summary.toString());
  }

  @Test
  void secondPassReplaysTheFirstPass() {
    final SyntheticLcmsData data = randomData(200, 300, 400, 11);
    final FastChromatogramBuilder builder = builder();
    final List<BuiltChromatogram> first = build(builder, data);
    final FastChromatogramBuilderStatistics statistics = builder.getStatistics();
    Assertions.assertNotNull(statistics);
    Assertions.assertEquals(data.numDataPoints(), statistics.numDataPoints());
    // the replay visits every data point once, either routed into a channel or loose
    Assertions.assertEquals(statistics.numDataPoints(),
        statistics.numMemberDataPoints() + statistics.numLooseDataPoints());
    Assertions.assertTrue(statistics.numMemberDataPoints() > 0);
    assertChromatogramsEqual(first, build(data));
  }

  @Test
  void unsortedScansGiveTheSameResult() {
    final SyntheticLcmsData data = randomData(100, 200, 100, 3);
    final Random random = new Random(1);
    final double[][] mzs = new double[data.numScans()][];
    final double[][] intensities = new double[data.numScans()][];
    for (int s = 0; s < data.numScans(); s++) {
      final int n = data.mzs[s].length;
      final List<Integer> order = new ArrayList<>();
      for (int i = 0; i < n; i++) {
        order.add(i);
      }
      Collections.shuffle(order, random);
      mzs[s] = new double[n];
      intensities[s] = new double[n];
      for (int i = 0; i < n; i++) {
        mzs[s][i] = data.mzs[s][order.get(i)];
        intensities[s][i] = data.intensities[s][order.get(i)];
      }
    }
    final List<BuiltChromatogram> sorted = build(data);
    final List<BuiltChromatogram> shuffled = builder().build(new ArrayScans(mzs, intensities), null,
        null);
    Assertions.assertNotNull(shuffled);
    assertChromatogramsEqual(sorted, shuffled);
  }

  @Test
  void invalidValuesAreIgnored() {
    final double[][] mzs = new double[20][];
    final double[][] intensities = new double[20][];
    for (int s = 0; s < 20; s++) {
      mzs[s] = new double[]{200, 300, 400, Double.NaN};
      intensities[s] = new double[]{0, 5E4, -1, 5E4};
    }
    final List<BuiltChromatogram> chromatograms = builder().build(new ArrayScans(mzs, intensities),
        null, null);
    Assertions.assertNotNull(chromatograms);
    Assertions.assertEquals(1, chromatograms.size());
    Assertions.assertEquals(300, chromatograms.getFirst().getCenterMz(), 1E-9);
    Assertions.assertEquals(20, chromatograms.getFirst().getNumberOfDataPoints());
  }

  @Test
  void cancelStopsTheBuild() {
    final SyntheticLcmsData data = randomData(50, 100, 50, 5);
    Assertions.assertNull(builder().build(data.scans(), () -> true, null));
  }

  @Test
  void filterNeedsConsecutiveScansAboveGroupIntensityAndHeightInSegment() {
    final int[] scans = {0, 1, 2, 3, 4, 6, 7, 8, 9, 10};
    // first segment is long enough but too low, second segment is high but too short
    final double[] intensities = {2E3, 2E3, 2E3, 2E3, 2E3, 5E3, 2E4, 5E3, 5E2, 3E3};
    Assertions.assertFalse(
        FastChromatogramBuilder.passesFilters(scans, intensities, scans.length, 5, 1E3, 1E4));
    Assertions.assertTrue(
        FastChromatogramBuilder.passesFilters(scans, intensities, scans.length, 3, 1E3, 1E4));
    Assertions.assertTrue(
        FastChromatogramBuilder.passesFilters(scans, intensities, scans.length, 5, 1E3, 2E3));
    // a single scan is enough with min consecutive scans of 1
    Assertions.assertTrue(
        FastChromatogramBuilder.passesFilters(scans, intensities, scans.length, 1, 1E3, 1E4));
    Assertions.assertFalse(
        FastChromatogramBuilder.passesFilters(scans, intensities, scans.length, 1, 1E3, 1E5));
  }

  /**
   * Random ions with realistic m/z errors plus random noise.
   */
  @NotNull
  static SyntheticLcmsData randomData(int numIons, int numScans, int noisePerScan, long seed) {
    final Random random = new Random(seed);
    final List<Ion> ions = new ArrayList<>();
    for (int i = 0; i < numIons; i++) {
      final double mz = 100 + random.nextDouble() * 900;
      final double apex = 10 + random.nextDouble() * (numScans - 20);
      final double sigma = 2 + random.nextDouble() * 4;
      final double height = Math.exp(Math.log(5E3) + random.nextDouble() * Math.log(1E3));
      ions.add(new Ion(mz, apex, sigma, height, 0.5 + random.nextDouble(), 0));
    }
    return SyntheticLcmsData.builder(numScans).ions(ions).noise(noisePerScan, 100, 1000, 1E2, 3E3)
        .detectionThreshold(5E2).maxErrorFactor(3).seed(seed).build();
  }

  private static void assertChromatogramsEqual(@NotNull List<BuiltChromatogram> expected,
      @NotNull List<BuiltChromatogram> actual) {
    Assertions.assertEquals(expected.size(), actual.size());
    for (int c = 0; c < expected.size(); c++) {
      final EvaluatedChromatogram a = EvaluatedChromatogram.of(expected.get(c));
      final EvaluatedChromatogram b = EvaluatedChromatogram.of(actual.get(c));
      Assertions.assertArrayEquals(a.scans(), b.scans());
      Assertions.assertArrayEquals(a.mzs(), b.mzs());
      Assertions.assertArrayEquals(a.intensities(), b.intensities());
    }
  }
}
