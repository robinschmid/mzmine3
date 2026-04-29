package io.github.mzmine.modules.tools.cxsmiles_generator;

import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.openscience.cdk.interfaces.IAtomContainer;

/**
 * Result of the CxSMILES Markush structure generation pipeline.
 */
public record CxSmilesResult(
    @NotNull String cxSmiles,
    // scaffold with 2D coordinates and ExtMulticenter Sgroups attached — for rendering
    @NotNull IAtomContainer scaffoldMol,
    @NotNull List<String> variablePositionSummary
) {

}
