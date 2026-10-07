/*
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
 */
package org.apache.ofbiz.ftm.garments

import java.sql.Timestamp

import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator
import org.apache.ofbiz.ftm.garments.sku.SkuComposer
import org.apache.ofbiz.ftm.garments.sku.SkuRefusedException

/*
 * SKU generator services (PLAYBOOK_SKU S2). The rule lives in FtmSkuRule / FtmSkuRuleSegment and the lookup
 * tables in ProductFeature (description = the attribute value, idCode = its code) — all DATA.
 * Methods take no parameter: OFBiz's GroovyEngine invokes them with no arguments and the IN map is the
 * script binding `parameters` (bug-1769).
 */

/** Attribute name under which an INPUT/TEXT segment's value is kept on the product. */
String skuAttrName(GenericValue seg) {
    return "FTMSKU_${seg.sequenceNum}"
}

Map composeFor(String skuRuleId, Map rawValues, List productFeatureIds) {
    GenericValue rule = from('FtmSkuRule').where('skuRuleId', skuRuleId).cache().queryOne()
    if (!rule) {
        throw new SkuRefusedException("No SKU rule [${skuRuleId}]")
    }
    List<GenericValue> segs = from('FtmSkuRuleSegment').where('skuRuleId', skuRuleId).orderBy('sequenceNum').cache().queryList()
    if (!segs) {
        throw new SkuRefusedException("SKU rule [${skuRuleId}] has no segments")
    }
    Map<String, String> values = (rawValues ?: [:]).collectEntries { k, v -> [(k as String): v == null ? null : v as String] }
    List<GenericValue> chosen = productFeatureIds
            ? from('ProductFeature').where(EntityCondition.makeCondition('productFeatureId', EntityOperator.IN, productFeatureIds))
                    .queryList()
            : []
    if (chosen.size() != (productFeatureIds ?: []).unique().size()) {
        throw new SkuRefusedException('Unknown productFeatureId in ' + productFeatureIds)
    }
    Closure<Map> featureFor = { String typeId, String inputName ->
        List<GenericValue> picked = chosen.findAll { GenericValue f -> f.productFeatureTypeId == typeId }
        if (picked.size() > 1) {
            throw new SkuRefusedException("More than one chosen feature of type [${typeId}]")
        }
        if (picked) {
            return picked[0]
        }
        String v = values[inputName]
        if (v == null || v.isEmpty()) {
            return null
        }
        // first match in sheet order, case-insensitive text: measured equal to the workbook's VLOOKUP on all fixtures
        return from('ProductFeature').where('productFeatureTypeId', typeId).orderBy('defaultSequenceNum').cache()
                .queryList().find { GenericValue f -> v.equalsIgnoreCase(f.description as String) }
    }
    Map out = SkuComposer.compose(segs, values, featureFor)
    out.lengthValid = rule.codeLength == null || out.canonical.length() == (rule.codeLength as int)
    out.rule = rule
    out.wantAttrs = segs.findAll { GenericValue s -> s.segmentTypeId in ['INPUT', 'TEXT'] }
            .collectEntries { GenericValue s -> [(skuAttrName(s)): values[s.inputName as String]] }
    return out
}

def generateTrimSku() {
    try {
        Map out = composeFor(parameters.skuRuleId, parameters.values, parameters.productFeatureIds)
        return success([skuCode: out.code, canonicalCode: out.canonical, lengthValid: out.lengthValid,
                        featureIds: out.featureIds, attributes: out.attributes])
    } catch (SkuRefusedException e) {
        return error(e.message)
    }
}

def createTrimProduct() {
    Map out
    try {
        out = composeFor(parameters.skuRuleId, parameters.values, parameters.productFeatureIds)
    } catch (SkuRefusedException e) {
        return error(e.message)
    }
    String canonical = out.canonical
    if (!out.lengthValid) {
        return error("SKU [${out.code}] has ${canonical.length()} characters; rule [${parameters.skuRuleId}] "
                + "requires ${out.rule.codeLength} - not created")
    }
    GenericValue held = from('GoodIdentification').where('goodIdentificationTypeId', 'SKU', 'idValue', canonical).queryFirst()
    if (held) {
        Set<String> haveFeatures = from('ProductFeatureAppl')
                .where('productId', held.productId, 'productFeatureApplTypeId', 'STANDARD_FEATURE')
                .filterByDate().queryList()*.productFeatureId as Set
        Map haveAttrs = from('ProductAttribute').where('productId', held.productId).queryList()
                .findAll { GenericValue a -> (a.attrName as String).startsWith('FTMSKU_') }
                .collectEntries { GenericValue a -> [(a.attrName): a.attrValue] }
        if (haveFeatures == (out.featureIds as Set) && haveAttrs == out.wantAttrs) {
            return success([productId: held.productId, canonicalCode: canonical, created: false])
        }
        return error("SKU [${canonical}] already belongs to product [${held.productId}] with different attributes "
                + '- refused, nothing changed')
    }
    Map userLoginCtx = [userLogin: parameters.userLogin]
    Map res = run service: 'createProduct', with: userLoginCtx + [productTypeId: 'RAW_MATERIAL', internalName: canonical,
            productName: parameters.productName ?: canonical, isVirtual: 'N', isVariant: 'N']
    // run service: throws on an error result, so a failed step rolls the whole creation back
    String productId = res.productId
    Timestamp now = UtilDateTime.nowTimestamp()
    out.featureIds.unique().each { String fid ->
        run service: 'applyFeatureToProduct', with: userLoginCtx + [productId: productId, productFeatureId: fid,
                productFeatureApplTypeId: 'STANDARD_FEATURE', fromDate: now]
    }
    out.wantAttrs.each { String name, String value ->
        run service: 'createProductAttribute', with: userLoginCtx + [productId: productId, attrName: name, attrValue: value]
    }
    run service: 'createGoodIdentification', with: userLoginCtx + [goodIdentificationTypeId: 'SKU', productId: productId,
            idValue: canonical]
    if (out.code != canonical) {
        run service: 'createGoodIdentification', with: userLoginCtx + [goodIdentificationTypeId: 'FTM_SKU_DASHED',
                productId: productId, idValue: out.code]
    }
    return success([productId: productId, canonicalCode: canonical, created: true])
}

def findProductBySku() {
    String canonical = SkuComposer.canonical(parameters.skuCode as String)
    GenericValue gi = from('GoodIdentification').where('goodIdentificationTypeId', 'SKU', 'idValue', canonical).queryFirst()
    return success([productId: gi?.productId])
}
