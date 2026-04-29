package io.github.mzmine.modules.tools.cxsmiles_generator.chemistry;

import io.github.mzmine.datamodel.structures.MolecularStructure;
import io.github.mzmine.datamodel.structures.StructureInputType;
import io.github.mzmine.datamodel.structures.StructureParser;
import io.github.mzmine.modules.tools.cxsmiles_generator.CxSmilesResult;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.openscience.cdk.CDKConstants;
import org.openscience.cdk.aromaticity.Aromaticity;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.layout.StructureDiagramGenerator;
import org.openscience.cdk.sgroup.Sgroup;
import org.openscience.cdk.smiles.SmiFlavor;
import org.openscience.cdk.smiles.SmilesGenerator;
import org.openscience.cdk.tools.CDKHydrogenAdder;
import org.openscience.cdk.tools.manipulator.AtomContainerManipulator;

/**
 * Orchestrates the full pipeline:
 * <ol>
 *   <li>Parse SMILES → {@link IAtomContainer} (suppress explicit H)</li>
 *   <li>Iterative MCS → scaffold via {@link McsScaffoldBuilder}</li>
 *   <li>Attach {@code ExtMulticenter} Sgroups via {@link MarkushSgroupBuilder}</li>
 *   <li>Assign 2D coordinates via {@link StructureDiagramGenerator}</li>
 *   <li>Emit CxSMILES via {@code SmilesGenerator(SmiFlavor.CxSmiles)}</li>
 * </ol>
 */
public class CxSmilesConverter {

  private static final Logger logger = Logger.getLogger(CxSmilesConverter.class.getName());

  private CxSmilesConverter() {
  }

  /**
   * @param smilesList at least 2 SMILES strings representing positional isomers
   * @return CxSMILES result including the Markush scaffold molecule ready for rendering
   * @throws Exception on parse failure, empty MCS, or CDK error
   */
  @NotNull
  public static CxSmilesResult convert(@NotNull List<String> smilesList) throws Exception {
    if (smilesList.size() < 2) {
      throw new IllegalArgumentException("At least 2 SMILES are required.");
    }

    // Step 1 — parse, suppress explicit hydrogens, and aromatize consistently.
    // Consistent aromaticity perception across all inputs is required for the MCS algorithm
    // to find a stable, maximal scaffold (otherwise bond representation differences cause
    // the matcher to settle on a smaller local-optimum subgraph).
    List<IAtomContainer> mols = new ArrayList<>(smilesList.size());
    StructureParser parser = StructureParser.silent();
    Aromaticity aromaticity = Aromaticity.cdkLegacy();
    for (String smi : smilesList) {
      String trimmed = smi.strip();
      if (trimmed.isEmpty()) {
        continue;
      }
      MolecularStructure parsed = parser.parseStructure(trimmed, StructureInputType.SMILES);
      if (parsed == null) {
        throw new IllegalArgumentException("Cannot parse SMILES: " + trimmed);
      }
      IAtomContainer mol = parsed.structure();
      AtomContainerManipulator.suppressHydrogens(mol);
      AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(mol);
      aromaticity.apply(mol);
      mols.add(mol);
    }

    if (mols.size() < 2) {
      throw new IllegalArgumentException("At least 2 non-empty SMILES are required.");
    }

    // Step 2 — compute MCS scaffold
    IAtomContainer scaffold = McsScaffoldBuilder.build(mols);

    // Step 3 — re-perceive atom types and add implicit hydrogens. After MCS extraction the
    // scaffold contains atom references whose neighbors may have been excluded (e.g. an OH
    // oxygen removed from a ring carbon's neighborhood). Without this step those carbons
    // keep their stale implicit-H count and SmilesGenerator emits "[C]" notation.
    AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(scaffold);
    CDKHydrogenAdder.getInstance(scaffold.getBuilder()).addImplicitHydrogens(scaffold);
    aromaticity.apply(scaffold);

    // Step 4 — attach ExtMulticenter Sgroups (mutates scaffold in-place)
    MarkushSgroupBuilder.buildAndAttach(scaffold, mols);

    // Step 5 — assign 2D coordinates for rendering
    StructureDiagramGenerator sdg = new StructureDiagramGenerator();
    sdg.setMolecule(scaffold, false);
    sdg.generateCoordinates();

    // Step 6 — generate CxSMILES
    SmilesGenerator sgen = new SmilesGenerator(SmiFlavor.CxSmiles | SmiFlavor.Stereo);
    String cxSmiles = sgen.create(scaffold);

    // Step 7 — build human-readable summary
    List<Sgroup> sgroups = scaffold.getProperty(CDKConstants.CTAB_SGROUPS);
    List<String> summary = new ArrayList<>();
    if (sgroups != null) {
      for (Sgroup sg : sgroups) {
        // Sgroup atoms = {*} + all scaffold candidate atoms
        long candidateCount = sg.getAtoms().stream()
            .filter(a -> !(a instanceof org.openscience.cdk.interfaces.IPseudoAtom))
            .count();
        summary.add("R-group with %d candidate positions".formatted(candidateCount));
      }
    }
    logger.info("CxSMILES generated: " + cxSmiles);

    return new CxSmilesResult(cxSmiles, scaffold, summary);
  }
}
