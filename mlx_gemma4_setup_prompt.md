# Handoff prompt for Claude Code CLI instance on MacStudio (~/mlx_gemma4/)

Paste this entire message as the first prompt to the Claude Code session inside ~/mlx_gemma4/.

---

## Context: what you are setting up

You are the Claude Code instance on **MacStudio** (192.168.192.79).
Your job is to configure the MLX-VLM server so it can be used by a
**free-claude-code proxy** running on a remote client machine (tmm7).

### Port layout (do not mix these up)

| Port | Service | Machine |
|------|---------|---------|
| 8090 | MLX-VLM server (this machine) | MacStudio |
| 8082 | free-claude-code proxy | tmm7 (client) |
| 11434 | Ollama | MacStudio |
| 8080 | OFBiz HTTP — **DO NOT USE** | ofbiz-dev |

The client (tmm7) opens an SSH tunnel:
  ssh -L 8090:localhost:8090 texchi@192.168.192.79 -N -f
After that, `http://localhost:8090` on tmm7 reaches this MLX server.

---

## Task 1 — Fix server.py port (8080 → 8090)

Read `~/mlx_gemma4/server.py`. Find the PORT default and change it:

```python
# Before
PORT = int(os.environ.get("PORT", "8080"))

# After
PORT = int(os.environ.get("PORT", "8090"))
```

Also update the docstring at the top if it mentions port 8080:
```python
# Before
    export ANTHROPIC_BASE_URL=http://localhost:8080

# After
    export ANTHROPIC_BASE_URL=http://localhost:8090
```

---

## Task 2 — Verify server.py dependencies

The server.py must import these from mlx-vlm:
```python
from mlx_vlm import load, stream_generate
from mlx_vlm.prompt_utils import apply_chat_template
from mlx_vlm.utils import load_config
```

Check the installed mlx-vlm version and that these imports work:
```bash
uv run python -c "from mlx_vlm import load, stream_generate; from mlx_vlm.prompt_utils import apply_chat_template; from mlx_vlm.utils import load_config; print('imports ok')"
```

If `apply_chat_template` or `load_config` import fails (API changed in newer
mlx-vlm versions), use this safer fallback pattern in server.py:

```python
try:
    from mlx_vlm.prompt_utils import apply_chat_template
    from mlx_vlm.utils import load_config
except ImportError:
    apply_chat_template = None
    load_config = None
```

And in `_build_prompt`, guard the call:
```python
if apply_chat_template is not None and load_config is not None:
    try:
        prompt = apply_chat_template(_processor, _config, hf_msgs, ...)
        ...
    except Exception:
        pass
```

---

## Task 3 — Find the correct MODEL_PATH

Run:
```bash
ls ~/mlx_gemma4/models/ 2>/dev/null || find ~/mlx_gemma4 -name "*.safetensors" -maxdepth 4 | head -5
```

The default in server.py is `./models/gemma-4-31b-bf16`. If the model lives
elsewhere, update the default or add the path to the start command.

---

## Task 4 — Start the server

```bash
cd ~/mlx_gemma4
PORT=8090 MODEL_PATH=./models/gemma-4-31b-bf16 uv run python server.py
```

Replace `./models/gemma-4-31b-bf16` with the actual path you found in Task 3.

Verify it is up:
```bash
curl http://localhost:8090/v1/models
# Expected: {"object":"list","data":[{"id":"gemma4",...}]}
```

---

## Task 5 — Set LM_STUDIO_BASE_URL in the proxy config on tmm7

This step is done on **tmm7** (the client), not MacStudio.
The file to edit is: `~/.config/free-claude-code/.env` on tmm7.

Add or confirm this line exists:
```
LM_STUDIO_BASE_URL="http://localhost:8090"
```

The free-claude-code proxy uses the `lmstudio` backend, which sends
`POST http://localhost:8090/v1/messages` — exactly what this server exposes.
After the client opens `tunnel-mlx`, the proxy can reach this server.

---

## Task 6 — Fix the auth conflict (IMPORTANT)

### The problem

When you run:
```bash
ANTHROPIC_BASE_URL=http://localhost:8090 ANTHROPIC_API_KEY=local claude
```

Claude Code shows:
```
⚠ Auth conflict: Both a token (claude.ai) and an API key (ANTHROPIC_API_KEY)
  are set. This may lead to unexpected behavior.
```

This happens because Claude Code already has an OAuth session token stored
in the macOS Keychain (`"Claude Code-credentials"`), AND you are also setting
`ANTHROPIC_API_KEY`. Claude Code does not know which one to use.

### The fix — two options

**Option A (recommended): use ANTHROPIC_AUTH_TOKEN instead of ANTHROPIC_API_KEY**

`ANTHROPIC_AUTH_TOKEN` is the variable the free-claude-code proxy pattern uses.
It does not trigger the OAuth conflict check:

```bash
ANTHROPIC_BASE_URL=http://localhost:8090 \
ANTHROPIC_AUTH_TOKEN=local \
claude
```

The MLX server does not validate auth headers, so any string works.

**Option B (destructive): logout from claude.ai first**

Only do this if Option A does not work. This permanently removes the OAuth
session on this machine:

```bash
claude /logout
# When asked "Remove API key too?" → answer No
# Now run:
ANTHROPIC_BASE_URL=http://localhost:8090 ANTHROPIC_API_KEY=local claude
```

To restore cloud access later: `claude /login`

**Option C (cleanest, same as tmm7): run the free-claude-code proxy here too**

Install the proxy on MacStudio and point it at the local MLX server:
```bash
git clone https://github.com/Alishahryar1/free-claude-code ~/development/free-claude-code
cd ~/development/free-claude-code
echo '3.14.3' > .python-version          # use stable Python, not alpha
uv venv --python /opt/homebrew/opt/python@3.14/bin/python3.14
uv sync
```

Create `~/.config/free-claude-code/.env`:
```
ANTHROPIC_AUTH_TOKEN="freecc"
LM_STUDIO_BASE_URL="http://localhost:8090"
MODEL_HAIKU="lmstudio/gemma4"
MODEL_SONNET="lmstudio/gemma4"
MODEL_OPUS="lmstudio/gemma4"
MODEL="lmstudio/gemma4"
ENABLE_NETWORK_PROBE_MOCK=true
ENABLE_TITLE_GENERATION_SKIP=true
ENABLE_SUGGESTION_MODE_SKIP=true
ENABLE_FILEPATH_EXTRACTION_MOCK=true
MESSAGING_PLATFORM="none"
```

Start proxy and Claude Code:
```bash
cd ~/development/free-claude-code
nohup uv run uvicorn server:app --host 127.0.0.1 --port 8082 &
ANTHROPIC_BASE_URL=http://localhost:8082 ANTHROPIC_AUTH_TOKEN=freecc claude
```

No auth conflict. No logout needed. `/model` picker shows all Ollama + MLX models.

---

## Summary of what you need to do (in order)

1. Edit `server.py`: PORT default 8080 → 8090
2. Verify imports work with `uv run python -c "..."`
3. Find MODEL_PATH, start server on port 8090
4. Verify: `curl http://localhost:8090/v1/models`
5. Use **Option A** to start Claude Code without auth conflict:
   `ANTHROPIC_BASE_URL=http://localhost:8090 ANTHROPIC_AUTH_TOKEN=local claude`
6. If you want the full proxy setup (Option C), follow those steps instead

After the server is running on port 8090 and is reachable from MacStudio's
localhost, the tmm7 client can use `tunnel-mlx` + the free-claude-code proxy
to route requests through it via `lmstudio/gemma4` in the `/model` picker.
