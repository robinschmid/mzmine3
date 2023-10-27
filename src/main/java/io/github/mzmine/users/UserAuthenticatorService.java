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

import io.github.mzmine.users.fx.UserPaneController;
import io.github.mzmine.util.files.FileAndPathUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class UserAuthenticatorService {

  private static final Logger logger = Logger.getLogger(UserAuthenticatorService.class.getName());

  public static void addUserFiles(final List<File> userFiles, final boolean openUserTab) {
    File path = UserFileReader.getUserPath();
    FileAndPathUtil.createDirectory(path);

    String extension = UserFileReader.USER_EXTENSION;
    // check that names are unique also in already available licenses
    Set<String> names = UserFileReader.streamAllUserFiles()
        .map(file -> file.getName().replaceAll(extension, ""))
        .collect(Collectors.toCollection(HashSet::new));

    // TODO decide if user should select new / old license to keep
    int failed = 0;
    int copied = 0;

    for (final File user : userFiles) {
      String basename = user.getName().replaceAll(extension, "");
      String name = basename;
      int counter = 1;
      while (names.contains(name)) {
        name = basename + "_" + counter;
        counter++;
      }
      names.add(name);

      File destination = FileAndPathUtil.getRealFilePath(path, name, extension);
      try {
        Files.copy(user.toPath(), destination.toPath());
        copied++;
      } catch (IOException e) {
        logger.log(Level.WARNING, "Cannot move file to the user folder", e);
        failed++;
      }
    }
    logger.finest("Installed users %d successfully and failed with %d".formatted(copied, failed));
    if (openUserTab) {
      UserPaneController.showUserTab();
    }
  }
}
