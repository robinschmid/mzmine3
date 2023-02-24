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

import io.github.mzmine.parameters.OptionalParameterContainer;
import io.github.mzmine.parameters.ParameterSet;
import java.util.Collection;
import java.util.Objects;
import org.w3c.dom.Element;

/**
 * parameter with optional use of sub ParameterSet
 *
 * @author Robin Schmid <a href="https://github.com/robinschmid">https://github.com/robinschmid</a>
 */
public abstract class OptionalEmbeddedParametersParameter<PARAMETERS extends ParameterSet, EditorComponent extends OptionalEmbeddedParametersComponent> extends
    EmbeddedParametersParameter<Boolean, PARAMETERS, EditorComponent> implements
    OptionalParameterContainer {

  protected boolean value;

  public OptionalEmbeddedParametersParameter(String name, String description, boolean defaultVal,
      PARAMETERS embeddedParameters) {
    super(name, description, defaultVal, embeddedParameters);
  }

  @Override
  public boolean isSelected() {
    return value;
  }

  @Override
  public void setSelected(boolean state) {
    value = state;
  }

  @Override
  public Boolean getValue() {
    return value;
  }

  @Override
  public void setValue(Boolean value) {
    this.value = Objects.requireNonNullElse(value, false);
  }

  @Override
  public void setValueFromComponent(EditorComponent component) {
    this.value = component.isSelected();
    component.updateParametersFromComponent();
  }

  @Override
  public void setValueToComponent(EditorComponent component, Boolean newValue) {
    component.setValue(newValue != null && newValue);
    component.setParameterValuesToComponents();
  }

  @Override
  public void loadValueFromXML(Element xmlElement) {
    embeddedParameters.loadValuesFromXML(xmlElement);
    String selectedAttr = xmlElement.getAttribute("selected");
    this.value = Objects.requireNonNullElse(Boolean.valueOf(selectedAttr), false);
  }

  @Override
  public void saveValueToXML(Element xmlElement) {
    xmlElement.setAttribute("selected", String.valueOf(value));
    embeddedParameters.saveValuesToXML(xmlElement);
  }

  @Override
  public boolean checkValue(Collection<String> errorMessages) {
    if (!value) {
      return true;
    }
    return embeddedParameters.checkParameterValues(errorMessages);
  }

}
