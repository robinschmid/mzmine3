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

import io.github.mzmine.gui.preferences.MZminePreferences;
import io.github.mzmine.main.MZmineCore;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Holds a single instance of the active user
 */
public class ActiveUser {

  private static MZmineUser user;

  /**
   * get the active user
   */
  @Nullable
  public static MZmineUser getUser() {
    return user;
  }

  /**
   * Set the active user
   */
  public static synchronized void setUser(final @Nullable MZmineUser user) {
    ActiveUser.user = user;
    // save to preferences so that this user is initialized during startup
    String fileName = user == null ? "" : user.getFile().getName();
    MZmineCore.getConfiguration().getPreferences()
        .setParameter(MZminePreferences.lastActiveUserFileName, fileName);
  }

  /**
   * @return true if the current user is still valid
   */
  public static boolean isValid() {
    return user != null && user.isValid();
  }

  /**
   * @return true if the current user is invalid
   */
  public static boolean isInvalid() {
    return !isValid();
  }


  /**
   * List of active services or empty list
   *
   * @return list of active services or empty list if no user defined
   */
  @NotNull
  public static List<UserActiveService> getServices() {
    if (user == null) {
      return List.of();
    }
    return user.getServices();
  }

  public static String getUserDescription() {
    if (user == null) {
      return "No active user";
    }
    return user.toString();
  }

  public static String getRequiredUserInfo() {
    return """
        Requires an active user that can be set via the -u "FULL FILE PATH.mzuser" command line argument,
        or by opening MZmine in graphical user mode and log in / sign up.
        The user file is available after sign up with various options:
           1. Community edition: Free for academics and other non-profits
           2. PRO edition: Licensed through ...
           3. limited time trials""";
  }

  /**
   * Checks if the users are the same by checking the loaded license file
   *
   * @param other user to be checked against active user
   * @return true if the users were loaded from the same file
   */
  public static boolean isActive(final MZmineUser other) {
    if (other == null || user == null) {
      return false;
    }
    return Objects.equals(other.getFile().getName(), user.getFile().getName());
  }
}
