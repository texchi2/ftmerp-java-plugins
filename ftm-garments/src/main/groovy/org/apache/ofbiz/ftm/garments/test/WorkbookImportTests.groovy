/*******************************************************************************
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
 *******************************************************************************/
package org.apache.ofbiz.ftm.garments.test

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.apache.ofbiz.base.util.Debug
import org.apache.ofbiz.service.ServiceUtil
import org.apache.ofbiz.service.testtools.OFBizTestCase

/**
 * PLAYBOOK_SKU S7 - the REAL one-time import of the owner's workbook codes (private fixtures, see SkuOracleTests).
 * Gate: every row in exactly one bucket, buckets add up, a second run loads nothing new; the report is written next to
 * the private data (import_report.json). Absent data FAILS.
 */
class WorkbookImportTests extends OFBizTestCase {

    private static final String MODULE = WorkbookImportTests.name

    WorkbookImportTests(String name) {
        super(name)
    }

    void testWorkbookImport() {
        String dir = System.getProperty('user.home') + '/ftm-sku-data'
        File file = new File(System.getProperty('ftm.sku.fixtures', dir + '/fixtures_ofbiz.jsonl'))
        assert file.exists() : "INSUFFICIENT: private fixtures not found at ${file}"
        List rows = file.readLines().findAll { String l -> l.trim() }.collect { String l ->
            Map f = new JsonSlurper().parseText(l) as Map
            [ref: f.cell, skuRuleId: f.skuRuleId, values: f.values, workbookCode: f.status == 'workbook_error' ? null : f.expected]
        }
        assert rows : 'INSUFFICIENT: no rows'
        Map first = dispatcher.runSync('importSkuRows', [rows: rows, userLogin: getUserLogin()])
        assert ServiceUtil.isSuccess(first) : ServiceUtil.getErrorMessage(first)
        int created = first.loaded.size() + first.alreadyPresent.size()
        assert created + first.refusedAsWorkbook.size() + first.disagreeing.size() == rows.size()
        Map second = dispatcher.runSync('importSkuRows', [rows: rows, userLogin: getUserLogin()])
        assert second.loaded == [] : 'a second run must load nothing new'
        assert second.alreadyPresent.size() == created
        assert second.disagreeing*.ref as Set == first.disagreeing*.ref as Set

        Map report = [rows: rows.size(), loaded: first.loaded.size(), alreadyPresent: first.alreadyPresent.size(),
                      refusedAsWorkbook: first.refusedAsWorkbook.size(), disagreeing: first.disagreeing]
        new File(dir, 'import_report.json').text = JsonOutput.prettyPrint(JsonOutput.toJson(report))
        Debug.logInfo("S7 import: ${rows.size()} rows -> loaded ${first.loaded.size()} - already ${first.alreadyPresent.size()} - "
                + "refused as the workbook ${first.refusedAsWorkbook.size()} - disagreeing ${first.disagreeing.size()}; "
                + "second run loaded ${second.loaded.size()}", MODULE)
    }

}
