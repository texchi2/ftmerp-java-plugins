/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ofbiz.ftm.garments.sku

import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet

/**
 * One sheet's table as found by WorkbookReader: a header row and the columns it names. Values are addressed by
 * header text only. Row numbers reported are the ones Excel shows (1-based), so a person can find the cell.
 */
final class SheetTable {

    final int headerRow
    /** normalised header -> column index */
    final Map<String, Integer> columns
    /** normalised header -> header as written in the sheet */
    final Map<String, String> written

    private final WorkbookReader reader
    private final Sheet sheet

    SheetTable(WorkbookReader reader, Sheet sheet, int headerRow, Map<String, Integer> columns, Map<String, String> written) {
        this.reader = reader
        this.sheet = sheet
        this.headerRow = headerRow
        this.columns = columns
        this.written = written
    }

    String getSheetName() {
        return sheet.sheetName
    }

    /** The headers of this row as written, in column order. */
    List<String> headersAsWritten() {
        return columns.sort { Map.Entry<String, Integer> a, Map.Entry<String, Integer> b -> a.value <=> b.value }
                .collect { String key, Integer col -> written[key] }
    }

    boolean hasColumn(String header) {
        return columns.containsKey(WorkbookReader.normalise(header))
    }

    /** Non-blank values of one column below the header row, each as [row: Excel row number, text: cell text]. */
    List<Map> column(String header) {
        Integer col = columns[WorkbookReader.normalise(header)]
        List<Map> out = []
        if (col == null) {
            return out
        }
        for (int r = headerRow + 1; r <= sheet.lastRowNum; r++) {
            Row row = sheet.getRow(r)
            String text = row == null ? '' : reader.cellText(row.getCell(col))
            if (text) {
                out << [row: r + 1, text: text]
            }
        }
        return out
    }

    /**
     * Rows below the header as header (as given) -> text, plus '_row' (Excel row number); the list stops at the
     * first row that is blank in every given column.
     */
    List<Map<String, String>> rows(Collection<String> headers) {
        Map<String, Integer> cols = headers.collectEntries { String h -> [(h): columns[WorkbookReader.normalise(h)]] }
        List<Map<String, String>> out = []
        for (int r = headerRow + 1; r <= sheet.lastRowNum; r++) {
            Row row = sheet.getRow(r)
            Map<String, String> values = cols.collectEntries { String h, Integer col ->
                [(h): (row == null || col == null) ? '' : reader.cellText(row.getCell(col))]
            }
            if (values.values().every { String v -> !v }) {
                break
            }
            values['_row'] = String.valueOf(r + 1)
            out << values
        }
        return out
    }

}
