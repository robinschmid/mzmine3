/*
 * Copyright 2006-2020 The MZmine Development Team
 *
 * This file is part of MZmine.
 *
 * MZmine is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MZmine; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA
 */

package io.github.mzmine.datamodel.data_access;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.DataPoint;
import io.github.mzmine.datamodel.MassList;
import io.github.mzmine.datamodel.MassSpectrum;
import io.github.mzmine.datamodel.MassSpectrumType;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.data_access.EfficientDataAccess.ScanDataType;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.util.exceptions.MissingMassListException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The intended use of this memory access is to loop over all scans and access data points via
 * {@link #getMzValue(int)} and {@link #getIntensityValue(int)}
 *
 * @author Robin Schmid (https://github.com/robinschmid)
 */
public class ScanDataAccess implements MassSpectrum {

  private static final Logger logger = Logger.getLogger(ScanDataAccess.class.getName());

  protected final RawDataFile dataFile;
  protected final ScanDataType type;
  protected final int totalScans;
  // current data
  protected final double[] mzs;
  protected final double[] intensities;
  private final ScanSelection selection;
  protected int currentNumberOfDataPoints = -1;
  protected int scanIndex = -1;
  protected int currentScanInDataFile = -1;
  protected Scan currentScan = null;

  // buffer to map Scan -> info
  // only used to jump from one scan to another
  private LinkedHashMap<Scan, ScanDataAccessInfo> scanInfoMap;
  /**
   * number of data points across all scans or mass lists
   */
  private int totalNumberOfDataPointsAcrossScans = -1;

  /**
   * for accessing single data points in different scans out of order - use methods {@link
   * #getMzValue(Scan, int)}. Will wait for multiple accessions from the same scan to preload data
   */
  private Scan lastAccessedScan = null;
  private int accessCounter = 0;

  /**
   * The intended use of this memory access is to loop over all scans and access data points via
   * {@link #getMzValue(int)} and {@link #getIntensityValue(int)}
   *
   * @param dataFile  target data file to loop over all scans or mass lists
   * @param type      processed or raw data
   * @param selection processed or raw data
   */
  protected ScanDataAccess(RawDataFile dataFile,
      ScanDataType type, ScanSelection selection) {
    this.dataFile = dataFile;
    this.type = type;
    this.selection = selection;
    // count matching scans
    if (selection == null) {
      totalScans = dataFile.getScans().size();
    } else {
      int size = 0;
      for (Scan s : dataFile.getScans()) {
        if (selection.matches(s)) {
          size++;
        }
      }
      totalScans = size;
    }
    // might even use the maximum number of data points in the selected scans
    // but seems unnecessary
    int length = getMaxNumberOfDataPoints();
    mzs = new double[length];
    intensities = new double[length];
  }

  /**
   * @return Number of data points in the current scan depending of the defined DataType
   * (RAW/CENTROID)
   */
  @Override
  public int getNumberOfDataPoints() {
    return currentNumberOfDataPoints;
  }

  public Scan getCurrentScan() {
    return currentScan;
  }

  /**
   * @return
   */
  private MassList getMassList() {
    return getCurrentScan().getMassList();
  }

  /**
   * Get mass-to-charge ratio at index
   *
   * @param index data point index
   * @return
   */
  @Override
  public double getMzValue(int index) {
    assert index < getNumberOfDataPoints() && index >= 0;
    return mzs[index];
  }

  /**
   * Get intensity at index
   *
   * @param index data point index
   * @return
   */
  @Override
  public double getIntensityValue(int index) {
    assert index < getNumberOfDataPoints() && index >= 0;
    return intensities[index];
  }

  /**
   * Set the data to the next scan, if available. Returns the scan for additional data access. m/z
   * and intensity values should be accessed from this data class via {@link #getMzValue(int)} and
   * {@link #getIntensityValue(int)}
   *
   * @return the scan or null
   * @throws MissingMassListException if DataType.CENTROID is selected and mass list is missing in
   *                                  the current scan
   */
  @Nullable
  public Scan nextScan() throws MissingMassListException {
    if (!hasNextScan()) {
      return null;
    }
    // find next scan
    do {
      // next scan in data file
      currentScanInDataFile++;
      currentScan = dataFile.getScan(currentScanInDataFile);

      assert currentScan != null;
      // find next scan
    } while (selection != null && !selection.matches(currentScan));

    // next scan found - set data
    scanIndex++;
    setCurrentData(currentScan);

    return currentScan;
  }

  private void setCurrentData(Scan scan) throws MissingMassListException {
    currentScan = scan;
    switch (type) {
      case RAW -> {
        scan.getMzValues(mzs);
        scan.getIntensityValues(intensities);
        currentNumberOfDataPoints = scan.getNumberOfDataPoints();
      }
      case CENTROID -> {
        MassList masses = scan.getMassList();
        if (masses == null) {
          throw new MissingMassListException(scan);
        }
        masses.getMzValues(mzs);
        masses.getIntensityValues(intensities);
        currentNumberOfDataPoints = masses.getNumberOfDataPoints();
      }
    }
    assert currentNumberOfDataPoints <= mzs.length;
  }

  /**
   * The current list of scans has another element
   *
   * @return
   */
  public boolean hasNextScan() {
    return scanIndex + 1 < getNumberOfScans();
  }

  /**
   * Number of selected scans
   *
   * @return
   */
  public int getNumberOfScans() {
    return totalScans;
  }

  /**
   * Maximum number of data points is used to create the arrays that back the data
   *
   * @return
   */
  private int getMaxNumberOfDataPoints() {
    return switch (type) {
      case CENTROID -> dataFile.getMaxCentroidDataPoints();
      case RAW -> dataFile.getMaxRawDataPoints();
    };
  }

  // ###############################################
  // general MassSpectrum methods
  @Override
  public MassSpectrumType getSpectrumType() {
    return switch (type) {
      case RAW -> dataFile.getScan(currentScanInDataFile).getSpectrumType();
      case CENTROID -> MassSpectrumType.CENTROIDED;
    };
  }

  @Nullable
  @Override
  public Double getBasePeakMz() {
    Integer index = getBasePeakIndex();
    return index != null && index >= 0 ? getMzValue(index) : null;
  }

  @Nullable
  @Override
  public Double getBasePeakIntensity() {
    Integer index = getBasePeakIndex();
    return index != null && index >= 0 ? getIntensityValue(index) : null;
  }

  @Nullable
  @Override
  public Integer getBasePeakIndex() {
    switch (type) {
      case RAW:
        return getCurrentScan().getBasePeakIndex();
      case CENTROID:
        MassList masses = getMassList();
        return masses == null ? null : masses.getBasePeakIndex();
      default:
        throw new IllegalStateException("Unexpected value: " + type);
    }
  }

  @Nullable
  @Override
  public Range<Double> getDataPointMZRange() {
    switch (type) {
      case RAW:
        return getCurrentScan().getDataPointMZRange();
      case CENTROID:
        MassList masses = getMassList();
        return masses == null ? null : masses.getDataPointMZRange();
      default:
        throw new IllegalStateException("Unexpected value: " + type);
    }
  }

  @Nullable
  @Override
  public Double getTIC() {
    switch (type) {
      case RAW:
        return getCurrentScan().getTIC();
      case CENTROID:
        MassList masses = getMassList();
        return masses == null ? null : masses.getTIC();
      default:
        throw new IllegalStateException("Unexpected value: " + type);
    }
  }


  /**
   * This method calculates the total number of data points stored in all selected scans/or
   * masslists depending on the underlying {@link ScanSelection} and {@link ScanDataType}
   *
   * @return total number of data points in all selected scans/mass lists
   */
  public int getTotalNumberOfDataPointsAcrossScans() throws MissingMassListException {
    if (totalNumberOfDataPointsAcrossScans == -1) {
      createScanInfoMap();
    }
    return totalNumberOfDataPointsAcrossScans;
  }

  /**
   * This method is used to jump to specific scans in the {@link ScanSelection}. To iterate over all
   * scans sorted by retention time - use {@link #nextScan()}
   *
   * @param scan the scan to jump to
   * @return true if the scan was already active or if the scan was available in the scan selection
   * and set as the current.
   */
  public boolean assureCurrentScanIs(Scan scan) {
    // check if already selected
    if (Objects.equals(currentScan, scan)) {
      return true;
    }

    // compute map and set to current
    if (scanInfoMap == null) {
      createScanInfoMap();
    }
    ScanDataAccessInfo info = scanInfoMap.get(scan);
    if (info != null) {
      currentScanInDataFile = info.originalIndex();
      scanIndex = info.filteredIndex();
      setCurrentData(scan);
      return true;
    }
    return false;
  }

  /**
   * Checks if scan equals the current scan and loads data efficiently. Prefer {@link
   * #getMzValue(int)} to access all data points in sequence.
   *
   * @param scan           the scan that contains the data point (either in RAW or CENTROID data)
   * @param dataPointIndex the index of the data point
   * @return the m/z value in a scan (or mass list) at index
   */
  public double getMzValue(@Nonnull Scan scan, int dataPointIndex) {
    Objects.requireNonNull(scan);

    // count access for preloading
    countAccess(scan);

    if (accessCounter > 2) {
      // data is accessed in a sequence (all data points)
      // set scan if not available
      if (assureCurrentScanIs(scan)) {
        return getMzValue(dataPointIndex);
      }
    } else if (containsScan(scan)) {
      // data is accessed from random scans
      // get data directly from scan to avoid loading the full data arrays
      return type.getMassSpectrum(scan).getMzValue(dataPointIndex);
    }
    return 0;
  }

  /**
   * Checks if scan equals the current scan and loads data efficiently. Prefer {@link
   * #getIntensityValue(int)} to access all data points in sequence.
   *
   * @param scan           the scan that contains the data point (either in RAW or CENTROID data)
   * @param dataPointIndex the index of the data point
   * @return the intensity value in a scan (or mass list) at index
   */
  public double getIntensityValue(@Nonnull Scan scan, int dataPointIndex) {
    Objects.requireNonNull(scan);

    // count access for preloading
    countAccess(scan);

    if (accessCounter > 2) {
      // data is accessed in a sequence (all data points)
      // set scan if not available
      if (assureCurrentScanIs(scan)) {
        return getIntensityValue(dataPointIndex);
      }
    } else if (containsScan(scan)) {
      // data is accessed from random scans
      // get data directly from scan to avoid loading the full data arrays
      return type.getMassSpectrum(scan).getIntensityValue(dataPointIndex);
    }
    return 0;
  }

  /**
   * Count access to this scan in a row to specify when to preload data from disk
   *
   * @param scan accessed scan
   */
  private void countAccess(@Nonnull Scan scan) {
    if (Objects.equals(scan, lastAccessedScan)) {
      accessCounter++;
    } else {
      lastAccessedScan = scan;
      accessCounter = 1;
    }
  }

  /**
   * Checks if the scan is actually from the correct raw data file and matches the {@link
   * ScanSelection}
   *
   * @param scan tested scan
   * @return true if scan is in this {@link RawDataFile} and {@link ScanSelection}
   */
  public boolean containsScan(Scan scan) {
    return scan != null && dataFile.equals(scan.getDataFile())
           && getScanInfoMap().get(scan) != null;
  }

  /**
   * A map with the filtered index, original in data file index, and number of data points. Sorted
   * in natural order (by retention time)
   *
   * @return map of scan information
   */
  public LinkedHashMap<Scan, ScanDataAccessInfo> getScanInfoMap() {
    if (scanInfoMap == null) {
      createScanInfoMap();
    }
    return scanInfoMap;
  }

  /**
   * Creates the scan info map to link all scans to their filteredIndex, index in data file, and
   * number of data points
   */
  private void createScanInfoMap() throws MissingMassListException {
    scanInfoMap = new LinkedHashMap<>(getNumberOfScans());
    Scan scan;
    int indexInDataFile = -1;

    totalNumberOfDataPointsAcrossScans = 0;
    for (int scanIndex = 0; scanIndex < getNumberOfScans(); scanIndex++) {
      do {
        // next scan in data file
        indexInDataFile++;
        scan = dataFile.getScan(indexInDataFile);

        assert scan != null;
        // find next scan
      } while (selection != null && !selection.matches(scan));
      // adding number of signals
      int numberOfDataPoints = type.getNumberOfDataPoints(scan);

      // create scan info to easily jump from scan to scan with the setScan method
      scanInfoMap.put(scan, new ScanDataAccessInfo(scanIndex, indexInDataFile, numberOfDataPoints));

      // calc total over all scans
      totalNumberOfDataPointsAcrossScans += numberOfDataPoints;
    }
  }


  @Override
  public double[] getMzValues(@Nonnull double[] dst) {
    throw new UnsupportedOperationException(
        "The intended use of this class is to loop over all scans and data points");
  }

  @Override
  public double[] getIntensityValues(@Nonnull double[] dst) {
    throw new UnsupportedOperationException(
        "The intended use of this class is to loop over all scans and data points");
  }

  @Override
  public Stream<DataPoint> stream() {
    throw new UnsupportedOperationException(
        "The intended use of this class is to loop over all scans and data points");
  }

  @Nonnull
  @Override
  public Iterator<DataPoint> iterator() {
    throw new UnsupportedOperationException(
        "The intended use of this class is to loop over all scans and data points");
  }

}
