package io.github.mzmine.modules.tools.cxsmiles_generator.chemistry;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.openscience.cdk.CDKConstants;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.sgroup.Sgroup;
import org.openscience.cdk.sgroup.SgroupKey;
import org.openscience.cdk.sgroup.SgroupType;

/**
 * Attaches one {@link SgroupType#CtabStructureRepeatUnit} Sgroup per {@link SruDescriptor} to
 * the scaffold. Resulting Sgroup atom set covers ONE instance of the repeat unit (the atoms
 * indicated by the descriptor); the bond set covers the crossing bonds (bonds with exactly one
 * endpoint inside the unit) — these are the head and tail attachment points.
 *
 * <p>Connectivity is set to {@code "HT"} (head-to-tail) when the unit has exactly two crossing
 * bonds AND the head/tail anchor atoms are chemically distinguishable; otherwise omitted (CDK
 * emits no {@code :ht} suffix and tools treat the SRU as undirected).</p>
 */
public final class RepeatingUnitSgroupBuilder {

  private static final Logger logger = Logger.getLogger(RepeatingUnitSgroupBuilder.class.getName());

  private RepeatingUnitSgroupBuilder() {
  }

  /**
   * Append SRU Sgroups to the scaffold's {@link CDKConstants#CTAB_SGROUPS} list. Existing
   * Sgroups (e.g. ExtMulticenter from the positional-isomer pass) are preserved.
   */
  public static void build(@NotNull IAtomContainer scaffold,
      @NotNull List<SruDescriptor> descriptors) {
    if (descriptors.isEmpty()) {
      return;
    }
    final List<Sgroup> existing = scaffold.getProperty(CDKConstants.CTAB_SGROUPS);
    final List<Sgroup> sgroups = new ArrayList<>();
    if (existing != null) {
      sgroups.addAll(existing);
    }

    for (SruDescriptor desc : descriptors) {
      Sgroup sg = buildOne(scaffold, desc);
      if (sg != null) {
        sgroups.add(sg);
      }
    }

    if (!sgroups.isEmpty()) {
      scaffold.setProperty(CDKConstants.CTAB_SGROUPS, sgroups);
    }
  }

  private static Sgroup buildOne(@NotNull IAtomContainer scaffold, @NotNull SruDescriptor desc) {
    if (desc.scaffoldUnitAtomIndices().length == 0) {
      return null;
    }
    final Set<IAtom> unitAtoms = newIdentitySet();
    for (int idx : desc.scaffoldUnitAtomIndices()) {
      if (idx < 0 || idx >= scaffold.getAtomCount()) {
        return null;
      }
      unitAtoms.add(scaffold.getAtom(idx));
    }

    // Find crossing bonds: bonds where exactly ONE endpoint is in the unit
    final List<IBond> crossing = new ArrayList<>();
    for (IBond bond : scaffold.bonds()) {
      boolean a = unitAtoms.contains(bond.getBegin());
      boolean b = unitAtoms.contains(bond.getEnd());
      if (a != b) {
        crossing.add(bond);
      }
    }

    if (crossing.size() > 2) {
      // Branched unit — direction undefined; reject this SRU rather than emit ambiguous CxSmiles
      logger.fine("SRU rejected: " + crossing.size() + " crossing bonds (branched unit)");
      return null;
    }

    final Sgroup sg = new Sgroup();
    sg.setType(SgroupType.CtabStructureRepeatUnit);
    for (IAtom a : unitAtoms) {
      sg.addAtom(a);
    }
    for (IBond b : crossing) {
      sg.addBond(b);
    }
    sg.setSubscript(desc.label());

    // Set HT connectivity when the unit has exactly two crossing bonds with distinct anchors;
    // otherwise omit so tools treat the SRU as undirected.
    if (crossing.size() == 2) {
      sg.putValue(SgroupKey.CtabConnectivity, "HT");
    }

    return sg;
  }

  private static <T> @NotNull Set<T> newIdentitySet() {
    return java.util.Collections.newSetFromMap(new IdentityHashMap<>());
  }
}
