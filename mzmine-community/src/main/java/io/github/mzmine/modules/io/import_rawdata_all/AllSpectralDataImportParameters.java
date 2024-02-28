/*
 * Copyright (c) 2004-2024 The MZmine Development Team
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

package io.github.mzmine.modules.io.import_rawdata_all;

import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.modules.io.import_spectral_library.SpectralLibraryImportParameters;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.impl.SimpleParameterSet;
import io.github.mzmine.parameters.parametertypes.filenames.FileNamesParameter;
import io.github.mzmine.parameters.parametertypes.submodules.OptionalModuleParameter;
import io.github.mzmine.util.files.ExtensionFilters;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public class AllSpectralDataImportParameters extends SimpleParameterSet {


  public static final FileNamesParameter fileNames = new FileNamesParameter("File names", "",
      ExtensionFilters.MS_RAW_DATA);

  public static final OptionalModuleParameter<AdvancedSpectraImportParameters> advancedImport = new OptionalModuleParameter<>(
      "Advanced import",
      "Caution: Advanced option that applies mass detection (centroiding+thresholding) directly to imported scans (see help).\nAdvantage: Lower memory consumption\nCaution: All processing steps will directly change the underlying data, with no way of retrieving raw data or initial results.",
      new AdvancedSpectraImportParameters(), false);

  public AllSpectralDataImportParameters() {
    super(fileNames, //
        advancedImport, // directly process masslists
        // allow import of spectral libraries
        SpectralLibraryImportParameters.dataBaseFiles);
  }

  /**
   * @return true if parameterset is of this class or contains at least the parameters
   */
  public static boolean isParameterSetClass(final ParameterSet parameters) {
    return parameters != null && (
        parameters.getClass().equals(AllSpectralDataImportParameters.class) || (
            parameters.hasParameter(advancedImport) && parameters.hasParameter(fileNames)));
  }

  /**
   * Get all files in the project that match the file path
   *
   * @return a list of already loaded raw data files
   */
  public static List<RawDataFile> getLoadedRawDataFiles(MZmineProject project,
      final ParameterSet parameters) {
    // all files that should be loaded
    // need to validate bruker paths and use absolute file paths as they are used in RawDataFile
    Set<File> loadFileSet = streamValidatedFiles(parameters).map(File::getAbsoluteFile)
        .collect(Collectors.toSet());

    // the actual files in the list
    return project.getCurrentRawDataFiles().stream()
        .filter(raw -> loadFileSet.contains(raw.getAbsoluteFilePath())).toList();
  }

  /**
   * Removes already loaded files from the import list
   *
   * @return array of files - files that are already loaded
   */
  public static File[] skipAlreadyLoadedFiles(MZmineProject project,
      final ParameterSet parameters) {
    var loadedFiles = getLoadedRawDataFiles(project, parameters);
    var loadedAbsFiles = loadedFiles.stream().map(RawDataFile::getAbsoluteFilePath)
        .collect(Collectors.toSet());

    // compare based on absolute files
    // skip all files in import that directly match the abs path of another file
    return streamValidatedFiles(parameters).filter(
        file -> !loadedAbsFiles.contains(file.getAbsoluteFile())).toArray(File[]::new);
  }


  /**
   * Applies {@link AllSpectralDataImportModule#validateBrukerPath(File)} to get the actual file
   * paths. This is done always before import.
   *
   * @return stream of files
   */
  @NotNull
  public static Stream<File> streamValidatedFiles(final ParameterSet parameters) {
    return Arrays.stream(parameters.getValue(fileNames))
        .map(AllSpectralDataImportModule::validateBrukerPath);
  }
}
