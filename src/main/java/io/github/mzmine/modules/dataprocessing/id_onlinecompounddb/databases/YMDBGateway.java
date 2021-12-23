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

package io.github.mzmine.modules.dataprocessing.id_onlinecompounddb.databases;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.features.compoundannotations.CompoundDBAnnotation;
import io.github.mzmine.datamodel.features.compoundannotations.SimpleCompoundDBAnnotation;
import io.github.mzmine.modules.dataprocessing.id_onlinecompounddb.DBGateway;
import io.github.mzmine.modules.dataprocessing.id_onlinecompounddb.OnlineDatabases;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.util.InetUtils;
import java.io.IOException;
import java.net.URL;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YMDBGateway implements DBGateway {

  private Logger logger = Logger.getLogger(this.getClass().getName());

  private static final String ymdbSearchAddress =
      "http://www.ymdb.ca/structures/search/compounds/mass?";
  public static final String ymdbSDFAddress = "http://www.ymdb.ca/structures/compounds/";
  public static final String ymdbEntryAddress = "http://www.ymdb.ca/compounds/";

  public String[] findCompounds(double mass, MZTolerance mzTolerance, int numOfResults,
      ParameterSet parameters) throws IOException {

    Range<Double> toleranceRange = mzTolerance.getToleranceRange(mass);

    String queryAddress = ymdbSearchAddress + "query_from=" + toleranceRange.lowerEndpoint()
        + "&query_to=" + toleranceRange.upperEndpoint();

    URL queryURL = new URL(queryAddress);

    // Submit the query
    logger.finest("Querying YMDB URL " + queryURL);
    String queryResult = InetUtils.retrieveData(queryURL);

    // Organize the IDs as a TreeSet to keep them sorted
    TreeSet<String> results = new TreeSet<String>();

    // Find IDs in the HTML data
    Pattern pat = Pattern.compile("/compounds/(YMDB[0-9]{5})");
    Matcher matcher = pat.matcher(queryResult);
    while (matcher.find()) {
      String ymdbID = matcher.group(1);
      results.add(ymdbID);
    }

    // Remove all except first numOfResults IDs. The reason why we first
    // retrieve all results and then remove those above numOfResults is to
    // keep the lowest YDMB IDs - these may be the most interesting ones.
    while (results.size() > numOfResults) {
      String lastItem = results.last();
      results.remove(lastItem);
    }

    return results.toArray(new String[0]);

  }

  /**
   * This method retrieves the details about YMDB compound
   * 
   */
  public CompoundDBAnnotation getCompound(String ID, ParameterSet parameters) throws IOException {

    // We will parse the name and formula from the SDF file, it seems like
    // the easiest way
    URL sdfURL = new URL(ymdbSDFAddress + ID + ".sdf");

    logger.finest("Querying YMDB URL " + sdfURL);
    String sdfRecord = InetUtils.retrieveData(sdfURL);
    String lines[] = sdfRecord.split("\n");

    String compoundName = null;
    String compoundFormula = null;
    URL entryURL = new URL(ymdbEntryAddress + ID);
    URL structure2DURL = sdfURL;
    URL structure3DURL = new URL(ymdbSDFAddress + ID + ".sdf?dim=3d");

    for (int i = 0; i < lines.length - 1; i++) {

      if (lines[i].contains("> <GENERIC_NAME>")) {
        compoundName = lines[i + 1];
      }

      if (lines[i].contains("> <FORMULA>")) {
        compoundFormula = lines[i + 1];
      }
    }

    if (compoundName == null) {
      throw (new IOException("Could not parse compound name"));
    }

    CompoundDBAnnotation newCompound = new SimpleCompoundDBAnnotation(OnlineDatabases.YMDB, ID, compoundName, compoundFormula,
        entryURL, structure2DURL, structure3DURL);

    return newCompound;

  }
}
