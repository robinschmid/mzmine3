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

import static io.github.mzmine.modules.dataprocessing.featdet_fastchromatogrambuilder.ChromatogramBenchmarkDatasets.PROPERTY;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.data_access.EfficientDataAccess;
import io.github.mzmine.datamodel.data_access.EfficientDataAccess.ScanDataType;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.modules.dataprocessing.featdet_adapchromatogrambuilder.ADAPChromatogramBuilderParameters;
import io.github.mzmine.modules.dataprocessing.featdet_adapchromatogrambuilder.ModularADAPChromatogramBuilderModule;
import io.github.mzmine.modules.dataprocessing.featdet_adapchromatogrambuilder.ModularADAPChromatogramBuilderTask;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.ResolvingDimension;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.minimumsearch.MinimumSearchFeatureResolverModule;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.minimumsearch.MinimumSearchFeatureResolverParameters;
import io.github.mzmine.modules.dataprocessing.featdet_fastchromatogrambuilder.GroundTruthEvaluator.Summary;
import io.github.mzmine.modules.dataprocessing.featdet_fastchromatogrambuilder.SyntheticLcmsData.Ion;
import io.github.mzmine.modules.dataprocessing.filter_groupms2.GroupMS2SubParameters;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.OriginalFeatureListHandlingParameter.OriginalFeatureListOption;
import io.github.mzmine.parameters.parametertypes.selectors.FeatureListsSelection;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilesSelection;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilesSelectionType;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.project.ProjectService;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import testutils.MZmineTestUtil;
import testutils.TaskResult;

/**
 * Compares the ADAP chromatogram builder with the {@link FastChromatogramBuilder} in speed and
 * quality. Run with
 * <pre>
 * gradlew :mzmine-community:benchmark --tests "*ChromatogramBuilderBenchmark*"
 * </pre>
 * The real data sets and their options are in {@link ChromatogramBenchmarkDatasets}, e.g.,
 * {@code -Dmzmine.test.chrombench.only=GC-EI-QTOF}. More options as system properties:
 * {@code .repeats=<timing repeats>}, {@code .out=<report folder>}. The report is written as
 * markdown.
 */
@Tag("benchmark")
@TestInstance(Lifecycle.PER_CLASS)
class ChromatogramBuilderBenchmark {

  private static final Logger logger = Logger.getLogger(
      ChromatogramBuilderBenchmark.class.getName());

  // decision: features within the tolerance and 0.03 min are the same feature, ~2 scans
  private static final float RT_TOLERANCE = 0.03f;
  // quantile of the chromatogram intensities below which the resolver removes data points
  private static final double CHROMATOGRAPHIC_THRESHOLD = 0.9;
  // decision: dips count between data points of 10 x the min height, clearly above the noise
  private static final double DIP_FLANK_FACTOR = 10;

  private final List<String> report = new ArrayList<>();

  @BeforeAll
  void init() {
    MZmineTestUtil.startMzmineCore();
  }

  /**
   * Builder settings of one benchmark.
   *
   * @param scanSelection the scans of both builders
   */
  record Settings(@NotNull MZTolerance tolerance, int minConsecutive, double minGroupIntensity,
                  double minHeight, @NotNull ScanSelection scanSelection) {

    @NotNull
    static Settings of(@NotNull ChromatogramBenchmarkDataset dataset) {
      return new Settings(dataset.preset(), dataset.minConsecutive(), dataset.minGroup(),
          dataset.minHeight(), dataset.scanSelection());
    }
  }

  /**
   * Result of one run of a builder on one file.
   *
   * @param statistics of the fast builder, null for other builders
   */
  record Run(@NotNull String method, long nanos, long allocatedBytes, @Nullable FeatureList flist,
             @NotNull List<EvaluatedChromatogram> chromatograms,
             @Nullable FastChromatogramBuilderStatistics statistics) {

  }

  @Test
  void syntheticGroundTruth() throws IOException {
    final Settings settings = new Settings(new MZTolerance(0.002, 10), 5, 1E3, 1E4,
        new ScanSelection(1));
    report.add("## Synthetic data with ground truth\n");
    report.add("""
        tolerance %s, min consecutive %d, min group intensity %.0f, min height %.0f
        """.formatted(settings.tolerance(), settings.minConsecutive(), settings.minGroupIntensity(),
        settings.minHeight()));
    report.add("""
        | dataset | method | time ms | alloc MB | chromatograms | detectable ions | found | completeness | split ions | holes | replaced | foreign | unmatched chromatograms |
        |---|---|---|---|---|---|---|---|---|---|---|---|---|""");

    final List<SyntheticCase> cases = List.of( //
        new SyntheticCase("standard", () -> randomIons(2000, 1200, 300, 0.5, 1.5, 0, 1)),
        new SyntheticCase("noisy", () -> randomIons(2000, 1200, 2500, 0.5, 1.5, 0, 2)),
        new SyntheticCase("high mz error", () -> randomIons(2000, 1200, 300, 1.5, 3, 0, 3)),
        new SyntheticCase("isobaric pairs", () -> randomIons(2000, 1200, 300, 0.5, 1.5, 1000, 4)));

    for (final SyntheticCase syntheticCase : cases) {
      final SyntheticLcmsData data = syntheticCase.data().get();
      final RawDataFile file = ChromatogramBenchmarkUtils.toRawDataFile(data,
          "synthetic_" + syntheticCase.name());
      final Scan[] scans = new ScanSelection(1).getMatchingScans(file);
      final List<Run> runs = new ArrayList<>();
      // warm up
      runAdap(file, settings);
      runFastTask(file, settings);
      runs.add(runAdap(file, settings));
      runs.add(runFastTask(file, settings));
      runs.add(runFastBuilder("fast builder only", data, settings, defaultBuilder(settings)));
      final FastChromatogramBuilderOptions defaults = FastChromatogramBuilderOptions.DEFAULT;
      runs.add(runFastBuilder("fast, no intensity cost", data, settings,
          builder(settings, defaults.withIntensityJumpWeight(0d))));
      runs.add(runFastBuilder("fast, merge colliding", data, settings,
          builder(settings, defaults.withSeparateCollidingTraces(false))));
      runs.add(runFastBuilder("fast, no complementary merge", data, settings,
          builder(settings, defaults.withComplementaryToleranceFactor(1d))));
      runs.add(runFastBuilder("fast, single point gap 1", data, settings,
          builder(settings, defaults.withMaxGapScans(3, 1))));
      runs.add(runFastBuilder("fast, no wide hole fill", data, settings,
          builder(settings, defaults.withHoleFillToleranceFactor(0d))));
      Assertions.assertEquals(scans.length, data.numScans());

      for (final Run run : runs) {
        final Summary summary = GroundTruthEvaluator.evaluate(data, run.chromatograms(),
            settings.minConsecutive(), settings.minGroupIntensity(), settings.minHeight());
        report.add(
            "| %s | %s | %.0f | %.0f | %d | %d | %d | %.4f | %d | %d | %d | %d | %d |".formatted(
                syntheticCase.name(), run.method(), run.nanos() / 1e6, run.allocatedBytes() / 1e6,
                summary.chromatograms(), summary.detectableIons(), summary.foundIons(),
                summary.meanCompleteness(), summary.splitIons(), summary.holes(),
                summary.replaced(), summary.foreign(), summary.unmatched()));
      }
      writeReport();
    }
  }

  @NotNull List<ChromatogramBenchmarkDataset> datasets() {
    return ChromatogramBenchmarkDatasets.selected();
  }

  /**
   * Real data with the settings of the batch wizard, with the tolerance of the wizard preset for
   * both builders.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("datasets")
  void realData(@NotNull ChromatogramBenchmarkDataset dataset) throws Exception {
    final String unavailable = ChromatogramBenchmarkDatasets.unavailableReason(dataset);
    if (unavailable != null) {
      report.add("## Real data: %s\n\nSkipped: %s\n".formatted(dataset.name(), unavailable));
      writeReport();
    }
    Assumptions.assumeTrue(unavailable == null, unavailable);
    runRealData(dataset, Integer.getInteger(PROPERTY + "repeats", 3));
  }

  private void runRealData(@NotNull ChromatogramBenchmarkDataset dataset, int repeats)
      throws Exception {
    MZmineTestUtil.clearProjectAndLibraries();
    final TaskResult imported = MZmineTestUtil.importFiles(dataset.paths(), 3600,
        dataset.importParameters());
    Assertions.assertInstanceOf(TaskResult.FINISHED.class, imported, imported.description());
    final Settings settings = Settings.of(dataset);

    report.add("## Real data: %s\n".formatted(dataset.name()));
    report.add(
        "%s, tolerance %s, median of %d runs\n".formatted(dataset.describe(), settings.tolerance(),
            repeats));
    report.add("""
        | file | MS1 scans | data points | method | time ms | alloc MB | chromatograms | chrom. data points | short gap scans | fillable holes | stolen holes | chrom. with fillable holes | split pairs | co-eluting pairs | split pairs 1-2 tol | co-eluting pairs 1-2 tol | apex found in other | overlap | unmatched failing segment height |
        |---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|""");

    final MZmineProject project = ProjectService.getProject();
    boolean warmedUp = false;
    final List<String> fastStatistics = new ArrayList<>();
    final List<String> dipRows = new ArrayList<>();
    for (final RawDataFile file : project.getCurrentRawDataFiles()) {
      final Scan[] scans = settings.scanSelection().getMatchingScans(file);
      final double[][] massListMzs = ChromatogramBenchmarkUtils.massListMzs(scans);
      final double[][] massListIntensities = ChromatogramBenchmarkUtils.massListIntensities(scans);
      final long numDataPoints = Arrays.stream(massListMzs).mapToLong(m -> m.length).sum();
      if (!warmedUp) {
        runAdap(file, settings);
        runFastTask(file, settings);
        warmedUp = true;
      }
      final Run adap = median(repeats, () -> runAdap(file, settings));
      final Run fast = median(repeats, () -> runFastTask(file, settings));

      for (final Run run : List.of(adap, fast)) {
        final Run other = run == adap ? fast : adap;
        final var metrics = ChromatogramQualityMetrics.evaluate(run.chromatograms(), massListMzs,
            settings.tolerance());
        final var cross = ChromatogramQualityMetrics.crossMatch(run.chromatograms(),
            other.chromatograms(), scans.length, settings.minConsecutive(),
            settings.minGroupIntensity(), settings.minHeight());
        report.add(
            "| %s | %d | %d | %s | %.0f | %.0f | %d | %d | %d | %d | %d | %d | %d | %d | %d | %d | %.4f | %.4f | %d |".formatted(
                file.getName(), scans.length, numDataPoints, run.method(), run.nanos() / 1e6,
                run.allocatedBytes() / 1e6, metrics.chromatograms(), metrics.dataPoints(),
                metrics.shortGapScans(), metrics.fillableHoles(), metrics.stolenHoles(),
                metrics.chromatogramsWithFillableHoles(), metrics.splitPairs(),
                metrics.coelutingPairs(), metrics.wideSplitPairs(), metrics.wideCoelutingPairs(),
                cross.matchedFraction(), cross.meanOverlap(), cross.unmatchedFailSegment()));
      }
      fastStatistics.add("- %s: %s".formatted(file.getName(), fast.statistics()));

      // the fast builder without dip bridge shows the dips that the bridge fills
      final List<BuiltChromatogram> withoutBridge = builder(settings,
          FastChromatogramBuilderOptions.DEFAULT.withDipBridgeToleranceFactor(0d)).build(
          new ScanDataAccessScans(
              EfficientDataAccess.of(file, ScanDataType.MASS_LIST, Arrays.asList(scans))), null,
          null);
      for (final Run run : List.of(adap, fast, new Run("fast, no dip bridge", 0, 0, null,
          EvaluatedChromatogram.of(Objects.requireNonNull(withoutBridge)), null))) {
        final var dips = ChromatogramQualityMetrics.dips(run.chromatograms(), massListMzs,
            massListIntensities, settings.tolerance(), DIP_FLANK_FACTOR * settings.minHeight(),
            FastChromatogramBuilderOptions.DEFAULT.dipBridgeToleranceFactor(),
            FastChromatogramBuilderOptions.DEFAULT.intensityJumpFactor());
        dipRows.add(
            "| %s | %s | %d | %d | %d |".formatted(file.getName(), run.method(), dips.dips(),
                dips.fillableDips(), dips.fillableScans()));
      }
      writeReport();
    }
    report.add(
        "\nFast builder of the median run, the rest of the task time creates the features:\n");
    report.addAll(fastStatistics);
    report.add("""
        
        Dips: up to %d scans without data point or with only data points below the weaker flank \
        / %.0f between two data points of at least %.0f x the min height. Fillable if at least \
        half of the dip scans have a mass list data point within %.0f x the tolerance of the \
        interpolated m/z and within %.0f x of the log interpolated intensity, e.g., a saturated \
        apex with a shifted m/z.
        
        | file | method | dips | fillable dips | fillable dip scans |
        |---|---|---|---|---|""".formatted(ChromatogramQualityMetrics.MAX_DIP_SCANS,
        FastChromatogramBuilderOptions.DEFAULT.intensityJumpFactor(), DIP_FLANK_FACTOR,
        FastChromatogramBuilderOptions.DEFAULT.dipBridgeToleranceFactor(),
        FastChromatogramBuilderOptions.DEFAULT.intensityJumpFactor()));
    report.addAll(dipRows);

    report.add("""
        
        Resolved with the local minimum resolver (chromatographic threshold 0.9, search range 0.04 min, \
        top/edge 2, min height and min scans as above). Duplicates and matches use the m/z tolerance \
        and 0.03 min.
        
        Unmatched features are explained by the chromatograms of the other builder: the other lacks \
        the signal, has the peak with more holes, or has the peak and the resolver decided \
        differently.
        
        | file | method | features | duplicate pairs | found in other | unmatched | other lacks signal | other more holes | other resolved apart | median height unmatched | median height all |
        |---|---|---|---|---|---|---|---|---|---|---|""");
    final List<String> joinRows = new ArrayList<>();
    for (final RawDataFile file : project.getCurrentRawDataFiles()) {
      joinRows.add(compareResolved(file, settings));
      writeReport();
    }
    report.add("""
        
        Complementary joins of the fast builder (traces 1 to 2 times the tolerance from the seed \
        of their channel) whose trace apex is more than the gap allowance (%d scans) outside the \
        seed trace, i.e., possibly a separate peak in a neighboring chromatogram. A peak is \
        resolved in a list if a feature is within the tolerance of the trace center and %.2f min \
        of the trace apex.
        
        | file | complementary joins | apex away | >= min height | resolved in both | only adap | only fast | neither |
        |---|---|---|---|---|---|---|---|""".formatted(maxJoinScanDistance(), RT_TOLERANCE));
    report.addAll(joinRows);
    report.add("");
    writeReport();
  }

  /**
   * @return the gap allowance of the consolidation, see {@link ChannelConsolidation}
   */
  private static int maxJoinScanDistance() {
    return FastChromatogramBuilderOptions.DEFAULT.maxGapScans() + 1;
  }

  @NotNull
  private static Run median(int repeats, @NotNull Supplier<Run> runner) {
    final List<Run> runs = new ArrayList<>();
    for (int i = 0; i < repeats; i++) {
      runs.add(runner.get());
    }
    runs.sort((a, b) -> Long.compare(a.nanos(), b.nanos()));
    return runs.get(runs.size() / 2);
  }

  @NotNull
  private static FastChromatogramBuilder defaultBuilder(@NotNull Settings settings) {
    return new FastChromatogramBuilder(settings.tolerance(), settings.minConsecutive(),
        settings.minGroupIntensity(), settings.minHeight());
  }

  @NotNull
  private static FastChromatogramBuilder builder(@NotNull Settings settings,
      @NotNull FastChromatogramBuilderOptions options) {
    return new FastChromatogramBuilder(settings.tolerance(), settings.minConsecutive(),
        settings.minGroupIntensity(), settings.minHeight(), options);
  }

  @NotNull
  private static Run runAdap(@NotNull RawDataFile file, @NotNull Settings settings) {
    return runAdap(file, settings, false);
  }

  @NotNull
  private static Run runAdap(@NotNull RawDataFile file, @NotNull Settings settings, boolean keep) {
    final ADAPChromatogramBuilderParameters parameters = ADAPChromatogramBuilderParameters.create(
        new RawDataFilesSelection(RawDataFilesSelectionType.ALL_FILES), settings.scanSelection(),
        settings.minConsecutive(), settings.tolerance(), "adap", settings.minGroupIntensity(),
        settings.minHeight(), false);
    final ModularADAPChromatogramBuilderTask task = ModularADAPChromatogramBuilderTask.forChromatography(
        ProjectService.getProject(), file, parameters, null, Instant.now(),
        ModularADAPChromatogramBuilderModule.class);
    return runTask("adap", task, file, file.getName() + " adap", settings, keep);
  }

  @NotNull
  private static Run runFastTask(@NotNull RawDataFile file, @NotNull Settings settings) {
    return runFastTask(file, settings, false);
  }

  @NotNull
  private static Run runFastTask(@NotNull RawDataFile file, @NotNull Settings settings,
      boolean keep) {
    final ParameterSet parameters = FastChromatogramBuilderParameters.create(
        new RawDataFilesSelection(RawDataFilesSelectionType.ALL_FILES), settings.scanSelection(),
        settings.minConsecutive(), settings.tolerance(), "fast", settings.minGroupIntensity(),
        settings.minHeight(), false);
    final FastChromatogramBuilderTask task = new FastChromatogramBuilderTask(
        ProjectService.getProject(), new RawDataFile[]{file}, parameters, null, Instant.now(),
        FastChromatogramBuilderModule.class);
    return runTask("fast", task, file, file.getName() + " fast", settings, keep);
  }

  /**
   * Runs the task in this thread to measure the time and the allocated bytes, then removes the
   * created feature list from the project.
   */
  @NotNull
  private static Run runTask(@NotNull String method, @NotNull AbstractTask task,
      @NotNull RawDataFile file, @NotNull String flistName, @NotNull Settings settings,
      boolean keep) {
    final long allocatedBefore = ChromatogramBenchmarkUtils.allocatedBytes();
    final long start = System.nanoTime();
    task.run();
    final long nanos = System.nanoTime() - start;
    final long allocated = ChromatogramBenchmarkUtils.allocatedBytes() - allocatedBefore;
    Assertions.assertEquals(TaskStatus.FINISHED, task.getStatus(), task.getErrorMessage());

    final MZmineProject project = ProjectService.getProject();
    final FeatureList flist = findFeatureList(project, flistName);
    Assertions.assertNotNull(flist, "No feature list " + flistName);
    final Scan[] scans = settings.scanSelection().getMatchingScans(file);
    final List<EvaluatedChromatogram> chromatograms = ChromatogramBenchmarkUtils.toChromatograms(
        flist, scans);
    if (!keep) {
      project.removeFeatureList(flist);
    }
    final FastChromatogramBuilderStatistics statistics =
        task instanceof FastChromatogramBuilderTask fastTask ? fastTask.getStatistics().getFirst()
            : null;
    return new Run(method, nanos, allocated, keep ? flist : null, chromatograms, statistics);
  }

  /**
   * Resolves both chromatogram lists with the local minimum resolver and compares the features.
   *
   * @return the row of the complementary join table of this file
   */
  @NotNull
  private String compareResolved(@NotNull RawDataFile file, @NotNull Settings settings)
      throws InterruptedException {
    final Run adap = runAdap(file, settings, true);
    final Run fast = runFastTask(file, settings, true);
    final String suffix = "resolved";
    final MinimumSearchFeatureResolverParameters parameters = MinimumSearchFeatureResolverParameters.create(
        new FeatureListsSelection((ModularFeatureList) adap.flist(),
            (ModularFeatureList) fast.flist()), suffix, OriginalFeatureListOption.KEEP, false,
        GroupMS2SubParameters.createDefault(), ResolvingDimension.RETENTION_TIME,
        CHROMATOGRAPHIC_THRESHOLD, 0.04, 0d,
        settings.minHeight(), 2d, Range.closed(0d, 1.2d), settings.minConsecutive());
    final TaskResult resolved = MZmineTestUtil.callModuleWithTimeout(1200,
        MinimumSearchFeatureResolverModule.class, parameters);
    Assertions.assertInstanceOf(TaskResult.FINISHED.class, resolved, resolved.description());

    final MZmineProject project = ProjectService.getProject();
    final FeatureList adapResolved = findFeatureList(project,
        adap.flist().getName() + " " + suffix);
    final FeatureList fastResolved = findFeatureList(project,
        fast.flist().getName() + " " + suffix);
    Assertions.assertNotNull(adapResolved);
    Assertions.assertNotNull(fastResolved);
    final Scan[] scans = settings.scanSelection().getMatchingScans(file);
    for (final FeatureList flist : List.of(adapResolved, fastResolved)) {
      final boolean isAdap = flist == adapResolved;
      final FeatureList other = isAdap ? fastResolved : adapResolved;
      final var metrics = ResolvedFeatureMetrics.evaluate(flist, other, settings.tolerance(),
          RT_TOLERANCE);
      final var differences = ResolvedFeatureDifferences.explain(flist, other,
          isAdap ? fast.chromatograms() : adap.chromatograms(), scans, settings.tolerance(),
          RT_TOLERANCE);
      report.add(
          "| %s | %s | %d | %d | %.4f | %d | %d | %d | %d | %.3g | %.3g |".formatted(file.getName(),
              isAdap ? "adap" : "fast", metrics.features(), metrics.duplicatePairs(),
              metrics.matchedFraction(), differences.unmatched(), differences.otherLacksSignal(),
              differences.otherHasMoreHoles(), differences.otherResolvedApart(),
              differences.medianHeight(), differences.medianHeightAll()));
      logger.info(() -> "Unmatched %s features of %s:\n%s".formatted(isAdap ? "adap" : "fast",
          file.getName(), String.join("\n", differences.examples())));
    }

    // the fast task uses the default options
    final List<ComplementaryJoinMetrics.Join> joins = ComplementaryJoinMetrics.findJoins(
        new ScanDataAccessScans(
            EfficientDataAccess.of(file, ScanDataType.MASS_LIST, Arrays.asList(scans))),
        settings.tolerance(), settings.minConsecutive(), settings.minHeight(),
        FastChromatogramBuilderOptions.DEFAULT);
    final var joinResult = ComplementaryJoinMetrics.classify(joins, scans, adapResolved,
        fastResolved, Objects.requireNonNull(fast.flist()), fast.chromatograms(),
        settings.tolerance(), RT_TOLERANCE, settings.minHeight(), maxJoinScanDistance(),
        CHROMATOGRAPHIC_THRESHOLD);
    logger.info(() -> "Complementary joins with the apex away from the seed in %s:\n%s".formatted(
        file.getName(), String.join("\n", joinResult.examples())));
    project.removeFeatureList(adap.flist(), fast.flist(), adapResolved, fastResolved);
    return "| %s | %d | %d | %d | %d | %d | %d | %d |".formatted(file.getName(), joinResult.joins(),
        joinResult.apexAway(), joinResult.aboveMinHeight(), joinResult.resolvedInBoth(),
        joinResult.onlyAdap(), joinResult.onlyFast(), joinResult.neither());
  }

  @NotNull
  private static Run runFastBuilder(@NotNull String method, @NotNull SyntheticLcmsData data,
      @NotNull Settings settings, @NotNull FastChromatogramBuilder builder) {
    // warm up
    builder.build(data.scans(), null, null);
    final long allocatedBefore = ChromatogramBenchmarkUtils.allocatedBytes();
    final long start = System.nanoTime();
    final List<BuiltChromatogram> chromatograms = builder.build(data.scans(), null, null);
    final long nanos = System.nanoTime() - start;
    final long allocated = ChromatogramBenchmarkUtils.allocatedBytes() - allocatedBefore;
    Assertions.assertNotNull(chromatograms);
    logger.info(() -> method + ": " + builder.getStatistics());
    return new Run(method, nanos, allocated, null, EvaluatedChromatogram.of(chromatograms),
        builder.getStatistics());
  }

  @Nullable
  private static FeatureList findFeatureList(@NotNull MZmineProject project, @NotNull String name) {
    for (final FeatureList flist : project.getCurrentFeatureLists()) {
      if (flist.getName().equals(name) && flist instanceof ModularFeatureList) {
        return flist;
      }
    }
    return null;
  }

  /**
   * Random ions, optionally with isobaric partners within the tolerance. Half of the partners
   * co-elute, half elute at another retention time.
   */
  @NotNull
  static SyntheticLcmsData randomIons(int numIons, int numScans, int noisePerScan,
      double minErrorPpm, double maxErrorPpm, int isobaricPartners, long seed) {
    final Random random = new Random(seed);
    final List<Ion> ions = new ArrayList<>();
    for (int i = 0; i < numIons; i++) {
      ions.add(
          randomIon(random, 100 + random.nextDouble() * 900, numScans, minErrorPpm, maxErrorPpm));
    }
    for (int i = 0; i < isobaricPartners; i++) {
      final Ion ion = ions.get(i);
      final double ppm = (3 + random.nextDouble() * 5) * (random.nextBoolean() ? 1 : -1);
      final double mz = ion.mz() * (1 + ppm * 1E-6);
      final Ion partner = randomIon(random, mz, numScans, minErrorPpm, maxErrorPpm);
      ions.add(i % 2 == 0 ? new Ion(mz, ion.apexScan() + random.nextGaussian(), ion.sigmaScans(),
          partner.height(), partner.mzErrorPpm(), 0) : partner);
    }
    return SyntheticLcmsData.builder(numScans).ions(ions).noise(noisePerScan, 100, 1000, 1E2, 3E3)
        .detectionThreshold(5E2).maxErrorFactor(3).seed(seed).build();
  }

  @NotNull
  private static Ion randomIon(@NotNull Random random, double mz, int numScans, double minErrorPpm,
      double maxErrorPpm) {
    final double apex = 10 + random.nextDouble() * (numScans - 20);
    final double sigma = 2 + random.nextDouble() * 4;
    final double height = Math.exp(Math.log(5E3) + random.nextDouble() * Math.log(1E3));
    final double error = minErrorPpm + random.nextDouble() * (maxErrorPpm - minErrorPpm);
    return new Ion(mz, apex, sigma, height, error, 0);
  }

  private void writeReport() throws IOException {
    final Path out = Path.of(
        System.getProperty(PROPERTY + "out", "build/reports/chromatogram-builder-benchmark"));
    Files.createDirectories(out);
    final String text = String.join("\n", report) + "\n";
    Files.writeString(out.resolve("benchmark.md"), text);
    logger.info("Chromatogram builder benchmark\n" + text);
  }

  /**
   * A named generator of synthetic data.
   */
  record SyntheticCase(@NotNull String name, @NotNull Supplier<SyntheticLcmsData> data) {

  }
}
