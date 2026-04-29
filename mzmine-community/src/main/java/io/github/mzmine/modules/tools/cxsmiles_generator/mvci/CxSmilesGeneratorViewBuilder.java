package io.github.mzmine.modules.tools.cxsmiles_generator.mvci;

import io.github.mzmine.javafx.mvci.FxViewBuilder;
import io.github.mzmine.modules.visualization.molstructure.Structure2DRenderConfig;
import io.github.mzmine.modules.visualization.molstructure.Structure2DRenderer;
import java.awt.Font;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.jetbrains.annotations.NotNull;

public class CxSmilesGeneratorViewBuilder extends FxViewBuilder<CxSmilesGeneratorModel> {

  private static final double CANVAS_W = 600;
  private static final double CANVAS_H = 400;

  private final Consumer<String> onGenerate;
  private final Structure2DRenderer renderer = new Structure2DRenderer(
      new Font("Arial", Font.PLAIN, 10));

  public CxSmilesGeneratorViewBuilder(@NotNull CxSmilesGeneratorModel model,
      @NotNull Consumer<String> onGenerate) {
    super(model);
    this.onGenerate = onGenerate;
  }

  @Override
  public @NotNull Region build() {
    final SplitPane split = new SplitPane(buildInputPanel(), buildResultPanel());
    split.setDividerPositions(0.35);
    return split;
  }

  private Region buildInputPanel() {
    final TextArea inputArea = new TextArea();
    inputArea.setPromptText("One SMILES per line\ne.g.\nClc1ccccc1-c1ccccc1\nClc1cccc(-c1ccccc1)c1");
    inputArea.setWrapText(false);
    VBox.setVgrow(inputArea, Priority.ALWAYS);

    final Button generateBtn = new Button("Generate CxSMILES");
    generateBtn.setMaxWidth(Double.MAX_VALUE);
    generateBtn.setOnAction(_ -> onGenerate.accept(inputArea.getText()));

    final VBox panel = new VBox(8,
        new Label("SMILES input (one per line)"),
        inputArea,
        generateBtn);
    panel.setPadding(new Insets(12));
    return panel;
  }

  private Region buildResultPanel() {
    final TextArea outputArea = new TextArea();
    outputArea.setEditable(false);
    outputArea.setWrapText(true);
    outputArea.setPromptText("CxSMILES will appear here…");
    outputArea.setPrefRowCount(4);

    final Canvas canvas = new Canvas(CANVAS_W, CANVAS_H);

    final Label statusLabel = new Label();
    statusLabel.textProperty().bind(model.statusMessageProperty());

    // redraw canvas whenever a new result arrives (called on FX thread via FxUpdateTask)
    model.resultProperty().subscribe(result -> {
      outputArea.setText(result != null ? result.cxSmiles() : "");
      if (result != null) {
        renderer.drawStructure(canvas, result.scaffoldMol(),
            Structure2DRenderConfig.DEFAULT_CONFIG);
      }
    });

    final VBox panel = new VBox(8,
        new Label("CxSMILES output"),
        outputArea,
        new Label("Structure depiction"),
        canvas,
        statusLabel);
    panel.setPadding(new Insets(12));
    BorderPane.setMargin(canvas, new Insets(4, 0, 4, 0));
    return panel;
  }
}
