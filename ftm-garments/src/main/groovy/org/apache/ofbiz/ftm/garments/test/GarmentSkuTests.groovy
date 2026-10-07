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
 * PLAYBOOK_SKU S5 on SYNTHETIC data (testdef/data/FtmGarmentSkuTestData.xml): the garment SKU generator is OFF by
 * default (the shipped properties file), a SystemProperty row turns it on, every variant then gets its code, a second
 * run changes nothing, and one used code refuses the whole style with nothing written.
 */
class GarmentSkuTests extends OFBizTestCase {

    GarmentSkuTests(String name) {
        super(name)
    }

    void testOffByDefaultWritesNothing() {
        delegator.removeByAnd('SystemProperty', [systemResourceId: 'ftm-garments', systemPropertyId: 'garmentSku.enabled'])
        // style 2 is never coded by any test, so this holds whatever order the tests run in
        Map result = assign('TEST_GSTYLE2')
        assert ServiceUtil.isError(result)
        assert ServiceUtil.getErrorMessage(result).contains('switched off')
        assert skusOf(['TEST_G2_RD_S', 'TEST_G2_BL_M']) == [null, null]
    }

    void testOnAssignsEveryVariantOnce() {
        switchTo('Y')
        try {
            Map first = assign('TEST_GSTYLE1')
            assert ServiceUtil.isSuccess(first) : ServiceUtil.getErrorMessage(first)
            assert skusOf(['TEST_G1_RD_S', 'TEST_G1_RD_M', 'TEST_G1_BL_S', 'TEST_G1_BL_M']) == ['GTG01RDS', 'GTG01RDM', 'GTG01BLS', 'GTG01BLM']
            assert from('GoodIdentification').where('goodIdentificationTypeId', 'FTM_SKU_DASHED', 'productId', 'TEST_G1_RD_S')
                    .queryOne()?.idValue == 'GTG01-RDS'
            Map second = assign('TEST_GSTYLE1')
            assert ServiceUtil.isSuccess(second)
            assert second.assigned == [] && second.alreadyAssigned.size() == 4
        } finally {
            switchTo('N')
        }
    }

    void testOneUsedCodeRefusesTheWholeStyle() {
        switchTo('Y')
        try {
            Map result = assign('TEST_GSTYLE2')
            assert ServiceUtil.isError(result)
            assert ServiceUtil.getErrorMessage(result).contains('TEST_G_SQUAT')
            assert skusOf(['TEST_G2_RD_S', 'TEST_G2_BL_M']) == [null, null]
        } finally {
            switchTo('N')
        }
    }

    private Map assign(String styleId) {
        return dispatcher.runSync('assignGarmentSkus', [productId: styleId, skuRuleId: 'TEST_GARMENT', userLogin: getUserLogin()])
    }

    private void switchTo(String value) {
        delegator.createOrStore(delegator.makeValue('SystemProperty', [systemResourceId: 'ftm-garments',
                systemPropertyId: 'garmentSku.enabled', systemPropertyValue: value]))
    }

    private List<String> skusOf(List<String> productIds) {
        return productIds.collect { String pid ->
            from('GoodIdentification').where('goodIdentificationTypeId', 'SKU', 'productId', pid).queryOne()?.idValue
        }
    }

}
