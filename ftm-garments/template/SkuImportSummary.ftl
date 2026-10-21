<#--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->
<#-- SKU workbook import summary: the import record and its audit rows (one per applied change: who, when, old, new). -->
<#if skuError?has_content><div class="alert alert-danger">${skuError}</div></#if>
<#if !skuImport??>
  <p>No import found for id "${skuImportId!""}".</p>
<#else>
  <p><b>Import ${skuImport.skuImportId}</b> &middot; ${skuImport.fileName!""} &middot; sha256 ${skuImport.fileSha256!""}<br/>
     status <b>${(statusItem.description)!skuImport.statusId}</b>
     <#if skuImport.committedDate??>&middot; committed by ${skuImport.committedByUserLoginId!""} at ${skuImport.committedDate}</#if>
     &middot; applied ${(skuImport.appliedCount!0)?c} of ${applied?size?c + notApplied?size?c} applicable rows
     <#if skuImport.message?has_content><br/>${skuImport.message}</#if></p>
  <h3>Applied (${applied?size?c}) &mdash; the audit</h3>
  <table class="basic-table hover-bar">
    <tr><th>#</th><th>Kind</th><th>Sheet</th><th>Row</th><th>Name</th><th>Key</th><th>Record</th><th>Old value</th><th>New value</th><th>By</th><th>At</th></tr>
    <#list applied as c>
    <tr><td>${c.changeSeq?c}</td><td>${c.kindId}</td><td>${c.sheetName!""}</td><td><#if c.rowNum??>${c.rowNum?c}</#if></td>
        <td>${c.displayName!""}</td><td>${c.keyValue!""}</td><td>${c.entityName!""} ${c.pkText!""}</td>
        <td>${c.oldValue!""}</td><td>${c.newValue!""}</td><td>${c.appliedByUserLoginId!""}</td><td>${c.appliedDate!""}</td></tr>
    </#list>
  </table>
  <#if notApplied?has_content>
  <h3>Applicable but not applied (${notApplied?size?c})</h3>
  <ul><#list notApplied as c><li>${c.kindId} &middot; ${c.sheetName!""} &middot; ${c.keyValue!""} &rarr; ${c.newValue!""}</li></#list></ul>
  </#if>
  <p><a href="<@ofbizUrl>SkuImport</@ofbizUrl>">Upload another workbook</a></p>
</#if>
