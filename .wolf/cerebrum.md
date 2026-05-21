# Cerebrum

> OpenWolf's learning memory. Updated automatically as the AI learns from interactions.
> Do not edit manually unless correcting an error.
> Last updated: 2026-04-05

## User Preferences

<!-- How the user likes things done. Code style, tools, patterns, communication. -->

## Key Learnings

- **Project:** ofbiz-plugins
- **Description:** Apache OFBiz is an open source product for the automation of enterprise processes.

## Do-Not-Repeat

<!-- Mistakes made and corrected. Each entry prevents the same mistake recurring. -->
<!-- Format: [YYYY-MM-DD] Description of what went wrong and what to do instead. -->

## Decision Log

<!-- Significant technical decisions with rationale. Why X was chosen over Y. -->

## Do-Not-Repeat (OFBiz-Specific)
- Never use runEvent() — does not exist in OFBiz Groovy
- Never use closure-based HttpServletResponse — use context.response
- Never return success([response: closure]) — return "success" string for type=none
- Never login to webtools then test ftm-wifi endpoints — different session context
- Never add PARAMETER think to Modelfile — think is an API field not Modelfile param
- Always .toString() on ALL JDBC setString() parameters
- Always declare variables before try block if used after finally
- [2026-05-21] NEVER use `result.X = Y` in OFBiz Groovy services — `result` is NOT pre-populated in the binding. GroovyEngine only puts dispatcher/delegator/parameters in gContext. Script-body services (no invoke=): use `return [responseMessage:"success", outKey: value]`. Method-invoke services (invoke=method): return the map from the method. OFBiz uses the script/method return value directly as service result.
- [2026-05-21] FTM_ENROLLMENT_DB_PASS env var must be set for ofbiz-dev JVM — add to /etc/environment in container AND start OFBiz with `env FTM_ENROLLMENT_DB_PASS=... ./gradlew ofbiz`

## Do-Not-Repeat (File Download Pattern)
[2026-4-5]
- NEVER use <hyperlink> inside a Form widget for file downloads - generates JS form submit that browsers block
- ALWAYS use <link> in Screen widget (FtmWifiScreens.xml) for file downloads
- <link> creates a standalone hidden form that browsers handle correctly as download
- Example: <link text="Export CSV" target="ExportAuthorizedUsersCsv" style="buttontext" url-mode="intra-app"/>
- OFBiz <form type="single"> always generates method="post" with JS submit - never use for downloads


## Agent Team Configuration (Apr 2026)
- Orchestrator: gemma4-ofbiz:latest (gemma4:31b) — best tool calling, 86.4% τ2-bench
- Planner: ofbiz-think:latest (gpt-oss:120b) — architecture, thinking mode
- Coder: devstral-ofbiz:latest (devstral:24b) — file editing, Groovy/XML
- Tester: gemma4-ofbiz:latest — curl tests, log verification
- Default Haiku/Sonnet → gemma4-ofbiz, Opus → ofbiz-think
- Ollama context: 65536 (MacStudio auto-scales to 262144 from 161GB VRAM)

## Phase 7 Complete (Apr 9 2026) — Critical Patterns

### File Upload Root Cause
UtilHttp.getParameterMap() skips multipart when URL params exist (externalLoginKey).
ALWAYS use type="groovy" event for file uploads. Read from request.getAttribute("multiPartMap").

### Screen Context Bridge
Groovy events → request.setAttribute() → bridge via ImportPreviewActions.groovy → context.x

### @Field required
Script-level vars need @groovy.transform.Field to be accessible in methods.

### Data Types
- ftmStaffVlan10: stored as "Y"/"N" string (not boolean)
- active: raw Java Boolean — use use-when="active" / use-when="!active"
- deviceQuota: use != null && != "" check (0 is falsy in Groovy)

### Deactivate ≠ Delete
Deactivate = SET active=FALSE (reversible). Delete = permanent with JS confirm FTL.




# FTM OFBiz cerebrum.md
<!-- Branch: feature/ftm-garments | Tracked in git | Updated: 2026-04-22 -->

## Project Identity

- **Repo:** ftmerp-java-plugins + ftmerp-java-project (Apache OFBiz fork)
- **Active branch:** feature/ftm-garments (all Phase 7-9 work)
- **OFBiz version:** trunk (JDK 21, Gradle 8.8, Tomcat 10.1)
- **DB:** PostgreSQL 16 on 192.168.30.3 — databases: ftmerp, ftm_enrollment, ftm_ofbiz
- **Dev container:** Incus ofbiz-dev at 192.168.30.102
- **Ollama:** MacStudio 192.168.192.79:11434 (llama3.3:70b, gemma4-ofbiz)

## Active Plugins

### ftm-wifi-enrollment
- **Purpose:** HR/IT UI for managing WiFi EAP-TLS certificate enrollment
- **DB:** ftm_enrollment (PostgreSQL), user: enrolladmin
- **Delegator:** ftmEnrollment (separate from OFBiz default)
- **Password injection:** start-ftm.sh reads gradle.properties.local → -PjvmArgs=-Dftm.enrolladmin.password=...
- **Groovy pattern:** System.getProperty("ftm.enrolladmin.password") ?: System.getenv("FTM_ENROLLMENT_DB_PASS") ?: "MISSING_PASSWORD"
- **Phase 7 complete:** CRUD, CSV export, Excel import (POI 5.3), activate/deactivate/delete

### ftm-garments
- **Purpose:** FTM garment manufacturing workflow (styles, colors, production)
- **Status:** Scaffolded, active development in Phase 9+
- **widget-resource:** removed from ofbiz-component.xml (OFBiz trunk schema change)

## Critical OFBiz Knowledge

### Build
```bash
# ALWAYS run after schema/entitymodel changes:
./gradlew cleanAll   # deletes build/ entirely
./gradlew build -x test
./gradlew loadAll    # only if DB tables missing
bash start-ftm.sh &  # starts OFBiz
```

### entityengine.xml Rules
- **Source:** framework/entity/config/entityengine.xml (real passwords on disk)
- **Git:** assume-unchanged — NEVER commit with real passwords
- **Built copy:** build/resources/main/entityengine.xml — Gradle copies from source
- **Schema:** use reader-name NOT helper-name; no write-data element (trunk XSD)
- **FTM datasources:** localpostgres (ftm_ofbiz), localpostgresftmerp (ftmerp), ftmEnrollmentDataSource (ftm_enrollment)
- **Delegator ordering:** default → localpostgresftmerp; ftmEnrollment → ftmEnrollmentDataSource

### Password Management
```
Real passwords ONLY in:
  entityengine.xml on disk (assume-unchanged)
  gradle.properties.local (gitignored, chmod 600)
  ~/.pgpass (chmod 600)

NEVER in:
  any committed file
  any .md, .sh, .xml, .groovy in git
  any env var printed to logs

Current passwords (rotated 2026-04-22):
  ftmuser → [in gradle.properties.local]
  enrolladmin → [in gradle.properties.local]
  ofbizadmin → [in gradle.properties.local]
  (old passwords FTMIT@2026 ftmscep2026 FtmOfbiz2026! are INVALID)
```

### OFBiz User Management (Derby embedded DB)
- Create user: POST `/webtools/control/createUserLogin` with userLoginId + currentPassword + currentPasswordVerify + requirePasswordChange=N
- Assign security group: POST `/webtools/control/userLogin_addUserLoginToSecurityGroup` — params: userLoginId, **groupId** (NOT securityGroupId), fromDate=YYYY-MM-DD HH:MM:SS.000
- "You cannot login to this application" = user exists but has NO security group — add FULLADMIN for IT admins
- "User not found" = user doesn't exist in OFBiz's own store (Derby) — create it first; Flask users are separate
- Plan: Derby → PostgreSQL migration pending (ftm_ofbiz / ftmerp databases on 192.168.30.3 currently empty)

## Do-Not-Repeat (OFBiz Proxy/Security)
- [2026-05-21] When proxying OFBiz behind nginx (or any reverse proxy), ALWAYS add the proxy domain to `host-headers-allowed` in `framework/security/config/security.properties`. OFBiz rejects all requests from unrecognized Host headers with RequestHandlerException. Use python3 str.replace() to edit (not sed — special chars in value).
- [2026-05-21] After editing security.properties, OFBiz MUST be restarted — it reads this file at startup only.

## Common Failure Patterns + Fixes

| Symptom | Root Cause | Fix |
|---------|-----------|-----|
| `delegator factory returned null` | build/resources/main/entityengine.xml stale schema | `./gradlew cleanAll && ./gradlew build -x test` |
| `helper-name not allowed` in entityengine.xml | Old schema — use reader-name | Python regex replace, not sed (tabs in file) |
| `password authentication failed enrolladmin` | Old hardcoded password in Groovy | Replace with System.getProperty("ftm.enrolladmin.password") |
| `-D flag not reaching OFBiz JVM` | -D goes to Gradle JVM not OFBiz JVM | Use -PjvmArgs="-Dfoo=bar" in gradlew call |
| `poi-ooxml duplicate in distTar` | lib/ dir + build.gradle both declare POI | Remove lib/ dir and classpath from ofbiz-component.xml |
| `widget-resource invalid` in ofbiz-component.xml | OFBiz trunk removed widget-resource from XSD | Delete the widget-resource lines |
| PostgreSQL 5432 only on 127.0.0.1 after reboot | Network timing — PostgreSQL starts before bridge | postgresql-listen-fix.service (systemd, sleeps 15s then restarts pg) |
| `sed` fails on special chars (@, #, !) | sed treats them as delimiters in some contexts | Use python3 str.replace() instead |

### Git Workflow
```bash
# Session start (ALWAYS first):
ftm-sync   # = git fetch origin && git rebase origin/feature/ftm-garments

# Push:
git push --force-with-lease origin feature/ftm-garments --no-verify

# Cherry-pick to ftm-wifi-enrollment branch:
git checkout feature/ftm-wifi-enrollment
git cherry-pick <hash>
git push origin feature/ftm-wifi-enrollment --no-verify
git checkout feature/ftm-garments
```

### start-ftm.sh
- Location: /opt/ofbiz-framework/start-ftm.sh
- Gitignored — must recreate after re-clone
- Reads FTM_ENROLLADMIN_PASSWORD from gradle.properties.local
- Passes via: ./gradlew ofbiz -PjvmArgs="-Dftm.enrolladmin.password=${ENROLL_PASS}"
- Safety check: fails if entityengine.xml still has YOUR_* placeholders

## Phase Status

```
Phase 7: COMPLETE — ftm-wifi-enrollment plugin fully working
Phase 8: COMPLETE — entityengine.xml FTM datasources configured
Phase 9A: COMPLETE — security hardening (BFG, password rotation, git rules)
Phase 9B: IN PROGRESS — PostgreSQL migration (loadAll done, backup cron pending)
Phase 9C: IN PROGRESS — Claude Code + Ollama model switching (Rev3 proxy deployed)
Phase 9D: PLANNED — Hermes Agent memory + skill extraction
Phase 10: PLANNED — Apache Camel + Superset + LangChain4j
```

## Multi-Instance Collaboration (Phase 9C Rev3)

### Auth finding (2026-05-03)
tmm7 uses Claude Desktop OAuth (CLAUDE_CODE_PROVIDER_MANAGED_BY_HOST=1).
NO sk-ant-* API key exists. Claude Code-credentials in macOS Keychain = OAuth token.
Proxy can route to LOCAL Ollama only. Cloud Anthropic requires Desktop OAuth directly.

### Rev3 Architecture: dual-mode via free-claude-code proxy
```
CLOUD mode  (cc / cc-sonnet / cc-opus)
  → no ANTHROPIC_BASE_URL override
  → Claude Desktop OAuth → real Anthropic API
  → use for: complex planning, architecture, git security tasks

LOCAL mode  (cc-local / cc-fast)
  → ANTHROPIC_BASE_URL=http://localhost:8082
  → free-claude-code proxy (~/development/free-claude-code/)
  → Ollama on MacStudio (localhost:11434)
  → use for: bulk coding, repetitive tasks, OFBiz XML/Groovy
  → /model picker: switch between Ollama backends MID-SESSION (no restart)
```

### Proxy files
  ~/development/free-claude-code/          ← proxy source (git clone)
  ~/.config/free-claude-code/.env          ← proxy config (model tier mapping)
  ~/development/ofbiz-plugins/start-ftm-proxy.sh  ← start/stop/model management

### Model tiers (current — update as you pull better models)
  MODEL_HAIKU  = ollama/phi3:3.8b     → upgrade: ollama pull gemma3:12b
  MODEL_SONNET = ollama/mistral:7b    → upgrade: ollama pull llama3.3:70b
  MODEL_OPUS   = ollama/mistral:7b    → upgrade: same llama3.3:70b

### Shell aliases (~/.zshrc)
  cc           → cloud Anthropic (Desktop OAuth)
  cc-sonnet    → cloud sonnet-4-6
  cc-opus      → cloud opus-4-6
  cc-local     → starts proxy + launches Claude Code in local/Ollama mode
  cc-fast      → local proxy already running, haiku tier (phi3 / gemma3)
  ftm-proxy    → proxy management (start/stop/restart/status/model)

### What Rev3 solves vs Rev2
  Rev3 BETTER: within local session, /model picker switches Ollama backends without restart
  Rev3 SAME: cloud↔local still requires new session (env var is process-scoped)
  Rev3 ADDS: proxy process to manage (ftm-proxy status / ftm-proxy restart)

### Cross-machine (ofbiz-dev / rpitex)
  Same pattern: tunnel Ollama (tunnel-ollama alias), then:
  ANTHROPIC_BASE_URL=http://127.0.0.1:8082 ANTHROPIC_AUTH_TOKEN=freecc claude
  Or run proxy directly on remote machine pointing OLLAMA_BASE_URL at tunnel.

```
tmm7 (primary-dev):     cc (cloud) or cc-local (proxy→Ollama)
ofbiz-dev (build-test): cc-local via SSH tunnel to MacStudio Ollama
rpitex (staging):       same as ofbiz-dev
```

