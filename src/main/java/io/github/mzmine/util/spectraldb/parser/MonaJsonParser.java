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

package io.github.mzmine.util.spectraldb.parser;

import io.github.mzmine.datamodel.DataPoint;
import io.github.mzmine.datamodel.impl.SimpleDataPoint;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.util.spectraldb.entry.DBEntryField;
import io.github.mzmine.util.spectraldb.entry.SpectralDBEntry;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;

// top level json objects/arrays

/**
 * Mass bank of North America (MONA) json database files
 *
 * @author Robin Schmid
 */
public class MonaJsonParser extends SpectralDBParser {

  private static final String COMPOUND = "compound", MONA_ID = "id", META_DATA = "metaData",
      SPECTRUM = "spectrum", SPLASH = "splash", SUBMITTER = "submitter";

  private static Logger logger = Logger.getLogger(MonaJsonParser.class.getName());

  public MonaJsonParser(int bufferEntries, LibraryEntryProcessor processor) {
    super(bufferEntries, processor);
  }

  @Override
  public boolean parse(AbstractTask mainTask, File dataBaseFile) throws IOException {
    logger.info("Parsing MONA spectral json library " + dataBaseFile.getAbsolutePath());
    // create db
    int correct = 0;
    int error = 0;

    try (BufferedReader br = new BufferedReader(new FileReader(dataBaseFile))) {
      String l;
      do {
        // main task was canceled?
        if (mainTask != null && mainTask.isCanceled()) {
          return false;
        }
        l = br.readLine();
        if (l != null && l.length() > 2) {
          try (JsonReader reader = Json.createReader(new StringReader(l))) {
            JsonObject json = reader.readObject();
            SpectralDBEntry entry = getDBEntry(json);
            if (entry != null) {
              addLibraryEntry(entry);
              correct++;
            } else {
              error++;
            }
          } catch (Exception ex) {
            error++;
            logger.log(Level.WARNING, "Error for entry", ex);
          }
          if (error >= 4) {
            logger.log(Level.WARNING, "This file was no MONA spectral json library");
            return false;
          }
        }
      } while (l != null);
    }
    //
    finish();
    return true;
  }

  public SpectralDBEntry getDBEntry(JsonObject main) {
    // extract dps
    DataPoint[] dps = getDataPoints(main);
    if (dps == null || dps.length == 0) {
      return null;
    }
    // metadata
    Map<DBEntryField, Object> map = new EnumMap<>(DBEntryField.class);
    extractAllFields(main, map);
    return new SpectralDBEntry(map, dps);
  }

  private void extractAllFields(JsonObject main, Map<DBEntryField, Object> map) {
    for (DBEntryField f : DBEntryField.values()) {
      Object value = null;
      JsonValue j;

      switch (f) {
        case INCHI:
          value = readCompound(main, "inchi");
          if (value == null) {
            value = readCompoundMetaData(main, "InChI");
          }
          break;
        case INCHIKEY:
          value = readCompound(main, "inchiKey");
          if (value == null) {
            value = readCompoundMetaData(main, "InChIKey");
          }
          break;
        case ACQUISITION:
          break;
        case MONA_ID:
          value = readMetaData(main, "accession");
          break;
        case CAS:
          // TODO check real id (cas CAS ?)
          value = readCompoundMetaData(main, "cas");
          break;
        case CHARGE:
          break;
        case COLLISION_ENERGY:
          value = readMetaData(main, "collision energy");
          break;
        case COMMENT:
          break;
        case DATA_COLLECTOR:
          value = readMetaData(main, "author");
          break;
        case INSTRUMENT:
          value = readMetaData(main, "instrument");
          break;
        case INSTRUMENT_TYPE:
          value = readMetaData(main, "instrument type");
          break;
        case MS_LEVEL:
          value = readMetaData(main, "ms level");
          break;
        case RESOLUTION:
          value = readMetaData(main, "resolution");
          if (value != null) {
            value = value.toString();
          }
          break;
        case ION_TYPE:
          value = readMetaData(main, "precursor type");
          break;
        case ION_MODE:
          value = readMetaData(main, "ionization mode");
          break;
        case ION_SOURCE:
          value = readMetaData(main, "ionization");
          break;
        case EXACT_MASS:
          value = readMetaDataDouble(main, "exact mass");
          break;
        case MOLWEIGHT:
          value = readMetaDataDouble(main, "exact mass");
          break;
        case MZ:
          value = readMetaDataDouble(main, "precursor m/z");
          break;
        case NAME:
          // can have multiple names
          JsonArray names = main.getJsonArray(COMPOUND).getJsonObject(0).getJsonArray("names");
          value = names.stream().map(v -> v.asJsonObject()).map(v -> v.getString("name", null))
              .filter(Objects::nonNull).collect(Collectors.joining(", "));
          break;
        case NUM_PEAKS:
          break;
        case PRINCIPAL_INVESTIGATOR:
          value = readMetaData(main, "author");
          break;
        case CHEMSPIDER:
          j = readCompoundMetaDataJson(main, "chemspider");
          if (j != null) {
            if (j.getValueType().equals(ValueType.STRING)) {
              value = ((JsonString) j).getString();
            }
            if (j.getValueType().equals(ValueType.NUMBER)) {
              value = ((JsonNumber) j).intValue();
            }
          }
          break;
        case PUBCHEM:
          j = readCompoundMetaDataJson(main, "pubchem cid");
          if (j != null) {
            if (j.getValueType().equals(ValueType.STRING)) {
              value = ((JsonString) j).getString();
            }
            if (j.getValueType().equals(ValueType.NUMBER)) {
              value = ((JsonNumber) j).intValue();
            }
          }
          break;
        case FORMULA:
          value = readCompoundMetaData(main, "molecular formula");
          break;
        case PUBMED:
          break;
        case RT:
          Object tmp = readMetaData(main, "retention time");
          if (tmp != null) {
            if (tmp instanceof Number) {
              value = ((Number) tmp).doubleValue();
            } else {
              try {
                String v = (String) tmp;
                v = v.replaceAll(" ", "");
                // to minutes
                if (v.endsWith("sec")) {
                  v = v.substring(0, v.length() - 3);
                  value = Float.parseFloat(v) / 60f;
                } else {
                  value = Float.parseFloat(v);
                }
              } catch (Exception ex) {
              }
            }
          }
          break;
        case SMILES:
          value = readCompoundMetaData(main, "SMILES");
          break;
        case SOFTWARE:
          break;
        case SYNONYM:
          break;
        default:
          break;
      }

      if (value != null && value.equals("N/A")) {
        value = null;
      }
      // add value
      if (value != null) {
        // add
        map.put(f, value);
      }
    }
  }

  /**
   * read from META_DATA array
   *
   * @param main
   * @param id
   * @return String or Number or null
   */
  private Object readMetaData(JsonObject main, String id) {
    JsonValue j = main.getJsonArray(META_DATA).stream().map(v -> v.asJsonObject())
        .filter(v -> v.getString("name").equals(id)).map(v -> v.get("value")).findFirst()
        .orElse(null);

    if (j != null) {
      if (j.getValueType().equals(ValueType.STRING)) {
        return ((JsonString) j).getString();
      }
      if (j.getValueType().equals(ValueType.NUMBER)) {
        return ((JsonNumber) j).numberValue();
      }
    }
    return null;
  }

  private Double readMetaDataDouble(JsonObject main, String id) {
    return main.getJsonArray(META_DATA).stream().map(v -> v.asJsonObject())
        .filter(v -> v.getString("name").equals(id))
        .map(v -> v.getJsonNumber("value").doubleValue()).findFirst().orElse(null);
  }

  private JsonValue readCompoundMetaDataJson(JsonObject main, String id) {
    return main.getJsonArray(COMPOUND).getJsonObject(0).getJsonArray(META_DATA).stream()
        .map(v -> v.asJsonObject()).filter(v -> v.getString("name").equals(id))
        .map(v -> v.get("value")).findFirst().orElse(null);
  }

  /**
   * read from COMPOUND...META_DATA array
   *
   * @param main
   * @param id
   * @return
   */
  private String readCompoundMetaData(JsonObject main, String id) {
    return main.getJsonArray(COMPOUND).getJsonObject(0).getJsonArray(META_DATA).stream()
        .map(v -> v.asJsonObject()).filter(v -> v.getString("name").equals(id))
        .map(v -> v.getString("value")).findFirst().orElse(null);
  }

  /**
   * Read from COMPOUND object
   *
   * @param main
   * @param id
   * @return
   */
  private String readCompound(JsonObject main, String id) {
    return main.getJsonArray(COMPOUND).getJsonObject(0).getString(id, null);
  }

  public DataPoint[] getDataPoints(JsonObject main) {
    String spec = main.getString("spectrum");
    if (spec == null) {
      return null;
    }
    String[] data = spec.split(" ");
    DataPoint[] dps = new DataPoint[data.length];
    for (int i = 0; i < dps.length; i++) {
      String[] dp = data[i].split(":");
      double mz = Double.parseDouble(dp[0]);
      double intensity = Double.parseDouble(dp[1]);
      dps[i] = new SimpleDataPoint(mz, intensity);
    }
    return dps;
  }
}
