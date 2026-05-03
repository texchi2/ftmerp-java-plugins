#!/usr/bin/env python3
"""
Anthropic Messages API server backed by Gemma 4 on Apple MLX (via mlx-vlm).

Usage:
    MODEL_PATH=./models/gemma-4-31b-it-bf16 uv run python mlx_server.py

Claude Code integration (direct):
    export ANTHROPIC_BASE_URL=http://localhost:8090
    export ANTHROPIC_AUTH_TOKEN=local
    claude

Claude Code integration (via ftm-llm-gateway proxy):
    ANTHROPIC_BASE_URL=http://localhost:8082 ANTHROPIC_AUTH_TOKEN=freecc claude
    then use /model picker → lmstudio/gemma4-mlx
"""

import json
import os
import time
import uuid
from typing import AsyncIterator

import uvicorn
from fastapi import FastAPI, HTTPException
from fastapi.responses import JSONResponse, StreamingResponse
from mlx_vlm import load, stream_generate

try:
    from mlx_vlm.prompt_utils import apply_chat_template
    from mlx_vlm.utils import load_config
except ImportError:
    apply_chat_template = None  # type: ignore[assignment]
    load_config = None          # type: ignore[assignment]

from pydantic import BaseModel, Field

# ---------------------------------------------------------------------------
# Config
# ---------------------------------------------------------------------------

MODEL_PATH = os.environ.get("MODEL_PATH", "./models/gemma-4-31b-it-bf16")
HOST = os.environ.get("HOST", "127.0.0.1")
PORT = int(os.environ.get("PORT", "8090"))
MAX_TOKENS_DEFAULT = int(os.environ.get("MAX_TOKENS_DEFAULT", "2048"))

# ---------------------------------------------------------------------------
# Model (loaded once at startup)
# ---------------------------------------------------------------------------

print(f"Loading model from {MODEL_PATH} …", flush=True)
_model, _processor = load(MODEL_PATH)
_config = load_config(MODEL_PATH) if load_config is not None else {}
print("Model ready.", flush=True)

# ---------------------------------------------------------------------------
# Pydantic schemas (Anthropic Messages API subset)
# ---------------------------------------------------------------------------


class ContentBlock(BaseModel):
    type: str = "text"
    text: str


class Message(BaseModel):
    role: str  # "user" | "assistant"
    content: str | list[ContentBlock]


class MessagesRequest(BaseModel):
    model: str = "gemma4"
    messages: list[Message]
    # Anthropic API allows system as string OR list of content blocks
    system: str | list[ContentBlock] | None = None
    max_tokens: int = Field(default=MAX_TOKENS_DEFAULT)
    temperature: float = 0.7
    top_p: float = 0.9
    stream: bool = False
    # Claude Code sends tool definitions; accepted but ignored by MLX server
    tools: list | None = None
    tool_choice: dict | None = None

    def system_text(self) -> str | None:
        if self.system is None:
            return None
        if isinstance(self.system, str):
            return self.system
        return " ".join(b.text for b in self.system if b.type == "text") or None


# ---------------------------------------------------------------------------
# Prompt construction
# ---------------------------------------------------------------------------

# Gemma 4 chat template (fallback when tokenizer has no chat_template field).
_GEMMA4_TMPL = (
    "{% for message in messages %}"
    "{% if message['role'] == 'system' %}"
    "<start_of_turn>user\n{{ message['content'] }}<end_of_turn>\n"
    "{% else %}"
    "<start_of_turn>{{ message['role'] }}\n{{ message['content'] }}<end_of_turn>\n"
    "{% endif %}"
    "{% endfor %}"
    "{% if add_generation_prompt %}<start_of_turn>model\n{% endif %}"
)


def _build_prompt(req: MessagesRequest) -> str:
    """Convert Anthropic messages to a Gemma4 chat prompt string."""
    hf_msgs: list[dict] = []
    sys_text = req.system_text()
    if sys_text:
        hf_msgs.append({"role": "system", "content": sys_text})
    for msg in req.messages:
        text = (
            msg.content
            if isinstance(msg.content, str)
            else " ".join(b.text for b in msg.content)
        )
        hf_msgs.append({"role": msg.role, "content": text})

    # 1. Try processor's built-in chat template.
    tokenizer = getattr(_processor, "tokenizer", _processor)
    if getattr(tokenizer, "chat_template", None):
        return tokenizer.apply_chat_template(
            hf_msgs, tokenize=False, add_generation_prompt=True
        )

    # 2. Fall back to mlx_vlm's apply_chat_template.
    if apply_chat_template is not None:
        try:
            prompt = apply_chat_template(
                _processor, _config, hf_msgs, add_generation_prompt=True
            )
            if isinstance(prompt, str):
                return prompt
        except Exception:
            pass

    # 3. Last resort: manual Gemma 4 Jinja2 template.
    from jinja2 import Environment
    env = Environment()
    tmpl = env.from_string(_GEMMA4_TMPL)
    return tmpl.render(messages=hf_msgs, add_generation_prompt=True)


# ---------------------------------------------------------------------------
# Generation
# ---------------------------------------------------------------------------


def _iter_tokens(prompt: str, req: MessagesRequest):
    """Yield text chunks from mlx_vlm stream_generate."""
    for result in stream_generate(
        _model,
        _processor,
        prompt=prompt,
        image=None,
        max_tokens=req.max_tokens,
        temperature=req.temperature,
        top_p=req.top_p,
    ):
        yield result.text if hasattr(result, "text") else str(result)


def _generate_sync(prompt: str, req: MessagesRequest) -> str:
    return "".join(_iter_tokens(prompt, req))


# ---------------------------------------------------------------------------
# SSE helpers (Anthropic streaming event format)
# ---------------------------------------------------------------------------


def _sse(event: str, data: dict) -> str:
    return f"event: {event}\ndata: {json.dumps(data)}\n\n"


async def _sse_stream(
    msg_id: str, model: str, prompt: str, req: MessagesRequest
) -> AsyncIterator[str]:
    yield _sse(
        "message_start",
        {
            "type": "message_start",
            "message": {
                "id": msg_id,
                "type": "message",
                "role": "assistant",
                "content": [],
                "model": model,
                "stop_reason": None,
                "stop_sequence": None,
                "usage": {"input_tokens": 0, "output_tokens": 0},
            },
        },
    )
    yield _sse(
        "content_block_start",
        {
            "type": "content_block_start",
            "index": 0,
            "content_block": {"type": "text", "text": ""},
        },
    )
    yield _sse("ping", {"type": "ping"})

    output_tokens = 0
    for token_text in _iter_tokens(prompt, req):
        if not token_text:
            continue
        output_tokens += 1
        yield _sse(
            "content_block_delta",
            {
                "type": "content_block_delta",
                "index": 0,
                "delta": {"type": "text_delta", "text": token_text},
            },
        )

    yield _sse("content_block_stop", {"type": "content_block_stop", "index": 0})
    yield _sse(
        "message_delta",
        {
            "type": "message_delta",
            "delta": {"stop_reason": "end_turn", "stop_sequence": None},
            "usage": {"output_tokens": output_tokens},
        },
    )
    yield _sse("message_stop", {"type": "message_stop"})


# ---------------------------------------------------------------------------
# FastAPI app
# ---------------------------------------------------------------------------

app = FastAPI(title="MLX Gemma4 – Anthropic API", version="0.2.0")


@app.get("/v1/models")
async def list_models():
    return {
        "object": "list",
        "data": [
            {
                "id": "gemma4",
                "object": "model",
                "created": int(time.time()),
                "owned_by": "local",
            }
        ],
    }


@app.post("/v1/messages")
async def messages(req: MessagesRequest):
    if not req.messages:
        raise HTTPException(status_code=400, detail="messages must not be empty")

    msg_id = f"msg_{uuid.uuid4().hex[:24]}"
    prompt = _build_prompt(req)

    if req.stream:
        return StreamingResponse(
            _sse_stream(msg_id, req.model, prompt, req),
            media_type="text/event-stream",
            headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
        )

    text = _generate_sync(prompt, req)
    return JSONResponse(
        {
            "id": msg_id,
            "type": "message",
            "role": "assistant",
            "content": [{"type": "text", "text": text}],
            "model": req.model,
            "stop_reason": "end_turn",
            "stop_sequence": None,
            "usage": {
                "input_tokens": len(prompt.split()),
                "output_tokens": len(text.split()),
            },
        }
    )


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    uvicorn.run(app, host=HOST, port=PORT, log_level="info")
