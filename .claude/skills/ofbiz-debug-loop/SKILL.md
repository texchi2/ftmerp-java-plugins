---
name: ofbiz-debug-loop
description: >
  Autonomous OFBiz fix-and-verify loop. Use when fixing OFBiz screen/service
  errors. Runs until no errors remain, then reports to user for confirmation.
---

# OFBiz Debug Loop

## When to Use
Any OFBiz XML/Groovy fix where the result must be verified in browser,
not just by file inspection or curl.

## Workflow

### Phase 1: Diagnose
1. Read error from ofbiz.log:
   `tail -100 /opt/ofbiz-framework/runtime/logs/ofbiz.log | grep -A10 "ERROR\|Exception"`
2. Identify which file/line is failing
3. Web search or /doc-first if error pattern is unknown

### Phase 2: Fix
1. Read the broken file completely before editing
2. Apply minimal targeted fix
3. Never use sed for multi-line XML — use Write tool

### Phase 3: Verify (hot-reload check)
1. Curl test (fast):
   ```bash
   curl -s -b /tmp/ftmg-cookie.txt -o /dev/null -w "%{http_code}" \
       "http://localhost:8080/ftm-garments/control/[SCREEN]"


	2.	If restart needed (ofbiz-component.xml, web.xml, entitymodel.xml):

pkill -f "ofbiz.base.start.Start"; sleep 8
rm -f /opt/ofbiz-framework/runtime/data/derby/ofbiz/dbex.lck
rm -f /opt/ofbiz-framework/runtime/data/derby/ofbiz/db.lck
./gradlew ofbiz > /tmp/ofbiz-debug.log 2>&1 &
sleep 90


	3.	Browser check via Playwright MCP:

Navigate to http://localhost:8080/ftm-garments/control/login
Fill USERNAME=admin, PASSWORD=ofbiz, submit
Navigate to http://localhost:8080/ftm-garments/control/[SCREEN]
Check page for "ERROR MESSAGE" text
Take screenshot if error found


### Phase 4: Loop or Checkpoint
	•	If ERROR MESSAGE found → back to Phase 1
	•	If page renders correctly → proceed to checkpoint
Checkpoint (report to user)

✅ [SCREEN] renders without errors.
Request time: [X]ms (check log)
Test command for you to confirm:
  Firefox: http://192.168.30.102:8080/ftm-garments/control/[SCREEN]
  Login: admin/ofbiz
Ready to commit? (git add ftm-garments/ && git commit -m "fix: ...")


Key Rules
	•	XML files: hot-reload (no restart)
	•	web.xml, ofbiz-component.xml, entitymodel.xml: restart required
	•	Always clear browser cookies between tests
	•	Max 3 fix attempts per error before asking user for guidance
	•	Request time must stay under 5 seconds (check ControlServlet log)
