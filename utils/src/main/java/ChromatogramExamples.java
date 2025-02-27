import java.util.Arrays;
import java.util.Random;

public class ChromatogramExamples {

  public static void main(String[] args) {
    // Generate and print examples
    Chromatogram singlePeak = generateSinglePeak();
    Chromatogram twoPeaksWellSeparated = generateTwoPeaksWellSeparated();
    Chromatogram threePeaksWithShouldering = generateThreePeaksWithShouldering();
    Chromatogram fourPeaksWithCoelution = generateFourPeaksWithCoelution();
    Chromatogram fivePeaksComplexPattern = generateFivePeaksComplexPattern();

    System.out.println("Single Peak: " + singlePeak);
    System.out.println("Two Peaks (Well Separated): " + twoPeaksWellSeparated);
    System.out.println("Three Peaks (With Shouldering): " + threePeaksWithShouldering);
    System.out.println("Four Peaks (With Coelution): " + fourPeaksWithCoelution);
    System.out.println("Five Peaks (Complex Pattern): " + fivePeaksComplexPattern);

    // Example of accessing data
    double[] xValues = singlePeak.x();
    double[] yValues = singlePeak.y();
    System.out.println("Single Peak X values: " + Arrays.toString(xValues));
    System.out.println("Single Peak Y values: " + Arrays.toString(yValues));
  }

  // Helper method to create Gaussian peak
  private static double gaussianPeak(double x, double center, double height, double width) {
    return height * Math.exp(-Math.pow(x - center, 2) / (2 * Math.pow(width, 2)));
  }

  // Add some random noise to make the data more realistic
  private static double addNoise(double value, double noiseFactor) {
    Random random = new Random();
    return value * (1 + noiseFactor * (random.nextDouble() - 0.5));
  }

  // Example 1: Single peak with high resolution (many data points)
  public static Chromatogram generateSinglePeak() {
    int dataPoints = 100;
    double[] x = new double[dataPoints];
    double[] y = new double[dataPoints];

    for (int i = 0; i < dataPoints; i++) {
      x[i] = i * 0.1; // 0 to 9.9 minutes
      y[i] = addNoise(gaussianPeak(x[i], 5.0, 100.0, 0.5), 0.02);
    }

    return new Chromatogram(x, y);
  }

  // Example 2: Two well-separated peaks with different widths
  public static Chromatogram generateTwoPeaksWellSeparated() {
    int dataPoints = 150;
    double[] x = new double[dataPoints];
    double[] y = new double[dataPoints];

    for (int i = 0; i < dataPoints; i++) {
      x[i] = i * 0.1; // 0 to 14.9 minutes
      y[i] = addNoise(gaussianPeak(x[i], 3.0, 75.0, 0.3) + // Narrow peak
                      gaussianPeak(x[i], 10.0, 120.0, 0.8), // Wider peak
          0.03);
    }

    return new Chromatogram(x, y);
  }

  // Example 3: Three peaks with shouldering (partial coelution)
  public static Chromatogram generateThreePeaksWithShouldering() {
    int dataPoints = 120;
    double[] x = new double[dataPoints];
    double[] y = new double[dataPoints];

    for (int i = 0; i < dataPoints; i++) {
      x[i] = i * 0.1; // 0 to 11.9 minutes
      y[i] = addNoise(gaussianPeak(x[i], 2.5, 50.0, 0.4) + gaussianPeak(x[i], 3.2, 95.0, 0.3) +
                      // Shoulder on first peak
                      gaussianPeak(x[i], 8.0, 110.0, 0.6), 0.02);
    }

    return new Chromatogram(x, y);
  }

  // Example 4: Four peaks with coelution and varying data point density
  public static Chromatogram generateFourPeaksWithCoelution() {
    // Using variable data point spacing to demonstrate different data point density
    double[] x = new double[200];
    double[] y = new double[200];

    // First region: sparse data points
    for (int i = 0; i < 50; i++) {
      x[i] = i * 0.2; // Sparse points from 0 to 9.8
      y[i] = addNoise(gaussianPeak(x[i], 5.0, 60.0, 0.7), 0.04);
    }

    // Middle region: dense data points (more resolution for coeluted peaks)
    for (int i = 50; i < 150; i++) {
      x[i] = 10 + (i - 50) * 0.05; // Dense points from 10 to 14.95
      y[i] = addNoise(gaussianPeak(x[i], 11.0, 85.0, 0.4) + gaussianPeak(x[i], 11.2, 90.0, 0.3) +
                      // Heavily coeluted peaks
                      gaussianPeak(x[i], 13.5, 70.0, 0.5), 0.03);
    }

    // End region: medium data points
    for (int i = 150; i < 200; i++) {
      x[i] = 15 + (i - 150) * 0.1; // Medium points from 15 to 19.9
      y[i] = addNoise(gaussianPeak(x[i], 17.0, 40.0, 0.6), 0.02);
    }

    return new Chromatogram(x, y);
  }

  // Baseline drift function to make it more realistic
  public static double baselineDrift(double x) {
    return 5 + 0.2 * x - 0.01 * x * x;
  }

  // Example 5: Five peaks with complex pattern (various widths, heights, and spacing)
  public static Chromatogram generateFivePeaksComplexPattern() {
    int dataPoints = 250;
    double[] x = new double[dataPoints];
    double[] y = new double[dataPoints];

    for (int i = 0; i < dataPoints; i++) {
      x[i] = i * 0.1; // 0 to 24.9 minutes
      double baseline = baselineDrift(x[i]);

      y[i] = baseline + addNoise(gaussianPeak(x[i], 2.0, 30.0, 0.25) + // Small, sharp peak
                                 gaussianPeak(x[i], 6.5, 120.0, 0.8) + // Large, broad peak
                                 gaussianPeak(x[i], 10.2, 45.0, 0.3) + // Medium, sharp peak
                                 gaussianPeak(x[i], 10.6, 30.0, 0.2) +
                                 // Small peak coeluting with previous
                                 gaussianPeak(x[i], 18.0, 85.0, 0.7),  // Medium-large peak
          0.04);
    }

    return new Chromatogram(x, y);
  }
}