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

## Phase 8A Lessons Learned (Apr 12 2026)

### Critical OFBiz plugin rules confirmed:
1. ofbiz-component.xml name= MUST match plugin dir name (not copy from another plugin)
2. mainDecoratorLocation in web.xml MUST point to *CommonScreens.xml (separate file)
   - NOT to the main screens file (causes decorator recursion/not-found error)
3. main-decorator screen MUST be in its own CommonScreens.xml → ApplicationDecorator
4. Menu file MUST define the exact menu name referenced in CommonScreens.xml
5. FtmStyleNumber is Derby (default delegator) → use EntityQuery.use(delegator)
   NOT Sql.newInstance/JDBC (causes 216s transaction timeout)
6. Groovy scripts hot-reload; web.xml and ofbiz-component.xml require restart
7. auth= attribute: only ONE per <service> element; sed append creates duplicates
8. Derby lock files: remove BOTH dbex.lck AND db.lck before restart
