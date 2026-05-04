#!/usr/bin/env bash
# start.sh — Start MLX servers + free-claude-code proxy on MacStudio
#   port 8090 : mlx_server.py       — Gemma4 31B (mlx_vlm)
#   port 8091 : mlx_server_gptoss.py — gpt-oss 120B (mlx_lm)  [optional, ~61 GB]
#   port 8082 : free-claude-code proxy
set -euo pipefail

GATEWAY_DIR="$(cd "$(dirname "$0")/.." && pwd)"
LOG_DIR="$HOME/.claude"
mkdir -p "$LOG_DIR"

export PATH="/opt/homebrew/bin:$HOME/.local/bin:$PATH"

# ── 1. MLX-VLM server ────────────────────────────────────────────────────────
MLX_PID="/tmp/ftm-mlx.pid"
MLX_LOG="$LOG_DIR/ftm-mlx.log"

mlx_running() { [[ -f "$MLX_PID" ]] && kill -0 "$(cat "$MLX_PID")" 2>/dev/null; }

if mlx_running; then
    echo "MLX server already running (PID $(cat "$MLX_PID"))"
else
    # Load MODEL_PATH from local config if set
    LOCAL_CONF="$GATEWAY_DIR/config/local.env"
    [[ -f "$LOCAL_CONF" ]] && source "$LOCAL_CONF"
    MODEL_PATH="${MODEL_PATH:-$GATEWAY_DIR/models/gemma-4-31b-bf16}"

    echo "Starting MLX-VLM server (port 8090) ..."
    cd "$GATEWAY_DIR"
    PORT=8090 MODEL_PATH="$MODEL_PATH" \
        nohup uv run --extra mlx python mlx_server.py \
        >> "$MLX_LOG" 2>&1 &
    echo $! > "$MLX_PID"
    sleep 3
    if mlx_running; then
        echo "  MLX server started (PID $(cat "$MLX_PID"))"
    else
        echo "  WARNING: MLX server may have failed — check $MLX_LOG"
    fi
fi

# ── 2. free-claude-code proxy ─────────────────────────────────────────────────
PROXY_PID="/tmp/ftm-proxy.pid"
PROXY_LOG="$LOG_DIR/ftm-proxy.log"

proxy_running() { [[ -f "$PROXY_PID" ]] && kill -0 "$(cat "$PROXY_PID")" 2>/dev/null; }

if proxy_running; then
    echo "Proxy already running (PID $(cat "$PROXY_PID"))"
else
    echo "Starting free-claude-code proxy (port 8082) ..."
    cd "$GATEWAY_DIR/proxy"
    nohup uv run uvicorn server:app --host 127.0.0.1 --port 8082 \
        >> "$PROXY_LOG" 2>&1 &
    echo $! > "$PROXY_PID"
    sleep 3
    if proxy_running; then
        echo "  Proxy started (PID $(cat "$PROXY_PID"))"
    else
        echo "  ERROR: Proxy failed — check $PROXY_LOG"
        exit 1
    fi
fi

# ── 3. gpt-oss-120b MLX server (optional) ────────────────────────────────────
GPT_PID="/tmp/ftm-gptoss.pid"
GPT_LOG="$LOG_DIR/ftm-gptoss.log"
GPT_MODEL="${GPT_MODEL:-$HOME/mlx_gemma4/models/gpt-oss-120b-bf16}"

gptoss_running() { [[ -f "$GPT_PID" ]] && kill -0 "$(cat "$GPT_PID")" 2>/dev/null; }

if gptoss_running; then
    echo "gpt-oss-120b server already running (PID $(cat "$GPT_PID"))"
elif [[ -d "$GPT_MODEL" ]]; then
    echo "Starting gpt-oss-120b server (port 8091) ..."
    cd "$GATEWAY_DIR"
    PORT=8091 MODEL_PATH="$GPT_MODEL" \
        nohup uv run --extra mlx python mlx_server_gptoss.py \
        >> "$GPT_LOG" 2>&1 &
    echo $! > "$GPT_PID"
    sleep 3
    if gptoss_running; then
        echo "  gpt-oss-120b started (PID $(cat "$GPT_PID"))"
    else
        echo "  WARNING: gpt-oss-120b may have failed — check $GPT_LOG"
    fi
else
    echo "  gpt-oss-120b model not found at $GPT_MODEL — skipping port 8091"
fi

echo ""
echo "ftm-llm-gateway running:"
echo "  Proxy     : http://localhost:8082  (SSH-tunnel accessible)"
echo "  Gemma4    : http://localhost:8090  (MacStudio local only)"
echo "  gpt-oss   : http://localhost:8091  (MacStudio local only)"
echo "  Ollama    : http://localhost:11434 (MacStudio local only)"
