package io.github.mzmine.modules.tools.cxsmiles_generator.chemistry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.openscience.cdk.CDKConstants;
import org.openscience.cdk.graph.ConnectivityChecker;
import org.openscience.cdk.graph.Cycles;
import org.openscience.cdk.graph.GraphUtil; // kept — used in symmetry path
import org.openscience.cdk.graph.invariant.Canon;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.interfaces.IPseudoAtom;
import org.openscience.cdk.interfaces.IRingSet;
import org.openscience.cdk.sgroup.Sgroup;
import org.openscience.cdk.sgroup.SgroupType;

/**
 * Expands the candidate atom set of each {@link SgroupType#ExtMulticenter} Sgroup so the Markush
 * structure covers more topologically-equivalent positions than were observed in the input.
 *
 * <p>For example, 3 chlorobiphenyl input isomers produce a Markush with 3 Cl candidate
 * positions; with {@link Strategy#SAME_RING_OR_CHAIN} expansion, the candidate set extends to
 * include all aromatic-CH positions on the substituted ring. For a fixed-length PFAS chain with
 * a CF3 branch observed at 3 positions, expansion adds the remaining middle CF2 carbons.</p>
 *
 * <p>Mutates each Sgroup's atom set in place. Does NOT add or remove atoms or bonds on the
 * scaffold; atom indices stay stable, so the existing
 * {@link CxSmilesEnumerator} keeps working unchanged.</p>
 */
public final class CandidatePositionExpander {

  private static final Logger logger = Logger.getLogger(CandidatePositionExpander.class.getName());

  /**
   * Strategy controlling which extra atoms are added to the candidate set.
   */
  public enum Strategy {
    /** No expansion. */
    NONE,
    /**
     * Extend candidates to topologically-similar atoms in the SAME ring (for cyclic candidates)
     * or in the SAME acyclic chain (for chain candidates). Filters by element, aromaticity,
     * implicit-H availability, and (for chains) atom degree.
     */
    SAME_RING_OR_CHAIN,
    /**
     * Extend candidates to atoms with the same canonical-symmetry equivalence class. Stricter
     * than {@code SAME_RING_OR_CHAIN}; yields nothing on low-symmetry scaffolds.
     */
    SYMMETRY_EQUIVALENT
  }

  private CandidatePositionExpander() {
  }

  /**
   * Expand each {@link SgroupType#ExtMulticenter} Sgroup on {@code scaffold} according to the
   * selected strategy. No-op when {@code strategy == NONE} or no such Sgroups are present.
   */
  public static void expand(@NotNull IAtomContainer scaffold, @NotNull Strategy strategy) {
    if (strategy == Strategy.NONE) {
      return;
    }
    final List<Sgroup> sgroups = scaffold.getProperty(CDKConstants.CTAB_SGROUPS);
    if (sgroups == null || sgroups.isEmpty()) {
      return;
    }

    // Cache: which atoms belong to the main scaffold connected component (i.e. NOT in the
    // disconnected substituent fragment that MarkushSgroupBuilder appended). We only ever
    // expand within the main scaffold component.
    final Set<IAtom> mainComponentAtoms = mainComponentAtoms(scaffold);

    // Cache rings once for the whole scaffold; falls back to relevant cycles for fused rings.
    final IRingSet rings = computeRings(scaffold);

    // Cache symmetry classes (only when needed).
    final long[] symmetryClasses;
    if (strategy == Strategy.SYMMETRY_EQUIVALENT) {
      symmetryClasses = computeSymmetryClasses(scaffold);
    } else {
      symmetryClasses = null;
    }

    int totalAdded = 0;
    for (Sgroup sg : sgroups) {
      if (sg.getType() != SgroupType.ExtMulticenter) {
        continue;
      }
      totalAdded += expandSgroup(scaffold, sg, strategy, rings, symmetryClasses,
          mainComponentAtoms);
    }
    logger.fine("CandidatePositionExpander added %d new candidate atoms (strategy=%s)".formatted(
        totalAdded, strategy));
  }

  private static int expandSgroup(@NotNull IAtomContainer scaffold, @NotNull Sgroup sg,
      @NotNull Strategy strategy, @NotNull IRingSet rings, long[] symmetryClasses,
      @NotNull Set<IAtom> mainComponentAtoms) {

    // Identify the existing candidate atoms (sg.atoms minus the * pseudo-atom).
    final List<IAtom> existing = new ArrayList<>();
    for (IAtom atom : sg.getAtoms()) {
      if (atom instanceof IPseudoAtom) {
        continue;
      }
      existing.add(atom);
    }
    if (existing.isEmpty()) {
      return 0;
    }

    final Set<IAtom> existingSet = newIdentitySet();
    existingSet.addAll(existing);

    // Build pool of candidate-equivalent atoms (excluding existing candidates).
    final Set<IAtom> pool = newIdentitySet();
    switch (strategy) {
      case SAME_RING_OR_CHAIN -> collectSameRingOrChain(scaffold, existing, rings,
          mainComponentAtoms, pool);
      case SYMMETRY_EQUIVALENT -> collectSymmetryEquivalent(scaffold, existing, symmetryClasses,
          mainComponentAtoms, pool);
      case NONE -> { /* unreachable */ }
    }
    logger.fine("Sgroup expansion: %d existing candidates → pool size %d".formatted(
        existing.size(), pool.size()));

    // Apply common filters (element, aromaticity, implicit-H, no pseudo-atoms, in main component).
    int added = 0;
    final IAtom representative = existing.get(0);
    final String repSymbol = representative.getSymbol();
    final boolean repAromatic = representative.isAromatic();
    final int repDegree = scaffold.getConnectedBondsCount(representative);

    for (IAtom candidate : pool) {
      if (existingSet.contains(candidate)) {
        continue;
      }
      if (candidate instanceof IPseudoAtom) {
        continue;
      }
      if (!mainComponentAtoms.contains(candidate)) {
        continue;
      }
      if (!repSymbol.equals(candidate.getSymbol())) {
        continue;
      }
      if (candidate.isAromatic() != repAromatic) {
        continue;
      }
      Integer h = candidate.getImplicitHydrogenCount();
      if (h == null || h < 1) {
        continue;
      }
      // Chain expansion: enforce same degree so middle CF2 doesn't expand onto terminal CF3.
      // (Ring expansion is already constrained by ring topology — degree = 2 or 3 for ring atoms.)
      if (strategy == Strategy.SAME_RING_OR_CHAIN
          && !inAnyRing(rings, candidate)
          && scaffold.getConnectedBondsCount(candidate) != repDegree) {
        continue;
      }
      sg.addAtom(candidate);
      added++;
    }
    return added;
  }

  // -- pool collection -------------------------------------------------------------------------

  private static void collectSameRingOrChain(@NotNull IAtomContainer scaffold,
      @NotNull List<IAtom> existing, @NotNull IRingSet rings,
      @NotNull Set<IAtom> mainComponentAtoms, @NotNull Set<IAtom> pool) {
    for (IAtom seed : existing) {
      if (inAnyRing(rings, seed)) {
        // Add every atom in any ring containing the seed. CDK's Cycles.toRingSet may wrap
        // atoms in AtomRef proxies that don't equal the scaffold's references by identity —
        // resolve back to the scaffold's actual reference via indexOf.
        for (IAtomContainer ring : ringsContaining(rings, seed)) {
          for (IAtom a : ring.atoms()) {
            int idx = scaffold.indexOf(a);
            if (idx >= 0) {
              pool.add(scaffold.getAtom(idx));
            }
          }
        }
      } else {
        // BFS within acyclic neighborhood: walk bonds whose endpoints are both non-ring.
        bfsAcyclicNeighborhood(scaffold, seed, rings, mainComponentAtoms, pool);
      }
    }
  }

  private static void collectSymmetryEquivalent(@NotNull IAtomContainer scaffold,
      @NotNull List<IAtom> existing, long[] symmetryClasses,
      @NotNull Set<IAtom> mainComponentAtoms, @NotNull Set<IAtom> pool) {
    if (symmetryClasses == null) {
      return;
    }
    final Set<Long> targetClasses = new java.util.HashSet<>();
    for (IAtom seed : existing) {
      int idx = scaffold.indexOf(seed);
      if (idx >= 0 && idx < symmetryClasses.length) {
        targetClasses.add(symmetryClasses[idx]);
      }
    }
    for (int i = 0; i < scaffold.getAtomCount(); i++) {
      if (i >= symmetryClasses.length) {
        break;
      }
      if (targetClasses.contains(symmetryClasses[i])) {
        IAtom atom = scaffold.getAtom(i);
        if (mainComponentAtoms.contains(atom)) {
          pool.add(atom);
        }
      }
    }
  }

  /**
   * BFS from {@code seed} along bonds whose BOTH endpoints are NOT in any ring. Collects all
   * reachable acyclic atoms in the same connected component. This is the "chain neighborhood"
   * — the contiguous acyclic chain (or branched acyclic substructure) containing the seed.
   */
  private static void bfsAcyclicNeighborhood(@NotNull IAtomContainer scaffold, @NotNull IAtom seed,
      @NotNull IRingSet rings, @NotNull Set<IAtom> mainComponentAtoms,
      @NotNull Set<IAtom> pool) {
    if (!mainComponentAtoms.contains(seed)) {
      return;
    }
    final Set<IAtom> visited = newIdentitySet();
    final Deque<IAtom> queue = new ArrayDeque<>();
    visited.add(seed);
    queue.add(seed);
    pool.add(seed);
    while (!queue.isEmpty()) {
      IAtom cur = queue.poll();
      for (IBond b : scaffold.getConnectedBondsList(cur)) {
        IAtom other = b.getOther(cur);
        if (visited.contains(other)) {
          continue;
        }
        if (inAnyRing(rings, other)) {
          // Reaching a ring atom is a hard stop — don't cross from chain into a ring system.
          continue;
        }
        if (!mainComponentAtoms.contains(other)) {
          continue;
        }
        visited.add(other);
        pool.add(other);
        queue.add(other);
      }
    }
  }

  // -- ring / connectivity helpers -------------------------------------------------------------

  private static @NotNull IRingSet computeRings(@NotNull IAtomContainer scaffold) {
    try {
      IRingSet rings = Cycles.essential(scaffold).toRingSet();
      if (rings.getAtomContainerCount() == 0) {
        rings = Cycles.relevant(scaffold).toRingSet();
      }
      return rings;
    } catch (Exception ex) {
      logger.log(Level.FINE, "Cycles.essential/relevant failed; using empty ring set", ex);
      return scaffold.getBuilder().newInstance(IRingSet.class);
    }
  }

  private static boolean inAnyRing(@NotNull IRingSet rings, @NotNull IAtom atom) {
    // Membership uses .equals() under the hood and works with both raw atoms and AtomRef wrappers.
    for (int i = 0; i < rings.getAtomContainerCount(); i++) {
      if (rings.getAtomContainer(i).contains(atom)) {
        return true;
      }
    }
    return false;
  }

  private static @NotNull List<IAtomContainer> ringsContaining(@NotNull IRingSet rings,
      @NotNull IAtom atom) {
    final List<IAtomContainer> result = new ArrayList<>();
    for (int i = 0; i < rings.getAtomContainerCount(); i++) {
      IAtomContainer ring = rings.getAtomContainer(i);
      if (ring.contains(atom)) {
        result.add(ring);
      }
    }
    return result;
  }

  /**
   * Returns the atoms of the main scaffold component, defined as the connected component
   * containing the largest number of non-pseudo atoms. Disconnected substituent fragments
   * (added by {@link MarkushSgroupBuilder}) are excluded.
   *
   * <p>Implemented via direct BFS over the scaffold's bond graph rather than
   * {@code ConnectivityChecker.partitionIntoMolecules}, because that API may return components
   * containing atom CLONES rather than the original references — breaking identity-based set
   * lookups.</p>
   */
  private static @NotNull Set<IAtom> mainComponentAtoms(@NotNull IAtomContainer scaffold) {
    final Set<IAtom> visited = newIdentitySet();
    Set<IAtom> bestComponent = newIdentitySet();
    int bestRealAtoms = -1;

    for (IAtom seed : scaffold.atoms()) {
      if (visited.contains(seed)) {
        continue;
      }
      Set<IAtom> component = newIdentitySet();
      java.util.Deque<IAtom> queue = new java.util.ArrayDeque<>();
      queue.add(seed);
      component.add(seed);
      visited.add(seed);
      int realAtoms = (seed instanceof IPseudoAtom) ? 0 : 1;
      while (!queue.isEmpty()) {
        IAtom cur = queue.poll();
        for (IBond b : scaffold.getConnectedBondsList(cur)) {
          IAtom other = b.getOther(cur);
          if (visited.contains(other)) {
            continue;
          }
          visited.add(other);
          component.add(other);
          queue.add(other);
          if (!(other instanceof IPseudoAtom)) {
            realAtoms++;
          }
        }
      }
      if (realAtoms > bestRealAtoms) {
        bestRealAtoms = realAtoms;
        bestComponent = component;
      }
    }
    return bestComponent;
  }

  /**
   * Compute canonical-symmetry classes for the whole scaffold. Returns {@code null} on failure.
   */
  private static long[] computeSymmetryClasses(@NotNull IAtomContainer scaffold) {
    try {
      int[][] adj = GraphUtil.toAdjList(scaffold);
      return Canon.symmetry(scaffold, adj);
    } catch (Exception ex) {
      // Older CDK versions can throw on disconnected molecules — try per-component.
      logger.log(Level.FINE,
          "Canon.symmetry failed on whole scaffold; falling back to per-component", ex);
      try {
        long[] classes = new long[scaffold.getAtomCount()];
        Map<IAtom, Integer> idx = new IdentityHashMap<>();
        for (int i = 0; i < scaffold.getAtomCount(); i++) {
          idx.put(scaffold.getAtom(i), i);
        }
        long offset = 0;
        var components = ConnectivityChecker.partitionIntoMolecules(scaffold);
        for (IAtomContainer comp : components.atomContainers()) {
          int[][] compAdj = GraphUtil.toAdjList(comp);
          long[] compClasses = Canon.symmetry(comp, compAdj);
          long maxInComp = 0;
          for (int i = 0; i < comp.getAtomCount(); i++) {
            Integer originalIdx = idx.get(comp.getAtom(i));
            if (originalIdx != null) {
              classes[originalIdx] = compClasses[i] + offset;
              if (compClasses[i] > maxInComp) {
                maxInComp = compClasses[i];
              }
            }
          }
          offset += maxInComp + 1;
        }
        return classes;
      } catch (Exception ex2) {
        logger.log(Level.FINE, "Canon.symmetry per-component fallback failed", ex2);
        return null;
      }
    }
  }

  private static <T> @NotNull Set<T> newIdentitySet() {
    return java.util.Collections.newSetFromMap(new IdentityHashMap<>());
  }
}
