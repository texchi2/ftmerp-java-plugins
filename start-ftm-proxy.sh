#!/usr/bin/env bash
# start-ftm-proxy.sh — Phase 9C Rev3  (proxy on tmm7, providers on MacStudio)
#
# USAGE
#   start-ftm-proxy.sh                              → start proxy + claude (default model from .env)
#   start-ftm-proxy.sh --model ollama/llama3.3:70b  → start proxy + claude, specific model
#   start-ftm-proxy.sh --model lmstudio/gemma4-mlx  → start proxy + claude via MLX server
#   start-ftm-proxy.sh start|stop|restart|status|log
#   start-ftm-proxy.sh model <haiku|sonnet|opus> <provider/model>
#
# ALIASES  (source ~/.zshrc after changes)
#   cc-local  → default local session   (ollama/llama3.3:70b)
#   cc-ollama → ofbiz-think session     (ollama/ofbiz-think:latest  ← built on gpt-oss:120b)
#   cc-mlx    → MLX Apple Silicon       (lmstudio/gemma4-mlx  ← requires: tunnel-mlx)
#
# PROVIDERS (all on MacStudio 192.168.192.79, tunnelled to localhost)
#   Ollama  → localhost:11434  (tunnel-ollama)
#   MLX-VLM → localhost:8090   (tunnel-mlx)
#
# PROXY vs CLOUD
#   Local (this script) → ANTHROPIC_BASE_URL=localhost:8082 → MacStudio providers (free)
#   Cloud (cc/cc-sonnet) → Desktop OAuth → Anthropic API    (billed)

set -euo pipefail

PROXY_DIR="$HOME/development/free-claude-code"
PROXY_PORT=8082
LOG_FILE="$HOME/.claude/free-claude-code.log"
PID_FILE="/tmp/ftm-proxy.pid"
# Homebrew stable Python 3.14.3 — avoids alpha 3.14.0a5 segfault from uv default
export PATH="/opt/homebrew/bin:$HOME/.local/bin:$PATH"

# ── helpers ──────────────────────────────────────────────────────────────────

proxy_running() {
    [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null
}

start_proxy() {
    if proxy_running; then
        echo "Proxy already running (PID $(cat "$PID_FILE"))"
        return 0
    fi
    echo "Starting free-claude-code proxy on port $PROXY_PORT ..."
    cd "$PROXY_DIR"
    nohup uv run uvicorn server:app --host 127.0.0.1 --port "$PROXY_PORT" \
        >> "$LOG_FILE" 2>&1 &
    echo $! > "$PID_FILE"
    sleep 2
    if proxy_running; then
        echo "Proxy started (PID $(cat "$PID_FILE"))"
    else
        echo "ERROR: Proxy failed to start. Check: $LOG_FILE"
        return 1
    fi
}

stop_proxy() {
    if proxy_running; then
        kill "$(cat "$PID_FILE")" && rm -f "$PID_FILE"
        echo "Proxy stopped."
    else
        echo "Proxy not running."
    fi
}

# ── subcommands ───────────────────────────────────────────────────────────────

cmd_start()   { start_proxy; }
cmd_stop()    { stop_proxy; }
cmd_restart() { stop_proxy; sleep 1; start_proxy; }

cmd_status() {
    if proxy_running; then
        echo "Proxy RUNNING (PID $(cat "$PID_FILE"), port $PROXY_PORT)"
        curl -s -H "Authorization: Bearer freecc" \
            http://localhost:$PROXY_PORT/ 2>/dev/null | python3 -m json.tool 2>/dev/null || true
    else
        echo "Proxy STOPPED"
    fi
}

cmd_log() { tail -f "$LOG_FILE"; }

cmd_model() {
    # Usage: start-ftm-proxy.sh model sonnet ollama/llama3.3:70b
    local tier="${1:-}" model="${2:-}"
    [[ -z "$tier" || -z "$model" ]] && {
        echo "Usage: $0 model <haiku|sonnet|opus> <provider/model>"
        exit 1
    }
    local env_file="$HOME/.config/free-claude-code/.env"
    # tr for uppercase — bash 3.2 on macOS lacks \${var^^}
    local key="MODEL_$(echo "$tier" | tr '[:lower:]' '[:upper:]')"
    if grep -q "^${key}=" "$env_file"; then
        sed -i '' "s|^${key}=.*|${key}=\"${model}\"|" "$env_file"
    else
        echo "${key}=\"${model}\"" >> "$env_file"
    fi
    echo "Updated $key → $model  (restart proxy to apply)"
}

cmd_launch() {
    # $1 optional: --model <provider/model>
    local model_flag=""
    if [[ "${1:-}" == "--model" && -n "${2:-}" ]]; then
        model_flag="$2"
    fi

    start_proxy

    echo ""
    if [[ -n "$model_flag" ]]; then
        echo "  Backend : $model_flag"
    else
        echo "  Backend : default (MODEL_SONNET from ~/.config/free-claude-code/.env)"
    fi
    echo "  Proxy   : http://localhost:$PROXY_PORT"
    echo "  Picker  : /model  → switch backends mid-session"
    echo ""

    if [[ -n "$model_flag" ]]; then
        ANTHROPIC_BASE_URL="http://localhost:$PROXY_PORT" \
        ANTHROPIC_AUTH_TOKEN="freecc" \
        claude --model "$model_flag"
    else
        ANTHROPIC_BASE_URL="http://localhost:$PROXY_PORT" \
        ANTHROPIC_AUTH_TOKEN="freecc" \
        claude
    fi
}

# ── dispatch ──────────────────────────────────────────────────────────────────

case "${1:-launch}" in
    start)    cmd_start ;;
    stop)     cmd_stop ;;
    restart)  cmd_restart ;;
    status)   cmd_status ;;
    log)      cmd_log ;;
    model)    shift; cmd_model "$@" ;;
    --model)  cmd_launch "$@" ;;    # start-ftm-proxy.sh --model <ref>
    launch|"") cmd_launch "$@" ;;  # bare call or explicit 'launch'
    *)
        echo "Usage: $0 [--model <provider/model>]"
        echo "       $0 {start|stop|restart|status|log}"
        echo "       $0 model <haiku|sonnet|opus> <provider/model>"
        echo ""
        echo "Launch modes:"
        echo "  $0                                    default (ollama/llama3.3:70b)"
        echo "  $0 --model ollama/ofbiz-think:latest  OFBiz specialist (gpt-oss:120b base)"
        echo "  $0 --model lmstudio/gemma4-mlx        Apple Silicon MLX  (needs tunnel-mlx)"
        echo "  $0 --model ollama/llama3.3:70b        explicit llama3.3"
        echo ""
        echo "Proxy management:"
        echo "  $0 model sonnet ollama/llama3.3:70b   update default tier in .env"
        echo "  $0 restart                            apply .env changes"
        echo "  $0 status                             show running model"
        echo "  $0 log                                tail proxy log"
        ;;
esac
