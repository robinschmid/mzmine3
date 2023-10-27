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

import io.github.mzmine.users.MZmineUser;
import io.github.mzmine.users.UserActiveService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Icons describing the user type, additional services, validity
 */
public enum UserIcon {
  // User type
  PRO, NON_PROFIT, BUGGY_USER,

  // services
  PRO_WORKSPACES, COMMUNITY, SPECTRAL_LIBRARIES,

  // web
  WEBSERVICES, RECEPTION1, RECEPTION4,

  // time
  USER_VALID_LONG, USER_VALID_HALFUP, USER_VALID_SOONUP, USER_EXCEEDED;

  public static List<UserIcon> getIcons(final MZmineUser user) {
    List<UserIcon> icons = new ArrayList<>();
    var icon = switch (user.getUserType()) {
      case PRO -> PRO;
      case NON_PROFIT -> NON_PROFIT;
      case UNVALIDATED -> BUGGY_USER;
    };
    icons.add(icon);
    icons.add(iconForRemainingDays(user.getRemainingDaysActive()));

    icons.addAll(user.getServices().stream().map(UserIcon::getServiceIcon).toList());

    return icons.stream().filter(Objects::nonNull).distinct().toList();
  }

  private static UserIcon getServiceIcon(final UserActiveService service) {
    return switch (service) {
      case PRO_WORKSPACES -> PRO_WORKSPACES;
      case COMMUNITY -> COMMUNITY;
      case WEBSERVICES -> WEBSERVICES;
      case SPECTRAL_LIBRARIES -> SPECTRAL_LIBRARIES;
    };
  }

  private static UserIcon iconForRemainingDays(final long days) {
    if (days < 0) {
      return USER_EXCEEDED;
    } else if (days < 30) {
      return USER_VALID_SOONUP;
    } else if (days > 90) {
      return USER_VALID_LONG;
    } else {
      return USER_VALID_HALFUP;
    }
  }

  public String getIconCode() {
    return switch (this) {
      case PRO -> "bi-gem";
      case NON_PROFIT -> "bi-person-circle";
      case BUGGY_USER -> "bi-bug";
      // SERVICES
      case PRO_WORKSPACES -> "bi-layers-half";
      case COMMUNITY -> "bi-layout-sidebar";
      case SPECTRAL_LIBRARIES -> "bi-unlock";
      case WEBSERVICES -> "bi-cloud-arrow-up";
      // LATER FOR WEBSERVICES?
      case RECEPTION1 -> "bi-reception-1";
      case RECEPTION4 -> "bi-reception-4";
      // HOW LONG STILL ACTIVE?
      case USER_VALID_LONG -> "bi-hourglass-top";
      case USER_VALID_HALFUP -> "bi-hourglass-split";
      case USER_VALID_SOONUP -> "bi-hourglass-bottom";
      case USER_EXCEEDED -> "bi-hourglass";
    };
  }

  public String getIconTooltip() {
    return switch (this) {
      case PRO -> "PRO user";
      case NON_PROFIT -> "Non-profit/academic user";
      case BUGGY_USER -> "User has issues and needs to sign in again";
      case PRO_WORKSPACES -> "PRO workspaces available";
      case COMMUNITY -> "Community edition powered by open-source";
      case SPECTRAL_LIBRARIES -> "Access to paid spectral libraries";
      case WEBSERVICES -> "Has access to webservices";
      case RECEPTION1 -> "";
      case RECEPTION4 -> "";
      case USER_VALID_LONG -> "User is valid";
      case USER_VALID_HALFUP -> "User is valid for some more time";
      case USER_VALID_SOONUP -> "User needs to sign in and validate soon";
      case USER_EXCEEDED -> "User needs to sign in to validate";
    };
  }

}
