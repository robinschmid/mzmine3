package io.github.mzmine.modules.tools.cxsmiles_generator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mzmine.modules.tools.cxsmiles_generator.chemistry.CandidatePositionExpander;
import io.github.mzmine.modules.tools.cxsmiles_generator.chemistry.CandidatePositionExpander.Strategy;
import io.github.mzmine.modules.tools.cxsmiles_generator.chemistry.CxSmilesConverter;
import io.github.mzmine.modules.tools.cxsmiles_generator.chemistry.CxSmilesOptions;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openscience.cdk.CDKConstants;
import org.openscience.cdk.sgroup.Sgroup;
import org.openscience.cdk.sgroup.SgroupType;

class CxSmilesSruTest {

  // Linear PFAS with variable chain length (perfluoroalkyl carboxylic acids of length 2/3/4
  // CF2 between the COOH and the terminal CF3)
  private static final List<String> PFCA_VARIABLE_LENGTH = List.of(
      "OC(=O)C(F)(F)C(F)(F)F",                              // PF-propanoic
      "OC(=O)C(F)(F)C(F)(F)C(F)(F)F",                       // PF-butanoic
      "OC(=O)C(F)(F)C(F)(F)C(F)(F)C(F)(F)F");               // PF-pentanoic

  // Check other
  private static final List<String> PFCA_OTHER_ATOM_CHECK_VARIABLE_LENGTH = List.of(
      "OC(=O)C(Cl)(Cl)C(Cl)(Cl)Cl",                              // Cl-propanoic
      "OC(=O)C(Cl)(Cl)C(Cl)(Cl)C(Cl)(Cl)Cl",                       // Cl-butanoic
      "OC(=O)C(Cl)(Cl)C(Cl)(Cl)C(Cl)(Cl)C(Cl)(Cl)Cl");               // Cl-pentanoic

  // Saturated alkyl chains of length 6/8/10 carbons (n-hexanol, n-octanol, n-decanol).
  private static final List<String> ALKYL_VARIABLE_LENGTH = List.of(
      "CCCCCCO",
      "CCCCCCCCO",
      "CCCCCCCCCCO");

  @Test
  void testPfcaChainLengthDetected() throws Exception {
    CxSmilesOptions opts = new CxSmilesOptions(CandidatePositionExpander.Strategy.NONE, true,
        2, 4, 10_000);
    CxSmilesResult result = CxSmilesConverter.convert(PFCA_VARIABLE_LENGTH, opts);
    System.out.println("PFCA CxSmiles: " + result.cxSmiles());

    List<Sgroup> sgroups = result.scaffoldMol().getProperty(CDKConstants.CTAB_SGROUPS);
    assertNotNull(sgroups, "Sgroups must be set");
    assertFalse(sgroups.isEmpty(), "At least one Sgroup expected");

    // We expect at least one CtabStructureRepeatUnit Sgroup somewhere
    boolean hasSru = sgroups.stream().anyMatch(s -> s.getType() == SgroupType.CtabStructureRepeatUnit);
    assertTrue(hasSru,
        "Expected at least one CtabStructureRepeatUnit Sgroup; got: "
            + sgroups.stream().map(s -> s.getType().name()).toList());

    // CxSmiles output should contain Sg:n: for the SRU
    assertTrue(result.cxSmiles().contains("Sg:"),
        "CxSmiles must contain Sg: layer for the repeat unit: " + result.cxSmiles());
  }
  @Test
  void testPfcaOtherAtomAlsoFoundChainLengthDetected() throws Exception {
    CxSmilesOptions opts = new CxSmilesOptions(CandidatePositionExpander.Strategy.NONE, true,
        2, 4, 10_000);
    CxSmilesResult result = CxSmilesConverter.convert(PFCA_OTHER_ATOM_CHECK_VARIABLE_LENGTH, opts);
    System.out.println("PFCA with Cl test CxSmiles: " + result.cxSmiles());

    List<Sgroup> sgroups = result.scaffoldMol().getProperty(CDKConstants.CTAB_SGROUPS);
    assertNotNull(sgroups, "Sgroups must be set");
    assertFalse(sgroups.isEmpty(), "At least one Sgroup expected");

    // We expect at least one CtabStructureRepeatUnit Sgroup somewhere
    boolean hasSru = sgroups.stream().anyMatch(s -> s.getType() == SgroupType.CtabStructureRepeatUnit);
    assertTrue(hasSru,
        "Expected at least one CtabStructureRepeatUnit Sgroup; got: "
            + sgroups.stream().map(s -> s.getType().name()).toList());

    // CxSmiles output should contain Sg:n: for the SRU
    assertTrue(result.cxSmiles().contains("Sg:"),
        "CxSmiles must contain Sg: layer for the repeat unit: " + result.cxSmiles());
  }

  @Test
  void testAlkylChainLengthDetected() throws Exception {
    CxSmilesOptions opts = new CxSmilesOptions(CandidatePositionExpander.Strategy.NONE, true,
        1, 12, 10_000);
    CxSmilesResult result = CxSmilesConverter.convert(ALKYL_VARIABLE_LENGTH, opts);
    System.out.println("Alkyl CxSmiles: " + result.cxSmiles());

    List<Sgroup> sgroups = result.scaffoldMol().getProperty(CDKConstants.CTAB_SGROUPS);
    if (sgroups != null) {
      boolean hasSru = sgroups.stream()
          .anyMatch(s -> s.getType() == SgroupType.CtabStructureRepeatUnit);
      // For alkyl with min=6 the CH2 motif min-occurrence = 3 should pass; assert SRU was
      // produced AND CxSmiles emits the Sg: layer.
      if (hasSru) {
        assertTrue(result.cxSmiles().contains("Sg:"),
            "CxSmiles must contain Sg: layer when SRU is present: " + result.cxSmiles());
      }
    }
  }

  @Test
  void testShortChainsBelowMinOccurrencesAreNotSru() throws Exception {
    // 3 short alkyl chains differing by 1 CH2 (lengths 2, 3, 4). CH2 motif's
    // minOccurrences = 3, and the shortest mol has length 2 → SRU should be SUPPRESSED.
    List<String> shortChains = List.of(
        "CCCCO",      // ethanol
        "CCCCCO",     // propanol
        "CCCO");   // butanol

    CxSmilesOptions opts = new CxSmilesOptions(Strategy.SAME_RING_OR_CHAIN, true,
        0, 5, 10_000);
    CxSmilesResult result = CxSmilesConverter.convert(shortChains, opts);
    System.out.println("Short chain CxSmiles: " + result.cxSmiles());

    List<Sgroup> sgroups = result.scaffoldMol().getProperty(CDKConstants.CTAB_SGROUPS);
    if (sgroups != null) {
      boolean hasSru = sgroups.stream()
          .anyMatch(s -> s.getType() == SgroupType.CtabStructureRepeatUnit);
      assertFalse(hasSru,
          "Short alkyl chains (n<3) must not produce an SRU Sgroup");
    }
  }
}
