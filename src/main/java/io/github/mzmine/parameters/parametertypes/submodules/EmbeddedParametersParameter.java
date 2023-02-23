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

import io.github.mzmine.parameters.Parameter;
import io.github.mzmine.parameters.ParameterContainer;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.ParameterUtils;
import io.github.mzmine.parameters.UserParameter;
import io.github.mzmine.parameters.parametertypes.EmbeddedParameterSet;
import java.util.Collection;
import javafx.scene.Node;

/**
 * This adds an accordion to the parameter pane with additional parameters. Those parameters should
 * only be used if the value (check box) is selected. One use case is the advanced batch mode.
 *
 * @author Robin Schmid <a href="https://github.com/robinschmid">https://github.com/robinschmid</a>
 */
public abstract class EmbeddedParametersParameter<ValueType, PARAMETERS extends ParameterSet, EditorComponent extends Node> implements
    UserParameter<ValueType, EditorComponent>, ParameterContainer,
    EmbeddedParameterSet<PARAMETERS, ValueType> {

  private final String name;
  private final String description;
  private PARAMETERS embeddedParameters;
  private ValueType value;

  public EmbeddedParametersParameter(String name, String description, ValueType defaultVal,
      PARAMETERS embeddedParameters) {
    this.name = name;
    this.description = description;
    this.embeddedParameters = embeddedParameters;
    value = defaultVal;
  }

  @Override
  public PARAMETERS getEmbeddedParameters() {
    return embeddedParameters;
  }

  public void setEmbeddedParameters(PARAMETERS embeddedParameters) {
    this.embeddedParameters = embeddedParameters;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getDescription() {
    return description;
  }

  @Override
  public ValueType getValue() {
    return value;
  }

  @Override
  public void setValue(ValueType value) {
    this.value = value;
  }


  @Override
  public boolean checkValue(Collection<String> errorMessages) {
    return embeddedParameters.checkParameterValues(errorMessages);
  }

  @Override
  public void setSkipSensitiveParameters(boolean skipSensitiveParameters) {
    // delegate skipSensitiveParameters to embedded ParameterSet
    embeddedParameters.setSkipSensitiveParameters(skipSensitiveParameters);
  }

  @Override
  public boolean valueEquals(Parameter<?> that) {
    if (!(that instanceof EmbeddedParametersParameter thatOpt)) {
      return false;
    }

    if (value != thatOpt.getValue()) {
      return false;
    }

    return ParameterUtils.equalValues(getEmbeddedParameters(), thatOpt.getEmbeddedParameters(),
        false, false);
  }

}
