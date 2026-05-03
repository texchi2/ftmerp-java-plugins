# ftm-llm-gateway

**FTM LLM Gateway** — Ollama + MLX-VLM server for Claude Code CLI on Apple Silicon.

Runs on MacStudio (or any Apple Silicon Mac). Exposes a single port (8082) that
Claude Code clients access via SSH tunnel. Internally routes to Ollama and the
MLX-VLM server based on the model requested.

```
Client (tmm7 / ofbiz-dev / rpitex / rpi5)
  ssh -L 8082:localhost:8082 texchi@192.168.192.79
      └── free-claude-code proxy  :8082
          ├── Ollama               :11434  (llama3.3:70b, ofbiz-think, gemma4-ofbiz, ...)
          └── MLX-VLM server       :8090   (gemma4-31b, Apple Silicon native speed)
```

## Setup (on MacStudio)

```bash
git clone <this-repo> ~/ftm-llm-gateway
cd ~/ftm-llm-gateway
bash bin/setup.sh
```

Edit `config/local.env` to set `MODEL_PATH` for the MLX model.

## Usage

```bash
bash bin/start.sh    # start everything
bash bin/stop.sh     # stop everything
bash bin/status.sh   # check status
```

## Client setup

Install `cc-llm.sh` from [ftm-wifi-enrollment/ofbiz-plugins](https://github.com/texchi2/ofbiz-plugins):

```bash
# On each client machine
cp cc-llm.sh ~/bin/cc-llm && chmod +x ~/bin/cc-llm
```

Add to `~/.zshrc` / `~/.bashrc`:
```bash
alias cc-local='cc-llm.sh'
alias cc-ollama='cc-llm.sh --model ollama/ofbiz-think:latest'
alias cc-mlx='cc-llm.sh --model lmstudio/gemma4-mlx'
alias tunnel-llm='ssh -L 8082:localhost:8082 texchi@192.168.192.79 -N -f && echo LLM Gateway tunnel open'
```
