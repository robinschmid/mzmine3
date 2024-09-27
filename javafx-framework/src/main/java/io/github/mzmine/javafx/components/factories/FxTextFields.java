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

package io.github.mzmine.javafx.components.factories;

import io.github.mzmine.javafx.components.NumberTextField;
import io.github.mzmine.javafx.components.util.FxLayout;
import java.text.NumberFormat;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.Property;
import javafx.beans.property.StringProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FxTextFields {

  // Numbers
  public static TextField newNumberField(@Nullable Integer columnCount,
      @NotNull NumberFormat format, @Nullable ObjectProperty<Number> valueProperty,
      @Nullable String tooltip) {
    return newNumberField(columnCount, format, valueProperty, null, tooltip);
  }

  public static TextField newNumberField(@Nullable Integer columnCount,
      @NotNull NumberFormat format, @Nullable ObjectProperty<Number> valueProperty,
      @Nullable String prompt, @Nullable String tooltip) {
    var field = new NumberTextField(format);
    applyToField(field, columnCount, null, prompt, tooltip);
    field.valueProperty().bindBidirectional(valueProperty);
    return field;
  }

  // strings
  public static TextField newTextField(@Nullable Integer columnCount,
      @Nullable StringProperty textProperty, @Nullable String tooltip) {
    return newTextField(columnCount, textProperty, null, tooltip);
  }

  public static TextField newTextField(@Nullable Integer columnCount,
      @Nullable StringProperty textProperty, @Nullable String prompt, @Nullable String tooltip) {
    return applyToField(new TextField(), columnCount, textProperty, prompt, tooltip);
  }

  public static TextField applyToField(@NotNull final TextField field,
      final @Nullable Integer columnCount, final @Nullable String tooltip) {
    return applyToField(field, columnCount, null, tooltip);
  }

  public static TextField applyToField(@NotNull final TextField field,
      final @Nullable Integer columnCount, final @Nullable StringProperty textProperty,
      final @Nullable String tooltip) {
    return applyToField(field, columnCount, textProperty, null, tooltip);
  }

  public static TextField applyToField(@NotNull final TextField field,
      final @Nullable Integer columnCount, final @Nullable StringProperty textProperty,
      final @Nullable String prompt, final @Nullable String tooltip) {
    if (textProperty != null) {
      field.textProperty().bindBidirectional(textProperty);
    }
    if (prompt != null) {
      field.setPromptText(prompt);
    }
    if (tooltip != null) {
      field.setTooltip(new Tooltip(tooltip));
    }
    if (columnCount == null) {
      field.setPrefColumnCount(columnCount);
    }
    return field;
  }

  public static PasswordField newPasswordField(@Nullable Integer columnCount,
      @Nullable Property<StringBuilder> passwordProperty, @Nullable String prompt,
      @Nullable String tooltip) {
    var passField = (PasswordField) applyToField(new PasswordField(), columnCount, null, prompt,
        tooltip);
    if (passwordProperty != null) {
      passwordProperty.setValue((StringBuilder) passField.getCharacters());
    }
    return passField;
  }

  /**
   * Adds a password field and button to trigger an action event like login.
   *
   * @param passwordProperty binds the password but gets cleared right after action
   * @param onAction         this action will trigger on enter in passwordfield or on button click
   *                         and then clear the password field right after.
   * @return a wrapper around the field and button
   */
  public static HBox newPasswordFieldButton(@Nullable Integer columnCount,
      @Nullable Property<StringBuilder> passwordProperty, @Nullable String tooltip,
      String buttonLabel, Runnable onAction) {
    return newPasswordFieldButton(columnCount, passwordProperty, null, tooltip, buttonLabel,
        onAction);
  }

  /**
   * Adds a password field and button to trigger an action event like login.
   *
   * @param passwordProperty binds the password but gets cleared right after action
   * @param prompt           in PasswordField
   * @param onAction         this action will trigger on enter in passwordfield or on button click
   *                         and then clear the password field right after.
   * @return a wrapper around the field and button
   */
  public static HBox newPasswordFieldButton(@Nullable Integer columnCount,
      @Nullable Property<StringBuilder> passwordProperty, @Nullable String prompt,
      @Nullable String tooltip, String buttonLabel, @NotNull Runnable onAction) {
    final var passwordField = newPasswordField(columnCount, passwordProperty, prompt, tooltip);
    EventHandler<ActionEvent> clearAfterHandler = _ -> {
      onAction.run();
      passwordField.clear();
    };
    passwordField.setOnAction(clearAfterHandler);

    return FxLayout.newHBox(Insets.EMPTY, passwordField,
        FxButtons.createButton(buttonLabel, tooltip, clearAfterHandler));
  }
}
