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
import org.apache.ofbiz.service.ServiceUtil

/*
 * SKU generator screen event (PLAYBOOK_SKU S7). "Generate" shows the code the rule gives (writes nothing);
 * "Create" runs createTrimProduct (idempotent; refuses a used code or a wrong length). Both always return to the screen,
 * with the result or the refusal shown - never a partial code.
 */
String generate() {
    String ruleId = parameters.skuRuleId
    Map values = [:]
    from('FtmSkuRuleSegment').where('skuRuleId', ruleId).queryList().each { GenericValue s ->
        String v = parameters["v_${s.sequenceNum}".toString()] as String
        if (s.segmentTypeId != 'LITERAL' && v) {
            values[s.inputName] = v.trim()
        }
    }
    request.setAttribute('skuRuleId', ruleId)
    request.setAttribute('skuValues', values)
    boolean create = parameters.skuAction == 'create'
    Map res = dispatcher.runSync(create ? 'createTrimProduct' : 'generateTrimSku',
            [skuRuleId: ruleId, values: values] + (create ? [userLogin: userLogin] : [:]))
    if (ServiceUtil.isError(res)) {
        request.setAttribute('skuError', ServiceUtil.getErrorMessage(res))
    } else {
        request.setAttribute('skuResult', res + [action: create ? 'create' : 'generate'])
    }
    return 'success'
}
