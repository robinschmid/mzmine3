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

package io.github.mzmine.modules.visualization.spectra.simplespectra.datasets;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.util.scans.ScanUtils;
import java.util.Hashtable;
import java.util.Map;
import org.jfree.data.xy.AbstractXYDataset;
import org.jfree.data.xy.IntervalXYDataset;

/**
 * Spectra visualizer data set for scan data points
 */
public class ScanDataSet extends AbstractXYDataset implements IntervalXYDataset, RelativeOption {

  private static final long serialVersionUID = 1L;

  // For comparing small differences.
  private static final double EPSILON = 0.0000001;

  private final String label;
  private final Scan scan;
  private final Map<Integer, String> annotation = new Hashtable<>();
  private final Map<Double, String> mzAnnotationMap = new Hashtable<>();
  private final double maxIntensity;
  private boolean normalize;

  /*
   * Save a local copy of m/z and intensity values, because accessing the scan every time may cause
   * reloading the data from HDD
   */
  // private DataPoint dataPoints[];

  public ScanDataSet(Scan scan) {
    this(scan, false);
  }

  public ScanDataSet(Scan scan, boolean normalize) {
    this("Scan #" + scan.getScanNumber(), scan, normalize);
  }

  public ScanDataSet(String label, Scan scan) {
    this(label, scan, false);
  }

  public ScanDataSet(String label, Scan scan, boolean normalize) {
    // this.dataPoints = scan.getDataPoints();
    this.scan = scan;
    this.label = label;
    this.normalize = normalize;
    final Double basePeak = scan.getBasePeakIntensity();
    maxIntensity = basePeak == null ? -1 : basePeak;

    /*
     * This optimalization is disabled, because it crashes on scans with no datapoints. Also, it
     * distorts the view of the raw data - user would see something different from the actual
     * content of the raw data file.
     *
     * // remove all extra zeros List<DataPoint> dp = new ArrayList<>(); dp.add(dataPoints[0]); for
     * (int i = 1; i < dataPoints.length - 1; i++) { // previous , this and next are zero --> do not
     * add this data point if (Double.compare(dataPoints[i - 1].getIntensity(), 0d) != 0 ||
     * Double.compare(dataPoints[i].getIntensity(), 0d) != 0 || Double.compare(dataPoints[i +
     * 1].getIntensity(), 0d) != 0) { dp.add(dataPoints[i]); } } dp.add(dataPoints[dataPoints.length
     * - 1]); this.dataPoints = dp.toArray(new DataPoint[0]);
     */
  }

  @Override
  public int getSeriesCount() {
    return 1;
  }

  @Override
  public Comparable<?> getSeriesKey(int series) {
    return label;
  }

  @Override
  public int getItemCount(int series) {
    return scan.getNumberOfDataPoints();
  }

  @Override
  public Number getX(int series, int item) {
    return scan.getMzValue(item);
  }

  @Override
  public Number getY(int series, int item) {
    return normalize ? scan.getIntensityValue(item) / maxIntensity * 100d
        : scan.getIntensityValue(item);
  }

  @Override
  public Number getEndX(int series, int item) {
    return getX(series, item);
  }

  @Override
  public double getEndXValue(int series, int item) {
    return getXValue(series, item);
  }

  @Override
  public Number getEndY(int series, int item) {
    return getY(series, item);
  }

  @Override
  public double getEndYValue(int series, int item) {
    return getYValue(series, item);
  }

  @Override
  public Number getStartX(int series, int item) {
    return getX(series, item);
  }

  @Override
  public double getStartXValue(int series, int item) {
    return getXValue(series, item);
  }

  @Override
  public Number getStartY(int series, int item) {
    return getY(series, item);
  }

  @Override
  public double getStartYValue(int series, int item) {
    return getYValue(series, item);
  }

  public int getIndex(final double mz, final double intensity) {
    for (int i = 0; i < scan.getNumberOfDataPoints(); i++) {
      if (Math.abs(mz - scan.getMzValue(i)) < EPSILON
          && Math.abs(intensity - scan.getIntensityValue(i)) < EPSILON) {
        return i;
      }
    }
    return -1;
  }

  /**
   * This function finds highest data point intensity in given m/z range. It is important for
   * normalizing isotope patterns.
   */
  public double getHighestIntensity(Range<Double> mzRange) {
    return ScanUtils.findBasePeak(scan, mzRange).getIntensity();
  }

  public Scan getScan() {
    return scan;
  }

  public void addAnnotation(Map<Integer, String> annotation) {
    this.annotation.putAll(annotation);
  }

  /**
   * Add annotations for m/z values
   *
   * @param annotation m/z value and annotation map
   */
  public void addMzAnnotation(Map<Double, String> annotation) {
    this.mzAnnotationMap.putAll(annotation);
  }


  public String getAnnotation(int item) {
    String ann = mzAnnotationMap.get(getXValue(0, item));
    String ann2 = annotation.get(item);
    if (ann != null && ann2 != null) {
      return ann + " " + ann2;
    }
    if (ann2 != null) {
      return ann2;
    }
    if (ann != null) {
      return ann;
    }
    return null;
  }

  @Override
  public void setRelative(boolean relative) {
    normalize = relative;
  }
}
