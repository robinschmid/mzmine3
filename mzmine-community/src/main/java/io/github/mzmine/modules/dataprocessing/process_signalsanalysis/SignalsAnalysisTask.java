/*
 * Copyright (c) 2004-2024 The MZmine Development Team
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

package io.github.mzmine.modules.dataprocessing.process_signalsanalysis;

import static io.github.mzmine.util.DataPointUtils.removePrecursorMz;
import static io.github.mzmine.util.scans.ScanUtils.findAllMS2FragmentScans;

import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.TreeRangeMap;
import io.github.mzmine.datamodel.DataPoint;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.data_access.EfficientDataAccess.ScanDataType;
import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.types.analysis.InsourceFragmentAnalysisType;
import io.github.mzmine.datamodel.features.types.analysis.InsourceFragmentsAnalysisRobinType;
import io.github.mzmine.datamodel.impl.SimpleDataPoint;
import io.github.mzmine.modules.MZmineModule;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.taskcontrol.AbstractFeatureListTask;
import io.github.mzmine.util.DataPointSorter;
import io.github.mzmine.util.MemoryMapStorage;
import io.github.mzmine.util.scans.ScanUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The SignalsAnalysisTask is responsible for analyzing the signals from feature lists. The task
 * will be scheduled by the TaskController and progress is calculated from the
 * finishedItems/totalItems. It compares all MS1 signals present in the feature mass spectrum to the
 * MS2 signals present in all the fragment scans corresponding to precursors corresponding to MS1
 * signals.
 */
class SignalsAnalysisTask extends AbstractFeatureListTask {

  private static final Logger logger = Logger.getLogger(SignalsAnalysisTask.class.getName());
  private final List<FeatureList> featureLists;
  private final boolean useMassList;
  private final MZTolerance tolerance;

  /**
   * Constructor to initialize the task with necessary parameters.
   *
   * @param project        The MZmine project.
   * @param featureLists   The feature lists to process.
   * @param parameters     User parameters.
   * @param storage        Optional memory map storage.
   * @param moduleCallDate The date the module was called.
   * @param moduleClass    The class of the calling module.
   */
  public SignalsAnalysisTask(MZmineProject project, List<FeatureList> featureLists,
      ParameterSet parameters, @Nullable MemoryMapStorage storage, @NotNull Instant moduleCallDate,
      @NotNull Class<? extends MZmineModule> moduleClass) {
    super(storage, moduleCallDate, parameters, moduleClass);
    this.featureLists = featureLists;
    this.totalItems = featureLists.stream().mapToInt(FeatureList::getNumberOfRows).sum();
    var scanDataType = parameters.getValue(SignalsAnalysisParameters.scanDataType);
    this.useMassList = scanDataType == ScanDataType.MASS_LIST;
    this.tolerance = parameters.getValue(SignalsAnalysisParameters.tolerance);
  }

  /**
   * Streams MS1 scans from the grouped scans.
   *
   * @param groupedScans The grouped scans.
   * @return A stream of MS1 scans.
   */
  private static @NotNull Stream<Scan> streamMs1Scans(final List<GroupedSignalScans> groupedScans) {
    return groupedScans.stream().map(GroupedSignalScans::ms1Scans).flatMap(Collection::stream);
  }

  @Override
  protected void process() {
    List<GroupedSignalScans> groupedScans = collectSpectra();
    logger.info("Collected spectra - now starting to analyze the grouped scans.");
  }

  /**
   * Collects spectra from feature lists.
   *
   * @return A list of grouped signal scans.
   */
  private List<GroupedSignalScans> collectSpectra() {
    List<GroupedSignalScans> groupingResults = new ArrayList<>();
    for (FeatureList featureList : featureLists) {
      for (FeatureListRow row : featureList.getRows()) {
        GroupedSignalScans result = processRow(row);
        groupingResults.add(result);
      }
    }
    return groupingResults;
  }

  /**
   * Processes a single row from the feature list.
   *
   * @param row The feature list row.
   * @return The grouped signal scans.
   */
  private GroupedSignalScans processRow(FeatureListRow row) {
    List<Scan> ms1Scans = new ArrayList<>();
    List<Scan> ms2Scans = new ArrayList<>();
    for (final ModularFeature feature : row.getFeatures()) {
      try {
        List<Scan> exportedScans = processFeature(feature);
        for (Scan scan : exportedScans) {
          if (scan.getMSLevel() == 1) {
            ms1Scans.add(scan);
          } else if (scan.getMSLevel() == 2) {
            ms2Scans.add(scan);
          }
        }
      } catch (Exception ex) {
        logger.log(Level.WARNING, ex.getMessage(), ex);
      }
    }
    // adriano
    SignalsResults results = countUniqueSignalsBetweenMs1AndMs2Adriano(ms1Scans, ms2Scans,
        tolerance);
    row.set(InsourceFragmentAnalysisType.class, results);
    // robin
    SignalsResults resultsRobin = countUniqueSignalsBetweenMs1AndMs2Robin(ms1Scans, ms2Scans,
        tolerance);
    row.set(InsourceFragmentsAnalysisRobinType.class, resultsRobin);

    return new GroupedSignalScans(row, ms1Scans, ms2Scans);
  }

  /**
   * Processes a single feature.
   *
   * @param feature The feature to process.
   * @return The list of exported scans.
   */
  private List<Scan> processFeature(final Feature feature) {
    List<Scan> scansToExport = new ArrayList<>();
    Scan bestMs1 = feature.getRepresentativeScan();
    if (bestMs1 != null) {
      scansToExport.add(bestMs1);
      // TODO: This could be a parameter to get it over the whole file instead
      Range<Float> rawRtRange = feature.getRawDataPointsRTRange();
      RawDataFile raw = feature.getRawDataFile();
      DataPoint[] dataPoints = ScanUtils.extractDataPoints(bestMs1, useMassList);
      for (DataPoint dp : dataPoints) {
        double ms1Signal = dp.getMZ();
        Scan[] fragmentScansBroad = findAllMS2FragmentScans(raw, rawRtRange,
            tolerance.getToleranceRange(ms1Signal));
        for (Scan ms2 : fragmentScansBroad) {
          if (ms2 != null) {
            scansToExport.add(ms2);
          }
        }
      }
    }
    feature.getAllMS2FragmentScans().stream().filter(scan -> scan.getMSLevel() == 2)
        .forEach(scansToExport::add);
    return scansToExport;
  }

  @Override
  public String getTaskDescription() {
    return "Signals analysis task running on " + featureLists;
  }

  @Override
  protected @NotNull List<FeatureList> getProcessedFeatureLists() {
    return featureLists;
  }


  /**
   * Counts unique signals between MS1 and MS2 scans.
   *
   * @param ms1Scans  The MS1 scans.
   * @param ms2Scans  The MS2 scans.
   * @param tolerance The MZ tolerance.
   * @return The results of the signal count.
   */
  private SignalsResults countUniqueSignalsBetweenMs1AndMs2Robin(List<Scan> ms1Scans,
      List<Scan> ms2Scans, MZTolerance tolerance) {
    // require signal to be in 90% of MS1 scans
    int minMs1Scans = (int) Math.ceil(ms1Scans.size() * 0.9);
    var ms1SignalMap = filterMap(collectUniqueSignalsFromScansRobin(ms1Scans, tolerance),
        minMs1Scans);

    // in MS2 this may not be true if different energies used, so apply no filter
    var ms2SignalMap = collectUniqueSignalsFromScansRobin(ms2Scans, tolerance);

    List<UniqueSignal> ms1Signals = mapToList(ms1SignalMap);
    List<UniqueSignal> ms2Signals = mapToList(ms2SignalMap);

    List<UniqueSignal> ms1SignalMatchesMs2 = ms1Signals.stream()
        .filter(signal -> ms2SignalMap.get(signal.mz()) != null).toList();
    List<UniqueSignal> ms2SignalMatchesMs1 = ms2Signals.stream()
        .filter(signal -> ms1SignalMap.get(signal.mz()) != null).toList();
//    List<UniqueSignal> uniqueMs1Signals = findUnique(ms1Signals, ms2SignalMap);

    double ms1IntensityTotal = calcSumIntensity(ms1Signals);
    double ms1IntensityMatched = calcSumIntensity(ms1SignalMatchesMs2);
    double ms1IntensityMatchedPercent = ms1IntensityMatched / ms1IntensityTotal;
    double ms2IntensityTotal = calcSumIntensity(ms2Signals);
    double ms2IntensityMatched = calcSumIntensity(ms2SignalMatchesMs1);
    double ms2IntensityMatchedPercent = ms2IntensityMatched / ms2IntensityTotal;

    int ms1SignalsTotal = ms1Signals.size();
    int ms1SignalsMatched = ms1SignalMatchesMs2.size();
    double ms1SignalsMatchedPercent = ms1SignalsMatched / (double) ms1SignalsTotal;
    int ms2SignalsTotal = ms2Signals.size();
    int ms2SignalsMatched = ms2SignalMatchesMs1.size();
    double ms2SignalsMatchedPercent = ms2SignalsMatched / (double) ms2SignalsTotal;

    return new SignalsResults(ms1SignalsMatched, ms1SignalsTotal, ms1SignalsMatchedPercent,
        ms1IntensityMatchedPercent, ms2SignalsMatched, ms2SignalsTotal, ms2SignalsMatchedPercent,
        ms2IntensityMatchedPercent, 0);
  }

  private static double calcSumIntensity(final List<UniqueSignal> ms1SignalMatchesMs2) {
    return ms1SignalMatchesMs2.stream().mapToDouble(UniqueSignal::sumIntensity).sum();
  }

  private List<UniqueSignal> mapToList(final RangeMap<Double, UniqueSignal> map) {
    return new ArrayList<>(map.asMapOfRanges().values());
  }

  private @NotNull RangeMap<Double, UniqueSignal> filterMap(
      final RangeMap<Double, UniqueSignal> uniqueMs1Map, final int minSamples) {
    RangeMap<Double, UniqueSignal> unique = TreeRangeMap.create();

    uniqueMs1Map.asMapOfRanges().values().stream()
        .filter(signal -> signal.numberOfScans() >= minSamples).forEach(signal -> {
          unique.put(tolerance.getToleranceRange(signal.mz()), signal);
        });

    return unique;
  }

  /**
   * Collects unique dataPoints from scans.
   *
   * @param scans     The scans.
   * @param tolerance The MZ tolerance.
   * @return A set of unique dataPoints.
   */
  private RangeMap<Double, UniqueSignal> collectUniqueSignalsFromScansRobin(List<Scan> scans,
      MZTolerance tolerance) {
    RangeMap<Double, UniqueSignal> unique = TreeRangeMap.create();

    for (Scan scan : scans) {
      DataPoint[] dataPoints = ScanUtils.extractDataPoints(scan, useMassList);
      if (scan.getMSLevel() > 1) {
        // TODO arbitrarily removing 1 around precursor for now
        dataPoints = removePrecursorMz(dataPoints, scan.getPrecursorMz(), 1);
      }
      // start with most abundant signals first as they are usually more important
      Arrays.stream(dataPoints).sorted(DataPointSorter.DEFAULT_INTENSITY).forEach(dp -> {
        var uniqueSignal = unique.get(dp.getMZ());
        if (uniqueSignal == null) {
          uniqueSignal = new UniqueSignal(dp, scan);
          unique.put(tolerance.getToleranceRange(dp.getMZ()), uniqueSignal);
        } else {
          uniqueSignal.add(dp, scan);
        }
      });
    }

    return unique;
  }

  /**
   * Counts unique signals between MS1 and MS2 scans.
   *
   * @param ms1Scans  The MS1 scans.
   * @param ms2Scans  The MS2 scans.
   * @param tolerance The MZ tolerance.
   * @return The results of the signal count.
   */
  private SignalsResults countUniqueSignalsBetweenMs1AndMs2Adriano(List<Scan> ms1Scans,
      List<Scan> ms2Scans, MZTolerance tolerance) {
    var uniqueMs1 = collectUniqueSignalsFromScansAdriano(ms1Scans, tolerance);
    var uniqueMs2 = collectUniqueSignalsFromScansAdriano(ms2Scans, tolerance);

    List<DataPoint> precursors = new ArrayList<>();
    for (Scan scan : ms2Scans) {
      DataPoint precursorDataPoint = new SimpleDataPoint(scan.getPrecursorMz(), 0.0);
      precursors.add(precursorDataPoint);
    }
    int precursorsCount = collectUniqueSignals(precursors, tolerance).size();
    int commonCount = countUniquePairs(uniqueMs1, uniqueMs2, tolerance);
    int ms1Count = uniqueMs1.size();
    int ms2Count = uniqueMs2.size();

    double ms1Intensity = uniqueMs1.stream().mapToDouble(DataPoint::getIntensity).sum();
    double commonIntensity = uniqueMs1.stream().filter(dp1 -> uniqueMs2.stream()
            .anyMatch(dp2 -> tolerance.checkWithinTolerance(dp1.getMZ(), dp2.getMZ())))
        .mapToDouble(DataPoint::getIntensity).sum();

    return new SignalsResults(commonCount, ms1Count, commonCount / (double) ms1Count,
        commonIntensity / ms1Intensity, -1, ms2Count, -1, -1d, precursorsCount);
  }

  /**
   * Collects unique dataPoints from scans.
   *
   * @param scans     The scans.
   * @param tolerance The MZ tolerance.
   * @return A set of unique dataPoints.
   */
  private Set<DataPoint> collectUniqueSignalsFromScansAdriano(List<Scan> scans,
      MZTolerance tolerance) {
    Set<DataPoint> uniqueSignals = new HashSet<>();
    for (Scan scan : scans) {
      DataPoint[] dataPoints = ScanUtils.extractDataPoints(scan, useMassList);
      if (scan.getMSLevel() > 1) {
        // TODO arbitrarily removing 1 around precursor for now
        dataPoints = removePrecursorMz(dataPoints, scan.getPrecursorMz(), 1);
      }
      List<DataPoint> dps = Arrays.asList(dataPoints);
      uniqueSignals.addAll(collectUniqueSignals(dps, tolerance));
    }
    return uniqueSignals;
  }

  /**
   * Collects unique signals from a list of dataPoints.
   *
   * @param dataPoints The dataPoints.
   * @param tolerance  The MZ tolerance.
   * @return A set of unique signals.
   */
  private Set<DataPoint> collectUniqueSignals(List<DataPoint> dataPoints, MZTolerance tolerance) {
    Set<DataPoint> uniqueSignals = new HashSet<>();
    for (DataPoint dp : dataPoints) {
      boolean isUnique = true;
      for (DataPoint uniqueDp : uniqueSignals) {
        if (tolerance.checkWithinTolerance(dp.getMZ(), uniqueDp.getMZ())) {
          isUnique = false;
          break;
        }
      }
      if (isUnique) {
        uniqueSignals.add(dp);
      }
    }
    return uniqueSignals;
  }

  /**
   * Counts unique pairs between two sets of MZ values.
   *
   * @param uniqueMs1 The unique MS1 signals.
   * @param uniqueMs2 The unique MS2 signals.
   * @param tolerance The MZ tolerance.
   * @return The count of unique pairs.
   */
  private int countUniquePairs(Set<DataPoint> uniqueMs1, Set<DataPoint> uniqueMs2,
      MZTolerance tolerance) {
    int uniqueCount = 0;
    for (DataPoint dp1 : uniqueMs1) {
      for (DataPoint dp2 : uniqueMs2) {
        if (tolerance.checkWithinTolerance(dp1.getMZ(), dp2.getMZ())) {
          uniqueCount++;
          break; // Move to next dp1
        }
      }
    }
    return uniqueCount;
  }

}
