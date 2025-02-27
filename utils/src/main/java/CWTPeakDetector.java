import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Chromatogram record to store retention time and intensity data
 */
record Chromatogram(double[] x, double[] y) {

}

/**
 * Peak information record with properties
 */
record Peak(double retentionTime, double intensity, double area, double fwhm, double asymmetry,
            double snr) {

}

/**
 * Implementation of Continuous Wavelet Transform for chromatography peak detection
 */
public class CWTPeakDetector {

  // Parameters
  private final int minScale;
  private final int maxScale;
  private final int scaleStep;
  private final double snrThreshold;
  private final double ridgeThreshold;

  /**
   * Constructor with default parameters
   */
  public CWTPeakDetector() {
    // Default parameters
    this.minScale = 1;
    this.maxScale = 32;
    this.scaleStep = 1;
    this.snrThreshold = 3.0;
    this.ridgeThreshold = 0.5;
  }

  /**
   * Constructor with custom parameters
   */
  public CWTPeakDetector(int minScale, int maxScale, int scaleStep, double snrThreshold,
      double ridgeThreshold) {
    this.minScale = minScale;
    this.maxScale = maxScale;
    this.scaleStep = scaleStep;
    this.snrThreshold = snrThreshold;
    this.ridgeThreshold = ridgeThreshold;
  }

  /**
   * Main method to detect peaks in a chromatogram
   *
   * @param chromatogram The input chromatogram data
   * @return List of detected peaks
   */
  public List<Peak> detectPeaks(Chromatogram chromatogram) {
    double[] x = chromatogram.x();
    double[] y = chromatogram.y();

    // Compute CWT coefficients
    double[][] cwtCoeffs = computeCWT(y);

    // Find ridges in the CWT matrix
    List<int[]> ridges = findRidges(cwtCoeffs);

    // Filter ridges to find peaks
    List<Peak> peaks = extractPeaksFromRidges(ridges, cwtCoeffs, x, y);

    return peaks;
  }

  /**
   * Compute the Continuous Wavelet Transform coefficients
   *
   * @param signal The intensity signal
   * @return 2D array of CWT coefficients
   */
  private double[][] computeCWT(double[] signal) {
    int n = signal.length;
    int numScales = (maxScale - minScale) / scaleStep + 1;
    double[][] cwtCoeffs = new double[numScales][n];

    for (int i = 0; i < numScales; i++) {
      int scale = minScale + i * scaleStep;
      cwtCoeffs[i] = convolveWithRickerWavelet(signal, scale);
    }

    return cwtCoeffs;
  }

  /**
   * Convolve the signal with a Ricker (Mexican hat) wavelet at a given scale
   *
   * @param signal The intensity signal
   * @param scale  The wavelet scale
   * @return Array of convolution results
   */
  private double[] convolveWithRickerWavelet(double[] signal, int scale) {
    int n = signal.length;
    double[] result = new double[n];

    // Generate Ricker wavelet
    int waveletWidth = 10 * scale;
    if (waveletWidth % 2 == 0) {
      waveletWidth++;
    }
    int halfWidth = waveletWidth / 2;
    double[] wavelet = new double[waveletWidth];

    for (int i = 0; i < waveletWidth; i++) {
      double t = (i - halfWidth) / (double) scale;
      // Ricker wavelet formula: (1 - t^2) * exp(-t^2/2)
      wavelet[i] = (1 - t * t) * Math.exp(-t * t / 2);
    }

    // Normalize wavelet
    double sum = Arrays.stream(wavelet).sum();
    for (int i = 0; i < waveletWidth; i++) {
      wavelet[i] /= sum;
    }

    // Perform convolution
    for (int i = 0; i < n; i++) {
      double sum2 = 0;
      for (int j = 0; j < waveletWidth; j++) {
        int signalIdx = i + j - halfWidth;
        if (signalIdx >= 0 && signalIdx < n) {
          sum2 += signal[signalIdx] * wavelet[j];
        }
      }
      result[i] = sum2;
    }

    return result;
  }

  /**
   * Find ridges in the CWT coefficient matrix
   *
   * @param cwtCoeffs CWT coefficient matrix
   * @return List of ridge points (scale and position)
   */
  private List<int[]> findRidges(double[][] cwtCoeffs) {
    int numScales = cwtCoeffs.length;
    int signalLength = cwtCoeffs[0].length;
    List<int[]> ridges = new ArrayList<>();

    // Find local maxima in each scale
    for (int i = 0; i < numScales; i++) {
      for (int j = 1; j < signalLength - 1; j++) {
        if (cwtCoeffs[i][j] > cwtCoeffs[i][j - 1] && cwtCoeffs[i][j] > cwtCoeffs[i][j + 1]) {
          // Local maximum found at scale i, position j
          ridges.add(new int[]{i, j});
        }
      }
    }

    // Group ridges across scales (simplified approach)
    List<List<int[]>> ridgeLines = new ArrayList<>();

    for (int i = 0; i < numScales - 1; i++) {
      final int scale = i;
      List<int[]> pointsAtScale = ridges.stream().filter(point -> point[0] == scale).toList();

      for (int[] point : pointsAtScale) {
        int position = point[1];

        // Look for a nearby point in the next scale
        final int finalPosition = position;
        List<int[]> nextScalePoints = ridges.stream()
            .filter(p -> p[0] == scale + 1 && Math.abs(p[1] - finalPosition) <= 2).toList();

        if (!nextScalePoints.isEmpty()) {
          // Found continuing ridge
          List<int[]> ridgeLine = new ArrayList<>();
          ridgeLine.add(point);
          ridgeLine.add(nextScalePoints.get(0)); // Take the closest one
          ridgeLines.add(ridgeLine);
        }
      }
    }

    // Filter ridges by length
    return ridges.stream().filter(point -> {
      double coeff = cwtCoeffs[point[0]][point[1]];
      return coeff > ridgeThreshold;
    }).toList();
  }

  /**
   * Extract peak information from the ridges
   *
   * @param ridges    Ridge points
   * @param cwtCoeffs CWT coefficients
   * @param x         Retention time array
   * @param y         Intensity array
   * @return List of detected peaks
   */
  private List<Peak> extractPeaksFromRidges(List<int[]> ridges, double[][] cwtCoeffs, double[] x,
      double[] y) {
    List<Peak> peaks = new ArrayList<>();

    // Group ridge points by position (simplified approach)
    for (int[] point : ridges) {
      int scaleIdx = point[0];
      int posIdx = point[1];

      // Skip points at very low or very high scales
      if (scaleIdx < 3 || scaleIdx > cwtCoeffs.length - 3) {
        continue;
      }

      double coefficient = cwtCoeffs[scaleIdx][posIdx];

      // Check if coefficient is strong enough
      if (coefficient > ridgeThreshold) {
        double rt = x[posIdx];
        double intensity = y[posIdx];

        // Calculate local SNR
        double snr = calculateSNR(y, posIdx);

        // Only consider peaks with good SNR
        if (snr > snrThreshold) {
          // Calculate other peak properties
          double area = estimatePeakArea(x, y, posIdx);
          double fwhm = estimateFWHM(x, y, posIdx);
          double asymmetry = calculateAsymmetry(x, y, posIdx);

          peaks.add(new Peak(rt, intensity, area, fwhm, asymmetry, snr));
        }
      }
    }

    return mergeDuplicatePeaks(peaks);
  }

  /**
   * Calculate signal-to-noise ratio for a potential peak
   *
   * @param y       Intensity array
   * @param peakIdx Index of the peak in the array
   * @return SNR value
   */
  private double calculateSNR(double[] y, int peakIdx) {
    int n = y.length;
    int windowSize = 10; // Adjust based on your data

    // Get local region around the peak
    int start = Math.max(0, peakIdx - windowSize);
    int end = Math.min(n - 1, peakIdx + windowSize);

    // Calculate local baseline and noise
    double sum = 0;
    double sumSq = 0;
    int count = 0;

    for (int i = start; i <= end; i++) {
      // Skip the peak region itself
      if (Math.abs(i - peakIdx) > 3) {
        sum += y[i];
        sumSq += y[i] * y[i];
        count++;
      }
    }

    double meanBaseline = sum / count;
    double variance = sumSq / count - meanBaseline * meanBaseline;
    double stdDev = Math.sqrt(variance);

    // Calculate SNR
    return (y[peakIdx] - meanBaseline) / stdDev;
  }

  /**
   * Estimate the area of a peak
   *
   * @param x       Retention time array
   * @param y       Intensity array
   * @param peakIdx Index of the peak in the array
   * @return Estimated peak area
   */
  private double estimatePeakArea(double[] x, double[] y, int peakIdx) {
    int n = y.length;
    double area = 0;

    // Find peak boundaries
    int leftIdx = peakIdx;
    while (leftIdx > 0 && y[leftIdx] > y[leftIdx - 1]) {
      leftIdx--;
    }

    int rightIdx = peakIdx;
    while (rightIdx < n - 1 && y[rightIdx] > y[rightIdx + 1]) {
      rightIdx++;
    }

    // Trapezoidal integration
    for (int i = leftIdx; i < rightIdx; i++) {
      area += (x[i + 1] - x[i]) * (y[i] + y[i + 1]) / 2;
    }

    return area;
  }

  /**
   * Estimate Full Width at Half Maximum
   *
   * @param x       Retention time array
   * @param y       Intensity array
   * @param peakIdx Index of the peak in the array
   * @return FWHM value
   */
  private double estimateFWHM(double[] x, double[] y, int peakIdx) {
    int n = y.length;
    double halfHeight = y[peakIdx] / 2;

    // Find left half-max point
    int leftIdx = peakIdx;
    while (leftIdx > 0 && y[leftIdx] > halfHeight) {
      leftIdx--;
    }

    // Interpolate for better precision
    double leftX;
    if (leftIdx == 0) {
      leftX = x[0];
    } else {
      double fraction = (halfHeight - y[leftIdx]) / (y[leftIdx + 1] - y[leftIdx]);
      leftX = x[leftIdx] + fraction * (x[leftIdx + 1] - x[leftIdx]);
    }

    // Find right half-max point
    int rightIdx = peakIdx;
    while (rightIdx < n - 1 && y[rightIdx] > halfHeight) {
      rightIdx++;
    }

    // Interpolate for better precision
    double rightX;
    if (rightIdx == n - 1) {
      rightX = x[n - 1];
    } else {
      double fraction = (halfHeight - y[rightIdx]) / (y[rightIdx - 1] - y[rightIdx]);
      rightX = x[rightIdx] - fraction * (x[rightIdx] - x[rightIdx - 1]);
    }

    return rightX - leftX;
  }

  /**
   * Calculate peak asymmetry factor
   *
   * @param x       Retention time array
   * @param y       Intensity array
   * @param peakIdx Index of the peak in the array
   * @return Asymmetry factor
   */
  private double calculateAsymmetry(double[] x, double[] y, int peakIdx) {
    double tenPercentHeight = y[peakIdx] * 0.1;
    int n = y.length;

    // Find left 10% point
    int leftIdx = peakIdx;
    while (leftIdx > 0 && y[leftIdx] > tenPercentHeight) {
      leftIdx--;
    }

    // Find right 10% point
    int rightIdx = peakIdx;
    while (rightIdx < n - 1 && y[rightIdx] > tenPercentHeight) {
      rightIdx++;
    }

    // Calculate distances
    double leftDistance = x[peakIdx] - x[leftIdx];
    double rightDistance = x[rightIdx] - x[peakIdx];

    // Asymmetry factor is right half-width divided by left half-width
    return rightDistance / leftDistance;
  }

  /**
   * Merge duplicate peaks that are close to each other
   *
   * @param peaks List of potential peaks
   * @return Filtered list with duplicates merged
   */
  private List<Peak> mergeDuplicatePeaks(List<Peak> peaks) {
    List<Peak> result = new ArrayList<>();

    // Sort peaks by retention time
    peaks.sort((p1, p2) -> Double.compare(p1.retentionTime(), p2.retentionTime()));

    if (peaks.isEmpty()) {
      return result;
    }

    // Merge peaks that are too close
    Peak currentPeak = peaks.get(0);

    for (int i = 1; i < peaks.size(); i++) {
      Peak nextPeak = peaks.get(i);

      // If peaks are very close (less than half FWHM apart)
      if (nextPeak.retentionTime() - currentPeak.retentionTime() < currentPeak.fwhm() / 2) {
        // Keep the one with better SNR
        if (nextPeak.snr() > currentPeak.snr()) {
          currentPeak = nextPeak;
        }
      } else {
        result.add(currentPeak);
        currentPeak = nextPeak;
      }
    }

    // Add the last peak
    result.add(currentPeak);

    return result;
  }

  /**
   * Example usage
   */
  public static void main(String[] args) {
    // Sample data
    double[] x = {1.0, 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 2.0, 2.1, 2.2, 2.3, 2.4, 2.5};
    double[] y = {1.0, 1.2, 1.5, 2.0, 5.0, 9.0, 7.0, 4.0, 2.5, 2.0, 1.8, 1.5, 1.3, 1.2, 1.1, 1.0};

//    Chromatogram chromatogram = new Chromatogram(x, y);

    final Chromatogram chromatogram = ChromatogramExamples.generateFourPeaksWithCoelution();
    // Create detector with custom parameters
    CWTPeakDetector detector = new CWTPeakDetector(1, 16, 1, 2.0, 0.3);

    // Detect peaks
    List<Peak> peaks = detector.detectPeaks(chromatogram);

    // Print results
    System.out.println("Detected peaks: " + peaks.size());

    for (Peak peak : peaks) {
      System.out.printf(
          "RT: %.2f, Height: %.2f, Area: %.2f, FWHM: %.2f, Asymmetry: %.2f, SNR: %.2f%n",
          peak.retentionTime(), peak.intensity(), peak.area(), peak.fwhm(), peak.asymmetry(),
          peak.snr());
    }

    int pi = 0;
    String sep = "\t";
    String nl = "\n";
    try (var writer = Files.newBufferedWriter(
        Path.of("D:\\git\\mzmine3\\utils\\src\\main\\java\\chrom.tsv"), StandardCharsets.UTF_8)) {
      writer.append(String.join(sep, "x", "y", "py")).append(nl);
      for (int i = 0; i < chromatogram.x().length; i++) {
        writer.append("%.2f%s%.2f".formatted(chromatogram.x()[i], sep, chromatogram.y()[i]));
        if (pi < peaks.size()) {
          final Peak peak = peaks.get(pi);
          if (Double.compare(chromatogram.y()[i], peak.intensity()) == 0) {
            pi++;
            writer.append(sep);
            writer.append("%.2f".formatted(peak.intensity()));
          } else {
            writer.append(sep);
          }
        }
        writer.append(nl);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}