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
import io.github.mzmine.parameters.Parameter;
import io.github.mzmine.parameters.ParameterContainer;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.ParameterUtils;
import io.github.mzmine.parameters.UserParameter;
import io.github.mzmine.parameters.parametertypes.EmbeddedParameterSet;
import java.util.Collection;
import java.util.Objects;
import javafx.scene.layout.Priority;
import org.w3c.dom.Element;

/**
 * This adds an accordion to the parameter pane with additional parameters. Those parameters should
 * only be used if the value (check box) is selected. One use case is the advanced batch mode.
 *
 * @author Robin Schmid <a href="https://github.com/robinschmid">https://github.com/robinschmid</a>
 */
public class AdvancedParametersParameter<T extends ParameterSet> implements
    UserParameter<Boolean, AdvancedParametersComponent>, OptionalParameterContainer,
    ParameterContainer, EmbeddedParameterSet<T, Boolean> {

  private final String name;
  private final String description;
  private T embeddedParameters;
  private boolean value;

  public AdvancedParametersParameter(String name, String description, T embeddedParameters,
      boolean defaultVal) {
    this.name = name;
    this.description = description;
    this.embeddedParameters = embeddedParameters;
    value = defaultVal;
  }

  public AdvancedParametersParameter(String name, String description, T embeddedParameters) {
    this(name, description, embeddedParameters, false);
  }

  public AdvancedParametersParameter(T embeddedParameters) {
    this("Advanced", "Advanced parameters", embeddedParameters, false);
  }

  public T getEmbeddedParameters() {
    return embeddedParameters;
  }

  public void setEmbeddedParameters(T embeddedParameters) {
    this.embeddedParameters = embeddedParameters;
  }

  @Override
  public Priority getComponentVgrowPriority() {
    return Priority.SOMETIMES;
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
  public AdvancedParametersComponent createEditingComponent() {
    return new AdvancedParametersComponent(embeddedParameters, name, value);
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
  public AdvancedParametersParameter<T> cloneParameter() {
    final T embeddedParametersClone = (T) embeddedParameters.cloneParameterSet();
    final AdvancedParametersParameter<T> copy = new AdvancedParametersParameter<>(name, description,
        embeddedParametersClone);
    copy.setValue(this.getValue());
    return copy;
  }

  @Override
  public void setValueFromComponent(AdvancedParametersComponent component) {
    this.value = component.isSelected();
    component.getValue();
  }

  @Override
  public void setValueToComponent(AdvancedParametersComponent component, Boolean newValue) {
    component.setSelected(Objects.requireNonNullElse(newValue, false));
    component.setValue(embeddedParameters);
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

  @Override
  public void setSkipSensitiveParameters(boolean skipSensitiveParameters) {
    // delegate skipSensitiveParameters to embedded ParameterSet
    embeddedParameters.setSkipSensitiveParameters(skipSensitiveParameters);
  }

  @Override
  public boolean valueEquals(Parameter<?> that) {
    if (!(that instanceof AdvancedParametersParameter thatOpt)) {
      return false;
    }

    if (value != thatOpt.getValue()) {
      return false;
    }

    return ParameterUtils.equalValues(getEmbeddedParameters(), thatOpt.getEmbeddedParameters(),
        false, false);
  }

  @Override
  public boolean isSelected() {
    return value;
  }
}
