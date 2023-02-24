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

import io.github.mzmine.parameters.AbstractParameter;
import io.github.mzmine.parameters.Parameter;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.ParameterUtils;
import io.github.mzmine.parameters.parametertypes.EmbeddedParameterSet;
import java.util.Collection;
import java.util.Objects;

/**
 * Base class for parameter that hold an embedded parameter set and optionally a value type like
 * boolean for the {@link OptionalEmbeddedParametersParameter}
 *
 * @author Robin Schmid <a href="https://github.com/robinschmid">https://github.com/robinschmid</a>
 */
public abstract class EmbeddedParametersParameter<ValueType, PARAMETERS extends ParameterSet, EditorComponent extends EmbeddedParametersComponent<ValueType>> extends
    AbstractParameter<ValueType, EditorComponent> implements
    EmbeddedParameterSet<PARAMETERS, ValueType> {

  protected PARAMETERS embeddedParameters;

  public EmbeddedParametersParameter(String name, String description, ValueType defaultVal,
      PARAMETERS embeddedParameters) {
    super(name, description, defaultVal);
    this.embeddedParameters = embeddedParameters;
  }

  @Override
  public PARAMETERS getEmbeddedParameters() {
    return embeddedParameters;
  }

  public void setEmbeddedParameters(PARAMETERS embeddedParameters) {
    this.embeddedParameters = embeddedParameters;
  }


  @Override
  public boolean checkValue(Collection<String> errorMessages) {
    return embeddedParameters.checkParameterValues(errorMessages);
  }

  @Override
  public void setValueFromComponent(EditorComponent component) {
    setValue(component.getValue());
    component.updateParametersFromComponent();
  }

  @Override
  public void setValueToComponent(EditorComponent component, ValueType newValue) {
    component.setValue(newValue);
    component.setParameterValuesToComponents();
  }

  @Override
  public boolean valueEquals(Parameter<?> that) {
    if (!(that instanceof EmbeddedParametersParameter thatOpt)) {
      return false;
    }

    if (!Objects.equals(getValue(), thatOpt.getValue())) {
      return false;
    }

    return ParameterUtils.equalValues(getEmbeddedParameters(), thatOpt.getEmbeddedParameters(),
        false, false);
  }

}
