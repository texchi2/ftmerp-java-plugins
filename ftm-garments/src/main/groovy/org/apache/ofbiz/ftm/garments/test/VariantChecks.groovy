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
import org.apache.ofbiz.entity.util.EntityUtil
import org.apache.ofbiz.service.testtools.OFBizTestCase

/** Shared S4 assertion: a style has N variants, each with exactly one COLOR and one SIZE standard feature, all distinct. */
final class VariantChecks {

    private VariantChecks() { }

    static void assertOneColourOneSize(OFBizTestCase test, String styleId, int expected) {
        List<String> ids = EntityUtil.filterByDate(test.from('ProductAssoc')
                .where('productId', styleId, 'productAssocTypeId', 'PRODUCT_VARIANT').queryList())*.productIdTo
        assert ids.size() == expected : "${styleId}: ${ids.size()} variants, expected ${expected}"
        Set<String> cells = [] as Set
        ids.each { String vid ->
            List<GenericValue> feats = EntityUtil.filterByDate(test.from('ProductFeatureAndAppl')
                    .where('productId', vid, 'productFeatureApplTypeId', 'STANDARD_FEATURE').queryList())
            List<String> colours = feats.findAll { GenericValue f -> f.productFeatureTypeId == 'COLOR' }*.productFeatureId
            List<String> sizes = feats.findAll { GenericValue f -> f.productFeatureTypeId == 'SIZE' }*.productFeatureId
            assert colours.size() == 1 && sizes.size() == 1 : "${vid}: colours ${colours} sizes ${sizes}"
            cells << colours[0] + '::' + sizes[0]
            assert test.from('Product').where('productId', vid).queryOne().isVariant == 'Y'
        }
        assert cells.size() == expected : "${styleId}: two variants share a colour x size cell"
    }

}
