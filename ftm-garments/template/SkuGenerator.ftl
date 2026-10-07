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
<#-- SKU generator entry screen (PLAYBOOK_SKU S7). Generate = show the code; Create = make the item (idempotent).
     No ?html: OFBiz HTML-encodes context strings before they reach FreeMarker; ?html encoded them twice (measured). -->
<form method="get" action="<@ofbizUrl>SkuGenerator</@ofbizUrl>" id="skuRuleForm">
  <label for="skuRuleId">Item category (SKU rule)</label>
  <select name="skuRuleId" id="skuRuleId" onchange="this.form.submit()">
    <#list skuRules as r>
      <option value="${r.skuRuleId}" <#if r.skuRuleId == (skuRuleId!"")>selected="selected"</#if>>${r.skuRuleId} - ${(r.description!"")}</option>
    </#list>
  </select>
</form>
<#if skuRuleId?has_content>
<form method="post" action="<@ofbizUrl>SkuGenerate</@ofbizUrl>" id="skuValuesForm">
  <input type="hidden" name="skuRuleId" value="${skuRuleId}"/>
  <table class="basic-table">
    <#list skuInputRows as row>
      <tr>
        <td class="label"><label for="${row.field}">${row.label}</label></td>
        <td>
          <#if row.options??>
            <select name="${row.field}" id="${row.field}">
              <option value=""></option>
              <#list row.options as o>
                <option value="${o.value}" <#if (row.value!"") == o.value>selected="selected"</#if>>${o.value}<#if o.code?has_content> (${o.code})</#if></option>
              </#list>
            </select>
          <#else>
            <input type="text" name="${row.field}" id="${row.field}" value="${(row.value!"")}"/>
          </#if>
        </td>
      </tr>
    </#list>
  </table>
  <button type="submit" name="skuAction" value="generate" id="skuGenerate">Generate</button>
  <button type="submit" name="skuAction" value="create" id="skuCreate">Create item</button>
</form>
</#if>
<#if skuError?has_content>
  <p class="alert" id="skuError">Refused: ${skuError}</p>
</#if>
<#if skuResult??>
  <table class="basic-table" id="skuResult">
    <#if skuResult.skuCode??><tr><td class="label">Code</td><td><b>${skuResult.skuCode}</b></td></tr></#if>
    <tr><td class="label">Canonical (stored)</td><td>${(skuResult.canonicalCode!"")}</td></tr>
    <#if skuResult.lengthValid??><tr><td class="label">Length valid</td><td>${skuResult.lengthValid?string("yes", "NO - this code cannot be created")}</td></tr></#if>
    <#if skuResult.productId??><tr><td class="label">Item</td><td>${skuResult.productId} <#if skuResult.created>(created)<#else>(already existed - nothing changed)</#if></td></tr></#if>
  </table>
</#if>
