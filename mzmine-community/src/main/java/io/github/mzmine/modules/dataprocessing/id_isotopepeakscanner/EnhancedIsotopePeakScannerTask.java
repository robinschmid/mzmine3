package io.github.mzmine.modules.dataprocessing.id_isotopepeakscanner;

import io.github.mzmine.datamodel.IsotopePattern;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.MassList;
import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.FeatureList.FeatureListAppliedMethod;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.SimpleFeatureListAppliedMethod;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.mobilitytolerance.MobilityTolerance;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.MemoryMapStorage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javafx.collections.ObservableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class EnhancedIsotopePeakScannerTask extends AbstractTask {

  private static ModularFeatureList resultPeakList;
  private ParameterSet parameters;
  private MZmineProject project;
  private FeatureList peakList;
  private MZTolerance mzTolerance;
  private MobilityTolerance mobTolerance;
  private io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance rtTolerance;
  private double minPatternIntensity;
  private String element, suffix;
  private int charge;
  private PolarityType polarityType;
  private double minIsotopePatternScore;
  private boolean bestScores;
  private boolean onlyMonoisotopic;
  private boolean resolvedByMobility;
  private double minHeight;


  private static final Logger logger = Logger.getLogger(
      EnhancedIsotopePeakScannerTask.class.getName());

//  /**
//   * Scanning for characteristic isotope pattern and their monoisotopic mass
//   *
//   * @param rows            apply to all rows
//   * @param mzTolerance     tolerance for signal matching
//   * @param minIntensity    minimum isotope intensity for prediction
//   */

  EnhancedIsotopePeakScannerTask(MZmineProject project, FeatureList peakList,
      ParameterSet parameters, @Nullable MemoryMapStorage storage,
      @NotNull Instant moduleCallDate) {
    super(storage, moduleCallDate);
    this.parameters = parameters;
    this.project = project;
    this.peakList = peakList;

    mzTolerance = parameters.getParameter(IsotopePeakScannerParameters.mzTolerance).getValue();
    rtTolerance = parameters.getParameter(IsotopePeakScannerParameters.rtTolerance).getValue();
    mobTolerance = parameters.getParameter(IsotopePeakScannerParameters.mobTolerance).getValue();
    minPatternIntensity = parameters.getParameter(IsotopePeakScannerParameters.minPatternIntensity)
        .getValue();
    element = parameters.getParameter(IsotopePeakScannerParameters.element).getValue();
    suffix = parameters.getParameter(IsotopePeakScannerParameters.suffix).getValue();
    charge = parameters.getParameter(IsotopePeakScannerParameters.charge).getValue();
    resultPeakList = new ModularFeatureList(peakList.getName() + " " + suffix,
        getMemoryMapStorage(), peakList.getRawDataFiles());
    minIsotopePatternScore = parameters.getParameter(
        IsotopePeakScannerParameters.minIsotopePatternScore).getValue();
    bestScores = parameters.getParameter(IsotopePeakScannerParameters.bestScores).getValue();
    onlyMonoisotopic = parameters.getParameter(IsotopePeakScannerParameters.onlyMonoisotopic)
        .getValue();
    resolvedByMobility = parameters.getParameter(IsotopePeakScannerParameters.resolvedByMobility)
        .getValue();
    minHeight = parameters.getParameter(IsotopePeakScannerParameters.minHeight).getValue();

    polarityType = (charge > 0) ? PolarityType.POSITIVE : PolarityType.NEGATIVE;
    charge = (charge < 0) ? charge * -1 : charge;
  }

  @Override
  public void run() {

    if (!checkParameters()) {
      return;
    }

    if (getPeakListPolarity(peakList) != polarityType) {
      logger.warning(
          "PeakList.polarityType does not match selected polarity. " + getPeakListPolarity(
              peakList).toString() + "!=" + polarityType.toString());
    }

    ObservableList<FeatureListRow> rows = peakList.getRows();
    PeakListHandler featureMap = new PeakListHandler();
    Map<Integer, IsotopePattern> detectedIsotopePattern = new HashMap<>();
    List<FeatureListRow> rowsWithIPs = new ArrayList <>();
    Map<Integer, Double> scores = new HashMap<>();
    PeakListHandler finalMap;
    IsotopePatternCalculator ipCalculator = new IsotopePatternCalculator();
    IsotopePeakFinder isotopePeakFinder = new IsotopePeakFinder();
    IsotopePatternScoring scoring = new IsotopePatternScoring();
    MajorIsotopeIdentifier majorIsotopeIdentifier = new MajorIsotopeIdentifier();

    for (FeatureListRow row : rows) {
      featureMap.addRow(row);
    }

    // Scanning for characteristic isotope patterns for each element combination and charge.
    // Stored in "ResultMap" if the Similarity Score is higher than minScore.
    //In case of multiple scores for different charges, only the isotope pattern with the highest
    // score is stored as "detectedIsotopePattern.

    String[] elements;
    int[] charges = new int[charge];
    for (int i = 0; i < charges.length; i++) {
      charges[i] = i + 1;
    }
//    if (element.contains(",")) {
//      elements = element.split(",");
//    } else {
//      elements = new String[1];
//      elements[0] = element;
//    }
      elements = element.split(",");

    for (String element : elements) {
      for (int charge : charges) {

        for (FeatureListRow row : rows) {

          IsotopePattern calculatedPattern = ipCalculator.calculateIsotopePattern(row,element,
              minPatternIntensity, mzTolerance, charge);
          IsotopePattern detectedPattern = isotopePeakFinder.detectedIsotopePattern(peakList, row,
              ipCalculator.calculatedPatternDPs, mzTolerance, minHeight,
              calculatedPattern.getDataPointMZRange(), resolvedByMobility, charge);
          double score = scoring.calculateIsotopeScore(detectedPattern,
              calculatedPattern, mzTolerance, minHeight);

          if (score >= minIsotopePatternScore) {
            if (scores.get(row.getID()) != null && scores.get(row.getID()) > score){
              continue;
            }
            detectedIsotopePattern.put(row.getID(), detectedPattern);
            row.getBestFeature().setCharge(charge);
            rowsWithIPs.add(row);
            scores.put(row.getID(), score);
          }
        }
      }
      //Reduction of the features to the monoisotopic signals, whereby the monoisotopic signal is
      // assumed to be the one with the highest SimilarityScore within the MZ range of the IsotopePattern.
      //A tolerance range of 0.01 was applied to avoid excluding isotopic patterns with very similar score values.

      if (onlyMonoisotopic) {
        majorIsotopeIdentifier.findMajorIsotopes(rowsWithIPs,scores,detectedIsotopePattern,
            rtTolerance,mobTolerance, resolvedByMobility);
      } else {
       majorIsotopeIdentifier.findAllIsotopes(rowsWithIPs,scores,detectedIsotopePattern);
      }
      rowsWithIPs.clear();
      detectedIsotopePattern.clear();
      scores.clear();
    }

    // Reduction of features to those with the best similarity values of all considered element combinations.
    if (bestScores) {
      majorIsotopeIdentifier.findMajorIsotopesWithBestScores(rtTolerance,mobTolerance,resolvedByMobility);
      finalMap= majorIsotopeIdentifier.resultMapOfMajorIsotopesWithBestScores;
    } else {
      finalMap = majorIsotopeIdentifier.resultMapOfIsotopes;
    }

    resultPeakList = finalMap.generateResultPeakList(majorIsotopeIdentifier, resultPeakList);
    if (resultPeakList.getNumberOfRows() > 1) {
      addResultToProject(resultPeakList);
    } else {
      //message = "Element not found.";
    }
    setStatus(TaskStatus.FINISHED);
  }

  //
//  /**
//   * Apply isotope scoring to filter for monoisotopic masses.
//   *
//   * @param row             apply to this row
//   * @param mzTolerance     tolerance for signal matching
//   * @param minIntensity    minimum isotope intensity for prediction
//   * @param minIsotopeScore minimum isotope score to retain annotations
//   */

  private PolarityType getPeakListPolarity(FeatureList peakList) {
    return peakList.getRawDataFiles().stream()
        .map(raw -> raw.getDataPolarity().stream().findFirst().orElse(PolarityType.UNKNOWN))
        .findFirst().orElse(PolarityType.UNKNOWN);
  }

  /**
   * @return The {@link MemoryMapStorage} used to store results of this task (e.g. RawDataFiles,
   * MassLists, FeatureLists). May be null if results shall be stored in ram.
   */
  @Nullable
  public MemoryMapStorage getMemoryMapStorage() {
    return storage;
  }

  @Override
  public String getTaskDescription() {
    return null;
  }

  @Override
  public double getFinishedPercentage() {
    return 0;
  }


  public void addResultToProject(ModularFeatureList resultPeakList) {
    // Add new peakList to the project
    project.addFeatureList(resultPeakList);

    // Load previous applied methods
    for (FeatureListAppliedMethod proc : peakList.getAppliedMethods()) {
      resultPeakList.addDescriptionOfAppliedTask(proc);
    }

    // Add task description to peakList
    resultPeakList.addDescriptionOfAppliedTask(
        new SimpleFeatureListAppliedMethod("IsotopePeakScanner", IsotopePeakScannerModule.class,
            parameters, getModuleCallDate()));
  }


  private boolean checkParameters() {
    if (charge == 0) {
      setErrorMessage("Error: charge may not be 0!");
      setStatus(TaskStatus.ERROR);
      return false;
    }

    RawDataFile[] raws = peakList.getRawDataFiles().toArray(RawDataFile[]::new);
    boolean foundMassList = false;
    for (RawDataFile raw : raws) {
      ObservableList<Scan> scanNumbers = raw.getScans();
      for (Scan scan : scanNumbers) {
        MassList massList = scan.getMassList();
        if (massList != null) {
          foundMassList = true;
          break;
        }
      }
    }
    if (foundMassList == false) {
      setErrorMessage("Feature list \"" + peakList.getName() + "\" does not contain a mass list");
      setStatus(TaskStatus.ERROR);
      return false;
    }

    return true;
  }

}
