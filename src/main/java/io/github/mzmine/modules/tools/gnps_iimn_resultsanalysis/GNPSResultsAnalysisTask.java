/*
 * Copyright 2006-2018 The MZmine 2 Development Team
 * 
 * This file is part of MZmine 2.
 * 
 * MZmine 2 is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 * 
 * MZmine 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with MZmine 2; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301
 * USA
 */

package io.github.mzmine.modules.tools.gnps_iimn_resultsanalysis;

import io.github.mzmine.datamodel.DataPoint;
import io.github.mzmine.datamodel.impl.SimpleDataPoint;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.dataprocessing.id_gnpsresultsimport.GNPSLibraryMatch;
import io.github.mzmine.modules.dataprocessing.id_gnpsresultsimport.GNPSLibraryMatch.ATT;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.files.FileAndPathUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.graphstream.graph.Edge;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.MultiGraph;
import org.graphstream.stream.file.FileSource;
import org.graphstream.stream.file.FileSourceGraphML;
import com.google.common.util.concurrent.AtomicDouble;

/**
 * Extract statistics from GNPS IIN and FBMN results
 * 
 * @author Robin Schmid (robinschmid@uni-muenster.de)
 *
 */
public class GNPSResultsAnalysisTask extends AbstractTask {
  private final Logger logger = Logger.getLogger(this.getClass().getName());
  private static final DecimalFormat scoreFormat = new DecimalFormat("0.000");
  private static String del = ",";
  private static String nl = "\n";

  // public static void main(String[] args) {
  // new GNPSResultsAnalysisTask(new File(
  // "D:\\Daten2\\UCSD\\IIN_paper\\20190709_bile_acids\\FBMN_IINwithcorr\\FEATURE-BASED-MOLECULAR-NETWORKING-9103d908-download_cytoscape_data-main.graphml"),
  // new File(
  // "D:\\Daten2\\UCSD\\IIN_paper\\20190709_bile_acids\\FBMN_IINwithcorr\\20190709_bile_acid_standards_IIN.mgf"),
  // new File("D:\\Daten2\\UCSD\\IIN_paper\\20190709_bile_acids\\FBMN_IINwithcorr\\test2.csv"))
  // .run();
  // }
  public static void main(String[] args) {
    new GNPSResultsAnalysisTask(new File(
        "D:\\Dropbox\\IIN_PAPER\\Data (old)\\Statistics test\\FEATURE-BASED-MOLECULAR-NETWORKING-9103d908-download_cytoscape_data-main.graphml"),
        new File(
            "D:\\Dropbox\\IIN_PAPER\\Data (old)\\Statistics test\\20190709_bile_acid_standards_IIN.mgf"),
        new File("D:\\Dropbox\\IIN_PAPER\\Data (old)\\Statistics test\\test6.csv")).run();
  }

  private File file;
  private File fileMGF;
  private File output;
  private File outputLibrary;

  private AtomicDouble progress = new AtomicDouble(0);
  private ParameterSet parameters;
  private String step = "Importing GNPS results for";
  private final Boolean createSpecLib;
  private final Boolean createSummary;
  private Integer minSignals;
  private Double minRelativeIntensity;


  public enum NodeAtt {
    IIN_ADDUCT("Best Ion", String.class), //
    NET_ID("Annotated Adduct Features ID", Double.class), //
    MS2_VERIFICATION("MS2 Verification Comment", String.class), //
    NEUTRAL_MASS("neutral M mass", Double.class), //
    PRECURSOR_MASS("precursor mass", Double.class);

    public final String key;
    public final Class c;

    private NodeAtt(String key, Class c) {
      this.c = c;
      this.key = key;
    }

    public Class getValueClass() {
      return c;
    }

    public String getKey() {
      return key;
    }
  }
  public enum EdgeAtt {
    EDGE_TYPE("EdgeType", String.class), // edgetype
    EDGE_SCORE("EdgeScore", Double.class), EDGE_ANNOTATION("EdgeAnnotation", String.class);

    public final String key;
    public final Class c;

    private EdgeAtt(String key, Class c) {
      this.c = c;
      this.key = key;
    }

    public Class getValueClass() {
      return c;
    }

    public String getKey() {
      return key;
    }
  }

  public enum EdgeType {
    MS1_ANNOTATION("MS1 annotation"), COSINE("Cosine");
    public final String key;

    private EdgeType(String key) {
      this.key = key;
    }
  }

  public GNPSResultsAnalysisTask(ParameterSet parameters) {
    super(null);
    this.parameters = parameters;
    createSummary =
        parameters.getParameter(GNPSResultsAnalysisParameters.CREATE_SUMMARY).getValue();

    minSignals = parameters.getParameter(GNPSResultsAnalysisParameters.MIN_SIGNALS).getValue();
    minRelativeIntensity =
        parameters.getParameter(GNPSResultsAnalysisParameters.MIN_REL_INTENSITY).getValue();

    createSpecLib =
        parameters.getParameter(GNPSResultsAnalysisParameters.CREATE_SPECTRAL_LIB).getValue();
    file = parameters.getParameter(GNPSResultsAnalysisParameters.FILE).getValue();
    fileMGF = parameters.getParameter(GNPSResultsAnalysisParameters.FILE_MGF).getValue();
    output = parameters.getParameter(GNPSResultsAnalysisParameters.OUTPUT).getValue();
    outputLibrary =
        FileAndPathUtil.getRealFilePath(output.getParentFile(), output.getName(), ".json");
  }


  public GNPSResultsAnalysisTask(File file, File fileMGF, File output) {
    super(null);
    this.file = file;
    this.fileMGF = fileMGF;
    this.output = output;
    outputLibrary =
        FileAndPathUtil.getRealFilePath(output.getParentFile(), output.getName(), ".json");
    createSpecLib = false;
    createSummary = true;
  }

  @Override
  public double getFinishedPercentage() {
    return progress.get();
  }

  @Override
  public String getTaskDescription() {
    return "Analyse GNPS results  in file " + file + "and mgf " + fileMGF;
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);
    logger.info("Importing GNPS results for " + file + " and mgf " + fileMGF);

    // remove zeros from edge ids (GNPS export error)
    removeZeroIDFromEdge(file);

    GnpsResults res = importResults(file, fileMGF);


    if (res != null) {
      // create library in GNPS format
      if (createSpecLib) {
        IINLibraryCreationParameters methodParam =
            parameters.getParameter(GNPSResultsAnalysisParameters.CREATE_SPECTRAL_LIB)
                .getEmbeddedParameters();
        GNPSLibraryBatchExportTask libTask;
        try {
          libTask = new GNPSLibraryBatchExportTask(methodParam, fileMGF.getName(), outputLibrary,
              res, minSignals, minRelativeIntensity);
        } catch (IOException e1) {
          setErrorMessage("Could not configure or import sample list filter");
          setStatus(TaskStatus.ERROR);
          return;
        }
        MZmineCore.getTaskController().addTask(libTask);

        // copy mgf to new folder
        if (!fileMGF.getParentFile().equals(outputLibrary.getParentFile())) {
          File source = fileMGF;
          File dest = FileAndPathUtil.getRealFilePath(outputLibrary.getParentFile(),
              source.getName(), ".mgf");
          if (!dest.exists()) {
            try {
              FileUtils.copyFile(source, dest);
            } catch (IOException e) {
              logger.log(Level.WARNING, "Cannot copy mgf to new folder", e);
            }
          }
        }
      }

      // analyse and write files
      if (createSummary)
        analyse(output, res);


      //
      // File reducedFile = FileAndPathUtil.getRealFilePath(file.getParentFile(),
      // FileAndPathUtil.eraseFormat(file.getName()) + "_collapsedIIN", ".graphml");
      // logger.info("Exporting reduced graphml to " + reducedFile.getAbsolutePath());
      // MZmineCore.getTaskController().addTask(new NetworkGraphMLExportTask(reducedg,
      // reducedFile));

      logger.info("Finished import of GNPS results for " + file + " and " + fileMGF);
      setStatus(TaskStatus.FINISHED);
    } else {
      setErrorMessage("Error while importing graphml file: " + file.getAbsolutePath());
      setStatus(TaskStatus.ERROR);
    }
  }



  /**
   * Analyse and output file
   * 
   */
  private void analyse(File output, GnpsResults res) {
    Graph graph = res.getGraph();
    Map<Integer, GNPSLibraryMatch> matches = res.getMatches();
    Map<Integer, DataPoint[]> msmsData = res.getMsmsData();
    Map<Integer, IonIdentityNetworkResult> nets = res.getNets();

    DecimalFormat perc = new DecimalFormat("0.0");
    DecimalFormatSymbols dfs = perc.getDecimalFormatSymbols();
    dfs.setDecimalSeparator('.');
    perc.setDecimalFormatSymbols(dfs);

    StringBuilder general = new StringBuilder();
    StringBuilder distance = new StringBuilder();
    StringBuilder adduct = new StringBuilder();
    StringBuilder iin = new StringBuilder();

    Map<String, Integer> adductCount = mapAdducts(graph);
    long ions = adductCount.values().stream().mapToInt(i -> i).sum();
    long ionsWithMSMS = countAdductsWithMSMS(graph, msmsData, 0);
    long ident = matches.size();
    int total = msmsData.size();
    long ionsWithLibraryMatch = countIonsWithLibraryMatch(matches, graph);

    // #####################################################################
    // identified compounds
    // check if dataset is valid
    long identWithMSMS =
        matches.entrySet().stream().filter(e -> msmsData.get(e.getKey()) != null).count();
    appendLine(general, "Valid dataset (mgf fits graphml)?", identWithMSMS == ident);
    appendLine(general, "All library hits have MSMS in mgf", identWithMSMS == ident);
    appendLine(general, "Library hits with MSMS in mgf %",
        perc.format((identWithMSMS / (double) ident) * 100.0),
        "(should be 100 %; otherwise check if graphml was result of this mgf)");


    appendLine(general);
    appendLine(general, "total nodes", graph.getNodeCount(),
        "All nodes (might include some nodes without MS/MS scan but ion identity)");
    appendLine(general, "total nodes with MS/MS", total, "All nodes with MS/MS scan");
    appendLine(general, "Library matches (MS2)", ident, "Library matches with the GNPS library");
    appendLine(general, "Ion identities (MS1)", ions,
        "Nodes with ion identities (can include some without MS/MS scan)");
    appendLine(general, "Ion identities with MS/MS", ionsWithMSMS,
        "Nodes with ion identity and MS/MS scan");
    appendLine(general, "library matches with ion identity", ionsWithLibraryMatch);
    appendLine(general, "library matches with ion identity %",
        perc.format(ionsWithLibraryMatch / (double) ident * 100.0));
    appendLine(general, "Ion Identity Networks", nets.size());

    appendLine(general);
    // write all percentages for different min signals MS/MS scan cut off
    appendLine(general,
        "Percentage of identified compounds per feature with a minimum of X signals in MS/MS spectrum");
    appendLine(general,
        "Nodes reduced by IIN = All nodes (with MS/MS) in a ion identity network reduced to 1 neutral molecule node");
    // MS/MS signals 0, 1,4,6
    int[] min = new int[] {0, 1, 4, 6};
    appendLine(general, "min signals", "MS/MS scans", "identified", "identified  %",
        "singletons FBMN", "singletons IIN+FBMN", "% singletons FBMN", "% singletons IIN+FBMN",
        "Nodes reduced by IIN", "% nodes reduced by IIN", "Remaining nodes after IIN reduction",
        "Possible new library spectra by IIN");
    for (int minSignals : min) {
      long msmsSpectra = minSignals == 0 ? total
          : msmsData.values().stream().filter(signals -> signals.length >= minSignals).count();

      long identified = countIdentified(matches, msmsData, minSignals);
      String ratio = perc.format(identified / (double) msmsSpectra * 100.0);
      // singletons FBMN vs IIN+FBMN
      long[] singletons = countSingletons(graph, msmsData, minSignals);
      String ratioFBMNSingle = perc.format(singletons[0] / (double) msmsSpectra * 100.0);
      String ratioIINSingle = perc.format(singletons[1] / (double) msmsSpectra * 100.0);

      // percentage of nodes reduced by iin
      int reducedByIIN =
          nets.values().stream().mapToInt(net -> net.getReducedNumber(msmsData, minSignals)).sum();
      String ratioIINreduced = perc.format(reducedByIIN / (double) msmsSpectra * 100.0);

      // possible new library entries
      int newLibraryEntries = nets.values().stream()
          .mapToInt(net -> net.countPossibleNewLibraryEntries(msmsData, minSignals, matches)).sum();

      // add data line
      appendLine(general, minSignals, msmsSpectra, identified, ratio, singletons[0], singletons[1],
          ratioFBMNSingle, ratioIINSingle, reducedByIIN, ratioIINreduced,
          msmsSpectra - reducedByIIN, newLibraryEntries);
    }

    // #####################################################################
    // distance to identity
    appendSeparator(distance);
    appendLine(distance,
        "distance to identified compounds (each node is only counted once (even if connected to multiple identified compounds)");
    appendLine(distance, "Feature based molecular networking only (NO IIN)");
    // header
    int maxDist = 5;
    distance.append(del + "dist");
    for (int i = 0; i <= maxDist; i++)
      distance.append(del + i);
    for (int i = 0; i <= maxDist; i++)
      distance.append(del + i);
    distance.append(nl);

    distance.append("min signals" + del + "MS/MS scans");
    for (int i = 0; i <= maxDist; i++)
      distance.append(del + "n within " + i);
    for (int i = 0; i <= maxDist; i++)
      distance.append(del + "% within " + i);
    distance.append(nl);

    for (int minSignals : min) {
      long msmsSpectra = minSignals == 0 ? total
          : msmsData.values().stream().filter(signals -> signals.length >= minSignals).count();
      // count in distance
      int[] dist = countInDist(graph, matches, msmsData, minSignals, maxDist, true);

      distance.append(minSignals + del + msmsSpectra);
      for (int i = 0; i <= maxDist; i++)
        distance.append(del + dist[i]);
      for (int i = 0; i <= maxDist; i++)
        distance.append(del + (perc.format(dist[i] / (double) msmsSpectra * 100.0)));
      distance.append(nl);
    }

    // ###############################
    // with IIN
    distance.append(nl + nl + "FBMN and IIN edges" + nl);
    distance.append(del + "dist");
    for (int i = 0; i <= maxDist; i++)
      distance.append(del + i);
    for (int i = 0; i <= maxDist; i++)
      distance.append(del + i);
    distance.append(nl);

    distance.append("min signals" + del + "MS/MS scans");
    for (int i = 0; i <= maxDist; i++)
      distance.append(del + "n within " + i);
    for (int i = 0; i <= maxDist; i++)
      distance.append(del + "% within " + i);
    distance.append(nl);

    for (int minSignals : min) {
      long msmsSpectra = minSignals == 0 ? total
          : msmsData.values().stream().filter(signals -> signals.length >= minSignals).count();
      // count in distance
      int[] distIIN = countInDist(graph, matches, msmsData, minSignals, maxDist, false);

      distance.append(minSignals + del + msmsSpectra);
      for (int i = 0; i <= maxDist; i++)
        distance.append(del + distIIN[i]);
      for (int i = 0; i <= maxDist; i++)
        distance.append(del + (perc.format(distIIN[i] / (double) msmsSpectra * 100.0)));
      distance.append(nl);
    }

    appendSeparator(distance);

    // #####################################################################
    // adduct analysis
    appendLine(adduct, "Ion identities", ions);
    appendLine(adduct);

    appendLine(adduct, "Adduct distribution,,,,,,Adduct distribution (sorted by n)");
    appendLine(adduct, "ion type", "n", "", "", "", "", "", "ion type", "n");
    List<Entry<String, Integer>> byName = streamSorted(adductCount).collect(Collectors.toList());
    List<Entry<String, Integer>> byN = streamSortedByN(adductCount).collect(Collectors.toList());

    for (int i = 0; i < byName.size(); i++) {
      appendLine(adduct, byName.get(i).getKey(), byName.get(i).getValue(), "", "", "", "", "",
          byN.get(i).getKey(), byN.get(i).getValue());
    }

    appendSeparator(adduct);

    appendLine(iin, "Ion identity networks");
    appendLine(iin, "", "", "", "", "", "", "", "Possible library entries with n signals", "", "",
        "");
    appendLine(iin, "NetID", "net size", "nodes with MSMS", "identified", "all ions",
        "ions matched library", "Reduction by", "n=3", "ions n=3", "n=4", "ions n=4", "n=6",
        "ions n=6");

    // start with largest network
    nets.entrySet().stream()
        .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size())).forEach(e -> {
          IonIdentityNetworkResult net = e.getValue();
          Integer netID = e.getKey();
          int withMSMS = net.countWithMSMS(msmsData, 0);
          int identified = net.countIdentified(matches);
          String matchedIons =
              net.streamIdentifiedIonStrings(matches).sorted().collect(Collectors.joining(" | "));
          String allIons = net.stream().map(IonIdentityNetworkResult::getIonString).sorted()
              .collect(Collectors.joining(" | "));
          int reduction = net.getReducedNumber(msmsData, 0);
          int new3 = net.countPossibleNewLibraryEntries(msmsData, 3, matches);
          int new4 = net.countPossibleNewLibraryEntries(msmsData, 4, matches);
          int new6 = net.countPossibleNewLibraryEntries(msmsData, 6, matches);
          String ions3 = net.streamPossibleNewLibraryEntries(msmsData, 3, matches)
              .map(IonIdentityNetworkResult::getIonString).sorted()
              .collect(Collectors.joining(" | "));
          String ions4 = net.streamPossibleNewLibraryEntries(msmsData, 4, matches)
              .map(IonIdentityNetworkResult::getIonString).sorted()
              .collect(Collectors.joining(" | "));
          String ions6 = net.streamPossibleNewLibraryEntries(msmsData, 6, matches)
              .map(IonIdentityNetworkResult::getIonString).sorted()
              .collect(Collectors.joining(" | "));

          appendLine(iin, netID, net.size(), withMSMS, identified, allIons, matchedIons, reduction,
              new3, ions3, new4, ions4, new6, ions6);
        });


    writeToFile(output, general, distance, adduct, iin);
  }

  private Stream<Node> streamNodesWithMSMS(Graph graph, Map<Integer, DataPoint[]> msmsData,
      int minSignals) {
    return graph.nodes().filter(n -> hasMSMS(n, msmsData, minSignals));
  }

  private Stream<Node> streamIdentifiedNodes(Graph graph, Map<Integer, GNPSLibraryMatch> matches,
      Map<Integer, DataPoint[]> msmsData, int minSignals) {
    return streamNodesWithMSMS(graph, msmsData, minSignals).filter(n -> isIdentified(n, matches));
  }

  private long countIdentified(Map<Integer, GNPSLibraryMatch> matches,
      Map<Integer, DataPoint[]> msmsData, int minSignals) {
    return matches.entrySet().stream().map(e -> msmsData.get(e.getKey())).filter(Objects::nonNull)
        .filter(signals -> signals.length >= minSignals).count();
  }

  /**
   * Count all library matches with an ion identity based on MS1
   * 
   * @param matches
   * @param graph
   * @return
   */
  private long countIonsWithLibraryMatch(Map<Integer, GNPSLibraryMatch> matches, Graph graph) {
    return matches.keySet().stream().map(Object::toString)
        .filter(id -> getIonIdentity(graph.getNode(id)) != null).count();
  }

  /**
   * sorted by name
   * 
   * @param adductCount
   * @return
   */
  private Stream<Entry<String, Integer>> streamSorted(Map<String, Integer> adductCount) {
    return adductCount.entrySet().stream().sorted((a, b) -> a.getKey().compareTo(b.getKey()));
  }

  /**
   * Sorted by count
   * 
   * @param adductCount
   * @return
   */
  private Stream<Entry<String, Integer>> streamSortedByN(Map<String, Integer> adductCount) {
    return adductCount.entrySet().stream()
        .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()));
  }

  /**
   * Count adducts of different ion identities
   * 
   * @param graph
   * @return
   */
  private Map<String, Integer> mapAdducts(Graph graph) {
    Map<String, Integer> map = new HashMap<>();
    graph.nodes().map(n -> getIonIdentity(n)).filter(Objects::nonNull).filter(s -> !s.isEmpty())
        .forEach(ion -> {
          Integer count = map.get(ion);
          map.put(ion, count == null ? 1 : count + 1);
        });
    return map;
  }

  /**
   * Count different types of ion identites
   * 
   * @param graph
   * @param msmsData
   * @param minSignals
   * @return
   */
  private Map<String, Integer> mapAdductsWithMSMS(Graph graph, Map<Integer, DataPoint[]> msmsData,
      int minSignals) {
    Map<String, Integer> map = new HashMap<>();
    graph.nodes().filter(n -> hasMSMS(n, msmsData, minSignals)).map(n -> getIonIdentity(n))
        .filter(Objects::nonNull).filter(s -> !s.isEmpty()).forEach(ion -> {
          Integer count = map.get(ion);
          map.put(ion, count == null ? 1 : count + 1);
        });
    return map;
  }

  /**
   * Total number of ion identities with MSMS
   * 
   * @param graph
   * @param msmsData
   * @param minSignals
   * @return
   */
  private int countAdductsWithMSMS(Graph graph, Map<Integer, DataPoint[]> msmsData,
      int minSignals) {
    return mapAdductsWithMSMS(graph, msmsData, minSignals).values().stream().mapToInt(i -> i).sum();
  }


  /**
   * Network id or null
   * 
   * @param n
   * @return
   */
  private Integer getIonNetworkID(Node n) {
    Object o = n.getAttribute(NodeAtt.NET_ID.key);
    if (o == null || o.toString().isEmpty())
      return null;
    try {
      double d = Double.valueOf(o.toString());
      return Integer.valueOf((int) d);
    } catch (Exception e) {
      logger.log(Level.WARNING, "Cannot convert net id to int", e);
    }
    return null;
  }

  /**
   * 
   * @param n
   * @return ion identity or null
   */
  private String getIonIdentity(Node n) {
    Object o = n.getAttribute(NodeAtt.IIN_ADDUCT.key);
    return o == null || o.toString().isEmpty() ? null : o.toString();
  }

  private HashMap<Integer, List<Node>> getIonNetworks(Graph g) {
    HashMap<Integer, List<Node>> nets = new HashMap<>();
    g.nodes().forEach(n -> {
      Integer netID = getIonNetworkID(n);
      if (netID != null) {
        if (nets.containsKey(netID)) {
          nets.get(netID).add(n);
        } else {
          List<Node> list = new ArrayList<>();
          list.add(n);
          nets.put(netID, list);
        }
      }
    });
    return nets;
  }

  /**
   * Should be more correct as some nodes are connected to different Ion Identity networks
   * 
   * @param g
   * @return
   */
  private HashMap<Integer, IonIdentityNetworkResult> getIonNetworksByEdges(Graph g) {
    HashMap<Integer, IonIdentityNetworkResult> nets = new HashMap<>();
    List<Node> done = new ArrayList<>();

    g.nodes().forEach(n -> {
      addAllToIonNetworks(nets, done, n);
    });
    logger.info("Found " + nets.size() + " IIN");
    return nets;
  }

  /**
   * Recursive method to add all nodes that are connected by Ion identity networking edges to a
   * network in a map
   * 
   * @param nets
   * @param done
   * @param n
   */
  private void addAllToIonNetworks(HashMap<Integer, IonIdentityNetworkResult> nets, List<Node> done,
      Node n) {
    // check every node only once
    if (!done.contains(n)) {
      // add to done list
      done.add(n);

      Integer netID = getIonNetworkID(n);
      if (netID != null) {
        IonIdentityNetworkResult net;
        if (nets.containsKey(netID)) {
          net = nets.get(netID);
          net.add(n);
        } else {
          net = new IonIdentityNetworkResult();
          nets.put(netID, net);
          net.add(n);
        }

        // add all linked nodes and perform the same
        streamIINLinkedNodes(n).forEach(next -> {
          addAllToIonNetworks(nets, done, next);
          // also add to this network if id is different...
          Integer nextID = getIonNetworkID(next);
          if (nextID != null && !netID.equals(nextID)) {
            net.add(next);
          }
        });
      }
    }
  }


  /**
   * count nodes with MSMS that have identified compounds in distance
   * 
   * @param graph
   * @param matches
   * @param msmsData
   * @param minSignals
   * @return
   */
  private int[] countInDist(Graph graph, Map<Integer, GNPSLibraryMatch> matches,
      Map<Integer, DataPoint[]> msmsData, int minSignals, int maxDist, boolean onlyCosineEdges) {

    int[] indist = new int[maxDist + 1];
    indist[0] = matches.size();
    for (int i = 1; i <= maxDist; i++) {
      // list of nodes in distance to ID
      List<Integer> list = new ArrayList<>();
      list.addAll(streamIdentifiedNodes(graph, matches, msmsData, minSignals).map(n -> toIndex(n))
          .collect(Collectors.toList()));

      final int dist = i;
      streamIdentifiedNodes(graph, matches, msmsData, minSignals).forEach(
          n -> addAllInDistance(list, graph, n, msmsData, minSignals, dist, onlyCosineEdges));
      indist[i] = list.size();
    }

    return indist;
  }

  /**
   * Node index was peak list row index
   * 
   * @param n
   * @return
   */
  private Integer toIndex(Node n) {
    return Integer.parseInt(n.getId());
  }


  /**
   * Adds all nodes in distance to list
   * 
   * @param list
   * @param graph
   * @param msmsData
   * @param minSignals
   */
  private void addAllInDistance(List<Integer> list, Graph graph, Node n,
      Map<Integer, DataPoint[]> msmsData, int minSignals, int dist, boolean onlyCosineEdges) {
    // no need to check for MSMS data here - later when add to list
    if (n != null) {
      // stream only cosine edges (FBMN) or all edges (IIN FBMN)
      Stream<Edge> edges;
      if (onlyCosineEdges)
        edges = streamCosineEdges(n);
      else
        edges = streamEdges(n);

      // add all neighbours to the list
      edges.map(e -> getLinkedNode(n, e)).forEach(next -> {
        // not already inserted
        Integer id = toIndex(next);
        if (!list.contains(id)) {
          // only add features with MSMS scan to be comparable to FBMN
          if (hasMSMS(next, msmsData, minSignals))
            list.add(id);
          // add nodes next to next
        }
        if (dist >= 2) {
          addAllInDistance(list, graph, next, msmsData, minSignals, dist - 1, onlyCosineEdges);
        }
      });
    }
  }


  private long[] countSingletons(Graph graph, Map<Integer, DataPoint[]> msmsData, int minSignals) {
    // 0 count all FBMN singletons (no IIN edges, with msms data)
    // 1 count all IIN FBMN singletons (with msms data)
    return new long[] {
        graph.nodes().filter(n -> !hasCosineEdges(n) && hasMSMS(n, msmsData, minSignals)).count(),
        // count IIN FBMN singletons
        graph.nodes().filter(n -> !hasEdges(n) && hasMSMS(n, msmsData, minSignals)).count()};
  }

  /**
   * Stream cosine edges without selfloops
   * 
   * @param n
   * @return
   */
  private Stream<Edge> streamCosineEdges(Node n) {
    return n.edges()
        .filter(e -> e.getAttribute(EdgeAtt.EDGE_TYPE.getKey()).equals(EdgeType.COSINE.key))
        .filter(e -> !e.getNode0().getId().equals(e.getNode1().getId()));
  }

  /**
   * stream ion identity networking edges
   * 
   * @param n
   * @return
   */
  private Stream<Edge> streamIINEdges(Node n) {
    return n.edges()
        .filter(e -> e.getAttribute(EdgeAtt.EDGE_TYPE.getKey()).equals(EdgeType.MS1_ANNOTATION.key))
        .filter(e -> !e.getNode0().getId().equals(e.getNode1().getId()));
  }

  /**
   * stream ion identity networking nodes that are connected to n
   * 
   * @param n
   * @return
   */
  private Stream<Node> streamIINLinkedNodes(Node n) {
    return n.edges()
        .filter(e -> e.getAttribute(EdgeAtt.EDGE_TYPE.getKey()).equals(EdgeType.MS1_ANNOTATION.key))
        .filter(e -> !e.getNode0().getId().equals(e.getNode1().getId()))
        .map(e -> getLinkedNode(n, e));
  }

  private Node getLinkedNode(Node n, Edge e) {
    if (n.getId().equals(e.getNode0().getId()))
      return e.getNode1();
    else
      return e.getNode0();
  }

  /**
   * Still contains ion identity networking edges
   * 
   * @param g
   * @return
   */
  private boolean hasIINEdges(Graph g) {
    return g.edges().anyMatch(
        e -> e.getAttribute(EdgeAtt.EDGE_TYPE.getKey()).equals(EdgeType.MS1_ANNOTATION.key));
  }

  /**
   * Stream edges without selfloops
   * 
   * @param n
   * @return
   */
  private Stream<Edge> streamEdges(Node n) {
    return n.edges().filter(e -> !e.getNode0().getId().equals(e.getNode1().getId()));
  }

  /**
   * minimum signals above cutOffFromMaxIntensity
   * 
   * @param n
   * @param msmsData
   * @param minSignals
   * @param cutOffFromMaxIntensity 0.01 is 1 % of max intensity
   * @return
   */
  private boolean hasMSMS(Node n, Map<Integer, DataPoint[]> msmsData, int minSignals,
      final double cutOffFromMaxIntensity) {
    DataPoint[] signals = msmsData.get(toIndex(n));
    if (signals == null)
      return false;
    final double max = Arrays.stream(signals).mapToDouble(DataPoint::getIntensity).max().orElse(0);
    long dp = Arrays.stream(signals).mapToDouble(DataPoint::getIntensity)
        .filter(intensity -> intensity >= max * cutOffFromMaxIntensity).count();
    return dp >= minSignals;
  }

  private boolean hasMSMS(Node n, Map<Integer, DataPoint[]> msmsData, int minSignals) {
    DataPoint[] signals = msmsData.get(Integer.parseInt(n.getId()));
    return signals != null && signals.length >= minSignals;
  }

  private boolean isIdentified(Node n, Map<Integer, GNPSLibraryMatch> matches) {
    return matches.containsKey(Integer.parseInt(n.getId()));
  }

  private boolean hasEdges(Node n) {
    return streamEdges(n).count() > 0l;
  }

  private boolean hasCosineEdges(Node n) {
    return streamCosineEdges(n).count() > 0l;
  }

  private void logImportResults(Graph graph, Map<Integer, GNPSLibraryMatch> matches,
      Map<Integer, DataPoint[]> msmsData) {
    logger.info(MessageFormat.format("Results: nodes={0}  edges={1}", graph.getNodeCount(),
        graph.getEdgeCount()));
    logger.info(MessageFormat.format("library matches={0}", matches.size()));
    logger.info(MessageFormat.format("features with MS/MS scan {0} (of {1})", msmsData.size(),
        graph.getNodeCount()));

    long min4 = msmsData.values().stream().filter(signals -> signals.length >= 4).count();
    long min6 = msmsData.values().stream().filter(signals -> signals.length >= 6).count();

    logger.info(MessageFormat.format("features with MS/MS scan and min 4 signals  {0} (of {1})",
        min4, graph.getNodeCount()));
    logger.info(MessageFormat.format("features with MS/MS scan and min 6 signals  {0} (of {1})",
        min6, graph.getNodeCount()));
  }


  // ##################################################################################
  // IMPORT
  /**
   * Import graphml and mgf (MS/MS scans)
   * 
   * @param file
   * @param fileMGF
   * @return
   */
  private GnpsResults importResults(File file, File fileMGF) {
    Graph graph = new MultiGraph("GNPS");
    if (importGraphData(graph, file)) {
      progress.set(0d);
      step = "Importing library matches";
      logger.info("Starting to import library matches");
      // import library matches from nodes
      HashMap<Integer, GNPSLibraryMatch> matches = importLibraryMatches(graph);

      logger.info("Starting to import MS2 data from mgf");
      HashMap<Integer, DataPoint[]> msmsData = importMSMSfromMgf(fileMGF);

      //
      HashMap<Integer, IonIdentityNetworkResult> nets = getIonNetworksByEdges(graph);

      // log some results
      logImportResults(graph, matches, msmsData);

      return new GnpsResults(graph, nets, msmsData, matches);
    }
    return null;
  }

  /**
   * All
   * 
   * @param file an mgf file that was used for GNPS feature based molecular networking
   * @return Map<featureID, signals in MS/MS>
   */
  private HashMap<Integer, DataPoint[]> importMSMSfromMgf(File file) {
    HashMap<Integer, DataPoint[]> msmsData = new HashMap<>();
    Path path = Paths.get(file.getAbsolutePath());
    try (Stream<String> lines = Files.lines(path)) {
      AtomicInteger featureID = new AtomicInteger(-1);
      List<DataPoint> spec = new ArrayList<>();
      AtomicBoolean readSignals = new AtomicBoolean(false);

      lines.forEach(line -> {
        // find next FEATURE_ID=1
        if (line.startsWith("FEATURE_ID=")) {
          featureID.set(Integer.parseInt(line.split("=")[1]));
        } else if (line.startsWith("MSLEVEL")) {
          // restart array
          spec.clear();
          readSignals.getAndSet(true);
        } else if (line.startsWith("END IONS")) {
          // create
          msmsData.put(featureID.get(), spec.toArray(new DataPoint[0]));
          readSignals.getAndSet(false);
        } else if (readSignals.get()) {
          try {
            String[] split = line.split(" ");
            spec.add(
                new SimpleDataPoint(Double.parseDouble(split[0]), Double.parseDouble(split[1])));
          } catch (Exception e) {
            logger.log(Level.WARNING, "Cannot read mz / intensity from mgf: " + line, e);
          }
        }
      });
      return msmsData;
    } catch (IOException e) {
      logger.log(Level.SEVERE, "graphml NOT LOADED: " + file.getAbsolutePath(), e);
      setErrorMessage("Cannot load graphml file: " + file.getAbsolutePath());
      setStatus(TaskStatus.ERROR);
      cancel();
      return null;
    }
  }

  /**
   * All edges have id=0 - this causes an exception. Replace all zero ids and save the file
   * 
   */
  private void removeZeroIDFromEdge(File file) {
    AtomicLong counter = new AtomicLong(1);
    logger.info("replacing zero ids in graphml");
    Path path = Paths.get(file.getAbsolutePath());
    try (Stream<String> lines = Files.lines(path)) {
      List<String> replaced = lines.map(line -> {
        line = line.replaceAll("edge id=\"0\"", "edge id=\"" + counter.getAndIncrement() + "\"");
        line = line.replaceAll("edge id=\"1\"", "edge id=\"" + counter.getAndIncrement() + "\"");
        line = line.replaceAll("edge id=\"2\"", "edge id=\"" + counter.getAndIncrement() + "\"");
        line = line.replaceAll("edge id=\"3\"", "edge id=\"" + counter.getAndIncrement() + "\"");
        line = line.replaceAll("edge id=\"4\"", "edge id=\"" + counter.getAndIncrement() + "\"");
        return line;
      }).collect(Collectors.toList());
      Files.write(path, replaced);
      lines.close();
      logger.info("zero ids in graphml replaced");
    } catch (IOException e) {
      logger.log(Level.SEVERE, "graphml NOT LOADED: " + file.getAbsolutePath(), e);
      setErrorMessage("Cannot load graphml file: " + file.getAbsolutePath());
      setStatus(TaskStatus.ERROR);
    }
  }


  private HashMap<Integer, GNPSLibraryMatch> importLibraryMatches(Graph graph) {
    HashMap<Integer, GNPSLibraryMatch> matches = new HashMap<>();

    // go through all nodes and add info
    final int size = graph.getNodeCount();
    AtomicInteger done = new AtomicInteger(0);
    graph.nodes().forEach(node -> {
      int id = Integer.parseInt(node.getId());
      // has library match?
      String compoundName = (String) node.getAttribute(ATT.COMPOUND_NAME.getKey());
      if (compoundName != null && !compoundName.isEmpty()) {
        String adduct = (String) node.getAttribute(ATT.ADDUCT.getKey());

        // find all results
        HashMap<String, Object> results = new HashMap<>();
        for (ATT att : ATT.values()) {
          Object result = node.getAttribute(att.getKey());
          if (result != null) {
            results.put(att.getKey(), result);
          }
        }

        // add identity
        GNPSLibraryMatch identity = new GNPSLibraryMatch(results, compoundName, id);
        matches.put(id, identity);
      }

      // increment
      done.getAndIncrement();
      progress.set(done.get() / (double) size);
    });

    logger.info(matches.size() + " rows found with library matches");
    return matches;
  }

  private boolean importGraphData(Graph graph, File file) {
    boolean result = true;
    FileSource fs = null;
    logger.info("Importing graphml data");
    try {
      fs = new FileSourceGraphML();
      fs.addSink(graph);
      fs.readAll(file.getAbsolutePath());
      logger.info(() -> MessageFormat.format("GNPS results: nodes={0} edges={1}",
          graph.getNodeCount(), graph.getEdgeCount()));
    } catch (Exception e) {
      logger.log(Level.SEVERE, "NOT LOADED", e);
      setErrorMessage("Cannot load graphml file: " + file.getAbsolutePath());
      setStatus(TaskStatus.ERROR);
      result = false;
    } finally {
      try {
        if (fs != null)
          fs.removeSink(graph);
      } catch (Exception e2) {
        logger.log(Level.SEVERE, "NOT LOADED", e2);
        setErrorMessage(
            "Cannot close file sink while loading graphml file: " + file.getAbsolutePath());
        setStatus(TaskStatus.ERROR);
      }
    }
    return result;
  }

  // ##################################################################################
  // WRITING
  private void writeToFile(File output, StringBuilder general, StringBuilder distance,
      StringBuilder adduct, StringBuilder iin) {

    Path path = Paths.get(output.getAbsolutePath());
    try {
      List<String> text = new ArrayList<>();
      text.add(general.toString());
      text.add(distance.toString());
      text.add(adduct.toString());
      text.add(iin.toString());
      Files.write(path, text);
      logger.info("Exported all to " + output.getAbsolutePath());
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Cannot export to: " + output.getAbsolutePath(), e);
      setErrorMessage("Cannot export to: " + output.getAbsolutePath());
      setStatus(TaskStatus.ERROR);
      cancel();
    }
  }

  private void appendSeparator(StringBuilder distance) {
    distance
        .append(nl + "###################################################################" + nl);
  }

  private void appendLine(StringBuilder b, Object... s) {
    if (s != null)
      b.append(Arrays.stream(s).map(o -> o.toString()).collect(Collectors.joining(del)));
    b.append(nl);
  }
}
