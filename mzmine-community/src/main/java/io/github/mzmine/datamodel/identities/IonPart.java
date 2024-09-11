/*
 * Copyright (c) 2004-2024 The mzmine Development Team
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package io.github.mzmine.datamodel.identities;

import static java.util.Objects.requireNonNullElse;

import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.main.ConfigService;
import io.github.mzmine.util.FormulaUtils;
import io.github.mzmine.util.ParsingUtils;
import io.github.mzmine.util.StringUtils;
import io.github.mzmine.util.maths.Precision;
import java.util.Comparator;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openscience.cdk.interfaces.IMolecularFormula;

/**
 * A single part in an IonType: like 2Na or -H
 *
 * @param name          clear name - often derived from formula or from alternative names
 * @param singleFormula uncharged formula without multiplier formula may be null if unknown. Formula
 *                      of a single item - so the count multiplier is not added
 * @param absSingleMass absolute (positive) mass of a single item of this type which is multiplied
 *                      by count to get total mass.
 * @param singleCharge  signed charge of a single item which is multiplied by count to get total
 *                      charge
 * @param count         the singed multiplier of this single item, non-zero. e.g., 2 for 2Na and -1
 *                      for -H
 */
public record IonPart(@NotNull String name, @Nullable IMolecularFormula singleFormula,
                      double absSingleMass, int singleCharge, int count) {

  private static final Logger logger = Logger.getLogger(IonPart.class.getName());
  public static final String XML_ELEMENT = "ionpart";
  /**
   * non charged then charged modifications. Each sorted by name. Used to create the name of
   * {@link IonType}
   */
  public static final Comparator<IonPart> DEFAULT_NAME_SORTER = Comparator.comparing(
      IonPart::isCharged).thenComparing(IonPart::name);

  /**
   * sort by charge and then mass
   */
  public static final Comparator<IonPart> DEFAULT_CHARGE_MASS_SORTER = Comparator.comparing(
      IonPart::singleCharge).thenComparing(IonPart::absSingleMass);

  public static final Pattern PART_PATTERN = Pattern.compile("([+-]?\\d*)(\\w+)");


  public IonPart(@NotNull final String name, @Nullable final IMolecularFormula singleFormula,
      final double absSingleMass, final int singleCharge, final int count) {
    this.name = name;
    this.singleFormula = singleFormula;
    this.absSingleMass = Math.abs(absSingleMass); // allways positive and then multiplied with count
    this.singleCharge = singleCharge;
    this.count = count;
  }

  /**
   * Formula constructor with count 1
   *
   * @param formula used to calculate other fields
   */
  public IonPart(@NotNull final String formula, final int singleCharge) {
    this(formula, singleCharge, 1);
  }

  /**
   * Formula constructor
   *
   * @param formula used to calculate other fields
   */
  public IonPart(@NotNull final String formula, final int singleCharge, final int count) {
    this(Objects.requireNonNull(
        FormulaUtils.createMajorIsotopeMolFormulaWithCharge(formula, singleCharge)), count);
  }

  public IonPart(@NotNull final IMolecularFormula formula, final int count) {
    this(FormulaUtils.getFormulaString(formula, false), formula,
        requireNonNullElse(formula.getCharge(), 0), count);
  }

  public IonPart(@NotNull final IMolecularFormula formula, final int singleCharge,
      final int count) {
    this(FormulaUtils.getFormulaString(formula, false), formula, singleCharge, count);
  }

  /**
   * No formula constructor
   */
  public IonPart(@NotNull String name, final double singleMass, final int singleCharge) {
    this(name, singleMass, singleCharge, 1);
  }

  /**
   * No formula constructor
   */
  public IonPart(@NotNull String name, final double singleMass, final int singleCharge,
      final int count) {
    this(name, null, singleMass, singleCharge, count);
  }

  public IonPart(@NotNull String name, @NotNull String formula, final int singleCharge) {
    this(name, formula, singleCharge, 1);
  }

  public IonPart(@NotNull String name, @NotNull String formula, final int singleCharge,
      final int count) {
    this(name, Objects.requireNonNull(
            FormulaUtils.createMajorIsotopeMolFormulaWithCharge(formula, singleCharge)), singleCharge,
        count);
  }

  public IonPart(@NotNull String name, @NotNull IMolecularFormula formula, final int singleCharge,
      final int count) {
    this(name, formula, FormulaUtils.getMonoisotopicMass(formula, singleCharge), singleCharge,
        count);
  }


  @Nullable
  public static IonPart parse(@NotNull String part) {
    // mod is +Na or -H so with sign and multiplier -2H
    var matcher = PART_PATTERN.matcher(part);
    if (!matcher.matches()) {
      return null;
    }

    // need +H or -H2O to get the correct part
    String sign = StringUtils.orDefault(matcher.group(1), "+").trim();
    int count = sign.length() == 1 ? (sign.equals("-") ? -1 : 1) : Integer.parseInt(sign);

    String name = StringUtils.orDefault(matcher.group(2), "").trim();
    // try to find predefined parts by name
    return IonParts.findPartByNameOrFormula(name, count);
  }

  /**
   * @param signedCount - or + count here to see if this is a loss or addition
   */
  public static IonPart unknown(final String name, final int signedCount) {
    // need to add a tiny mass difference to allow - or + in toString
    //
    return new IonPart(name, null, 0d, 0, signedCount);
  }

  public boolean isUnknown() {
    return singleFormula == null && absSingleMass == 0d;
  }

  /**
   * Creates the final part string with mass and charge see {@link #toString(IonStringFlavor)} with
   * {@link IonStringFlavor#FULL}
   *
   * @return sign count name charge (mass)
   */
  @Override
  public String toString() {
    return toString(IonStringFlavor.FULL);
  }

  public String toString(IonStringFlavor flavor) {
    if (name.isBlank()) {
      // e,g, {@link IonParts#}
      return "";
    }
    String base = IonUtils.getSignedNumberOmit1(count) + name;
    return switch (flavor) {
      case SIMPLE_NO_CHARGE -> base;
      case SIMPLE_WITH_CHARGE -> base + IonUtils.getChargeString(singleCharge());
      case FULL ->
          base + IonUtils.getChargeString(singleCharge()) + " (" + ConfigService.getExportFormats()
              .mz(absSingleMass()) + " Da)";
    };
  }

  /**
   * @return A merged IonPart if both ions match completely, excluding their count field. Otherwise,
   * null or null if both a and b are null.
   */
  @Nullable
  public static IonPart merge(@Nullable IonPart a, @Nullable IonPart b) {
    if (a == null && b == null) {
      return null;
    }
    if (a == null) {
      return b;
    }
    if (b == null) {
      return a;
    }
    if (!a.equals(b)) {
      throw new IllegalArgumentException(
          "A and B define IonParts with different properties and cannot be merged %s and %s".formatted(
              a, b));
    }
    return a.withCount(a.count + b.count);
  }

  /**
   * Flip count to flip the effective total mass and charge
   *
   * @return Same formula, e.g., conversion from +Na to -Na
   */
  public IonPart flipCount() {
    return new IonPart(name, singleFormula, absSingleMass, singleCharge, -count);
  }

  public IonPart withCount(final int count) {
    if (count == this.count) {
      return this;
    }
    return new IonPart(name, singleFormula, absSingleMass, singleCharge, count);
  }


  public int totalCharge() {
    return singleCharge * count;
  }

  public int absTotalCharge() {
    return Math.abs(totalCharge());
  }

  public boolean isCharged() {
    return singleCharge != 0;
  }

  /**
   * Polarity of total charge so charge * count which may flip sign of singleCharge
   */
  public PolarityType totalChargePolarity() {
    return totalCharge() < 0 ? PolarityType.NEGATIVE : PolarityType.POSITIVE;
  }

  public double totalMass() {
    return absSingleMass * count;
  }

  public double absTotalMass() {
    return Math.abs(totalMass());
  }

  public String partSign() {
    return isLoss() ? "-" : "+";
  }

  public boolean isLoss() {
    return count < 0;
  }

  public boolean isAddition() {
    return count >= 0;
  }


  public boolean isNeutralModification() {
    return !isCharged();
  }

  public Type type() {
    if (isCharged()) {
      return Type.ADDUCT;
    }
    return isLoss() ? Type.IN_SOURCE_FRAGMENT : Type.CLUSTER;
  }


  /**
   * Exclude count from equals and hash so that duplicate elements can be more easily merged
   *
   * @param o the reference object with which to compare.
   * @return true if equals
   */
  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof final IonPart ionPart)) {
      return false;
    }

    return singleCharge == ionPart.singleCharge && Precision.equals(absSingleMass,
        ionPart.absSingleMass, 0.0000000) && name.equals(ionPart.name) && Objects.equals(
        singleFormula, ionPart.singleFormula);
  }

  @Override
  public int hashCode() {
    int result = name.hashCode();
    result = 31 * result + Objects.hashCode(singleFormula);
    result = 31 * result + Double.hashCode(absSingleMass);
    result = 31 * result + singleCharge;
    return result;
  }


  public void saveToXML(XMLStreamWriter writer) throws XMLStreamException {
    writer.writeStartElement(XML_ELEMENT);
    writer.writeAttribute("name", name);
    writer.writeAttribute("mass", String.valueOf(absSingleMass));
    writer.writeAttribute("charge", String.valueOf(singleCharge));
    writer.writeAttribute("count", String.valueOf(count));
    if (singleFormula != null) {
      writer.writeAttribute("formula", FormulaUtils.getFormulaString(singleFormula, false));
    }
    writer.writeEndElement();
  }

  public static IonPart loadFromXML(XMLStreamReader reader) throws XMLStreamException {
    if (!(reader.isStartElement() && reader.getLocalName().equals("ionpart"))) {
      throw new IllegalStateException("Current element is not an ionpart");
    }

    final Integer charge = ParsingUtils.stringToInteger(reader.getAttributeValue(null, "charge"));
    final Integer count = ParsingUtils.stringToInteger(reader.getAttributeValue(null, "count"));
    final Double mass = ParsingUtils.stringToDouble(reader.getAttributeValue(null, "mass"));
    final String name = reader.getAttributeValue(null, "name");
    Objects.requireNonNull(charge);
    Objects.requireNonNull(count);
    Objects.requireNonNull(mass);
    Objects.requireNonNull(name);
    // may be null
    final String formula = reader.getAttributeValue(null, "formula");
    final IMolecularFormula parsedFormula =
        formula == null ? null : FormulaUtils.createMajorIsotopeMolFormulaWithCharge(formula);

    return new IonPart(name, parsedFormula, mass, charge, count);
  }

  /**
   * @param formula changed in place
   * @param ionize  ionize formula if part has charge
   */
  public void addToFormula(final IMolecularFormula formula, final boolean ionize) {
    final int formulaCharge = requireNonNullElse(formula.getCharge(), 0);
    if (ionize) {
      formula.setCharge(formulaCharge + totalCharge());
    }
    if (singleFormula == null) {
      return;
    }
    for (int i = 0; i < Math.abs(count); i++) {
      if (isLoss()) {
        FormulaUtils.subtractFormula(formula, singleFormula);
      } else {
        FormulaUtils.addFormula(formula, singleFormula);
      }
    }
  }

  /**
   * @return silent charge is the only blank name with charge 1
   */
  public boolean isSilentCharge() {
    return name.isBlank() && singleFormula == null && singleCharge == 1;
  }


  public enum Type {
    /**
     * has charge, positive or negative mass
     */
    ADDUCT,
    /**
     * negative mass, no charge
     */
    IN_SOURCE_FRAGMENT,
    /**
     * positive mass, no charge
     */
    CLUSTER

  }

  public enum IonStringFlavor {

    /**
     * including count, name, charge, mass: +2Na+ (absSingleMass Da)
     */
    FULL,
    /**
     * count and name: +2Na
     */
    SIMPLE_NO_CHARGE,
    /**
     * count, name, charge: +2Na+
     */
    SIMPLE_WITH_CHARGE

  }
}