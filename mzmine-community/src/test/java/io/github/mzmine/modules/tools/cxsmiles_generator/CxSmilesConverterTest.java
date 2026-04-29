package io.github.mzmine.modules.tools.cxsmiles_generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mzmine.modules.tools.cxsmiles_generator.chemistry.CxSmilesConverter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openscience.cdk.CDKConstants;
import org.openscience.cdk.sgroup.Sgroup;
import org.openscience.cdk.sgroup.SgroupType;

class CxSmilesConverterTest {

  // 2-, 3-, and 4-chlorobiphenyl positional isomers — the biphenyl core is the MCS scaffold
  private static final List<String> CHLOROBIPHENYL_SMILES = List.of(
      "Clc1ccccc1-c2ccccc2",          // 2-chlorobiphenyl
      "Clc1cccc(-c2ccccc2)c1",        // 3-chlorobiphenyl
      "Clc1ccc(-c2ccccc2)cc1"         // 4-chlorobiphenyl
  );

  @Test
  void testCxSmilesContainsPositionalVariationLayer() throws Exception {
    CxSmilesResult result = CxSmilesConverter.convert(CHLOROBIPHENYL_SMILES);

    assertNotNull(result);
    // CxSMILES must contain the m: positional-variation layer
    assertTrue(result.cxSmiles().contains("|"),
        "CxSMILES must contain a CxSMILES extension block: " + result.cxSmiles());
    assertTrue(result.cxSmiles().contains("m:"),
        "CxSMILES must contain an m: positional-variation layer: " + result.cxSmiles());
  }

  @Test
  void testScaffoldIsCoreBiphenyl() throws Exception {
    CxSmilesResult result = CxSmilesConverter.convert(CHLOROBIPHENYL_SMILES);

    // biphenyl core = 12 carbons; plus a disconnected Cl-* fragment = 14 atoms total
    int atomCount = result.scaffoldMol().getAtomCount();
    assertEquals(14, atomCount,
        "Expected biphenyl (12 C) + disconnected Cl-* fragment (2 atoms) = 14 atoms");
  }

  @Test
  void testExactlyOneExtMulticenterSgroup() throws Exception {
    CxSmilesResult result = CxSmilesConverter.convert(CHLOROBIPHENYL_SMILES);

    List<Sgroup> sgroups = result.scaffoldMol().getProperty(CDKConstants.CTAB_SGROUPS);
    assertNotNull(sgroups, "CTAB_SGROUPS property must be set on the scaffold");
    assertEquals(1, sgroups.size(), "Expected exactly 1 Sgroup for the Cl positional variation");
    assertEquals(SgroupType.ExtMulticenter, sgroups.get(0).getType());
  }

  @Test
  void testCandidateAtomCountMatchesIsomerCount() throws Exception {
    CxSmilesResult result = CxSmilesConverter.convert(CHLOROBIPHENYL_SMILES);

    List<Sgroup> sgroups = result.scaffoldMol().getProperty(CDKConstants.CTAB_SGROUPS);
    Sgroup sg = sgroups.get(0);
    // Markush form: Sgroup atoms = {*} + all scaffold candidate atoms.
    long candidateCount = sg.getAtoms().stream()
        .filter(a -> !(a instanceof org.openscience.cdk.interfaces.IPseudoAtom))
        .count();
    assertEquals(3, candidateCount,
        "Expected 3 candidate scaffold attachment atoms for 3 chlorobiphenyl isomers");
  }

  @Test
  void testSubstituentIsPreservedInOutput() throws Exception {
    CxSmilesResult result = CxSmilesConverter.convert(CHLOROBIPHENYL_SMILES);

    // The Cl substituent must appear as a disconnected Cl-* fragment in the output
    assertTrue(result.cxSmiles().contains("Cl"),
        "CxSMILES must preserve the Cl substituent: " + result.cxSmiles());
    assertTrue(result.cxSmiles().contains("*"),
        "CxSMILES must contain the * pseudo-atom: " + result.cxSmiles());
  }

  @Test
  void testVariablePositionSummaryIsPopulated() throws Exception {
    CxSmilesResult result = CxSmilesConverter.convert(CHLOROBIPHENYL_SMILES);

    assertFalse(result.variablePositionSummary().isEmpty(),
        "Variable position summary must not be empty");
  }

  // 1-, 2-, and 4-methoxy ethers of 1,2,4-trihydroxybenzene (hydroxyhydroquinone).
  // Conceptually: a methyl group that may sit on any of the polyphenol's hydroxyl oxygens.
  // Expected scaffold: 1,2,4-trihydroxybenzene (6 ring C + 3 O = 9 atoms) with all 3 OHs
  // preserved; the methyl carbon is the variable substituent attaching to any of the 3 oxygens.
  private static final List<String> METHYL_POLYPHENOL_SMILES = List.of(
      "COc1c(O)cc(O)cc1",   // 1-methoxy-2,4-dihydroxybenzene
      "Oc1c(OC)cc(O)cc1",   // 2-methoxy-1,4-dihydroxybenzene
      "Oc1c(O)cc(OC)cc1"    // 4-methoxy-1,2-dihydroxybenzene
  );

  @Test
  void testMethylVariableOnPolyphenolHydroxyls() throws Exception {
    CxSmilesResult result = CxSmilesConverter.convert(METHYL_POLYPHENOL_SMILES);

    // Markush CxSMILES contract: must contain a positional-variation layer and a * pseudo-atom
    assertTrue(result.cxSmiles().contains("|m:"),
        "CxSMILES must contain an m: positional-variation layer: " + result.cxSmiles());
    assertTrue(result.cxSmiles().contains("*"),
        "CxSMILES must contain the * pseudo-atom: " + result.cxSmiles());

    // The 3 OHs of the polyphenol must be preserved in the scaffold (not collapsed away)
    int oxygenCount = 0;
    for (org.openscience.cdk.interfaces.IAtom a : result.scaffoldMol().atoms()) {
      if ("O".equals(a.getSymbol())) {
        oxygenCount++;
      }
    }
    assertEquals(3, oxygenCount,
        "Expected all 3 hydroxyl oxygens preserved in the scaffold");

    List<Sgroup> sgroups = result.scaffoldMol().getProperty(CDKConstants.CTAB_SGROUPS);
    assertNotNull(sgroups, "Sgroups must be set on the scaffold");
    assertEquals(1, sgroups.size(),
        "Expected exactly 1 ExtMulticenter Sgroup for the variable methyl");

    Sgroup sg = sgroups.get(0);
    assertEquals(SgroupType.ExtMulticenter, sg.getType());

    // 3 candidate hydroxyl-oxygen attachment positions for the variable methyl
    long candidateCount = sg.getAtoms().stream()
        .filter(a -> !(a instanceof org.openscience.cdk.interfaces.IPseudoAtom))
        .count();
    assertEquals(3, candidateCount,
        "Expected 3 candidate oxygen attachment positions for the methyl group");

    // All 3 candidates must be oxygens (the methyl varies between hydroxyl groups)
    long oxygenCandidates = sg.getAtoms().stream()
        .filter(a -> !(a instanceof org.openscience.cdk.interfaces.IPseudoAtom))
        .filter(a -> "O".equals(a.getSymbol()))
        .count();
    assertEquals(3, oxygenCandidates,
        "All 3 methyl-attachment candidates should be hydroxyl oxygens");
  }

  // 3 acetate ester isomers of a polyhydroxy flavone — the acetyl (-C(=O)CH3) sits on a
  // different hydroxyl oxygen of the phenyl ring in each isomer. The chromone part and 2 of the
  // phenyl OHs stay constant; one OH is acetylated at a varying position.
  private static final List<String> ACETYL_FLAVONE_SMILES = List.of(
      "C1=CC(=C(C=C1C2=CC(=O)C3=C(O2)C=C(C=C3)O)O)OC(=O)C",
      "C1=CC(=C(C=C1C2=CC(=O)C3=C(O2)C=C(C=C3)O)OC(=O)C)O",
      "C1=CC(=C(C=C1C2=CC(=O)C3=C(O2)C=C(C=C3)OC(=O)C)O)O"
  );

  @Test
  void testAcetylVariableOnFlavoneHydroxyls() throws Exception {
    CxSmilesResult result = CxSmilesConverter.convert(ACETYL_FLAVONE_SMILES);
    System.out.println("Acetyl flavone CxSMILES: " + result.cxSmiles());

    assertTrue(result.cxSmiles().contains("|m:"),
        "CxSMILES must contain an m: positional-variation layer: " + result.cxSmiles());
    assertTrue(result.cxSmiles().contains("*"),
        "CxSMILES must contain the * pseudo-atom: " + result.cxSmiles());

    List<Sgroup> sgroups = result.scaffoldMol().getProperty(CDKConstants.CTAB_SGROUPS);
    assertNotNull(sgroups, "Sgroups must be set on the scaffold");
    assertFalse(sgroups.isEmpty(), "Expected at least one Sgroup");

    Sgroup sg = sgroups.get(0);
    assertEquals(SgroupType.ExtMulticenter, sg.getType());

    // 3 candidate attachment positions for the acetyl group
    long candidateCount = sg.getAtoms().stream()
        .filter(a -> !(a instanceof org.openscience.cdk.interfaces.IPseudoAtom))
        .count();
    assertTrue(candidateCount >= 2,
        "Expected ≥2 candidate scaffold attachment atoms for the acetyl variation, got "
            + candidateCount);
  }

  @Test
  void testAcetylVariableExtendedWithDifferentScaffold() throws Exception {
    // Adds a 4th structure where the chromone's OH is missing AND the acetate sits at yet
    // another position on the phenyl ring. Tests how the algorithm generalizes when one input
    // diverges from the others (smaller MCS, more candidate positions, possibly multiple
    // R-group types).
    List<String> smilesList = List.of(
        "C1=CC(=C(C=C1C2=CC(=O)C3=C(O2)C=C(C=C3)O)O)OC(=O)C",
        "C1=CC(=C(C=C1C2=CC(=O)C3=C(O2)C=C(C=C3)O)OC(=O)C)O",
        "C1=CC(=C(C=C1C2=CC(=O)C3=C(O2)C=C(C=C3)OC(=O)C)O)O",
        "C1(OC(=O)C)=CC(=C(C=C1C2=CC(=O)C3=C(O2)C=C(C=C3))O)O"
    );
    CxSmilesResult result = CxSmilesConverter.convert(smilesList);
    System.out.println("Extended acetyl flavone CxSMILES: " + result.cxSmiles());
    System.out.println("Variable position summary: " + result.variablePositionSummary());

    assertTrue(result.cxSmiles().contains("|m:"),
        "CxSMILES must contain an m: positional-variation layer: " + result.cxSmiles());
    assertTrue(result.cxSmiles().contains("*"),
        "CxSMILES must contain the * pseudo-atom: " + result.cxSmiles());

    List<Sgroup> sgroups = result.scaffoldMol().getProperty(CDKConstants.CTAB_SGROUPS);
    assertNotNull(sgroups, "Sgroups must be set on the scaffold");
    assertFalse(sgroups.isEmpty(), "Expected at least one Sgroup");
  }

  @Test
  void testTwoIdenticalSmilesProducesNoVariablePositions() throws Exception {
    // When all inputs are identical, MCS = full molecule, no variable positions
    List<String> same = List.of("c1ccccc1", "c1ccccc1");
    CxSmilesResult result = CxSmilesConverter.convert(same);

    List<Sgroup> sgroups = result.scaffoldMol().getProperty(CDKConstants.CTAB_SGROUPS);
    assertTrue(sgroups == null || sgroups.isEmpty(),
        "No Sgroups expected when all input molecules are identical");
  }
}
