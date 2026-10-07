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

import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.util.EntityUtil
import org.apache.ofbiz.entity.util.EntityUtilProperties
import org.apache.ofbiz.service.ServiceUtil

/*
 * PLAYBOOK_SKU S5 - garment SKU generator, SWITCHED OFF by default (config/ftm-garments.properties,
 * garmentSku.enabled=N; a SystemProperty row turns it on without a deploy). The code comes from the same rule
 * engine as the trims (generateTrimSku): the rule is DATA (FtmSkuRule), written by its owner.
 * Inputs offered to the rule for each variant: values STYLE (the FtmStyleNumber.styleNumber linked to the style,
 * else the style's productId) and STYLE_ID (the style's productId); productFeatureIds = the variant's STANDARD
 * features (its colour and size). All codes are computed and checked BEFORE anything is written.
 */

boolean garmentSkuEnabled() {
    return EntityUtilProperties.propertyValueEqualsIgnoreCase('ftm-garments', 'garmentSku.enabled', 'Y', delegator)
}

def assignGarmentSkus() {
    if (!garmentSkuEnabled()) {
        return error('The garment SKU generator is switched off (ftm-garments / garmentSku.enabled is not Y) - nothing assigned')
    }
    String styleId = parameters.productId
    String ruleId = parameters.skuRuleId ?: EntityUtilProperties.getPropertyValue('ftm-garments', 'garmentSku.ruleId', delegator)
    if (!ruleId) {
        return error('No garment SKU rule: pass skuRuleId or set ftm-garments / garmentSku.ruleId')
    }
    GenericValue style = from('Product').where('productId', styleId).queryOne()
    if (!style || style.isVirtual != 'Y') {
        return error("Product [${styleId}] is not a virtual style")
    }
    String styleNumber = from('FtmStyleNumber').where('productId', styleId).queryFirst()?.styleNumber ?: styleId
    List<String> variantIds = EntityUtil.filterByDate(from('ProductAssoc')
            .where('productId', styleId, 'productAssocTypeId', 'PRODUCT_VARIANT').queryList())*.productIdTo
    if (!variantIds) {
        return error("Style [${styleId}] has no variants")
    }

    // 1. compute and check every code first - nothing is written unless all variants pass
    List plan = []
    List kept = []
    Set<String> planned = [] as Set
    for (String vid in variantIdsSorted(variantIds)) {
        GenericValue have = from('GoodIdentification').where('goodIdentificationTypeId', 'SKU', 'productId', vid).queryOne()
        List<String> features = EntityUtil.filterByDate(from('ProductFeatureAppl')
                .where('productId', vid, 'productFeatureApplTypeId', 'STANDARD_FEATURE').queryList())*.productFeatureId
        Map gen = dispatcher.runSync('generateTrimSku', [skuRuleId: ruleId, productFeatureIds: features,
                values: [STYLE: styleNumber, STYLE_ID: styleId]])
        if (ServiceUtil.isError(gen)) {
            return error("Variant [${vid}]: ${ServiceUtil.getErrorMessage(gen)} - nothing assigned")
        }
        if (!gen.lengthValid) {
            return error("Variant [${vid}]: code [${gen.skuCode}] has the wrong length for rule [${ruleId}] - nothing assigned")
        }
        String code = gen.canonicalCode
        if (have) {
            if (have.idValue != code) {
                return error("Variant [${vid}] already has SKU [${have.idValue}], the rule gives [${code}] - nothing changed")
            }
            kept << vid
            continue
        }
        GenericValue held = from('GoodIdentification').where('goodIdentificationTypeId', 'SKU', 'idValue', code).queryFirst()
        if (held || !planned.add(code)) {
            return error("Code [${code}] for variant [${vid}] is already used${held ? ' by product [' + held.productId + ']' : ' by another variant'}"
                    + ' - nothing assigned')
        }
        plan << [productId: vid, code: code, dashed: gen.skuCode]
    }

    // 2. write
    plan.each { Map p ->
        run service: 'createGoodIdentification', with: [goodIdentificationTypeId: 'SKU', productId: p.productId, idValue: p.code]
        if (p.dashed != p.code) {
            run service: 'createGoodIdentification', with: [goodIdentificationTypeId: 'FTM_SKU_DASHED', productId: p.productId,
                    idValue: p.dashed]
        }
    }
    return success([assigned: plan.collect { Map p -> [productId: p.productId, sku: p.code] }, alreadyAssigned: kept])
}

List<String> variantIdsSorted(List<String> ids) {
    return ids.sort(false)
}

/*
 * A CUSTOMER's own item code per variant (e.g. a retailer's per-size SKU on its purchase order), kept as a
 * GoodIdentification whose type is a child of FTM_CUSTOMER_SKU (one type per customer, defined as data).
 * Not switched: these codes are the customer's, recorded as printed. All rows are checked before any is written.
 */
def assignCustomerSkus() {
    String styleId = parameters.productId
    String typeId = parameters.goodIdentificationTypeId
    if (from('GoodIdentificationType').where('goodIdentificationTypeId', typeId).queryOne()?.parentTypeId != 'FTM_CUSTOMER_SKU') {
        return error("[${typeId}] is not a customer SKU type (its parent must be FTM_CUSTOMER_SKU)")
    }
    Map<String, String> skuBySize = parameters.skuBySizeFeatureId
    Map variants = variantsOfColour(styleId, parameters.colorFeatureId as String)
    List plan = []
    List kept = []
    for (Map.Entry<String, String> e in skuBySize.entrySet().sort { a, b -> a.key <=> b.key }) {
        String vid = variants[e.key]
        if (!vid) {
            return error("Style [${styleId}] has no variant for colour [${parameters.colorFeatureId}] and size [${e.key}] - nothing written")
        }
        String code = (e.value as String).trim()
        GenericValue have = from('GoodIdentification').where('goodIdentificationTypeId', typeId, 'productId', vid).queryOne()
        if (have) {
            if (have.idValue != code) {
                return error("Variant [${vid}] already has [${have.idValue}] for ${typeId}; the input says [${code}] - nothing changed")
            }
            kept << vid
            continue
        }
        GenericValue held = from('GoodIdentification').where('goodIdentificationTypeId', typeId, 'idValue', code).queryFirst()
        if (held || plan.any { Map p -> p.code == code }) {
            return error("${typeId} [${code}] is already on ${held ? 'product [' + held.productId + ']' : 'another size of this style'} - nothing written")
        }
        plan << [productId: vid, code: code]
    }
    plan.each { Map p ->
        run service: 'createGoodIdentification', with: [goodIdentificationTypeId: typeId, productId: p.productId, idValue: p.code]
    }
    return success([assigned: plan, alreadyAssigned: kept])
}

/** sizeFeatureId -> variant productId, for the variants of a style that carry the given colour. */
Map variantsOfColour(String styleId, String colourId) {
    Map out = [:]
    List<String> ids = EntityUtil.filterByDate(from('ProductAssoc')
            .where('productId', styleId, 'productAssocTypeId', 'PRODUCT_VARIANT').queryList())*.productIdTo
    for (String vid in ids) {
        List feats = EntityUtil.filterByDate(from('ProductFeatureAndAppl')
                .where('productId', vid, 'productFeatureApplTypeId', 'STANDARD_FEATURE').queryList())
        if (feats.any { it.productFeatureTypeId == 'COLOR' && it.productFeatureId == colourId }) {
            String size = feats.find { it.productFeatureTypeId == 'SIZE' }?.productFeatureId
            if (size) {
                out[size] = vid
            }
        }
    }
    return out
}
