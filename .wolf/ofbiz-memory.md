# FTM OFBiz Dev Memory Index

## Known Bugs (Do-Not-Repeat)
- GStringImpl: parameters.x must be .toString() before JDBC setString()
- context.response: ONLY correct way to get HttpServletResponse in Groovy
- return "success": string not map for type="none" controller response
- vlanWarning: declare BEFORE try block, not inside it
- Login context: ftm-wifi/control/login for ftm-wifi endpoints (not webtools)
- PARAMETER think: does NOT exist in Modelfile — it's an API request field

## Key File Locations
- Groovy: /opt/ofbiz-plugins/ftm-wifi-enrollment/groovyScripts/ftm/wifi/
- Screens: /opt/ofbiz-plugins/ftm-wifi-enrollment/widget/
- Controller: /opt/ofbiz-plugins/ftm-wifi-enrollment/webapp/ftm-wifi/WEB-INF/controller.xml
- PostgreSQL: 192.168.30.3:5432/ftm_enrollment (enrolladmin)
- Logs: /opt/ofbiz-framework/runtime/logs/ofbiz.log

## Phase 7 Status
- FindAuthorizedUsers: WORKING (11 users)
- Add/Edit/Update User: WORKING with audit log
- Export CSV: WORKING (12 lines)
- Import Excel: PENDING
- Deactivate User: PENDING test
