#!/usr/bin/env bash
# cc-llm.sh — Claude Code LLM client (Phase 9C Rev3)
#
# Connects Claude Code CLI to ftm-llm-gateway on MacStudio via SSH tunnel.
# The proxy, Ollama, and MLX-VLM all run on MacStudio; this script is
# client-side only (tmm7, ofbiz-dev, rpitex, ltsp-rpi4b256, rpi5).
#
# USAGE
#   cc-llm.sh                              → auto-tunnel + claude (default model)
#   cc-llm.sh --model ollama/llama3.3:70b  → specific model
#   cc-llm.sh --model lmstudio/gemma4-mlx  → Apple Silicon MLX
#   cc-llm.sh --model ollama/ofbiz-think:latest  → OFBiz specialist
#   cc-llm.sh start|stop|restart|status|log      → manage gateway on MacStudio
#   cc-llm.sh model <haiku|sonnet|opus> <ref>    → update model tier on MacStudio
#
# ARCHITECTURE
#   client (this machine)
#     └── SSH tunnel  -L 8082:localhost:8082 → MacStudio (192.168.192.79)
#         └── ftm-llm-gateway/proxy  port 8082
#             ├── Ollama           port 11434  (local on MacStudio)
#             └── MLX-VLM server   port 8090   (local on MacStudio)
#
# INSTALL  (on each client machine)
#   cp cc-llm.sh ~/bin/cc-llm && chmod +x ~/bin/cc-llm
#   # or add ofbiz-plugins to PATH

set -euo pipefail

MACSTUDIO="texchi@192.168.192.79"
PROXY_PORT=8082
GATEWAY_DIR="\$HOME/ftm-llm-gateway"
GATEWAY_LOG="\$HOME/.claude/ftm-llm-gateway.log"
PROXY_PID="/tmp/ftm-proxy.pid"

# ── tunnel helpers ────────────────────────────────────────────────────────────

tunnel_alive() {
    curl -s --connect-timeout 2 \
        -H "Authorization: Bearer freecc" \
        "http://localhost:${PROXY_PORT}/health" > /dev/null 2>&1
}

ensure_tunnel() {
    if tunnel_alive; then
        return 0
    fi
    echo "Opening SSH tunnel → MacStudio (${MACSTUDIO}) port ${PROXY_PORT} ..."
    ssh -L "${PROXY_PORT}:localhost:${PROXY_PORT}" \
        "$MACSTUDIO" -N -f -o ConnectTimeout=10 -o BatchMode=yes \
        -o ExitOnForwardFailure=yes
    local i=0
    while ! tunnel_alive && (( i++ < 6 )); do sleep 1; done
    if tunnel_alive; then
        echo "Tunnel open."
    else
        echo "ERROR: proxy not reachable at localhost:${PROXY_PORT}"
        echo "  Is ftm-llm-gateway running? Run:  cc-llm.sh start"
        return 1
    fi
}

# ── MacStudio management (via SSH) ────────────────────────────────────────────

remote() { ssh "$MACSTUDIO" "$@"; }

cmd_start() {
    echo "Starting ftm-llm-gateway on MacStudio ..."
    remote "cd ${GATEWAY_DIR} && bash start.sh"
}

cmd_stop() {
    echo "Stopping ftm-llm-gateway on MacStudio ..."
    remote "cd ${GATEWAY_DIR} && bash stop.sh"
}

cmd_restart() {
    echo "Restarting ftm-llm-gateway on MacStudio ..."
    remote "cd ${GATEWAY_DIR} && bash stop.sh && sleep 2 && bash start.sh"
}

cmd_status() {
    echo "=== Tunnel ==="
    tunnel_alive && echo "OPEN  (localhost:${PROXY_PORT})" || echo "CLOSED"
    echo ""
    echo "=== MacStudio gateway ==="
    remote "cd ${GATEWAY_DIR} && bash status.sh" 2>/dev/null || \
        echo "(SSH to MacStudio failed)"
}

cmd_log() {
    echo "=== MacStudio gateway log ==="
    remote "tail -50 ${GATEWAY_LOG}" 2>/dev/null || echo "(SSH failed)"
}

cmd_model() {
    local tier="${1:-}" model="${2:-}"
    [[ -z "$tier" || -z "$model" ]] && {
        echo "Usage: $0 model <haiku|sonnet|opus> <provider/model>"
        exit 1
    }
    local key="MODEL_$(echo "$tier" | tr '[:lower:]' '[:upper:]')"
    local env_file="\$HOME/.config/free-claude-code/.env"
    remote "
      if grep -q '^${key}=' '${env_file}'; then
        sed -i '' 's|^${key}=.*|${key}=\"${model}\"|' '${env_file}'
      else
        echo '${key}=\"${model}\"' >> '${env_file}'
      fi
      echo 'Updated ${key} → ${model}'
    "
    echo "Restart gateway to apply: cc-llm.sh restart"
}

# ── launch ────────────────────────────────────────────────────────────────────

cmd_launch() {
    local model_ref="${1:-}"

    ensure_tunnel

    echo ""
    if [[ -n "$model_ref" ]]; then
        echo "  Backend : $model_ref"
    else
        echo "  Backend : default (MODEL_SONNET on MacStudio)"
    fi
    echo "  Proxy   : http://localhost:${PROXY_PORT}"
    echo "  Picker  : /model  — switch backends mid-session"
    echo ""

    if [[ -n "$model_ref" ]]; then
        ANTHROPIC_BASE_URL="http://localhost:${PROXY_PORT}" \
        ANTHROPIC_AUTH_TOKEN="freecc" \
        claude --model "$model_ref"
    else
        ANTHROPIC_BASE_URL="http://localhost:${PROXY_PORT}" \
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
    --model)
        [[ -z "${2:-}" ]] && { echo "Usage: $0 --model <provider/model>"; exit 1; }
        cmd_launch "$2"
        ;;
    launch|"") cmd_launch "" ;;
    *)
        echo "Usage: $0 [--model <provider/model>]"
        echo "       $0 {start|stop|restart|status|log}"
        echo "       $0 model <haiku|sonnet|opus> <provider/model>"
        echo ""
        echo "Launch:"
        echo "  $0                                      default (ollama/llama3.3:70b)"
        echo "  $0 --model ollama/ofbiz-think:latest    OFBiz specialist"
        echo "  $0 --model lmstudio/gemma4-mlx          Apple Silicon MLX"
        echo "  $0 --model ollama/gemma4-ofbiz:latest   Gemma4 OFBiz fine-tune"
        echo ""
        echo "Gateway management (runs on MacStudio via SSH):"
        echo "  $0 start / stop / restart / status / log"
        echo "  $0 model sonnet ollama/llama3.3:70b"
        ;;
esac
