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

package io.github.mzmine.util.components;

import static io.github.mzmine.util.StringUtils.hasValue;

import javafx.beans.property.ObjectProperty;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import net.synedra.validatorfx.Validator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openscience.cdk.interfaces.IMolecularFormula;

public class FormulaTextField extends TextField {

  private final TextFormatter<IMolecularFormula> textFormatter;

  /**
   * Shows charge and allows empty value
   */
  public FormulaTextField() {
    this(true, false);
  }

  public FormulaTextField(boolean requireValue) {
    this(true, requireValue);
  }

  public FormulaTextField(boolean showCharge, final boolean requireValue) {
    final IMolecularFormulaStringConverter stringConverter = new IMolecularFormulaStringConverter(
        showCharge);
    textFormatter = new TextFormatter<>(stringConverter);
    setTextFormatter(textFormatter);

    // validation support after text formatter - maybe conflict of order with converting the types
//    var validSupport = new ValidationSupport();
//    validSupport.registerValidator(this,
//        (Control c, String value) -> ValidationResult.fromErrorIf(this, "Formula is invalid",
//            StringUtils.hasValue(value) && formula.get() == null));
//    validSupport.registerValidator(this, Validator.<String>createPredicateValidator(
//        value -> StringUtils.hasValue(value) && getFormula() == null, "Formula is invalid"));
    new Validator().createCheck() //
        .dependsOn("formula", formulaProperty()) //
//        .dependsOn("text", textProperty()) //
        .decorates(this).withMethod(c -> {
          IMolecularFormula formula = c.get("formula");
//      String text = c.get("text");
          if ((requireValue || hasValue(getText())) && formula == null) {
            String format = showCharge ? "[Fe]+2" : "Fe";
            c.error("Cannot parse formula, required format: " + format);
          }
        }).immediate();
  }

  /**
   * Create formula field and bind bidirectionally
   *
   * @param showCharge      show charge in formula
   * @param formulaProperty bind bidirectional
   * @return formula field
   */
  public static FormulaTextField newFormulaTextField(final boolean showCharge,
      final boolean requireValue,
      final @NotNull ObjectProperty<@Nullable IMolecularFormula> formulaProperty) {
    var field = new FormulaTextField(showCharge, requireValue);
    field.formulaProperty().bindBidirectional(formulaProperty);
    return field;
  }

  /**
   * Create formula field and bind bidirectionally
   *
   * @param formulaProperty bind bidirectional
   * @return formula field
   */
  public static FormulaTextField newFormulaTextField(
      final @NotNull ObjectProperty<@Nullable IMolecularFormula> formulaProperty) {
    return newFormulaTextField(false, false, formulaProperty);
  }


  public @Nullable IMolecularFormula getFormula() {
    return formulaProperty().get();
  }

  public ObjectProperty<@Nullable IMolecularFormula> formulaProperty() {
    return textFormatter.valueProperty();
  }

  public void setFormula(@Nullable IMolecularFormula formula) {
    this.formulaProperty().set(formula);
  }
}
