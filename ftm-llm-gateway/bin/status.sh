#!/usr/bin/env bash
svc() {
    local name="$1" pidfile="$2" url="$3" token="${4:-}"
    if [[ -f "$pidfile" ]] && kill -0 "$(cat "$pidfile")" 2>/dev/null; then
        local detail=""
        if [[ -n "$url" ]]; then
            local hdr=""
            [[ -n "$token" ]] && hdr="-H \"Authorization: Bearer $token\""
            detail=$(eval curl -s --connect-timeout 2 $hdr "$url" 2>/dev/null | \
                python3 -m json.tool 2>/dev/null | head -4 || echo "")
        fi
        echo "  $name  RUNNING (PID $(cat "$pidfile"))"
        [[ -n "$detail" ]] && echo "$detail" | sed 's/^/    /'
    else
        echo "  $name  STOPPED"
    fi
}

echo "=== ftm-llm-gateway status ==="
svc "Proxy (8082)" /tmp/ftm-proxy.pid "http://localhost:8082/" "freecc"
svc "MLX   (8090)" /tmp/ftm-mlx.pid  "http://localhost:8090/v1/models"
echo ""
echo "Ollama models:"
curl -s http://localhost:11434/api/tags 2>/dev/null | \
    python3 -c "import json,sys; [print('  ' + m['name']) for m in json.load(sys.stdin).get('models',[])]" \
    2>/dev/null || echo "  (ollama not running)"
