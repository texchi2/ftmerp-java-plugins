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

import org.apache.ofbiz.service.GenericServiceException
import org.apache.ofbiz.service.ServiceUtil
import org.apache.ofbiz.service.testtools.OFBizTestCase

/**
 * PLAYBOOK_SKU S7 on the SYNTHETIC rule TEST_SKU: every import row lands in exactly one bucket, a disagreeing row creates
 * nothing, and the buckets add up to the rows (0 silent). The dry run classifies exactly as the real run and writes nothing;
 * with expectCounts the import is kept only when every count matches.
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

    void testDryRunClassifiesLikeTheRealRunAndWritesNothing() {
        List rows = [
            row('new', 'Black', '0301', 'TBK-ABC0301'),
            row('same-again', 'Black', '0301', 'TBK-ABC0301'),
            row('same-code-other-attributes', 'Black', '0301', 'TBK-ABC0301', 'ABC'),
            row('held-by-another', 'White', '0002', 'TWH-ABC0002'),
            row('both-refuse', 'Purple', '0304', null),
            row('codes-differ', 'Black', '0302', 'TBK-ABC9999'),
        ]
        Map before = rowCounts()
        Map dry = dispatcher.runSync('importSkuRows', [rows: rows, dryRun: true, userLogin: getUserLogin()])
        assert ServiceUtil.isSuccess(dry) : ServiceUtil.getErrorMessage(dry)
        assert dry.dryRun
        assert rowCounts() == before : 'the dry run wrote something'
        Map<String, List> dryBuckets = bucketsOf(dry)
        assert dryBuckets == [loaded: ['new'], alreadyPresent: ['same-again'], refusedAsWorkbook: ['both-refuse'],
                              disagreeing: ['codes-differ', 'held-by-another', 'same-code-other-attributes']]
        Map real = dispatcher.runSync('importSkuRows', [rows: rows, userLogin: getUserLogin()])
        assert ServiceUtil.isSuccess(real) : ServiceUtil.getErrorMessage(real)
        assert bucketsOf(real) == dryBuckets : 'the real run classified a row differently from the dry run'
        assert rowCounts().Product == before.Product + 1
    }

    void testExpectCountsMismatchKeepsNothing() {
        List rows = [row('a', 'Black', '0401', 'TBK-ABC0401'), row('b', 'Black', '0402', 'TBK-ABC0402')]
        Map before = rowCounts()
        Map r = dispatcher.runSync('importSkuRows', [rows: rows, userLogin: getUserLogin(),
                expectCounts: [loaded: 1, alreadyPresent: 0, refusedAsWorkbook: 0, disagreeing: 0]])
        assert ServiceUtil.isError(r)
        assert ServiceUtil.getErrorMessage(r).contains('rolled back')
        assert rowCounts() == before : 'a rolled-back import left rows behind'
        ['TBKABC0401', 'TBKABC0402'].each { String code ->
            assert dispatcher.runSync('findProductBySku', [skuCode: code, userLogin: getUserLogin()]).productId == null
        }
    }

    void testExpectCountsMatchCommitsTheWholeImport() {
        List rows = [row('a', 'Black', '0411', 'TBK-ABC0411'), row('a-again', 'Black', '0411', 'TBK-ABC0411'),
                     row('refused', 'Purple', '0412', null)]
        Map r = dispatcher.runSync('importSkuRows', [rows: rows, userLogin: getUserLogin(),
                expectCounts: [loaded: 1, alreadyPresent: 1, refusedAsWorkbook: 1, disagreeing: 0]])
        assert ServiceUtil.isSuccess(r) : ServiceUtil.getErrorMessage(r)
        assert dispatcher.runSync('findProductBySku', [skuCode: 'TBKABC0411', userLogin: getUserLogin()]).productId
    }

    void testExpectCountsCreationFailureMidRunKeepsNothing() {
        Map ok1 = row('first', 'Black', '0431', 'TBK-ABC0431')
        Map ok2 = row('second', 'Black', '0432', 'TBK-ABC0432')
        Map bad = row('third', 'Black', '0433', 'TBK-ABC0433') + [productName: 'x' * 400]  // passes every check, fails to write
        Map before = rowCounts()
        Map r = null
        try {
            r = dispatcher.runSync('importSkuRows', [rows: [ok1, ok2, bad], userLogin: getUserLogin(),
                    expectCounts: [loaded: 3, alreadyPresent: 0, refusedAsWorkbook: 0, disagreeing: 0]])
        } catch (GenericServiceException e) {
            r = ServiceUtil.returnError(e.message)
        }
        assert ServiceUtil.isError(r) : 'a failed creation must fail the import'
        assert rowCounts() == before : 'rows created before the failure survived the rollback'
        ['TBKABC0431', 'TBKABC0432', 'TBKABC0433'].each { String code ->
            assert dispatcher.runSync('findProductBySku', [skuCode: code, userLogin: getUserLogin()]).productId == null
        }
    }

    void testExpectCountsMustNameEveryBucket() {
        Map before = rowCounts()
        Map r = dispatcher.runSync('importSkuRows', [rows: [row('a', 'Black', '0421', 'TBK-ABC0421')], userLogin: getUserLogin(),
                expectCounts: [loaded: 1]])
        assert ServiceUtil.isError(r)
        assert rowCounts() == before
    }

    private static Map<String, List> bucketsOf(Map result) {
        return ['loaded', 'alreadyPresent', 'refusedAsWorkbook', 'disagreeing'].collectEntries { String b ->
            [(b): (result[b]*.ref as List).sort()]
        }
    }

    private static Map row(String ref, String colour, String seq, String workbookCode, String name = 'abc') {
        return [ref: ref, skuRuleId: 'TEST_SKU', values: [Colour: colour, Name: name, Seq: seq], workbookCode: workbookCode]
    }

    private Map rowCounts() {
        return ['Product', 'GoodIdentification', 'ProductAttribute', 'ProductFeatureAppl'].collectEntries { String e ->
            [(e): delegator.findCountByCondition(e, null, null, null)]
        }
    }

}
