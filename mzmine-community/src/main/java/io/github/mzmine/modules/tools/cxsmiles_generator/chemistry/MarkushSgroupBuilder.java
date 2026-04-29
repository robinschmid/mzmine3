package io.github.mzmine.modules.tools.cxsmiles_generator.chemistry;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.openscience.cdk.Atom;
import org.openscience.cdk.Bond;
import org.openscience.cdk.CDKConstants;
import org.openscience.cdk.PseudoAtom;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.isomorphism.AtomMatcher;
import org.openscience.cdk.isomorphism.BondMatcher;
import org.openscience.cdk.isomorphism.Pattern;
import org.openscience.cdk.isomorphism.VentoFoggia;
import org.openscience.cdk.sgroup.Sgroup;
import org.openscience.cdk.sgroup.SgroupType;

/**
 * Identifies which scaffold atoms are candidate attachment sites for each variable substituent
 * across all input molecules, then attaches CDK {@link Sgroup} objects of type
 * {@link SgroupType#ExtMulticenter} to the scaffold. Once attached, {@code SmilesGenerator(
 * SmiFlavor.CxSmiles)} emits the {@code m:} positional-variation layer automatically.
 *
 * <p>The scaffold is mutated in-place: a {@code *} pseudo-atom and a single bond are added per
 * unique substituent type, and {@link CDKConstants#CTAB_SGROUPS} is set on the scaffold.</p>
 */
public class MarkushSgroupBuilder {

  private static final Logger logger = Logger.getLogger(MarkushSgroupBuilder.class.getName());

  private MarkushSgroupBuilder() {
  }

  /**
   * @param scaffold scaffold returned by {@link McsScaffoldBuilder#build(List)}; atoms are
   *                 references from {@code mols.get(0)}
   * @param mols     the original parsed molecules (hydrogen-suppressed)
   */
  public static void buildAndAttach(@NotNull IAtomContainer scaffold,
      @NotNull List<IAtomContainer> mols) {

    // scaffold atoms are object references from mols.get(0); identity-based set is safe
    final Set<IAtom> scaffoldAtomSet = newIdentitySet();
    for (IAtom a : scaffold.atoms()) {
      scaffoldAtomSet.add(a);
    }

    // VF2 with element-only atom matching and any-bond matching — tolerates aromatic vs Kekule
    // bond representation differences between the MCS scaffold and the original aromatic mols.
    // decision: BondMatcher.forAny() is required because UniversalIsomorphismTester.getOverlaps
    // produces a scaffold whose bonds drop the AROMATIC flag (Kekule single/double), while the
    // input mols still hold aromatic bonds — strict matching would never find the mapping.
    final Pattern pattern = VentoFoggia.findSubstructure(scaffold,
        AtomMatcher.forElement(), BondMatcher.forAny());

    // key = substituent symbol → (template variable atom, candidate scaffold atoms)
    // template is used to copy valence / implicit-H properties when creating the * fragment
    Map<String, VariablePosition> substituents = new LinkedHashMap<>();

    for (IAtomContainer mol : mols) {
      Map<IAtom, IAtom> molAtomToScaffold = mapMolAtomsToScaffold(scaffold, mol, scaffoldAtomSet,
          pattern);
      if (molAtomToScaffold.isEmpty()) {
        logger.warning("Could not map scaffold onto one of the input molecules — skipping.");
        continue;
      }

      for (IBond bond : mol.bonds()) {
        IAtom a1 = bond.getBegin();
        IAtom a2 = bond.getEnd();
        boolean a1mapped = molAtomToScaffold.containsKey(a1);
        boolean a2mapped = molAtomToScaffold.containsKey(a2);

        // only process bonds where exactly one side is in the scaffold
        if (a1mapped == a2mapped) {
          continue;
        }
        IAtom scaffoldAtom = a1mapped ? molAtomToScaffold.get(a1) : molAtomToScaffold.get(a2);
        IAtom varAtom = a1mapped ? a2 : a1;

        // assumption: positional isomers carry the same substituent type at different positions —
        // atom symbol is sufficient as a grouping key for the common case.
        String subKey = varAtom.getSymbol();
        substituents.computeIfAbsent(subKey, _ -> new VariablePosition(varAtom))
            .candidateScaffoldAtoms.add(scaffoldAtom);
      }
    }

    if (substituents.isEmpty()) {
      logger.info("No variable positions found — all input molecules have identical structure.");
      return;
    }

    final List<Sgroup> sgroups = new ArrayList<>();

    for (Map.Entry<String, VariablePosition> entry : substituents.entrySet()) {
      String subKey = entry.getKey();
      VariablePosition pos = entry.getValue();
      Set<IAtom> candidateAtoms = pos.candidateScaffoldAtoms;
      if (candidateAtoms.size() < 2) {
        // only one position — not actually a variable position; skip
        continue;
      }

      // Markush convention: add the substituent as a DISCONNECTED Cl-* fragment.
      // The m: layer encodes which scaffold atom * connects to (NO explicit scaffold bond).
      // CDK Sgroup structure for this:
      //   atoms = {*} + ALL scaffold candidate atoms
      //   bond  = {substituent-* bond}; bond.begin = substituent (NOT in Sgroup atoms),
      //                                 bond.end   = * (IS in Sgroup atoms)
      Atom substituentAtom = new Atom(subKey);
      // copy valence / implicit-H from the template so SmilesGenerator doesn't choke.
      // After H-suppression, the template held its full implicit H count + 1 bond to scaffold.
      // In the Markush form the substituent has 1 bond (to *) instead — same bond count, so
      // the template's implicit H count carries over directly.
      Integer templateImplicitH = pos.templateAtom.getImplicitHydrogenCount();
      substituentAtom.setImplicitHydrogenCount(templateImplicitH != null ? templateImplicitH : 0);
      Integer templateAtomicNumber = pos.templateAtom.getAtomicNumber();
      if (templateAtomicNumber != null) {
        substituentAtom.setAtomicNumber(templateAtomicNumber);
      }

      PseudoAtom pseudo = new PseudoAtom("*");
      pseudo.setImplicitHydrogenCount(0);
      scaffold.addAtom(substituentAtom);
      scaffold.addAtom(pseudo);

      Bond bond = new Bond(substituentAtom, pseudo, IBond.Order.SINGLE);
      scaffold.addBond(bond);

      Sgroup sg = new Sgroup();
      sg.setType(SgroupType.ExtMulticenter);
      sg.addAtom(pseudo);
      sg.addBond(bond);
      for (IAtom candidate : candidateAtoms) {
        sg.addAtom(candidate);
      }
      sgroups.add(sg);

      logger.fine(
          "R-group '%s': %d candidate scaffold attachment atoms".formatted(subKey,
              candidateAtoms.size()));
    }

    if (sgroups.isEmpty()) {
      return;
    }
    scaffold.setProperty(CDKConstants.CTAB_SGROUPS, sgroups);
  }

  /**
   * Builds an identity-keyed map from each scaffold-mapped atom in {@code mol} to its corresponding
   * scaffold atom. For {@code mols.get(0)} the atoms ARE the scaffold atoms (object identity), so
   * we short-circuit. For other mols we run VF2 substructure matching.
   */
  private static @NotNull Map<IAtom, IAtom> mapMolAtomsToScaffold(
      @NotNull IAtomContainer scaffold, @NotNull IAtomContainer mol,
      @NotNull Set<IAtom> scaffoldAtomSet, @NotNull Pattern pattern) {
    final Map<IAtom, IAtom> result = new IdentityHashMap<>();

    // fast path: if mol contains scaffold atoms by reference, this is mols.get(0)
    boolean hasIdentityOverlap = false;
    for (IAtom atom : mol.atoms()) {
      if (scaffoldAtomSet.contains(atom)) {
        hasIdentityOverlap = true;
        break;
      }
    }
    if (hasIdentityOverlap) {
      for (IAtom atom : mol.atoms()) {
        if (scaffoldAtomSet.contains(atom)) {
          result.put(atom, atom);
        }
      }
      return result;
    }

    // slow path: run VF2 to map scaffold onto mol
    int[] match = pattern.match(mol);
    if (match.length == 0) {
      return result;
    }
    for (int i = 0; i < match.length; i++) {
      IAtom scaffoldAtom = scaffold.getAtom(i);
      IAtom molAtom = mol.getAtom(match[i]);
      result.put(molAtom, scaffoldAtom);
    }
    return result;
  }

  private static <T> @NotNull Set<T> newIdentitySet() {
    return java.util.Collections.newSetFromMap(new IdentityHashMap<>());
  }

  /**
   * Tracks the scaffold attachment sites for a single substituent type (e.g. all the ring
   * positions where Cl was found across the input molecules), plus a template variable atom
   * used to copy valence / implicit-H properties when reconstructing the Markush fragment.
   */
  private static final class VariablePosition {

    final IAtom templateAtom;
    final Set<IAtom> candidateScaffoldAtoms;

    VariablePosition(@NotNull IAtom templateAtom) {
      this.templateAtom = templateAtom;
      this.candidateScaffoldAtoms = newIdentitySet();
    }
  }
}
