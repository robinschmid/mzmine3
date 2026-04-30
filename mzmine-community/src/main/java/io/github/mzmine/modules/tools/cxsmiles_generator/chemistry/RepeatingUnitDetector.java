package io.github.mzmine.modules.tools.cxsmiles_generator.chemistry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openscience.cdk.DefaultChemObjectBuilder;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.interfaces.IPseudoAtom;
import org.openscience.cdk.isomorphism.AtomMatcher;
import org.openscience.cdk.isomorphism.BondMatcher;
import org.openscience.cdk.isomorphism.Pattern;
import org.openscience.cdk.isomorphism.VentoFoggia;
import org.openscience.cdk.smarts.SmartsPattern;

/**
 * Secondary detection pass (runs AFTER {@link MarkushSgroupBuilder}) that scans for
 * variable-length repeat motifs (CF2, CH2, CH2-CH2-O, …) in the input molecules and produces
 * {@link SruDescriptor}s ready to be turned into {@code CtabStructureRepeatUnit} Sgroups by
 * {@link RepeatingUnitSgroupBuilder}.
 *
 * <p>Algorithm: for each motif, count maximal contiguous chains of matching atoms in each input
 * mol. If the chain length differs across mols, the motif is variable; we pick the shortest
 * mol's chain, take one unit at the chain's head, and project those atoms onto the
 * scaffold.</p>
 */
public final class RepeatingUnitDetector {

  private static final Logger logger = Logger.getLogger(RepeatingUnitDetector.class.getName());

  private RepeatingUnitDetector() {
  }

  /**
   * Detect variable-length repeat units across all input mols.
   *
   * @param mols     parsed input molecules (hydrogen-suppressed, aromatized — i.e. the same
   *                 mols passed to {@link MarkushSgroupBuilder})
   * @param scaffold the scaffold produced by the existing pipeline (may already contain
   *                 ExtMulticenter Sgroups from the positional-isomer pass)
   * @param library  motif library to search; usually {@link MotifLibrary#defaults()}
   * @return one {@link SruDescriptor} per detected variable-length motif (may be empty)
   */
  public static @NotNull List<SruDescriptor> detect(@NotNull List<IAtomContainer> mols,
      @NotNull IAtomContainer scaffold, @NotNull List<RepeatUnitMotif> library) {
    final List<SruDescriptor> results = new ArrayList<>();
    if (mols.size() < 2) {
      return results;
    }

    for (RepeatUnitMotif motif : library) {
      try {
        SruDescriptor desc = detectMotif(mols, scaffold, motif);
        if (desc != null) {
          results.add(desc);
          logger.fine("Detected variable repeat unit: " + motif.name() + " range=["
              + desc.minRepeats() + "," + desc.maxRepeats() + "]");
        }
      } catch (Exception ex) {
        logger.fine("Motif '" + motif.name() + "' detection failed: " + ex.getMessage());
      }
    }
    return results;
  }

  /**
   * Detect a single motif: find maximal chains in each mol, check for length variation, and if
   * present, project one unit onto the scaffold.
   */
  private static @Nullable SruDescriptor detectMotif(@NotNull List<IAtomContainer> mols,
      @NotNull IAtomContainer scaffold, @NotNull RepeatUnitMotif motif) throws CDKException {

    final SmartsPattern pattern = SmartsPattern.create(motif.smarts(),
        DefaultChemObjectBuilder.getInstance());
    pattern.setPrepare(true);

    // For each mol, find the maximal chain length of consecutive units (i.e. the longest run
    // of "anchor atoms" — the first atom of each match — that are connected end-to-end).
    final List<List<IAtom>> chainsPerMol = new ArrayList<>(mols.size());
    int minLen = Integer.MAX_VALUE;
    int maxLen = 0;
    int shortestIdx = 0;
    for (int i = 0; i < mols.size(); i++) {
      List<IAtom> chain = longestUnitChain(mols.get(i), pattern);
      chainsPerMol.add(chain);
      if (chain.size() < minLen) {
        minLen = chain.size();
        shortestIdx = i;
      }
      if (chain.size() > maxLen) {
        maxLen = chain.size();
      }
    }

    if (maxLen == 0 || minLen == maxLen) {
      // No chains, or all the same length → not variable
      return null;
    }
    if (minLen < motif.minOccurrences()) {
      // Shortest is too short → likely a false positive (e.g. n-propyl sneaking into a CH2 SRU)
      return null;
    }

    // Take one unit at the head of the shortest mol's chain. The "unit" includes the anchor
    // atom plus its branch atoms within the SMARTS match (e.g. for CF2, the C plus its 2 F).
    IAtomContainer shortestMol = mols.get(shortestIdx);
    List<IAtom> shortestChain = chainsPerMol.get(shortestIdx);
    if (shortestChain.isEmpty()) {
      return null;
    }
    IAtom firstAnchor = shortestChain.get(0);
    Set<IAtom> unitAtoms = oneUnitAtoms(shortestMol, firstAnchor, pattern);
    if (unitAtoms.isEmpty()) {
      return null;
    }

    // Project unit atoms onto the scaffold via VF2 substructure matching. We map the scaffold
    // onto the shortest mol; then for each unit atom in the shortest mol, find which scaffold
    // atom it corresponds to (inverse the mapping).
    int[] scaffoldUnitIndices = projectOntoScaffold(scaffold, shortestMol, unitAtoms);
    if (scaffoldUnitIndices == null || scaffoldUnitIndices.length == 0) {
      return null;
    }

    return new SruDescriptor(scaffoldUnitIndices, minLen, maxLen, motif.label());
  }

  /**
   * Find the longest CONNECTED chain of motif anchor atoms in {@code mol}. The "anchor atom" is
   * the first atom of each SMARTS match (e.g. the C in {@code [CX4](F)(F)}). Two anchors are
   * adjacent in the chain if they are connected by a bond in {@code mol}.
   */
  private static @NotNull List<IAtom> longestUnitChain(@NotNull IAtomContainer mol,
      @NotNull SmartsPattern pattern) {
    // Collect all anchor atoms (one per match)
    final Set<IAtom> anchors = newIdentitySet();
    int[][] all = pattern.matchAll(mol).toArray();
    for (int[] match : all) {
      if (match.length == 0) {
        continue;
      }
      anchors.add(mol.getAtom(match[0]));
    }
    if (anchors.isEmpty()) {
      return List.of();
    }

    // BFS through anchor-anchor adjacencies; track the largest connected component
    final Set<IAtom> visited = newIdentitySet();
    List<IAtom> longest = List.of();
    for (IAtom seed : anchors) {
      if (visited.contains(seed)) {
        continue;
      }
      List<IAtom> chain = new ArrayList<>();
      Deque<IAtom> queue = new ArrayDeque<>();
      queue.add(seed);
      visited.add(seed);
      chain.add(seed);
      while (!queue.isEmpty()) {
        IAtom cur = queue.poll();
        for (IBond b : mol.getConnectedBondsList(cur)) {
          IAtom other = b.getOther(cur);
          if (visited.contains(other)) {
            continue;
          }
          if (!anchors.contains(other)) {
            continue;
          }
          visited.add(other);
          chain.add(other);
          queue.add(other);
        }
      }
      if (chain.size() > longest.size()) {
        longest = chain;
      }
    }
    return longest;
  }

  /**
   * Get the atoms making up ONE motif unit: the anchor atom plus the atoms returned in the
   * first SMARTS match that has {@code anchor} as its first atom (so for {@code [CX4](F)(F)},
   * the C plus its 2 F neighbors).
   */
  private static @NotNull Set<IAtom> oneUnitAtoms(@NotNull IAtomContainer mol,
      @NotNull IAtom anchor, @NotNull SmartsPattern pattern) {
    int anchorIdx = mol.indexOf(anchor);
    int[][] all = pattern.matchAll(mol).toArray();
    for (int[] match : all) {
      if (match.length > 0 && match[0] == anchorIdx) {
        Set<IAtom> result = newIdentitySet();
        for (int idx : match) {
          result.add(mol.getAtom(idx));
        }
        return result;
      }
    }
    // Fallback — anchor only
    return Set.of(anchor);
  }

  /**
   * Find the scaffold atoms corresponding to {@code unitAtomsInMol}. Returns the scaffold atom
   * indices, or {@code null} if mapping fails or any unit atom is not present in the scaffold.
   */
  private static int @Nullable [] projectOntoScaffold(@NotNull IAtomContainer scaffold,
      @NotNull IAtomContainer mol, @NotNull Set<IAtom> unitAtomsInMol) {
    // Build VF2 mapping scaffold→mol with permissive matchers (same approach as
    // MarkushSgroupBuilder so we are consistent with the existing scaffold definition).
    Pattern p = VentoFoggia.findSubstructure(scaffold, AtomMatcher.forElement(),
        BondMatcher.forAny());
    int[] match = p.match(mol);
    if (match.length == 0) {
      return null;
    }
    // match[i] = mol-atom-index of the atom corresponding to scaffold atom i
    // Build inverse: molAtom → scaffoldIdx
    final java.util.Map<IAtom, Integer> molToScaffoldIdx = new IdentityHashMap<>(match.length);
    for (int i = 0; i < match.length; i++) {
      molToScaffoldIdx.put(mol.getAtom(match[i]), i);
    }
    // Only keep unit atoms that mapped onto the scaffold; drop pseudo atoms and substituent
    // fragments that aren't part of the main scaffold component.
    final List<Integer> result = new ArrayList<>(unitAtomsInMol.size());
    for (IAtom unitAtom : unitAtomsInMol) {
      Integer idx = molToScaffoldIdx.get(unitAtom);
      if (idx == null) {
        continue;
      }
      IAtom scaffoldAtom = scaffold.getAtom(idx);
      if (scaffoldAtom instanceof IPseudoAtom) {
        continue;
      }
      result.add(idx);
    }
    if (result.isEmpty()) {
      return null;
    }
    int[] arr = new int[result.size()];
    for (int i = 0; i < result.size(); i++) {
      arr[i] = result.get(i);
    }
    return arr;
  }

  // -- helpers ---------------------------------------------------------------------------------

  private static <T> @NotNull Set<T> newIdentitySet() {
    return java.util.Collections.newSetFromMap(new IdentityHashMap<>());
  }
}
