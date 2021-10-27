/*
 * Copyright 2006-2021 The MZmine Development Team
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

package io.github.mzmine.modules.io.import_rawdata_bruker_tdf.datamodel.sql;

import java.util.Arrays;

/**
 * Additional MS/MS meta-information.
 */
public class TDFFrameMsMsInfoTable extends TDFDataTable<Long> {

  public static final String FRAME_MSMS_INFO_TABLE = "FrameMsMsInfo";

  /**
   * The frame to which this information applies. Should be an MS^2 frame, i.e., the corresponding
   * Frames.MsMsType should be 2.
   */
  public static final String FRAME_ID = "Frame";

  /**
   * Links to the corresponding "parent" MS^1 frame, in which this precursor was found. (Due to
   * possible out-of-order / asynchronous scan-task execution in the engine, this is not necessarily
   * the first MS^1 frame preceding this MS^2 frame in the analysis.) Parent is NULL for MRM scans.
   */
  public static final String PARENT_ID = "Parent";

  /**
   * The mass to which the quadrupole has been tuned for isolating this particular precursor. (in
   * the m/z calibration state that was used during acquisition). May or may not coincide with one
   * of the peaks in the parent frame.
   */
  public static final String TRIGGER_MASS = "TriggerMass";

  /**
   * The total 3-dB width of the isolation window (in m/z units), the center of which is given by
   * 'TriggerMass'.
   */
  public static final String ISOLATION_WIDTH = "IsolationWidth";

  /**
   * The charge state of the precursor as estimated by the precursor selection code that controls
   * the DDA acquisition. Can be NULL, which means that the charge state could not be determined,
   * e.g., because only one isotope peak could be detected.
   */
  public static final String PRECURSOR_CHARGE = "PrecursorCharge";

  /**
   * Collision energy (in eV) using which this frame was produced.
   */
  public static final String COLLISION_ENERGY = "CollisionEnergy";

  public TDFFrameMsMsInfoTable() {
    super(FRAME_MSMS_INFO_TABLE, FRAME_ID);

    columns.addAll(Arrays.asList(
        new TDFDataColumn<Long>(PARENT_ID),
        new TDFDataColumn<Double>(TRIGGER_MASS),
        new TDFDataColumn<Double>(ISOLATION_WIDTH),
        new TDFDataColumn<Long>(PRECURSOR_CHARGE),
        new TDFDataColumn<Double>(COLLISION_ENERGY)
    ));
  }

  @Override
  public boolean isValid() {
    return true;
  }
}
