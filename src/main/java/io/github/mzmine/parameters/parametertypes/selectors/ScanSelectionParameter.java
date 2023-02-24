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

package io.github.mzmine.parameters.parametertypes.selectors;

import io.github.mzmine.parameters.Parameter;
import io.github.mzmine.parameters.parametertypes.submodules.EmbeddedParametersParameter;
import io.github.mzmine.parameters.parametertypes.submodules.OptionalModuleComponent;
import io.github.mzmine.parameters.parametertypes.submodules.ParameterSetComponent;
import java.util.Collection;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;

public class ScanSelectionParameter extends
    EmbeddedParametersParameter<ScanSelection, ScanSelectionFiltersParameters, OptionalModuleComponent<ScanSelection>> {

  private boolean active = false;

  public ScanSelectionParameter() {
    this(null);
  }

  public ScanSelectionParameter(ScanSelection defaultValue) {
    super("Scan filters",
        "Select scans that should be included. Needs to be activated to actually filter.",
        defaultValue, new ScanSelectionFiltersParameters(defaultValue));
  }

  private <T> void setParameterIfNotNull(Parameter<T> parameter, T value) {
    if (value != null) {
      getEmbeddedParameters().setParameter(parameter, value);
    }
  }
//
//  @Override
//  public ParameterSetComponent createEditingComponent() {
//    var component = super.createEditingComponent();
//    final Text filterLabel = new Text("All");
//    var clearBtn = new Button("Clear");
//    component.getChildren().addAll(clearBtn, filterLabel);
//
//    clearBtn.setOnAction(event -> clearFilters(component));
//
//    // TODO fire change event and update text
//    return component;
//  }

  public void clearFilters() {
    clearFilters(null);
  }

  public void clearFilters(@Nullable final ParameterSetComponent component) {
    setValue(null);
    if (component != null) {
      component.setValue(getEmbeddedParameters());
    }
  }

  /**
   * @return ScanSelection from the current dataset
   */
  public @NotNull ScanSelection createFilter() {
    return active ? getEmbeddedParameters().createFilter() : ScanSelection.ALL;
  }

  public void setValue(final boolean active, final ScanSelection value) {
    this.active = active;
    setValue(value);
  }

  @Override
  public ScanSelection getValue() {
    return createFilter();
  }

  @Override
  public void setValue(final ScanSelection newValue) {
    getEmbeddedParameters().setFilter(newValue);
  }

  @Override
  public void setValueFromComponent(OptionalModuleComponent component) {
    this.active = component.isSelected();
    component.updateParametersFromComponent();
  }

  @Override
  public void setValueToComponent(OptionalModuleComponent component, ScanSelection newValue) {
    component.setValue(active);
    component.setParameterValuesToComponents();
  }

  @Override
  public ScanSelectionParameter cloneParameter() {
    return new ScanSelectionParameter(getValue());
  }


  @Override
  public OptionalModuleComponent<ScanSelection> createEditingComponent() {
    return new OptionalModuleComponent<>(embeddedParameters, active);
  }


  @Override
  public void loadValueFromXML(Element xmlElement) {
    embeddedParameters.loadValuesFromXML(xmlElement);
    String selectedAttr = xmlElement.getAttribute("selected");
    this.active = Objects.requireNonNullElse(Boolean.valueOf(selectedAttr), false);
  }

  @Override
  public void saveValueToXML(Element xmlElement) {
    xmlElement.setAttribute("selected", String.valueOf(active));
    embeddedParameters.saveValuesToXML(xmlElement);
  }

  @Override
  public boolean checkValue(Collection<String> errorMessages) {
    if (!active) {
      return true;
    }
    return embeddedParameters.checkParameterValues(errorMessages);
  }
}
