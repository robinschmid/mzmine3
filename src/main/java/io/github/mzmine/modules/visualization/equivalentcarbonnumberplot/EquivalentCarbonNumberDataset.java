package io.github.mzmine.modules.visualization.equivalentcarbonnumberplot;

import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.datamodel.features.types.annotations.LipidMatchListType;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.common.lipididentificationtools.MSMSLipidTools;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.common.lipididentificationtools.matchedlipidannotations.MatchedLipid;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.common.lipididentificationtools.matchedlipidannotations.molecularspecieslevelidentities.MolecularSpeciesLevelAnnotation;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.common.lipididentificationtools.matchedlipidannotations.specieslevellipidmatches.SpeciesLevelAnnotation;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.common.lipids.ILipidAnnotation;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.common.lipids.ILipidClass;
import io.github.mzmine.taskcontrol.Task;
import io.github.mzmine.taskcontrol.TaskPriority;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.taskcontrol.TaskStatusListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleObjectProperty;
import org.jetbrains.annotations.NotNull;
import org.jfree.data.xy.AbstractXYDataset;

public class EquivalentCarbonNumberDataset extends AbstractXYDataset implements Task {

  protected final @NotNull Property<TaskStatus> status = new SimpleObjectProperty<>(
      TaskStatus.WAITING);
  protected String errorMessage = null;
  private List<TaskStatusListener> listener;
  private double finishedSteps;

  private double[] xValues;
  private double[] yValues;
  private final FeatureListRow[] lipidRows;
  private final List<FeatureListRow> selectedRows;
  private final List<MatchedLipid> matchedLipids = new ArrayList<>();
  private final ILipidClass selectedLipidClass;
  private final int selectedDBENumber;
  private List<MatchedLipid> lipidsForDBE;


  public EquivalentCarbonNumberDataset(List<FeatureListRow> selectedRows,
      FeatureListRow[] lipidRows, ILipidClass selectedLipidClass, int selectedDBENumber) {
    this.selectedRows = selectedRows;
    this.lipidRows = lipidRows;
    this.selectedLipidClass = selectedLipidClass;
    this.selectedDBENumber = selectedDBENumber;
    for (FeatureListRow featureListRow : lipidRows) {
      if (featureListRow instanceof ModularFeatureListRow) {
        matchedLipids.add(featureListRow.get(LipidMatchListType.class).get(0));
      }
    }
    MZmineCore.getTaskController().addTask(this);
  }

  @Override
  public void run() {
    finishedSteps = 0;
    setStatus(TaskStatus.PROCESSING);
    if (isCanceled()) {
      setStatus(TaskStatus.CANCELED);
    }
    Map<ILipidClass, Map<Integer, List<MatchedLipid>>> groupedLipids = matchedLipids.stream()
        .collect(
            Collectors.groupingBy(matchedLipid -> matchedLipid.getLipidAnnotation().getLipidClass(),
                Collectors.groupingBy(matchedLipid -> {
                  ILipidAnnotation lipidAnnotation = matchedLipid.getLipidAnnotation();
                  if (lipidAnnotation instanceof MolecularSpeciesLevelAnnotation molecularAnnotation) {
                    return MSMSLipidTools.getCarbonandDBEFromLipidAnnotaitonString(
                        molecularAnnotation.getAnnotation()).getValue();
                  } else if (lipidAnnotation instanceof SpeciesLevelAnnotation) {
                    return MSMSLipidTools.getCarbonandDBEFromLipidAnnotaitonString(
                        lipidAnnotation.getAnnotation()).getValue();
                  } else {
                    return -1;
                  }
                }, Collectors.toList())));

    Map<Integer, List<MatchedLipid>> lipidsOfClass = groupedLipids.get(selectedLipidClass);

    if (lipidsOfClass != null) {
      lipidsForDBE = lipidsOfClass.get(selectedDBENumber);

      if (lipidsForDBE != null) {
        xValues = new double[lipidsForDBE.size()];
        yValues = new double[lipidsForDBE.size()];

        for (int i = 0; i < lipidsForDBE.size(); i++) {
          // get number of Carbons
          ILipidAnnotation lipidAnnotation = lipidsForDBE.get(i).getLipidAnnotation();
          if (lipidAnnotation instanceof MolecularSpeciesLevelAnnotation molecularAnnotation) {
            yValues[i] = MSMSLipidTools.getCarbonandDBEFromLipidAnnotaitonString(
                molecularAnnotation.getAnnotation()).getKey();
          } else if (lipidAnnotation instanceof SpeciesLevelAnnotation) {
            yValues[i] = MSMSLipidTools.getCarbonandDBEFromLipidAnnotaitonString(
                lipidAnnotation.getAnnotation()).getKey();
          }
          for (FeatureListRow lipidRow : lipidRows) {
            List<MatchedLipid> featureLipids = lipidRow.get(LipidMatchListType.class);

            if (!featureLipids.isEmpty()) {
              MatchedLipid featureMatchedLipid = featureLipids.get(0);

              if (lipidsForDBE.get(i).equals(featureMatchedLipid)) {
                xValues[i] = lipidRow.getAverageRT();
                break;
              }
            }
          }
        }
      }
    }
    finishedSteps = 1;
    setStatus(TaskStatus.FINISHED);
  }


  @Override
  public int getSeriesCount() {
    return 1;
  }

  public Comparable<?> getRowKey(int row) {
    return selectedRows.get(row).toString();
  }

  @Override
  public Comparable getSeriesKey(int series) {
    return getRowKey(series);
  }


  public double[] getXValues() {
    return xValues;
  }

  @Override
  public int getItemCount(int series) {
    return xValues.length;
  }

  @Override
  public Number getX(int series, int item) {
    return xValues[item];
  }

  @Override
  public Number getY(int series, int item) {
    return yValues[item];
  }

  public MatchedLipid getMatchedLipid(int item) {
    return lipidsForDBE.get(item);
  }

  @Override
  public String getTaskDescription() {
    return "Computing ECN model for " + selectedLipidClass.getAbbr() + " with " + selectedDBENumber
        + " DBEs";
  }

  @Override
  public double getFinishedPercentage() {
    return finishedSteps;
  }

  @Override
  public TaskStatus getStatus() {
    return status.getValue();
  }

  @Override
  public String getErrorMessage() {
    return errorMessage;
  }

  @Override
  public TaskPriority getTaskPriority() {
    return TaskPriority.NORMAL;
  }

  @Override
  public void cancel() {
    setStatus(TaskStatus.CANCELED);
  }

  public final void setStatus(TaskStatus newStatus) {
    TaskStatus old = getStatus();
    status.setValue(newStatus);
    if (listener != null && !newStatus.equals(old)) {
      for (TaskStatusListener listener : listener) {
        listener.taskStatusChanged(this, newStatus, old);
      }
    }
  }

  public void addTaskStatusListener(TaskStatusListener list) {
    if (listener == null) {
      listener = new ArrayList<>();
    }
    listener.add(list);
  }

  @Override
  public boolean removeTaskStatusListener(TaskStatusListener list) {
    if (listener != null) {
      return listener.remove(list);
    } else {
      return false;
    }
  }

  @Override
  public void clearTaskStatusListener() {
    if (listener != null) {
      listener.clear();
    }
  }

}
