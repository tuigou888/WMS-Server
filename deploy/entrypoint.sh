#!/bin/bash
# 单容器入口：同时拉起 Java 后端与 nginx 前端。
# 任一进程退出（Java 崩溃或 nginx 挂掉）都让整个容器退出，交给 restart: unless-stopped 拉起。
set -euo pipefail

java -jar /app/app.jar &
java_pid=$!
trap 'kill "$java_pid" 2>/dev/null || true' TERM INT

nginx -g 'daemon off;' &
nginx_pid=$!

set +e
wait -n "$java_pid" "$nginx_pid"
status=$?
set -e

echo "process exited unexpectedly (status=$status), stopping container" >&2
kill "$java_pid" "$nginx_pid" 2>/dev/null || true
wait 2>/dev/null || true
exit "$status"
