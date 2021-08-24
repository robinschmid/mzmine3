package io.github.mzmine.modules.tools.gnps_iimn_resultsanalysis;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.google.common.io.Files;

/**
 * Filter library matches by sample name or plate number in sample name
 * 
 * @author Robin Schmid (robinschmid@uni-muenster.de)
 *
 */
public class SampleListFilter {
  public final Logger logger = Logger.getLogger(getClass().getName());

  private String compoundHeader;
  private File sampleList;
  private String plateHeader;
  private String sampleHeader;
  private boolean useSample;
  private boolean usePlate;
  private String separator;
  // sample or plate as key and compounds list
  private Map<String, List<String>> compMap;
  // rowID, list of samples
  private HashMap<String, List<String>> quantMap;


  public SampleListFilter(File sampleList, File quantTable, String compoundHeader,
      String plateHeader, String sampleHeader, String separator) throws IOException {
    this.sampleList = sampleList;
    this.compoundHeader = compoundHeader;
    this.plateHeader = plateHeader;
    this.sampleHeader = sampleHeader;
    this.separator = separator;
    usePlate = !plateHeader.isEmpty();
    useSample = !usePlate && !sampleHeader.isEmpty();

    readQuantTable(quantTable);
    readSampleList(sampleList);
  }


  private void readSampleList(File sampleList2) throws IOException {
    compMap = new HashMap<>();
    List<String> lines = Files.readLines(sampleList, StandardCharsets.UTF_8);
    int compoundIndex = -1;
    if (usePlate) {
      int plateIndex = -1;
      for (String line : lines) {
        String[] sep = line.split(separator);
        if (plateIndex == -1 && compoundIndex == -1) {
          for (int i = 0; i < sep.length; i++) {
            String s = sep[i];
            if (s.equalsIgnoreCase(plateHeader)) {
              plateIndex = i;
            }
            if (s.equalsIgnoreCase(compoundHeader)) {
              compoundIndex = i;
            }
          }
        } else {
          // found all headers
          String plate = extractPlateNumber(sep[plateIndex]);
          String compound = sep[compoundIndex];
          List<String> list = compMap.get(plate);
          if (list == null)
            list = new ArrayList<>();
          list.add(compound.toLowerCase());
          compMap.put(plate, list);
        }
      }
    } else {
      int sampleIndex = -1;
      for (String line : lines) {
        String[] sep = line.split(separator);
        if (sampleIndex == -1 && compoundIndex == -1) {
          for (int i = 0; i < sep.length; i++) {
            String s = sep[i];
            if (s.equalsIgnoreCase(sampleHeader)) {
              sampleIndex = i;
            }
            if (s.equalsIgnoreCase(compoundHeader)) {
              compoundIndex = i;
            }
          }
        } else {
          // found all headers
          String compound = sep[compoundIndex];
          String sample = sep[sampleIndex];
          List<String> list = compMap.get(sample);
          if (list == null)
            list = new ArrayList<>();
          list.add(compound.toLowerCase());
          compMap.put(sample, list);
        }
      }
    }
  }


  private void readQuantTable(File quantTable) throws IOException {
    quantMap = new HashMap<>();
    List<String> lines = Files.readLines(quantTable, StandardCharsets.UTF_8);

    String first = lines.remove(0);
    String[] sep = first.split(",");
    //
    int rowIdIndex = -1;
    int startSampleIndex = -1;
    int sub = " Peak area".length();
    List<String> sampleList = new ArrayList<>();
    for (int i = 0; i < sep.length; i++) {
      if (sep[i].equalsIgnoreCase("row ID")) {
        rowIdIndex = i;
      }
      if (sep[i].endsWith("Peak area")) {
        if (startSampleIndex == -1)
          startSampleIndex = i;

        String sample = sep[i].substring(0, sep[i].length() - sub);
        sampleList.add(sample);
        logger.info(sample);
      }
    }
    if (startSampleIndex == -1 || rowIdIndex == -1)
      return;

    for (String s : lines) {
      sep = s.split(",");
      String rowid = sep[rowIdIndex];
      List<String> samples = new ArrayList<String>();
      for (int i = startSampleIndex; i < sep.length; i++) {
        if (Double.parseDouble(sep[i]) > 0d) {
          samples.add(sampleList.get(i - startSampleIndex));
        }
      }
      // add row
      quantMap.put(rowid, samples);
    }
  }



  /**
   * row with compound match was detected in the sample from the sample list
   * 
   * @param rowID
   * @param compound
   * @return
   */
  public boolean rowWithCompoundDetectedInSample(String rowID, String compound) {
    List<String> samples = quantMap.get(rowID);
    return samples.stream().anyMatch(s -> matchesCompoundInSample(s, compound));
  }

  public boolean matchesCompoundInSample(String rawName, String compound) {
    String key = rawName;
    if (usePlate)
      key = extractPlateNumber(rawName);

    String lowerCaseComp = compound.toLowerCase();

    List<String> list = compMap.get(key);
    if (list != null) {
      // any entry in table is the start of compound (all to lower case)
      return list.stream().anyMatch(s -> lowerCaseComp.startsWith(s));
    }

    return false;
  }

  private String extractPlateNumber(String rawName) {
    Pattern p = Pattern.compile("\\d+");
    Matcher m = p.matcher(rawName);
    while (m.find()) {
      // 01 will be 1
      try {
        long plate = Math.abs(Long.parseLong(m.group()));
        if (plate <= 1000)
          return String.valueOf(plate);
      } catch (Exception e) {
      }
    }
    return "";
  }
}
