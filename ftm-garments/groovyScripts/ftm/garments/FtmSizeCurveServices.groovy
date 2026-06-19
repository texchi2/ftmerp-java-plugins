package org.apache.ofbiz.ftm.garments

import org.apache.ofbiz.entity.GenericValue
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
// NOTE: OFBiz GroovyEngine invokes with EMPTY_ARGS and binds `parameters`,
// `delegator`, `dispatcher` as script variables — do NOT declare a method arg
// (it would shadow the binding with null). Ref GroovyEngine.java:110.
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
