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

import io.github.mzmine.gui.mainwindow.SimpleTab;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.users.ActiveUser;
import io.github.mzmine.users.MZmineUser;
import io.github.mzmine.users.UserAuthenticatorService;
import io.github.mzmine.users.UserFileReader;
import io.github.mzmine.util.files.FileAndPathUtil;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;

public class UserPaneController {

  private static final Logger logger = Logger.getLogger(UserPaneController.class.getName());
  @FXML
  private ListView<MZmineUser> listViewLicenses;
  @FXML
  private TextArea textLicense;

  public static synchronized void showUserTab() {
    MZmineCore.runLater(() -> {
      UserTab userTab = UserTab.userTab;
      if (userTab == null) {
        logger.warning("Cannot open user tab as user tab is null");
        return;
      }
      MZmineCore.getDesktop().addTab(userTab);
      userTab.getController().loadUsers();
    });
  }

  private void loadUsers() {
    logger.fine("Loading users into tab");
    UserFileReader reader = new UserFileReader();
    List<MZmineUser> users = reader.readAllUserFiles();

    listViewLicenses.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
    listViewLicenses.setCellFactory(param -> new ListCell<>() {
      // load pane for each cell
      private final UserItemCell cell = createUserCell();

      @Override
      protected void updateItem(final MZmineUser user, final boolean empty) {
        super.updateItem(user, empty);
        if (!empty) {
          setGraphic(cell.getRootPane());
          cell.setUser(user);
        }
      }
    });
    listViewLicenses.setItems(FXCollections.observableArrayList(users));

    listViewLicenses.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
      if (nv == null) {
        textLicense.setText("");
        return;
      }
      textLicense.setText(nv.getLicenseText());

      setActiveUser(nv);
    });

    // select active user if any
    listViewLicenses.getItems().stream().filter(ActiveUser::isActive).findFirst()
        .ifPresent(user -> listViewLicenses.getSelectionModel().select(user));
  }

  private void setActiveUser(final MZmineUser user) {
    ActiveUser.setUser(user);
  }

  public UserItemCell createUserCell() {
    try {
      FXMLLoader loader = new FXMLLoader(UserItemCell.class.getResource("UserItemCell.fxml"));
      loader.load();
      return loader.getController();
    } catch (IOException e) {
      logger.log(Level.WARNING, "Cannot load user cell", e);
      return null;
    }
  }

  public Optional<MZmineUser> getSelectedUser() {
    return Optional.ofNullable(listViewLicenses.getSelectionModel().getSelectedItem());
  }

  @FXML
  void onRemoveUser(ActionEvent event) {
    getSelectedUser().ifPresent(user -> {
      // delete license file
      boolean deleted = FileAndPathUtil.delete(user.getFile());
      if (deleted) {
        listViewLicenses.getItems().remove(user);
      }
    });
  }

  @FXML
  void onSignIn(ActionEvent event) {

  }

  /**
   * Loads external user files and adds them to the user folder
   */
  public void loadExternalUserFiles(final ActionEvent event) {
    FileChooser chooser = new FileChooser();
    chooser.getExtensionFilters().add(UserFileReader.FILE_FILTER);
    chooser.setSelectedExtensionFilter(UserFileReader.FILE_FILTER);
    List<File> files = chooser.showOpenMultipleDialog(null);
    if (files != null && !files.isEmpty()) {
      UserAuthenticatorService.addUserFiles(files, true);
    }
  }

  /**
   * Holds a single instance of the user tab
   */
  static class UserTab extends SimpleTab {

    // singleton by inner class
    public static final UserTab userTab = createUserTab();
    private final UserPaneController controller;

    private UserTab(UserPaneController controller, final BorderPane rootPane) {
      super("Users", rootPane);
      this.controller = controller;
    }

    private static UserTab createUserTab() {
      try {
        logger.finest("User tab created");
        // Load the window FXML
        URL mainFXML = UserPaneController.class.getResource("UserPane.fxml");
        FXMLLoader loader = new FXMLLoader(mainFXML);
        BorderPane rootPane = loader.load();

        return new UserTab(loader.getController(), rootPane);
      } catch (IOException e) {
        logger.log(Level.WARNING, "Cannot open user dialog " + e.getMessage(), e);
      }
      return null;
    }

    public UserPaneController getController() {
      return controller;
    }
  }


}
