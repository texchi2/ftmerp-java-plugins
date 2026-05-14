---
name: ofbiz-new-service
description: Scaffold a complete OFBiz Groovy service — creates servicedef XML entry, Groovy script, and controller.xml request-map in one shot, then optionally tests via browser
---

# SKILL: ofbiz-new-service

Given a service name, plugin, and parameters, scaffold three files at once and optionally smoke-test via browser.

## Inputs (ask user if not provided)
- `serviceName` — e.g. `createFtmWifiEnrollment`
- `plugin` — `ftm-wifi-enrollment` or `ftm-garments`
- `params` — list of IN/OUT parameters with types
- `serviceType` — `map` (returns map) or `none` (side-effects only)
- `test` — whether to open OFBiz web GUI to test after scaffolding (default: yes)

## Step 1 — doc-first: fetch OFBiz Service Engine docs

Before writing, invoke context7 to verify ServiceContext API:
```
Use context7 to look up "OFBiz ServiceContext runSync dispatcher" 
and "OFBiz servicedef XML attributes engine location invoke"
```

## Step 2 — Scaffold servicedef XML entry

Add to `<plugin>/servicedef/services.xml`:

```xml
<service name="{serviceName}" engine="groovy"
         location="component://{plugin}/groovyScripts/{ServiceName}.groovy"
         invoke="{serviceName}" auth="true">
    <description>{Description}</description>
    <!-- IN parameters -->
    <attribute name="paramName" type="String" mode="IN" optional="false"/>
    <!-- OUT parameters (for type=map) -->
    <attribute name="result" type="Map" mode="OUT" optional="true"/>
</service>
```

**Rules:**
- `engine="groovy"` always for Groovy scripts
- `location` uses `component://` prefix, NOT absolute path
- `auth="true"` unless explicitly public
- For `type=none` services: no OUT attributes needed

## Step 3 — Scaffold Groovy script

Create `<plugin>/groovyScripts/{ServiceName}.groovy`:

```groovy
import org.apache.ofbiz.base.util.Debug
import org.apache.ofbiz.entity.util.EntityQuery

def MODULE = "FtmWifi::{ServiceName}"

// Get context variables
def delegator = context.delegator
def dispatcher = context.dispatcher
def userLogin = context.userLogin

// --- Business logic here ---

// For type=map services:
return success([result: "value"])

// For type=none services:
return "success"
```

**Do-NOT-Repeat (from cerebrum):**
- ❌ `runEvent()` — DOES NOT EXIST. Use `dispatcher.runSync("serviceName", params)`
- ❌ `return success([response: closure])` — closures not serializable
- ❌ closure-based `HttpServletResponse` — use `context.response` directly
- ✅ `EntityQuery.use(delegator).from("EntityName").where("field", value).queryList()`
- ✅ `return "success"` for type=none, `return success([key: val])` for type=map

## Step 4 — Scaffold controller.xml request-map (if view-facing)

Add to `<plugin>/webapp/<plugin>/WEB-INF/controller.xml`  
*(request-maps MUST come BEFORE view-maps)*:

```xml
<request-map uri="{serviceName}">
    <security https="true" auth="true"/>
    <event type="service" invoke="{serviceName}"/>
    <response name="success" type="view" value="{resultView}"/>
    <response name="error" type="view" value="{errorView}"/>
</request-map>
```

## Step 5 — Restart check

```bash
# On OFBiz dev host:
ssh texchi@192.168.30.102 'grep "Started Apache Tomcat" /opt/ofbiz-framework/runtime/logs/ofbiz.log | tail -1'
```

If OFBiz needs restart (entitymodel/ofbiz-component.xml changed):
```bash
ssh texchi@192.168.30.102 'cd /opt/ofbiz-framework && ./gradlew ofbiz &'
```

## Step 6 — Browser smoke-test (invoke ofbiz-browser-tester)

After scaffolding, delegate to the `ofbiz-browser-tester` agent to:
1. Navigate to the OFBiz web GUI
2. Find the new service's entry point (form or direct URL)
3. Fill in test parameters
4. Submit and check for errors

Trigger: `Use the ofbiz-browser-tester agent to test {serviceName} at http://192.168.30.102:8080`
