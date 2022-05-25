/*
 * Copyright 2006-2022 The MZmine Development Team
 *
 * This file is part of MZmine.
 *
 * MZmine is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MZmine; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */

package io.github.mzmine.modules.io.export_features_gnps.fast_masst;

import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.parameters.Parameter;
import io.github.mzmine.parameters.dialogs.ParameterSetupDialog;
import io.github.mzmine.parameters.impl.SimpleParameterSet;
import io.github.mzmine.parameters.parametertypes.BooleanParameter;
import io.github.mzmine.parameters.parametertypes.ComboParameter;
import io.github.mzmine.parameters.parametertypes.DoubleParameter;
import io.github.mzmine.parameters.parametertypes.IntegerParameter;
import io.github.mzmine.util.ExitCode;

/**
 * Submit MASST search on GNPS
 *
 * @author Robin Schmid (robinschmid@uni-muenster.de)
 */
public class GnpsDirectFastMasstParameters extends SimpleParameterSet {

  public static final ComboParameter<FastMasstDatabase> database = new ComboParameter<>(
      "Search in database", "Select databases", FastMasstDatabase.values(),
      FastMasstDatabase.GNPS_DATA);

  public static final DoubleParameter cosineScore = new DoubleParameter("Min cosine score", "",
      MZmineCore.getConfiguration().getScoreFormat(), 0.7, 0d, 1d);

  public static final IntegerParameter minimumMatchedSignals = new IntegerParameter(
      "Minimum matched peaks", "Minimum number of spectral signals to be matched", 6, false, 1,
      Integer.MAX_VALUE);

  public static final DoubleParameter parentMassTolerance = new DoubleParameter(
      "Absolute parent mass tolerance", "Absolute m/z tolerance to match precursors",
      MZmineCore.getConfiguration().getMZFormat(), 1d, 0d, Double.MAX_VALUE);

  public static final DoubleParameter fragmentMassTolerance = new DoubleParameter(
      "Absolute fragment mass tolerance", "Absolute m/z tolerance to match fragment ions",
      MZmineCore.getConfiguration().getMZFormat(), 0.5, 0d, Double.MAX_VALUE);

  public static final BooleanParameter searchAnalogs = new BooleanParameter("Analog search",
      "Search analogs by allowing m/z differences between precursors", false);

  public GnpsDirectFastMasstParameters() {
    super(new Parameter[]{cosineScore, minimumMatchedSignals, parentMassTolerance,
        fragmentMassTolerance, searchAnalogs, database});
  }

  @Override
  public ExitCode showSetupDialog(boolean valueCheckRequired) {
    String message = """
        <html><strong>About the Mass Spectrometry Search Tool (MASST) direct submission:</strong><br>
        When using MASST please cite:<br>
        Wang, M., Jarmusch, A.K., Vargas, F. et al. Mass spectrometry searches using MASST. Nat Biotechnol 38, 23–26 (2020). <a href="https://doi.org/10.1038/s41587-019-0375-9">https://doi.org/10.1038/s41587-019-0375-9</a>
        </html>""";

    ParameterSetupDialog dialog = new ParameterSetupDialog(valueCheckRequired, this, message);
    dialog.showAndWait();
    return dialog.getExitCode();
  }

}
