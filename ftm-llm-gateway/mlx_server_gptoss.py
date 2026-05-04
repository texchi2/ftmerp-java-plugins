#!/usr/bin/env python3
"""
Anthropic Messages API server backed by gpt-oss-120b on Apple MLX (via mlx-lm).

This is a second server instance alongside mlx_server.py (Gemma4/VLM).
Uses mlx_lm (text-only LLM) rather than mlx_vlm (vision-language models).

Usage:
    MODEL_PATH=~/mlx_gemma4/models/gpt-oss-120b-bf16 PORT=8091 \\
        uv run --extra mlx python mlx_server_gptoss.py

Claude Code integration (direct):
    export ANTHROPIC_BASE_URL=http://localhost:8091
    export ANTHROPIC_AUTH_TOKEN=local
    claude

Claude Code integration (via dedicated alias on tmm7):
    cc-120b          →  auto-tunnels port 8091, sets BASE_URL=localhost:8091
"""

import json
import os
import time
import uuid
from typing import Any, AsyncIterator

import uvicorn
from fastapi import FastAPI, HTTPException
from fastapi.responses import JSONResponse, StreamingResponse
from mlx_lm import load
from mlx_lm import stream_generate as lm_stream_generate

from pydantic import BaseModel, ConfigDict, Field

# ---------------------------------------------------------------------------
# Config
# ---------------------------------------------------------------------------

MODEL_PATH = os.environ.get(
    "MODEL_PATH",
    os.path.expanduser("~/mlx_gemma4/models/gpt-oss-120b-bf16"),
)
HOST = os.environ.get("HOST", "127.0.0.1")
PORT = int(os.environ.get("PORT", "8091"))
MAX_TOKENS_DEFAULT = int(os.environ.get("MAX_TOKENS_DEFAULT", "2048"))

# ---------------------------------------------------------------------------
# Model (loaded once at startup)
# ---------------------------------------------------------------------------

print(f"Loading model from {MODEL_PATH} …", flush=True)
_model, _tokenizer = load(MODEL_PATH)
print("Model ready.", flush=True)

# ---------------------------------------------------------------------------
# Pydantic schemas (same permissive schema as mlx_server.py)
# ---------------------------------------------------------------------------


class ContentBlock(BaseModel):
    model_config = ConfigDict(extra="allow")
    type: str = "text"
    text: str | None = None
    thinking: str | None = None

    def to_text(self) -> str:
        if self.text:
            return self.text
        if self.thinking:
            return f"<thinking>{self.thinking}</thinking>"
        return ""


class Message(BaseModel):
    model_config = ConfigDict(extra="allow")
    role: str
    content: str | list[ContentBlock] | list[Any]

    def to_text(self) -> str:
        if isinstance(self.content, str):
            return self.content
        parts: list[str] = []
        for block in self.content:
            if isinstance(block, ContentBlock):
                parts.append(block.to_text())
            elif isinstance(block, dict):
                parts.append(block.get("text") or block.get("thinking") or "")
        return " ".join(p for p in parts if p)


class MessagesRequest(BaseModel):
    model_config = ConfigDict(extra="ignore")
    model: str = "gpt-oss-120b"
    messages: list[Message]
    system: str | list[ContentBlock] | list[Any] | None = None
    max_tokens: int = Field(default=MAX_TOKENS_DEFAULT)
    temperature: float = 0.7
    top_p: float = 0.9
    top_k: int | None = None
    stream: bool = False
    stop_sequences: list[str] | None = None
    metadata: dict | None = None
    tools: list | None = None
    tool_choice: dict | None = None

    def system_text(self) -> str | None:
        if self.system is None:
            return None
        if isinstance(self.system, str):
            return self.system
        parts: list[str] = []
        for b in self.system:
            if isinstance(b, ContentBlock):
                parts.append(b.to_text())
            elif isinstance(b, dict):
                parts.append(b.get("text") or "")
        return " ".join(p for p in parts if p) or None


# ---------------------------------------------------------------------------
# Prompt construction
# ---------------------------------------------------------------------------


def _build_prompt(req: MessagesRequest) -> str:
    """Apply the tokenizer's chat template to build a prompt string."""
    hf_msgs: list[dict] = []
    sys_text = req.system_text()
    if sys_text:
        hf_msgs.append({"role": "system", "content": sys_text})
    for msg in req.messages:
        hf_msgs.append({"role": msg.role, "content": msg.to_text()})

    chat_template = getattr(_tokenizer, "chat_template", None)
    if chat_template:
        return _tokenizer.apply_chat_template(
            hf_msgs, tokenize=False, add_generation_prompt=True
        )
    # Minimal fallback: concatenate role: content pairs
    lines = []
    for m in hf_msgs:
        lines.append(f"{m['role']}: {m['content']}")
    lines.append("assistant:")
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# Generation
# ---------------------------------------------------------------------------


def _iter_tokens(prompt: str, req: MessagesRequest):
    """Yield text chunks from mlx_lm stream_generate."""
    for token in lm_stream_generate(
        _model,
        _tokenizer,
        prompt=prompt,
        max_tokens=req.max_tokens,
        temp=req.temperature,
        top_p=req.top_p,
    ):
        yield token if isinstance(token, str) else getattr(token, "text", str(token))


# ---------------------------------------------------------------------------
# SSE helpers
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
        {"type": "content_block_start", "index": 0,
         "content_block": {"type": "text", "text": ""}},
    )
    yield _sse("ping", {"type": "ping"})

    output_tokens = 0
    for token_text in _iter_tokens(prompt, req):
        if not token_text:
            continue
        output_tokens += 1
        yield _sse(
            "content_block_delta",
            {"type": "content_block_delta", "index": 0,
             "delta": {"type": "text_delta", "text": token_text}},
        )

    yield _sse("content_block_stop", {"type": "content_block_stop", "index": 0})
    yield _sse(
        "message_delta",
        {"type": "message_delta",
         "delta": {"stop_reason": "end_turn", "stop_sequence": None},
         "usage": {"output_tokens": output_tokens}},
    )
    yield _sse("message_stop", {"type": "message_stop"})


# ---------------------------------------------------------------------------
# FastAPI app
# ---------------------------------------------------------------------------

app = FastAPI(title="MLX gpt-oss-120b – Anthropic API", version="0.1.0")


@app.get("/v1/models")
async def list_models():
    return {
        "object": "list",
        "data": [
            {"id": "gpt-oss-120b", "object": "model",
             "created": int(time.time()), "owned_by": "local"},
        ],
    }


@app.post("/v1/messages")
async def messages(req: MessagesRequest):
    if not req.messages:
        raise HTTPException(status_code=400, detail="messages must not be empty")

    msg_id = f"msg_{uuid.uuid4().hex}"
    prompt = _build_prompt(req)

    if req.stream:
        return StreamingResponse(
            _sse_stream(msg_id, req.model, prompt, req),
            media_type="text/event-stream",
        )

    text = "".join(_iter_tokens(prompt, req))
    return JSONResponse({
        "id": msg_id,
        "type": "message",
        "role": "assistant",
        "content": [{"type": "text", "text": text}],
        "model": req.model,
        "stop_reason": "end_turn",
        "stop_sequence": None,
        "usage": {"input_tokens": 0, "output_tokens": len(text.split())},
    })


if __name__ == "__main__":
    uvicorn.run(app, host=HOST, port=PORT, log_level="info")
