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

package io.github.mzmine.util.scans;

import io.github.mzmine.datamodel.MassSpectrum;
import java.util.logging.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ScanUtilsTest {

  private static final Logger logger = Logger.getLogger(ScanUtilsTest.class.getName());

  @Test
  public void testSpectralEntropy() {
    String filteredSpec = """
        56.96575546	45622.00781
        84.96044159	3834890
        102.9707794	487282.7188
        120.9812393	84239.8125
        121.0845566	25546.92578
        125.9865189	748793.375
        143.9969788	1184730.625
        149.0597229	30264.38086
        162.0074921	174033.6563
        167.0133057	44573.92578
        208.1329346	26131.29102""";

    String fullSpec = """
        55.98538208	4505.976563
        55.9875946	6591.681152
        56.96575546	45622.00781
        71.95267487	7936.068848
        74.15888977	3457.361328
        84.96044159	3834890
        102.9707794	487282.7188
        109.5409851	4085.818359
        120.0807953	8288.743164
        120.9812393	84239.8125
        121.0845566	25546.92578
        125.9865189	748793.375
        138.9915771	5330.115723
        141.6085968	3485.90918
        143.9969788	1184730.625
        149.0597229	30264.38086
        162.0074921	174033.6563
        167.0133057	44573.92578
        167.0895233	8389.011719
        185.0231018	4254.073242
        208.1329346	26131.29102""";

    MassSpectrum filtered = ScanUtils.parseTsvSpectrum(filteredSpec).get();
    MassSpectrum full = ScanUtils.parseTsvSpectrum(fullSpec).get();

    printEntropy(filtered, full);
    checkEntropy(full, 1.402,	0.461);
    checkEntropy(filtered, 1.346,	0.561);

    fullSpec = """
        50.32513809	3230.545898
        55.35963058	3503.615479
        55.9354248	47559.88672
        59.33499527	4169.597168
        61.75786591	4013.699707
        64.81539917	4595.391602
        68.98788452	3625.327148
        72.9380188	30601.33203
        111.7709503	4153.307129
        126.0739441	4444.541016
        144.8644409	4981.513672
        148.2593536	4188.614746
        162.8768616	8420.958008
        177.8644257	25465.11328
        179.8805695	79549.8125
        180.8721161	5189.466309
        180.8881683	179639.8125
        195.8753967	66559.15625
        197.891098	440869.5313
        198.8986816	33975.48438
        213.8858948	78871.85938
        215.9015503	339597.5625
        217.8600311	5369.084961
        220.9054871	5067.191895
        223.8704224	43914.22656
        227.8961792	4661.583008
        235.8698883	54845.51563
        238.9169006	4606.033691
        241.8808441	62671.95703
        253.8807678	851320
        276.8967896	324151.625""";

    filteredSpec = """
        55.9354248	47559.88672
        72.9380188	30601.33203
        162.8768616	8420.958008
        177.8644257	25465.11328
        179.8805695	79549.8125
        180.8881683	179639.8125
        195.8753967	66559.15625
        197.891098	440869.5313
        198.8986816	33975.48438
        213.8858948	78871.85938
        215.9015503	339597.5625
        223.8704224	43914.22656
        235.8698883	54845.51563
        241.8808441	62671.95703
        253.8807678	851320
        276.8967896	324151.625""";

    filtered = ScanUtils.parseTsvSpectrum(filteredSpec).get();
    full = ScanUtils.parseTsvSpectrum(fullSpec).get();
    printEntropy(filtered, full);
    checkEntropy(full, 2.267,0.660);
    checkEntropy(filtered, 2.140,	0.772);

    filteredSpec = """
        56.96569443	36375.72656
        228.1267242	15920.97461
        238.1115112	17369.11328
        246.1380157	47651.96094
        256.122406	36264.35156
        272.1173706	32860.45703
        274.1321716	78256.79688
        292.1435852	29232.42969
        300.111084	62071.89453
        318.1221008	274606.5
        323.1269836	16287.89258
        336.1321716	30487.39258
        341.1371765	24793.36328
        382.9781494	8289.367188""";

    fullSpec = """
        56.96569443	36375.72656
        56.96793365	4726.039551
        63.8182869	3201.786377
        66.75447845	3541.510742
        68.32141113	3141.167236
        73.27983093	3791.140381
        99.78142548	3366.70459
        109.5358887	3971.434326
        124.5405273	3822.974609
        143.038147	4287.906738
        159.0325775	3458.746338
        160.064209	5268.468262
        161.0484772	7566.280273
        176.0596008	3634.950928
        177.0435638	5406.192871
        199.9262543	3245.786621
        206.070755	7787.507813
        211.073822	3866.766602
        223.2901764	3136.630371
        224.0799103	4389.028809
        228.1267242	15920.97461
        238.1115112	17369.11328
        246.1380157	47651.96094
        254.1065521	4447.716797
        256.122406	36264.35156
        264.1499329	4654.557617
        272.1173706	32860.45703
        274.1321716	78256.79688
        281.390686	3202.61499
        282.1030884	4124.212402
        292.1435852	29232.42969
        300.111084	62071.89453
        310.2050476	4229.327148
        310.5082703	3372.403076
        318.1221008	274606.5
        323.1269836	16287.89258
        336.1321716	30487.39258
        341.1371765	24793.36328
        382.9781494	8289.367188
        383.0216064	7259.629883""";

    filtered = ScanUtils.parseTsvSpectrum(filteredSpec).get();
    full = ScanUtils.parseTsvSpectrum(fullSpec).get();
    printEntropy(filtered, full);
    checkEntropy(full, 2.695,	0.731);
    checkEntropy(filtered, 2.149,	0.814);
  }

  private static void checkEntropy(MassSpectrum spec, double entropy, double normEntropy) {
    final double specEntropy = ScanUtils.getSpectralEntropy(spec);
    final double specNormEntropy = ScanUtils.getNormalizedSpectralEntropy(spec);

    Assertions.assertEquals(entropy, specEntropy, 0.001);
    Assertions.assertEquals(normEntropy, specNormEntropy, 0.001);
  }
  private static void printEntropy(MassSpectrum filtered, MassSpectrum full) {
    final double filteredEntropy = ScanUtils.getSpectralEntropy(filtered);
    final double fullEntropy = ScanUtils.getSpectralEntropy(full);
    final double filteredNormEntropy = ScanUtils.getNormalizedSpectralEntropy(filtered);
    final double fullNormEntropy = ScanUtils.getNormalizedSpectralEntropy(full);

    logger.info("""
        
                 \tEntropy\tNorm. Entropy
        Full:    \t%.3f\t%.3f
        Filtered:\t%.3f\t%.3f""".formatted(fullEntropy, fullNormEntropy, filteredEntropy, filteredNormEntropy));

    Assertions.assertNotNull(filtered);
  }


}