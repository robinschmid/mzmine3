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

package io.github.mzmine.modules.dataprocessing.process_fragmentsanalysis;

import static io.github.mzmine.util.DataPointUtils.removePrecursorMz;
import static io.github.mzmine.util.scans.ScanUtils.findAllMS2FragmentScans;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.DataPoint;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.data_access.EfficientDataAccess.ScanDataType;
import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.types.numbers.CommonFragmentsType;
import io.github.mzmine.modules.MZmineModule;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.taskcontrol.AbstractFeatureListTask;
import io.github.mzmine.util.MemoryMapStorage;
import io.github.mzmine.util.scans.ScanUtils;
import java.time.Instant;
import java.util.ArrayList;
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
 * The task will be scheduled by the TaskController. Progress is calculated from the
 * finishedItems/totalItems
 */
class FragmentsAnalysisTask extends AbstractFeatureListTask {

  private static final Logger logger = Logger.getLogger(FragmentsAnalysisTask.class.getName());
  private final List<FeatureList> featureLists;
  private final boolean useMassList;
  private final MZTolerance tolerance;

  /**
   * Constructor is used to extract all parameters
   *
   * @param featureLists data source is featureLists
   * @param parameters   user parameters
   */
  public FragmentsAnalysisTask(MZmineProject project, List<FeatureList> featureLists,
      ParameterSet parameters, @Nullable MemoryMapStorage storage, @NotNull Instant moduleCallDate,
      @NotNull Class<? extends MZmineModule> moduleClass) {
    super(storage, moduleCallDate, parameters, moduleClass);
    this.featureLists = featureLists;
    totalItems = featureLists.stream().mapToInt(FeatureList::getNumberOfRows).sum();
    var scanDataType = parameters.getValue(FragmentsAnalysisParameters.scanDataType);
    useMassList = scanDataType == ScanDataType.MASS_LIST;
    tolerance = parameters.getValue(FragmentsAnalysisParameters.tolerance);
  }

  private static @NotNull Stream<Scan> streamMs1Scans(
      final List<GroupedFragmentScans> groupedScans) {
    return groupedScans.stream().map(GroupedFragmentScans::ms1Scans).flatMap(Collection::stream);
  }

  @Override
  protected void process() {
    List<GroupedFragmentScans> groupedScans = collectSpectra();
    logger.info("collected spectra - now starting to analyze the grouped scans");
  }

  private List<GroupedFragmentScans> collectSpectra() {
    List<GroupedFragmentScans> groupingResults = new ArrayList<>();
    for (FeatureList featureList : featureLists) {
      for (var row : featureList.getRows()) {
        GroupedFragmentScans result = processRow(row);
        if (!result.ms2Scans().isEmpty()) {
          groupingResults.add(result);
        }
      }
    }
    return groupingResults;
  }

  private GroupedFragmentScans processRow(FeatureListRow row) {
    // collect all MS1 and MS2 for this row
    List<Scan> ms1Scans = new ArrayList<>();
    List<Scan> ms2Scans = new ArrayList<>();
    for (final ModularFeature feature : row.getFeatures()) {
      try {
        List<Scan> exportedScans = processFeature(row, feature);
        for (Scan scan : exportedScans) {
          if (scan.getMSLevel() == 1) {
            ms1Scans.add(scan);
          }
          if (scan.getMSLevel() == 2) {
            ms2Scans.add(scan);
          }
        }
      } catch (Exception ex) {
        logger.log(Level.WARNING, ex.getMessage(), ex);
      }
    }
    int commonFragmentsCount = countUniqueFragmentsBetweenMs1AndMs2(ms1Scans, ms2Scans, tolerance);
    row.set(CommonFragmentsType.class, commonFragmentsCount);
    return new GroupedFragmentScans(row, ms1Scans, ms2Scans);
  }

  /**
   * For each feature (individual sample)
   *
   * @return all exported scans
   */
  private List<Scan> processFeature(final FeatureListRow row, final Feature feature) {
    // skip if there are no MS2
    List<Scan> fragmentScans = feature.getAllMS2FragmentScans();
    if (fragmentScans.isEmpty()) {
      return List.of(); // return empty list
    }
    RawDataFile raw = feature.getRawDataFile();
    String rawFileName = raw.getFileName();
    // collect all scans to export
    List<Scan> scansToExport = new ArrayList<>();
    Scan bestMs1 = feature.getRepresentativeScan();
    if (bestMs1 != null) {
      scansToExport.add(bestMs1);
    }

    for (Scan ms2 : fragmentScans) {
      if (ms2.getMSLevel() != 2) {
        continue; // skip MSn
      }
      if (ms2 != null) {
        scansToExport.add(ms2);
      }
      Scan previousScan = ScanUtils.findPrecursorScan(ms2);
      if (previousScan != null) {
        scansToExport.add(previousScan);
      }
      Scan nextScan = ScanUtils.findSucceedingPrecursorScan(ms2);
      if (nextScan != null) {
        scansToExport.add(nextScan);
      }
    }
    return scansToExport;
  }

  @Override
  public String getTaskDescription() {
    return STR."Fragments analysis task runs on \{featureLists}";
  }

  @Override
  protected @NotNull List<FeatureList> getProcessedFeatureLists() {
    return featureLists;
  }

  // TODO: Sanitize the spectra by removing everything above the precursor - tolerance? And more?
  private int countUniqueFragmentsBetweenMs1AndMs2(List<Scan> ms1Scans, List<Scan> ms2Scans,
      MZTolerance tolerance) {
    Set<Double> uniqueMs1 = collectUniqueFragments(ms1Scans, tolerance);
    Set<Double> uniqueMs2 = collectUniqueFragments(ms2Scans, tolerance);
    int uniqueCount = countUniquePairs(uniqueMs1, uniqueMs2, tolerance);
    return uniqueCount;
  }

  private Set<Double> collectUniqueFragments(List<Scan> scans, MZTolerance tolerance) {
    Set<Double> uniqueFragments = new HashSet<>();
    for (Scan scan : scans) {
      DataPoint[] dataPoints = ScanUtils.extractDataPoints(scan, useMassList);
      if (scan.getMSLevel() > 1) {
        dataPoints = removePrecursorMz(dataPoints, scan.getPrecursorMz(), 1);
      }
      for (DataPoint dp : dataPoints) {
        double mz = dp.getMZ();
        boolean isUnique = true;

        // Check against existing uniqueFragments
        for (double uniqueMz : uniqueFragments) {
          if (tolerance.checkWithinTolerance(mz, uniqueMz)) {
            isUnique = false;
            break;
          }
        }

        if (isUnique) {
          uniqueFragments.add(mz);
        }
      }
    }
    return uniqueFragments;
  }

  private int countUniquePairs(Set<Double> uniqueMs1, Set<Double> uniqueMs2,
      MZTolerance tolerance) {
    int uniqueCount = 0;
    for (double mz1 : uniqueMs1) {
      for (double mz2 : uniqueMs2) {
        if (tolerance.checkWithinTolerance(mz1, mz2)) {
          uniqueCount++;
          break; // Move to next mz1
        }
      }
    }
    return uniqueCount;
  }
}
