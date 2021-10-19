/*
 * Copyright 2006-2020 The MZmine Development Team
 *
 * This file is part of MZmine.
 *
 * MZmine is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MZmine; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA
 */

package io.github.mzmine.datamodel.features;

import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.featuredata.IonTimeSeries;
import io.github.mzmine.datamodel.features.types.DataType;

/**
 * @author Robin Schmid (https://github.com/robinschmid)
 */
public class FeatureDataBinding implements
    DataTypeValueChangeListener<IonTimeSeries<? extends Scan>> {

  @Override
  public void valueChanged(ModularDataModel dataModel, DataType<IonTimeSeries<? extends Scan>> type,
      IonTimeSeries<? extends Scan> oldValue, IonTimeSeries<? extends Scan> newValue) {
    final FeatureListRow row;
    if (dataModel instanceof Feature f) {
      row = f.getRow();
    } else if (dataModel instanceof FeatureListRow dataRow) {
      row = dataRow;
    } else {
      throw new UnsupportedOperationException(String.format(
          "Cannot apply this binding to a non Feature or FeatureListRow data model %s of class %s",
          dataModel, dataModel.getClass()));
    }

    // todo changes to charts etc
  }
}