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
import org.apache.ofbiz.ftm.garments.sku.SkuComposer
import org.apache.ofbiz.service.ServiceUtil
import org.apache.ofbiz.service.testtools.OFBizTestCase

/**
 * SKU generator services against the SYNTHETIC rule TEST_SKU (testdef/data/FtmSkuTestData.xml):
 * code shape, first-match lookup, refusals, idempotence, collision refusal, both code forms, length check.
 */
class SkuServiceTests extends OFBizTestCase {

    private static final String RULE = 'TEST_SKU'

    SkuServiceTests(String name) {
        super(name)
    }

    void testTextOpsFollowExcel() {
        assert SkuComposer.applyTextOps('xy20-Abc', 'LEFT:3|UPPER|TRIM') == 'XY2'
        assert SkuComposer.applyTextOps('  a   b ', 'TRIM') == 'a b'
        assert SkuComposer.applyTextOps('HS', 'RIGHT:1') == 'S'
        assert SkuComposer.applyTextOps('ab', 'LEFT:5') == 'ab'
        assert SkuComposer.canonical('t-bk1-0002') == 'TBK10002'
    }

    void testGenerateKnownCode() {
        Map result = generate([Colour: 'Black', Name: 'abcdef', Seq: '0001'])
        assert ServiceUtil.isSuccess(result)
        assert result.skuCode == 'TBK-ABC0001'
        assert result.canonicalCode == 'TBKABC0001'
        assert result.lengthValid
        assert result.featureIds == ['SKU_TEST_C_BK']
    }

    void testFirstMatchWinsCaseInsensitively() {
        // 'black' matches both 'Black' (sequence 1, code BK) and 'BLACK' (sequence 3, code ZZ): the first wins
        assert generate([Colour: 'black', Name: 'abc', Seq: '0001']).skuCode == 'TBK-ABC0001'
    }

    void testUnknownOrMissingValueIsRefused() {
        Map unknown = generate([Colour: 'Purple', Name: 'abc', Seq: '0001'])
        assert ServiceUtil.isError(unknown)
        assert ServiceUtil.getErrorMessage(unknown).contains('Purple')
        assert ServiceUtil.isError(generate([Colour: 'Black', Name: 'abc']))
        assert ServiceUtil.isError(generate([Colour: 'Black', Name: '', Seq: '0001']))
    }

    void testChosenFeatureIdsGiveTheSameCode() {
        Map result = dispatcher.runSync('generateTrimSku',
                [skuRuleId: RULE, productFeatureIds: ['SKU_TEST_C_WH'], values: [Name: 'abc', Seq: '0001']])
        assert result.skuCode == 'TWH-ABC0001'
    }

    void testCreateIsIdempotentAndBothFormsFindTheProduct() {
        Map values = [Colour: 'White', Name: 'abc', Seq: '0003']
        Map first = create(values)
        Map second = create(values)
        assert ServiceUtil.isSuccess(first) && ServiceUtil.isSuccess(second)
        assert first.productId == second.productId
        assert !second.created
        assert from('GoodIdentification').where('goodIdentificationTypeId', 'SKU', 'idValue', 'TWHABC0003').queryList().size() == 1
        assert from('GoodIdentification').where('goodIdentificationTypeId', 'FTM_SKU_DASHED', 'productId', first.productId)
                .queryOne()?.idValue == 'TWH-ABC0003'
        assert find('TWH-ABC0003') == first.productId
        assert find('TWHABC0003') == first.productId
        assert from('ProductFeatureAppl').where('productId', first.productId).queryList()*.productFeatureId == ['SKU_TEST_C_WH']
    }

    void testCodeHeldByAnotherItemIsRefusedAndNothingChanges() {
        Map result = create([Colour: 'White', Name: 'abc', Seq: '0002'])
        assert ServiceUtil.isError(result)
        assert ServiceUtil.getErrorMessage(result).contains('TEST_SKU_SQUAT')
        List<GenericValue> holders = from('GoodIdentification').where('goodIdentificationTypeId', 'SKU', 'idValue', 'TWHABC0002')
                .queryList()
        assert holders*.productId == ['TEST_SKU_SQUAT']
    }

    void testWrongLengthIsReportedAndNotCreated() {
        Map generated = dispatcher.runSync('generateTrimSku', [skuRuleId: 'TEST_SKU_LEN', values: [:]])
        assert ServiceUtil.isSuccess(generated)
        assert !generated.lengthValid
        assert ServiceUtil.isError(dispatcher.runSync('createTrimProduct',
                [skuRuleId: 'TEST_SKU_LEN', values: [:], userLogin: getUserLogin()]))
    }

    private Map generate(Map values) {
        return dispatcher.runSync('generateTrimSku', [skuRuleId: RULE, values: values])
    }

    private Map create(Map values) {
        return dispatcher.runSync('createTrimProduct', [skuRuleId: RULE, values: values, userLogin: getUserLogin()])
    }

    private String find(String code) {
        return dispatcher.runSync('findProductBySku', [skuCode: code, userLogin: getUserLogin()]).productId
    }

}
