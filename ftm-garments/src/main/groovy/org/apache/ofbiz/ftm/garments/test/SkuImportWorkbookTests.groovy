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
package org.apache.ofbiz.ftm.garments.test

import java.nio.ByteBuffer

import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.util.EntityQuery
import org.apache.ofbiz.service.GenericServiceException
import org.apache.ofbiz.service.ServiceUtil
import org.apache.ofbiz.service.testtools.OFBizTestCase
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook

/**
 * SKU workbook import on the SYNTHETIC rule TEST_SKU and the synthetic sheet map (FtmSkuImportTestData.xml), with
 * workbooks built here by POI (DESIGN_sku_workbook_import_2026-10-22 §7, gate G1).
 *
 * Every case was watched going red on a deliberate break of the code under test before it was trusted: two rounds on the
 * rpitex working tree on 2026-10-22 (logs sku-lane/logs/import-mutants.log and import-mutants2.log), each break reverted
 * with git checkout afterwards.
 *  - round 1, exactly these seven red (153 tests, 7 errors): a column read by its index (shuffled columns); the
 *    stale-fingerprint check disabled (stale preview); use-transaction="false" on the commit (rollback); required-permissions
 *    removed from the preview service (permission); the refused-as-workbook rows dropped (every kind in one bucket);
 *    appliedBy nulled (audit); HEADER_MISSING downgraded to WARNING (missing header);
 *  - round 2, the other four red (153 tests, 5 errors): the ticks ignored (tick-only, and the audit case as a side effect);
 *    canMint() always true (non-numeric mint); VOCAB_DUPLICATE_KEY downgraded to WARNING (duplicate key); the cap raised to
 *    8 GB (size cap);
 *  - v0.2 (2026-10-22, logs sku-lane/logs/mutant-v02-M1.log, -M2.log): M1 = a header resolved to its FIRST match, no block,
 *    no ambiguity check -> exactly the two v0.2 cases red (block resolution, repeated header); M2 = the code-header anchor
 *    ignored (block = null, row-wide uniqueness kept) -> the block-resolution case red (155 tests each round).
 */
class SkuImportWorkbookTests extends OFBizTestCase {

    private static final List<String> COUNTED = ['ProductFeature', 'Product', 'GoodIdentification', 'ProductFeatureAppl', 'ProductAttribute',
                                                 'FtmSkuImport', 'FtmSkuImportChange']

    SkuImportWorkbookTests(String name) {
        super(name)
    }

    // ---- tests ---------------------------------------------------------------------------------------------------

    void testEveryKindLandsInExactlyOneBucketAndNothingIsWritten() {
        Map before = counts()
        Map res = preview(workbook(happySheets()))
        assert ServiceUtil.isSuccess(res) : ServiceUtil.getErrorMessage(res)
        List<GenericValue> rows = changes(res.skuImportId as String)
        Map<String, Integer> k = kinds(rows)
        // VOCAB_MASTER_ONLY is >= 1: other cases of this class add sizes the happy sheets never list (run order is not fixed)
        assert k.VOCAB_CODE_DIFFERS == 1 && k.VOCAB_MASTER_ONLY >= 1 && k.VOCAB_NEW == 1 : "vocabulary kinds: ${k}"
        assert k.ITEM_NEW == 1 && k.ITEM_SAME == 1 && k.ITEM_DISAGREES == 1 && k.ITEM_REFUSED == 1 : "item kinds: ${k}"
        assert k.SHEET_UNMAPPED == 1 && !k.HEADER_MISSING && !k.HEADER_AMBIGUOUS : "structure kinds: ${k}"
        assert !k.COLUMN_UNMAPPED && !k.VOCAB_DUPLICATE_KEY && !k.MAP_INVALID : "structure kinds: ${k}"
        assert k.values().sum() == rows.size() && rows.size() == res.changeCount : 'a row is missing or counted twice'
        assert res.errorCount == 0 && res.applicableCount == 3 : "severities: ${res}"
        GenericValue differs = rows.find { GenericValue c -> c.kindId == 'VOCAB_CODE_DIFFERS' }
        assert differs.keyValue == 'White' && differs.oldValue == 'WH' && differs.newValue == 'WX' && differs.applyDefault == 'N'
        GenericValue masterOnly = rows.find { GenericValue c -> c.kindId == 'VOCAB_MASTER_ONLY' }
        assert masterOnly.keyValue == 'BLACK' && masterOnly.oldValue == 'ZZ' : 'the second Black row is a repeat, BLACK/ZZ is unmatched'
        GenericValue item = rows.find { GenericValue c -> c.kindId == 'ITEM_NEW' }
        assert item.newValue == 'TBKABC0801' && item.payload.contains('"Seq":"0801"') && item.rowNum == 2L
        // the preview wrote its own record and nothing else
        Map after = counts()
        assert after.FtmSkuImport == before.FtmSkuImport + 1 && after.FtmSkuImportChange == before.FtmSkuImportChange + rows.size()
        COUNTED.findAll { String e -> !e.startsWith('FtmSkuImport') }.each { String e -> assert after[e] == before[e] : "${e} changed on preview" }
    }

    void testShuffledColumnsGiveTheSameFingerprint() {
        Map plain = preview(workbook(happySheets()))
        Map<String, List<List<Object>>> sheets = happySheets()
        sheets['Test Colours'] = shuffled(sheets['Test Colours'], [2, 0, 1])
        sheets['Test Items'] = shuffled(sheets['Test Items'], [3, 2, 1, 0])
        Map moved = preview(workbook(sheets))
        assert ServiceUtil.isSuccess(plain) && ServiceUtil.isSuccess(moved)
        assert moved.fingerprint == plain.fingerprint : 'a column found by its header gives the same differences wherever it sits'
        assert kinds(changes(moved.skuImportId as String)) == kinds(changes(plain.skuImportId as String))
    }

    void testItemsAreReadFromTheBlockHoldingTheCodeHeader() {
        // the 2024 layout: a vocabulary block that repeats the item headers, a gap, then the item table with the code column
        Map plain = preview(workbook(happySheets()))
        Map<String, List<List<Object>>> left = happySheets()
        left['Test Items'] = twoBlocks(decoyBlock(), null, left['Test Items'])
        Map<String, List<List<Object>>> right = happySheets()
        right['Test Items'] = twoBlocks(right['Test Items'], null, decoyBlock())
        Map vocabLeft = preview(workbook(left))
        Map vocabRight = preview(workbook(right))
        assert ServiceUtil.isSuccess(vocabLeft) && ServiceUtil.isSuccess(vocabRight)
        assert vocabLeft.fingerprint == plain.fingerprint : 'items come from the block holding the code header, not the first match'
        assert vocabRight.fingerprint == plain.fingerprint : 'the same whichever side the decoy block sits'
        assert kinds(changes(vocabLeft.skuImportId as String)) == kinds(changes(plain.skuImportId as String))
    }

    void testARepeatedHeaderIsAnErrorNotAGuess() {
        Map before = counts()
        Map<String, List<List<Object>>> sheets = happySheets()
        // the key header twice (no gap, no anchor) and an input twice inside the code block
        sheets['Test Colours'] = twoBlocks(sheets['Test Colours'], [], [['Colour'], ['Grey'], ['Grey'], ['Grey']])
        sheets['Test Items'] = twoBlocks(sheets['Test Items'], [], [['Seq'], ['9'], ['9'], ['9'], ['9']])
        Map res = preview(workbook(sheets))
        assert ServiceUtil.isSuccess(res) : ServiceUtil.getErrorMessage(res)
        List<GenericValue> rows = changes(res.skuImportId as String)
        List<GenericValue> ambiguous = rows.findAll { GenericValue c -> c.kindId == 'HEADER_AMBIGUOUS' }
        assert ambiguous.size() == 2 && ambiguous.every { GenericValue c -> c.severityId == 'ERROR' } : "ambiguous: ${ambiguous*.reason}"
        assert ambiguous.find { GenericValue c -> c.sheetName == 'Test Colours' }.reason.contains("'Colour' appears 2 times")
        assert ambiguous.find { GenericValue c -> c.sheetName == 'Test Items' }.reason.contains("'Seq' appears 2 times inside the block")
        assert !rows.any { GenericValue c -> c.sheetName == 'Test Items' && c.kindId.startsWith('ITEM_') } : 'no item from a guessed column'
        Map done = commit(res.skuImportId as String, res.fingerprint as String, seqsOf(rows) { GenericValue c -> c.severityId == 'APPLICABLE' })
        assert ServiceUtil.isError(done) && ServiceUtil.getErrorMessage(done).startsWith('REFUSED: the preview holds errors')
        // the code header itself twice: the anchor is not unique
        Map<String, List<List<Object>>> twice = happySheets()
        twice['Test Items'] = twoBlocks(twice['Test Items'], [], [['SKU'], ['X'], ['X'], ['X'], ['X']])
        Map res2 = preview(workbook(twice))
        GenericValue a2 = changes(res2.skuImportId as String).find { GenericValue c -> c.kindId == 'HEADER_AMBIGUOUS' }
        assert a2 != null && a2.reason.contains("code header 'SKU' appears 2 times")
        Map after = counts()
        COUNTED.findAll { String e -> !e.startsWith('FtmSkuImport') }.each { String e -> assert after[e] == before[e] : "${e} changed" }
    }

    void testValueOpsNormaliseAnInputBeforeMatching() {
        // the 2024 drawcord Width: a number shown as "6.0 mm" by its format, while the master holds "6"; the owner's code
        // column carries the code built from the stored value
        List<List<Object>> items = [['Colour', 'Name', 'Seq', 'SKU'], ['Black', 'abc', [num: 8801.0, format: '0.0 "pcs"'], 'TBK-ABC8801']]
        Map plain = preview(workbook(['Test Items': items]))
        Map withOps = preview(workbook(['Test Items Ops': items]))
        assert ServiceUtil.isSuccess(plain) && ServiceUtil.isSuccess(withOps)
        GenericValue opsRow = EntityQuery.use(delegator).from('FtmSkuSheetColumn').where('sheetMapId', 'TEST_ITEMS_OPS').queryFirst()
        assert opsRow?.valueOps == 'STRIP_UNIT|NUMBER' : "the ops row as loaded: ${opsRow}"
        List<GenericValue> rawRows = changes(plain.skuImportId as String)
        List<GenericValue> opsRows = changes(withOps.skuImportId as String)
        // without ops the rule sees the text a person sees ("801.0 pcs"), so its code differs from the owner's
        assert kinds(rawRows).ITEM_DISAGREES == 1 && !kinds(rawRows).ITEM_NEW : "plain: ${kinds(rawRows)}"
        GenericValue cooked = opsRows.find { GenericValue c -> c.kindId == 'ITEM_NEW' }
        List<String> told = opsRows.collect { GenericValue c -> "${c.kindId} ${c.oldValue}->${c.newValue}: ${c.reason}".toString() }
        assert cooked != null : "with ops (the rule's codeLength is 10, so the cooked seq must be 4 digits): ${told}"
        assert cooked.payload.contains('"Seq":"8801"') && cooked.newValue == 'TBKABC8801' : "with STRIP_UNIT|NUMBER: ${cooked.payload}"
    }

    void testAnUnknownValueOpIsAnErrorThatBlocksTheCommit() {
        Map before = counts()
        Map<String, List<List<Object>>> sheets = happySheets()
        sheets['Test Items Bad'] = sheets['Test Items']
        Map res = preview(workbook(sheets))
        assert ServiceUtil.isSuccess(res) : ServiceUtil.getErrorMessage(res)
        List<GenericValue> rows = changes(res.skuImportId as String)
        GenericValue bad = rows.find { GenericValue c -> c.kindId == 'MAP_INVALID' }
        assert bad != null && bad.severityId == 'ERROR' && bad.sheetName == 'Test Items Bad' : "map invalid: ${bad}"
        assert bad.reason.contains('[FOO]') : "the unknown op is named: ${bad.reason}"
        assert !rows.any { GenericValue c -> c.sheetName == 'Test Items Bad' && c.kindId.startsWith('ITEM_') } : 'nothing classified under a bad map'
        assert rows.count { GenericValue c -> c.sheetName == 'Test Items' && c.kindId.startsWith('ITEM_') } == 4 : 'the good map still classifies'
        Map done = commit(res.skuImportId as String, res.fingerprint as String, seqsOf(rows) { GenericValue c -> c.severityId == 'APPLICABLE' })
        assert ServiceUtil.isError(done) && ServiceUtil.getErrorMessage(done).startsWith('REFUSED: the preview holds errors')
        Map after = counts()
        COUNTED.findAll { String e -> !e.startsWith('FtmSkuImport') }.each { String e -> assert after[e] == before[e] : "${e} changed" }
    }

    void testMissingHeaderIsAnErrorThatBlocksTheCommit() {
        Map<String, List<List<Object>>> sheets = happySheets()
        sheets['Test Items'] = [['Colour', 'Name', 'SKU'], ['Black', 'abc', 'TBK-ABC0801']]
        Map res = preview(workbook(sheets))
        assert ServiceUtil.isSuccess(res) : ServiceUtil.getErrorMessage(res)
        List<GenericValue> rows = changes(res.skuImportId as String)
        GenericValue missing = rows.find { GenericValue c -> c.kindId == 'HEADER_MISSING' }
        assert missing != null && missing.severityId == 'ERROR' && missing.sheetName == 'Test Items' && missing.reason.contains('Seq')
        assert rows.any { GenericValue c -> c.kindId == 'VOCAB_NEW' } : 'the other sheets are still classified'
        Map before = counts()
        Map done = commit(res.skuImportId as String, res.fingerprint as String, seqsOf(rows) { GenericValue c -> c.severityId == 'APPLICABLE' })
        assert ServiceUtil.isError(done) && ServiceUtil.getErrorMessage(done).startsWith('REFUSED:') : done
        assert counts() == before : 'a refused commit wrote something'
    }

    void testCommitAppliesTheTickedRowsWithOneAuditRowEach() {
        String expectedCode = nextSizeCode()
        Map res = preview(workbook(commitSheets()))
        assert ServiceUtil.isSuccess(res) : ServiceUtil.getErrorMessage(res)
        List<GenericValue> rows = changes(res.skuImportId as String)
        GenericValue grand = rows.find { GenericValue c -> c.kindId == 'VOCAB_NEW' }
        assert grand.keyValue == 'Grand' && grand.newValue == expectedCode : 'the master mints the next code in the type\'s own width'
        List<String> ticked = seqsOf(rows) { GenericValue c -> c.severityId == 'APPLICABLE' && c.applyDefault == 'Y' }
        assert ticked.size() == 2
        Map done = commit(res.skuImportId as String, res.fingerprint as String, ticked)
        assert ServiceUtil.isSuccess(done) : ServiceUtil.getErrorMessage(done)
        assert done.appliedCount == 2
        GenericValue made = feature('SKU_TEST_SIZE', 'Grand')
        assert made != null && made.idCode == expectedCode && made.defaultSequenceNum == grand.sequenceNum
        assert dispatcher.runSync('findProductBySku', [skuCode: 'TBKABC0901', userLogin: userLogin]).productId != null
        List<GenericValue> after = changes(res.skuImportId as String)
        List<GenericValue> applied = after.findAll { GenericValue c -> c.applied == 'Y' }
        assert applied*.changeSeq*.toString().sort() == ticked.sort()
        applied.each { GenericValue c ->
            assert c.appliedByUserLoginId == userLogin.userLoginId && c.appliedDate != null && c.pkText : "audit row incomplete: ${c}"
        }
        assert after.find { GenericValue c -> c.kindId == 'VOCAB_CODE_DIFFERS' }.applied == 'N' : 'an unticked row is not applied'
        GenericValue header = EntityQuery.use(delegator).from('FtmSkuImport').where('skuImportId', res.skuImportId).queryOne()
        assert header.statusId == 'FTMSKU_COMMITTED' && header.appliedCount == 2L && header.committedByUserLoginId == userLogin.userLoginId
        // the same workbook again: nothing new
        Map again = preview(workbook(commitSheets()))
        Map<String, Integer> k = kinds(changes(again.skuImportId as String))
        assert !k.VOCAB_NEW && !k.ITEM_NEW && k.ITEM_SAME == 1 : "second preview: ${k}"
    }

    void testCodeChangeIsAppliedOnlyWhenTicked() {
        Map res = preview(workbook(happySheets()))
        List<GenericValue> rows = changes(res.skuImportId as String)
        List<String> ticked = seqsOf(rows) { GenericValue c -> c.kindId == 'VOCAB_CODE_DIFFERS' }
        assert ticked.size() == 1
        Map before = counts()
        Map done = commit(res.skuImportId as String, res.fingerprint as String, ticked)
        assert ServiceUtil.isSuccess(done) : ServiceUtil.getErrorMessage(done)
        assert done.appliedCount == 1 : "one ticked row, ${done.appliedCount} applied"
        assert counts() == before : 'a code change adds no row, and the unticked rows add nothing'
        assert changes(res.skuImportId as String).findAll { GenericValue c -> c.severityId == 'APPLICABLE' && !(c.changeSeq.toString() in ticked) }
                .every { GenericValue c -> c.applied != 'Y' } : 'an unticked applicable row was applied'
        GenericValue white = feature('SKU_TEST_COLOR', 'White')
        assert white.idCode == 'WX'
        GenericValue audit = changes(res.skuImportId as String).find { GenericValue c -> c.kindId == 'VOCAB_CODE_DIFFERS' }
        assert audit.applied == 'Y' && audit.oldValue == 'WH' && audit.newValue == 'WX' && audit.pkText == white.productFeatureId
        // put the shared test master back for the other suites
        white.idCode = 'WH'
        white.store()
        delegator.clearAllCaches()
    }

    void testStalePreviewIsRefusedAndNothingWritten() {
        Map<String, List<List<Object>>> sheets = [
            'Test Sizes': [['Size'], ['Huge']],
        ]
        Map res = preview(workbook(sheets))
        assert ServiceUtil.isSuccess(res) : ServiceUtil.getErrorMessage(res)
        GenericValue huge = changes(res.skuImportId as String).find { GenericValue c -> c.kindId == 'VOCAB_NEW' }
        assert huge != null
        // the master moves between the preview and the commit
        delegator.create('ProductFeature', [productFeatureId: 'SKU_TEST_S_HUGE', productFeatureTypeId: 'SKU_TEST_SIZE', description: 'Huge',
                                            idCode: huge.newValue, defaultSequenceNum: huge.sequenceNum])
        Map before = counts()
        Map done = commit(res.skuImportId as String, res.fingerprint as String, [(huge.changeSeq as Long).toString()])
        assert ServiceUtil.isError(done) && ServiceUtil.getErrorMessage(done).contains('changed since the preview') : done
        assert counts() == before
        assert changes(res.skuImportId as String).every { GenericValue c -> c.applied == 'N' }
        Map wrongPrint = commit(res.skuImportId as String, 'not-the-preview-fingerprint', [(huge.changeSeq as Long).toString()])
        assert ServiceUtil.isError(wrongPrint) && ServiceUtil.getErrorMessage(wrongPrint).startsWith('REFUSED:')
    }

    void testAFailureRollsTheWholeCommitBack() {
        Map<String, List<List<Object>>> sheets = [
            'Test Sizes': [['Size'], ['Giant'], ['Tiny']],
        ]
        Map res = preview(workbook(sheets))
        assert ServiceUtil.isSuccess(res) : ServiceUtil.getErrorMessage(res)
        List<GenericValue> rows = changes(res.skuImportId as String)
        List<String> ticked = seqsOf(rows) { GenericValue c -> c.kindId == 'VOCAB_NEW' }
        assert ticked.size() == 2
        Map before = counts()
        Map done = commit(res.skuImportId as String, res.fingerprint as String, ticked, [failAfterApplied: 1])
        assert ServiceUtil.isError(done) : 'the injected fault must surface as an error'
        delegator.clearAllCaches()
        assert counts() == before : 'the row applied before the fault must be gone'
        assert feature('SKU_TEST_SIZE', 'Giant') == null && feature('SKU_TEST_SIZE', 'Tiny') == null
        assert changes(res.skuImportId as String).every { GenericValue c -> c.applied == 'N' }
        GenericValue header = EntityQuery.use(delegator).from('FtmSkuImport').where('skuImportId', res.skuImportId).queryOne()
        assert header.statusId == 'FTMSKU_PREVIEWED' : 'the service\'s own status change rolled back with the rest'
        Map marked = dispatcher.runSync('markSkuImportStatus', [skuImportId: res.skuImportId, statusId: 'FTMSKU_ROLLEDBACK',
                                                                message: ServiceUtil.getErrorMessage(done), userLogin: userLogin])
        assert ServiceUtil.isSuccess(marked)
        assert EntityQuery.use(delegator).from('FtmSkuImport').where('skuImportId', res.skuImportId).queryOne().statusId == 'FTMSKU_ROLLEDBACK'
    }

    void testALoginWithoutThePermissionIsRefused() {
        GenericValue nobody = EntityQuery.use(delegator).from('UserLogin').where('userLoginId', 'skuimport-nobody').queryOne()
        assert nobody != null : 'INSUFFICIENT: test login not loaded'
        Map before = counts()
        boolean refused
        try {
            Map res = preview(workbook(happySheets()), nobody)
            refused = ServiceUtil.isError(res)
        } catch (GenericServiceException ignored) {
            refused = true
        }
        assert refused : 'previewSkuWorkbook ran for a login with no permission'
        assert counts() == before
        Map done
        try {
            done = dispatcher.runSync('commitSkuWorkbookImport', [skuImportId: 'none', fingerprint: 'x', applyChangeSeqs: [], userLogin: nobody])
        } catch (GenericServiceException e) {
            done = ServiceUtil.returnError(e.message)
        }
        assert ServiceUtil.isError(done)
    }

    void testATypeWithNonNumericCodesCannotMintAndBlocksTheCommit() {
        Map<String, List<List<Object>>> sheets = [
            'Test Colours': [['Colour', 'Code', 'Note'], ['Purple', 'PU', 'new']],
        ]
        Map res = preview(workbook(sheets))
        assert ServiceUtil.isSuccess(res) : ServiceUtil.getErrorMessage(res)
        GenericValue purple = changes(res.skuImportId as String).find { GenericValue c -> c.kindId == 'VOCAB_NEW' }
        assert purple != null && purple.severityId == 'ERROR' && purple.reason.contains('cannot mint')
        assert res.errorCount == 1 && res.applicableCount == 0
    }

    void testADuplicateKeyWithAnotherCodeIsAnError() {
        Map<String, List<List<Object>>> sheets = [
            'Test Colours': [['Colour', 'Code', 'Note'], ['Black', 'BK', 'one'], ['BLACK', 'B2', 'two']],
        ]
        Map res = preview(workbook(sheets))
        assert ServiceUtil.isSuccess(res) : ServiceUtil.getErrorMessage(res)
        GenericValue dup = changes(res.skuImportId as String).find { GenericValue c -> c.kindId == 'VOCAB_DUPLICATE_KEY' }
        assert dup != null && dup.severityId == 'ERROR' && dup.oldValue == 'BK' && dup.newValue == 'B2' && dup.rowNum == 3L
    }

    void testTheSizeCapIsCheckedBeforeAnythingIsStored() {
        Map before = counts()
        Map res = dispatcher.runSync('previewSkuWorkbook', [uploadedFile: ByteBuffer.allocate(8 * 1024 * 1024 + 1), fileName: 'big.xlsx',
                                                            userLogin: userLogin])
        assert ServiceUtil.isError(res) && ServiceUtil.getErrorMessage(res).contains('limit')
        assert counts() == before
        Map junk = preview('not a workbook'.bytes)
        assert ServiceUtil.isError(junk) && ServiceUtil.getErrorMessage(junk).startsWith('Cannot read the workbook')
        assert counts() == before
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    /** An .xlsx with one sheet per entry: name -> rows (row 0 = headers); null cells are left empty. */
    private static byte[] workbook(Map<String, List<List<Object>>> sheets) {
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        new XSSFWorkbook().withCloseable { XSSFWorkbook wb ->
            sheets.each { String name, List<List<Object>> rows ->
                Sheet sheet = wb.createSheet(name)
                rows.eachWithIndex { List<Object> values, int r ->
                    Row row = sheet.createRow(r)
                    values.eachWithIndex { Object v, int c ->
                        if (v != null) {
                            Cell cell = row.createCell(c)
                            if (v instanceof Number) {
                                cell.setCellValue((v as Number).doubleValue())
                            } else if (v instanceof Map) {
                                // a number shown through an Excel format, as the owner's sheets do: [num: 6.0, format: '0.0 "mm"']
                                cell.setCellValue((v.num as Number).doubleValue())
                                CellStyle style = wb.createCellStyle()
                                style.dataFormat = wb.createDataFormat().getFormat(v.format as String)
                                cell.cellStyle = style
                            } else {
                                cell.setCellValue(v as String)
                            }
                        }
                    }
                }
            }
            wb.write(out)
        }
        return out.toByteArray()
    }

    /** The same rows with their columns in another order (header row included), so only the header text can find them. */
    private static List<List<Object>> shuffled(List<List<Object>> rows, List<Integer> order) {
        return rows.collect { List<Object> r -> order.collect { int i -> i < r.size() ? r[i] : null } }
    }

    /** A vocabulary-looking block that repeats the item headers with misleading values (the 2024 "Trims Spec" block). */
    private static List<List<Object>> decoyBlock() {
        return [['Colour', 'Name', 'Seq', 'Note'], ['White', 'zzz', '0001', 'decoy'], ['White', 'zzz', '0002', 'decoy'],
                ['White', 'zzz', '0003', 'decoy'], ['White', 'zzz', '0004', 'decoy']]
    }

    /** Two blocks side by side on the same rows; a null separator inserts one blank column between them. */
    private static List<List<Object>> twoBlocks(List<List<Object>> a, List<List<Object>> separator, List<List<Object>> b) {
        int rows = Math.max(a.size(), b.size())
        return (0..<rows).collect { int r ->
            List<Object> left = r < a.size() ? a[r] : (0..<a[0].size()).collect { null }
            List<Object> right = r < b.size() ? b[r] : []
            left + (separator == null ? [null] : []) + right
        }
    }

    /** The happy workbook: every kind once, no error. */
    private static Map<String, List<List<Object>>> happySheets() {
        return [
            'Test Colours': [['Colour', 'Code', 'Note'], ['Black', 'BK', 'same'], ['White', 'WX', 'code differs'], ['black', 'BK', 'repeat']],
            'Test Sizes':   [['Size'], ['Small'], ['Large'], ['Medium']],
            'Test Items':   [['Colour', 'Name', 'Seq', 'SKU'], ['Black', 'abc', '0801', 'TBK-ABC0801'], ['Black', 'abc', '0801', 'TBK-ABC0801'],
                             ['Black', 'abc', '0802', 'TBK-ABC9999'], ['Purple', 'abc', '0803', null]],
            'Scratch':      [['Anything'], ['at all']],
        ]
    }

    /** The happy sheets with values only this case commits ('Grand', item 0901), so the other cases keep their expectations whatever the order. */
    private static Map<String, List<List<Object>>> commitSheets() {
        return [
            'Test Colours': [['Colour', 'Code', 'Note'], ['White', 'WX', 'code differs']],
            'Test Sizes':   [['Size'], ['Grand']],
            'Test Items':   [['Colour', 'Name', 'Seq', 'SKU'], ['Black', 'abc', '0901', 'TBK-ABC0901']],
        ]
    }

    private Map preview(byte[] bytes, GenericValue login = userLogin) {
        return dispatcher.runSync('previewSkuWorkbook', [uploadedFile: ByteBuffer.wrap(bytes), fileName: 'test.xlsx', userLogin: login])
    }

    private Map commit(String skuImportId, String fingerprint, List<String> seqs, Map extra = [:]) {
        try {
            return dispatcher.runSync('commitSkuWorkbookImport', [skuImportId: skuImportId, fingerprint: fingerprint, applyChangeSeqs: seqs,
                                                                   userLogin: userLogin] + extra)
        } catch (GenericServiceException e) {
            return ServiceUtil.returnError(e.message)
        }
    }

    private List<GenericValue> changes(String skuImportId) {
        return EntityQuery.use(delegator).from('FtmSkuImportChange').where('skuImportId', skuImportId).orderBy('changeSeq').queryList()
    }

    private Map<String, Integer> kinds(List<GenericValue> rows) {
        return rows.countBy { GenericValue c -> c.kindId as String }
    }

    private List<String> seqsOf(List<GenericValue> rows, Closure<Boolean> pick) {
        return rows.findAll(pick).collect { GenericValue c -> (c.changeSeq as Long).toString() }
    }

    private Map<String, Long> counts() {
        return COUNTED.collectEntries { String e -> [(e): EntityQuery.use(delegator).from(e).queryCount()] }
    }

    private GenericValue feature(String typeId, String description) {
        return EntityQuery.use(delegator).from('ProductFeature').where('productFeatureTypeId', typeId).queryList()
                .find { GenericValue f -> description.equalsIgnoreCase(f.description as String) }
    }

    private String nextSizeCode() {
        List<Integer> codes = EntityQuery.use(delegator).from('ProductFeature').where('productFeatureTypeId', 'SKU_TEST_SIZE').queryList()
                *.idCode.collect { Object c -> (c as String).toInteger() }
        return String.valueOf(codes.max() + 1).padLeft(2, '0')
    }

}
