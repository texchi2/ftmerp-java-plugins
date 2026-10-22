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
package org.apache.ofbiz.ftm.garments

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.sql.Timestamp

import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.ftm.garments.sku.SheetTable
import org.apache.ofbiz.ftm.garments.sku.SkuRefusedException
import org.apache.ofbiz.ftm.garments.sku.SkuComposer
import org.apache.ofbiz.ftm.garments.sku.TableLookup
import org.apache.ofbiz.ftm.garments.sku.WorkbookReader
import org.apache.ofbiz.service.ServiceUtil

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.Field

/*
 * SKU workbook import services (DESIGN_sku_workbook_import_2026-10-22). The master is OFBiz; a workbook is a proposal.
 *   previewSkuWorkbook      store the file, classify every difference into exactly one change row, fingerprint the list
 *   commitSkuWorkbookImport apply the ticked rows in ONE transaction, one audit row per change; refuse a stale preview
 *   markSkuImportStatus     record a refusal or a rollback in its own transaction
 * Columns are found by header text through WorkbookReader; the sheet map (FtmSkuSheetMap / FtmSkuSheetColumn) and the
 * rule segments (FtmSkuRuleSegment.inputName) say which headers matter. Methods take no parameter: OFBiz's GroovyEngine
 * invokes them with the IN map as the script binding `parameters` (bug-1769).
 */

/** Largest workbook accepted, in bytes (chosen, design §9). Checked before anything is stored. */
@Field static final long MAX_FILE_BYTES = 8L * 1024 * 1024
@Field static final String STATUS_PREVIEWED = 'FTMSKU_PREVIEWED'
@Field static final String STATUS_COMMITTED = 'FTMSKU_COMMITTED'
@Field static final List<String> KIND_ORDER = ['VOCAB_NEW', 'VOCAB_CODE_DIFFERS', 'ITEM_NEW']

String sha256(byte[] bytes) {
    return MessageDigest.getInstance('SHA-256').digest(bytes).encodeHex().toString()
}

String sha256(String text) {
    return sha256(text.getBytes('UTF-8'))
}

/** One difference. Every row of the preview is built here, so every row carries the same fields. */
Map change(String kindId, String severityId, Map fields) {
    return [kindId: kindId, severityId: severityId, applyDefault: severityId == 'APPLICABLE' ? (fields.applyDefault ?: 'Y') : null] + fields
}

/**
 * The fingerprint of a difference list: what each row means, not where it sits. A row number is left out (a moved row is
 * the same difference); a minted code, a master value and an item's inputs are in (the master moving changes them).
 */
String fingerprint(List<Map> changes) {
    List<String> lines = changes.collect { Map c ->
        [c.kindId, c.sheetName, c.header, c.keyValue, c.oldValue, c.newValue, c.pkText, c.payload]
                .collect { Object v -> v == null ? '' : v as String }.join('|')
    }
    return sha256(lines.sort().join('\n'))
}

/** Next code and sequence for a feature type whose codes are numbers of one width; null when it cannot mint. */
class CodeMint {

    private final int width
    private int last
    private long lastSeq
    private final boolean numeric

    CodeMint(List<GenericValue> features) {
        List<String> codes = features*.idCode.findAll { Object c -> c } as List<String>
        numeric = codes && codes.every { String c -> c ==~ /\d+/ }
        width = numeric ? codes*.length().max() : 0
        last = numeric ? codes*.toInteger().max() : 0
        lastSeq = (features*.defaultSequenceNum.findAll { Object s -> s != null }*.longValue().max() ?: 0L) as long
    }

    boolean canMint() {
        return numeric
    }

    String next() {
        last++
        lastSeq++
        return String.valueOf(last).padLeft(width, '0')
    }

    long getNextSeq() {
        return lastSeq
    }

}

/** Column cells as row -> text. */
Map<Integer, String> byRow(List<Map> cells) {
    return cells.collectEntries { Map c -> [(c.row as Integer): c.text as String] }
}

/** Every difference between the workbook and the master, sheet by sheet. Reads the master; writes nothing. */
List<Map> classify(byte[] bytes) {
    List<Map> out = []
    List<GenericValue> maps = from('FtmSkuSheetMap').queryList()
    Map<String, GenericValue> byName = maps.collectEntries { GenericValue m -> [(WorkbookReader.normalise(m.sheetName as String)): m] }
    new WorkbookReader(bytes).withCloseable { WorkbookReader reader ->
        reader.sheetNames().each { String sheetName ->
            GenericValue map = byName[WorkbookReader.normalise(sheetName)]
            if (!map) {
                out << change('SHEET_UNMAPPED', 'INFO', [sheetName: sheetName,
                        reason: "no sheet map: ${reader.rowCount(sheetName)} rows not read (for ruling)"])
            } else if (map.mapKindId == 'VOCAB') {
                classifyVocab(reader, sheetName, map, out)
            } else {
                classifyItems(reader, sheetName, map, out)
            }
        }
    }
    return out
}

void classifyVocab(WorkbookReader reader, String sheetName, GenericValue map, List<Map> out) {
    List<GenericValue> cols = from('FtmSkuSheetColumn').where('sheetMapId', map.sheetMapId).orderBy('columnSeq').queryList()
    Set<String> mapped = [] as Set
    SheetTable first = null
    for (GenericValue col in cols) {
        List<String> required = [col.keyHeader, col.codeHeader, col.nameHeader].findAll { Object h -> h } as List<String>
        mapped.addAll(required.collect { String h -> WorkbookReader.normalise(h) })
        TableLookup lookup = reader.locate(sheetName, required)
        if (lookup.problem) {
            out << change('HEADER_AMBIGUOUS', 'ERROR', [sheetName: sheetName, header: required.join(' | '),
                    productFeatureTypeId: col.productFeatureTypeId, reason: lookup.problem])
            continue
        }
        SheetTable table = lookup.table
        if (table == null) {
            out << change('HEADER_MISSING', 'ERROR', [sheetName: sheetName, header: required.join(' | '),
                    productFeatureTypeId: col.productFeatureTypeId,
                    reason: "no row within the first ${WorkbookReader.HEADER_SCAN_ROWS} carries all of: ${required.join(', ')}"])
            continue
        }
        first = first ?: table
        classifyVocabColumn(table, col, sheetName, out)
    }
    if (first != null) {
        first.headersAsWritten().each { String header ->
            if (!(WorkbookReader.normalise(header) in mapped)) {
                out << change('COLUMN_UNMAPPED', 'INFO', [sheetName: sheetName, header: header,
                        reason: "${first.column(header).size()} values not read (no column map; for ruling)"])
            }
        }
    }
}

void classifyVocabColumn(SheetTable table, GenericValue col, String sheetName, List<Map> out) {
    String typeId = col.productFeatureTypeId
    // no cache: the preview must see the master as it is now
    List<GenericValue> features = from('ProductFeature').where('productFeatureTypeId', typeId).orderBy('defaultSequenceNum').queryList()
    Map<Integer, String> codes = col.codeHeader ? byRow(table.column(col.codeHeader as String)) : [:]
    Map<Integer, String> names = col.nameHeader ? byRow(table.column(col.nameHeader as String)) : [:]
    boolean compare = col.codeHeader && col.compareCode == 'Y'
    Map<String, Map> seen = [:]
    Set<String> matched = [] as Set
    CodeMint mint = new CodeMint(features)
    Map common = [sheetName: sheetName, header: col.keyHeader, productFeatureTypeId: typeId, skuRuleId: null]
    for (Map cell in table.column(col.keyHeader as String)) {
        String key = cell.text as String
        Integer row = cell.row as Integer
        String code = codes[row]
        String name = names[row]
        String nkey = key.toLowerCase(Locale.ROOT)
        Map prior = seen[nkey]
        if (prior != null) {
            if (col.codeHeader && prior.code != code) {
                out << change('VOCAB_DUPLICATE_KEY', 'ERROR', common + [rowNum: row, keyValue: key, oldValue: prior.code, newValue: code,
                        reason: "the key also appears in row ${prior.row} with code ${prior.code}"])
            }
            continue
        }
        seen[nkey] = [code: code, row: row]
        GenericValue feature = features.find { GenericValue f -> key.equalsIgnoreCase(f.description as String) }
        if (feature != null) {
            matched << (feature.productFeatureId as String)
            if (compare && code && code != (feature.idCode as String)) {
                out << change('VOCAB_CODE_DIFFERS', 'APPLICABLE', common + [rowNum: row, keyValue: key, displayName: name ?: feature.description,
                        entityName: 'ProductFeature', pkText: feature.productFeatureId, fieldName: 'idCode',
                        oldValue: feature.idCode, newValue: code, applyDefault: 'N',
                        reason: 'the workbook code differs from the master code; a change touches every future SKU of this type'])
            }
            continue
        }
        if (!mint.canMint()) {
            out << change('VOCAB_NEW', 'ERROR', common + [rowNum: row, keyValue: key, displayName: name ?: key, newValue: code,
                    reason: "new value, but the master cannot mint a code for type ${typeId}: its codes are not all numbers"])
            continue
        }
        String minted = mint.next()
        out << change('VOCAB_NEW', 'APPLICABLE', common + [rowNum: row, keyValue: key, displayName: name ?: key,
                entityName: 'ProductFeature', fieldName: 'idCode', oldValue: null, newValue: minted, sequenceNum: mint.nextSeq,
                reason: code ? "workbook code ${code}; the master mints ${minted}" : "the master mints ${minted}"])
    }
    features.findAll { GenericValue f -> !((f.productFeatureId as String) in matched) }.each { GenericValue f ->
        out << change('VOCAB_MASTER_ONLY', 'INFO', common + [keyValue: f.description, oldValue: f.idCode, entityName: 'ProductFeature',
                pkText: f.productFeatureId, reason: 'in the master, not in the sheet; nothing changes'])
    }
}

void classifyItems(WorkbookReader reader, String sheetName, GenericValue map, List<Map> out) {
    String ruleId = map.skuRuleId
    List<GenericValue> segs = from('FtmSkuRuleSegment').where('skuRuleId', ruleId).orderBy('sequenceNum').queryList()
    List<String> inputs = segs.findAll { GenericValue s -> s.segmentTypeId != 'LITERAL' && s.inputName }*.inputName as List<String>
    String codeHeader = map.codeHeader
    List<String> required = inputs + [codeHeader]
    Map common = [sheetName: sheetName, skuRuleId: ruleId, header: codeHeader]
    TableLookup lookup = reader.locate(sheetName, required, codeHeader)
    if (lookup.problem) {
        out << change('HEADER_AMBIGUOUS', 'ERROR', common + [header: required.join(' | '), reason: lookup.problem])
        return
    }
    SheetTable table = lookup.table
    if (table == null) {
        out << change('HEADER_MISSING', 'ERROR', common + [header: required.join(' | '),
                reason: "no row within the first ${WorkbookReader.HEADER_SCAN_ROWS} carries all of: ${required.join(', ')}"])
        return
    }
    Map<String, String> ops = valueOpsOf(map, common, out)
    if (ops == null) {
        return
    }
    List<Map<String, String>> rows = table.rows(required)
    List<Map> importRows = rows.collect { Map<String, String> r ->
        [ref: "${sheetName}!${r._row}".toString(), skuRuleId: ruleId,
         values: inputs.collectEntries { String h -> [(h): cooked(r[h], ops[WorkbookReader.normalise(h)])] },
         workbookCode: r[codeHeader] ?: null, row: r._row as Integer]
    }
    if (!importRows) {
        out << change('ITEM_SAME', 'INFO', common + [reason: 'the item list is empty', newValue: '0'])
        return
    }
    Map res = dispatcher.runSync('importSkuRows', [rows: importRows.collect { Map r -> r.subMap(['ref', 'skuRuleId', 'values', 'workbookCode']) },
                                                   dryRun: true, userLogin: parameters.userLogin])
    if (ServiceUtil.isError(res)) {
        out << change('ITEMS_UNREADABLE', 'ERROR', common + [reason: ServiceUtil.getErrorMessage(res)])
        return
    }
    Map<String, Map> byRef = importRows.collectEntries { Map r -> [(r.ref): r] }
    res.loaded.each { Map r ->
        Map row = byRef[r.ref]
        out << change('ITEM_NEW', 'APPLICABLE', common + [rowNum: row.row, keyValue: row.workbookCode, newValue: r.sku, entityName: 'Product',
                fieldName: 'SKU', displayName: row.values.values().join(' / '), payload: JsonOutput.toJson(row.values),
                reason: 'the rule reproduces the workbook code and no product holds it'])
    }
    if (res.alreadyPresent) {
        out << change('ITEM_SAME', 'INFO', common + [newValue: res.alreadyPresent.size() as String,
                reason: "${res.alreadyPresent.size()} rows already in the master with the same attributes"])
    }
    res.disagreeing.each { Map r ->
        Map row = byRef[r.ref]
        out << change('ITEM_DISAGREES', 'WARNING', common + [rowNum: row.row, keyValue: row.workbookCode, oldValue: r.workbookCode,
                newValue: r.ruleCode, displayName: row.values.values().join(' / '), reason: r.reason])
    }
    res.refusedAsWorkbook.each { Map r ->
        Map row = byRef[r.ref]
        out << change('ITEM_REFUSED', 'WARNING', common + [rowNum: row.row, displayName: row.values.values().join(' / '),
                reason: 'the workbook shows no code for the row and the rule refuses it too'])
    }
}

/** A cell's text after the column's value ops, or as read when the column has none. */
static String cooked(String text, String ops) {
    return ops ? SkuComposer.applyTextOps(text, ops) : text
}

/**
 * The ITEMS map's per-input value ops (FtmSkuSheetColumn.keyHeader -> valueOps, header normalised), each checked against
 * SkuComposer's vocabulary first; null after reporting MAP_INVALID when an op is unknown (nothing of the sheet is classified).
 */
Map<String, String> valueOpsOf(GenericValue map, Map common, List<Map> out) {
    List<GenericValue> cols = from('FtmSkuSheetColumn').where('sheetMapId', map.sheetMapId).orderBy('columnSeq').queryList()
    Map<String, String> ops = [:]
    for (GenericValue col in cols.findAll { GenericValue c -> c.valueOps && c.keyHeader }) {
        try {
            SkuComposer.applyTextOps('', col.valueOps as String)
        } catch (SkuRefusedException e) {
            out << change('MAP_INVALID', 'ERROR', common + [header: col.keyHeader,
                    reason: "sheet map ${map.sheetMapId} column ${col.columnSeq}: valueOps [${col.valueOps}] - ${e.message}"])
            return null
        }
        ops[WorkbookReader.normalise(col.keyHeader as String)] = col.valueOps as String
    }
    return ops
}

Map severityCounts(List<Map> changes) {
    Map<String, Integer> counts = [ERROR: 0, WARNING: 0, APPLICABLE: 0, INFO: 0]
    changes.each { Map c -> counts[c.severityId as String] = (counts[c.severityId as String] ?: 0) + 1 }
    return counts
}

def previewSkuWorkbook() {
    ByteBuffer buffer = parameters.uploadedFile as ByteBuffer
    if (buffer == null) {
        return error('No workbook uploaded')
    }
    ByteBuffer view = buffer.duplicate()
    if (view.remaining() > MAX_FILE_BYTES) {
        return error("The workbook is ${view.remaining()} bytes; the limit is ${MAX_FILE_BYTES} bytes - nothing stored")
    }
    byte[] bytes = new byte[view.remaining()]
    view.get(bytes)
    List<Map> changes
    try {
        changes = classify(bytes)
    } catch (RuntimeException | IOException e) {
        return error("Cannot read the workbook: ${e.message}")
    }
    // the record is written only after the whole file classified (validate before the write)
    String skuImportId = delegator.getNextSeqId('FtmSkuImport')
    Timestamp now = UtilDateTime.nowTimestamp()
    String fp = fingerprint(changes)
    Map counts = severityCounts(changes)
    delegator.create('FtmSkuImport', [skuImportId: skuImportId, fileName: parameters.fileName ?: 'workbook.xlsx', fileSha256: sha256(bytes),
            fileBytes: bytes.length as Long, statusId: STATUS_PREVIEWED, fingerprint: fp, uploadedByUserLoginId: parameters.userLogin?.userLoginId,
            uploadedDate: now, changeCount: changes.size() as Long, errorCount: counts.ERROR as Long, appliedCount: 0L])
    delegator.create('FtmSkuImportFile', [skuImportId: skuImportId, fileData: bytes])
    changes.eachWithIndex { Map c, int i ->
        Map row = [skuImportId: skuImportId, changeSeq: (i + 1) as Long, applied: 'N']
        ['kindId', 'severityId', 'sheetName', 'header', 'skuRuleId', 'productFeatureTypeId', 'keyValue', 'displayName', 'entityName', 'pkText',
         'fieldName', 'oldValue', 'newValue', 'payload', 'reason', 'applyDefault'].each { String f -> row[f] = c[f] == null ? null : c[f] as String }
        row.rowNum = c.rowNum == null ? null : c.rowNum as Long
        row.sequenceNum = c.sequenceNum == null ? null : c.sequenceNum as Long
        delegator.create('FtmSkuImportChange', row)
    }
    return success([skuImportId: skuImportId, fingerprint: fp, changeCount: changes.size(), errorCount: counts.ERROR, warningCount: counts.WARNING,
                    applicableCount: counts.APPLICABLE, infoCount: counts.INFO, kindCounts: changes.countBy { Map c -> c.kindId }])
}

/** Apply one ticked row; throws on any failure so the whole commit rolls back. */
void applyChange(GenericValue c) {
    Map ctx = [userLogin: parameters.userLogin]
    switch (c.kindId) {
        case 'VOCAB_NEW':
            Map made = run service: 'createProductFeature', with: ctx + [productFeatureTypeId: c.productFeatureTypeId, description: c.keyValue,
                    idCode: c.newValue, defaultSequenceNum: c.sequenceNum]
            c.pkText = made.productFeatureId
            break
        case 'VOCAB_CODE_DIFFERS':
            run service: 'updateProductFeature', with: ctx + [productFeatureId: c.pkText, productFeatureTypeId: c.productFeatureTypeId,
                    description: c.keyValue, idCode: c.newValue]
            break
        case 'ITEM_NEW':
            Map values = new JsonSlurper().parseText(c.payload as String) as Map
            Map made = run service: 'createTrimProduct', with: ctx + [skuRuleId: c.skuRuleId, values: values]
            if (!made.created) {
                throw new IllegalStateException("row ${c.sheetName}!${c.rowNum}: product ${made.productId} already held the code - the master moved")
            }
            c.pkText = made.productId
            break
        default:
            throw new IllegalStateException("row ${c.changeSeq}: kind ${c.kindId} cannot be applied")
    }
}

def commitSkuWorkbookImport() {
    String skuImportId = parameters.skuImportId
    GenericValue skuImport = from('FtmSkuImport').where('skuImportId', skuImportId).queryOne()
    if (skuImport == null) {
        return error("REFUSED: no import ${skuImportId}")
    }
    if (skuImport.statusId != STATUS_PREVIEWED) {
        return error("REFUSED: import ${skuImportId} is ${skuImport.statusId}, not previewed")
    }
    if (parameters.fingerprint != skuImport.fingerprint) {
        return error('REFUSED: the fingerprint sent is not the preview\'s')
    }
    GenericValue file = from('FtmSkuImportFile').where('skuImportId', skuImportId).queryOne()
    if (fingerprint(classify(file.fileData as byte[])) != skuImport.fingerprint) {
        return error('REFUSED: the master or the sheet map changed since the preview - preview the workbook again')
    }
    List<GenericValue> changes = from('FtmSkuImportChange').where('skuImportId', skuImportId).orderBy('changeSeq').queryList()
    if (changes.any { GenericValue c -> c.severityId == 'ERROR' }) {
        return error('REFUSED: the preview holds errors; fix the workbook or the sheet map and preview again')
    }
    Set<String> wanted = (parameters.applyChangeSeqs ?: []).collect { Object s -> s as String } as Set
    List<GenericValue> todo = changes.findAll { GenericValue c -> c.severityId == 'APPLICABLE' && ((c.changeSeq as Long).toString() in wanted) }
    if (!todo) {
        return error('Nothing ticked - nothing to commit')
    }
    // vocabulary before items, so an item that needs a new value finds it in the same transaction
    todo.sort { GenericValue c -> KIND_ORDER.indexOf(c.kindId as String) }
    Timestamp now = UtilDateTime.nowTimestamp()
    String who = parameters.userLogin?.userLoginId
    Integer failAfter = parameters.failAfterApplied
    int applied = 0
    for (GenericValue c in todo) {
        applyChange(c)
        c.applied = 'Y'
        c.appliedByUserLoginId = who
        c.appliedDate = now
        c.store()
        applied++
        if (failAfter != null && applied >= failAfter) {
            throw new IllegalStateException("test fault injected after ${applied} applied rows")
        }
    }
    skuImport.statusId = STATUS_COMMITTED
    skuImport.committedByUserLoginId = who
    skuImport.committedDate = now
    skuImport.appliedCount = applied as Long
    skuImport.store()
    return success([appliedCount: applied])
}

def markSkuImportStatus() {
    GenericValue skuImport = from('FtmSkuImport').where('skuImportId', parameters.skuImportId).queryOne()
    if (skuImport == null) {
        return error("no import ${parameters.skuImportId}")
    }
    skuImport.statusId = parameters.statusId
    skuImport.message = parameters.message
    skuImport.store()
    return success()
}
