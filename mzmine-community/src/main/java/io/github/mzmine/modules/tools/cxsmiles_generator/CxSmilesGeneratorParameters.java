package io.github.mzmine.modules.tools.cxsmiles_generator;

import io.github.mzmine.parameters.impl.SimpleParameterSet;

/**
 * No parameters — {@link SimpleParameterSet#showSetupDialog} returns {@code ExitCode.OK}
 * immediately, which triggers {@link CxSmilesGeneratorModule#runModule} to open the tool window.
 */
public class CxSmilesGeneratorParameters extends SimpleParameterSet {

  public CxSmilesGeneratorParameters() {
    super();
  }
}
