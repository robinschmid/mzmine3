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

package io.github.mzmine.parameters.parametertypes.combowithinput;

import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.RTToleranceParameter;

public class RtLimitsFilterParameter extends
    ComboWithInputParameter<RtLimitsFilter, FeatureLimitOptions, RTToleranceParameter> {

  public RtLimitsFilterParameter(final RTTolerance rtTolerance) {
    this(new RTToleranceParameter("Retention time filter", """
            This parameter either limits the grouping to the RT limits of each feature OR
            sets a tolerance around the feature RT.""", rtTolerance),
        // options
        FeatureLimitOptions.values(), FeatureLimitOptions.USE_FEATURE_EDGES);
  }

  public RtLimitsFilterParameter(final RTToleranceParameter embedded, FeatureLimitOptions[] options,
      FeatureLimitOptions selected) {
    super(embedded, options, selected, FeatureLimitOptions.USE_TOLERANCE);
  }

  @Override
  public RtLimitsFilter getValue() {
    return new RtLimitsFilter(selectedChoice, getEmbeddedParameter().getValue());
  }

  @Override
  public void setValue(RtLimitsFilter value) {
    setSelectedOption(value.filter());
    getEmbeddedParameter().setValue(value.getEmbeddedValue());
  }

  @Override
  public RtLimitsFilterParameter cloneParameter() {
    var embeddedParameterClone = embeddedParameter.cloneParameter();
    return new RtLimitsFilterParameter(embeddedParameterClone,
        choices.toArray(FeatureLimitOptions[]::new), selectedChoice);
  }

}
