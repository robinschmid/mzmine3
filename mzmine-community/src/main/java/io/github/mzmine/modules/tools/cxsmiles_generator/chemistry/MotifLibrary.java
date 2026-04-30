package io.github.mzmine.modules.tools.cxsmiles_generator.chemistry;

import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Registry of recognized repeating-unit motifs. The default library covers the most common
 * variable-length motifs in the mass-spec / environmental-chemistry domain:
 * <ul>
 *   <li><b>CF2</b> — perfluorinated alkyl chains (PFAS / PFCA / PFSA backbones)</li>
 *   <li><b>CH2</b> — saturated alkyl chains (lipids, detergents)</li>
 *   <li><b>EO</b>  — ethylene oxide (-CH2-CH2-O-) for polyethylene glycols (PEG)</li>
 * </ul>
 *
 * <p>Add new motifs by composing your own list — each motif is a self-contained
 * {@link RepeatUnitMotif} record.</p>
 */
public final class MotifLibrary {

  /** Single CF2 carbon (with two F neighbors). Min run = 2 to avoid trivial CF2 matches. */
  public static final RepeatUnitMotif CF2 = new RepeatUnitMotif(
      "CF2", "[CX4](F)(F)", "n", 2);

  public static final RepeatUnitMotif CCL2 = new RepeatUnitMotif(
      "CCl2", "[CX4](Cl)(Cl)", "n", 2);

  /**
   * Single sp3 CH2 carbon. Min run = 3 to avoid declaring "n" on short alkyl bridges
   * (n-propyl, etc.).
   */
  public static final RepeatUnitMotif CH2 = new RepeatUnitMotif(
      "CH2", "[CX4;H2]", "n", 3);

  /**
   * Ethylene-oxide unit (-CH2-CH2-O-) for polyethylene glycols. Min run = 2 to require at least
   * a diethylene glycol before declaring n.
   */
  public static final RepeatUnitMotif EO = new RepeatUnitMotif(
      "EO", "[CX4;H2][CX4;H2][OX2]", "n", 2);

  private MotifLibrary() {
  }

  /**
   * Default motif library, in the order the detector tries them.
   */
  public static @NotNull List<RepeatUnitMotif> defaults() {
    return List.of(CF2, CCL2, EO, CH2);
  }
}
