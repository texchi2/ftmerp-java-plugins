# OpenWolf

@.wolf/OPENWOLF.md

This project uses OpenWolf for context management. Read and follow .wolf/OPENWOLF.md every session. Check .wolf/cerebrum.md before generating code. Check .wolf/anatomy.md before reading files.


# FTM OFBiz Development

## Model
llama3.3-agent:latest via http://192.168.30.3:11434

## Rules
1. /doc-first: fetch OFBiz XSD before writing XML
2. External DB entities: no-auto-stamp="true"
3. Groovy: use groovy.sql.Sql, return success([...])
4. Screen XML: xmlns= + xsi:schemaLocation= required
5. controller.xml: request-maps BEFORE view-maps
6. Restart required for: web.xml, entitymodel, ofbiz-component.xml

## Paths
- Plugin: /opt/ofbiz-plugins/ftm-wifi-enrollment/
- Framework: /opt/ofbiz-framework/
- Logs: /opt/ofbiz-framework/runtime/logs/ofbiz.log
- UI: http://192.168.30.102:8080 (admin/ofbiz)

## Restart OFBiz
pkill -f "ofbiz.base.start.Start"; sleep 5
cd /opt/ofbiz-framework && ./gradlew ofbiz &
