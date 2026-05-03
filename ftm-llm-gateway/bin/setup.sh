#!/usr/bin/env bash
# setup.sh — One-time setup of ftm-llm-gateway on MacStudio
# Run from: ~/ftm-llm-gateway/  after cloning
# Prerequisites: Homebrew, git, uv, Python 3.14 (brew install python@3.14)

set -euo pipefail
GATEWAY_DIR="$(cd "$(dirname "$0")/.." && pwd)"
export PATH="/opt/homebrew/bin:$HOME/.local/bin:$PATH"

echo "=== ftm-llm-gateway setup on $(hostname) ==="

# ── 1. Clone free-claude-code proxy into proxy/ ───────────────────────────────
if [[ ! -d "$GATEWAY_DIR/proxy/.git" ]]; then
    echo "Cloning free-claude-code proxy ..."
    git clone https://github.com/Alishahryar1/free-claude-code \
        "$GATEWAY_DIR/proxy"
fi

# Pin proxy to stable Python 3.14.3 (avoid alpha segfault)
echo "3.14.3" > "$GATEWAY_DIR/proxy/.python-version"
echo "Pinned proxy Python → 3.14.3"

# Install proxy deps
cd "$GATEWAY_DIR/proxy"
uv venv --python "$(brew --prefix python@3.14)/bin/python3.14" --allow-existing
uv sync
echo "Proxy deps installed."

# ── 2. Install MLX-VLM deps for mlx_server.py ────────────────────────────────
cd "$GATEWAY_DIR"
# Use system Python for MLX (Apple Silicon native)
uv venv --python "$(brew --prefix python@3.11)/bin/python3.11" 2>/dev/null || \
uv venv --python python3 --allow-existing
uv sync --extra mlx
echo "MLX-VLM deps installed."

# ── 3. Config ─────────────────────────────────────────────────────────────────
CONF_DIR="$HOME/.config/free-claude-code"
mkdir -p "$CONF_DIR"
if [[ ! -f "$CONF_DIR/.env" ]]; then
    cp "$GATEWAY_DIR/config/.env.example" "$CONF_DIR/.env"
    echo "Config created at $CONF_DIR/.env  ← edit MODEL_PATH if needed"
else
    echo "Config already exists at $CONF_DIR/.env  (not overwritten)"
fi

# ── 4. local.env for MODEL_PATH ───────────────────────────────────────────────
LOCAL_CONF="$GATEWAY_DIR/config/local.env"
if [[ ! -f "$LOCAL_CONF" ]]; then
    # Try to auto-detect model path
    DETECTED=$(find "$GATEWAY_DIR/models" "$HOME/mlx_gemma4/models" \
        -maxdepth 1 -name "*.safetensors" 2>/dev/null | head -1 | xargs dirname 2>/dev/null || true)
    MODEL_PATH="${DETECTED:-$GATEWAY_DIR/models/gemma-4-31b-bf16}"
    echo "MODEL_PATH=\"$MODEL_PATH\"" > "$LOCAL_CONF"
    echo "MODEL_PATH set to $MODEL_PATH  ← edit $LOCAL_CONF if wrong"
fi

# ── 5. Make scripts executable ────────────────────────────────────────────────
chmod +x "$GATEWAY_DIR/bin/"*.sh

# ── 6. Optionally copy existing mlx model ────────────────────────────────────
if [[ -d "$HOME/mlx_gemma4/models" && ! -d "$GATEWAY_DIR/models" ]]; then
    echo "Symlinking models from ~/mlx_gemma4/models ..."
    ln -s "$HOME/mlx_gemma4/models" "$GATEWAY_DIR/models"
fi

echo ""
echo "=== Setup complete ==="
echo "  Start:  cd $GATEWAY_DIR && bash bin/start.sh"
echo "  Status: bash bin/status.sh"
echo "  Clients tunnel:  ssh -L 8082:localhost:8082 texchi@$(hostname -I | awk '{print $1}') -N -f"
