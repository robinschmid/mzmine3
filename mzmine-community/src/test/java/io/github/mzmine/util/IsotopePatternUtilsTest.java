/*
 * Copyright (c) 2004-2026 The mzmine Development Team
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

package io.github.mzmine.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class IsotopePatternUtilsTest {

  @Test
  void carbonIsotopePattern() {
    final double[] c1 = IsotopePatternUtils.carbonIsotopePattern(1);
    final double[] c10 = IsotopePatternUtils.carbonIsotopePattern(10);
    final double[] c50 = IsotopePatternUtils.carbonIsotopePattern(50);
    final double[] c100 = IsotopePatternUtils.carbonIsotopePattern(100);

    assertNotNull(c1);
    assertEquals(1, c1[0], 0.0000001);
    assertEquals(0.010815728292732234, c1[1], 0.0000001);

    assertEquals(1, c10[0], 0.0000001);
    assertEquals(0.10815728292732234, c10[1], 0.0000001);
    assertEquals(0.005264099032599384, c10[2], 0.0000001);

    assertEquals(1, c50[0], 0.0000001);
    assertEquals(0.5407864146366117, c50[1], 0.0000001);
    assertEquals(0.14330047366520549, c50[2], 0.0000001);

    assertEquals(0.9245794392523367, c100[0], 0.0000001);
    assertEquals(1, c100[1], 0.0000001);
    assertEquals(0.5353785504902457, c100[2], 0.0000001);
  }

}