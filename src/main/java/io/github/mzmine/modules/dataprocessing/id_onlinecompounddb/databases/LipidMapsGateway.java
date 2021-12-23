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

public class LipidMapsGateway implements DBGateway {

  private Logger logger = Logger.getLogger(this.getClass().getName());

  public static final String lipidMapsSearchAddress =
      "http://www.lipidmaps.org/data/structure/LMSDSearch.php?Mode=ProcessTextSearch&OutputMode=File&OutputColumnHeader=No&";
  public static final String lipidMapsEntryAddress =
      "http://www.lipidmaps.org/data/structure/LMSDSearch.php?Mode=ProcessStrSearch&OutputColumnHeader=No&OutputMode=File&LMID=";
  public static final String lipidMapsStructureAddress =
      "http://www.lipidmaps.org/data/structure/LMSDSearch.php?Mode=ProcessStrSearch&OutputMode=File&OutputType=SDF&LMID=";
  public static final String lipidMapsDetailsAddress =
      "http://www.lipidmaps.org/data/LMSDRecord.php?LMID=";

  public String[] findCompounds(double mass, MZTolerance mzTolerance, int numOfResults,
      ParameterSet parameters) throws IOException {

    Range<Double> toleranceRange = mzTolerance.getToleranceRange(mass);

    String queryAddress =
        lipidMapsSearchAddress + "ExactMass=" + RangeUtils.rangeCenter(toleranceRange)
            + "&ExactMassOffSet=" + (RangeUtils.rangeLength(toleranceRange) / 2);

    final URL queryURL = new URL(queryAddress);

    // Submit the query
    logger.finest("Searching LipidMaps via URL " + queryURL.toString());
    String queryResult = InetUtils.retrieveData(queryURL);

    Vector<String> results = new Vector<String>();

    String lines[] = queryResult.split("\n");
    for (String line : lines) {
      String fields[] = line.split("\t");
      if (fields.length < 3)
        continue;
      results.add(fields[0]);
    }

    return results.toArray(new String[0]);

  }

  /**
   * This method retrieves the details about the compound
   * 
   */
  public CompoundDBAnnotation getCompound(String ID, ParameterSet parameters) throws IOException {

    final URL entryURL = new URL(lipidMapsEntryAddress + ID);

    logger.finest("Loading data from LipidMaps via URL " + entryURL.toString());
    String lipidMapsEntry = InetUtils.retrieveData(entryURL);

    String fields[] = lipidMapsEntry.split("\t");

    if (fields.length < 4) {
      throw (new IOException("Could not parse compound " + ID));
    }

    // The columns are ID, common name, systematic name, formula, mass, etc.
    // Use common name by default for compoundName
    String compoundName = fields[1];

    // Those lipids which do not have a common name are represented by "-",
    // in that case we use the systematic name
    if (compoundName.equals("-"))
      compoundName = fields[2];

    String compoundFormula = fields[3];
    URL structure2DURL = new URL(lipidMapsStructureAddress + ID);
    URL structure3DURL = null;
    URL databaseURL = new URL(lipidMapsDetailsAddress + ID);

    CompoundDBAnnotation newCompound = new SimpleCompoundDBAnnotation(OnlineDatabases.LIPIDMAPS, ID, compoundName,
        compoundFormula, databaseURL, structure2DURL, structure3DURL);

    return newCompound;

  }
}
