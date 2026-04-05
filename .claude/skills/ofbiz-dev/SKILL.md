---
name: ofbiz-dev
description: >-
  OFBiz plugin development skill for ftmerp-java-plugins on ofbiz-dev (Incus container,
  192.168.30.102). Use when: writing Groovy services, screen XML, form XML, controller.xml,
  entitymodel XML, or any OFBiz component file for ftm-wifi-enrollment or ftm-garments.
  Always doc-first before writing any OFBiz-specific XML or Groovy.
---

# ofbiz-dev — FTM OFBiz Plugin Development Skill

## Core Rule: /doc-first Always

Before writing ANY OFBiz XML or Groovy service, fetch and verify the official schema.
Never guess attribute names or element ordering.

### Authoritative Sources

| Resource | URL |
|----------|-----|
| OFBiz Developer Manual | https://nightlies.apache.org/ofbiz/trunk/ofbiz/html5/developer-manual.html |
| Widget Screen XSD | https://ofbiz.apache.org/dtds/widget-screen.xsd |
| Widget Form XSD | https://ofbiz.apache.org/dtds/widget-form.xsd |
| Site-Conf (controller) XSD | https://ofbiz.apache.org/dtds/site-conf.xsd |
| Entity Model XSD | https://ofbiz.apache.org/dtds/entitymodel.xsd |
| Service Engine Guide | https://cwiki.apache.org/confluence/display/OFBIZ/Service+Engine+Guide |
| Groovy Tips | https://cwiki.apache.org/confluence/display/OFBIZ/Tips+and+Tricks+while+working+with+Groovy |
| FAQ / External DB | https://cwiki.apache.org/confluence/display/OFBIZ/FAQ+-+Tips+-+Tricks+-+Cookbook+-+HowTo |
| Example plugin (reference) | /opt/ofbiz-plugins/example/ |

## Infrastructure

| Item | Value |
|------|-------|
| Container | ofbiz-dev (Incus on pfsense-msi-ftm 192.168.30.3) |
| Container IP | 192.168.30.102 (static via netplan) |
| OFBiz framework | /opt/ofbiz-framework |
| OFBiz plugins | /opt/ofbiz-plugins (symlinked from framework/plugins) |
| FTM plugin | /opt/ofbiz-plugins/ftm-wifi-enrollment/ |
| PostgreSQL | 192.168.30.3:5432, db=ftm_enrollment, user=enrolladmin |
| Derby (OFBiz main) | /opt/ofbiz-framework/runtime/data/derby/ |
| OFBiz URL | http://192.168.30.102:8080 |
| Admin login | admin / ofbiz |
| GitHub repos | texchi2/ftmerp-java-plugins, texchi2/ftmerp-java-project |

## Known OFBiz Trunk Bugs (Fixed in ofbiz-dev)

1. **`framework/entity/ofbiz-component.xml`**: `loaders="main,load-data"` + `preloaded-delegators value=""`
2. **`entityengine.xml`**: `ftmEnrollment` group-map must be in BOTH `default` delegator AND `ftmEnrollment` delegator; delegators must appear BEFORE datasources in XML
3. **`ofbiz-component.xml` (any plugin)**: MUST have `<resource-loader name="main" type="component"/>` if it has `entity-resource` elements
4. **`controller.xml`**: request-maps MUST come BEFORE view-maps; include `common-controller.xml` for `checkLogin`
5. **`FtmWifiCommonScreens.xml`**: `mainDecoratorLocation` must NOT point to a file containing its own `main-decorator` (infinite recursion)
6. **External DB entities**: MUST use `no-auto-stamp="true"` in entitymodel or OFBiz adds non-existent stamp columns to SQL

## Component Structure (ftm-wifi-enrollment)

```
ftm-wifi-enrollment/
├── ofbiz-component.xml          # MUST have resource-loader name="main"
├── entitydef/
│   ├── entitymodel_ftm_wifi.xml # no-auto-stamp="true" on all external entities
│   └── entitygroup_ftm_wifi.xml # group="ftmEnrollment" for all FTM entities
├── servicedef/
│   └── services_ftm_wifi.xml    # auth="false" for read services; no description= on attribute
├── groovyScripts/ftm/wifi/      # Direct JDBC via groovy.sql.Sql; return success([...])
├── webapp/ftm-wifi/WEB-INF/
│   ├── controller.xml           # request-maps first, then view-maps; include common-controller
│   └── web.xml                  # mainDecoratorLocation → FtmWifiCommonScreens.xml
└── widget/
    ├── FtmWifiCommonScreens.xml # main-decorator → ApplicationDecorator (commonext)
    ├── FtmWifiScreens.xml       # xmlns= + xsi:schemaLocation= (not noNamespaceSchemaLocation)
    ├── FtmWifiForms.xml         # xmlns="http://ofbiz.apache.org/Widget-Form"
    └── FtmWifiMenus.xml
```

## Critical XML Patterns

### Screen file header (correct)
```xml
<screens xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xmlns="http://ofbiz.apache.org/Widget-Screen"
    xsi:schemaLocation="http://ofbiz.apache.org/Widget-Screen http://ofbiz.apache.org/dtds/widget-screen.xsd">
```

### Form file header (correct)
```xml
<forms xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xmlns="http://ofbiz.apache.org/Widget-Form"
    xsi:schemaLocation="http://ofbiz.apache.org/Widget-Form http://ofbiz.apache.org/dtds/widget-form.xsd">
```

### FtmWifiCommonScreens.xml pattern (mainDecoratorLocation target)
```xml
<screen name="main-decorator">
    <section>
        <actions>
            <set field="activeApp" value="ftm-wifi" global="true"/>
            <set field="applicationMenuName" value="FtmWifiAppBar" global="true"/>
            <set field="applicationMenuLocation"
                value="component://ftm-wifi-enrollment/widget/FtmWifiMenus.xml" global="true"/>
        </actions>
        <widgets>
            <include-screen name="ApplicationDecorator"
                location="component://commonext/widget/CommonScreens.xml"/>
        </widgets>
    </section>
</screen>
```

### Screen using decorator (correct)
```xml
<decorator-screen name="main-decorator" location="${parameters.mainDecoratorLocation}">
```

### Groovy service pattern (external PostgreSQL)
```groovy
import groovy.sql.Sql

def myService() {
    def sql = Sql.newInstance(
        "jdbc:postgresql://192.168.30.3:5432/ftm_enrollment",
        "enrolladmin",
        System.getenv("FTM_ENROLLMENT_DB_PASS") ?: "ftmscep2026",
        "org.postgresql.Driver"
    )
    def result = []
    try {
        sql.eachRow("SELECT * FROM authorized_users WHERE active = TRUE") { row ->
            result.add([employeeId: row.employee_id, fullName: row.full_name])
        }
    } finally {
        sql.close()
    }
    return success([userList: result, userCount: result.size()])
}
return myService()
```

### Service definition (correct)
```xml
<service name="myService" engine="groovy"
    location="component://ftm-wifi-enrollment/groovyScripts/ftm/wifi/MyService.groovy"
    invoke="myService" auth="false">
    <description>Description here</description>
    <attribute name="employeeId" type="String" mode="IN" optional="true"/>
    <!-- NO description= attribute on <attribute> elements -->
    <attribute name="userList" type="List" mode="OUT" optional="false"/>
</service>
```

### entitymodel entry (external DB, correct)
```xml
<entity entity-name="FtmAuthorizedUser"
    package-name="org.apache.ofbiz.ftm.wifi"
    table-name="authorized_users"
    no-auto-stamp="true"
    never-cache="true">
    <field name="employeeId" type="short-varchar" col-name="employee_id"/>
    <prim-key field="employeeId"/>
</entity>
```

## OFBiz Restart Commands

```bash
cd /opt/ofbiz-framework
pkill -f "ofbiz.base.start.Start"; sleep 5
./gradlew ofbiz &

# Load seed data (after cleanAll)
./gradlew cleanAll "ofbiz --load-data readers=seed,seed-initial"
./gradlew loadAdminUserLogin -PuserLoginId=admin
```

## Git Workflow (ofbiz-dev)

```bash
cd /opt/ofbiz-plugins
git add ftm-wifi-enrollment/[changed files]
git commit -m "fix/feat(phase7): description"
git push --no-verify origin feature/ftm-wifi-enrollment

cd /opt/ofbiz-framework
git add framework/entity/config/entityengine.xml framework/entity/ofbiz-component.xml
git commit -m "fix(phase7): description"
git push --no-verify origin feature/ftm-enrollment-datasource
```

## What Requires Restart vs Hot-Reload

| Change | Restart needed? |
|--------|----------------|
| Groovy service script | No — reloads on next request |
| Screen/Form XML | No — reloads on next request |
| Service definition XML | No — reloads on next request |
| controller.xml | No — reloads on next request |
| web.xml | YES |
| ofbiz-component.xml | YES |
| entitymodel XML | YES |
| entityengine.xml | YES |
| Java source code | YES + rebuild |

---

## Updates from Phase 7 Debug Session (Apr 2026)

### Additional Groovy Gotchas

**GStringImpl SQL type error** — Always cast OFBiz form parameters to `.toString()` before JDBC:
```groovy
// WRONG — GStringImpl causes PSQLException
stmt.setString(1, parameters.employeeId.trim())

// CORRECT
stmt.setString(1, parameters.employeeId.toString().trim())
stmt.setString(4, parameters.department?.toString() ?: null)
```

**Screen actions — no conditional service calls** — `<if-not-empty>` is NOT valid inside screen `<actions>`. Use a Groovy script instead:
```xml
<!-- WRONG — will fail if employeeId is null -->
<service service-name="getUser" result-map="r">
    <field-map field-name="employeeId" from-field="parameters.employeeId"/>
</service>

<!-- CORRECT — use script that handles null gracefully -->
<script location="component://ftm-wifi-enrollment/groovyScripts/ftm/wifi/EditUserActions.groovy"/>
```

**Screen action script** sets context variables directly:
```groovy
// In screen action Groovy — set context directly, no return needed
context.user = [employeeId: "FTM001", fullName: "Fan CEO"]
context.titleProperty = "Edit User"
// No return statement needed in screen action scripts
```

### autossh / SSH Tunnel for Starlink

**Use `-C` flag for compression** on high-latency Starlink connections:
```bash
# Correct command — nohup + -C compression + bind on specific interface
nohup ssh -N -C \
    -o "ServerAliveInterval 30" \
    -o "ServerAliveCountMax 3" \
    -o "ExitOnForwardFailure yes" \
    -L 192.168.30.3:11434:localhost:11434 \
    texchi@192.168.192.79 &
```

**autossh `-f` flag does NOT bind to non-loopback** — avoid `-f` with specific interface binding; use `nohup ... &` instead.

### mainDecoratorLocation — Infinite Recursion

The `web.xml` `mainDecoratorLocation` MUST NOT point to a file that contains a `main-decorator` screen which calls `${parameters.mainDecoratorLocation}` — this causes infinite recursion (1400+ identical section comments in output).

**Solution**: Create a separate `FtmWifiCommonScreens.xml` with `main-decorator` → `ApplicationDecorator` (commonext), and set `mainDecoratorLocation` to point to this file.

### Claude Code + Ollama (Jan 2026+)

Correct environment variable setup — no `--ollama-base-url` flag:
```bash
export ANTHROPIC_AUTH_TOKEN="ollama"
export ANTHROPIC_API_KEY=""
export ANTHROPIC_BASE_URL="http://192.168.30.3:11434"
claude --model llama3.3-agent:latest
```

### OFBiz file download pattern 
use <link> in Screen XML (not <hyperlink> in Form XML) with a standalone hidden form. The <link> element in a screen widget creates a separate form context that browsers handle as a downloadable response, while <hyperlink> inside an existing form gets nested and blocked.


