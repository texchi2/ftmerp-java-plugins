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
# stop all daemon
./gradlew --no-daemon terminateOfbiz

# Stop OFBiz cleanly
# Kill ALL Java processes cleanly
pkill -f "ofbiz.base.start.Start" 2>/dev/null
pkill -f "GradleWrapperMain" 2>/dev/null
sleep 3
# Force kill any remaining Java
kill -9 $(ps aux | grep java | grep -v grep | awk '{print $2}') 2>/dev/null
sleep 3

# Verify all Java gone
ps aux | grep java | grep -v grep
# Should return nothing
# verify
grep "Started Apache Tomcat" runtime/logs/ofbiz.log | tail -2
# start ofbiz
cd /opt/ofbiz-framework && ./gradlew ofbiz &

## Browser Testing
Use agent-browser (not Playwright) for all OFBiz UI testing.
Always run `agent-browser skills get agent-browser` before browser tasks.
OFBiz URL: http://192.168.30.102:8080
Login: admin / ofbiz
After login, navigate to target screens and check for ERROR MESSAGE text.

## 
