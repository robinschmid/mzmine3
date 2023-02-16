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

import io.github.mzmine.parameters.parametertypes.IntegerParameter;
import io.github.mzmine.parameters.parametertypes.combowithinput.MsLevelFilter.Options;

public class MsLevelFilterParameter extends
    ComboWithInputParameter<MsLevelFilter, MsLevelFilter.Options, IntegerParameter> {

  public MsLevelFilterParameter() {
    this(new IntegerParameter("MS level filter",
        "Only select MS of defined levels. MS1, MS2, MSn (all >1), or specific levels entered as number.",
        3, true, 1, 100000), Options.values(), Options.ALL);
  }

  public MsLevelFilterParameter(final IntegerParameter embedded, Options[] options,
      Options selected) {
    super(embedded, options, selected, Options.SPECIFIC_LEVEL);
  }

  @Override
  public MsLevelFilter getValue() {
    return new MsLevelFilter(selectedChoice, getEmbeddedParameter().getValue());
  }

  @Override
  public void setValue(MsLevelFilter value) {
    setSelectedOption(value.filter());
    getEmbeddedParameter().setValue(value.getEmbeddedValue());
  }

  @Override
  public MsLevelFilterParameter cloneParameter() {
    var embeddedParameterClone = embeddedParameter.cloneParameter();
    return new MsLevelFilterParameter(embeddedParameterClone, choices.toArray(Options[]::new),
        selectedChoice);
  }

}
