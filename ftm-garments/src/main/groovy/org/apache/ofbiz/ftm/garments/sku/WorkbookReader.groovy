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

import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory

/**
 * Reads an .xlsx by HEADER NAMES, never by column position (DESIGN_sku_workbook_import_2026-10-22 §5).
 * A sheet's table starts at the first row that carries every required header; columns are resolved from that
 * row's text only. Cell text is what a person sees: numbers through POI's DataFormatter (7.5 stays 7.5, 0042 stays
 * 0042 - the 07-06 audit's truncation), formula cells by their cached result, error cells as empty text.
 */
final class WorkbookReader implements Closeable {

    /** Rows scanned from the top of a sheet for the header row (the owner's sheets carry instructions above it). */
    static final int HEADER_SCAN_ROWS = 60

    private final Workbook workbook
    private final DataFormatter formatter = new DataFormatter(Locale.ROOT)

    WorkbookReader(byte[] bytes) {
        workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))
    }

    /** Header matching: whitespace collapsed, trimmed, case folded. Never a column index. */
    static String normalise(String text) {
        return text == null ? '' : text.replaceAll(/\s+/, ' ').trim().toLowerCase(Locale.ROOT)
    }

    List<String> sheetNames() {
        return (0..<workbook.numberOfSheets).collect { int i -> workbook.getSheetName(i) }
    }

    /** The workbook's own name of the sheet matching the given name (normalised), or null. */
    String findSheet(String name) {
        String wanted = normalise(name)
        return sheetNames().find { String n -> normalise(n) == wanted }
    }

    /** Rows POI reports for a sheet; 0 for a missing sheet. */
    int rowCount(String sheetName) {
        Sheet sheet = workbook.getSheet(sheetName)
        return sheet == null ? 0 : sheet.physicalNumberOfRows
    }

    String cellText(Cell cell) {
        if (cell == null) {
            return ''
        }
        boolean formula = cell.cellType == CellType.FORMULA
        CellType type = formula ? cell.cachedFormulaResultType : cell.cellType
        switch (type) {
            case CellType.STRING:
                return cell.stringCellValue.trim()
            case CellType.NUMERIC:
                return formula
                        ? formatter.formatRawCellContents(cell.numericCellValue, cell.cellStyle.dataFormat, cell.cellStyle.dataFormatString).trim()
                        : formatter.formatCellValue(cell).trim()
            case CellType.BOOLEAN:
                return cell.booleanCellValue ? 'TRUE' : 'FALSE'
            default:
                return ''
        }
    }

    /**
     * The table under the first row (within HEADER_SCAN_ROWS) carrying every required header. Headers are resolved by
     * their text and never by picking one of several silently: with an anchor header (an item list's code column) every
     * required header is taken from the contiguous run of header cells that holds the anchor; a header that repeats where
     * it must be unique is reported as a problem instead (G2 2026-10-22: the 2024 list repeats its attribute names in a
     * vocabulary block and in the generator block of the same row, and the first occurrence was the wrong one).
     */
    TableLookup locate(String sheetName, Collection<String> requiredHeaders, String anchorHeader = null) {
        Sheet sheet = workbook.getSheet(sheetName)
        if (sheet == null) {
            return new TableLookup(null, null)
        }
        List<String> wanted = requiredHeaders.collect { String h -> normalise(h) }.unique()
        int last = Math.min(sheet.lastRowNum, sheet.firstRowNum + HEADER_SCAN_ROWS)
        for (int r = sheet.firstRowNum; r <= last; r++) {
            Row row = sheet.getRow(r)
            Map<String, List<Integer>> found = row == null ? [:] : occurrencesOf(row)
            if (found.keySet().containsAll(wanted)) {
                return resolve(sheet, row, found, wanted, anchorHeader == null ? null : normalise(anchorHeader))
            }
        }
        return new TableLookup(null, null)
    }

    @Override
    void close() {
        workbook.close()
    }

    /** The required headers of a header row as columns: unique in the row, or unique inside the anchor's block. */
    private TableLookup resolve(Sheet sheet, Row row, Map<String, List<Integer>> found, List<String> wanted, String anchor) {
        Map<String, String> written = writtenOf(row)
        Map<String, Integer> columns = found.collectEntries { String key, List<Integer> cols -> [(key): cols[0]] }
        int excelRow = row.rowNum + 1
        Range<Integer> block = null
        if (anchor != null) {
            List<Integer> anchors = found[anchor] ?: []
            if (anchors.size() != 1) {
                String name = written[anchor] ?: anchor
                return new TableLookup(null, "the code header '${name}' appears ${anchors.size()} times in row ${excelRow}; it must be unique")
            }
            block = blockAround(row, anchors[0])
        }
        String where = anchor == null ? "in row ${excelRow}" : "inside the block of columns holding '${written[anchor]}' in row ${excelRow}"
        List<String> problems = []
        wanted.each { String key ->
            List<Integer> cols = block == null ? found[key] : found[key].findAll { Integer c -> c in block }
            if (cols.size() == 1) {
                columns[key] = cols[0]
            } else if (cols.empty) {
                problems << "'${written[key]}' is not ${where}"
            } else {
                problems << "'${written[key]}' appears ${cols.size()} times ${where}"
            }
        }
        if (problems) {
            return new TableLookup(null, problems.join('; ') + ' - a header must name exactly one column')
        }
        return new TableLookup(new SheetTable(this, sheet, row.rowNum, columns, written), null)
    }

    /** The contiguous run of non-blank header cells around a column (the owner's tables are ListObjects without gaps). */
    private Range<Integer> blockAround(Row row, int col) {
        int lo = col
        while (lo > 0 && normalise(cellText(row.getCell(lo - 1)))) {
            lo--
        }
        int hi = col
        while (normalise(cellText(row.getCell(hi + 1)))) {
            hi++
        }
        return lo..hi
    }

    /** A row's non-blank cells as normalised header -> every column index carrying it, in column order. */
    private Map<String, List<Integer>> occurrencesOf(Row row) {
        Map<String, List<Integer>> found = [:]
        for (Cell cell : row) {
            String key = normalise(cellText(cell))
            if (key) {
                found.computeIfAbsent(key) { String k -> [] } << cell.columnIndex
            }
        }
        return found
    }

    /** A row's non-blank cells as normalised header -> header as written; the first cell carrying a header wins. */
    private Map<String, String> writtenOf(Row row) {
        Map<String, String> written = [:]
        for (Cell cell : row) {
            String text = cellText(cell)
            String key = normalise(text)
            if (key && !written.containsKey(key)) {
                written[key] = text
            }
        }
        return written
    }

}
