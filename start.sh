#!/usr/bin/env bash
# 一键启动 WMS 服务（后端 :8088，可选前端 :5173）
# 用法: ./start.sh [--web] [--dev]   --web=同时启动前端  --dev=前端开发模式(默认构建后经 nginx 方式不可用,直接 npm run dev)
set -euo pipefail
cd "$(dirname "$0")"

PID_FILE=/tmp/wms-server.pid
LOG_FILE=/tmp/opencode/wms-server.log

if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  echo "后端已在运行 (PID $(cat "$PID_FILE"))，跳过启动"
else
  echo "启动后端 (日志: $LOG_FILE)..."
  (setsid nohup mvn -f wms-server/pom.xml spring-boot:run > "$LOG_FILE" 2>&1 & echo $! > "$PID_FILE")
  for i in $(seq 1 60); do
    sleep 2
    if curl -s -o /dev/null http://localhost:8088/api/v1/health 2>/dev/null; then
      echo "后端就绪: http://localhost:8088/api/v1 (PID $(cat "$PID_FILE"))"
      break
    fi
    [ "$i" = 60 ] && { echo "后端启动超时，请查看 $LOG_FILE"; exit 1; }
  done
fi

if [ "${1:-}" = "--web" ]; then
  if curl -s -o /dev/null http://localhost:5173 2>/dev/null; then
    echo "前端已在运行: http://localhost:5173"
  else
    echo "启动前端 (Vite) ..."
    (setsid nohup npm --prefix wms-web run dev > /tmp/opencode/wms-web.log 2>&1 &)
    for i in $(seq 1 30); do
      sleep 1
      if curl -s -o /dev/null http://localhost:5173 2>/dev/null; then
        echo "前端就绪: http://localhost:5173 (日志: /tmp/opencode/wms-web.log)"
        break
      fi
      [ "$i" = 30 ] && echo "前端启动超时，请查看 /tmp/opencode/wms-web.log"
    done
  fi
fi
