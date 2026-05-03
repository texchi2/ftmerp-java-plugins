#!/usr/bin/env bash
# start.sh — Start MLX-VLM server (8090) + free-claude-code proxy (8082) on MacStudio
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

echo ""
echo "ftm-llm-gateway running:"
echo "  Proxy  : http://localhost:8082  (SSH-tunnel accessible)"
echo "  MLX    : http://localhost:8090  (MacStudio local only)"
echo "  Ollama : http://localhost:11434 (MacStudio local only)"
