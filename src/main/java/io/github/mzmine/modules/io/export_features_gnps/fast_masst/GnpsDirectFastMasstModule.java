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

import io.github.mzmine.datamodel.DataPoint;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.MassSpectrum;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.MZmineModuleCategory;
import io.github.mzmine.modules.MZmineRunnableModule;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.taskcontrol.Task;
import io.github.mzmine.util.ExitCode;
import io.github.mzmine.util.scans.ScanUtils;
import java.time.Instant;
import java.util.Collection;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * Directly submits a new MASST job from MZmine https://fastlibrarysearch.ucsd.edu/fastsearch/
 *
 * @author Robin Schmid (robinschmid@uni-muenster.de)
 */
public class GnpsDirectFastMasstModule implements MZmineRunnableModule {

  private static final Logger logger = Logger.getLogger(GnpsDirectFastMasstModule.class.getName());

  private static final String MODULE_NAME = "fastMASST public data search (GNPS)";
  private static final String MODULE_DESCRIPTION = "Directly search all public data on GNPS by fastMASST";

  public static ExitCode submitSingleMASSTJob(double precursorMZ, MassSpectrum spectrum) {
    return submitSingleMASSTJob(precursorMZ, ScanUtils.extractDataPoints(spectrum));
  }

  public static ExitCode submitSingleMASSTJob(double precursorMZ, DataPoint[] dataPoints) {
    final ParameterSet parameters = MZmineCore.getConfiguration()
        .getModuleParameters(GnpsDirectFastMasstModule.class);
    if (parameters.showSetupDialog(true) == ExitCode.OK) {
      MZmineCore.getTaskController()
          .addTask(new GnpsDirectFastMasstTask(precursorMZ, dataPoints, parameters, Instant.now()));
      return ExitCode.OK;
    }
    return ExitCode.CANCEL;
  }

  @Override
  public String getDescription() {
    return MODULE_DESCRIPTION;
  }

  @Override
  @NotNull
  public ExitCode runModule(MZmineProject project, ParameterSet parameters, Collection<Task> tasks,
      @NotNull Instant moduleCallDate) {

    return ExitCode.CANCEL;
  }

  @Override
  public MZmineModuleCategory getModuleCategory() {
    return MZmineModuleCategory.FEATURELISTEXPORT;
  }

  @Override
  public String getName() {
    return MODULE_NAME;
  }

  @Override
  public Class<? extends ParameterSet> getParameterSetClass() {
    return GnpsDirectFastMasstParameters.class;
  }

}