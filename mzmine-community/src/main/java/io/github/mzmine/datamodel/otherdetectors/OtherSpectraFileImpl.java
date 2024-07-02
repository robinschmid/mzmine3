/*
 * Copyright (c) 2004-2024 The MZmine Development Team
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

package io.github.mzmine.datamodel.otherdetectors;

import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.featuredata.IntensityTimeSeries;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OtherSpectraFileImpl implements OtherSpectraFile {

  private final RawDataFile rawDataFile;
  private final List<IntensityTimeSeries> timeSeries = new ArrayList<>();
  private final List<Spectrum> spectra = new ArrayList<>();

  private @Nullable String spectraDomainLabel;
  private @Nullable String spectraDomainUnit;
  private @Nullable String spectraRangeLabel;
  private @Nullable String spectraRangeUnit;

  private @Nullable String timeSeriesDomainLabel = "RT";
  private @Nullable String timeSeriesDomainUnit = "min";
  private @Nullable String timeSeriesRangeLabel;
  private @Nullable String timeSeriesRangeUnit;

  public OtherSpectraFileImpl(RawDataFile rawDataFile) {
    this.rawDataFile = rawDataFile;
  }

  @Override
  public @NotNull RawDataFile getCorrespondingRawDataFile() {
    return rawDataFile;
  }

  @Override
  public boolean hasTimeSeries() {
    return !timeSeries.isEmpty();
  }

  @Override
  public boolean hasSpectra() {
    return !spectra.isEmpty();
  }

  @Override
  public int getNumberOfSpectra() {
    return spectra.size();
  }

  @Override
  public int getNumberOfTimeSeries() {
    return timeSeries.size();
  }

  @Override
  public @NotNull List<@NotNull Spectrum> getSpectra() {
    return spectra;
  }

  @Override
  public @NotNull IntensityTimeSeries getTimeSeries(int index) {
    return timeSeries.get(index);
  }

  @Override
  public RawDataFile getRawDataFile() {
    return rawDataFile;
  }

  @Override
  public @NotNull List<IntensityTimeSeries> getTimeSeries() {
    return timeSeries;
  }

  public void addSpectrum(@NotNull Spectrum spectrum) {
    spectra.add(spectrum);
  }

  public void addTimeSeries(@NotNull IntensityTimeSeries series) {
    this.timeSeries.add(series);
  }

  @Override
  public @Nullable String getSpectraDomainLabel() {
    return spectraDomainLabel;
  }

  public void setSpectraDomainLabel(@Nullable String spectraDomainLabel) {
    this.spectraDomainLabel = spectraDomainLabel;
  }

  @Override
  public @Nullable String getSpectraDomainUnit() {
    return spectraDomainUnit;
  }

  public void setSpectraDomainUnit(@Nullable String spectraDomainUnit) {
    this.spectraDomainUnit = spectraDomainUnit;
  }

  @Override
  public @Nullable String getSpectraRangeLabel() {
    return spectraRangeLabel;
  }

  public void setSpectraRangeLabel(@Nullable String spectraRangeLabel) {
    this.spectraRangeLabel = spectraRangeLabel;
  }

  @Override
  public @Nullable String getSpectraRangeUnit() {
    return spectraRangeUnit;
  }

  public void setSpectraRangeUnit(@Nullable String spectraRangeUnit) {
    this.spectraRangeUnit = spectraRangeUnit;
  }

  @Override
  public @Nullable String getTimeSeriesDomainLabel() {
    return timeSeriesDomainLabel;
  }

  public void setTimeSeriesDomainLabel(@Nullable String timeSeriesDomainLabel) {
    this.timeSeriesDomainLabel = timeSeriesDomainLabel;
  }

  @Override
  public @Nullable String getTimeSeriesDomainUnit() {
    return timeSeriesDomainUnit;
  }

  public void setTimeSeriesDomainUnit(@Nullable String timeSeriesDomainUnit) {
    this.timeSeriesDomainUnit = timeSeriesDomainUnit;
  }

  @Override
  public @Nullable String getTimeSeriesRangeLabel() {
    return timeSeriesRangeLabel;
  }

  public void setTimeSeriesRangeLabel(@Nullable String timeSeriesRangeLabel) {
    this.timeSeriesRangeLabel = timeSeriesRangeLabel;
  }

  @Override
  public @Nullable String getTimeSeriesRangeUnit() {
    return timeSeriesRangeUnit;
  }

  public void setTimeSeriesRangeUnit(@Nullable String timeSeriesRangeUnit) {
    this.timeSeriesRangeUnit = timeSeriesRangeUnit;
  }
}
