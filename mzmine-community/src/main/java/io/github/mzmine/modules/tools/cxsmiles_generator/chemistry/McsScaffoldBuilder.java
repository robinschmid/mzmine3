package io.github.mzmine.modules.tools.cxsmiles_generator.chemistry;

import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.openscience.cdk.aromaticity.Aromaticity;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.isomorphism.UniversalIsomorphismTester;
import org.openscience.cdk.tools.manipulator.AtomContainerManipulator;

/**
 * Reduces a list of molecules to their maximum common substructure scaffold via iterative pairwise
 * MCS using CDK's {@link UniversalIsomorphismTester}.
 *
 * <p>The returned scaffold contains atom object references from {@code mols.get(0)}. That identity
 * is exploited by {@link MarkushSgroupBuilder} to locate attachment sites.</p>
 */
public class McsScaffoldBuilder {

  private static final Logger logger = Logger.getLogger(McsScaffoldBuilder.class.getName());

  private McsScaffoldBuilder() {
  }

  /**
   * @param mols at least 2 hydrogen-suppressed molecules (all from the same CDK builder)
   * @return largest maximum common substructure across all molecules
   * @throws Exception if no common scaffold exists between any pair, or CDK throws
   */
  @NotNull
  public static IAtomContainer build(@NotNull List<IAtomContainer> mols) throws Exception {
    if (mols.size() < 2) {
      throw new IllegalArgumentException("At least 2 molecules are required for MCS computation.");
    }

    UniversalIsomorphismTester uit = new UniversalIsomorphismTester();
    Aromaticity aromaticity = Aromaticity.cdkLegacy();
    IAtomContainer scaffold = mols.get(0);

    // Comparator: prefer larger overlaps; on a tie, prefer the one with more heteroatoms.
    // This biases the MCS toward subgraphs that retain oxygens/nitrogens at fixed positions,
    // which produces a more meaningful Markush scaffold for positional isomers
    // (e.g. polyphenol with all OHs preserved, methyl as the variable substituent).
    final Comparator<IAtomContainer> overlapPreference = Comparator
        .comparingInt(IAtomContainer::getAtomCount)
        .thenComparingLong(McsScaffoldBuilder::heteroatomCount);

    for (int i = 1; i < mols.size(); i++) {
      final IAtomContainer next = mols.get(i);
      // UIT.getOverlaps is order-sensitive — try both directions and keep the best overlap
      // so we don't settle on a sub-optimal local MCS due to atom ordering.
      final List<IAtomContainer> overlapsAB = uit.getOverlaps(scaffold, next);
      final List<IAtomContainer> overlapsBA = uit.getOverlaps(next, scaffold);

      IAtomContainer bestAB = overlapsAB.stream().max(overlapPreference).orElse(null);
      IAtomContainer bestBA = overlapsBA.stream().max(overlapPreference).orElse(null);

      if (bestAB == null && bestBA == null) {
        throw new IllegalArgumentException(
            "Molecules 1 and %d share no common scaffold.".formatted(i + 1));
      }
      // Prefer overlaps from (scaffold, next) — those keep atom references from the running
      // scaffold, which the Markush builder relies on. Fall back to the reversed result only
      // if it is strictly preferred.
      if (bestAB == null) {
        scaffold = bestBA;
      } else if (bestBA == null) {
        scaffold = bestAB;
      } else {
        scaffold = overlapPreference.compare(bestBA, bestAB) > 0 ? bestBA : bestAB;
      }
      int sizeAB = bestAB != null ? bestAB.getAtomCount() : -1;
      int sizeBA = bestBA != null ? bestBA.getAtomCount() : -1;

      // Re-perceive atom types and aromaticity on the running scaffold. UIT.getOverlaps
      // produces a fresh IAtomContainer whose bonds lose the aromatic flag (Kekule fallback).
      // Without re-aromatization, the next iteration matches an aromatic scaffold against
      // aromatic mols and ends up smaller than the true MCS.
      AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(scaffold);
      aromaticity.apply(scaffold);

      logger.fine("After MCS with mol %d: scaffold has %d atoms (AB=%d, BA=%d)".formatted(
          i + 1, scaffold.getAtomCount(), sizeAB, sizeBA));
    }

    return scaffold;
  }

  private static long heteroatomCount(@NotNull IAtomContainer mol) {
    long count = 0;
    for (org.openscience.cdk.interfaces.IAtom atom : mol.atoms()) {
      String s = atom.getSymbol();
      if (s != null && !"C".equals(s) && !"H".equals(s)) {
        count++;
      }
    }
    return count;
  }
}
