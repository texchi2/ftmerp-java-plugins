#!/bin/bash
# ftm-ide — FTM Claude Code CLI-based IDE
# Usage:
#   ftm-ide                                    # default cc-sonnet
#   ftm-ide /opt/ofbiz-plugins cc-ofbiz       # local Ollama (gemma4)
#   ftm-ide /opt/ofbiz-plugins cc-llama       # llama3.3:70b
#   ftm-ide /opt/ofbiz-plugins cc-fast        # gemma3:12b (Pi3)
#
# Machine-aware via ~/.ftm-machine.env (FTM_MACHINE_NAME)
# Windows: ofbiz-dev | db-superset | camel-ollama | tests | git
set -e

SESSION="ftm-dev"

# Source machine env first so FTM_MACHINE_PATH can serve as default PROJECT_DIR
[ -f "$HOME/.ftm-machine.env" ] && source "$HOME/.ftm-machine.env"
MACHINE="${FTM_MACHINE_NAME:-$(hostname)}"

# Default project dir: machine env > /opt/ofbiz-plugins (Incus container only)
PROJECT_DIR="${1:-${FTM_MACHINE_PATH:-/opt/ofbiz-plugins}}"
MODEL_ALIAS="${2:-cc}"
FRAMEWORK_DIR="$(dirname "$PROJECT_DIR")/ofbiz-framework"
LOG_FILE="$FRAMEWORK_DIR/runtime/logs/ofbiz.log"

# Auto-select lighter model on ltsp-rpi4b256
if [ "$MACHINE" = "ltsp-rpi4b256" ] && [ "${2:-}" = "" ]; then
  MODEL_ALIAS="cc-fast"
fi

if tmux has-session -t "$SESSION" 2>/dev/null; then
  echo "Attaching to existing session..."
  tmux attach -t "$SESSION"
  exit 0
fi

echo "Starting FTM IDE on $MACHINE (model: $MODEL_ALIAS)..."

# ── Window 0: OFBiz Development (tiled 4-pane) ─────────────────────────────
tmux new-session -d -s "$SESSION" -n "ofbiz-dev" -x 220 -y 50

# Pane 0 top-left: vim
tmux send-keys -t "$SESSION:0.0" "cd $PROJECT_DIR && vim ." C-m

# Pane 1 top-right: Claude Code
tmux split-window -h -t "$SESSION:0.0"
tmux send-keys -t "$SESSION:0.1" \
  "cd $PROJECT_DIR && ftm-sync && $MODEL_ALIAS" C-m

# Pane 2 bottom-left: OFBiz log tailer
tmux split-window -v -t "$SESSION:0.0"
tmux send-keys -t "$SESSION:0.2" \
  "while [ ! -f $LOG_FILE ]; do sleep 2; done && \
   tail -f $LOG_FILE | grep --line-buffered \
   -E 'ERROR|WARN|INFO.*Started|Groovy|FTM'" C-m

# Pane 3 bottom-right: Git + shell
tmux split-window -v -t "$SESSION:0.1"
tmux send-keys -t "$SESSION:0.3" \
  "cd $PROJECT_DIR && git status --short" C-m

tmux select-layout -t "$SESSION:0" tiled

# ── Window 1: Database + Superset (Phase 10A ready) ────────────────────────
tmux new-window -t "$SESSION" -n "db-superset"
tmux send-keys -t "$SESSION:1.0" \
  "psql -h 192.168.30.3 -U ftmuser -d ftmerp" C-m
tmux split-window -h -t "$SESSION:1.0"
tmux send-keys -t "$SESSION:1.1" \
  "echo 'Superset: docker logs -f ftm-superset (Phase 10A)'" C-m
tmux split-window -v -t "$SESSION:1.0"
tmux send-keys -t "$SESSION:1.2" \
  "psql -h 192.168.30.3 -U mcp_readonly -d ftmerp \
   -c 'SELECT extname FROM pg_extension;'" C-m

# ── Window 2: Camel + Ollama (Phase 10B ready) ─────────────────────────────
tmux new-window -t "$SESSION" -n "camel-ollama"
tmux send-keys -t "$SESSION:2.0" \
  "ls /opt/camel-integration 2>/dev/null || \
   echo 'Phase 10B not yet deployed'" C-m
tmux split-window -h -t "$SESSION:2.0"
tmux send-keys -t "$SESSION:2.1" \
  "ss -tlnp | grep 11434 && \
   curl -s http://127.0.0.1:11434/api/tags | \
   python3 -m json.tool | grep name | head -5" C-m

# ── Window 3: Tests (HTTP watcher + gradle, always-on) ─────────────────────
tmux new-window -t "$SESSION" -n "tests"
tmux send-keys -t "$SESSION:3.0" \
  'watch -n 10 "curl -s -o /dev/null -w \"ftm-wifi:%{http_code} \" \
   http://192.168.30.102:8080/ftm-wifi/control/FindAuthorizedUsers && \
   curl -s -o /dev/null -w \"webtools:%{http_code}\n\" \
   http://192.168.30.102:8080/webtools/control/main"' C-m
tmux split-window -h -t "$SESSION:3.0"
tmux send-keys -t "$SESSION:3.1" \
  "cd $FRAMEWORK_DIR && echo 'Run: ./gradlew test'" C-m

# ── Window 4: Git Dashboard (both repos side by side) ──────────────────────
tmux new-window -t "$SESSION" -n "git"
tmux send-keys -t "$SESSION:4.0" \
  "cd $PROJECT_DIR && git log --oneline -10" C-m
tmux split-window -h -t "$SESSION:4.0"
tmux send-keys -t "$SESSION:4.1" \
  "cd $FRAMEWORK_DIR && git log --oneline -5" C-m

# ── Status bar ──────────────────────────────────────────────────────────────
tmux set-option -t "$SESSION" status-right \
  "#[fg=green]$MACHINE #[fg=yellow]| OFBiz:8080 #[fg=cyan]| %H:%M"
tmux set-option -t "$SESSION" status-left \
  "#[fg=blue,bold]FTM-IDE #[fg=white]| "
tmux set-option -t "$SESSION" status-style "bg=black,fg=white"
tmux set-option -t "$SESSION" pane-border-status top
tmux set-option -t "$SESSION" pane-border-format \
  " #{pane_index}: #{pane_title} "

# Focus Claude Code pane on startup
tmux select-window -t "$SESSION:0"
tmux select-pane -t "$SESSION:0.1"

echo "Windows: ofbiz-dev | db-superset | camel-ollama | tests | git"
tmux attach -t "$SESSION"
