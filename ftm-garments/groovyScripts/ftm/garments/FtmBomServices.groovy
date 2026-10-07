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

import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.util.EntityUtil
import org.apache.ofbiz.service.ServiceUtil

/*
 * PLAYBOOK_SKU S6 - garment BOM on OFBiz's own model, with the COLOUR PACKAGE (a garment colour decides each trim's
 * colour). The BOM is ProductAssoc MANUF_COMPONENT on the virtual style (every variant inherits it). A BOM line that
 * depends on the garment colour points at a VIRTUAL trim; the concrete trim is chosen at explosion by OFBiz:
 *   1. a ProductManufacturingRule of the style for (component, garment colour) - the package row this service writes;
 *   2. else a trim variant carrying the SAME colour feature as the garment variant (shared colour vocabulary).
 * OFBiz leaves a component VIRTUAL, without an error, when neither resolves it; getVariantBom turns that into a refusal.
 * A package row ALWAYS carries a quantity (0 = keep the BOM line's quantity, OFBiz's own meaning): trunk's
 * BOMNode.substituteNode calls compareTo on the rule quantity, and a NULL quantity crashes the whole BOM explosion.
 */

def setColourPackage() {
    String styleId = parameters.productId
    String colourId = parameters.garmentColorFeatureId
    String componentId = parameters.componentProductId
    String trimId = parameters.trimProductId
    GenericValue style = from('Product').where('productId', styleId).queryOne()
    if (!style || style.isVirtual != 'Y') {
        return error("Product [${styleId}] is not a virtual style")
    }
    if (from('ProductFeature').where('productFeatureId', colourId).queryOne()?.productFeatureTypeId != 'COLOR') {
        return error("[${colourId}] is not a COLOR feature")
    }
    GenericValue line = EntityUtil.getFirst(EntityUtil.filterByDate(from('ProductAssoc')
            .where('productId', styleId, 'productIdTo', componentId, 'productAssocTypeId', 'MANUF_COMPONENT').queryList()))
    if (!line) {
        return error("[${componentId}] is not on the BOM of style [${styleId}]")
    }
    if (from('Product').where('productId', componentId).queryOne()?.isVirtual != 'Y') {
        return error("BOM line [${componentId}] is not a virtual trim - OFBiz substitutes only virtual components")
    }
    if (!EntityUtil.filterByDate(from('ProductAssoc')
            .where('productId', componentId, 'productIdTo', trimId, 'productAssocTypeId', 'PRODUCT_VARIANT').queryList())) {
        return error("[${trimId}] is not a variant of trim [${componentId}]")
    }
    Double quantity = parameters.quantity != null ? parameters.quantity as Double : 0d
    GenericValue existing = EntityUtil.getFirst(EntityUtil.filterByDate(from('ProductManufacturingRule')
            .where('productId', styleId, 'productIdFor', styleId, 'productIdIn', componentId, 'productFeature', colourId).queryList()))
    if (existing) {
        if (existing.productIdInSubst == trimId && existing.quantity == null) {
            existing.quantity = quantity   // repair: a NULL quantity would crash OFBiz's explosion (see above)
            existing.store()
            return success([ruleId: existing.ruleId, created: false])
        }
        if (existing.productIdInSubst == trimId && existing.quantity == quantity) {
            return success([ruleId: existing.ruleId, created: false])
        }
        return error("Style [${styleId}] already maps colour [${colourId}] on [${componentId}] to [${existing.productIdInSubst}]"
                + ' - refused, nothing changed')
    }
    Map res = run service: 'addProductManufacturingRule', with: [productId: styleId, productIdFor: styleId, productIdIn: componentId,
            productIdInSubst: trimId, productFeature: colourId, ruleOperator: 'OR', quantity: quantity,
            fromDate: UtilDateTime.nowTimestamp(), description: 'FTM colour package']
    String ruleId = res.ruleId ?: from('ProductManufacturingRule').where('productId', styleId, 'productIdIn', componentId,
            'productFeature', colourId).queryFirst()?.ruleId
    return success([ruleId: ruleId, created: true])
}

def getVariantBom() {
    BigDecimal qty = (parameters.quantity ?: 1) as BigDecimal
    Map res = dispatcher.runSync('getManufacturingComponents', [productId: parameters.productId, quantity: qty,
            userLogin: parameters.userLogin])
    if (ServiceUtil.isError(res)) {
        return res
    }
    List components = (res.componentsMap ?: []).collect { Map c ->
        GenericValue p = c.product
        [productId: p.productId, quantity: c.quantity, isVirtual: p.isVirtual == 'Y']
    }
    List unresolved = components.findAll { Map c -> c.isVirtual }*.productId
    if (unresolved && parameters.requireResolved != false) {
        return error("No colour package or matching trim colour for ${unresolved} on [${parameters.productId}] - the BOM is incomplete")
    }
    return success([components: components, unresolvedComponentIds: unresolved])
}
