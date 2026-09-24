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

import io.github.mzmine.datamodel.FeatureStatus;
import io.github.mzmine.datamodel.IMSRawDataFile;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.SimpleRange.SimpleDoubleRange;
import io.github.mzmine.datamodel.data_access.EfficientDataAccess;
import io.github.mzmine.datamodel.data_access.EfficientDataAccess.ScanDataType;
import io.github.mzmine.datamodel.data_access.ScanDataAccess;
import io.github.mzmine.datamodel.featuredata.impl.SimpleIonTimeSeries;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.datamodel.features.SimpleFeatureListAppliedMethod;
import io.github.mzmine.datamodel.features.types.FeatureShapeType;
import io.github.mzmine.gui.DesktopService;
import io.github.mzmine.modules.MZmineModule;
import io.github.mzmine.modules.dataprocessing.norm_remove_scanrtcal.RemoveScanRtCorrectionModule;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.DataTypeUtils;
import io.github.mzmine.util.FeatureListUtils;
import io.github.mzmine.util.MemoryMapStorage;
import io.github.mzmine.util.exceptions.MissingMassListException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Builds the chromatograms of one raw data file with the {@link FastChromatogramBuilder}.
 */
public class FastChromatogramBuilderTask extends AbstractTask {

  private static final Logger logger = Logger.getLogger(
      FastChromatogramBuilderTask.class.getName());

  private final @NotNull MZmineProject project;
  private final @NotNull RawDataFile dataFile;
  private final @NotNull ScanSelection scanSelection;
  private final @NotNull String suffix;
  private final @NotNull MZTolerance mzTolerance;
  private final int minimumConsecutiveScans;
  private final double minGroupIntensity;
  private final double minHighestPoint;
  private final boolean clearRtCorrection;
  private final @NotNull ParameterSet parameters;
  private final @NotNull Class<? extends MZmineModule> callingModule;
  private double progress = 0d;
  private @Nullable ModularFeatureList newFeatureList;
  private @Nullable FastChromatogramBuilderStatistics statistics;

  public FastChromatogramBuilderTask(@NotNull MZmineProject project, @NotNull RawDataFile dataFile,
      @NotNull ParameterSet parameters, @Nullable MemoryMapStorage storage,
      @NotNull Instant moduleCallDate, @NotNull Class<? extends MZmineModule> callingModule) {
    super(storage, moduleCallDate);
    this.project = project;
    this.dataFile = dataFile;
    this.parameters = parameters;
    this.callingModule = callingModule;
    scanSelection = parameters.getValue(FastChromatogramBuilderParameters.scanSelection);
    mzTolerance = parameters.getValue(FastChromatogramBuilderParameters.mzTolerance);
    minimumConsecutiveScans = parameters.getValue(
        FastChromatogramBuilderParameters.minimumConsecutiveScans);
    minGroupIntensity = parameters.getValue(FastChromatogramBuilderParameters.minGroupIntensity);
    minHighestPoint = parameters.getValue(FastChromatogramBuilderParameters.minHighestPoint);
    suffix = parameters.getValue(FastChromatogramBuilderParameters.suffix);
    clearRtCorrection = parameters.getValue(FastChromatogramBuilderParameters.clearRtCorrection);
  }

  @Override
  public String getTaskDescription() {
    return "Detecting chromatograms in " + dataFile;
  }

  @Override
  public double getFinishedPercentage() {
    return progress;
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);
    logger.info(() -> "Started fast chromatogram builder on " + dataFile);

    if (clearRtCorrection) {
      RemoveScanRtCorrectionModule.clearRtCorrection(new RawDataFile[]{dataFile},
          getModuleCallDate(), "Resetting RT correction during chromatogram builder.");
    }

    final Scan[] scans = scanSelection.getMatchingScans(dataFile);
    if (!checkScans(scans)) {
      return;
    }

    final ScanDataAccess access = EfficientDataAccess.of(dataFile, ScanDataType.MASS_LIST,
        Arrays.asList(scans));
    final FastChromatogramBuilder builder = new FastChromatogramBuilder(mzTolerance,
        minimumConsecutiveScans, minGroupIntensity, minHighestPoint);
    final List<BuiltChromatogram> chromatograms;
    try {
      chromatograms = builder.build(new ScanDataAccessScans(access), this::isCanceled,
          p -> progress = 0.8 * p);
    } catch (MissingMassListException e) {
      // mass lists are checked before, only fails if they are removed concurrently
      error(e.getMessage(), e);
      return;
    }
    if (chromatograms == null || isCanceled()) {
      return;
    }
    statistics = builder.getStatistics();
    logger.fine(() -> "Fast chromatogram builder on %s: %s".formatted(dataFile, statistics));

    final ModularFeatureList flist = new ModularFeatureList(dataFile + " " + suffix,
        getMemoryMapStorage(), dataFile);
    // ensure that the default columns are available
    DataTypeUtils.addDefaultChromatographicTypeColumns(flist);

    final Ms2ScanIndex ms2Index = new Ms2ScanIndex(dataFile);
    int id = 1;
    for (final BuiltChromatogram chromatogram : chromatograms) {
      if (isCanceled()) {
        return;
      }
      final ModularFeature feature = createFeature(flist, chromatogram, scans, ms2Index);
      final ModularFeatureListRow row = new ModularFeatureListRow(flist, id++, feature);
      flist.addRow(row);
      // activate shape for this row
      row.set(FeatureShapeType.class, true);
      progress = 0.8 + 0.2 * id / chromatograms.size();
    }

    // sort and reset IDs here to have the same sorting for every feature list
    FeatureListUtils.sortByDefault(flist, true);
    flist.setSelectedScans(dataFile, Arrays.asList(scans));
    dataFile.getAppliedMethods().forEach(m -> flist.getAppliedMethods().add(m));
    flist.getAppliedMethods()
        .add(new SimpleFeatureListAppliedMethod(callingModule, parameters, getModuleCallDate()));
    project.addFeatureList(flist);
    newFeatureList = flist;

    progress = 1d;
    setStatus(TaskStatus.FINISHED);
    logger.info(
        () -> "Finished fast chromatogram builder on %s with %d chromatograms".formatted(dataFile,
            chromatograms.size()));
  }

  /**
   * Same checks and messages as the ADAP chromatogram builder.
   *
   * @return false if the scans cannot be processed, sets the error
   */
  private boolean checkScans(@NotNull Scan[] scans) {
    if (scans.length == 0) {
      error("""
          There are no scans in file "%s" satisfying scan filters. Consider updating filters
          with "Show" on the "Scan filters" parameter. Filter was: %s""".formatted(
          dataFile.getName(), scanSelection.toShortDescription()));
      return false;
    }

    int emptyScans = 0;
    double previousRt = Double.NEGATIVE_INFINITY;
    for (final Scan scan : scans) {
      if (scan.getMassList() == null) {
        error(missingMassListMessage(scan));
        return false;
      }
      if (scan.isEmptyScan()) {
        emptyScans++;
        continue;
      }
      if (scan.getRetentionTime() < previousRt) {
        error("Retention time of scan #" + scan.getScanNumber() + " in file " + dataFile.getName()
            + " is smaller then the retention time of the previous scan."
            + " Please make sure you only use scans with increasing retention times."
            + " You can restrict the scan numbers in the parameters, or you can use the Crop"
            + " filter module");
        return false;
      }
      previousRt = scan.getRetentionTime();
    }
    if (emptyScans > 0) {
      final int numEmpty = emptyScans;
      logger.info(() -> numEmpty + " scans were found to be empty.");
    }

    final int level = scans[0].getMSLevel();
    final PolarityType polarity = scans[0].getPolarity();
    for (int i = 1; i < scans.length; i++) {
      if (level != scans[i].getMSLevel()) {
        DesktopService.getDesktop().displayMessage(null,
            "mzmine thinks that you are running the chromatogram builder on both MS1- and "
                + "MS2-scans. " + "This will likely produce wrong results. "
                + "Please, set the scan filter parameter to a specific MS level");
        break;
      }
      if (polarity != scans[i].getPolarity()) {
        DesktopService.getDesktop().displayMessage("""
            mzmine thinks you are processing data of multiple polarities (%s and %s)
            at the same time. This will likely lead to wrong results.
            Set the polarity filter in the wizard or the chromatogram builder step to process
            each polarity individually.""".formatted(polarity, scans[i].getPolarity()));
        break;
      }
    }
    return true;
  }

  @NotNull
  private String missingMassListMessage(@NotNull Scan scan) {
    final StringBuilder b = new StringBuilder("Scan #");
    b.append(scan.getScanNumber()).append(" from ");
    b.append(dataFile.getName());
    b.append(" does not have a mass list. Please run \"Raw data methods\" -> \"Mass detection\"");
    if (dataFile instanceof IMSRawDataFile) {
      b.append("\nIMS files require mass detection on the frame level (Scan type = \"Frames ");
      b.append("only\" or \"All scan types\"");
    }
    return b.toString();
  }

  /**
   * Adds a zero intensity to every scan next to a detected data point without own data point, like
   * the ADAP chromatogram builder, so that resolvers see the edges of each signal.
   */
  @NotNull
  private ModularFeature createFeature(@NotNull ModularFeatureList flist,
      @NotNull BuiltChromatogram chromatogram, @NotNull Scan[] scans,
      @NotNull Ms2ScanIndex ms2Index) {
    final int n = chromatogram.getNumberOfDataPoints();
    final int numScans = scans.length;
    final int size = countWithFlankingZeros(chromatogram, numScans);
    final double zeroMz = chromatogram.getMeanMz();
    final double[] mzs = new double[size];
    final double[] intensities = new double[size];
    final List<Scan> seriesScans = new ArrayList<>(size);
    double minMz = Double.POSITIVE_INFINITY;
    double maxMz = Double.NEGATIVE_INFINITY;
    int out = 0;
    int lastScan = -1;
    for (int k = 0; k < n; k++) {
      final int scan = chromatogram.getScanIndex(k);
      if (scan - 1 >= 0 && scan - 1 > lastScan) {
        seriesScans.add(scans[scan - 1]);
        mzs[out] = zeroMz;
        intensities[out++] = 0d;
      }
      final double mz = chromatogram.getMz(k);
      seriesScans.add(scans[scan]);
      mzs[out] = mz;
      intensities[out++] = chromatogram.getIntensity(k);
      minMz = Math.min(minMz, mz);
      maxMz = Math.max(maxMz, mz);
      lastScan = scan;
      final int nextDetected = k + 1 < n ? chromatogram.getScanIndex(k + 1) : numScans;
      if (scan + 1 < numScans && scan + 1 < nextDetected) {
        seriesScans.add(scans[scan + 1]);
        mzs[out] = zeroMz;
        intensities[out++] = 0d;
        lastScan = scan + 1;
      }
    }
    if (out < size) {
      // only zeros were skipped, cannot happen as the size is counted with the same rules
      throw new IllegalStateException("Unexpected number of chromatogram data points");
    }
    // the zeros carry the mean m/z, which lies within the detected m/z range
    minMz = Math.min(minMz, zeroMz);
    maxMz = Math.max(maxMz, zeroMz);

    final SimpleIonTimeSeries series = new SimpleIonTimeSeries(flist.getMemoryMapStorage(), mzs,
        intensities, seriesScans);
    final ModularFeature feature = new ModularFeature(flist, dataFile, series,
        FeatureStatus.DETECTED);

    // use wider mz range to group MS2 with chromatogram, same as the ADAP builder
    final SimpleDoubleRange toleranceRange = mzTolerance.getSimpleToleranceRange(feature.getMZ());
    final float minRt = seriesScans.getFirst().getRetentionTime();
    final float maxRt = seriesScans.getLast().getRetentionTime();
    feature.setAllMS2FragmentScans(
        ms2Index.findFragmentScans(minRt, maxRt, Math.min(toleranceRange.lower(), minMz),
            Math.max(toleranceRange.upper(), maxMz)));
    return feature;
  }

  private static int countWithFlankingZeros(@NotNull BuiltChromatogram chromatogram, int numScans) {
    final int n = chromatogram.getNumberOfDataPoints();
    int size = 0;
    int lastScan = -1;
    for (int k = 0; k < n; k++) {
      final int scan = chromatogram.getScanIndex(k);
      if (scan - 1 >= 0 && scan - 1 > lastScan) {
        size++;
      }
      size++;
      lastScan = scan;
      final int nextDetected = k + 1 < n ? chromatogram.getScanIndex(k + 1) : numScans;
      if (scan + 1 < numScans && scan + 1 < nextDetected) {
        size++;
        lastScan = scan + 1;
      }
    }
    return size;
  }

  @NotNull
  public RawDataFile getDataFile() {
    return dataFile;
  }

  /**
   * @return the created feature list after the task finished, otherwise null
   */
  @Nullable
  public ModularFeatureList getFeatureList() {
    return newFeatureList;
  }

  /**
   * @return the statistics of the chromatogram detection after the task finished, otherwise null
   */
  @Nullable
  public FastChromatogramBuilderStatistics getStatistics() {
    return statistics;
  }
}
