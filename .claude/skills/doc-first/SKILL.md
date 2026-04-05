# SKILL: doc-first
**Skill ID:** `doc-first`
**Trigger phrases:** "doc-first", "find official docs", "check the docs first", "no guessing"
**Applies to:** OpenFang agents, Claude Code, Claude Chat (this conversation)

---

## Purpose

Enforce a **documentation-first, no-guessing** workflow for technical problem solving.
Before attempting any fix, configuration, or code change: locate and read the official
source documentation. Save fetched docs locally for reuse. Never invent API fields,
config keys, or behavior from memory alone.

---

## Activation

Invoke this skill when:
- Starting work on any new software component (OFBiz, Ollama, OpenFang, pfSense, etc.)
- Encountering an error or unexpected behavior
- About to write config files, agent manifests, or integration code
- Claude or an agent is about to "guess" at a solution

Human trigger: **"doc-first"**
Agent self-trigger: when unsure about any API field, config key, or behavior

---

## Workflow

### Phase 1: Identify authoritative sources

For each software component involved, locate in priority order:

1. **Official GitHub docs** — `https://github.com/{org}/{repo}/tree/main/docs`
2. **Official documentation site** — check repo README for docs URL
3. **Changelog/releases** — for version-specific behavior
4. **Source code** — for undocumented fields (config structs with `#[serde(default)]`)

**Never use:**
- Third-party tutorials as primary source
- AI training knowledge alone (may be outdated)
- Unofficial forks of documentation repos

### Phase 2: Fetch and save docs locally

```bash
# Save to local knowledge base
mkdir -p /home/texchi/ofbiz/export/docs/{software_name}

# Fetch via curl (preferred — no rate limits)
curl -s https://raw.githubusercontent.com/{org}/{repo}/main/docs/{file}.md \
  > /home/texchi/ofbiz/export/docs/{software_name}/{file}.md

# Or via MCP tool in OpenFang:
# mcp_filesystem_write_file(path, content_from_web_fetch)
```

**Canonical locations for FTM project:**

| Software | Local path | Source |
|----------|-----------|--------|
| OpenFang config | `/home/texchi/openfang/docs/configuration.md` | github.com/RightNow-AI/openfang/docs |
| OpenFang agents | `/home/texchi/openfang/docs/agent-templates.md` | github.com/RightNow-AI/openfang/docs |
| OpenFang architecture | `/home/texchi/openfang/docs/architecture.md` | github.com/RightNow-AI/openfang/docs |
| OFBiz EntityEngine | `/home/texchi/ofbiz/export/docs/ofbiz/entityengine.md` | cwiki.apache.org/confluence/display/OFBIZ |
| Ollama API | `/home/texchi/ofbiz/export/docs/ollama/api.md` | docs.ollama.com/api |
| Ollama OpenAI compat | `/home/texchi/ofbiz/export/docs/ollama/openai.md` | docs.ollama.com/api/openai-compatibility |

### Phase 3: Read before acting

Before writing any config, code, or command:

```
1. mcp_filesystem_read_file(relevant_doc_path)
2. Identify the exact field/endpoint/behavior needed
3. Verify field exists in the official schema
4. Only then write config or code
```

### Phase 4: Validate against source

After writing:
- Cross-check every config key against the official field table
- If a field is not in the official docs: **do not use it**
- If behavior is uncertain: fetch the relevant doc section again

---

## Anti-patterns to avoid

| Bad | Good |
|-----|------|
| Guessing `allowedPaths` is a valid TOML key | Read agent-templates.md first |
| Assuming `[routing]` section exists | Check configuration.md field table |
| Inventing `embedding_provider` field | Verify in [memory] section of config docs |
| Trying `stream=false` in agent manifest | Confirm field exists in manifest schema |
| Using mudrii's unofficial docs | Use github.com/RightNow-AI/openfang/docs only |

---

## For OpenFang agents

When an agent invokes `doc-first`:

```
1. Use mcp_exa-search_search to find official docs URL
2. Use web_fetch to retrieve the content
3. Use mcp_filesystem_write_file to save to /home/texchi/ofbiz/export/docs/
4. Use mcp_filesystem_read_file to read back and extract relevant section
5. Proceed with the task using only verified information
6. If still uncertain: write findings to export/ and ask human for confirmation
```

---

## Export for Claude.ai

When communicating findings to Claude.ai (this chat):

```bash
# Write clean export file
mcp_filesystem_write_file(
  path="/home/texchi/ofbiz/export/YYYY-MM-DD_topic.md",
  content="# Topic\n## Official source\n...\n## Verified fields\n..."
)

# On ltsp-rpi4b terminal:
cat /home/texchi/ofbiz/export/YYYY-MM-DD_topic.md
# Then paste into Claude.ai chat
```

---

## Skill metadata (OpenFang SKILL.md format)

```yaml
name: doc-first
version: 1.0.0
description: Documentation-first workflow — fetch official docs before acting
runtime: prompt-only
tags: [workflow, documentation, reliability, no-guessing]
triggers:
  - "doc-first"
  - "check the docs"
  - "find official docs"
  - "no guessing"
applies_to:
  - ofbiz-dev
  - ofbiz-analyst
  - ofbiz-coder
  - ofbiz-debugger
  - ofbiz-researcher
```
