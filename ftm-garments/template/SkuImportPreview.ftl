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
<#-- SKU workbook import preview (DESIGN_sku_workbook_import_2026-10-22 §4): every difference between the workbook and
     the master in exactly one bucket; the applicable rows carry a tick. No ?html: OFBiz encodes context strings before
     they reach FreeMarker (SkuGenerator.ftl, measured). Numbers use ?c so no grouping separators appear in ids. -->
<#if skuError?has_content><div class="alert alert-danger">${skuError}</div></#if>
<#if !skuImport??>
  <p>No import found for id "${skuImportId!""}".</p>
<#else>
  <p><b>Import ${skuImport.skuImportId}</b> &middot; ${skuImport.fileName!""} &middot; ${(skuImport.fileBytes!0)?c} bytes
     &middot; sha256 ${skuImport.fileSha256!""}<br/>
     uploaded by ${skuImport.uploadedByUserLoginId!""} at ${skuImport.uploadedDate!""} &middot;
     status <b>${(statusItem.description)!skuImport.statusId}</b> &middot; fingerprint ${skuImport.fingerprint!""}</p>
  <p>Differences by kind:
    <#list kindCounts?keys as k>${k} ${kindCounts[k]?c}<#sep> &middot; </#list><#if !kindCounts?has_content>none</#if></p>

  <#if errors?has_content>
  <h3>Errors (${errors?size?c}) &mdash; the commit is blocked until the workbook or the sheet map is fixed</h3>
  <table class="basic-table hover-bar">
    <tr><th>Kind</th><th>Sheet</th><th>Row</th><th>Header / key</th><th>Reason</th></tr>
    <#list errors as c>
    <tr><td>${c.kindId}</td><td>${c.sheetName!""}</td><td><#if c.rowNum??>${c.rowNum?c}</#if></td>
        <td>${c.header!""} ${c.keyValue!""}</td><td>${c.reason!""}</td></tr>
    </#list>
  </table>
  </#if>

  <#if warnings?has_content>
  <h3>Listed, not applied (${warnings?size?c})</h3>
  <table class="basic-table hover-bar">
    <tr><th>Kind</th><th>Sheet</th><th>Row</th><th>Attributes</th><th>Workbook code</th><th>Rule code</th><th>Reason</th></tr>
    <#list warnings as c>
    <tr><td>${c.kindId}</td><td>${c.sheetName!""}</td><td><#if c.rowNum??>${c.rowNum?c}</#if></td>
        <td>${c.displayName!""}</td><td>${c.oldValue!""}</td><td>${c.newValue!""}</td><td>${c.reason!""}</td></tr>
    </#list>
  </table>
  </#if>

  <form method="post" action="<@ofbizUrl>CommitSkuImport</@ofbizUrl>" id="skuImportCommitForm">
    <input type="hidden" name="skuImportId" value="${skuImport.skuImportId}"/>
    <input type="hidden" name="fingerprint" value="${skuImport.fingerprint!""}"/>
    <h3>Applicable (${applicable?size?c}) &mdash; tick what the master takes</h3>
    <#if applicable?has_content>
    <table class="basic-table hover-bar">
      <tr><th>Apply</th><th>Kind</th><th>Sheet</th><th>Row</th><th>Name</th><th>Key</th><th>Master value</th><th>New value</th><th>Note</th></tr>
      <#list applicable as c>
      <tr>
        <td><input type="checkbox" name="apply" value="${c.changeSeq?c}"<#if (c.applyDefault!"") == "Y"> checked="checked"</#if>/></td>
        <td>${c.kindId}</td><td>${c.sheetName!""}</td><td><#if c.rowNum??>${c.rowNum?c}</#if></td>
        <td>${c.displayName!""}</td><td>${c.keyValue!""}</td><td>${c.oldValue!""}</td><td><b>${c.newValue!""}</b></td><td>${c.reason!""}</td>
      </tr>
      </#list>
    </table>
    </#if>
    <#if canCommit>
      <p><input type="submit" value="Commit the ticked rows (one transaction; a failure keeps nothing)"/></p>
    <#else>
      <p>Nothing can be committed:
        <#if errors?has_content>the errors above.<#elseif !applicable?has_content>no applicable row.<#else>the import is ${skuImport.statusId}.</#if></p>
    </#if>
  </form>

  <#if infos?has_content>
  <h3>Information (${infos?size?c}) &mdash; nothing changes</h3>
  <table class="basic-table hover-bar">
    <tr><th>Kind</th><th>Sheet</th><th>Header</th><th>Key</th><th>Master value</th><th>Note</th></tr>
    <#list infos as c>
    <tr><td>${c.kindId}</td><td>${c.sheetName!""}</td><td>${c.header!""}</td><td>${c.keyValue!""}</td><td>${c.oldValue!""}${c.newValue!""}</td>
        <td>${c.reason!""}</td></tr>
    </#list>
  </table>
  </#if>
</#if>
