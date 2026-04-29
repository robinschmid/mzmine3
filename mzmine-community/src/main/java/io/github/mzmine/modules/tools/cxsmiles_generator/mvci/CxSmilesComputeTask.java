package io.github.mzmine.modules.tools.cxsmiles_generator.mvci;

import io.github.mzmine.javafx.mvci.FxUpdateTask;
import io.github.mzmine.modules.tools.cxsmiles_generator.CxSmilesResult;
import io.github.mzmine.modules.tools.cxsmiles_generator.chemistry.CxSmilesConverter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Runs the CxSMILES pipeline off the FX thread and pushes the result back via
 * {@link #updateGuiModel()} on the FX thread.
 */
public class CxSmilesComputeTask extends FxUpdateTask<CxSmilesGeneratorModel> {

  private static final Logger logger = Logger.getLogger(CxSmilesComputeTask.class.getName());

  // snapshot taken before going off-thread
  private final List<String> smilesList;

  // filled during process(), consumed in updateGuiModel()
  @Nullable
  private CxSmilesResult computedResult;
  @Nullable
  private String errorMessage;

  protected CxSmilesComputeTask(@NotNull CxSmilesGeneratorModel model) {
    super("CxSMILES generation", model);
    // take a snapshot so the user can keep editing the text area while the task runs
    this.smilesList = new ArrayList<>(model.getInputSmiles());
  }

  @Override
  public boolean checkPreConditions() {
    long nonEmpty = model.getInputSmiles().stream().filter(s -> !s.isBlank()).count();
    return nonEmpty >= 2;
  }

  @Override
  public void onFailedPreCondition() {
    model.setStatusMessage("Enter at least 2 non-empty SMILES.");
  }

  @Override
  protected void process() {
    try {
      computedResult = CxSmilesConverter.convert(smilesList);
    } catch (Exception ex) {
      logger.log(Level.WARNING, "CxSMILES generation failed", ex);
      errorMessage = ex.getMessage();
    }
  }

  @Override
  protected void updateGuiModel() {
    if (computedResult != null) {
      model.setResult(computedResult);
      model.setStatusMessage("Done. " + String.join(" | ", computedResult.variablePositionSummary()));
    } else {
      model.setStatusMessage("Error: " + errorMessage);
    }
  }

  @Override
  public String getTaskDescription() {
    return "Computing CxSMILES from %d SMILES".formatted(smilesList.size());
  }

  @Override
  public double getFinishedPercentage() {
    return 0;
  }
}
