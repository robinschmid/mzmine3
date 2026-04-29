package io.github.mzmine.modules.tools.cxsmiles_generator.chemistry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
 * <p>The full substituent fragment is preserved: e.g. an acetyl group {@code -C(=O)CH3} attached
 * to a scaffold OH at variable positions appears in the output as a disconnected
 * {@code CC(=O)*} fragment plus an {@code m:} layer enumerating the candidate scaffold atoms,
 * not just the first variable atom.</p>
 *
 * <p>The scaffold is mutated in-place: the substituent fragment + a {@code *} pseudo-atom and a
 * single bond are added per unique substituent type, and {@link CDKConstants#CTAB_SGROUPS} is set
 * on the scaffold.</p>
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
    // BondMatcher.forAny() is required because UniversalIsomorphismTester.getOverlaps produces
    // a scaffold whose bonds drop the AROMATIC flag (Kekule single/double), while the input mols
    // still hold aromatic bonds — strict matching would never find the mapping.
    final Pattern pattern = VentoFoggia.findSubstructure(scaffold,
        AtomMatcher.forElement(), BondMatcher.forAny());

    // assumption: positional isomers carry the same substituent type at different positions —
    // grouping by the variable atom's element symbol is sufficient for the common case.
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

        String subKey = varAtom.getSymbol();
        // Capture the variable bond so we can re-extract the full substituent fragment later.
        substituents.computeIfAbsent(subKey, _ -> new VariablePosition(mol, varAtom, bond))
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

      // Walk the FULL substituent fragment from one of the original mols (BFS from the variable
      // atom, never crossing the bond back into the scaffold). Then clone all the fragment's
      // atoms and bonds into the scaffold so the substituent is preserved verbatim
      // (e.g. -C(=O)CH3 acetyl, not just the first carbon).
      FragmentExtraction frag = extractFragment(pos.sourceMol, pos.rootAtom, pos.anchorBond);
      IAtom clonedRoot = cloneFragmentIntoScaffold(scaffold, frag);

      PseudoAtom pseudo = new PseudoAtom("*");
      pseudo.setImplicitHydrogenCount(0);
      scaffold.addAtom(pseudo);

      // Markush convention: bond connects the substituent root (NOT in Sgroup) to * (IN Sgroup).
      // Sgroup atoms = {*} + ALL scaffold candidate atoms; the m: layer enumerates the candidates.
      Bond bond = new Bond(clonedRoot, pseudo, IBond.Order.SINGLE);
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
          "R-group '%s' (%d-atom fragment): %d candidate scaffold attachment atoms".formatted(
              subKey, frag.atoms.size(), candidateAtoms.size()));
    }

    if (sgroups.isEmpty()) {
      return;
    }
    scaffold.setProperty(CDKConstants.CTAB_SGROUPS, sgroups);
  }

  /**
   * BFS from the root atom outward, never crossing the anchor bond (the bond that connects the
   * fragment to the scaffold). Returns all atoms and bonds in the full substituent subgraph.
   */
  private static @NotNull FragmentExtraction extractFragment(@NotNull IAtomContainer mol,
      @NotNull IAtom rootAtom, @NotNull IBond anchorBond) {
    final List<IAtom> atoms = new ArrayList<>();
    final List<IBond> bonds = new ArrayList<>();
    final Set<IAtom> visited = newIdentitySet();
    final Set<IBond> bondsAdded = newIdentitySet();
    final Deque<IAtom> queue = new ArrayDeque<>();

    visited.add(rootAtom);
    queue.add(rootAtom);
    atoms.add(rootAtom);

    while (!queue.isEmpty()) {
      IAtom cur = queue.poll();
      for (IBond b : mol.getConnectedBondsList(cur)) {
        if (b == anchorBond) {
          continue;
        }
        IAtom other = b.getOther(cur);
        if (!visited.contains(other)) {
          visited.add(other);
          atoms.add(other);
          queue.add(other);
        }
        if (!bondsAdded.contains(b)) {
          bonds.add(b);
          bondsAdded.add(b);
        }
      }
    }
    return new FragmentExtraction(atoms, bonds, rootAtom);
  }

  /**
   * Clones the fragment atoms and bonds into {@code scaffold} as a disconnected component and
   * returns the cloned counterpart of the fragment's root atom. Implicit-H counts and aromatic
   * flags are preserved — they are correct for the new environment because each atom keeps the
   * same set of neighboring bonds (anchor bond replaced by the future bond to {@code *}, which
   * is the same bond order).
   */
  private static @NotNull IAtom cloneFragmentIntoScaffold(@NotNull IAtomContainer scaffold,
      @NotNull FragmentExtraction frag) {
    final Map<IAtom, IAtom> cloneMap = new IdentityHashMap<>();
    for (IAtom orig : frag.atoms) {
      Atom clone = new Atom(orig.getSymbol());
      Integer atomicNumber = orig.getAtomicNumber();
      if (atomicNumber != null) {
        clone.setAtomicNumber(atomicNumber);
      }
      Integer implicitH = orig.getImplicitHydrogenCount();
      clone.setImplicitHydrogenCount(implicitH != null ? implicitH : 0);
      clone.setIsAromatic(orig.isAromatic());
      Integer formalCharge = orig.getFormalCharge();
      if (formalCharge != null) {
        clone.setFormalCharge(formalCharge);
      }
      scaffold.addAtom(clone);
      cloneMap.put(orig, clone);
    }
    for (IBond origBond : frag.bonds) {
      IAtom cBegin = cloneMap.get(origBond.getBegin());
      IAtom cEnd = cloneMap.get(origBond.getEnd());
      Bond cloneBond = new Bond(cBegin, cEnd, origBond.getOrder());
      cloneBond.setIsAromatic(origBond.isAromatic());
      scaffold.addBond(cloneBond);
    }
    return cloneMap.get(frag.rootAtom);
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
   * Holds the scaffold attachment sites for a single substituent type and a reference to one
   * source mol + the variable bond, used later to re-extract and clone the full substituent
   * fragment when building the Sgroup.
   */
  private static final class VariablePosition {

    final IAtomContainer sourceMol;
    final IAtom rootAtom;       // variable atom in sourceMol bonded to scaffold
    final IBond anchorBond;     // bond from rootAtom into the scaffold (do not cross during BFS)
    final Set<IAtom> candidateScaffoldAtoms;

    VariablePosition(@NotNull IAtomContainer sourceMol, @NotNull IAtom rootAtom,
        @NotNull IBond anchorBond) {
      this.sourceMol = sourceMol;
      this.rootAtom = rootAtom;
      this.anchorBond = anchorBond;
      this.candidateScaffoldAtoms = newIdentitySet();
    }
  }

  /**
   * Result of extracting a substituent fragment: ordered list of atoms (root first), all bonds
   * within the fragment, and a reference to the root atom (the one that was bonded to the
   * scaffold via the anchor bond).
   */
  private record FragmentExtraction(@NotNull List<IAtom> atoms, @NotNull List<IBond> bonds,
                                    @NotNull IAtom rootAtom) {

  }
}
