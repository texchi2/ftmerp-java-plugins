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

import java.nio.ByteBuffer

import org.apache.ofbiz.base.util.UtilHttp
import org.apache.ofbiz.service.GenericServiceException
import org.apache.ofbiz.service.ServiceUtil

/*
 * SKU workbook import screen events (DESIGN_sku_workbook_import_2026-10-22 §3). The upload goes straight to
 * previewSkuWorkbook, which stores the file and the differences; the session keeps nothing but the import id.
 * The commit carries the import id, the preview's fingerprint and the ticked change rows. A failed commit is
 * recorded on the import (its own transaction) so the record says what happened, and the entity cache is cleared
 * because a rolled-back create can leave its row in the cache.
 */
String preview() {
    Map multiPartMap = request.getAttribute('multiPartMap') ?: UtilHttp.getMultiPartParameterMap(request)
    Object file = multiPartMap?.get('uploadedFile')
    if (!(file instanceof ByteBuffer)) {
        request.setAttribute('_ERROR_MESSAGE_', 'No workbook uploaded')
        return 'error'
    }
    Map res = dispatcher.runSync('previewSkuWorkbook', [uploadedFile: file,
            fileName: multiPartMap.get('_uploadedFile_fileName') as String, userLogin: userLogin])
    if (ServiceUtil.isError(res)) {
        request.setAttribute('_ERROR_MESSAGE_', ServiceUtil.getErrorMessage(res))
        return 'error'
    }
    request.setAttribute('skuImportId', res.skuImportId)
    return 'success'
}

String commit() {
    String skuImportId = parameters.skuImportId
    List<String> apply = (request.getParameterValues('apply') ?: []) as List<String>
    request.setAttribute('skuImportId', skuImportId)
    Map res
    try {
        res = dispatcher.runSync('commitSkuWorkbookImport', [skuImportId: skuImportId, fingerprint: parameters.fingerprint,
                applyChangeSeqs: apply, userLogin: userLogin])
    } catch (GenericServiceException e) {
        res = ServiceUtil.returnError(e.message)
    }
    if (ServiceUtil.isError(res)) {
        String message = ServiceUtil.getErrorMessage(res)
        delegator.clearAllCaches()
        String statusId = message.startsWith('REFUSED:') ? 'FTMSKU_REFUSED' : 'FTMSKU_ROLLEDBACK'
        dispatcher.runSync('markSkuImportStatus', [skuImportId: skuImportId, statusId: statusId, message: message, userLogin: userLogin])
        request.setAttribute('_ERROR_MESSAGE_', message)
        return 'error'
    }
    return 'success'
}
