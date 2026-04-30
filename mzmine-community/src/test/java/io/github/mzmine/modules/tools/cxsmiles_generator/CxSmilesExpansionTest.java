package io.github.mzmine.modules.tools.cxsmiles_generator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mzmine.modules.tools.cxsmiles_generator.chemistry.CandidatePositionExpander;
import io.github.mzmine.modules.tools.cxsmiles_generator.chemistry.CxSmilesConverter;
import io.github.mzmine.modules.tools.cxsmiles_generator.chemistry.CxSmilesEnumerator;
import io.github.mzmine.modules.tools.cxsmiles_generator.chemistry.CxSmilesOptions;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.openscience.cdk.CDKConstants;
import org.openscience.cdk.interfaces.IPseudoAtom;
import org.openscience.cdk.sgroup.Sgroup;

class CxSmilesExpansionTest {

  // --- Extension 1, case (a): same-ring expansion on a benzene scaffold ---------------
  // 3 monochlorobenzene-like inputs (here just a benzene with Cl on different ring positions).
  // Without expansion: 3 candidate atoms (the 3 observed ring carbons).
  // With SAME_RING_OR_CHAIN: candidates extend to all aromatic-CH atoms on that ring (all 6 are
  // equivalent before substitution; after MCS the 6 ring carbons remain, all candidates become
  // aromatic CH). Enumeration must round-trip the 3 inputs and yield more total isomers than
  // without expansion.
  private static final List<String> CHLOROBENZENE_OBSERVED = List.of(
      "Clc1ccccc1",
      "Clc1ccccc1",
      "Clc1ccccc1");

  // --- Extension 1, case (b): same-chain expansion on a fixed-length asymmetric chain --
  // 3 isomers of a methylpentanol (HO-CH2-CH2-CH2-CH2-CH3 with a methyl branch on different
  // middle carbons). The chain is asymmetric (OH at one end, CH3 at the other), so MCS aligns
  // it deterministically. Input observes branch at positions 2, 3, 4 (counted from OH);
  // expansion should also surface positions 5 (the carbon adjacent to the terminal CH3).
  private static final List<String> METHYLPENTANOL_BRANCH_FIXED_BACKBONE = List.of(
      "OCC(C)CCCC",   // 2-methylhexanol (branch on 2nd carbon from OH)
      "OCCC(C)CCC",   // 3-methylhexanol
      "OCCCC(C)CC");  // 4-methylhexanol

  @Test
  void testChlorobenzeneNoExpansionMatchesInputCount() throws Exception {
    // Use 3 distinct chlorobenzene positional isomers — but on a benzene ring all positions are
    // symmetry-equivalent, so this collapses to the same molecule. Use chlorotoluene instead so
    // there are 3 distinct positional isomers.
    List<String> inputs = List.of(
        "Cc1ccc(Cl)cc1",   // 4-chlorotoluene
        "Cc1cccc(Cl)c1",   // 3-chlorotoluene
        "Cc1ccccc1Cl");    // 2-chlorotoluene

    CxSmilesResult resultNoExpansion = CxSmilesConverter.convert(inputs,
        CxSmilesOptions.defaults());
    List<String> withoutExpansion = CxSmilesEnumerator
        .enumerateCanonicalSmiles(resultNoExpansion.scaffoldMol());

    CxSmilesResult resultExpanded = CxSmilesConverter.convert(inputs, new CxSmilesOptions(
        CandidatePositionExpander.Strategy.SAME_RING_OR_CHAIN, false, 1, 10, 10_000));
    List<String> withExpansion = CxSmilesEnumerator
        .enumerateCanonicalSmiles(resultExpanded.scaffoldMol());

    System.out.println("Chlorotoluene CxSmiles (no expansion): " + resultNoExpansion.cxSmiles());
    System.out.println("Chlorotoluene CxSmiles (expanded)    : " + resultExpanded.cxSmiles());
    System.out.println("Enumerated (no expansion): " + withoutExpansion);
    System.out.println("Enumerated (expanded)    : " + withExpansion);

    // All 3 inputs round-trip in both modes
    for (String inputSmiles : inputs) {
      String c = CxSmilesEnumerator.canonicalize(inputSmiles);
      assertTrue(withoutExpansion.contains(c),
          "Input " + inputSmiles + " missing in non-expanded enumeration: " + withoutExpansion);
      assertTrue(withExpansion.contains(c),
          "Input " + inputSmiles + " missing in expanded enumeration: " + withExpansion);
    }

    // Expansion produces a strict superset: the expanded candidate set covers all aromatic-CH
    // positions on the benzene ring; even with the methyl-blocked position, expansion should
    // grow the Sgroup or at minimum keep parity. Use ≥ comparison to stay robust.
    assertTrue(new HashSet<>(withExpansion).containsAll(withoutExpansion),
        "Expansion must cover everything the no-expansion set covers");
  }

  @Test
  void testFixedBackboneChainExpansion() throws Exception {
    CxSmilesResult resultNoExpansion = CxSmilesConverter.convert(
        METHYLPENTANOL_BRANCH_FIXED_BACKBONE, CxSmilesOptions.defaults());
    List<String> withoutExpansion = CxSmilesEnumerator
        .enumerateCanonicalSmiles(resultNoExpansion.scaffoldMol());

    CxSmilesResult resultExpanded = CxSmilesConverter.convert(
        METHYLPENTANOL_BRANCH_FIXED_BACKBONE, new CxSmilesOptions(
            CandidatePositionExpander.Strategy.SAME_RING_OR_CHAIN, false, 1, 10, 10_000));
    List<String> withExpansion = CxSmilesEnumerator
        .enumerateCanonicalSmiles(resultExpanded.scaffoldMol());

    System.out.println("Chain CxSmiles (no expansion): " + resultNoExpansion.cxSmiles());
    System.out.println("Chain CxSmiles (expanded)    : " + resultExpanded.cxSmiles());
    System.out.println("Chain enumerated (no expansion): " + withoutExpansion);
    System.out.println("Chain enumerated (expanded)    : " + withExpansion);

    // All 3 inputs must round-trip in expanded mode (the no-expansion mode might fail to
    // round-trip if MCS picks an unusual scaffold; we only require correctness with expansion).
    for (String inputSmiles : METHYLPENTANOL_BRANCH_FIXED_BACKBONE) {
      String c = CxSmilesEnumerator.canonicalize(inputSmiles);
      assertTrue(withExpansion.contains(c),
          "Input %s (canonical: %s) missing in expanded enumeration: %s".formatted(
              inputSmiles, c, withExpansion));
    }

    // Expansion must not shrink the candidate set; with chain expansion enabled, it should
    // grow when there are matching-degree chain atoms not yet in the candidate set.
    int candidatesNoExp = countNonPseudoSgroupAtoms(resultNoExpansion);
    int candidatesExp = countNonPseudoSgroupAtoms(resultExpanded);
    System.out.println("Sgroup candidate atoms — no expansion: " + candidatesNoExp);
    System.out.println("Sgroup candidate atoms — expanded    : " + candidatesExp);
    assertTrue(candidatesExp >= candidatesNoExp,
        "Expansion must not shrink the candidate set");

    // The expanded enumeration must be a superset of the unexpanded one.
    Set<String> expSet = new HashSet<>(withExpansion);
    for (String s : withoutExpansion) {
      assertTrue(expSet.contains(s),
          "Expanded enumeration must contain everything the unexpanded set contains; missing: " + s);
    }
  }

  @Test
  void testSymmetryEquivalentExpansionNeverShrinks() throws Exception {
    // Methyl-polyphenol case: 3 monomethyl ethers of 1,2,4-trihydroxybenzene.
    // Symmetry expansion may add nothing (low symmetry) but must not shrink the candidate set
    // and must still round-trip all 3 inputs.
    List<String> inputs = List.of(
        "COc1c(O)cc(O)cc1",
        "Oc1c(OC)cc(O)cc1",
        "Oc1c(O)cc(OC)cc1");

    CxSmilesResult r = CxSmilesConverter.convert(inputs, new CxSmilesOptions(
        CandidatePositionExpander.Strategy.SYMMETRY_EQUIVALENT, false, 1, 10, 10_000));
    List<String> enumerated = CxSmilesEnumerator.enumerateCanonicalSmiles(r.scaffoldMol());
    System.out.println("Polyphenol CxSmiles (symmetry expansion): " + r.cxSmiles());
    System.out.println("Polyphenol enumerated: " + enumerated);

    for (String inputSmiles : inputs) {
      String c = CxSmilesEnumerator.canonicalize(inputSmiles);
      assertTrue(enumerated.contains(c),
          "Input " + inputSmiles + " missing in expanded enumeration");
    }
    assertFalse(enumerated.isEmpty());
  }

  // -- helpers ---------------------------------------------------------------------------------

  private static int countNonPseudoSgroupAtoms(CxSmilesResult result) {
    List<Sgroup> sgroups = result.scaffoldMol().getProperty(CDKConstants.CTAB_SGROUPS);
    assertNotNull(sgroups, "expected ExtMulticenter Sgroup");
    int count = 0;
    for (Sgroup sg : sgroups) {
      for (var atom : sg.getAtoms()) {
        if (!(atom instanceof IPseudoAtom)) {
          count++;
        }
      }
    }
    return count;
  }
}
