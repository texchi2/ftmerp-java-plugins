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
 * PLAYBOOK_SKU S4 on SYNTHETIC data: createVariantsFromSizeCurve makes colours x sizes variants, each with exactly
 * one COLOR and one SIZE feature, and is idempotent. Uses the NEITH demo colours and curve (asserted by this suite).
 */
class StyleVariantTests extends OFBizTestCase {

    StyleVariantTests(String name) {
        super(name)
    }

    void testVariantsAreColoursTimesSizesAndIdempotent() {
        Map args = [productId: 'TEST_STYLE_V', sizeCurveId: 'CHINO_STD_CURVE', colorFeatureIds: ['COLOR_NAVY', 'COLOR_BLACK'],
                    userLogin: getUserLogin()]
        Map first = dispatcher.runSync('createVariantsFromSizeCurve', args)
        assert ServiceUtil.isSuccess(first)
        assert first.variantCount == 6
        Map second = dispatcher.runSync('createVariantsFromSizeCurve', args)
        assert ServiceUtil.isSuccess(second)
        assert second.createdVariantIds == []
        assert second.existingVariantIds as Set == (first.createdVariantIds + first.existingVariantIds) as Set
        VariantChecks.assertOneColourOneSize(this, 'TEST_STYLE_V', 6)

        Map expanded = dispatcher.runSync('expandSizeCurveToVariants', [productId: 'TEST_STYLE_V', sizeCurveId: 'CHINO_STD_CURVE',
                colorFeatureIds: ['COLOR_NAVY', 'COLOR_BLACK'], quantityPerColor: 100.0G])
        assert expanded.variantQuantities.every { Map row -> row.productId }
    }

    void testHandLoadedDemoVariantsAreRecognised() {
        Map result = dispatcher.runSync('createVariantsFromSizeCurve', [productId: 'STYLE_CHINO01', sizeCurveId: 'CHINO_STD_CURVE',
                userLogin: getUserLogin()])
        assert ServiceUtil.isSuccess(result)
        assert result.createdVariantIds == []
        assert result.existingVariantIds.size() == 9
    }

    void testNonVirtualProductIsRefused() {
        Map result = dispatcher.runSync('createVariantsFromSizeCurve', [productId: 'TEST_NOT_VIRTUAL', sizeCurveId: 'CHINO_STD_CURVE',
                colorFeatureIds: ['COLOR_NAVY'], userLogin: getUserLogin()])
        assert ServiceUtil.isError(result)
    }

}
