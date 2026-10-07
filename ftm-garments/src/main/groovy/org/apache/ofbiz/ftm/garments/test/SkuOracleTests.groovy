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
import org.apache.ofbiz.service.ServiceUtil
import org.apache.ofbiz.service.testtools.OFBizTestCase

/**
 * The workbook oracle: every fixture extracted from the owner's workbooks must be reproduced by generateTrimSku.
 * The fixtures and the rule data are PRIVATE (never in this repository); their path is the system property
 * ftm.sku.fixtures, default ~/ftm-sku-data/fixtures_ofbiz.jsonl. Absent fixtures or rules FAIL the test:
 * an oracle that could not run is not a pass. The known positives are flagged in the private fixtures
 * (knownPositive), so this public code names no workbook value.
 */
class SkuOracleTests extends OFBizTestCase {

    private static final String MODULE = SkuOracleTests.name

    SkuOracleTests(String name) {
        super(name)
    }

    void testWorkbookFixturesAreReproduced() {
        File file = new File(System.getProperty('ftm.sku.fixtures',
                System.getProperty('user.home') + '/ftm-sku-data/fixtures_ofbiz.jsonl'))
        assert file.exists() : "INSUFFICIENT: private fixtures not found at ${file} - the oracle did not run"
        List<Map> fixtures = file.readLines().findAll { String l -> l.trim() }.collect { String l -> new JsonSlurper().parseText(l) as Map }
        assert fixtures : "INSUFFICIENT: ${file} holds no fixtures"
        assert from('FtmSkuRule').queryCount() >= fixtures*.skuRuleId.unique().size() : 'INSUFFICIENT: private SKU rules not loaded'

        Map<String, Integer> tally = [:].withDefault { 0 }
        List<String> misses = []
        fixtures.each { Map f ->
            String verdict = check(f)
            tally[verdict]++
            if (verdict.startsWith('MISMATCH')) {
                misses << "${f.cell}: ${verdict}"
            }
        }
        List<Map> knownPositives = fixtures.findAll { Map x -> x.knownPositive }
        assert knownPositives : 'INSUFFICIENT: no known positive in the fixtures'
        knownPositives.each { Map f -> assert check(f) == 'MATCH' : "known positive ${f.cell} not reproduced" }
        // control: a planted wrong expectation must be caught, or this test cannot see a mismatch at all
        Map planted = new HashMap(knownPositives[0])
        String code = planted.expected
        planted.expected = code[0..-2] + (code[-1] == '0' ? '1' : '0')
        assert check(planted).startsWith('MISMATCH') : 'control failed: a planted wrong fixture was not caught'
        Debug.logInfo("SKU oracle: ${fixtures.size()} fixtures ${tally}", MODULE)
        misses.take(20).each { String m -> Debug.logWarning(m, MODULE) }
        assert misses.isEmpty() : "${misses.size()} fixture(s) not reproduced, first: ${misses.take(5)}"
    }

    private String check(Map f) {
        Map r = dispatcher.runSync('generateTrimSku', [skuRuleId: f.skuRuleId, values: f.values])
        if (f.status == 'workbook_error') {
            return ServiceUtil.isError(r) ? 'REFUSED_AS_WORKBOOK' : "MISMATCH produced ${r.skuCode} where the workbook errors"
        }
        if (ServiceUtil.isError(r)) {
            return "MISMATCH refused: ${ServiceUtil.getErrorMessage(r)}"
        }
        if (r.skuCode != f.expected || r.canonicalCode != f.canonical) {
            return "MISMATCH got ${r.skuCode} expected ${f.expected}"
        }
        boolean shouldBeValid = f.status != 'computed_length_not_16'
        return r.lengthValid == shouldBeValid ? 'MATCH' : "MISMATCH lengthValid ${r.lengthValid}"
    }

}
