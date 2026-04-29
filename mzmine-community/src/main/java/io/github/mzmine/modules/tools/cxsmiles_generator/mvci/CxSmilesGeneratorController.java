package io.github.mzmine.modules.tools.cxsmiles_generator.mvci;

import io.github.mzmine.javafx.mvci.FxController;
import io.github.mzmine.javafx.mvci.FxViewBuilder;
import java.util.Arrays;
import org.jetbrains.annotations.NotNull;

public class CxSmilesGeneratorController extends FxController<CxSmilesGeneratorModel> {

  private final CxSmilesGeneratorViewBuilder viewBuilder;

  public CxSmilesGeneratorController() {
    super(new CxSmilesGeneratorModel());
    viewBuilder = new CxSmilesGeneratorViewBuilder(model, this::onGenerate);
  }

  /**
   * Called by the view's Generate button. Splits the raw text into SMILES lines, syncs the model,
   * then launches the compute task off the FX thread.
   */
  public void onGenerate(@NotNull String rawText) {
    final String[] lines = rawText.split("\\R");
    model.getInputSmiles().setAll(Arrays.asList(lines));
    onTaskThreadDelayed(new CxSmilesComputeTask(model));
  }

  @Override
  protected @NotNull FxViewBuilder<CxSmilesGeneratorModel> getViewBuilder() {
    return viewBuilder;
  }
}
