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
import io.github.mzmine.util.RangeUtils;
import java.io.IOException;
import java.net.URL;
import java.util.Vector;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MassBankEuropeGateway implements DBGateway {

  private Logger logger = Logger.getLogger(this.getClass().getName());

  private static final String massBankSearchAddress =
      "https://massbank.eu/MassBank/Result.jsp?type=quick&formula=&compound=&"
          + "ms=all&ms=MS&ms=MS2&ms=MS3&ms=MS4&ion=0&op1=and&"
          + "inst_grp=ESI&inst=CE-ESI-TOF&inst=ESI-ITFT&inst=ESI-ITTOF&inst=ESI-QTOF&inst=ESI-TOF&"
          + "inst=LC-ESI-IT&inst=LC-ESI-ITFT&inst=LC-ESI-ITTOF&inst=LC-ESI-Q&inst=LC-ESI-QFT&"
          + "inst=LC-ESI-QIT&inst=LC-ESI-QQ&inst=LC-ESI-QTOF&inst=LC-ESI-TOF";

  private static final String massBankEntryAddress =
      "https://massbank.eu/MassBank/RecordDisplay.jsp?id=";

  public String[] findCompounds(double mass, MZTolerance mzTolerance, int numOfResults,
      ParameterSet parameters) throws IOException {

    Range<Double> toleranceRange = mzTolerance.getToleranceRange(mass);

    StringBuilder queryAddress = new StringBuilder(massBankSearchAddress);

    queryAddress.append("&mz=");
    queryAddress.append(RangeUtils.rangeCenter(toleranceRange));
    queryAddress.append("&tol=");
    queryAddress.append(RangeUtils.rangeLength(toleranceRange) / 2.0);

    URL queryURL = new URL(queryAddress.toString());

    // Submit the query
    logger.finest("Querying MassBank.eu URL " + queryURL);
    String queryResult = InetUtils.retrieveData(queryURL);

    Vector<String> results = new Vector<String>();

    // Find IDs in the HTML data
    Pattern pat = Pattern.compile("&nbsp;&nbsp;&nbsp;&nbsp;([A-Z0-9]{8})&nbsp;");
    Matcher matcher = pat.matcher(queryResult);
    while (matcher.find()) {
      String MID = matcher.group(1);
      results.add(MID);
      if (results.size() == numOfResults)
        break;
    }

    return results.toArray(new String[0]);

  }

  /**
   * This method retrieves the details about the compound
   * 
   */
  public CompoundDBAnnotation getCompound(String ID, ParameterSet parameters) throws IOException {

    URL entryURL = new URL(massBankEntryAddress + ID);

    // Retrieve data
    logger.finest("Querying MassBank.eu URL " + entryURL);
    String massBankEntry = InetUtils.retrieveData(entryURL);

    String compoundName = null;
    String compoundFormula = null;
    URL structure2DURL = null;
    URL structure3DURL = null;
    URL databaseURL = entryURL;

    // Find compound name
    Pattern patName = Pattern.compile("RECORD_TITLE: (.*)");
    Matcher matcherName = patName.matcher(massBankEntry);
    if (matcherName.find()) {
      compoundName = matcherName.group(1).replaceAll("\\<[^>]*>", "");
    }

    // Find compound formula
    Pattern patFormula = Pattern.compile("CH\\$FORMULA: .*>(.*)</a>");
    Matcher matcherFormula = patFormula.matcher(massBankEntry);
    if (matcherFormula.find()) {
      compoundFormula = matcherFormula.group(1).replaceAll("\\<[^>]*>", "");
    }

    if (compoundName == null) {
      logger.warning("Could not parse compound name for compound " + ID);
      return null;
    }

    CompoundDBAnnotation newCompound = new SimpleCompoundDBAnnotation(OnlineDatabases.MASSBANKEurope, ID, compoundName,
        compoundFormula, databaseURL, structure2DURL, structure3DURL);

    return newCompound;

  }
}
