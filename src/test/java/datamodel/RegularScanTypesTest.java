/*
 * Copyright (c) 2004-2022 The MZmine Development Team
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package datamodel;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.IonizationType;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.MassSpectrumType;
import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.datamodel.features.types.MsMsInfoType;
import io.github.mzmine.datamodel.features.types.annotations.LipidMatchListType;
import io.github.mzmine.datamodel.features.types.annotations.SpectralLibraryMatchesType;
import io.github.mzmine.datamodel.features.types.numbers.BestScanNumberType;
import io.github.mzmine.datamodel.features.types.numbers.FragmentScanNumbersType;
import io.github.mzmine.datamodel.impl.DDAMsMsInfoImpl;
import io.github.mzmine.datamodel.impl.MSnInfoImpl;
import io.github.mzmine.datamodel.impl.SimpleScan;
import io.github.mzmine.datamodel.msms.ActivationMethod;
import io.github.mzmine.datamodel.msms.DDAMsMsInfo;
import io.github.mzmine.datamodel.msms.MsMsInfo;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.lipids.LipidClasses;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.lipids.MolecularSpeciesLevelAnnotation;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.lipids.SpeciesLevelAnnotation;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.lipidutils.LipidFactory;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.lipidutils.MatchedLipid;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.project.impl.MZmineProjectImpl;
import io.github.mzmine.project.impl.RawDataFileImpl;
import io.github.mzmine.util.scans.ScanUtils;
import io.github.mzmine.util.scans.similarity.HandleUnmatchedSignalOptions;
import io.github.mzmine.util.scans.similarity.SpectralSimilarity;
import io.github.mzmine.util.scans.similarity.Weights;
import io.github.mzmine.util.scans.similarity.impl.composite.CompositeCosineSpectralSimilarity;
import io.github.mzmine.util.scans.similarity.impl.composite.CompositeCosineSpectralSimilarityParameters;
import io.github.mzmine.util.spectraldb.entry.DBEntryField;
import io.github.mzmine.util.spectraldb.entry.SpectralDBAnnotation;
import io.github.mzmine.util.spectraldb.entry.SpectralLibraryEntry;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;


@RunWith(MockitoJUnitRunner.class)
@TestInstance(Lifecycle.PER_CLASS)
public class RegularScanTypesTest {

  RawDataFile file;
  ModularFeatureList flist;
  ModularFeatureListRow row;
  ModularFeature feature;
  List<Scan> scans;

  MZmineProject project;

  @BeforeAll
  void initialise() {
    file = new RawDataFileImpl("testfile", null, null, Color.BLACK);
    Assertions.assertNotNull(file);

    flist = new ModularFeatureList("flist", null, file);
    row = new ModularFeatureListRow(flist, 1);
    feature = new ModularFeature(flist, file, null, null);
    row.addFeature(file, feature);
    flist.addRow(row);

    // add MS1
    scans = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      scans.add(new SimpleScan(file, i, 1, 0.1f * i, null, new double[]{700, 800, 900, 1000, 1100},
          new double[]{1700, 1800, 1900, 11000, 11100}, MassSpectrumType.CENTROIDED,
          PolarityType.POSITIVE, "", Range.closed(0d, 1d)));
    }

    // add MS2
    for (int i = 5; i < 10; i++) {
      scans.add(new SimpleScan(file, i, 2, 0.1f * i,
          new DDAMsMsInfoImpl(300, 1, 20f, null, null, 2, ActivationMethod.UNKNOWN,
              Range.closed(299d, 301d)), new double[]{700, 800, 900, 1000, 1100},
          new double[]{1700, 1800, 1900, 11000, 11100}, MassSpectrumType.CENTROIDED,
          PolarityType.POSITIVE, "", Range.closed(0d, 1d)));
    }

    // add ms3 scan
    SimpleScan ms3Scan = new SimpleScan(file, file.getNumOfScans(), 3, 1000,
        new MSnInfoImpl(List.of((DDAMsMsInfo) scans.get(scans.size() - 1).getMsMsInfo())),
        new double[]{700, 800, 900, 1000, 1100}, new double[]{1700, 1800, 1900, 11000, 11100},
        MassSpectrumType.CENTROIDED, PolarityType.POSITIVE, "", Range.closed(0d, 1d));
    scans.add(ms3Scan);

    for (Scan scan : scans) {
      try {
        file.addScan(scan);
      } catch (IOException e) {
        e.printStackTrace();
        Assertions.fail("Cannot add scans to raw data file.");
      }
    }

    flist.setSelectedScans(file, scans);

    project = new MZmineProjectImpl();
    project.addFile(file);
    project.addFeatureList(flist);
  }

  @Test
  void testDDAMsMsInfo() {
    var type = new MsMsInfoType();

    final List<MsMsInfo> msMsInfos = List.of(
        new DDAMsMsInfoImpl(550, 1, 30f, null, null, 2, ActivationMethod.HCD,
            Range.closed(500d, 600d)),
        new DDAMsMsInfoImpl(550, null, null, file.getScan(7), null, 2, ActivationMethod.UNKNOWN,
            Range.closed(500d, 600d)));

    Assertions.assertTrue(msMsInfos.size() > 0);

    DataTypeTestUtils.testSaveLoad(type, msMsInfos, project, flist, row, feature, file);
    DataTypeTestUtils.testSaveLoad(type, msMsInfos, project, flist, row, null, null);
    DataTypeTestUtils.testSaveLoad(type, null, project, flist, row, feature, file);
    DataTypeTestUtils.testSaveLoad(type, null, project, flist, row, null, null);

    final RawDataFile file2 = new RawDataFileImpl("file2", null, null, Color.BLACK);
    final MZmineProject newProject = new MZmineProjectImpl();
    newProject.addFile(file);
    newProject.addFile(file2);
    DataTypeTestUtils.testSaveLoad(type, msMsInfos, newProject, flist, row, feature, file2);
    DataTypeTestUtils.testSaveLoad(type, msMsInfos, newProject, flist, row, null, null);
    DataTypeTestUtils.testSaveLoad(type, null, newProject, flist, row, feature, file2);
    DataTypeTestUtils.testSaveLoad(type, null, newProject, flist, row, null, null);
  }

  @Test
  void testMSnInfo() {
    MsMsInfoType type = new MsMsInfoType();

    final DDAMsMsInfoImpl ms2Info = new DDAMsMsInfoImpl(550, 1, 30f, null, null, 2,
        ActivationMethod.HCD, Range.closed(500d, 600d));
    final DDAMsMsInfoImpl ms3Info = new DDAMsMsInfoImpl(550, 1, 30f, null, null, 3,
        ActivationMethod.HCD, Range.closed(500d, 600d));

    MSnInfoImpl msnInfo = new MSnInfoImpl(List.of(ms2Info, ms3Info));

    DataTypeTestUtils.simpleDataTypeSaveLoadTest(type, List.of(msnInfo));
  }


  @Test
  void bestScanNumberTypeTest() {
    BestScanNumberType type = new BestScanNumberType();
    Scan value = file.getScan(3);
    DataTypeTestUtils.testSaveLoad(type, value, project, flist, row, null, null);
    DataTypeTestUtils.testSaveLoad(type, value, project, flist, row, feature, file);

    DataTypeTestUtils.testSaveLoad(type, null, project, flist, row, null, null);
    DataTypeTestUtils.testSaveLoad(type, null, project, flist, row, feature, file);
  }

  @Test
  void fragmentScanNumbersTypeTest() {
    FragmentScanNumbersType type = new FragmentScanNumbersType();
    List<Scan> value = new ArrayList<>(scans.subList(6, 9));
    DataTypeTestUtils.testSaveLoad(type, value, project, flist, row, null, null);
    DataTypeTestUtils.testSaveLoad(type, value, project, flist, row, feature, file);

    DataTypeTestUtils.testSaveLoad(type, null, project, flist, row, null, null);
    DataTypeTestUtils.testSaveLoad(type, null, project, flist, row, feature, file);
  }

  @Test
  void spectralLibMatchSummaryTypeTest() {
    SpectralLibraryMatchesType type = new SpectralLibraryMatchesType();

    var param = new CompositeCosineSpectralSimilarityParameters().cloneParameterSet();
    param.setParameter(CompositeCosineSpectralSimilarityParameters.minCosine, 0.7d);
    param.setParameter(CompositeCosineSpectralSimilarityParameters.handleUnmatched,
        HandleUnmatchedSignalOptions.REMOVE_ALL);
    param.setParameter(CompositeCosineSpectralSimilarityParameters.weight, Weights.MASSBANK);
    CompositeCosineSpectralSimilarity simFunc = new CompositeCosineSpectralSimilarity();

    Scan query = file.getScan(6);
    Scan library = file.getScan(7);

    Map<DBEntryField, Object> map = Map.of(DBEntryField.ENTRY_ID, "123swd", DBEntryField.CAS,
        "468-531-21", DBEntryField.DATA_COLLECTOR, "Dr. Xy", DBEntryField.CHARGE, 1);

    SpectralLibraryEntry entry = SpectralLibraryEntry.create(null, map,
        ScanUtils.extractDataPoints(library));

    SpectralSimilarity similarity = simFunc.getSimilarity(param, new MZTolerance(0.005, 15), 0,
        ScanUtils.extractDataPoints(library), ScanUtils.extractDataPoints(query));

    List<SpectralDBAnnotation> value = List.of(
        new SpectralDBAnnotation(entry, similarity, query, null),
        new SpectralDBAnnotation(entry, similarity, query, 0.043f));

    DataTypeTestUtils.testSaveLoad(type, value, project, flist, row, null, null);
    DataTypeTestUtils.testSaveLoad(type, Collections.emptyList(), project, flist, row, null, null);
    DataTypeTestUtils.testSaveLoad(type, value, project, flist, row, feature, file);
    DataTypeTestUtils.testSaveLoad(type, Collections.emptyList(), project, flist, row, feature,
        file);
  }

  @Test
  void lipidAnnotationSummaryTypeTest() {
    var type = new LipidMatchListType();

    LipidFactory lipidFactory = new LipidFactory();
    SpeciesLevelAnnotation speciesLevelAnnotation = lipidFactory.buildSpeciesLevelLipid(
        LipidClasses.DIACYLGLYCEROPHOSPHATES, 36, 2);

    MolecularSpeciesLevelAnnotation molecularSpeciesLevelAnnotation = lipidFactory.buildMolecularSpeciesLevelLipid(
        LipidClasses.DIACYLGLYCEROPHOSPHOCHOLINES, new int[]{12, 14}, new int[]{0, 2});

    List<MatchedLipid> value = new ArrayList<>();

    value.add(new MatchedLipid(speciesLevelAnnotation, 785.59346 + 1.003,
        IonizationType.POSITIVE_HYDROGEN, new HashSet<>(), 0.0d));

    value.add(new MatchedLipid(molecularSpeciesLevelAnnotation, 785.59346 + 1.003,
        IonizationType.POSITIVE_HYDROGEN, new HashSet<>(), 0.0d));

    List<MatchedLipid> loaded = (List<MatchedLipid>) DataTypeTestUtils.saveAndLoad(type, value,
        project, flist, row, null, null);

    Assertions.assertEquals(value.size(), loaded.size());
    final MatchedLipid first = value.get(0);
    final MatchedLipid firstLoaded = loaded.get(0);
    Assertions.assertEquals(first.getIonizationType(), firstLoaded.getIonizationType());
    Assertions.assertEquals(first.getMsMsScore(), firstLoaded.getMsMsScore());
    Assertions.assertEquals(first.getAccurateMz(), firstLoaded.getAccurateMz());

    final MatchedLipid second = value.get(1);
    final MatchedLipid secondLoaded = loaded.get(1);
    Assertions.assertEquals(second.getIonizationType(), secondLoaded.getIonizationType());
    Assertions.assertEquals(second.getMsMsScore(), secondLoaded.getMsMsScore());
    Assertions.assertEquals(second.getAccurateMz(), secondLoaded.getAccurateMz());
  }
}
