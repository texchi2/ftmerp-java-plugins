package org.apache.ofbiz.ftm.garments

import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator
import org.apache.ofbiz.entity.util.EntityUtil
import java.math.BigDecimal
import java.math.RoundingMode

/*
 * Project NEITH Phase 2 — size-curve order expansion.
 *
 * Given a virtual style, a set of colours, a size curve (per-size pack ratios)
 * and a per-colour quantity, expand into concrete per-variant SKU quantities.
 * Pure read/compute (no DB writes) so it is reusable by both OFBiz order entry
 * and the MERN enquiry ETL. Returns one row per (colour, size):
 *   [productId(variant, may be null if SKU absent), colorFeatureId, sizeFeatureId, quantity]
 */
/** (colorFeatureId::sizeFeatureId) -> variant productId, over the style's current PRODUCT_VARIANT associations. */
Map variantsByColorSize(String productId) {
    List variantAssocs = EntityUtil.filterByDate(delegator.findByAnd("ProductAssoc",
        [productId: productId, productAssocTypeId: "PRODUCT_VARIANT"], null, false))
    Map variantByColorSize = [:]
    for (GenericValue va in variantAssocs) {
        String variantId = va.productIdTo
        List feats = EntityUtil.filterByDate(delegator.findByAnd("ProductFeatureAndAppl",
            [productId: variantId, productFeatureApplTypeId: "STANDARD_FEATURE"], null, false))
        String c = feats.find { it.productFeatureTypeId == "COLOR" }?.productFeatureId
        String s = feats.find { it.productFeatureTypeId == "SIZE" }?.productFeatureId
        if (c && s) variantByColorSize[c + "::" + s] = variantId
    }
    return variantByColorSize
}

// No method parameter: OFBiz's GroovyEngine calls invokeMethod(name, EMPTY_ARGS), so a declared
// `Map parameters` argument arrives as null and shadows the script binding that holds the IN map.
def expandSizeCurveToVariants() {
    String productId = parameters.productId
    String sizeCurveId = parameters.sizeCurveId
    BigDecimal qtyPerColor = parameters.quantityPerColor as BigDecimal
    List colorFeatureIds = parameters.colorFeatureIds

    if (qtyPerColor == null || qtyPerColor <= BigDecimal.ZERO) {
        return error("quantityPerColor must be a positive number")
    }

    // 1. load curve items (sizeFeatureId -> ratio), ordered by sequence
    List curveItems = delegator.findByAnd("FtmSizeCurveItem", [sizeCurveId: sizeCurveId], ["sequenceNum"], false)
    if (!curveItems) return error("Size curve [${sizeCurveId}] not found or has no items")
    BigDecimal sumRatios = curveItems.inject(BigDecimal.ZERO) { acc, ci -> acc + (ci.ratio ?: BigDecimal.ZERO) }
    if (sumRatios <= BigDecimal.ZERO) return error("Size curve [${sizeCurveId}] ratios sum to zero")

    // 2. colours: explicit list, else the SELECTABLE COLOR features on the virtual style
    if (!colorFeatureIds) {
        List colorAppls = delegator.findByAnd("ProductFeatureAndAppl",
            [productId: productId, productFeatureTypeId: "COLOR", productFeatureApplTypeId: "SELECTABLE_FEATURE"],
            ["sequenceNum"], false)
        colorFeatureIds = EntityUtil.filterByDate(colorAppls).collect { it.productFeatureId }.unique()
    }
    if (!colorFeatureIds) {
        return error("No colours for style [${productId}] — pass colorFeatureIds or add SELECTABLE COLOR features")
    }

    // 3. map (colorFeatureId::sizeFeatureId) -> variant productId
    Map variantByColorSize = variantsByColorSize(productId)

    // 4. expand each colour across the curve using largest-remainder rounding
    BigDecimal targetPerColor = qtyPerColor.setScale(0, RoundingMode.HALF_UP)
    List out = []
    BigDecimal grand = BigDecimal.ZERO
    for (String color in colorFeatureIds) {
        List rows = []
        BigDecimal allocated = BigDecimal.ZERO
        for (GenericValue ci in curveItems) {
            BigDecimal exact = qtyPerColor.multiply(ci.ratio).divide(sumRatios, 6, RoundingMode.HALF_UP)
            BigDecimal q = exact.setScale(0, RoundingMode.FLOOR)
            rows << [sizeFeatureId: ci.sizeFeatureId, frac: exact.remainder(BigDecimal.ONE), qty: q]
            allocated = allocated + q
        }
        // hand out the remainder to the largest fractional parts so the colour totals exactly
        BigDecimal remainder = targetPerColor - allocated
        if (remainder > BigDecimal.ZERO) {
            rows.sort { a, b -> b.frac <=> a.frac }
            int i = 0
            while (remainder > BigDecimal.ZERO && i < rows.size()) {
                rows[i].qty = rows[i].qty + BigDecimal.ONE
                remainder = remainder - BigDecimal.ONE
                i++
            }
        }
        for (Map r in rows) {
            if (r.qty <= BigDecimal.ZERO) continue
            out << [productId       : variantByColorSize[color + "::" + r.sizeFeatureId],
                    colorFeatureId  : color,
                    sizeFeatureId   : r.sizeFeatureId,
                    quantity        : r.qty]
            grand = grand + r.qty
        }
    }

    return success([variantQuantities: out, totalQuantity: grand])
}

/*
 * PLAYBOOK_SKU S4 — create the garment variants of a virtual style: one variant Product per (colour x size on the
 * curve), each with exactly one COLOR and one SIZE STANDARD_FEATURE and a PRODUCT_VARIANT association, through
 * OFBiz's own quickAddVariant. Idempotent: a cell that already has its variant is left untouched (quickAddVariant
 * would rewrite it). The style gets the colours and sizes as SELECTABLE features if it lacks them.
 */
def createVariantsFromSizeCurve() {
    String productId = parameters.productId
    GenericValue style = from('Product').where('productId', productId).queryOne()
    if (!style || style.isVirtual != 'Y') {
        return error("Product [${productId}] is not a virtual style")
    }
    List sizeIds = from('FtmSizeCurveItem').where('sizeCurveId', parameters.sizeCurveId).orderBy('sequenceNum').queryList()*.sizeFeatureId
    if (!sizeIds) {
        return error("Size curve [${parameters.sizeCurveId}] not found or has no items")
    }
    List colorIds = parameters.colorFeatureIds ?: EntityUtil.filterByDate(from('ProductFeatureAndAppl')
            .where('productId', productId, 'productFeatureTypeId', 'COLOR', 'productFeatureApplTypeId', 'SELECTABLE_FEATURE')
            .orderBy('sequenceNum').queryList())*.productFeatureId.unique()
    if (!colorIds) {
        return error("No colours for style [${productId}] - pass colorFeatureIds or add SELECTABLE COLOR features")
    }
    Map features = from('ProductFeature').where(EntityCondition.makeCondition('productFeatureId', EntityOperator.IN,
            colorIds + sizeIds)).queryList().collectEntries { GenericValue f -> [(f.productFeatureId): f] }
    List wrongType = colorIds.findAll { features[it]?.productFeatureTypeId != 'COLOR' } +
            sizeIds.findAll { features[it]?.productFeatureTypeId != 'SIZE' }
    if (wrongType) {
        return error("Not a COLOR / SIZE feature (or unknown): ${wrongType}")
    }

    // the style shows the matrix: colours and sizes as SELECTABLE features
    Set selectable = EntityUtil.filterByDate(from('ProductFeatureAppl')
            .where('productId', productId, 'productFeatureApplTypeId', 'SELECTABLE_FEATURE').queryList())*.productFeatureId as Set
    (colorIds + sizeIds).findAll { !(it in selectable) }.each { String fid ->
        run service: 'applyFeatureToProduct', with: [productId: productId, productFeatureId: fid,
                productFeatureApplTypeId: 'SELECTABLE_FEATURE', fromDate: UtilDateTime.nowTimestamp()]
    }

    Map existing = variantsByColorSize(productId)
    List created = []
    List kept = []
    long seq = existing.size()
    for (String c in colorIds) {
        for (String s in sizeIds) {
            String have = existing[c + '::' + s]
            if (have) {
                kept << have
                continue
            }
            String variantId = variantIdFor(productId, features[c], features[s])
            if (from('Product').where('productId', variantId).queryOne()) {
                return error("Product [${variantId}] exists but is not the ${c} / ${s} variant of [${productId}] - refused")
            }
            Map res = run service: 'quickAddVariant', with: [productId: productId, productFeatureIds: c + '|' + s,
                    productVariantId: variantId, sequenceNum: ++seq]
            created << res.productVariantId
        }
    }
    return success([createdVariantIds: created, existingVariantIds: kept, variantCount: created.size() + kept.size()])
}

/** <style>-<colour code>-<size code> from the features' abbrev/idCode; a sequenced id when that exceeds 20 characters. */
String variantIdFor(String styleId, GenericValue color, GenericValue size) {
    Closure<String> code = { GenericValue f -> (f.abbrev ?: f.idCode ?: f.productFeatureId) as String }
    String id = "${styleId}-${code(color)}-${code(size)}".toUpperCase().replaceAll(/[^A-Z0-9_-]/, '')
    return id.length() <= 20 ? id : delegator.getNextSeqId('Product')
}
