# ftm-llm-gateway

This file provides guidance to Claude Code when working in this repository.

## Purpose

**ftm-llm-gateway** runs on **Mac Studio M2 Ultra (192 GB shared memory, 192.168.192.79)**
and exposes a unified LLM gateway for all FTM project Claude Code clients:

- **MLX server** — Gemma 4 (31B, bf16) via Apple MLX framework. No Ollama, no llama.cpp.
- **free-claude-code proxy** — Routes Claude Code API calls to Ollama models OR the MLX server.

Clients (tmm7, ofbiz-dev, rpitex, ltsp-rpi4b256, Kona's rpi5) connect via a single SSH tunnel
on port 8082. The proxy's `/model` picker exposes all Ollama models + `lmstudio/gemma4-mlx`.

## Architecture

```
Client machine (any)
  ssh -L 8082:localhost:8082 texchi@192.168.192.79
  ANTHROPIC_BASE_URL=http://localhost:8082
  ANTHROPIC_AUTH_TOKEN=freecc
        │
        ▼
  free-claude-code proxy  (port 8082, proxy/)
        │
        ├── Ollama  (port 11434)  ← llama3.3:70b, ofbiz-think, gemma4-ofbiz, …
        │
        └── MLX server  (port 8090, mlx_server.py)
                │
                ▼
          mlx_vlm.load() + mlx_vlm.stream_generate()
                │
                ▼
          Gemma 4 31B bf16 weights  (loaded once at startup, ~62 GB)
```

Direct access (no proxy, MLX only):
```
ANTHROPIC_BASE_URL=http://localhost:8090
ANTHROPIC_AUTH_TOKEN=local          ← NOT ANTHROPIC_API_KEY (causes auth conflict)
claude
```

## Quick start (on MacStudio)

```bash
# 1. Setup (clone proxy, install deps, create config)
bash bin/setup.sh

# 2. Download model if not already present (~62 GB, one-time)
uv run hf download mlx-community/gemma-4-31b-it-bf16 \
  --local-dir ./models/gemma-4-31b-it-bf16

# 3. Set model path in config/local.env
echo 'MODEL_PATH="./models/gemma-4-31b-it-bf16"' > config/local.env

# 4. Start both services
bash bin/start.sh

# 5. Verify
bash bin/status.sh
curl http://localhost:8090/v1/models
curl -H "Authorization: Bearer freecc" http://localhost:8082/
```

## Service management

```bash
bash bin/start.sh    # start MLX server (8090) + proxy (8082)
bash bin/stop.sh     # stop both
bash bin/status.sh   # show PID, health, and Ollama model list
```

## Common commands

| Task | Command |
|------|---------|
| Install proxy deps | `cd proxy && uv sync` |
| Install MLX deps | `uv sync --extra mlx` |
| Add a package | `uv add <pkg>` |
| Start MLX server only | `PORT=8090 uv run --extra mlx python mlx_server.py` |
| Start proxy only | `cd proxy && uv run uvicorn server:app --host 127.0.0.1 --port 8082` |
| Smoke test MLX | `curl -N -X POST http://localhost:8090/v1/messages -H 'Content-Type: application/json' -H 'x-api-key: local' -d '{"model":"gemma4","messages":[{"role":"user","content":"hi"}],"max_tokens":64,"stream":true}'` |
| Tail logs | `tail -f ~/.claude/ftm-mlx.log ~/.claude/ftm-proxy.log` |

## MLX server key configuration

All config via environment variables (set in `config/local.env`, gitignored):

| Variable | Default | Purpose |
|----------|---------|---------|
| `MODEL_PATH` | `./models/gemma-4-31b-it-bf16` | Local MLX model directory |
| `HOST` | `127.0.0.1` | Bind address (SSH tunnel reaches 127.0.0.1) |
| `PORT` | `8090` | MLX server port (8080 = OFBiz conflict, 8082 = proxy) |
| `MAX_TOKENS_DEFAULT` | `2048` | Default max_tokens when client omits it |

## Proxy model tiers (edit `~/.config/free-claude-code/.env`)

| Claude tier | Routes to |
|-------------|-----------|
| haiku | `ollama/phi3:3.8b` |
| sonnet | `ollama/llama3.3:70b` |
| opus | `ollama/ofbiz-think:latest` |
| fallback | `ollama/mistral:7b-instruct` |
| MLX (via picker) | `lmstudio/gemma4-mlx` → MLX server port 8090 |

## MLX model variants

| Model | Disk | Notes |
|-------|------|-------|
| `mlx-community/gemma-4-31b-it-bf16` | ~62 GB | **Recommended** — instruction-tuned, has chat template |
| `mlx-community/gemma-4-31b-bf16` | ~62 GB | Base model; no chat template, uses Jinja2 fallback |
| `mlx-community/gemma-4-31b-it-4bit` | ~16 GB | 4-bit quant — faster, lower quality |

## Auth note (important)

Use `ANTHROPIC_AUTH_TOKEN` not `ANTHROPIC_API_KEY` when a claude.ai OAuth session exists:

```bash
# CORRECT — no auth conflict warning
ANTHROPIC_BASE_URL=http://localhost:8090 ANTHROPIC_AUTH_TOKEN=local claude

# WRONG — triggers "Auth conflict: Both a token and an API key are set"
ANTHROPIC_BASE_URL=http://localhost:8090 ANTHROPIC_API_KEY=local claude
```

Proxy clients use `ANTHROPIC_AUTH_TOKEN=freecc` (handled automatically by `cc-llm.sh`).

## Client install

On each client machine, from the `ofbiz-plugins` repo:
```bash
cp cc-llm.sh ~/bin/cc-llm && chmod +x ~/bin/cc-llm

# ~/.bashrc or ~/.zshrc:
alias cc-local='cc-llm'
alias cc-ollama='cc-llm --model ollama/ofbiz-think:latest'
alias cc-mlx='cc-llm --model lmstudio/gemma4-mlx'
```

`cc-llm` auto-opens the SSH tunnel on first use — no manual tunnel command needed.
