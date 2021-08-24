/*
 * Copyright 2006-2018 The MZmine 2 Development Team
 * 
 * This file is part of MZmine 2.
 * 
 * MZmine 2 is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 * 
 * MZmine 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with MZmine 2; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301
 * USA
 */

package io.github.mzmine.modules.tools.gnps_iimn_resultsanalysis;

import io.github.mzmine.parameters.Parameter;
import io.github.mzmine.parameters.impl.SimpleParameterSet;
import io.github.mzmine.parameters.parametertypes.BooleanParameter;
import io.github.mzmine.parameters.parametertypes.DoubleParameter;
import io.github.mzmine.parameters.parametertypes.IntegerParameter;
import io.github.mzmine.parameters.parametertypes.filenames.FileNameParameter;
import io.github.mzmine.parameters.parametertypes.filenames.FileSelectionType;
import io.github.mzmine.parameters.parametertypes.submodules.OptionalModuleParameter;
import java.text.DecimalFormat;

/**
 * Extract statistics from gnps results
 * 
 * @author Robin Schmid (robinschmid@uni-muenster.de)
 *
 */
public class GNPSResultsAnalysisParameters extends SimpleParameterSet {

  public static final FileNameParameter FILE_MGF =
      new FileNameParameter("MGF", "mgf file that was submitted to GNPS", "mgf", FileSelectionType.OPEN);
  public static final FileNameParameter FILE =
      new FileNameParameter("GNPS graphml file", "GNPS results in the graphml file", "graphml", FileSelectionType.OPEN);
  public static final FileNameParameter OUTPUT =
      new FileNameParameter("Results file", "Results file", "csv", FileSelectionType.SAVE);

  public static final BooleanParameter CREATE_SUMMARY =
      new BooleanParameter("Create summary file", "Summary statistics", false);

  public static final OptionalModuleParameter<IINLibraryCreationParameters> CREATE_SPECTRAL_LIB =
      new OptionalModuleParameter<>("Create spectral library",
          "Creates a spectral library for all nodes in an Ion Identity Network with a spectral match identity",
          new IINLibraryCreationParameters(), true);

  public static final IntegerParameter MIN_SIGNALS = new IntegerParameter("Min signals in new lib",
      "Minimum number of singals in new library spectrum (with intensity/max(intensity) >= min rel. intensity)",
      3);

  public static final DoubleParameter MIN_REL_INTENSITY = new DoubleParameter("Min rel. intensity",
      "Minimum relative intensity to count signal for minimum signals in new library entry",
      new DecimalFormat("0.000"), 0.001);


  public GNPSResultsAnalysisParameters() {
    super(new Parameter[] {OUTPUT, FILE, FILE_MGF, CREATE_SUMMARY, CREATE_SPECTRAL_LIB, MIN_SIGNALS,
        MIN_REL_INTENSITY});
  }

}
