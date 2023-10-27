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

package io.github.mzmine.users;

import io.github.mzmine.util.files.FileAndPathUtil;
import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javafx.stage.FileChooser.ExtensionFilter;
import javax0.license3j.License;
import javax0.license3j.io.IOFormat;
import javax0.license3j.io.LicenseReader;
import org.jetbrains.annotations.Nullable;

public class UserFileReader {

  public static final String USER_EXTENSION = ".mzuser";
  public static final ExtensionFilter FILE_FILTER = new ExtensionFilter("MZmine users",
      "*" + USER_EXTENSION);
  private static final Logger logger = Logger.getLogger(UserFileReader.class.getName());


  /**
   * User/.mzmine/users/
   */
  @Nullable
  public static File getUserPath() {
    return FileAndPathUtil.getUserSettingsDir("users");
  }

  public static Stream<File> streamAllUserFiles() {
    return FileAndPathUtil.streamFilesInDirFlat(getUserPath(), FILE_FILTER, false);
  }

  public List<MZmineUser> readAllUserFiles() {
    Comparator<MZmineUser> SORTER = Comparator.nullsLast(
        Comparator.comparingLong(MZmineUser::getRemainingDaysActive).reversed());

    List<MZmineUser> users = streamAllUserFiles().map(this::readUserFile).filter(Objects::nonNull)
        .sorted(SORTER).toList();
    for (int i = 0; i < users.size(); i++) {
      users.get(i).setIndex(i);
    }
    return users;
  }

  @Nullable
  public MZmineUser readUserFile(File file) {
    try (final var reader = new LicenseReader(file)) {
      License license = reader.read(IOFormat.BINARY);
      if (license.isOK(UserPubKey.KEY)) {
        return new MZmineUser(file, license);
      } else {
        logger.finest("User file is not signed properly");
        return null;
      }
    } catch (IOException ex) {
      logger.log(Level.WARNING, "Cannot read user from file " + file.getAbsolutePath(), ex);
      return null;
    }
  }

  @Nullable
  public MZmineUser readUserByFileName(final String filename) {
    if (filename == null || filename.isBlank()) {
      return null;
    }
    return streamAllUserFiles().filter(f -> f.getName().equals(filename)).map(this::readUserFile)
        .findFirst().orElse(null);
  }
}
