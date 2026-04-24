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

## Appended THIS SECTION to /opt/ofbiz-plugins/CLAUDE.md

---

## Phase 9C: Multi-Instance Collaboration + Model Switching

### Instance Roles

```
tmm7 (MacStudio)   → primary-dev  → claude-sonnet-4-6 / llama3.3:70b
ofbiz-dev (Incus)  → build-test   → gemma4-ofbiz:latest (via SSH tunnel)
rpitex (Pi5)       → staging      → gemma4-ofbiz:latest (via SSH tunnel)
```

### Shell Aliases (add to ~/.zshrc on tmm7, ~/.bashrc on ofbiz-dev/rpitex)

```bash
# Cloud Claude
alias cc='claude'
alias cc-sonnet='claude --model claude-sonnet-4-6'
alias cc-opus='claude --model claude-opus-4-6'

# Local Ollama (MacStudio)
alias cc-llama='ollama launch claude --model llama3.3:70b'
alias cc-ofbiz='ollama launch claude --model gemma4-ofbiz:latest'
alias cc-fast='ollama launch claude --model gemma3:12b'

# On ofbiz-dev/rpitex (SSH tunnel to MacStudio Ollama)
alias tunnel-ollama='ssh -L 11434:localhost:11434 texchi@192.168.192.79 -N -f'
alias cc-ofbiz='ANTHROPIC_BASE_URL=http://127.0.0.1:11434 \
  ANTHROPIC_AUTH_TOKEN=ollama ANTHROPIC_API_KEY=ollama \
  claude --model gemma4-ofbiz:latest'
```

### Session Start Checklist (ALL instances)

```bash
# 1. Sync git first
ftm-sync

# 2. Check .claude-code-state.json for handoff notes
cat /opt/ofbiz-plugins/.claude-code-state.json | \
  python3 -c "import json,sys; s=json.load(sys.stdin)['session_handoff']; \
  print('Last:', s['last_completed']); \
  print('Current:', s['current_task']); \
  [print('TODO:', x) for x in s['unresolved']]"

# 3. Start appropriate model
# Complex task (architecture, debugging) → cc-sonnet
# Bulk coding (repetitive, large files)  → cc-llama (free)
# OFBiz-specific (Groovy, XML, screens)  → cc-ofbiz
```

### Context Continuity

Claude Code preserves context across model switches via compaction:
- Compaction triggers automatically near context limit
- Summarizes: architectural decisions, unresolved bugs, implementation state
- CLAUDE.md + .wolf/cerebrum.md always loaded = persistent project context
- Session handoff: update `.claude-code-state.json` before switching machines

### Shared State Files

```
Tracked in git (shared across instances automatically):
  CLAUDE.md              ← this file, project conventions
  .wolf/cerebrum.md      ← accumulated OFBiz knowledge + failure patterns

Gitignored (per-machine, copy manually):
  .claude-code-state.json       ← session handoff, instance config
  gradle.properties.local       ← real passwords
  framework/entity/config/entityengine.xml  ← real passwords (assume-unchanged)
  start-ftm.sh                  ← recreate after re-clone
```

### Model Decision Guide

```
Task type                          → Model
───────────────────────────────────────────────
 Architecture / phase planning    → cc-sonnet or cc-opus
 Debugging complex OFBiz errors   → cc-sonnet
 Writing Groovy services          → cc-ofbiz (gemma4, knows OFBiz)
 Writing screen/form XML          → cc-ofbiz
 Bulk find-and-replace tasks      → cc-llama (free, fast)
 git history / security tasks     → cc-sonnet (careful reasoning)
 Quick bash commands              → cc-fast (gemma3:12b)
```

