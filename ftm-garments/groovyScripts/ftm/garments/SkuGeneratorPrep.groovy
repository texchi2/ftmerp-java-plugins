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

/*
 * Screen data for the SKU generator (PLAYBOOK_SKU S7): the rules, and for the chosen rule one input row per non-literal
 * segment - a drop-down where the rule has a list (FEATURE lookups, INPUT/TEXT allowed lists), else a text box.
 */
List<GenericValue> rules = from('FtmSkuRule').orderBy('skuRuleId').queryList()
context.skuRules = rules
String ruleId = parameters.skuRuleId ?: request.getAttribute('skuRuleId') ?: rules ? rules[0].skuRuleId : null
context.skuRuleId = ruleId
Map entered = request.getAttribute('skuValues') ?: [:]
List rows = []
if (ruleId) {
    from('FtmSkuRuleSegment').where('skuRuleId', ruleId).orderBy('sequenceNum').queryList().each { GenericValue s ->
        if (s.segmentTypeId == 'LITERAL') {
            return
        }
        String listType = s.segmentTypeId == 'FEATURE' ? s.productFeatureTypeId : s.allowedFeatureTypeId
        List options = listType ? from('ProductFeature').where('productFeatureTypeId', listType).orderBy('defaultSequenceNum')
                .cache().queryList().collect { GenericValue f -> [value: f.description, code: f.idCode] } : null
        rows << [field: "v_${s.sequenceNum}", label: s.inputName, options: options, value: entered[s.inputName as String]]
    }
}
context.skuInputRows = rows
context.skuResult = request.getAttribute('skuResult')
context.skuError = request.getAttribute('skuError')
