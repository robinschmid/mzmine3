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

package io.github.mzmine.datamodel.featuredata;

import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.modules.io.projectload.version_3_0.CONST;
import io.github.mzmine.util.ParsingUtils;
import java.nio.DoubleBuffer;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * Stores series of m/z values.
 *
 * @author https://github.com/SteffenHeu
 */
public interface MzSeries extends SeriesValueCount {

  /**
   * Appends an {@link MzSeries} element as a child to the current element.
   */
  static void saveMzValuesToXML(XMLStreamWriter writer, MzSeries series)
      throws XMLStreamException {
    writer.writeStartElement(CONST.XML_MZ_VALUES_ELEMENT);
    writer.writeAttribute(CONST.XML_NUM_VALUES_ATTR, String.valueOf(series.getNumberOfValues()));
    writer.writeCharacters(ParsingUtils.doubleBufferToString(series.getMZValueBuffer()));
    writer.writeEndElement();
  }

  /**
   * @return All mz values corresponding to non-0 intensities.
   */
  DoubleBuffer getMZValueBuffer();

  /**
   *
   * @param dst results are reflected in this array
   * @return All m/z values of detected data points.
   */
  default double[] getMzValues(double[] dst) {
    if (dst.length < getNumberOfValues()) {
      dst = new double[getNumberOfValues()];
    }
    getMZValueBuffer().get(0, dst, 0, getNumberOfValues());
    return dst;
  }

  /**
   * @param index
   * @return The value at the index position. Note the index does not correspond to scan numbers.
   * @see IonTimeSeries#getMzForSpectrum(Scan)
   */
  default double getMZ(int index) {
    return getMZValueBuffer().get(index);
  }

  /**
   * @return The number of mz values corresponding to non-0 intensities.
   */
  default int getNumberOfValues() {
    return getMZValueBuffer().capacity();
  }

}
