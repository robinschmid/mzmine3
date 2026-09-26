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

import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.data_access.EfficientDataAccess;
import io.github.mzmine.datamodel.data_access.EfficientDataAccess.ScanDataType;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilesSelection;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilesSelectionType;
import io.github.mzmine.project.ProjectService;
import io.github.mzmine.taskcontrol.TaskStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import testutils.MZmineTestUtil;
import testutils.TaskResult;

/**
 * Times the {@link FastChromatogramBuilder} on the first file of each data set of
 * {@link ChromatogramBenchmarkDatasets} with the preset tolerance and optionally records a JFR
 * profile. Run with
 * <pre>
 * gradlew :mzmine-community:benchmark --tests "*ChromatogramBuilderProfile*"
 *     -Dmzmine.test.chrombench.only=GC-EI-QTOF -Dmzmine.test.chrombench.profile.jfr=true
 * </pre>
 * Options as system properties with the prefix {@value ChromatogramBenchmarkDatasets#PROPERTY}:
 * {@code profile.runs=<timed runs>}, {@code profile.jfr=true} records the timed runs,
 * {@code profile.task=true} times the whole file task including the feature creation instead of the
 * builder. The times and JFR files go to {@code build/reports/chromatogram-builder-profile}, e.g.,
 * {@code jfr view hot-methods <file>}.
 */
@Tag("benchmark")
@TestInstance(Lifecycle.PER_CLASS)
class ChromatogramBuilderProfile {

  private static final Logger logger = Logger.getLogger(ChromatogramBuilderProfile.class.getName());
  private static final Path OUT = Path.of("build/reports/chromatogram-builder-profile");

  @BeforeAll
  void init() {
    MZmineTestUtil.startMzmineCore();
  }

  @NotNull List<ChromatogramBenchmarkDataset> datasets() {
    return ChromatogramBenchmarkDatasets.selected();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("datasets")
  void profile(@NotNull ChromatogramBenchmarkDataset dataset) throws Exception {
    final String unavailable = ChromatogramBenchmarkDatasets.unavailableReason(dataset);
    Assumptions.assumeTrue(unavailable == null, unavailable);
    MZmineTestUtil.clearProjectAndLibraries();
    final TaskResult imported = MZmineTestUtil.importFiles(dataset.paths().subList(0, 1), 3600,
        dataset.importParameters());
    Assertions.assertInstanceOf(TaskResult.FINISHED.class, imported, imported.description());
    final RawDataFile file = ProjectService.getProject().getCurrentRawDataFiles().getFirst();
    final Scan[] scans = dataset.scanSelection().getMatchingScans(file);

    final int runs = Integer.getInteger(PROPERTY + "profile.runs", 7);
    final boolean wholeTask = Boolean.getBoolean(PROPERTY + "profile.task");
    // warm up
    run(dataset, file, scans, wholeTask);
    final Recording recording = Boolean.getBoolean(PROPERTY + "profile.jfr") ? new Recording(
        Configuration.getConfiguration("profile")) : null;
    if (recording != null) {
      recording.start();
    }
    final long[] nanos = new long[runs];
    FastChromatogramBuilderStatistics statistics = null;
    for (int r = 0; r < runs; r++) {
      final long start = System.nanoTime();
      statistics = run(dataset, file, scans, wholeTask);
      nanos[r] = System.nanoTime() - start;
    }
    Files.createDirectories(OUT);
    if (recording != null) {
      recording.stop();
      recording.dump(OUT.resolve(dataset.name().replaceAll("[^A-Za-z0-9]+", "_") + ".jfr"));
      recording.close();
    }
    Arrays.sort(nanos);
    final String line = "%s, %s: median %.0f ms, min %.0f ms, %s".formatted(dataset.name(),
        wholeTask ? "task" : "builder", nanos[runs / 2] / 1e6, nanos[0] / 1e6, statistics);
    logger.info(line);
    Files.writeString(OUT.resolve("times.md"), "- " + line + "\n", StandardOpenOption.CREATE,
        StandardOpenOption.APPEND);
  }

  @NotNull
  private static FastChromatogramBuilderStatistics run(
      @NotNull ChromatogramBenchmarkDataset dataset, @NotNull RawDataFile file,
      @NotNull Scan[] scans, boolean wholeTask) {
    if (!wholeTask) {
      final FastChromatogramBuilder builder = new FastChromatogramBuilder(dataset.preset(),
          dataset.minConsecutive(), dataset.minGroup(), dataset.minHeight());
      Assertions.assertNotNull(builder.build(new ScanDataAccessScans(
          EfficientDataAccess.of(file, ScanDataType.MASS_LIST, Arrays.asList(scans))), null, null));
      return builder.getStatistics();
    }
    final ParameterSet parameters = FastChromatogramBuilderParameters.create(
        new RawDataFilesSelection(RawDataFilesSelectionType.ALL_FILES), dataset.scanSelection(),
        dataset.minConsecutive(), dataset.preset(), "profile", dataset.minGroup(),
        dataset.minHeight(), false);
    final FastChromatogramBuilderTask task = new FastChromatogramBuilderTask(
        ProjectService.getProject(), new RawDataFile[]{file}, parameters, null, Instant.now(),
        FastChromatogramBuilderModule.class);
    task.run();
    Assertions.assertEquals(TaskStatus.FINISHED, task.getStatus(), task.getErrorMessage());
    ProjectService.getProject().removeFeatureList(task.getFeatureLists().getFirst());
    return task.getStatistics().getFirst();
  }
}
