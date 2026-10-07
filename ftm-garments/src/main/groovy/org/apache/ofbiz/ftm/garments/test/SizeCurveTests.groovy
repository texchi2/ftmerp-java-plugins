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

import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.service.ServiceUtil
import org.apache.ofbiz.service.testtools.OFBizTestCase

/**
 * NEITH size curve: the demo curve CHINO_STD_CURVE (1:2:1 over W32/W34/W36)
 * expanded over the demo style STYLE_CHINO01 (3 colours x 3 sizes).
 */
class SizeCurveTests extends OFBizTestCase {

    private static final String STYLE = 'STYLE_CHINO01'
    private static final String CURVE = 'CHINO_STD_CURVE'

    SizeCurveTests(String name) {
        super(name)
    }

    void testDemoCurveIsLoaded() {
        List<GenericValue> items = from('FtmSizeCurveItem').where('sizeCurveId', CURVE).orderBy('sequenceNum').queryList()
        assert items*.sizeFeatureId == ['SIZE_W32', 'SIZE_W34', 'SIZE_W36']
        assert items*.ratio*.stripTrailingZeros() == [1G, 2G, 1G]
    }

    void testExpandDemoCurveOverAllColours() {
        Map result = dispatcher.runSync('expandSizeCurveToVariants',
                [productId: STYLE, sizeCurveId: CURVE, quantityPerColor: 100.0G])
        assert ServiceUtil.isSuccess(result)
        List<Map> rows = result.variantQuantities
        Map expectedBySize = [SIZE_W32: 25, SIZE_W34: 50, SIZE_W36: 25]

        assert rows.size() == 9
        assert result.totalQuantity == 300
        assert rows*.colorFeatureId.toSet() == ['COLOR_NAVY', 'COLOR_BLACK', 'COLOR_KHAKI'] as Set
        // every (colour, size) cell resolves to its own variant product
        assert rows.every { Map row -> row.productId }
        assert rows*.productId.toSet().size() == 9
        rows.each { Map row ->
            assert row.quantity == expectedBySize[row.sizeFeatureId]
        }
    }

    void testOddQuantityStillTotalsPerColour() {
        Map result = dispatcher.runSync('expandSizeCurveToVariants',
                [productId: STYLE, sizeCurveId: CURVE, quantityPerColor: 10.0G, colorFeatureIds: ['COLOR_NAVY']])
        assert ServiceUtil.isSuccess(result)
        List<Map> rows = result.variantQuantities

        // 1:2:1 of 10 = 2.5 / 5 / 2.5: W34 is exact, the one leftover unit goes to W32 or W36
        assert rows.size() == 3
        assert rows.sum { Map row -> row.quantity } == 10
        assert result.totalQuantity == 10
        assert rows.find { Map row -> row.sizeFeatureId == 'SIZE_W34' }.quantity == 5
    }

    void testNonPositiveQuantityIsRefused() {
        Map result = dispatcher.runSync('expandSizeCurveToVariants',
                [productId: STYLE, sizeCurveId: CURVE, quantityPerColor: 0.0G])
        assert ServiceUtil.isError(result)
    }

}
