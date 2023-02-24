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
import javafx.scene.layout.Priority;

/**
 * This adds an accordion to the parameter pane with additional parameters. Those parameters should
 * only be used if the value (check box) is selected. One use case is the advanced batch mode.
 *
 * @author Robin Schmid <a href="https://github.com/robinschmid">https://github.com/robinschmid</a>
 */
public class AdvancedParametersParameter<PARAMETERS extends ParameterSet> extends
    OptionalEmbeddedParametersParameter<PARAMETERS, AdvancedParametersComponent> {

  public AdvancedParametersParameter(String name, String description, PARAMETERS embeddedParameters,
      boolean defaultVal) {
    super(name, description, defaultVal, embeddedParameters);
  }

  public AdvancedParametersParameter(String name, String description,
      PARAMETERS embeddedParameters) {
    this(name, description, embeddedParameters, false);
  }

  public AdvancedParametersParameter(PARAMETERS embeddedParameters) {
    this("Advanced", "Advanced parameters", embeddedParameters, false);
  }

  @Override
  public Priority getComponentVgrowPriority() {
    return Priority.SOMETIMES;
  }

  @Override
  public AdvancedParametersComponent createEditingComponent() {
    return new AdvancedParametersComponent(embeddedParameters, name, value);
  }

  @Override
  public AdvancedParametersParameter<PARAMETERS> cloneParameter() {
    final PARAMETERS embeddedParametersClone = (PARAMETERS) embeddedParameters.cloneParameterSet();
    final AdvancedParametersParameter<PARAMETERS> copy = new AdvancedParametersParameter<>(name,
        description, embeddedParametersClone);
    copy.setValue(this.getValue());
    return copy;
  }

}
