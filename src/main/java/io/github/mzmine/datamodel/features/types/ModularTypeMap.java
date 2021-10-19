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

package io.github.mzmine.datamodel.features.types;

import io.github.mzmine.datamodel.features.ModularDataModel;
import java.util.HashMap;
import java.util.Map;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;

/**
 * Used in {@link ModularType} as an observable property. The parentType is the main column and all
 * DataTypes in this map are the sub columns
 *
 * @author Robin Schmid (https://github.com/robinschmid)
 */
public class ModularTypeMap implements ModularDataModel {

  private final ObservableMap<DataType, Object> map =
      FXCollections.observableMap(new HashMap<>());

  protected ModularType parentType;

  public ModularTypeMap(ModularType parentType) {
    super();
    this.parentType = parentType;
  }

  public ModularTypeMap(Map<DataType, Object> map, ModularType parentType) {
    super();
    this.map.putAll(map);
    this.parentType = parentType;
  }

  public ModularType getParentType() {
    return parentType;
  }

  @Override
  public ObservableMap<Class<? extends DataType>, DataType> getTypes() {
    return parentType.getSubDataTypesMap();
  }

  @Override
  public ObservableMap<DataType, Object> getMap() {
    return map;
  }
}