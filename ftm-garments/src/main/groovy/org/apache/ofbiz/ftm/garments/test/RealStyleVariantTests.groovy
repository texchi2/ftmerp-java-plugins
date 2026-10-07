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
 * PLAYBOOK_SKU S4 on a REAL FTM style (private data: system property ftm.sku.style, default
 * ~/ftm-sku-data/s4_style.json, + its seed). Variant count = colours x sizes on the curve; each variant exactly one
 * colour + one size; idempotent; the curve expands the order quantity back to the document's per-size quantities.
 * Absent data FAILS (INSUFFICIENT is not a pass).
 */
class RealStyleVariantTests extends OFBizTestCase {

    private static final String MODULE = RealStyleVariantTests.name

    RealStyleVariantTests(String name) {
        super(name)
    }

    void testRealStyleVariants() {
        File file = new File(System.getProperty('ftm.sku.style', System.getProperty('user.home') + '/ftm-sku-data/s4_style.json'))
        assert file.exists() : "INSUFFICIENT: private style data not found at ${file}"
        Map s = new JsonSlurper().parse(file) as Map
        int expected = s.colorFeatureIds.size() * s.sizeFeatureIds.size()
        Map args = [productId: s.styleProductId, sizeCurveId: s.sizeCurveId, colorFeatureIds: s.colorFeatureIds, userLogin: getUserLogin()]
        Map first = dispatcher.runSync('createVariantsFromSizeCurve', args)
        assert ServiceUtil.isSuccess(first) : ServiceUtil.getErrorMessage(first)
        assert first.variantCount == expected
        Map second = dispatcher.runSync('createVariantsFromSizeCurve', args)
        assert second.createdVariantIds == [] && second.variantCount == expected
        VariantChecks.assertOneColourOneSize(this, s.styleProductId as String, expected)

        Map expanded = dispatcher.runSync('expandSizeCurveToVariants', [productId: s.styleProductId, sizeCurveId: s.sizeCurveId,
                colorFeatureIds: s.colorFeatureIds, quantityPerColor: s.quantityPerColor as BigDecimal])
        assert ServiceUtil.isSuccess(expanded)
        assert expanded.variantQuantities.every { Map row -> row.productId }
        Map bySize = expanded.variantQuantities.collectEntries { Map row -> [(row.sizeFeatureId): row.quantity as int] }
        assert bySize == s.expectedQuantityBySize : "per-size quantities ${bySize} differ from the document"
        Debug.logInfo("S4 real style ${s.styleProductId}: ${expected} variants, quantities match the document", MODULE)
    }

    void testRealCustomerSkusPerVariant() {
        File file = new File(System.getProperty('ftm.sku.style', System.getProperty('user.home') + '/ftm-sku-data/s4_style.json'))
        assert file.exists() : "INSUFFICIENT: private style data not found at ${file}"
        Map s = new JsonSlurper().parse(file) as Map
        assert s.customerSkuBySize : 'INSUFFICIENT: no customer SKUs in the private style data'
        dispatcher.runSync('createVariantsFromSizeCurve', [productId: s.styleProductId, sizeCurveId: s.sizeCurveId,
                colorFeatureIds: s.colorFeatureIds, userLogin: getUserLogin()])
        Map args = [productId: s.styleProductId, colorFeatureId: s.colorFeatureIds[0], goodIdentificationTypeId: s.customerSkuType,
                    skuBySizeFeatureId: s.customerSkuBySize, userLogin: getUserLogin()]
        Map first = dispatcher.runSync('assignCustomerSkus', args)
        assert ServiceUtil.isSuccess(first) : ServiceUtil.getErrorMessage(first)
        assert dispatcher.runSync('assignCustomerSkus', args).assigned == []
        // read back: each customer code finds the variant of exactly its size
        s.customerSkuBySize.each { String sizeId, String code ->
            String vid = from('GoodIdentification').where('goodIdentificationTypeId', s.customerSkuType, 'idValue', code).queryOne()?.productId
            assert vid : "customer code ${code} not stored"
            assert from('ProductFeatureAppl').where('productId', vid, 'productFeatureId', sizeId).queryCount() == 1 :
                    "customer code ${code} is on ${vid}, not on the ${sizeId} variant"
        }
        Debug.logInfo("S4 real style: ${s.customerSkuBySize.size()} customer SKUs stored and read back per size", MODULE)
    }

}
