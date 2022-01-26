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

package datamodel;

import io.github.mzmine.datamodel.FeatureStatus;
import io.github.mzmine.datamodel.IsotopePattern;
import io.github.mzmine.datamodel.IsotopePattern.IsotopePatternStatus;
import io.github.mzmine.datamodel.MobilityType;
import io.github.mzmine.datamodel.features.types.DetectionType;
import io.github.mzmine.datamodel.features.types.FeatureInformationType;
import io.github.mzmine.datamodel.features.types.IsotopePatternType;
import io.github.mzmine.datamodel.features.types.MobilityUnitType;
import io.github.mzmine.datamodel.impl.MultiChargeStateIsotopePattern;
import io.github.mzmine.datamodel.impl.SimpleFeatureInformation;
import io.github.mzmine.datamodel.impl.SimpleIsotopePattern;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class GeneralTypeTests {

  @Test
  void detectionTypeTest() {
    DetectionType type = new DetectionType();
    var value = FeatureStatus.DETECTED;
    DataTypeTestUtils.simpleDataTypeSaveLoadTest(type, value);
  }

  @Test
  @DisplayName("SimpleIsotopePattern save load")
  void simpleIsotopePatternTypeTest() {
    IsotopePatternType type = new IsotopePatternType();
    IsotopePattern pattern = new SimpleIsotopePattern(new double[]{200d, 201d, 202d},
        new double[]{1.0, 0.5, 0.11}, 1, IsotopePatternStatus.DETECTED, "Save load test");

    DataTypeTestUtils.simpleDataTypeSaveLoadTest(type, pattern);
  }

  @Test
  @DisplayName("MultiChargeStateIsotopePattern save load")
  void multiChargeStateIsotopePatternTypeTest() {
    IsotopePatternType type = new IsotopePatternType();

    IsotopePattern pattern = new MultiChargeStateIsotopePattern(List.of(
        new SimpleIsotopePattern(new double[]{200d, 201d, 202d}, new double[]{1.0, 0.5, 0.11}, 1,
            IsotopePatternStatus.DETECTED, "Save load test1"),
        new SimpleIsotopePattern(new double[]{100d, 100.5, 101d}, new double[]{1.0, 0.5, 0.11}, 2,
            IsotopePatternStatus.DETECTED, "Save load test2")));

    DataTypeTestUtils.simpleDataTypeSaveLoadTest(type, pattern);
  }

  // todo FeatureGroupType

  @Test
  void featureInformationTypeTest() {
    FeatureInformationType type = new FeatureInformationType();
    SimpleFeatureInformation info = new SimpleFeatureInformation();
    info.addProperty("bla", "blub");
    info.addProperty("ß012eisd", "ß0widqscn/+9");
    DataTypeTestUtils.simpleDataTypeSaveLoadTest(type, info);
  }

  /**
   *ImsMsMsInfoType test in {@link IMSScanTypesTest}
   */

  /**
   *
   */
  @Test
  void mobilityUnitTypeTest() {
    MobilityUnitType type = new MobilityUnitType();
    var value = MobilityType.TIMS;
    DataTypeTestUtils.simpleDataTypeSaveLoadTest(type, value);
  }
}
