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

import org.apache.ofbiz.service.ServiceUtil
import org.apache.ofbiz.service.testtools.OFBizTestCase

/**
 * PLAYBOOK_SKU S7 on the SYNTHETIC rule TEST_SKU: every import row lands in exactly one bucket, a disagreeing row creates
 * nothing, and the buckets add up to the rows (0 silent).
 */
class SkuImportTests extends OFBizTestCase {

    SkuImportTests(String name) {
        super(name)
    }

    void testEveryRowLandsInExactlyOneBucket() {
        List rows = [
            row('agree', 'Black', '0101', 'TBK-ABC0101'),
            row('agree-again', 'Black', '0101', 'TBK-ABC0101'),
            row('codes-differ', 'Black', '0102', 'TBK-ABC9999'),
            row('both-refuse', 'Purple', '0104', null),
            row('rule-only', 'Black', '0103', null),
            row('workbook-only', 'Purple', '0105', 'TPU-ABC0105'),
            row('held-by-another', 'White', '0002', 'TWH-ABC0002'),
        ]
        Map r = dispatcher.runSync('importSkuRows', [rows: rows, userLogin: getUserLogin()])
        assert ServiceUtil.isSuccess(r) : ServiceUtil.getErrorMessage(r)
        Map<String, List<String>> buckets = ['loaded', 'alreadyPresent', 'refusedAsWorkbook', 'disagreeing']
                .collectEntries { String b -> [(b): r[b]*.ref] }
        assert buckets.values().flatten().sort() == rows*.ref.sort() : "a row is missing or counted twice: ${buckets}"
        assert r.rowCount == 7
        assert ('agree' in buckets.loaded) || ('agree' in buckets.alreadyPresent)
        assert 'agree-again' in buckets.alreadyPresent
        assert buckets.refusedAsWorkbook == ['both-refuse']
        assert buckets.disagreeing as Set == ['codes-differ', 'rule-only', 'workbook-only', 'held-by-another'] as Set
        // a disagreeing row creates nothing
        ['TBKABC9999', 'TBKABC0102', 'TBKABC0103'].each { String code ->
            assert dispatcher.runSync('findProductBySku', [skuCode: code, userLogin: getUserLogin()]).productId == null
        }
    }

    private static Map row(String ref, String colour, String seq, String workbookCode) {
        return [ref: ref, skuRuleId: 'TEST_SKU', values: [Colour: colour, Name: 'abc', Seq: seq], workbookCode: workbookCode]
    }

}
