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
import org.apache.ofbiz.entity.transaction.TransactionUtil
import org.apache.ofbiz.ftm.garments.sku.SkuComposer
import org.apache.ofbiz.ftm.garments.sku.SkuRefusedException
import org.apache.ofbiz.service.ServiceUtil

import groovy.transform.Field

/*
 * SKU generator services (PLAYBOOK_SKU S2). The rule lives in FtmSkuRule / FtmSkuRuleSegment and the lookup
 * tables in ProductFeature (description = the attribute value, idCode = its code) — all DATA.
 * Methods take no parameter: OFBiz's GroovyEngine invokes them with no arguments and the IN map is the
 * script binding `parameters` (bug-1769).
 */

/** Upper bound for the one transaction of an expectCounts import (seconds). */
@Field static final int IMPORT_TRANSACTION_SECONDS = 900

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
    Closure<Boolean> allowedFor = { String typeId, String value ->
        from('ProductFeature').where('productFeatureTypeId', typeId).cache().queryList()
                .any { GenericValue f -> value.equalsIgnoreCase(f.description as String) }
    }
    Map out = SkuComposer.compose(segs, values, featureFor, allowedFor)
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

/**
 * The checks createTrimProduct makes before it writes anything. Returns [error: message], or [out: the composed code,
 * heldProductId: the product already holding this code with the same features and attributes, else null].
 * The import's dry run calls exactly this, so a dry run and a real run classify a row the same way.
 */
Map checkTrim(String skuRuleId, Map values, List productFeatureIds) {
    Map out
    try {
        out = composeFor(skuRuleId, values, productFeatureIds)
    } catch (SkuRefusedException e) {
        return [error: e.message]
    }
    String canonical = out.canonical
    if (!out.lengthValid) {
        return [error: "SKU [${out.code}] has ${canonical.length()} characters; rule [${skuRuleId}] "
                + "requires ${out.rule.codeLength} - not created"]
    }
    GenericValue held = from('GoodIdentification').where('goodIdentificationTypeId', 'SKU', 'idValue', canonical).queryFirst()
    if (!held) {
        return [out: out, heldProductId: null]
    }
    Set<String> haveFeatures = from('ProductFeatureAppl')
            .where('productId', held.productId, 'productFeatureApplTypeId', 'STANDARD_FEATURE')
            .filterByDate().queryList()*.productFeatureId as Set
    Map haveAttrs = from('ProductAttribute').where('productId', held.productId).queryList()
            .findAll { GenericValue a -> (a.attrName as String).startsWith('FTMSKU_') }
            .collectEntries { GenericValue a -> [(a.attrName): a.attrValue] }
    if (haveFeatures == (out.featureIds as Set) && haveAttrs == out.wantAttrs) {
        return [out: out, heldProductId: held.productId]
    }
    return [error: "SKU [${canonical}] already belongs to product [${held.productId}] with different attributes "
            + '- refused, nothing changed']
}

def createTrimProduct() {
    Map chk = checkTrim(parameters.skuRuleId, parameters.values, parameters.productFeatureIds)
    if (chk.error) {
        return error(chk.error)
    }
    Map out = chk.out
    String canonical = out.canonical
    if (chk.heldProductId) {
        return success([productId: chk.heldProductId, canonicalCode: canonical, created: false])
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

/*
 * PLAYBOOK_SKU S7 - one-time import of codes that already exist (the owner's workbooks). Every row ends in EXACTLY ONE
 * bucket, and the buckets add up to the rows given (0 silent):
 *   loaded            the rule reproduces the workbook's code -> createTrimProduct created it
 *   alreadyPresent    the rule reproduces the code and the item already exists (same attributes)
 *   refusedAsWorkbook the workbook has no code for the row and the rule refuses it too
 *   disagreeing       anything else - listed with both codes and the reason; NOTHING is created for it
 * Rows: [ref, skuRuleId, values, workbookCode]. Each creation runs in its own transaction, except:
 *   dryRun        the same path with writes disabled: each row runs createTrimProduct's own checks (checkTrim) and
 *                 nothing is created. A code an earlier row of the same run would create counts as alreadyPresent
 *                 (disagreeing if its features or attributes differ), as it would in a real run.
 *   expectCounts  [loaded, alreadyPresent, refusedAsWorkbook, disagreeing] -> the whole import is ONE transaction,
 *                 committed only if every count equals the expectation (the dry run's); otherwise nothing is kept.
 */
def importSkuRows() {
    List rows = parameters.rows ?: []
    boolean dryRun = parameters.dryRun as boolean
    Map expect = dryRun ? null : parameters.expectCounts
    List<String> bucketNames = ['loaded', 'alreadyPresent', 'refusedAsWorkbook', 'disagreeing']
    if (expect != null && !bucketNames.every { String b -> expect[b] instanceof Number }) {
        return error("expectCounts needs a number for each of ${bucketNames} - nothing done")
    }
    Map<String, List> buckets = bucketNames.collectEntries { String b -> [(b): []] }
    Map<String, Map> planned = [:]
    boolean began = expect != null ? TransactionUtil.begin(IMPORT_TRANSACTION_SECONDS) : false
    try {
        for (Map row in rows) {
            String failed = importOneRow(row, dryRun, expect != null, planned, buckets)
            if (failed) {
                TransactionUtil.rollback(began, failed, null)
                return error("Import rolled back, nothing kept: ${failed}")
            }
        }
        Map counts = buckets.collectEntries { String b, List l -> [(b): l.size()] }
        if (counts.values().sum() != rows.size()) {
            TransactionUtil.rollback(began, 'accounting broken', null)
            return error("Import accounting broken: ${rows.size()} rows, ${counts.values().sum()} accounted")
        }
        if (expect != null) {
            Map want = bucketNames.collectEntries { String b -> [(b): expect[b] as int] }
            if (counts != want) {
                TransactionUtil.rollback(began, 'counts differ from the expectation', null)
                return error("Import rolled back, nothing kept: counts ${counts} differ from the expected ${want}")
            }
            TransactionUtil.commit(began)
        }
    } catch (Exception e) {
        TransactionUtil.rollback(began, 'importSkuRows failed', e)
        throw e
    }
    return success(buckets + [rowCount: rows.size(), dryRun: dryRun])
}

/** One import row into its bucket. Returns a message only when a creation failed inside the one expectCounts transaction. */
String importOneRow(Map row, boolean dryRun, boolean oneTransaction, Map<String, Map> planned, Map<String, List> buckets) {
    Map gen
    try {
        // generateTrimSku's own composition, called directly and not as a service: an error RESULT from any service called
        // inside an open transaction marks it rollback-only, use-transaction="false" or not (ServiceDispatcher), which
        // would sink an expectCounts import on its first expected refusal
        Map out = composeFor(row.skuRuleId as String, row.values as Map, null)
        gen = [skuCode: out.code, canonicalCode: out.canonical, lengthValid: out.lengthValid]
    } catch (SkuRefusedException e) {
        if (row.workbookCode) {
            buckets.disagreeing << [ref: row.ref, workbookCode: row.workbookCode, ruleCode: null, reason: e.message]
        } else {
            buckets.refusedAsWorkbook << [ref: row.ref]
        }
        return null
    }
    String reason = !row.workbookCode ? 'the rule gives a code where the workbook has none'
            : gen.skuCode != row.workbookCode ? 'codes differ'
            : !gen.lengthValid ? "the code has ${(gen.canonicalCode as String).length()} characters - the rule requires its codeLength"
            : null
    if (reason) {
        buckets.disagreeing << [ref: row.ref, workbookCode: row.workbookCode, ruleCode: gen.skuCode, reason: reason]
        return null
    }
    Map chk = checkTrim(row.skuRuleId as String, row.values as Map, null)
    if (chk.error) {
        buckets.disagreeing << [ref: row.ref, workbookCode: row.workbookCode, ruleCode: gen.skuCode, reason: chk.error]
        return null
    }
    if (dryRun) {
        dryRunRow(row, chk, planned, buckets)
        return null
    }
    Map ctx = [skuRuleId: row.skuRuleId, values: row.values, productName: row.productName ?: gen.canonicalCode,
               userLogin: parameters.userLogin]
    // one transaction: join it, so a creation that still fails rolls the whole import back; otherwise a new one per row
    Map made = oneTransaction ? dispatcher.runSync('createTrimProduct', ctx) : dispatcher.runSync('createTrimProduct', ctx, 0, true)
    if (ServiceUtil.isError(made)) {
        if (oneTransaction) {
            return "row ${row.ref}: ${ServiceUtil.getErrorMessage(made)}"
        }
        buckets.disagreeing << [ref: row.ref, workbookCode: row.workbookCode, ruleCode: gen.skuCode, reason: ServiceUtil.getErrorMessage(made)]
        return null
    }
    if (made.created) {
        buckets.loaded << [ref: row.ref, productId: made.productId, sku: made.canonicalCode]
    } else {
        buckets.alreadyPresent << [ref: row.ref, productId: made.productId, sku: made.canonicalCode]
    }
    return null
}

/**
 * Dry-run bucket for a row that passed checkTrim, nothing written. A code that an earlier row of this run would create
 * is treated as held by that row, with createTrimProduct's own test: same features and attributes -> alreadyPresent,
 * otherwise disagreeing.
 */
void dryRunRow(Map row, Map chk, Map<String, Map> planned, Map<String, List> buckets) {
    Map out = chk.out
    String canonical = out.canonical
    Map mine = [features: out.featureIds as Set, attrs: out.wantAttrs]
    if (chk.heldProductId) {
        buckets.alreadyPresent << [ref: row.ref, productId: chk.heldProductId, sku: canonical]
    } else if (!planned.containsKey(canonical)) {
        planned[canonical] = mine
        buckets.loaded << [ref: row.ref, productId: null, sku: canonical]
    } else if (planned[canonical] == mine) {
        buckets.alreadyPresent << [ref: row.ref, productId: null, sku: canonical]
    } else {
        buckets.disagreeing << [ref: row.ref, workbookCode: row.workbookCode, ruleCode: out.code,
                                reason: "SKU [${canonical}] would already belong to an item of this run with different attributes"]
    }
}
