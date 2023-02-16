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

import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.parameters.parametertypes.combowithinput.MsLevelFilter.Options;

/**
 * @param filter
 * @param specificLevel only used for the option SPECIFIC_FILTER
 */
public record MsLevelFilter(Options filter, int specificLevel) implements
    ComboWithInputValue<Options, Integer> {

  @Override
  public Options getSelectedOption() {
    return filter;
  }

  @Override
  public Integer getEmbeddedValue() {
    return specificLevel;
  }

  /**
   * @param scan the tested scan
   * @return true if scan matches filter
   */
  public boolean accept(Scan scan) {
    return switch (filter) {
      case ALL -> true;
      case MS1 -> scan.getMSLevel() == 1;
      case MS2 -> scan.getMSLevel() == 2;
      case MSn -> scan.getMSLevel() > 1;
      case SPECIFIC_LEVEL -> scan.getMSLevel() == specificLevel;
    };
  }

  enum Options {
    ALL, MS1, MSn, MS2, SPECIFIC_LEVEL;

    @Override
    public String toString() {
      return switch (this) {
        case MS2, MS1, MSn -> toString();
        case ALL -> "All MS levels";
        case SPECIFIC_LEVEL -> "Specific MS level";
      };
    }
  }

}
