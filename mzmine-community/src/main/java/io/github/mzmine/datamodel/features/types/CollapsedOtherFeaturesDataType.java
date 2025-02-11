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

package io.github.mzmine.datamodel.features.types;

import io.github.mzmine.datamodel.IMSRawDataFile;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.featuredata.IonTimeSeries;
import io.github.mzmine.datamodel.featuredata.impl.AverageMzIonTimeSeries;
import io.github.mzmine.datamodel.featuredata.impl.IonMobilogramTimeSeriesFactory;
import io.github.mzmine.datamodel.featuredata.impl.SimpleIonMobilogramTimeSeries;
import io.github.mzmine.datamodel.featuredata.impl.SimpleIonTimeSeries;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.datamodel.features.RowBinding;
import io.github.mzmine.datamodel.features.types.modifiers.NoTextColumn;
import io.github.mzmine.datamodel.features.types.modifiers.NullColumnType;
import io.github.mzmine.datamodel.features.types.numbers.abstr.ListDataType;
import io.github.mzmine.modules.dataprocessing.featdet_spectraldeconvolutiongc.SpectralDeconvolutionGCModule;
import io.github.mzmine.modules.io.projectload.version_3_0.CONST;
import java.util.List;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * GC-EI uses {@link SpectralDeconvolutionGCModule} to collapse multiple signals with similar shape
 * to a pseudo spectrum. This data type contains all chromatographic shapes of all features. Even
 * the one of the main feature that was selected to represent the feature.
 * <p>
 * This can be useful to change to a common feature during alignment. Or for visualization. All
 * feature data is memory mapped either way so there should not be a large overhead.
 */
public class CollapsedOtherFeaturesDataType extends ListDataType<AverageMzIonTimeSeries> implements
    NoTextColumn, NullColumnType {

  private static final String ATTR_MZ = "mz";
  private static final String ATTR_HEIGHT = "height";

  @NotNull
  @Override
  public final String getUniqueID() {
    // Never change the ID for compatibility during saving/loading of type
    return "collapsed_features_data";
  }

  @NotNull
  @Override
  public String getHeaderString() {
    return "Collapsed features";
  }

  @NotNull
  @Override
  public List<RowBinding> createDefaultRowBindings() {
    // listen to changes in DataPointsType for all ModularFeatures
    return List.of();
  }

  @Override
  public void saveToXML(@NotNull final XMLStreamWriter writer, @Nullable final Object value,
      @NotNull final ModularFeatureList flist, @NotNull final ModularFeatureListRow row,
      @Nullable final ModularFeature feature, @Nullable final RawDataFile file)
      throws XMLStreamException {
    if (value == null) {
      return;
    }
    if (!(value instanceof AverageMzIonTimeSeries(
        IonTimeSeries series, double mz, Float height
    ))) {
      throw new IllegalArgumentException(
          "Wrong value type for data type: " + this.getClass().getName() + " value class: "
          + value.getClass());
    }
    if (file == null) {
      throw new IllegalArgumentException("Cannot save collapsed features data for file = null");
    }
    final List<? extends Scan> selectedScans = flist.getSeletedScans(file);
    if (selectedScans == null) {
      // sanity check during saving.
      throw new IllegalArgumentException("Cannot find selected scans.");
    }

    writer.writeStartElement(getUniqueID());
    writer.writeAttribute(ATTR_MZ, String.valueOf(mz));
    if (height != null) {
      writer.writeAttribute(ATTR_HEIGHT, String.valueOf(height));
    }
    series.saveValueToXML(writer, file.getScans()); // use ALL scans of the given raw data file.
    writer.writeEndElement();
  }

  @Override
  public Object loadFromXML(@NotNull final XMLStreamReader reader, @NotNull MZmineProject project,
      @NotNull final ModularFeatureList flist, @NotNull final ModularFeatureListRow row,
      @Nullable final ModularFeature feature, @Nullable final RawDataFile file)
      throws XMLStreamException {

    assert file != null;

    double mz = 0d;
    Float height = null;

    while (reader.hasNext()) {
      if (reader.isEndElement() && reader.getLocalName().equals(CONST.XML_DATA_TYPE_ELEMENT)) {
        // nothing saved
        return null;
      }
      if (reader.isStartElement()) {
        if (getUniqueID().equals(reader.getLocalName())) {
          mz = Double.parseDouble(reader.getAttributeValue(null, ATTR_MZ));
          String heightStr = reader.getAttributeValue(null, ATTR_HEIGHT);
          if (heightStr != null) {
            height = Float.parseFloat(heightStr);
          }
        }

        if (reader.getLocalName().equals(SimpleIonTimeSeries.XML_ELEMENT) || reader.getLocalName()
            .equals(SimpleIonMobilogramTimeSeries.XML_ELEMENT)) {
          IonTimeSeries<?> series = switch (reader.getLocalName()) {
            case SimpleIonTimeSeries.XML_ELEMENT ->
                SimpleIonTimeSeries.loadFromXML(reader, flist.getMemoryMapStorage(), file);

            case SimpleIonMobilogramTimeSeries.XML_ELEMENT ->
                IonMobilogramTimeSeriesFactory.loadFromXML(reader, flist.getMemoryMapStorage(),
                    (IMSRawDataFile) file);
            default -> null;
          };
          // create new series
          return new AverageMzIonTimeSeries(series, mz, height);
        }
      }
      reader.next();
    }
    return null;
  }
}
