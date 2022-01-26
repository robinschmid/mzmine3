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

package io.github.mzmine.datamodel.impl;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.DataPoint;
import io.github.mzmine.datamodel.IsotopePattern;
import io.github.mzmine.datamodel.MassSpectrumType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.events.XMLEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Holds multiple isotope pattern of different charge states. Not every charge state is represented
 */
public class MultiChargeStateIsotopePattern implements IsotopePattern {

  public static final String XML_ELEMENT = "multi_charge_state_isotopepattern";

  @NotNull
  private final List<IsotopePattern> patterns = new ArrayList<>();


  public MultiChargeStateIsotopePattern(@NotNull IsotopePattern... patterns) {
    this(Arrays.asList(patterns));
  }

  public MultiChargeStateIsotopePattern(@NotNull List<IsotopePattern> patterns) {
    if (patterns.isEmpty()) {
      throw new IllegalArgumentException("List of isotope patterns cannot be empty");
    }
    this.patterns.addAll(patterns);
  }

  public static IsotopePattern loadFromXML(XMLStreamReader reader) throws XMLStreamException {
    if (!reader.getLocalName().equals(XML_ELEMENT)) {
      throw new IllegalStateException("Invalid element");
    }
    List<IsotopePattern> patterns = new ArrayList<>(2);

    while (reader.hasNext() && !(reader.isEndElement() && reader.getLocalName()
        .equals(XML_ELEMENT))) {
      int next = reader.next();
      if (next != XMLEvent.START_ELEMENT) {
        continue;
      }

      switch (reader.getLocalName()) {
        case SimpleIsotopePattern.XML_ELEMENT -> patterns.add(
            SimpleIsotopePattern.loadFromXML(reader));
      }
    }
    return patterns.isEmpty() ? null : new MultiChargeStateIsotopePattern(patterns);
  }

  /**
   * List of all isotope patterns. not all charge states must be represented.
   *
   * @return isotope pattern for different charge states
   */
  public List<IsotopePattern> getPatterns() {
    return patterns;
  }

  /**
   * Add new isotope pattern to list (end of list)
   *
   * @param pattern new isotope pattern
   */
  public void addPattern(IsotopePattern pattern) {
    addPattern(pattern, false);
  }

  /**
   * Add new isotope pattern to start of list (if preferred) or end if false
   *
   * @param pattern      new isotope pattern
   * @param setPreferred true: start of list; false: end of list
   */
  public void addPattern(IsotopePattern pattern, boolean setPreferred) {
    if (setPreferred) {
      patterns.add(0, pattern);
    } else {
      patterns.add(pattern);
    }
  }

  /**
   * @param charge the charge state
   * @return returns the isotope pattern with all signals detected for this charge state
   */
  public IsotopePattern getPatternForCharge(int charge) {
    for (var pattern : patterns) {
      if (pattern.getCharge() == charge) {
        return pattern;
      }
    }
    return null;
  }

  /**
   * The preferred isotope pattern is the first in the list. usually charge 1 unless defined
   * differently
   *
   * @return the first isotope pattern
   */
  public IsotopePattern getPreferredIsotopePattern() {
    return patterns.get(0);
  }

  @Override
  public int getNumberOfDataPoints() {
    return getPreferredIsotopePattern().getNumberOfDataPoints();
  }

  @Override
  public int getCharge() {
    return getPreferredIsotopePattern().getCharge();
  }

  @Override
  public @NotNull IsotopePatternStatus getStatus() {
    return getPreferredIsotopePattern().getStatus();
  }

  @Override
  public @Nullable Integer getBasePeakIndex() {
    return getPreferredIsotopePattern().getBasePeakIndex();
  }

  @Override
  public @NotNull String getDescription() {
    return getPreferredIsotopePattern().getDescription();
  }

  @Override
  public String toString() {
    return "Isotope pattern: " + getDescription();
  }

  @Override
  @NotNull
  public Range<Double> getDataPointMZRange() {
    return getPreferredIsotopePattern().getDataPointMZRange();
  }

  @Override
  public @NotNull Double getTIC() {
    return getPreferredIsotopePattern().getTIC();
  }

  @Override
  public MassSpectrumType getSpectrumType() {
    return MassSpectrumType.CENTROIDED;
  }

  @Override
  public double[] getMzValues(@NotNull double[] dst) {
    return getPreferredIsotopePattern().getMzValues(dst);
  }

  @Override
  public double[] getIntensityValues(@NotNull double[] dst) {
    return getPreferredIsotopePattern().getIntensityValues(dst);
  }

  @Override
  public double getMzValue(int index) {
    return getPreferredIsotopePattern().getMzValue(index);
  }

  @Override
  public double getIntensityValue(int index) {
    return getPreferredIsotopePattern().getIntensityValue(index);
  }

  @Override
  @Nullable
  public Double getBasePeakMz() {
    return getPreferredIsotopePattern().getBasePeakMz();
  }

  @Override
  @Nullable
  public Double getBasePeakIntensity() {
    return getPreferredIsotopePattern().getBasePeakIntensity();
  }

  @Override
  public Iterator<DataPoint> iterator() {
    return getPreferredIsotopePattern().iterator();
  }

  @Override
  public Stream<DataPoint> stream() {
    return getPreferredIsotopePattern().stream();
  }

  @Override
  public void saveToXML(XMLStreamWriter writer) throws XMLStreamException {
    writer.writeStartElement(XML_ELEMENT);

    for (IsotopePattern pattern : patterns) {
      pattern.saveToXML(writer);
    }

    writer.writeEndElement();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    MultiChargeStateIsotopePattern that = (MultiChargeStateIsotopePattern) o;
    return patterns.equals(that.patterns);
  }

  @Override
  public int hashCode() {
    return Objects.hash(patterns);
  }
}
