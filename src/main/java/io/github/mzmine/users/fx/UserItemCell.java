/*
 * Copyright (c) 2004-2023 The MZmine Development Team
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

package io.github.mzmine.users.fx;

import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.users.ActiveUser;
import io.github.mzmine.users.MZmineUser;
import io.github.mzmine.util.color.SimpleColorPalette;
import io.github.mzmine.util.javafx.FxColorUtil;
import io.github.mzmine.util.javafx.FxIconUtil;
import java.util.List;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kordamp.ikonli.javafx.FontIcon;

public class UserItemCell {

  public GridPane pnIcons;
  public StackPane pnCurrentUser;
  public Label lbActiveUntil;
  public Label lbNickname;
  public BorderPane rootPane;

  public UserItemCell() {
  }

  private static Color getActiveStateColor(final @NotNull MZmineUser user,
      final SimpleColorPalette colors) {
    return switch (user.getActiveState()) {
      case ACTIVE -> colors.getPositiveColor();
      case ACTIVE_BUT_EXCEEDED -> colors.getNeutralColor();
      case EXPIRED, INVALID -> colors.getNegativeColor();
    };
  }

  public BorderPane getRootPane() {
    return rootPane;
  }

  public void setUser(@Nullable MZmineUser user) {
    lbActiveUntil.setText("");
    lbNickname.setText("");
    pnIcons.getChildren().clear();
    pnCurrentUser.getChildren().clear();

    if (user == null) {
      return;
    }

    String acText = user.getActiveUtilMessage();
    String userType = user.getUserType().toString();

    lbActiveUntil.setText(userType + " - " + acText);
    lbNickname.setText(user.getNickname());

    int size = 24;
    SimpleColorPalette colors = MZmineCore.getConfiguration().getDefaultColorPalette();

    // set color of active label
    Color acColor = getActiveStateColor(user, colors);
    lbActiveUntil.setStyle("-fx-text-fill: " + FxColorUtil.colorToHex(acColor));

    // add icons
    List<UserIcon> icons = user.getIcons();

    int maxRows = 1;
    int col = 0;
    int row = 0;
    for (final UserIcon ui : icons) {
      var icon = FxIconUtil.getFontIcon(ui.getIconCode(), size, acColor);
      // tooltip for bootstrap icons does not work. need box around it
      var box = new HBox(icon);
      Tooltip.install(box, new Tooltip(ui.getIconTooltip()));
      pnIcons.add(box, col, row);
      row++;
      if (row % maxRows == 0) {
        row = 0;
        col++;
      }
    }

    // only one user can be active
    if (ActiveUser.isActive(user)) {
      FontIcon icon = FxIconUtil.getFontIcon("bi-check2-circle", 30, acColor);
      var box = new StackPane(icon);
      Tooltip.install(box, new Tooltip("The currently active user"));
      pnCurrentUser.getChildren().add(box);
    }
  }


}

