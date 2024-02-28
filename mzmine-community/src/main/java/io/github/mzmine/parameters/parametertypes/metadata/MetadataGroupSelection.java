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

package io.github.mzmine.parameters.parametertypes.metadata;

import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.visualization.projectmetadata.table.MetadataTable;
import io.github.mzmine.modules.visualization.projectmetadata.table.columns.MetadataColumn;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record MetadataGroupSelection(@NotNull String columnName, @NotNull String groupStr) {

  public static MetadataGroupSelection NONE = new MetadataGroupSelection("", "");

  /**
   * @return Checks if the current metadata table contains the specified column and the specified
   * value. Case sensitive.
   */
  public boolean isValid() {
    if (columnName == null || columnName.isBlank() || groupStr == null
        || groupStr.isBlank()) {
      return false;
    }

    final MetadataTable metadata = MZmineCore.getProjectMetadata();

    final MetadataColumn<?> column = getColumn();
    if (column == null) {
      return false;
    }

    final Map<RawDataFile, Object> columnValues = metadata.getData().get(column);
    return columnValues.values().stream().map(Object::toString)
        .anyMatch(str -> str.equals(groupStr()));
  }

  /**
   * @return The actual column from the metadata table or null.
   */
  @Nullable
  public MetadataColumn<?> getColumn() {
    return MZmineCore.getProjectMetadata().getColumnByName(columnName());
  }

  /**
   * @return A list of files that match have the same value as {@link #groupStr()} in the specified
   * {@link #columnName} in the {@link MetadataTable}. Empty list if the column does not exist or it
   * does not contain the value.
   */
  public List<RawDataFile> getMatchingFiles() {
    if (!isValid()) {
      return List.of();
    }

    final Map<RawDataFile, Object> column = MZmineCore.getProjectMetadata().getData()
        .get(getColumn());
    return column.entrySet().stream()
        .filter(entry -> entry.getValue().toString().equals(groupStr())).map(Entry::getKey)
        .toList();
  }
}
