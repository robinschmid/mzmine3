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

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.MassSpectrumType;
import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.parameters.Parameter;
import io.github.mzmine.parameters.parametertypes.ParameterSetComponent;
import io.github.mzmine.parameters.parametertypes.ParameterSetParameter;
import io.github.mzmine.parameters.parametertypes.combowithinput.MsLevelFilter;
import io.github.mzmine.util.RangeUtils;
import io.github.mzmine.util.XMLUtils;
import java.util.Collection;
import javafx.scene.control.Button;
import javafx.scene.text.Text;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class ScanSelectionParameter extends ParameterSetParameter {

  public ScanSelectionParameter() {
    this(null);
  }

  public ScanSelectionParameter(ScanSelection defaultValue) {
    this("Scans", "Select scans that should be included.", defaultValue);
  }

  public ScanSelectionParameter(String name, String description, ScanSelection defaultValue) {
    super(name, description, new ScanSelectionFiltersParameters(defaultValue));
  }

  @Override
  public boolean checkValue(Collection<String> errorMessages) {
    return true;
  }

  @Override
  public void loadValueFromXML(Element xmlElement) {
    // changed this parameter to a ParameterSetParameter
    // before all parameters were manually saved
    // Load parent first for new versions and then try to see if old version was saved below
    clearFilters();
    super.loadValueFromXML(xmlElement);

    // try to load values from old format
    Range<Integer> scanNumberRange = null;
    Integer baseFilteringInteger = null;
    Range<Double> scanMobilityRange = null;
    Range<Float> scanRTRange = null;
    PolarityType polarity = null;
    MassSpectrumType spectrumType = null;
    Integer msLevel = null;
    String scanDefinition = null;

    scanNumberRange = XMLUtils.parseIntegerRange(xmlElement, "scan_numbers");
    scanMobilityRange = XMLUtils.parseDoubleRange(xmlElement, "mobility");
    scanRTRange = XMLUtils.parseFloatRange(xmlElement, "retention_time");

    NodeList items = xmlElement.getElementsByTagName("ms_level");
    for (int i = 0; i < items.getLength(); i++) {
      msLevel = Integer.valueOf(items.item(i).getTextContent());
    }

    items = xmlElement.getElementsByTagName("polarity");
    for (int i = 0; i < items.getLength(); i++) {
      try {
        polarity = PolarityType.valueOf(items.item(i).getTextContent());
      } catch (Exception e) {
        polarity = PolarityType.fromSingleChar(items.item(i).getTextContent());
      }
    }

    items = xmlElement.getElementsByTagName("spectrum_type");
    for (int i = 0; i < items.getLength(); i++) {
      spectrumType = MassSpectrumType.valueOf(items.item(i).getTextContent());
    }

    items = xmlElement.getElementsByTagName("scan_definition");
    for (int i = 0; i < items.getLength(); i++) {
      scanDefinition = items.item(i).getTextContent();
    }

    // apply if not null
    setParameterIfNotNull(ScanSelectionFiltersParameters.scanNumParameter, scanNumberRange);
    setParameterIfNotNull(ScanSelectionFiltersParameters.baseFilteringIntegerParameter,
        baseFilteringInteger);
    setParameterIfNotNull(ScanSelectionFiltersParameters.rtParameter,
        RangeUtils.toDoubleRange(scanRTRange));
    setParameterIfNotNull(ScanSelectionFiltersParameters.mobilityParameter, scanMobilityRange);
    setParameterIfNotNull(ScanSelectionFiltersParameters.polarityParameter, polarity);
    setParameterIfNotNull(ScanSelectionFiltersParameters.spectrumTypeParameter, spectrumType);
    setParameterIfNotNull(ScanSelectionFiltersParameters.scanDefinitionParameter, scanDefinition);
    setParameterIfNotNull(ScanSelectionFiltersParameters.msLevelParameter,
        MsLevelFilter.of(msLevel));

  }

  private <T> void setParameterIfNotNull(Parameter<T> parameter, T value) {
    if (value != null) {
      getEmbeddedParameters().setParameter(parameter, value);
    }
  }

  @Override
  public ParameterSetComponent createEditingComponent() {
    var component = super.createEditingComponent();
    final Text filterLabel = new Text("All");
    var clearBtn = new Button("Clear");
    component.getChildren().addAll(clearBtn, filterLabel);

    clearBtn.setOnAction(event -> clearFilters(component));

    // TODO fire change event and update text
    return component;
  }

  public void clearFilters() {
    clearFilters(null);
  }

  public void clearFilters(@Nullable final ParameterSetComponent component) {
    setFilter(null);
    if (component != null) {
      component.setValue(getEmbeddedParameters());
    }
  }

  public void setFilter(@Nullable ScanSelection selection) {
    ((ScanSelectionFiltersParameters) getEmbeddedParameters()).setFilter(selection);
  }

}
