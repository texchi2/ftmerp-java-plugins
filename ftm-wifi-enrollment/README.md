# ftm-wifi-enrollment — OFBiz Hot-Deploy Component
**FTM Garments Swaziland (PTY) LTD**
Phase 7: HR/IT management of WiFi certificate enrollment authorization

---

## Architecture

```
OFBiz (rpitex 192.168.30.129)
  └── ftm-wifi-enrollment component
        └── ftmEnrollment delegator
              └── ftmEnrollmentDataSource
                    └── PostgreSQL ftm_enrollment (192.168.30.3:5432)
                          ├── authorized_users    ← managed here (shared with Flask MDM)
                          └── ftm_wifi_audit_log  ← created by this component
```

The Flask MDM portal at `https://mdm.ftm.arpa` continues to own `enrolled_devices`
and `it_admins`. OFBiz only manages `authorized_users` and the new audit table.

---

## Pre-deployment checklist

### 1. Create audit table on Ubuntu host (192.168.30.3)

```bash
psql -h 192.168.30.3 -U enrolladmin -d ftm_enrollment -c "
CREATE TABLE IF NOT EXISTS ftm_wifi_audit_log (
    id           SERIAL PRIMARY KEY,
    changed_by   VARCHAR(50),
    changed_at   TIMESTAMP DEFAULT NOW(),
    employee_id  VARCHAR(20),
    field_name   VARCHAR(50),
    old_value    TEXT,
    new_value    TEXT,
    action       VARCHAR(20)
);
GRANT SELECT, INSERT ON ftm_wifi_audit_log TO enrolladmin;
GRANT USAGE, SELECT ON SEQUENCE ftm_wifi_audit_log_id_seq TO enrolladmin;"
```

### 2. Verify PostgreSQL JDBC driver on rpitex

```bash
ls /home/texchi/ofbiz/framework/entity/lib/jdbc/postgresql-*.jar
# If missing:
wget https://jdbc.postgresql.org/download/postgresql-42.7.3.jar \
  -O /home/texchi/ofbiz/framework/entity/lib/jdbc/postgresql-42.7.3.jar
```

### 3. Patch entityengine.xml on rpitex

Edit `/home/texchi/ofbiz/framework/entity/config/entityengine.xml`:

**Add delegator** (after the closing `</delegator>` of the "default" delegator):
```xml
<delegator name="ftmEnrollment"
    entity-model-reader="main"
    entity-group-reader="main"
    entity-eca-reader="main">
    <group-map group-name="ftmEnrollment" datasource-name="ftmEnrollmentDataSource"/>
</delegator>
```

**Add datasource** (after the last existing `</datasource>`):
```xml
<datasource name="ftmEnrollmentDataSource"
    helper-class="org.apache.ofbiz.entity.datasource.GenericHelperDAO"
    field-type-name="postgres"
    check-on-start="false"
    add-missing-on-start="false"
    use-foreign-keys="false"
    use-pk-constraint-names="false"
    join-style="ansi"
    alias-view-columns="false">
    <inline-jdbc
        jdbc-driver="org.postgresql.Driver"
        jdbc-uri="jdbc:postgresql://192.168.30.3:5432/ftm_enrollment"
        jdbc-username="enrolladmin"
        jdbc-password="ENROLLADMIN_PASS"
        isolation-level="ReadCommitted"
        pool-minsize="2"
        pool-maxsize="10"
        time-between-eviction-runs-millis="600000"/>
</datasource>
```

Replace `ENROLLADMIN_PASS` with the actual password.

---

## Deployment

### From ltsp-rpi4b256 (dev machine) to rpitex (production)

```bash
# 1. Copy component to rpitex hot-deploy directory
scp -r /home/texchi/ofbiz/hot-deploy/ftm-wifi-enrollment \
    texchi@192.168.30.129:/home/texchi/ofbiz/hot-deploy/

# 2. Set PostgreSQL password (on rpitex)
ssh texchi@192.168.30.129
vim /home/texchi/ofbiz/framework/entity/config/entityengine.xml
# Replace ENROLLADMIN_PASS

# 3. Restart OFBiz on rpitex
cd /home/texchi/ofbiz
./gradlew ofbiz 2>&1 | tee /tmp/ofbiz-start.log &

# 4. Verify component loaded
tail -f runtime/logs/ofbiz.log | grep -E "ftm-wifi|ftmEnrollment|ERROR"
```

### Verify deployment success

```bash
# Component loaded
grep "ftm-wifi-enrollment" runtime/logs/ofbiz.log | grep -i "loaded\|start"

# Datasource connected
grep "ftmEnrollmentDataSource" runtime/logs/ofbiz.log | grep -v "DEBUG"

# No entity errors
grep "FtmAuthorizedUser\|FtmWifiAuditLog" runtime/logs/ofbiz.log
```

### Access

```
https://rpitex:443/ftm-wifi/control/FindAuthorizedUsers
```

Login with existing OFBiz credentials (requires OFBTOOLS permission).

---

## File structure

```
ftm-wifi-enrollment/
├── ofbiz-component.xml               Component registration
├── entityengine_patch.xml            Datasource/delegator — add to main entityengine.xml
├── entitydef/
│   ├── entitymodel_ftm_wifi.xml      FtmAuthorizedUser, FtmWifiAuditLog
│   └── entitygroup_ftm_wifi.xml      Entity→datasource group mapping
├── servicedef/
│   └── services_ftm_wifi.xml         6 service definitions
├── groovyScripts/ftm/wifi/
│   ├── GetAuthorizedUsers.groovy     List with filters
│   ├── CreateAuthorizedUser.groovy   Create + audit
│   ├── UpdateAuthorizedUser.groovy   Update + VLAN warning + audit
│   ├── DeactivateUser.groovy         Deactivate + FreeRADIUS reminder
│   ├── GetEnrollmentCount.groovy     Count enrolled devices (read-only)
│   └── ImportUsersFromExcel.groovy   Apache POI import with preview
├── webapp/ftm-wifi/
│   └── WEB-INF/
│       ├── web.xml
│       └── controller.xml            All request routes
├── widget/
│   ├── FtmWifiScreens.xml            Screen definitions
│   ├── FtmWifiForms.xml              Form widgets
│   └── FtmWifiMenus.xml              Navigation menu
└── data/
    └── GenerateSampleExcel.groovy    Standalone script to generate sample .xlsx
```

---

## Key constraints

- **Groovy only** — no `.java` files (hot-deploy on ARM64 JDK 21)
- **External delegator** — all DB ops via `ftmEnrollment` delegator, never default
- **Read-only tables** — `enrolled_devices` and `it_admins` are never touched
- **Flask portal** — `/opt/ftm-enrollment/` files are never modified
- **FreeRADIUS revocation** — must still be done manually on pfSense after deactivation
- **VLAN change** — changing `ftm_staff_vlan10` requires device re-enrollment and cert revocation

---

## Troubleshooting

| Symptom | Check |
|---------|-------|
| `ftmEnrollmentDataSource` not found | entityengine.xml patch not applied or OFBiz not restarted |
| `FtmAuthorizedUser` entity not found | ofbiz-component.xml not loaded — check hot-deploy dir |
| PostgreSQL connection refused | Firewall between rpitex and 192.168.30.3, or wrong password |
| Excel import fails | Verify `poi-ooxml-*.jar` on OFBiz classpath |
| VLAN warning not showing | Check `vlanWarning` field in screen — requires UpdateAuthorizedUser response |
