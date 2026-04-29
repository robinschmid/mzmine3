package io.github.mzmine.modules.tools.cxsmiles_generator;

import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.modules.MZmineModuleCategory;
import io.github.mzmine.modules.MZmineRunnableModule;
import io.github.mzmine.modules.tools.cxsmiles_generator.mvci.CxSmilesGeneratorController;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.taskcontrol.Task;
import io.github.mzmine.util.ExitCode;
import java.time.Instant;
import java.util.Collection;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

/**
 * Opens a standalone window for building CxSMILES Markush structures from a list of SMILES
 * positional isomers. Called on the FX thread via the Tools menu.
 */
public class CxSmilesGeneratorModule implements MZmineRunnableModule {

  private static final String MODULE_NAME = "CxSMILES Markush generator";
  private static final String MODULE_DESCRIPTION =
      "Converts a list of positional-isomer SMILES into a CxSMILES Markush structure using MCS.";

  @Override
  public @NotNull String getName() {
    return MODULE_NAME;
  }

  @Override
  public @NotNull String getDescription() {
    return MODULE_DESCRIPTION;
  }

  @Override
  public @NotNull MZmineModuleCategory getModuleCategory() {
    return MZmineModuleCategory.TOOLS;
  }

  @Override
  public @NotNull Class<? extends ParameterSet> getParameterSetClass() {
    return CxSmilesGeneratorParameters.class;
  }

  @Override
  public @NotNull ExitCode runModule(@NotNull MZmineProject project,
      @NotNull ParameterSet parameters, @NotNull Collection<Task> tasks,
      @NotNull Instant moduleCallDate) {

    CxSmilesGeneratorController controller = new CxSmilesGeneratorController();
    Scene scene = new Scene(controller.buildView(), 1000, 640);

    Stage stage = new Stage();
    stage.setTitle(MODULE_NAME);
    stage.setScene(scene);
    stage.setOnHidden(_ -> controller.close());
    stage.show();

    return ExitCode.OK;
  }
}
