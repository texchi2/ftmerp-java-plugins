# Phase 8A Instruction for Claude Code / gemma4

## FTM Garments ERP — OFBiz Core First Strategy

**Reference**: https://ofbiz.apache.org/developers.html
**Dev Tutorial**: https://cwiki.apache.org/confluence/display/OFBIZ/OFBiz+Tutorial+-+A+Beginners+Development+Guide
**Dev Manual**: https://nightlies.apache.org/ofbiz/stable/ofbiz/html5/developer-manual.html
**User Manual**: https://nightlies.apache.org/ofbiz/trunk/ofbiz/html5/user-manual.html

-----

## STRATEGIC CONTEXT (read before acting)

FTM Garments Swaziland is an OEM garment factory (Ladies/Kids/Men’s apparel, ISO 9001).
Locations: HQ (Nhlangano), factories JJ1/JJ2/JJ3, Durban branch office.

**Strategy: OFBiz core-first.** Apache OFBiz trunk already contains:

- Party Manager (customers, suppliers, employees)
- Order Manager (sales orders, purchase orders)
- Catalog (products, categories, BOM via ProductAssoc)
- Manufacturing (work efforts, production runs)
- Facility (inventory, warehouses)
- Accounting (invoices, payments, general ledger)
- HR (employees, departments)
- Scrum/Project Manager

**FTM adds custom plugins ONLY when OFBiz core is insufficient:**

- `ftm-wifi-enrollment` — EAP-TLS WiFi auth (no OFBiz core equivalent) ✅ DONE
- `ftm-garments` — Style# registry + garment-specific production tracking (extends core) 🔄 Phase 8A

**Database strategy:**

- OFBiz core entities → Derby (default) or PostgreSQL (production)
- FTM custom entities in `ftm-garments` → OFBiz entity engine (Derby/PostgreSQL via entitymodel.xml)
- WiFi enrollment only → separate PostgreSQL schema `ftm_enrollment` (standalone, no OFBiz entity engine)

-----

## MANDATORY FIRST STEPS (doc-first — no exceptions)

```
Step 1: Read FTM developer manual and patterns from Phase 7:
  cat /opt/ofbiz-plugins/docs/asciidoc/ftm-developer-manual.adoc
  cat /opt/ofbiz-plugins/.wolf/cerebrum.md

Step 2: Understand the existing ftm-garments prototype (built by Claude Code Sonnet, NEVER run):
  cat /opt/ofbiz-plugins/ftm-garments/ofbiz-component.xml.disabled
  cat /opt/ofbiz-plugins/ftm-garments/entitydef/entitymodel.xml
  cat /opt/ofbiz-plugins/ftm-garments/servicedef/services.xml
  cat /opt/ofbiz-plugins/ftm-garments/data/FtmGarmentsTypeData.xml
  cat /opt/ofbiz-plugins/ftm-garments/data/FtmWorkflowData.xml
  ls -R /opt/ofbiz-plugins/ftm-garments/webapp/

Step 3: Explore OFBiz core applications that FTM will use:
  ls /opt/ofbiz-framework/applications/
  cat /opt/ofbiz-framework/applications/order/servicedef/services.xml | grep -A5 "createSalesOrder\|createOrder" | head -30
  cat /opt/ofbiz-framework/applications/product/entitydef/entitymodel_product.xml | grep -A8 "entity-name=\"Product\"" | head -20
  cat /opt/ofbiz-framework/applications/manufacturing/servicedef/services.xml | grep -A5 "createProductionRun" | head -20
```

-----

## WHAT EXISTS IN ftm-garments PROTOTYPE

```
ftm-garments/ (on main branch — prototype, never deployed)
├── build.gradle                    ← pluginLibsCompile (review dependencies)
├── ofbiz-component.xml.disabled    ← has typo "vereion", rename + fix to enable
├── entitydef/entitymodel.xml       ← ONE entity: FtmStyleNumber (id, styleNumber,
│                                      description, productCategory, season, dates)
├── servicedef/services.xml         ← createFtmStyleNumber (entity-auto — simplest possible)
├── data/
│   ├── FtmGarmentsTypeData.xml     ← SecurityPermissions (FTM_GARMENTS_ADMIN/CREATE/
│   │                                  UPDATE/VIEW) + ProductCategory (CASUAL_PANTS,
│   │                                  CASUAL_SHIRTS)
│   ├── FtmWorkflowData.xml         ← Product FTM-PANT-CASUAL-32X32-NAVY + BOM via
│   │                                  ProductAssoc (MANUF_COMPONENT, quantity=1.8)
│   └── FtmGarmentsDemoData.xml     ← empty placeholder
└── webapp/ftm-garments/            ← EMPTY — needs WEB-INF/
```

-----

## FTM GARMENT WORKFLOW → OFBiz CORE MAPPING

```
FTM Process              OFBiz Core App    Key Entity/Service
─────────────────────────────────────────────────────────────
Buyer/Supplier mgmt   → Party Manager    Party, PartyRole, ContactMech
Style/Product catalog → Catalog          Product, ProductCategory
BOM (fabric+trims)    → Catalog/Mfg      ProductAssoc (MANUF_COMPONENT)
Sales Order           → Order Manager    OrderHeader, OrderItem
Purchase Order        → Order Manager    OrderHeader (PURCHASE_ORDER)
MRP                   → Manufacturing    MrpEvent, ProposedOrderWorkEffort
Production Run        → Manufacturing    WorkEffort, ProductionRun
Factory/Line tracking → Facility         Facility (JJ1/JJ2/JJ3), WorkEffort
QC Inspection         → Manufacturing    WorkEffortPartyAssignment
Inventory             → Facility         InventoryItem, InventoryItemDetail
Packing/Shipment      → Facility/Order   Shipment, ShipmentItem
Invoice               → Accounting       Invoice, InvoiceItem
Payment               → Accounting       Payment, PaymentApplication
GL/Finance            → Accounting       GlAccount, AcctgTrans

Custom (ftm-garments plugin):
Style# ↔ Buyer link   → FtmStyleNumber   (extends Product with garment metadata)
Color/dye lot track   → FtmColorMatch    (future — ladies' trouser quality)
Daily output/line     → FtmLineOutput    (future — JJ1/JJ2/JJ3 production KPIs)
```

-----

## PHASE 8A TASKS (in strict order)

### Task 1 — Create feature branch

```bash
cd /opt/ofbiz-plugins
git fetch origin
git checkout -b feature/ftm-garments origin/main
```

### Task 2 — Fix and enable ofbiz-component.xml

```bash
cp ftm-garments/ofbiz-component.xml.disabled ftm-garments/ofbiz-component.xml
```

Fix typo (`vereion` → `version`) and update to match Phase 7 patterns:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<ofbiz-component name="ftm-garments"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:noNamespaceSchemaLocation="http://ofbiz.apache.org/dtds/ofbiz-component.xsd">

    <resource-loader name="main" type="component"/>
    <classpath type="dir" location="config"/>
    <classpath type="jar" location="build/lib/*"/>

    <!-- OFBiz entity engine (Derby/PostgreSQL via entity engine) -->
    <entity-resource type="model"  reader-name="main" loader="main"
        location="entitydef/entitymodel.xml"/>
    <entity-resource type="data"   reader-name="seed" loader="main"
        location="data/FtmGarmentsTypeData.xml"/>
    <entity-resource type="data"   reader-name="ext"  loader="main"
        location="data/FtmWorkflowData.xml"/>

    <!-- Services -->
    <service-resource type="model" loader="main" location="servicedef/services.xml"/>

    <!-- Web application -->
    <webapp name="ftm-garments"
        title="FTM Garments Management"
        server="default-server"
        location="webapp/ftm-garments"
        base-permission="OFBTOOLS,FTM_GARMENTS_VIEW"
        mount-point="/ftm-garments"/>
</ofbiz-component>
```

### Task 3 — Extend FtmStyleNumber entity

Add missing fields to entitydef/entitymodel.xml — keep existing fields, ADD:

```xml
<entity entity-name="FtmStyleNumber" package-name="com.ftm.garments"
    title="FTM Style Number" author="FTM Garments" version="1.0">
    <field name="styleNumberId"    type="id"/>
    <field name="styleNumber"      type="short-varchar"/>
    <field name="buyer"            type="short-varchar"/>    <!-- ADD -->
    <field name="description"      type="description"/>
    <field name="productType"      type="short-varchar"/>    <!-- PANTS/SHIRT/JACKET/DRESS -->
    <field name="productCategory"  type="short-varchar"/>
    <field name="season"           type="short-varchar"/>
    <field name="status"           type="short-varchar"/>    <!-- ACTIVE/SAMPLING/INACTIVE -->
    <field name="productId"        type="id"/>               <!-- FK to OFBiz Product -->
    <field name="createdDate"      type="date-time"/>
    <field name="lastModifiedDate" type="date-time"/>
    <prim-key field="styleNumberId"/>
    <relation type="one" rel-entity-name="Product" fk-name="FTM_STYNO_PRD">
        <key-map field-name="productId"/>
    </relation>
</entity>
```

### Task 4 — Update services.xml with Groovy services

Replace `entity-auto` with Groovy services for flexibility:

```xml
<!-- Keep entity-auto createFtmStyleNumber for simple creates -->
<!-- ADD Groovy service for list/find -->
<service name="getFtmStyles" engine="groovy"
    location="component://ftm-garments/groovyScripts/ftm/garments/GetStyles.groovy"
    invoke="getFtmStyles" auth="true">
    <description>Find FTM Style Numbers with filters</description>
    <attribute name="buyer"        type="String"  mode="IN" optional="true"/>
    <attribute name="productType"  type="String"  mode="IN" optional="true"/>
    <attribute name="status"       type="String"  mode="IN" optional="true"/>
    <attribute name="styleList"    type="List"    mode="OUT" optional="true"/>
    <attribute name="styleCount"   type="Integer" mode="OUT" optional="true"/>
</service>
```

### Task 5 — Create GetStyles.groovy using OFBiz delegator

**IMPORTANT: Use OFBiz entity engine (delegator), NOT direct JDBC.**
FtmStyleNumber is an OFBiz entity → use delegator for queries.

```groovy
// groovyScripts/ftm/garments/GetStyles.groovy
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator

def getFtmStyles() {
    def conditions = []
    if (parameters.buyer)
        conditions << EntityCondition.makeCondition("buyer",
            org.apache.ofbiz.entity.condition.EntityOperator.LIKE,
            "%" + parameters.buyer + "%")
    if (parameters.productType)
        conditions << EntityCondition.makeCondition("productType", parameters.productType)
    if (parameters.status)
        conditions << EntityCondition.makeCondition("status", parameters.status)

    def cond = conditions ?
        EntityCondition.makeCondition(conditions, EntityOperator.AND) : null

    def styleList = delegator.findList("FtmStyleNumber", cond, null,
        ["styleNumber"], null, false)

    return success([styleList: styleList, styleCount: styleList.size()])
}
return getFtmStyles()
```

### Task 6 — Create webapp skeleton (copy from ftm-wifi-enrollment)

```
webapp/ftm-garments/WEB-INF/
├── web.xml          ← copy ftm-wifi-enrollment/webapp/ftm-wifi/WEB-INF/web.xml,
│                      change display-name and servlet-name to ftm-garments
└── controller.xml   ← minimal: main → FindStyles view
```

**controller.xml** minimal:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<site-conf xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:noNamespaceSchemaLocation="http://ofbiz.apache.org/dtds/site-conf.xsd">

    <include location="component://common/webcommon/WEB-INF/common-controller.xml"/>
    <description>FTM Garments Management</description>
    <owner>FTM Garments Swaziland (PTY) LTD</owner>

    <request-map uri="main">
        <security https="false" auth="true"/>
        <response name="success" type="view" value="FindStyles"/>
    </request-map>

    <request-map uri="FindStyles">
        <security https="false" auth="true"/>
        <response name="success" type="view" value="FindStyles"/>
    </request-map>

    <view-map name="FindStyles" type="screen"
        page="component://ftm-garments/widget/FtmGarmentsScreens.xml#FindStyles"/>
</site-conf>
```

### Task 7 — Create FindStyles screen + form (follow Phase 7 pattern EXACTLY)

**widget/FtmGarmentsMenus.xml** — tab bar: Style List | Add Style

**widget/FtmGarmentsScreens.xml**:

```xml
<screen name="FindStyles">
    <section>
        <actions>
            <service service-name="getFtmStyles" result-map="serviceResult">
                <field-map field-name="buyer"       from-field="parameters.buyer"/>
                <field-map field-name="productType" from-field="parameters.productType"/>
                <field-map field-name="status"      from-field="parameters.status"/>
            </service>
            <set field="styleList"  from-field="serviceResult.styleList"/>
            <set field="styleCount" from-field="serviceResult.styleCount"/>
        </actions>
        <widgets>
            <decorator-screen name="main-decorator"
                location="${parameters.mainDecoratorLocation}">
                <decorator-section name="body">
                    <screenlet title="FTM Styles (${styleCount} total)">
                        <include-form name="FindStylesFilter"
                            location="component://ftm-garments/widget/FtmGarmentsForms.xml"/>
                    </screenlet>
                    <screenlet title="Style List">
                        <include-form name="StyleList"
                            location="component://ftm-garments/widget/FtmGarmentsForms.xml"/>
                    </screenlet>
                </decorator-section>
            </decorator-screen>
        </widgets>
    </section>
</screen>
```

NOTE: `result-map="serviceResult"` puts results as `serviceResult.styleList` —
BUT `<set field="styleList" from-field="serviceResult.styleList"/>` bridges it to screen context.
This is the CORRECT pattern from OFBiz core (used in ordermgr, catalog, etc.)

**widget/FtmGarmentsForms.xml**:

```xml
<form name="FindStylesFilter" type="single" target="FindStyles">
    <field name="buyer" title="Buyer"><text-find/></field>
    <field name="productType" title="Product Type">
        <drop-down allow-empty="true">
            <option key="" description="-- All --"/>
            <option key="PANTS"   description="Pants/Trousers"/>
            <option key="SHIRT"   description="Shirts"/>
            <option key="JACKET"  description="Jackets"/>
            <option key="DRESS"   description="Dresses"/>
        </drop-down>
    </field>
    <field name="status" title="Status">
        <drop-down allow-empty="true">
            <option key=""        description="-- All --"/>
            <option key="ACTIVE"  description="Active"/>
            <option key="SAMPLING" description="Sampling"/>
            <option key="INACTIVE" description="Inactive"/>
        </drop-down>
    </field>
    <field name="searchButton" title="Search">
        <submit button-type="button"/>
    </field>
</form>

<form name="StyleList" type="list" list-name="styleList"
    default-table-style="basic-table hover-bar">
    <field name="styleNumber"   title="Style No"><display/></field>
    <field name="buyer"         title="Buyer">   <display/></field>
    <field name="description"   title="Description"><display/></field>
    <field name="productType"   title="Type">    <display/></field>
    <field name="season"        title="Season">  <display/></field>
    <field name="status"        title="Status">  <display/></field>
</form>
```

### Task 8 — Load data and test

```bash
# Restart OFBiz (required: new component, new entity)
cd /opt/ofbiz-framework
pkill -f "ofbiz.base.start.Start"; sleep 5
./gradlew --stop; sleep 3

# Load seed data (creates FtmStyleNumber table + security permissions)
./gradlew "ofbiz --load-data readers=seed,ext" &
sleep 90

# Test login
curl -s -c /tmp/garments.jar \
    -d "USERNAME=admin&PASSWORD=ofbiz&requirePasswordChange=N" \
    "http://192.168.30.102:8080/ftm-garments/control/login" | \
    grep -i "error\|FindStyle\|302\|200" | head -3

# Test FindStyles
curl -s -b /tmp/garments.jar \
    "http://192.168.30.102:8080/ftm-garments/control/FindStyles" | \
    grep -i "Style No\|FTM Styles\|error\|exception" | head -5

# Check OFBiz log
tail -20 /opt/ofbiz-framework/runtime/logs/ofbiz.log | \
    grep -E "ERROR|Exception|ftm-garments|FtmStyle" | head -10
```

### Task 9 — Commit

```bash
cd /opt/ofbiz-plugins
git add ftm-garments/
git commit -m "feat(phase8a): activate ftm-garments plugin, FindStyles screen

- ofbiz-component.xml enabled (fixed typo vereion→version)
- FtmStyleNumber entity: added buyer, productType, status, productId fields
- getFtmStyles Groovy service using OFBiz delegator (entity engine)
- webapp/WEB-INF/controller.xml + web.xml created
- FindStyles screen + FindStylesFilter + StyleList forms
- Seed data: SecurityPermissions, ProductCategory loaded"

git push --no-verify origin feature/ftm-garments
```

-----

## KEY PATTERNS FROM PHASE 7 (DO NOT REPEAT MISTAKES)

```
1. Service result bridge: <set field="styleList" from-field="serviceResult.styleList"/>
   (NOT from-field="getFtmStyles.styleList" — that only works for request attributes)
   When using result-map="serviceResult" in screen actions, results are in serviceResult.*

2. <submit button-type="button"/> — NOT <submit><option.../></submit> (XSD violation)

3. use-when conditions: Groovy syntax (&&, ||) — NOT miniLang (and, or)

4. Delegator for OFBiz entities (FtmStyleNumber → Derby):
   delegator.findList("FtmStyleNumber", cond, null, ["styleNumber"], null, false)

5. Direct groovy.sql.Sql ONLY for non-OFBiz PostgreSQL schemas (ftm_enrollment.*)

6. @groovy.transform.Field for script-level vars used inside methods

7. Restart required for: new entities, ofbiz-component.xml changes, new services

8. File download: screen <link> widget NOT form <hyperlink>

9. File upload: type="groovy" event, reads multiPartMap from request attribute

10. No enctype= on upload forms, no confirmation= on hyperlinks

11. Screen context bridge (if needed): use a Groovy screen action script
    context.x = request.getAttribute("x") ?: []
```

-----

## SUCCESS CRITERIA FOR PHASE 8A

```
[ ] git checkout -b feature/ftm-garments created from origin/main
[ ] ofbiz-component.xml enabled and typo fixed
[ ] webapp/ftm-garments/WEB-INF/controller.xml + web.xml created
[ ] OFBiz starts without ERROR in ofbiz.log
[ ] FtmStyleNumber table created in Derby (check via WebTools Entity Data Maintenance)
[ ] SecurityPermissions loaded: FTM_GARMENTS_ADMIN/CREATE/UPDATE/VIEW
[ ] /ftm-garments/control/main redirects to FindStyles (HTTP 200)
[ ] FindStyles screen renders with filter form and empty table
[ ] curl test returns no exception
[ ] git commit on feature/ftm-garments
```

## DO NOT IMPLEMENT IN PHASE 8A

- CreateStyle / EditStyle / DeleteStyle (Phase 8B)
- ProductionOrders / DailyOutput (Phase 8C)
- BOM management screens (Phase 8D)
- Purchase Order integration (Phase 8E)
- Manufacturing workflow (Phase 8F)
- Finance/accounting (Phase 8G)
