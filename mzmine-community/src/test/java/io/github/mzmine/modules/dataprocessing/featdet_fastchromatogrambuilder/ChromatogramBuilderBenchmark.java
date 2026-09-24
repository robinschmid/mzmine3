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

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
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
import io.github.mzmine.modules.io.import_rawdata_all.AdvancedSpectraImportParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.MassDetectorWizardOptions;
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
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
import testutils.MZmineTestUtil;
import testutils.TaskResult;

/**
 * Compares the ADAP chromatogram builder with the {@link FastChromatogramBuilder} in speed and
 * quality. Run with
 * <pre>
 * gradlew :mzmine-community:benchmark --tests "*ChromatogramBuilderBenchmark*"
 * </pre>
 * Options as system properties: {@code -Dmzmine.test.chrombench.data=<folder with mzML>},
 * {@code .files=<max files>}, {@code .repeats=<timing repeats>}, {@code .out=<report folder>}. The
 * real data test is skipped if the data folder does not exist. The report is written as markdown.
 */
@Tag("benchmark")
@TestInstance(Lifecycle.PER_CLASS)
class ChromatogramBuilderBenchmark {

  private static final Logger logger = Logger.getLogger(
      ChromatogramBuilderBenchmark.class.getName());

  private static final String DEFAULT_DATA = "D:\\OneDrive - mzio GmbH\\Example data - Documents\\Thermo\\20 years mzmine";
  private static final String PROPERTY = "mzmine.test.chrombench.";

  private final List<String> report = new ArrayList<>();

  @BeforeAll
  void init() {
    MZmineTestUtil.startMzmineCore();
  }

  /**
   * Builder settings of one benchmark.
   */
  record Settings(@NotNull MZTolerance tolerance, int minConsecutive, double minGroupIntensity,
                  double minHeight) {

  }

  /**
   * Result of one run of a builder on one file.
   */
  record Run(@NotNull String method, long nanos, long allocatedBytes, @Nullable FeatureList flist,
             @NotNull List<EvaluatedChromatogram> chromatograms) {

  }

  @Test
  void syntheticGroundTruth() throws IOException {
    final Settings settings = new Settings(new MZTolerance(0.002, 10), 5, 1E3, 1E4);
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

  @Test
  void realData() throws Exception {
    final File folder = new File(System.getProperty(PROPERTY + "data", DEFAULT_DATA));
    Assumptions.assumeTrue(folder.isDirectory(), "No data folder " + folder);
    final int maxFiles = Integer.getInteger(PROPERTY + "files", 4);
    final int repeats = Integer.getInteger(PROPERTY + "repeats", 3);
    final File[] files = folder.listFiles((_, name) -> name.toLowerCase().endsWith(".mzml"));
    Assumptions.assumeTrue(files != null && files.length > 0, "No mzML files in " + folder);
    Arrays.sort(files);
    final List<String> paths = Arrays.stream(files).limit(maxFiles).map(File::getAbsolutePath)
        .toList();

    // settings of the workshop batch for Orbitrap data and a more sensitive setting with more data
    final MZTolerance tolerance = new MZTolerance(0.002, 10);
    runRealData(folder.getName(), paths, repeats, "workshop",
        MassDetectorWizardOptions.FACTOR_OF_LOWEST_SIGNAL, 5d,
        new Settings(tolerance, 6, 1E5, 5E5));
    runRealData(folder.getName(), paths, repeats, "sensitive",
        MassDetectorWizardOptions.FACTOR_OF_LOWEST_SIGNAL, 2d,
        new Settings(tolerance, 4, 1E4, 5E4));
  }

  /**
   * GC-EI-TOF data of the integration tests with the settings of its batch, low resolution with
   * many data points per scan.
   */
  @Test
  void gcTofData() throws Exception {
    final int repeats = Integer.getInteger(PROPERTY + "repeats", 3);
    runRealData("GC-EI-TOF",
        List.of("rawdatafiles/integration_tests/gc_tof_ms/019_KR8_20220715.mzML"), repeats,
        "gc_tof batch", MassDetectorWizardOptions.ABSOLUTE_NOISE_LEVEL, 500d,
        new Settings(new MZTolerance(0.005, 20), 4, 1E3, 1E3));
  }

  private void runRealData(@NotNull String dataName, @NotNull List<String> paths, int repeats,
      @NotNull String profile, @NotNull MassDetectorWizardOptions detector, double noise,
      @NotNull Settings settings) throws Exception {
    MZmineTestUtil.clearProjectAndLibraries();
    final AdvancedSpectraImportParameters advanced = AdvancedSpectraImportParameters.create(
        detector, noise,
        detector == MassDetectorWizardOptions.FACTOR_OF_LOWEST_SIGNAL ? 2.5d : noise, null,
        ScanSelection.ALL_SCANS, false);
    final TaskResult imported = MZmineTestUtil.importFiles(paths, 3600, advanced);
    Assertions.assertInstanceOf(TaskResult.FINISHED.class, imported, imported.description());

    report.add("## Real data: %s, %s\n".formatted(dataName, profile));
    report.add("""
        %d files, MS1 mass detection %s %.1f, tolerance %s, min consecutive %d, \
        min group intensity %.0f, min height %.0f, median of %d runs
        """.formatted(paths.size(), detector, noise, settings.tolerance(),
        settings.minConsecutive(), settings.minGroupIntensity(), settings.minHeight(), repeats));
    report.add("""
        | file | MS1 scans | data points | method | time ms | alloc MB | chromatograms | chrom. data points | short gap scans | fillable holes | stolen holes | chrom. with fillable holes | split pairs | co-eluting pairs | split pairs 1-2 tol | co-eluting pairs 1-2 tol | apex found in other | overlap | unmatched failing segment height |
        |---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|""");

    final MZmineProject project = ProjectService.getProject();
    boolean warmedUp = false;
    for (final RawDataFile file : project.getCurrentRawDataFiles()) {
      final Scan[] scans = new ScanSelection(1).getMatchingScans(file);
      final double[][] massListMzs = ChromatogramBenchmarkUtils.massListMzs(scans);
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
      writeReport();
    }

    report.add("""
        
        Resolved with the local minimum resolver (chromatographic threshold 0.9, search range 0.04 min, \
        top/edge 2, min height and min scans as above). Duplicates and matches use the m/z tolerance \
        and 0.03 min.
        
        Unmatched features are explained by the chromatograms of the other builder: the other lacks \
        the signal, has the peak with more holes, or has the peak and the resolver decided \
        differently.
        
        | file | method | features | duplicate pairs | found in other | unmatched | other lacks signal | other more holes | other resolved apart | median height unmatched | median height all |
        |---|---|---|---|---|---|---|---|---|---|---|""");
    for (final RawDataFile file : project.getCurrentRawDataFiles()) {
      compareResolved(file, settings);
    }
    writeReport();
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
        new RawDataFilesSelection(RawDataFilesSelectionType.ALL_FILES), new ScanSelection(1),
        settings.minConsecutive(), settings.tolerance(), "adap", settings.minGroupIntensity(),
        settings.minHeight(), false);
    final ModularADAPChromatogramBuilderTask task = ModularADAPChromatogramBuilderTask.forChromatography(
        ProjectService.getProject(), file, parameters, null, Instant.now(),
        ModularADAPChromatogramBuilderModule.class);
    return runTask("adap", task, file, file.getName() + " adap", keep);
  }

  @NotNull
  private static Run runFastTask(@NotNull RawDataFile file, @NotNull Settings settings) {
    return runFastTask(file, settings, false);
  }

  @NotNull
  private static Run runFastTask(@NotNull RawDataFile file, @NotNull Settings settings,
      boolean keep) {
    final ParameterSet parameters = FastChromatogramBuilderParameters.create(
        new RawDataFilesSelection(RawDataFilesSelectionType.ALL_FILES), new ScanSelection(1),
        settings.minConsecutive(), settings.tolerance(), "fast", settings.minGroupIntensity(),
        settings.minHeight(), false);
    final FastChromatogramBuilderTask task = new FastChromatogramBuilderTask(
        ProjectService.getProject(), file, parameters, null, Instant.now(),
        FastChromatogramBuilderModule.class);
    return runTask("fast", task, file, file.getName() + " fast", keep);
  }

  /**
   * Runs the task in this thread to measure the time and the allocated bytes, then removes the
   * created feature list from the project.
   */
  @NotNull
  private static Run runTask(@NotNull String method, @NotNull AbstractTask task,
      @NotNull RawDataFile file, @NotNull String flistName, boolean keep) {
    final long allocatedBefore = ChromatogramBenchmarkUtils.allocatedBytes();
    final long start = System.nanoTime();
    task.run();
    final long nanos = System.nanoTime() - start;
    final long allocated = ChromatogramBenchmarkUtils.allocatedBytes() - allocatedBefore;
    Assertions.assertEquals(TaskStatus.FINISHED, task.getStatus(), task.getErrorMessage());

    final MZmineProject project = ProjectService.getProject();
    final FeatureList flist = findFeatureList(project, flistName);
    Assertions.assertNotNull(flist, "No feature list " + flistName);
    final Scan[] scans = new ScanSelection(1).getMatchingScans(file);
    final List<EvaluatedChromatogram> chromatograms = ChromatogramBenchmarkUtils.toChromatograms(
        flist, scans);
    if (!keep) {
      project.removeFeatureList(flist);
    }
    return new Run(method, nanos, allocated, keep ? flist : null, chromatograms);
  }

  /**
   * Resolves both chromatogram lists with the local minimum resolver and compares the features.
   */
  private void compareResolved(@NotNull RawDataFile file, @NotNull Settings settings)
      throws InterruptedException {
    final Run adap = runAdap(file, settings, true);
    final Run fast = runFastTask(file, settings, true);
    final String suffix = "resolved";
    final MinimumSearchFeatureResolverParameters parameters = MinimumSearchFeatureResolverParameters.create(
        new FeatureListsSelection((ModularFeatureList) adap.flist(),
            (ModularFeatureList) fast.flist()), suffix, OriginalFeatureListOption.KEEP, false,
        GroupMS2SubParameters.createDefault(), ResolvingDimension.RETENTION_TIME, 0.9, 0.04, 0d,
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
    // decision: features within the tolerance and 0.03 min are the same feature, ~2 scans
    final float rtTolerance = 0.03f;
    final Scan[] scans = new ScanSelection(1).getMatchingScans(file);
    for (final FeatureList flist : List.of(adapResolved, fastResolved)) {
      final boolean isAdap = flist == adapResolved;
      final FeatureList other = isAdap ? fastResolved : adapResolved;
      final var metrics = ResolvedFeatureMetrics.evaluate(flist, other, settings.tolerance(),
          rtTolerance);
      final var differences = ResolvedFeatureDifferences.explain(flist, other,
          isAdap ? fast.chromatograms() : adap.chromatograms(), scans, settings.tolerance(),
          rtTolerance);
      report.add(
          "| %s | %s | %d | %d | %.4f | %d | %d | %d | %d | %.3g | %.3g |".formatted(file.getName(),
              isAdap ? "adap" : "fast", metrics.features(), metrics.duplicatePairs(),
              metrics.matchedFraction(), differences.unmatched(), differences.otherLacksSignal(),
              differences.otherHasMoreHoles(), differences.otherResolvedApart(),
              differences.medianHeight(), differences.medianHeightAll()));
      logger.info(() -> "Unmatched %s features of %s:\n%s".formatted(isAdap ? "adap" : "fast",
          file.getName(), String.join("\n", differences.examples())));
    }
    project.removeFeatureList(adap.flist(), fast.flist(), adapResolved, fastResolved);
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
    return new Run(method, nanos, allocated, null, EvaluatedChromatogram.of(chromatograms));
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
