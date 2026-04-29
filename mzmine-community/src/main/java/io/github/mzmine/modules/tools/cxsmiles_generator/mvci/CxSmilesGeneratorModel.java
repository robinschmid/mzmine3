package io.github.mzmine.modules.tools.cxsmiles_generator.mvci;

import io.github.mzmine.modules.tools.cxsmiles_generator.CxSmilesResult;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CxSmilesGeneratorModel {

  private final ObservableList<String> inputSmiles = FXCollections.observableArrayList();
  private final ObjectProperty<CxSmilesResult> result = new SimpleObjectProperty<>();
  private final StringProperty statusMessage = new SimpleStringProperty("Enter SMILES and click Generate.");

  @NotNull
  public ObservableList<String> getInputSmiles() {
    return inputSmiles;
  }

  @Nullable
  public CxSmilesResult getResult() {
    return result.get();
  }

  public void setResult(@Nullable CxSmilesResult value) {
    result.set(value);
  }

  @NotNull
  public ObjectProperty<CxSmilesResult> resultProperty() {
    return result;
  }

  @NotNull
  public String getStatusMessage() {
    return statusMessage.get();
  }

  public void setStatusMessage(@NotNull String message) {
    statusMessage.set(message);
  }

  @NotNull
  public StringProperty statusMessageProperty() {
    return statusMessage;
  }
}
