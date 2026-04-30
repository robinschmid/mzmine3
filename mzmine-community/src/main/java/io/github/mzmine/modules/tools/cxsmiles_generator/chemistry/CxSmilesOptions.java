package io.github.mzmine.modules.tools.cxsmiles_generator.chemistry;

import org.jetbrains.annotations.NotNull;

/**
 * Configuration for the CxSmiles generation pipeline. Defaults preserve the original
 * positional-isomer-only behavior so existing tests pass unchanged.
 *
 * @param expansion          candidate position expansion strategy applied AFTER MCS-based
 *                           positional-isomer detection
 * @param detectRepeatUnits  if {@code true}, run a secondary pass that searches for variable-
 *                           length repeat motifs (CF2, CH2-CH2-O, …) and attaches
 *                           {@code CtabStructureRepeatUnit} Sgroups
 * @param sruMinRepeats      minimum repeat count to enumerate from an SRU Sgroup (inclusive)
 * @param sruMaxRepeats      maximum repeat count to enumerate from an SRU Sgroup (inclusive)
 * @param enumerationLimit   safety cap on the number of concrete structures returned by
 *                           {@code CxSmilesEnumerator.enumerate}; prevents runaway combinatorics
 *                           when SRU range × ExtMulticenter candidates × expansion compounds
 */
public record CxSmilesOptions(
    @NotNull CandidatePositionExpander.Strategy expansion,
    boolean detectRepeatUnits,
    int sruMinRepeats,
    int sruMaxRepeats,
    int enumerationLimit) {

  public static @NotNull CxSmilesOptions defaults() {
    return new CxSmilesOptions(CandidatePositionExpander.Strategy.NONE, false, 1, 10, 10_000);
  }
}
