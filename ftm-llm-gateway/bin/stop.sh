#!/usr/bin/env bash
set -euo pipefail

stop_pid() {
    local name="$1" pidfile="$2"
    if [[ -f "$pidfile" ]] && kill -0 "$(cat "$pidfile")" 2>/dev/null; then
        kill "$(cat "$pidfile")" && rm -f "$pidfile"
        echo "$name stopped."
    else
        echo "$name not running."
        rm -f "$pidfile"
    fi
}

stop_pid "Proxy"      /tmp/ftm-proxy.pid
stop_pid "MLX server" /tmp/ftm-mlx.pid
