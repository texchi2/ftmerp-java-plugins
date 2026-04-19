# FTM Phase 9 Implementation Plan
**Apache OFBiz + Apache Superset + MCP AI (hermes)**
**Oracle Fusion Equivalent for FTM Garments Swaziland**

- **Version:** 1.0
- **Date:** April 2026
- **Authors:** Dr. Jamal Tex Chi (IT Head), Mr. Kona (IT Developer)
- **Status:** Active Development

---

## Table of Contents

1. [Project Vision](#1-project-vision)
2. [Hardware Architecture](#2-hardware-architecture)
3. [Network Topology](#3-network-topology)
4. [Software Stack](#4-software-stack)
5. [AI Model Assignment](#5-ai-model-assignment)
6. [Development Environment Setup](#6-development-environment-setup)
7. [Claude Code Settings](#7-claude-code-settings)
8. [Daily Workflow — Dr. Tex](#8-daily-workflow--dr-tex)
9. [Daily Workflow — Mr. Kona](#9-daily-workflow--mr-kona)
10. [Git Branching Strategy](#10-git-branching-strategy)
11. [Phase 9 Sub-phases](#11-phase-9-sub-phases)
12. [Production Deployment Path](#12-production-deployment-path)
13. [Cost Summary](#13-cost-summary)
14. [Handover Protocol](#14-handover-protocol)

---

## 1. Project Vision

Phase 9 delivers an Oracle Fusion equivalent for FTM Garments Swaziland by integrating three open-source pillars:

```
Apache OFBiz (ERP Core)
    +
Apache Superset (BI / Analytics)
    +
MCP AI hermes (Natural Language Operations Layer)
    =
Oracle Fusion Equivalent
```

### Sub-phases

| Sub-phase | Deliverable | Target |
|-----------|-------------|--------|
| **9A** | Apache Superset on rpitex Incus container | May 2026 |
| **9B** | PostgreSQL views bridging OFBiz → Superset | May 2026 |
| **9C** | FastMCP server (hermes) exposing OFBiz REST as AI tools | June 2026 |
| **9D** | OpenFang v0.5.10 runtime agents (production monitor, hermes query) | June 2026 |
| **9E** | Production deployment to FTM HQ (erp2, 192.168.10.x) | July 2026 |

---

## 2. Hardware Architecture

### 2.1 Server Infrastructure

```
┌─────────────────────────────────────────────────────────────────────┐
│                    DEVELOPMENT INFRASTRUCTURE                        │
│                    Subnet: 192.168.30.0/24                          │
├──────────────────────┬──────────────────────────────────────────────┤
│  MACHINE             │  ROLE / SPECS                                │
├──────────────────────┼──────────────────────────────────────────────┤
│  pfsense-msi-ftm     │  Ubuntu 24.04 host (Incus hypervisor)       │
│  192.168.30.3        │  ZeroTier: 192.168.192.68                   │
│  (Ubuntu host)       │  Hosts Incus containers:                    │
│                      │  ├─ pfsense-vm  (192.168.2.6)              │
│                      │  ├─ omada-vm    (192.168.30.10)            │
│                      │  ├─ ofbiz-dev   (192.168.30.102) ← testing │
│                      │  └─ superset    (on rpitex Incus)           │
├──────────────────────┼──────────────────────────────────────────────┤
│  rpitex              │  PRIMARY DEVELOPMENT MACHINE                │
│  192.168.30.129      │  Raspberry Pi 5 / ARM64                     │
│  ZT: 192.168.192.80  │  RAM: 15GB | Storage: 1.8TB NVMe           │
│                      │  Python 3.12.3 | JDK 21                     │
│                      │  Hosts Incus superset container             │
│                      │  OFBiz dev: /home/texchi/development/        │
├──────────────────────┼──────────────────────────────────────────────┤
│  MacStudio           │  AI INFERENCE SERVER (ALL LLM models)       │
│  192.168.192.79 (ZT) │  M2 Ultra | RAM: 192GB unified memory       │
│                      │  Ollama: all approved models                 │
│                      │  OLLAMA_HOST=0.0.0.0:11434                  │
├──────────────────────┼──────────────────────────────────────────────┤
│  ltsp-rpi4b256       │  OpenFang v0.5.10 RUNTIME ONLY              │
│  (Raspberry Pi 4B)   │  Agent monitor + hermes query (24/7)        │
│                      │  NOT used for coding development             │
└──────────────────────┴──────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                    PRODUCTION INFRASTRUCTURE                         │
│                    FTM HQ Subnet: 192.168.10.0/24                  │
├──────────────────────┬──────────────────────────────────────────────┤
│  erp1                │  Windows Server 2019                        │
│  192.168.10.101      │  Production Dashboard (Tomcat + MySQL)      │
│                      │  Update interval: 50 minutes                 │
├──────────────────────┬──────────────────────────────────────────────┤
│  erp2                │  Ubuntu 24.04 (libvirt/KVM hypervisor)      │
│  192.168.10.x        │  FUTURE: OFBiz + Superset production        │
│                      │  Phase 9E deployment target                  │
└──────────────────────┴──────────────────────────────────────────────┘
```

### 2.2 Developer Client Machines

```
┌─────────────────────────────────────────────────────────────────────┐
│                      DR. TEX CLIENTS                                 │
├──────────────────────┬──────────────────────────────────────────────┤
│  MacBook Air (tmm7)  │  Primary development terminal                │
│                      │  Claude Code Pro ($20/mo — Dr. Tex budget)  │
│                      │  ~/.claude/settings.json (full config)       │
│                      │  ~/.codex/config.toml (Ollama backend)       │
│                      │  git clone: ftmerp-java-plugins              │
│                      │             ftmerp-java-project              │
├──────────────────────┼──────────────────────────────────────────────┤
│  iPad                │  Mobile / emergency access                   │
│                      │  SSH via Blink Shell or Terminus             │
│                      │  ~/.claude/settings.json (simplified)        │
│                      │  ZeroTier → rpitex (192.168.192.80)         │
└──────────────────────┴──────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                      MR. KONA CLIENTS                                │
├──────────────────────┬──────────────────────────────────────────────┤
│  ftmitu              │  Primary dev terminal (Ubuntu 24.04)        │
│  (Ubuntu 24.04       │  Claude Code Pro ($20/mo — FTM IT budget)   │
│  + Windows 11)       │  ~/.claude/settings.json                     │
│                      │  SSH tunnel → MacStudio Ollama fallback     │
├──────────────────────┼──────────────────────────────────────────────┤
│  Kona's Pi 5         │  Secondary dev terminal                      │
│  16GB RAM + 500GB    │  Claude Code (shared FTM subscription)       │
│  SSD                 │  Can run gpt-oss:20b or mistral:7b locally  │
│                      │  Codex CLI optional                          │
└──────────────────────┴──────────────────────────────────────────────┘
```

---

## 3. Network Topology

```
                    [Internet / ZeroTier VPN]
                           │
              ┌────────────┴────────────────┐
              │                             │
     [Eswatini — FTM HQ]          [Taiwan / Remote]
              │                             │
    ┌─────────┴──────────┐        ┌─────────┴──────────┐
    │  pfSense Gateway   │        │  MacStudio (ZT)     │
    │  192.168.10.0/24   │        │  192.168.192.79     │
    │  (Production LAN)  │        │  Ollama ALL models  │
    │  erp1, erp2        │        └─────────────────────┘
    └────────────────────┘
              │
    ┌─────────┴──────────┐
    │  Dev LAN           │
    │  192.168.30.0/24   │
    │  ├─ Ubuntu host    │──── ZeroTier 192.168.192.68
    │  │  192.168.30.3   │
    │  │  ├─ ofbiz-dev   │  192.168.30.102
    │  │                 │
    │  └─ rpitex         │──── ZeroTier 192.168.192.80
    │     192.168.30.129 │
    │     └─ superset    │
    └────────────────────┘
```

**SSH Tunnel for Ollama (clients → MacStudio):**
```bash
ssh -L 11434:localhost:11434 texchi@192.168.192.79 -N &
# → Codex CLI and Claude Code fallback: http://127.0.0.1:11434
```

**Git Remote:**
```
git@github.com:texchi2/ftmerp-java-plugins.git
git@github.com:texchi2/ftmerp-java-project.git
```

---

## 4. Software Stack

| Component | Technology | Host | IP / Port |
|-----------|------------|------|-----------|
| OFBiz ERP (dev) | Java 21, Gradle | ofbiz-dev container | 192.168.30.102:8443 |
| OFBiz ERP (production) | Java 21, Gradle | erp2 | 192.168.10.x:8443 |
| Superset (dev) | Python 3.12, pip | superset (Incus on rpitex) | 192.168.30.129:8088 |
| Superset (prod) | Python 3.12 | erp2 | 192.168.10.x:8088 |
| PostgreSQL | v16+ | Ubuntu host | 192.168.30.3:5432 |
| hermes MCP server | Python FastMCP | rpitex | :8090 |
| OpenFang v0.5.10 | Rust binary | ltsp-rpi4b256 | :50051 |
| Ollama inference | All models | MacStudio | 192.168.192.79:11434 |

---

## 5. AI Model Assignment

### 5.1 Approved Models (No Chinese LLMs — Security Policy)

| Model | Origin | Size | Role |
|-------|--------|------|------|
| `gemma4-ofbiz:latest` | Google (FROM gemma4:31b) | 31B | OFBiz XML/Groovy specialist |
| `ofbiz-think:latest` | OpenAI OSS (FROM gpt-oss:120b) | 120B | Architecture + deep reasoning |
| `gpt-oss:20b` | OpenAI OSS | 20B | Fast lightweight tasks |
| `llama3.3:70b` | Meta | 70B | OpenFang tool-calling (proven) |
| `llama3.2-vision:90b` | Meta | 90B | Screenshot / diagram review |
| `llama4:scout` | Meta (MoE) | efficient | OpenFang scheduling hand |
| `codellama:70b` | Meta | 70B | Java implementation |
| `mistral:7b-instruct` | Mistral AI | 7B | Fast routing / triage |

**Banned (security policy — remove if present):**
```bash
ollama rm qwen3:235b 2>/dev/null || true
ollama rm deepseek-coder 2>/dev/null || true
```

### 5.2 Multi-Agent Model Assignment

**Claude Code Sub-agents (Development — via MCP on MacStudio)**

| Agent | Model | Invoked For |
|-------|-------|-------------|
| `ofbiz-architect` | `ofbiz-think:latest` | Entity design, service architecture, complex SQL |
| `ofbiz-coder` | `gemma4-ofbiz:latest` | entitymodel.xml, services.xml, Groovy, controller.xml |
| `java-coder` | `codellama:70b` | Java service implementations, MCP server code |
| `superset-analyst` | `ofbiz-think:latest` | PostgreSQL views, SQLAlchemy config |
| `vision-reviewer` | `llama3.2-vision:90b` | Dashboard screenshots, UI error analysis |
| `fast-router` | `mistral:7b-instruct` | Quick triage, TOML/YAML boilerplate |

**Codex CLI Agents (Autonomous — pure Ollama, $0)**

| Profile | Model | Task |
|---------|-------|------|
| default | `ofbiz-think:latest` (gpt-oss:120b base) | Tests, code review |
| `fast` | `gpt-oss:20b` | CRUD boilerplate, agent TOML stubs |

**OpenFang v0.5.10 Runtime Hands (Always-on on ltsp-rpi4b256)**

| Hand | Model | Schedule |
|------|-------|----------|
| `production-monitor` | `llama4:scout` | Every 50 min — polls OFBiz WorkEffort delays |
| `hermes-query` | `llama3.3:70b` | On-demand — NL → OFBiz/Superset |
| `researcher` | `llama3.3:70b` | Daily — garment industry digest |

> **Note:** Use `llama3.3:70b` for OpenFang — proven stable with OpenFang tool-calling
> loop from Phase 7. `gemma4-ofbiz` and `ofbiz-think` are NOT yet tested with OpenFang.
> Do not change OpenFang model until tested.

---

## 6. Development Environment Setup

### 6.1 MacStudio — Ollama Server (One-time)

```bash
# Bind Ollama to all interfaces for ZeroTier access
launchctl setenv OLLAMA_HOST "0.0.0.0:11434"
# Restart Ollama app

# Verify all approved models present
ollama list

# Remove banned models
ollama rm qwen3:235b 2>/dev/null || true
ollama rm deepseek-coder 2>/dev/null || true

# Test from rpitex
curl http://192.168.192.79:11434/api/tags | python3 -m json.tool | grep name
```

### 6.2 rpitex — Primary Dev Machine (One-time)

```bash
cd /home/texchi/development

# Clone repos if not present
git clone git@github.com:texchi2/ftmerp-java-plugins.git ofbiz-plugins
git clone git@github.com:texchi2/ftmerp-java-project.git ofbiz-framework

# Symlink plugins into OFBiz framework
ln -sf /home/texchi/development/ofbiz-plugins \
       /home/texchi/development/ofbiz-framework/plugins

# Install MCP servers
npm install -g @modelcontextprotocol/server-filesystem
npm install -g @modelcontextprotocol/server-postgres
npm install -g @openai/codex
```

### 6.3 ftmitu — Mr. Kona Ubuntu 24.04 (One-time)

```bash
# Install Claude Code
npm install -g @anthropic-ai/claude-code

# Install MCP servers
npm install -g @modelcontextprotocol/server-filesystem
npm install -g @modelcontextprotocol/server-postgres

# Install Codex CLI (optional)
npm install -g @openai/codex

# Clone repos
mkdir -p ~/development
git clone git@github.com:texchi2/ftmerp-java-plugins.git ~/development/ofbiz-plugins
git clone git@github.com:texchi2/ftmerp-java-project.git ~/development/ofbiz-framework

# Ollama tunnel alias
echo 'alias tunnel-ollama="ssh -L 11434:localhost:11434 texchi@192.168.192.79 -N -f && echo Ollama tunnel open"' >> ~/.bashrc
source ~/.bashrc
```

### 6.4 Kona's Raspberry Pi 5 (16GB)

```bash
# Same as ftmitu Ubuntu above
# Light local models for offline work
ollama pull gpt-oss:20b
ollama pull mistral:7b-instruct
```

---

## 7. Claude Code Settings

### 7.1 Dr. Tex — MacBook Air tmm7 (Primary, Full Config)

File: `~/.claude/settings.json`

```json
{
  "mcpServers": {
    "filesystem-rpitex": {
      "type": "stdio",
      "command": "ssh",
      "args": [
        "-o", "StrictHostKeyChecking=no",
        "texchi@192.168.30.129",
        "npx -y @modelcontextprotocol/server-filesystem /home/texchi/development/ofbiz-plugins /home/texchi/development/ofbiz-framework /home/texchi/development"
      ]
    },
    "filesystem-local": {
      "type": "stdio",
      "command": "npx",
      "args": [
        "-y", "@modelcontextprotocol/server-filesystem",
        "/Users/texchi/ftmerp-java-plugins",
        "/Users/texchi/ftmerp-java-project"
      ]
    },
    "ofbiz-postgres": {
      "type": "stdio",
      "command": "npx",
      "args": [
        "-y", "@modelcontextprotocol/server-postgres",
        "postgresql://ofbiz:ofbiz@192.168.30.3:5432/ofbiz"
      ]
    },
    "exa-search": {
      "type": "stdio",
      "command": "node",
      "args": [
        "/usr/local/lib/node_modules/exa-mcp-server/.smithery/stdio/index.cjs"
      ],
      "env": { "EXA_API_KEY": "YOUR_EXA_API_KEY" }
    },
    "ofbiz-architect": {
      "type": "stdio",
      "command": "ssh",
      "args": [
        "texchi@192.168.192.79",
        "ANTHROPIC_BASE_URL=http://localhost:11434 ANTHROPIC_API_KEY=ollama claude --model ofbiz-think:latest --print --system-prompt 'OFBiz architect. Read SKILL.md before any XML. Design entities and services only.'"
      ]
    },
    "ofbiz-coder": {
      "type": "stdio",
      "command": "ssh",
      "args": [
        "texchi@192.168.192.79",
        "ANTHROPIC_BASE_URL=http://localhost:11434 ANTHROPIC_API_KEY=ollama claude --model gemma4-ofbiz:latest --print --system-prompt 'OFBiz developer. Write entity XML, services.xml, Groovy, controller.xml. No raw SQL.'"
      ]
    },
    "java-coder": {
      "type": "stdio",
      "command": "ssh",
      "args": [
        "texchi@192.168.192.79",
        "ANTHROPIC_BASE_URL=http://localhost:11434 ANTHROPIC_API_KEY=ollama claude --model codellama:70b --print --system-prompt 'Java developer. Write OFBiz service implementations using Delegator and EntityQuery. Also write FastMCP Python code.'"
      ]
    },
    "vision-reviewer": {
      "type": "stdio",
      "command": "ssh",
      "args": [
        "texchi@192.168.192.79",
        "ANTHROPIC_BASE_URL=http://localhost:11434 ANTHROPIC_API_KEY=ollama claude --model llama3.2-vision:90b --print --system-prompt 'Visual reviewer. Analyze Superset dashboard screenshots and OFBiz UI. Identify issues and suggest improvements.'"
      ]
    }
  }
}
```

### 7.2 Dr. Tex — iPad (Simplified)

File: `~/.claude/settings.json`

```json
{
  "mcpServers": {
    "filesystem-rpitex": {
      "type": "stdio",
      "command": "ssh",
      "args": [
        "texchi@192.168.192.80",
        "npx -y @modelcontextprotocol/server-filesystem /home/texchi/development/ofbiz-plugins"
      ]
    },
    "ofbiz-postgres": {
      "type": "stdio",
      "command": "ssh",
      "args": [
        "texchi@192.168.30.3",
        "npx -y @modelcontextprotocol/server-postgres postgresql://ofbiz:ofbiz@192.168.30.3:5432/ofbiz"
      ]
    }
  }
}
```

### 7.3 Mr. Kona — ftmitu Ubuntu 24.04

File: `~/.claude/settings.json`

```json
{
  "mcpServers": {
    "filesystem-local": {
      "type": "stdio",
      "command": "npx",
      "args": [
        "-y", "@modelcontextprotocol/server-filesystem",
        "/home/kona/development/ofbiz-plugins",
        "/home/kona/development/ofbiz-framework"
      ]
    },
    "filesystem-rpitex": {
      "type": "stdio",
      "command": "ssh",
      "args": [
        "-o", "StrictHostKeyChecking=no",
        "kona@192.168.30.129",
        "npx -y @modelcontextprotocol/server-filesystem /home/texchi/development/ofbiz-plugins"
      ]
    },
    "ofbiz-postgres": {
      "type": "stdio",
      "command": "npx",
      "args": [
        "-y", "@modelcontextprotocol/server-postgres",
        "postgresql://ofbiz:ofbiz@192.168.30.3:5432/ofbiz"
      ]
    }
  }
}
```

### 7.4 Shell Aliases (Add to `~/.bashrc` or `~/.zshrc` — All Clients)

```bash
# Claude Code — cloud (Pro subscription)
alias cc='claude'

# Claude Code — Ollama fallback via SSH tunnel
alias cc-ofbiz='ANTHROPIC_BASE_URL=http://127.0.0.1:11434 ANTHROPIC_API_KEY=ollama claude --model gemma4-ofbiz:latest'
alias cc-think='ANTHROPIC_BASE_URL=http://127.0.0.1:11434 ANTHROPIC_API_KEY=ollama claude --model ofbiz-think:latest'
alias cc-fast='ANTHROPIC_BASE_URL=http://127.0.0.1:11434 ANTHROPIC_API_KEY=ollama claude --model gpt-oss:20b'

# Codex CLI — Ollama backend (always free)
alias cx='codex'
alias cx-think='codex --profile think'
alias cx-fast='codex --profile fast'

# Ollama SSH tunnel (run before Ollama fallback)
alias tunnel-ollama='ssh -L 11434:localhost:11434 texchi@192.168.192.79 -N -f && echo "Ollama tunnel open on :11434"'

# Phase 9 SSH shortcuts
alias rpitex='ssh texchi@192.168.30.129'
alias ofbiz-dev='ssh texchi@192.168.30.3 -t "sudo incus exec ofbiz-dev -- bash"'
alias ofbiz-log='ssh texchi@192.168.30.102 "tail -f /opt/ofbiz-framework/runtime/logs/ofbiz.log"'
alias ofbiz-deploy='ssh texchi@192.168.30.129 "cd /home/texchi/development/ofbiz-framework && ./gradlew ofbiz --reloadComponent=ftm-garments"'
alias superset-log='ssh texchi@192.168.30.129 "sudo incus exec superset -- tail -f /app/superset/logs/superset.log"'
alias macstudio='ssh texchi@192.168.192.79'
```

### 7.5 Codex CLI Config — Dr. Tex MacBook Air tmm7

File: `~/.codex/config.toml`

```toml
# Run tunnel-ollama before using Codex CLI
model_provider = "ollama"
model = "ofbiz-think:latest"

[model_providers.ollama]
name = "MacStudio Ollama (SSH tunnel)"
base_url = "http://localhost:11434/v1"

[profiles.think]
model_provider = "ollama"
model = "ofbiz-think:latest"

[profiles.ofbiz]
model_provider = "ollama"
model = "gemma4-ofbiz:latest"

[profiles.fast]
model_provider = "ollama"
model = "gpt-oss:20b"
```

---

## 8. Daily Workflow — Dr. Tex

### 8.1 Session Start (MacBook Air tmm7)

```bash
# Terminal 1: Ollama tunnel (always open first)
tunnel-ollama

# Terminal 2: Claude Code cloud (primary orchestrator)
cc
# First message every session:
# "Read CLAUDE.md and HANDOVER.md, then continue Phase 9"

# Terminal 3: SSH to rpitex
rpitex

# Terminal 4: Codex CLI autonomous background (free)
cx --approval-mode full-auto "generate tests for latest changes"
```

### 8.2 Task Routing Decision

```
NEW TASK
  │
  ├─ Complex OFBiz design / architecture / React UI?
  │    → cc (Claude Sonnet cloud)
  │
  ├─ Tests / boilerplate / CRUD / simple SQL?
  │    → cx full-auto (Codex CLI, Ollama, free)
  │
  ├─ Code review after writing?
  │    → /codex:review inside cc (dual-model, free tokens)
  │
  ├─ Claude Code session limit hit?
  │    → cc-ofbiz, cc-think, or cc-fast (Ollama fallback)
  │
  └─ Vision / screenshot review?
       → attach image to cc → vision-reviewer sub-agent
```

### 8.3 OFBiz Development Cycle

```bash
# 1. Architecture (Claude Code cloud)
cc
> "Using ofbiz-architect sub-agent, design entity model for
   FtmProductionOrder with StyleTrimTemplate relationships"

# 2. Delegate implementation to sub-agents via MCP
> "Delegate to ofbiz-coder: write entitymodel.xml for
   FtmProductionOrder following doc-first SKILL.md patterns"

# 3. Java service (delegate)
> "Delegate to java-coder: implement createProductionOrder
   service in GarmentBOMServices.java using EntityQuery"

# 4. Tests (Codex CLI, free)
cx --approval-mode full-auto \
  "Generate JUnit tests for GarmentBOMServices.java
   createProductionOrder edge cases"

# 5. Dual-model review (free)
/codex:review --background

# 6. Hot-deploy and test
ofbiz-deploy

# 7. Check logs
ofbiz-log

# 8. Push to GitHub
rpitex
cd /home/texchi/development/ofbiz-plugins
git add -A
git commit -m "Phase 9B: FtmProductionOrder entity + service"
git push git@github.com:texchi2/ftmerp-java-plugins.git \
  feature/ftm-garments --no-verify
```

### 8.4 Superset Development Cycle (Phase 9A/9B)

```bash
# 1. Design SQL view using live OFBiz schema
cc
> "Using ofbiz-postgres MCP, query live OFBiz schema and
   design vw_production_summary joining WorkEffort,
   FtmProductionSnapshot, FtmGarmentStyle, FtmColorFamily"

# 2. Generate view SQL (Codex CLI)
cx-think "Write complete vw_production_summary PostgreSQL view
          with order_status, progress_pct, style_name, color_family"

# 3. Apply to PostgreSQL
rpitex
psql -h 192.168.30.3 -U ofbiz -d ofbiz -f \
  /home/texchi/development/ofbiz-plugins/ftm-garments/data/sql/views/vw_production_summary.sql

# 4. Configure Superset datasource (UI at 192.168.30.129:8088)
# SQLAlchemy URI: postgresql+psycopg2://ofbiz:ofbiz@192.168.30.3/ofbiz

# 5. Screenshot review via vision agent
cc
# [attach Superset dashboard screenshot]
> "Use vision-reviewer: analyze this dashboard and suggest improvements"
```

### 8.5 From iPad (Mobile Access)

```bash
# Option A: SSH to MacBook Air → full session
ssh air.local
tunnel-ollama && cc

# Option B: ZeroTier → rpitex (from anywhere)
ssh texchi@192.168.192.80
git pull && git status

# Option C: Quick session on MacStudio
macstudio
ANTHROPIC_BASE_URL=http://localhost:11434 \
ANTHROPIC_API_KEY=ollama \
claude --model gemma4-ofbiz:latest
```

---

## 9. Daily Workflow — Mr. Kona

### 9.1 Session Start (ftmitu)

```bash
# Terminal 1: Ollama tunnel to MacStudio
tunnel-ollama

# Terminal 2: Claude Code (FTM Pro $20/mo)
cc
# First message: "Read CLAUDE.md and HANDOVER.md, resume from last commit"

# Terminal 3: SSH to rpitex for testing
ssh texchi@192.168.30.129

# Optional Terminal 4: Codex CLI
cx --approval-mode full-auto "generate tests for my changes"
```

### 9.2 Kona's Task Focus

```bash
# OFBiz screens / forms (Claude Code):
cc
> "Create FindStyles screen and form XML for FtmGarmentStyle
   following ftm-wifi-enrollment patterns"

# MERN dashboard (Claude Code — strong on React):
cc
> "Create React ProductionOrdersTable with recharts,
   fetching from /api/production/orders OFBiz endpoint"

# MongoDB schemas (Codex CLI, optional, free):
cx "Write Mongoose schema for FtmProductionSnapshot:
    workEffortId, snapshotTime, quantityProduced, defectCount"

# When Claude Code limit hit → Ollama fallback:
cc-ofbiz   # continue OFBiz work
cc-fast    # quick tasks
```

### 9.3 Handover: Kona → Tex

```bash
# === KONA: At ~40% session used ===
cd ~/development/ofbiz-plugins

cat > HANDOVER.md << 'EOF'
# Handover — $(date)
## Developer: Kona → Tex

## Stopping Point
- File: [exact path]
- Entity/Service: [name]
- Line: [number] — [description]
- Issue: [if any]

## Completed This Session
- [x] [task 1]
- [x] [task 2]

## Next Steps for Tex
1. [first action]
2. [then this]

## State
- ofbiz-dev running: YES/NO
- Last hot-deploy: [time]
- Tests passing: YES/NO
EOF

git add -A
git commit -m "WIP: [description] — handover to Tex" --no-verify
git push git@github.com:texchi2/ftmerp-java-plugins.git \
  feature/ftm-garments --no-verify
# Notify Dr. Tex: "Pushed — your turn"


# === TEX: Picking up ===
cc
/add HANDOVER.md
/add [file Kona was editing]
> "Resume from HANDOVER.md — continue implementing [task]"
```

---

## 10. Git Branching Strategy

```
GitHub: texchi2/ftmerp-java-plugins
GitHub: texchi2/ftmerp-java-project

Branches:
  main                        ← stable, production-ready
  feature/ftm-garments        ← Phase 8/9 garment BOM (active)
  feature/ftm-superset-views  ← Phase 9B SQL views
  feature/ftm-mcp-server      ← Phase 9C hermes FastMCP
  feature/ftm-mern-dashboard  ← Phase 9 MERN dashboard updates

Rules:
  - Push always: git push --no-verify
  - Commit format: "Phase 9X: [component] [description]"
  - Never commit directly to main without PR review
  - Commit HANDOVER.md before every Claude Code session ends
```

---

## 11. Phase 9 Sub-phases

### 9A — Apache Superset (Target: May 2026)

```
Status: IN PROGRESS

Tasks:
  [x] Create Incus superset container on rpitex
  [x] Fix incusbr0 network (internet access)
  [ ] Install apache-superset (Python 3.12, pip)
  [ ] Configure SQLAlchemy → OFBiz PostgreSQL
  [ ] Create initial FTM production dashboard
  [ ] Test access from FTM HQ

Key config:
  SQLALCHEMY_URI: postgresql+psycopg2://ofbiz:ofbiz@192.168.30.3/ofbiz
  superset URL:   http://192.168.30.129:8088
```

### 9B — PostgreSQL Views (Target: May 2026)

```
Location: ofbiz-plugins/ftm-garments/data/sql/views/

Views:
  vw_production_summary   → WorkEffort + FtmProductionSnapshot
                             + FtmGarmentStyle + FtmColorFamily
  vw_inventory_levels     → InventoryItem + Product + FtmColorFamily
  vw_bom_status           → FtmGarmentStyle + StyleTrimTemplate
                             + ColorMapping
  vw_quality_metrics      → FtmQualityInspection aggregated
```

### 9C — hermes FastMCP Server (Target: June 2026)

```
Language: Python (FastMCP)
Location: rpitex:/home/texchi/development/ofbiz-mcp-server/
Port: 8090

Tools:
  get_production_orders(status, limit)
  get_inventory_levels(product_type)
  create_garment_bom(ss_number, h_no, size)
  validate_color_consistency(bom_id)
  trigger_mrp_run(facility_id)
  query_superset_dashboard(chart_name)
```

### 9D — OpenFang v0.5.10 Runtime (Target: June 2026)

```
Host: ltsp-rpi4b256
Upgrade: 0.4.9 → 0.5.10

Hands:
  production-monitor  (llama4:scout)    every 50 min
  hermes-query        (llama3.3:70b)    on-demand NL queries
  researcher          (llama3.3:70b)    daily digest

Upgrade steps:
  curl -L [v0.5.10 release ARM64] -o /usr/local/bin/openfang-new
  chmod +x /usr/local/bin/openfang-new
  openfang-new migrate --from 0.4.9
  sudo mv /usr/local/bin/openfang /usr/local/bin/openfang-049.bak
  sudo mv /usr/local/bin/openfang-new /usr/local/bin/openfang
```

### 9E — Production Deployment (Target: July 2026)

```
Target: erp2 (192.168.10.x, FTM HQ LAN)

Steps:
  1. git clone ftmerp-java-plugins (main) on erp2
  2. git clone ftmerp-java-project (main) on erp2
  3. Install JDK 21, PostgreSQL, Gradle on erp2
  4. Configure entityengine.xml for production DB
  5. ./gradlew ofbiz — start OFBiz production
  6. Deploy Superset on erp2
  7. pfSense rule: allow 192.168.10.x:8443, :8088
  8. Test from branches JJ1, JJ2, JJ3
```

---

## 12. Production Deployment Path

```
MacBook Air tmm7 / ftmitu
  │  Claude Code writes code via MCP
  ▼
rpitex: /home/texchi/development/ofbiz-plugins/
  │  git push --no-verify
  ▼
GitHub: feature/ftm-garments
  │  PR review → merge to main
  ▼
ofbiz-dev container (192.168.30.102)
  │  git pull → hot-deploy → integration test
  ▼
GitHub: main branch (stable)
  │  git pull on erp2
  ▼
erp2 (192.168.10.x) — PRODUCTION
  └─ OFBiz ERP + Superset → FTM HQ staff
```

---

## 13. Cost Summary

```
Monthly AI Development Costs:
═══════════════════════════════════════════════════════════
  Dr. Tex — Claude Code Pro:       $20/mo  (personal budget)
  Mr. Kona — Claude Code Pro:      $20/mo  (FTM IT budget)
  Codex CLI (Dr. Tex + Kona):       $0/mo  (Ollama backend)
  Ollama on MacStudio:              $0/mo  (hardware owned)
  ─────────────────────────────────────────────────────────
  TOTAL:                           $40/mo  (~E658)

  vs. Max 5x × 2 users:           $200/mo  (E3,288)
  Annual savings:                $1,920/yr  (E31,566)

Claude Pro Usage Limits:
  Session window:  5-hour rolling reset
  Weekly limit:    resets Friday 9:00 AM
  Extra usage:     enabled, $20 monthly cap (adjustable)
  When limit hit:  cc-ofbiz / cc-think / cc-fast aliases
═══════════════════════════════════════════════════════════
```

---

## 14. Handover Protocol

### CLAUDE.md (Committed to git repo root)

```markdown
# FTM Phase 9 — CLAUDE.md
# OFBiz + Superset + hermes/MCP = Oracle Fusion Equivalent

## Claude Code Memory Limitation
Claude Code CLI has NO access to claude.ai conversation history,
even when logged in with the same Anthropic account.
All context must come from this file, HANDOVER.md, and /add commands.

## Infrastructure
- OFBiz dev:    192.168.30.102:8443 (Incus container)
- Superset dev: 192.168.30.129:8088 (Incus on rpitex)
- PostgreSQL:   192.168.30.3:5432, db=ofbiz, user=ofbiz
- MacStudio:    192.168.192.79 (Ollama: all models)
- ZeroTier:     192.168.192.68 (Ubuntu), 192.168.192.80 (rpitex)

## doc-first Rule (ENFORCED)
Before ANY OFBiz XML or Groovy:
  1. Read /mnt/skills/user/ofbiz-dev/SKILL.md
  2. Read /mnt/skills/user/doc-first/SKILL.md
  3. Search docs via exa-search MCP
Never guess OFBiz API or entity names.

## Git
- plugins:   git@github.com:texchi2/ftmerp-java-plugins.git
- framework: git@github.com:texchi2/ftmerp-java-project.git
- Branch:    feature/ftm-garments
- Push:      git push --no-verify

## Hot-deploy
ssh texchi@192.168.30.129
cd /home/texchi/development/ofbiz-framework
./gradlew ofbiz --reloadComponent=ftm-garments
```

### HANDOVER.md Template

```markdown
# Handover — [DATE TIME]
## Developer: [Tex / Kona]

## Stopping Point
- File: [exact path]
- Function/Entity: [name]
- Stopped at: [line or description]

## Completed
- [x] [task 1]
- [x] [task 2]

## Next Steps
1. [first action]
2. [then this]

## State
- ofbiz-dev running: YES/NO
- Last hot-deploy: [time]
- Tests passing: YES/NO
- Last commit: [hash]

## Resume Prompt for Claude Code
> "[Paste last meaningful instruction so next session
   resumes without re-explaining context]"
```

---

*Document: FTM_Phase9_Implementation_Plan.md*
*Maintained by: Dr. Jamal Tex Chi & Mr. Kona*
*Last updated: April 2026*
*Next review: Phase 9E — July 2026*
