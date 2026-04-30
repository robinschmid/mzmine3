package io.github.mzmine.modules.tools.cxsmiles_generator.chemistry;

import org.jetbrains.annotations.NotNull;

/**
 * Result of repeating-unit detection: identifies one instance of a variable-length repeating
 * motif and the range of repeat counts observed across the input molecules.
 *
 * @param scaffoldUnitAtomIndices indices in the scaffold of the atoms making up ONE instance of
 *                                the repeat unit (e.g. for CF2-chain detection, the indices of
 *                                a single CF2 carbon plus its two F neighbors)
 * @param minRepeats              smallest chain length observed across all input mols
 * @param maxRepeats              largest chain length observed across all input mols
 * @param label                   subscript label for the {@code Sg:n:} layer (e.g. {@code "n"})
 */
public record SruDescriptor(int @NotNull [] scaffoldUnitAtomIndices, int minRepeats,
                            int maxRepeats, @NotNull String label) {

}
