package io.github.mzmine.modules.tools.cxsmiles_generator.chemistry;

import io.github.mzmine.datamodel.structures.StructureUtils;
import io.github.mzmine.datamodel.structures.StructureUtils.SmilesFlavor;
import io.github.mzmine.modules.tools.cxsmiles_generator.CxSmilesResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.openscience.cdk.Bond;
import org.openscience.cdk.CDKConstants;
import org.openscience.cdk.aromaticity.Aromaticity;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.sgroup.Sgroup;
import org.openscience.cdk.sgroup.SgroupType;
import org.openscience.cdk.tools.manipulator.AtomContainerManipulator;

/**
 * Enumerates all concrete structures encoded by a Markush molecule with
 * {@link SgroupType#ExtMulticenter} Sgroups (the form produced by {@link CxSmilesConverter}).
 *
 * <p>Each Sgroup represents a positional-variation R-group: its {@code *} pseudo-atom is replaced
 * by each candidate scaffold atom in turn. When the molecule has multiple Sgroups, the
 * cartesian product of candidate atoms is enumerated. Combinations where two substituents would
 * target the same candidate atom are skipped (chemically invalid).</p>
 *
 * <p>This is the inverse operation of building the Markush form: it materializes the concrete
 * positional isomers so they can be compared back against the original input molecules.</p>
 */
public class CxSmilesEnumerator {

  private static final Logger logger = Logger.getLogger(CxSmilesEnumerator.class.getName());

  private CxSmilesEnumerator() {
  }

  /**
   * Enumerate all concrete {@link IAtomContainer} structures encoded by the Markush molecule.
   *
   * @param markushMol molecule with ExtMulticenter Sgroups stored under
   *                   {@link CDKConstants#CTAB_SGROUPS}; pass an unmodified
   *                   {@link CxSmilesResult#scaffoldMol()}
   * @return one concrete molecule per valid combination of substituent positions; if there are
   * no Sgroups, returns a singleton list containing a clone of the input
   */
  @NotNull
  public static List<IAtomContainer> enumerate(@NotNull IAtomContainer markushMol)
      throws Exception {
    final List<Sgroup> sgroups = extractExtMulticenterSgroups(markushMol);
    if (sgroups.isEmpty()) {
      return List.of((IAtomContainer) markushMol.clone());
    }

    // Resolve atom indices NOW (before any cloning) so they survive into the cloned molecules.
    final List<SgroupSubstitution> substitutions = new ArrayList<>(sgroups.size());
    for (Sgroup sg : sgroups) {
      SgroupSubstitution sub = resolveSubstitution(markushMol, sg);
      if (sub != null) {
        substitutions.add(sub);
      }
    }
    if (substitutions.isEmpty()) {
      return List.of((IAtomContainer) markushMol.clone());
    }

    final List<IAtomContainer> result = new ArrayList<>();
    final int[] combination = new int[substitutions.size()];
    enumerateRecursive(markushMol, substitutions, combination, 0, result);
    return result;
  }

  /**
   * Convenience wrapper: enumerate concrete structures and emit each as a canonical SMILES
   * string, suitable for set-membership comparison against input SMILES.
   */
  @NotNull
  public static List<String> enumerateCanonicalSmiles(@NotNull IAtomContainer markushMol)
      throws Exception {
    final List<IAtomContainer> mols = enumerate(markushMol);
    final List<String> result = new ArrayList<>(mols.size());
    for (IAtomContainer mol : mols) {
      String smiles = toCanonicalSmiles(mol);
      if (smiles != null) {
        result.add(smiles);
      }
    }
    return result;
  }

  /**
   * Canonicalize a SMILES string by parsing it and emitting the unique CDK canonical form.
   * Use this on input SMILES before comparing against
   * {@link #enumerateCanonicalSmiles(IAtomContainer)} so both sides go through the same
   * normalization pipeline.
   */
  @NotNull
  public static String canonicalize(@NotNull String smiles) throws Exception {
    IAtomContainer mol = io.github.mzmine.datamodel.structures.StructureParser.silent()
        .parseStructure(smiles,
            io.github.mzmine.datamodel.structures.StructureInputType.SMILES)
        .structure();
    AtomContainerManipulator.suppressHydrogens(mol);
    return toCanonicalSmiles(mol);
  }

  // ---------------------------------------------------------------------------------------------

  private static @NotNull List<Sgroup> extractExtMulticenterSgroups(
      @NotNull IAtomContainer markushMol) {
    final List<Sgroup> all = markushMol.getProperty(CDKConstants.CTAB_SGROUPS);
    if (all == null) {
      return Collections.emptyList();
    }
    final List<Sgroup> filtered = new ArrayList<>();
    for (Sgroup sg : all) {
      if (sg.getType() == SgroupType.ExtMulticenter) {
        filtered.add(sg);
      }
    }
    return filtered;
  }

  /**
   * Convert one Sgroup into a {@link SgroupSubstitution} keyed by atom indices in
   * {@code markushMol}. Returns {@code null} for malformed Sgroups (wrong bond count, no
   * candidates, etc.).
   */
  private static SgroupSubstitution resolveSubstitution(@NotNull IAtomContainer markushMol,
      @NotNull Sgroup sg) {
    if (sg.getBonds().size() != 1) {
      return null;
    }
    IBond bond = sg.getBonds().iterator().next();
    Set<IAtom> sgAtoms = sg.getAtoms();

    IAtom star;
    IAtom root;
    if (sgAtoms.contains(bond.getBegin())) {
      star = bond.getBegin();
      root = bond.getEnd();
    } else if (sgAtoms.contains(bond.getEnd())) {
      star = bond.getEnd();
      root = bond.getBegin();
    } else {
      return null;
    }

    final int starIdx = markushMol.indexOf(star);
    final int rootIdx = markushMol.indexOf(root);
    if (starIdx < 0 || rootIdx < 0) {
      return null;
    }

    final List<Integer> candidateIndices = new ArrayList<>();
    for (IAtom atom : sgAtoms) {
      if (atom == star) {
        continue;
      }
      int idx = markushMol.indexOf(atom);
      if (idx >= 0) {
        candidateIndices.add(idx);
      }
    }
    if (candidateIndices.isEmpty()) {
      return null;
    }
    return new SgroupSubstitution(starIdx, rootIdx, candidateIndices);
  }

  /**
   * Recursively pick one candidate per Sgroup and emit one concrete molecule per valid
   * combination. Combinations where two Sgroups land on the same candidate atom are skipped.
   */
  private static void enumerateRecursive(@NotNull IAtomContainer template,
      @NotNull List<SgroupSubstitution> subs, int[] combination, int subIndex,
      @NotNull List<IAtomContainer> result) throws Exception {
    if (subIndex >= subs.size()) {
      // reject combinations where two substituents target the same candidate atom
      Set<Integer> chosen = new HashSet<>();
      for (int i = 0; i < subs.size(); i++) {
        if (!chosen.add(subs.get(i).candidates().get(combination[i]))) {
          return;
        }
      }
      result.add(applyCombination(template, subs, combination));
      return;
    }
    SgroupSubstitution sub = subs.get(subIndex);
    for (int i = 0; i < sub.candidates().size(); i++) {
      combination[subIndex] = i;
      enumerateRecursive(template, subs, combination, subIndex + 1, result);
    }
  }

  /**
   * Materialize one concrete structure: clone the template, replace each {@code *} substituent
   * bond with a substituent-to-candidate bond, and remove the now-isolated {@code *} atoms.
   */
  private static @NotNull IAtomContainer applyCombination(@NotNull IAtomContainer template,
      @NotNull List<SgroupSubstitution> subs, int[] combination) throws Exception {
    final IAtomContainer mol = (IAtomContainer) template.clone();
    // The cloned Sgroups still reference the template's atoms — drop them; they served their
    // purpose, and the concrete molecule no longer has positional variation.
    mol.removeProperty(CDKConstants.CTAB_SGROUPS);

    final List<IAtom> starsToRemove = new ArrayList<>(subs.size());

    for (int i = 0; i < subs.size(); i++) {
      SgroupSubstitution sub = subs.get(i);
      int candidateIdx = sub.candidates().get(combination[i]);

      IAtom star = mol.getAtom(sub.starIdx());
      IAtom root = mol.getAtom(sub.rootIdx());
      IAtom candidate = mol.getAtom(candidateIdx);

      // 1. Disconnect the substituent fragment from *
      IBond starBond = mol.getBond(star, root);
      if (starBond != null) {
        mol.removeBond(starBond);
      }

      // 2. Reconnect the substituent fragment's root atom to the chosen scaffold candidate
      Bond newBond = new Bond(root, candidate, IBond.Order.SINGLE);
      mol.addBond(newBond);

      // 3. The candidate atom gained a new substituent bond — decrement its implicit H count
      // (e.g. an OH oxygen with H=1 becomes an O-R oxygen with H=0).
      Integer candidateH = candidate.getImplicitHydrogenCount();
      if (candidateH != null && candidateH > 0) {
        candidate.setImplicitHydrogenCount(candidateH - 1);
      }

      starsToRemove.add(star);
    }

    // 4. Remove all (now isolated) * pseudo atoms. Use atom REFERENCES so index shifts don't
    // matter as we remove atoms one by one.
    for (IAtom star : starsToRemove) {
      mol.removeAtom(star);
    }

    // 5. Re-perceive aromaticity so the canonical SMILES matches what's emitted from a fresh
    // parse of the same structure.
    try {
      AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(mol);
      Aromaticity.cdkLegacy().apply(mol);
    } catch (Exception ex) {
      logger.log(Level.FINE,
          "Could not re-perceive aromaticity for enumerated structure", ex);
    }

    return mol;
  }

  private static String toCanonicalSmiles(@NotNull IAtomContainer mol) {
    try {
      AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(mol);
      Aromaticity.cdkLegacy().apply(mol);
    } catch (Exception ex) {
      // fall through and try to generate SMILES anyway
    }
    return StructureUtils.getSmiles(SmilesFlavor.CANONICAL, mol);
  }

  /**
   * Substitution data for one ExtMulticenter Sgroup, expressed in atom indices so it survives
   * cloning of the markush molecule.
   */
  private record SgroupSubstitution(int starIdx, int rootIdx, @NotNull List<Integer> candidates) {

  }
}
