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

import io.github.mzmine.users.fx.UserIcon;
import java.io.File;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax0.license3j.Feature;
import javax0.license3j.License;
import org.jetbrains.annotations.NotNull;

public class MZmineUser {

  private static final Logger logger = Logger.getLogger(MZmineUser.class.getName());
  @NotNull
  private final List<UserIcon> icons;
  @NotNull
  private final List<UserActiveService> services;
  @NotNull
  private final File file;
  @NotNull
  private final License license;
  private final UserActiveState activeState;
  private int index = 0;

  MZmineUser(final @NotNull File file, final @NotNull License license) {
    this.file = file;
    this.license = license;
    UserActiveState activeState = evaluateActiveDate();

    List<UserActiveService> services;
    try {
      services = Arrays.stream(license.get(UserFeatures.SERVICES.toString()).getString().split(","))
          .map(UserActiveService::valueOf).toList();
    } catch (Exception ex) {
      logger.warning("Cannot parse service from user. Please sign in and reactivate this user.");
      services = List.of();
      activeState = UserActiveState.INVALID;
    }
    this.services = services;
    this.activeState = activeState;
    icons = UserIcon.getIcons(this);
  }

  private UserActiveState evaluateActiveDate() {
    try {
      long daysActive = getRemainingDaysActive();
      if (daysActive >= 0) {
        return UserActiveState.ACTIVE;
      } else if (daysActive < -14) {
        return UserActiveState.EXPIRED;
      } else {
        return UserActiveState.ACTIVE_BUT_EXCEEDED;
      }
    } catch (UserException e) {
      logger.finest(e.getMessage());
      return UserActiveState.INVALID;
    }
  }

  public File getFile() {
    return file;
  }

  @NotNull
  public List<UserActiveService> getServices() {
    return services;
  }

  public Date getActiveUntilDate() throws UserException {
    Feature feature = license.get(UserFeatures.ACTIVE_UNTIL_DATE.getId());
    if (feature == null) {
      throw new UserException("Active until date missing");
    } else if (!feature.isDate()) {
      throw new UserException("Active until date is mal formatted");
    }
    return feature.getDate();
  }

  public long getRemainingDaysActive() throws UserException {
    return Duration.between(Instant.now(), getActiveUntilDate().toInstant()).toDays() + 1;
  }

  /**
   * Nickname was given during generation of the license
   */
  public String getNickname() {
    return get(UserFeatures.NICKNAME).map(Feature::getString).orElse("NONAME");
  }

  /**
   * User types
   */
  public UserType getUserType() {
    return get(UserFeatures.USER_TYPE).map(Feature::getString).map(UserType::valueOf)
        .orElse(UserType.UNVALIDATED);
  }

  public Optional<Feature> get(UserFeatures uf) {
    return Optional.ofNullable(license.get(uf.toString()));
  }

  public Optional<Feature> get(String uf) {
    return Optional.ofNullable(license.get(uf));
  }

  /**
   * @return true if date is still not exceeded or if within grace period
   */
  public boolean isValid() {
    return activeState.isActive();
  }

  public UserActiveState getActiveState() {
    return activeState;
  }

  public String getLicenseText() {
    return license.toString();
  }

  public int getIndex() {
    return index;
  }

  public void setIndex(final int index) {
    this.index = index;
  }

  public List<UserIcon> getIcons() {
    return icons;
  }

  @Override
  public String toString() {
    String services = getServices().stream().map(Objects::toString)
        .collect(Collectors.joining(", "));
    return """
        User: %s
        Valid: %s
        Services: %s
        """.formatted(getNickname(), getActiveUtilMessage(), services);
  }

  /**
   * @return message about the duration of validity
   */
  @NotNull
  public String getActiveUtilMessage() {
    Date untilDate = getActiveUntilDate();
    long days = getRemainingDaysActive();
    var dateStr = untilDate == null ? "" : new SimpleDateFormat("MMM dd, yyyy").format(untilDate);

    String activeLabel = getActiveState().toLabel();

    return switch (getActiveState()) {
      case ACTIVE -> "ACTIVE until %s (%d days)".formatted(dateStr, days);
      case ACTIVE_BUT_EXCEEDED, INVALID, EXPIRED -> activeLabel;
    };
  }
}
