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
 * Parameter represented by check box with additional sub-module
 */
public class OptionalModuleParameter<PARAMETERS extends ParameterSet> extends
    OptionalEmbeddedParametersParameter<PARAMETERS, OptionalModuleComponent> {

  public OptionalModuleParameter(String name, String description, PARAMETERS embeddedParameters,
      boolean defaultVal) {
    super(name, description, defaultVal, embeddedParameters);
  }

  public OptionalModuleParameter(String name, String description, PARAMETERS embeddedParameters) {
    this(name, description, embeddedParameters, false);
  }

  @Override
  public OptionalModuleComponent createEditingComponent() {
    return new OptionalModuleComponent(embeddedParameters, isSelected());
  }

  @Override
  public Priority getComponentVgrowPriority() {
    return Priority.SOMETIMES;
  }

  @Override
  public OptionalModuleParameter<PARAMETERS> cloneParameter() {
    final PARAMETERS embeddedParametersClone = (PARAMETERS) embeddedParameters.cloneParameterSet();
    final OptionalModuleParameter<PARAMETERS> copy = new OptionalModuleParameter<>(name,
        description, embeddedParametersClone);
    copy.setValue(this.getValue());
    return copy;
  }

}
