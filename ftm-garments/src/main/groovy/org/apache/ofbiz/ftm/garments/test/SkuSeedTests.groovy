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

import groovy.json.JsonSlurper
import org.apache.ofbiz.base.util.Debug
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.service.testtools.OFBizTestCase

/**
 * The loaded SKU seed against a PRIVATE manifest of expected feature counts per feature type, counted from the
 * owner's workbooks by a reader independent of the seed generator (system property ftm.sku.manifest, default
 * ~/ftm-sku-data/seed_manifest.json). Absent manifest FAILS: a count that could not be checked is not a pass.
 */
class SkuSeedTests extends OFBizTestCase {

    private static final String MODULE = SkuSeedTests.name

    SkuSeedTests(String name) {
        super(name)
    }

    void testSeedCountsEqualTheWorkbook() {
        File file = new File(System.getProperty('ftm.sku.manifest',
                System.getProperty('user.home') + '/ftm-sku-data/seed_manifest.json'))
        assert file.exists() : "INSUFFICIENT: private seed manifest not found at ${file}"
        Map<String, Integer> expected = new JsonSlurper().parse(file) as Map
        assert expected : 'INSUFFICIENT: empty manifest'
        List<String> wrong = []
        expected.each { String type, Integer count ->
            long got = from('ProductFeature').where('productFeatureTypeId', type).queryCount()
            if (got != count) {
                wrong << "${type}: expected ${count}, loaded ${got}".toString()
            }
        }
        Debug.logInfo("SKU seed: ${expected.size()} feature types, ${expected.values().sum()} features expected; "
                + "${wrong.size()} differ", MODULE)
        assert wrong.isEmpty() : "seed counts differ from the workbook: ${wrong.take(5)}"
    }

    void testEverySegmentPointsAtLoadedData() {
        List<GenericValue> segs = from('FtmSkuRuleSegment').queryList().findAll { GenericValue s -> !(s.skuRuleId as String).startsWith('TEST_') }
        assert segs : 'INSUFFICIENT: no SKU rule segments loaded'
        segs.each { GenericValue s ->
            assert s.segmentTypeId in ['LITERAL', 'INPUT', 'TEXT', 'FEATURE'] : "unknown segment type at ${s.skuRuleId}/${s.sequenceNum}"
            if (s.segmentTypeId == 'FEATURE') {
                assert s.productFeatureTypeId && s.inputName : "FEATURE segment without type or input at ${s.skuRuleId}/${s.sequenceNum}"
            }
            if (s.segmentTypeId in ['INPUT', 'TEXT']) {
                assert s.inputName : "input segment without inputName at ${s.skuRuleId}/${s.sequenceNum}"
            }
        }
        from('FtmSkuRule').queryList().each { GenericValue r -> assert r.codeLength : "rule ${r.skuRuleId} has no codeLength" }
    }

}
