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
/*
 * This module was prepared by Abi Sarvepalli, Christopher Jensen, and Zheng Zhang at the Dorrestein
 * Lab (University of California, San Diego).
 *
 * It is freely available under the GNU GPL licence of MZmine2.
 *
 * For any questions or concerns, please refer to:
 * https://groups.google.com/forum/#!forum/molecular_networking_bug_reports
 *
 * Credit to the Du-Lab development team for the initial commitment to the MGF export module.
 */

package io.github.mzmine.modules.io.export_features_gnps.fast_masst;

import io.github.mzmine.datamodel.DataPoint;
import io.github.mzmine.modules.io.export_features_gnps.GNPSUtils;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * Submit MASST job to GNPS
 *
 * @author Robin Schmid (robinschmid@uni-muenster.de)
 */
public class GnpsDirectFastMasstTask extends AbstractTask {

  private static final Logger logger = Logger.getLogger(GnpsDirectFastMasstTask.class.getName());

  private final FastMasstDatabase database;
  private final double precursorMZ;
  private final DataPoint[] dataPoints;
  private final Double minCosine;
  private final double parentMzTol;
  private final double fragmentMzTol;
  private final int minMatchedSignals;
  private final boolean searchAnalogs;
  private final double progress = 0d;

  GnpsDirectFastMasstTask(double precursorMZ, DataPoint[] dataPoints, ParameterSet parameters,
      @NotNull Instant moduleCallDate) {
    super(null, moduleCallDate); // no new data stored -> null
    this.precursorMZ = precursorMZ;
    this.dataPoints = dataPoints;
    minCosine = parameters.getValue(GnpsDirectFastMasstParameters.cosineScore);
    database = parameters.getValue(GnpsDirectFastMasstParameters.database);
    parentMzTol = parameters.getValue(GnpsDirectFastMasstParameters.parentMassTolerance);
    fragmentMzTol = parameters.getValue(GnpsDirectFastMasstParameters.fragmentMassTolerance);
    minMatchedSignals = parameters.getValue(GnpsDirectFastMasstParameters.minimumMatchedSignals);
    searchAnalogs = parameters.getValue(GnpsDirectFastMasstParameters.searchAnalogs);
  }

  @Override
  public String getTaskDescription() {
    return "Submitting single MASST job to GNPS";
  }

  @Override
  public double getFinishedPercentage() {
    return progress;
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);

    try {
      boolean success = GNPSUtils.queryFastMASST(dataPoints, precursorMZ, database, minCosine,
          parentMzTol, fragmentMzTol, minMatchedSignals, searchAnalogs);
      if (!success) {
        logger.log(Level.WARNING, "GNPS MASST submit failed");
      }
    } catch (Exception e) {
      logger.log(Level.WARNING, "GNPS MASST submit failed", e);
    }

    setStatus(TaskStatus.FINISHED);
  }

}
