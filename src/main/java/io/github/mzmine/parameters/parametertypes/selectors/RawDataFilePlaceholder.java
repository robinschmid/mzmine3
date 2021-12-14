/*
 * Copyright 2006-2021 The MZmine Development Team
 *
 * This file is part of MZmine.
 *
 * MZmine is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MZmine; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */

package io.github.mzmine.parameters.parametertypes.selectors;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.MassList;
import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.features.FeatureList.FeatureListAppliedMethod;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.util.MemoryMapStorage;
import java.awt.Color;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RawDataFilePlaceholder implements RawDataFile {

  private final String name;
  private final String absPath;
  @Nullable
  private final Integer fileHashCode;

  public RawDataFilePlaceholder(@NotNull final RawDataFile file) {
    name = file.getName();
    absPath = file.getAbsolutePath();
    if(file instanceof RawDataFilePlaceholder rfp) {
      if(rfp.fileHashCode == null) {
        fileHashCode = null;
      } else {
        fileHashCode = rfp.fileHashCode;
      }
    }
    else {
      fileHashCode = file.hashCode();
    }
  }

  public RawDataFilePlaceholder(@NotNull String name, @Nullable String absPath) {
    this(name, absPath, null);
  }

  public RawDataFilePlaceholder(@NotNull String name, @Nullable String absPath,
      @Nullable Integer fileHashCode) {
    this.name = name;
    this.absPath = absPath;
    this.fileHashCode = fileHashCode;
  }

  public @Nullable Integer getFileHashCode() {
    return fileHashCode;
  }

  /**
   * @return The first matching raw data file of the current project.
   */
  @Nullable
  public RawDataFile getMatchingFile() {
    final MZmineProject proj = MZmineCore.getProjectManager().getCurrentProject();
    if (proj == null) {
      return null;
    }

    return proj.getCurrentRawDataFiles().stream().filter(this::matches).findFirst().orElse(null);
  }

  public boolean matches(@Nullable final RawDataFile file) {
    return file != null && file.getName().equals(name) && Objects
        .equals(absPath, file.getAbsolutePath()) && (fileHashCode == null || Objects
        .equals(fileHashCode, file.hashCode()));
  }

  @Override
  public String toString() {
    return name;
  }

  @Override
  public @NotNull String getName() {
    return name;
  }

  @Override
  public @Nullable String getAbsolutePath() {
    return absPath;
  }

  @Override
  public String setName(@NotNull String name) {
    throw new UnsupportedOperationException(
        "This class is only to be used in the RawDataFilesSelection and does not support the required operation.");
  }

  @Override
  public int getNumOfScans() {
    throw new UnsupportedOperationException(
        "This class is only to be used in the RawDataFilesSelection and does not support the required operation.");
  }

  @Override
  public int getNumOfScans(int msLevel) {
    throw new UnsupportedOperationException(
        "This class is only to be used in the RawDataFilesSelection and does not support the required operation.");
  }

  @Override
  public int getMaxCentroidDataPoints() {
    throw new UnsupportedOperationException(
        "This class is only to be used in the RawDataFilesSelection and does not support the required operation.");
  }

  @Override
  public int getMaxRawDataPoints() {
    throw new UnsupportedOperationException(
        "This class is only to be used in the RawDataFilesSelection and does not support the required operation.");
  }

  @Override
  public @NotNull int[] getMSLevels() {
    throw new UnsupportedOperationException(
        "This class is only to be used in the RawDataFilesSelection and does not support the required operation.");
  }

  @Override
  public @NotNull List<Scan> getScanNumbers(int msLevel) {
    throw new UnsupportedOperationException(
        "This class is only to be used in the RawDataFilesSelection and does not support the required operation.");
  }

  @Override
  public @NotNull Scan[] getScanNumbers(int msLevel, @NotNull Range<Float> rtRange) {
    throw new UnsupportedOperationException(
        "This class is only to be used in the RawDataFilesSelection and does not support the required operation.");
  }

  @Override
  public Scan getScanNumberAtRT(float rt, int mslevel) {
    throw new UnsupportedOperationException(
        "This class is only to be used in the RawDataFilesSelection and does not support the required operation.");
  }

  @Override
  public Scan getScanNumberAtRT(float rt) {
    throw new UnsupportedOperationException(
        "This class is only to be used in the RawDataFilesSelection and does not support the required operation.");
  }

  @Override
  public @NotNull Range<Double> getDataMZRange() {
    throw new UnsupportedOperationException(
        "This class is only to be used in the RawDataFilesSelection and does not support the required operation.");
  }

  @Override
  public @NotNull Range<Float> getDataRTRange() {
    throw new UnsupportedOperationException(
        "This class is only to be used in the RawDataFilesSelection and does not support the required operation.");
  }

  @Override
  public @NotNull Range<Double> getDataMZRange(int msLevel) {
    throw new UnsupportedOperationException(
        "This class is only to be used in the RawDataFilesSelection and does not support the required operation.");
  }

  @Override
  public @NotNull Range<Float> getDataRTRange(Integer msLevel) {
    throw new UnsupportedOperationException(
        "This class is only to be used in the RawDataFilesSelection and does not support the required operation.");
  }

  @Override
  public double getDataMaxBasePeakIntensity(int msLevel) {
    throw new UnsupportedOperationException(
        "This class is only to be used in the RawDataFilesSelection and does not support the required operation.");
  }

  @Override
  public double getDataMaxTotalIonCurrent(int msLevel) {
    throw new UnsupportedOperationException(
        "This class is only to be used in the RawDataFilesSelection and does not support the required operation.");
  }

  @Override
  public @NotNull List<PolarityType> getDataPolarity() {
    throw new UnsupportedOperationException(
        "This class is only to be used in the RawDataFilesSelection and does not support the required operation.");
  }

  @Override
  public Color getColorAWT() {
    throw new UnsupportedOperationException(
        "This class is only to be used in the RawDataFilesSelection and does not support the required operation.");
  }

  @Override
  public javafx.scene.paint.Color getColor() {
    throw new UnsupportedOperationException(
        "This class is only to be used in the RawDataFilesSelection and does not support the required operation.");
  }

  @Override
  public void setColor(javafx.scene.paint.Color color) {
    throw new UnsupportedOperationException(
        "This class is only to be used in the RawDataFilesSelection and does not support the required operation.");
  }

  @Override
  public ObjectProperty<javafx.scene.paint.Color> colorProperty() {
    throw new UnsupportedOperationException(
        "This class is only to be used in the RawDataFilesSelection and does not support the required operation.");
  }

  @Override
  public void close() {
    throw new UnsupportedOperationException(
        "This class is only to be used in the RawDataFilesSelection and does not support the required operation.");
  }

  @Override
  public @Nullable MemoryMapStorage getMemoryMapStorage() {
    throw new UnsupportedOperationException(
        "This class is only to be used in the RawDataFilesSelection and does not support the required operation.");
  }

  @Override
  public void addScan(Scan newScan) throws IOException {
    throw new UnsupportedOperationException(
        "This class is only to be used in the RawDataFilesSelection and does not support the required operation.");
  }

  @Override
  public void setRTRange(int msLevel, Range<Float> rtRange) {
    throw new UnsupportedOperationException(
        "This class is only to be used in the RawDataFilesSelection and does not support the required operation.");
  }

  @Override
  public void setMZRange(int msLevel, Range<Double> mzRange) {
    throw new UnsupportedOperationException(
        "This class is only to be used in the RawDataFilesSelection and does not support the required operation.");
  }

  @Override
  public ObservableList<Scan> getScans() {
    throw new UnsupportedOperationException(
        "This class is only to be used in the RawDataFilesSelection and does not support the required operation.");
  }

  @Override
  public void applyMassListChanged(Scan scan, MassList old, MassList masses) {
    throw new UnsupportedOperationException(
        "This class is only to be used in the RawDataFilesSelection and does not support the required operation.");
  }

  @Override
  public @NotNull ObservableList<FeatureListAppliedMethod> getAppliedMethods() {
    throw new UnsupportedOperationException(
        "This class is only to be used in the RawDataFilesSelection and does not support the required operation.");
  }

  @Override
  public @NotNull StringProperty nameProperty() {
    throw new UnsupportedOperationException(
        "This class is only to be used in the RawDataFilesSelection and does not support the required operation.");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    RawDataFilePlaceholder that = (RawDataFilePlaceholder) o;
    return Objects.equals(getName(), that.getName()) && Objects.equals(absPath, that.absPath)
        && Objects.equals(fileHashCode, that.fileHashCode);
  }

  @Override
  public int hashCode() {
    return Objects.hash(getName(), absPath, fileHashCode);
  }
}
