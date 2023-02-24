/*
 * Copyright (c) 2004-2022 The MZmine Development Team
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

package io.github.mzmine.parameters.parametertypes.submodules;

import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.dialogs.ParameterSetupPane;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Tooltip;

/**
 * Basis for parameter components with sub parameters and optional with checkbox
 */
public abstract class OptionalEmbeddedParametersComponent<ValueType> extends
    EmbeddedParametersComponent<ValueType> {

  protected final CheckBox checkBox;

  public OptionalEmbeddedParametersComponent(final ParameterSet parameters, String title,
      boolean state) {
    super(parameters);

    checkBox = new CheckBox(title);
    setSelected(state);

    checkBox.selectedProperty().addListener((ob, ov, nv) -> applyCheckBoxState());
    applyCheckBoxState();
  }

  protected ParameterSetupPane createParamPane(final ParameterSet parameters) {
    paramPane = new ParameterSetupPane(true, parameters, false, false, null, true, false);
    return paramPane;
  }

  public boolean isSelected() {
    return checkBox.isSelected();
  }

  public void setSelected(boolean state) {
    checkBox.setSelected(state);
  }

  public void setToolTipText(String toolTip) {
    checkBox.setTooltip(new Tooltip(toolTip));
  }

  public CheckBox getCheckbox() {
    return checkBox;
  }

  protected void applyCheckBoxState() {
    paramPane.getParametersAndComponents().values()
        .forEach(node -> node.setDisable(!checkBox.isSelected()));
  }
}
