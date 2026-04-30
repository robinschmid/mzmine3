package io.github.mzmine.modules.tools.cxsmiles_generator.chemistry;

import org.jetbrains.annotations.NotNull;

/**
 * A recognized variable-length repeating motif: SMARTS pattern that matches one repeat unit, a
 * label for the {@code Sg:n:} subscript, and the minimum chain length required to declare a
 * repeat (filters out short alkyl bridges and similar false positives).
 *
 * @param name             human-readable name (e.g. {@code "CF2"}, {@code "CH2"}, {@code "EO"})
 * @param smarts           SMARTS for one repeat unit (must define a single atom or short
 *                         contiguous fragment, and must be repeatable end-to-end)
 * @param label            CxSmiles {@code Sg:n:} subscript (defaults to {@code "n"})
 * @param minOccurrences   minimum repeat count in at least one input mol before declaring this
 *                         a variable-length region; suppresses spurious short-chain matches
 */
public record RepeatUnitMotif(@NotNull String name, @NotNull String smarts, @NotNull String label,
                              int minOccurrences) {

}
