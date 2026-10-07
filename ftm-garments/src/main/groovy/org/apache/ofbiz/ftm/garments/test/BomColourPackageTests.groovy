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
 * PLAYBOOK_SKU S6 on SYNTHETIC data (testdef/data/FtmBomTestData.xml): the colour package picks each variant's trims
 * at BOM explosion - zipper by shared colour, buttons by package rows - with quantities; an unresolved trim is refused;
 * a package row is never overwritten. Also customer SKUs per variant (assignCustomerSkus).
 */
class BomColourPackageTests extends OFBizTestCase {

    BomColourPackageTests(String name) {
        super(name)
    }

    void testRedGetsRedZipperByColourAndWhiteButtonsByPackage() {
        assert bom('TEST_B_RD', 10.0G) == [TEST_ZIP_RD: 10, TEST_BTN_WH: 40, TEST_THR: 1500]
    }

    void testPackageQuantityOverridesTheLine() {
        assert bom('TEST_B_BL', 10.0G) == [TEST_ZIP_BL: 10, TEST_BTN_BK: 60, TEST_THR: 1500]
    }

    void testColourWithoutPackageIsRefused() {
        Map result = dispatcher.runSync('getVariantBom', [productId: 'TEST_B_GN', quantity: 1.0G, userLogin: getUserLogin()])
        assert ServiceUtil.isError(result)
        assert ServiceUtil.getErrorMessage(result).contains('TEST_BTN_V')
        Map lenient = dispatcher.runSync('getVariantBom', [productId: 'TEST_B_GN', quantity: 1.0G, requireResolved: false,
                userLogin: getUserLogin()])
        assert lenient.unresolvedComponentIds == ['TEST_BTN_V']
    }

    void testPackageRowIsNeverOverwritten() {
        Map result = pack('TG_CLR_RD', 'TEST_BTN_BK', null)
        assert ServiceUtil.isError(result)
        assert from('ProductManufacturingRule').where('productId', 'TEST_BSTYLE', 'productIdIn', 'TEST_BTN_V',
                'productFeature', 'TG_CLR_RD').queryList()*.productIdInSubst == ['TEST_BTN_WH']
        assert ServiceUtil.isError(pack('TG_CLR_RD', 'TEST_THR', null))   // not a variant of the button line
    }

    void testCustomerSkusPerVariant() {
        Map args = [productId: 'TEST_GSTYLE1', colorFeatureId: 'TG_CLR_RD', goodIdentificationTypeId: 'TEST_CUST_SKU',
                    skuBySizeFeatureId: [TG_SZ_S: '900000001', TG_SZ_M: '900000002'], userLogin: getUserLogin()]
        Map first = dispatcher.runSync('assignCustomerSkus', args)
        assert ServiceUtil.isSuccess(first) : ServiceUtil.getErrorMessage(first)
        assert dispatcher.runSync('assignCustomerSkus', args).assigned == []
        assert from('GoodIdentification').where('goodIdentificationTypeId', 'TEST_CUST_SKU', 'idValue', '900000001')
                .queryOne()?.productId == 'TEST_G1_RD_S'
        Map clash = dispatcher.runSync('assignCustomerSkus', args + [colorFeatureId: 'TG_CLR_BL'])   // same codes, other colour
        assert ServiceUtil.isError(clash)
        assert from('GoodIdentification').where('goodIdentificationTypeId', 'TEST_CUST_SKU', 'productId', 'TEST_G1_BL_S').queryOne() == null
        assert ServiceUtil.isError(dispatcher.runSync('assignCustomerSkus', args + [goodIdentificationTypeId: 'TEST_NOT_CUST']))
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp()
        // the package rows every BOM test relies on (idempotent: re-running a test run changes nothing)
        assert ServiceUtil.isSuccess(pack('TG_CLR_RD', 'TEST_BTN_WH', null))
        assert ServiceUtil.isSuccess(pack('TG_CLR_BL', 'TEST_BTN_BK', 6.0G))
    }

    private Map pack(String colour, String trim, BigDecimal qty) {
        return dispatcher.runSync('setColourPackage', [productId: 'TEST_BSTYLE', garmentColorFeatureId: colour,
                componentProductId: 'TEST_BTN_V', trimProductId: trim, quantity: qty, userLogin: getUserLogin()])
    }

    private Map bom(String variantId, BigDecimal qty) {
        Map result = dispatcher.runSync('getVariantBom', [productId: variantId, quantity: qty, userLogin: getUserLogin()])
        assert ServiceUtil.isSuccess(result) : ServiceUtil.getErrorMessage(result)
        return result.components.collectEntries { Map c -> [(c.productId): c.quantity as int] }
    }

}
