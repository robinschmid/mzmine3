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
import io.github.mzmine.parameters.parametertypes.StringParameter;
import io.github.mzmine.parameters.parametertypes.submodules.OptionalModuleParameter;
import io.github.mzmine.parameters.parametertypes.submodules.SubModuleParameter;
import java.text.DecimalFormat;

/**
 * Extract statistics from gnps results
 * 
 * @author Robin Schmid (robinschmid@uni-muenster.de)
 *
 */
public class IINLibraryCreationParameters extends SimpleParameterSet {

  public static final SubModuleParameter<LibraryMethodeMetaDataParameters> METADATA =
      new SubModuleParameter<>("Create spectral library",
          "Creates a spectral library for all nodes in an Ion Identity Network with a spectral match identity",
          new LibraryMethodeMetaDataParameters());

  public static final OptionalModuleParameter<FilterLibraryMatchesBySampleListParameters> FILTER_SAMPLE_LIST =
      new OptionalModuleParameter<FilterLibraryMatchesBySampleListParameters>(
          "Filter matches by sample list", "", new FilterLibraryMatchesBySampleListParameters(),
          false);

  public static final BooleanParameter MATCH_ADDUCT_IIN = new BooleanParameter(
      "Match adduct to IIN", "Match adduct (library match) to best ion (IIN) for export", false);

  public static final StringParameter FILTER_PI = new StringParameter("PI filter",
      "Filter library matches by PI (not case sensitive, contains)", "", false);

  public static final StringParameter FILTER_DATA_COLLECTOR =
      new StringParameter("Data collector filter",
          "Filter library matches by data collector (not case sensitive, contains)", "", false);

  public static final DoubleParameter MIN_MATCH_SCORE =
      new DoubleParameter("Min GNPS lib match score",
          "Minimum match score against the GNPS spectral library to consider for new library",
          new DecimalFormat("0.000"), 0.9);

  public IINLibraryCreationParameters() {
    super(new Parameter[] {METADATA, FILTER_SAMPLE_LIST, MIN_MATCH_SCORE, MATCH_ADDUCT_IIN,
        FILTER_PI, FILTER_DATA_COLLECTOR});
  }

}
