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
 * Screen data for the import preview (DESIGN_sku_workbook_import_2026-10-22 §4): the import header and its change
 * rows by severity - errors (block the commit), warnings, applicable rows (the ones a person may tick), information.
 */
String skuImportId = request.getAttribute('skuImportId') ?: parameters.skuImportId
context.skuImportId = skuImportId
context.skuError = request.getAttribute('_ERROR_MESSAGE_')
List<GenericValue> changes = []
if (skuImportId) {
    GenericValue skuImport = from('FtmSkuImport').where('skuImportId', skuImportId).queryOne()
    context.skuImport = skuImport
    if (skuImport) {
        changes = from('FtmSkuImportChange').where('skuImportId', skuImportId).orderBy('changeSeq').queryList()
        context.statusItem = skuImport.getRelatedOne('StatusItem', true)
    }
}
context.errors = changes.findAll { GenericValue c -> c.severityId == 'ERROR' }
context.warnings = changes.findAll { GenericValue c -> c.severityId == 'WARNING' }
context.applicable = changes.findAll { GenericValue c -> c.severityId == 'APPLICABLE' }
context.infos = changes.findAll { GenericValue c -> c.severityId == 'INFO' }
context.kindCounts = changes.countBy { GenericValue c -> c.kindId }.sort()
context.canCommit = context.skuImport && context.skuImport.statusId == 'FTMSKU_PREVIEWED' && !context.errors && context.applicable
