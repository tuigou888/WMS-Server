#!/usr/bin/env bash
# 一键停止 WMS 服务（后端 :8088，前端 :5173，以及残留 java/node 服务进程）
cd "$(dirname "$0")"

PID_FILE=/tmp/wms-server.pid
stopped=0

if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  echo "停止后端 (PID $(cat "$PID_FILE"))..."
  kill "$(cat "$PID_FILE")" 2>/dev/null && stopped=1
  rm -f "$PID_FILE"
else
  echo "后端 PID 文件不存在或已停止"
fi

# 兜底：按端口清理残留进程
if curl -s -o /dev/null http://localhost:8088/api/v1/health 2>/dev/null; then
  echo "清理残留 8088 端口进程..."
  fuser -k 8088/tcp 2>/dev/null || true
  stopped=1
fi

if curl -s -o /dev/null http://localhost:5173 2>/dev/null; then
  echo "停止前端 (5173)..."
  fuser -k 5173/tcp 2>/dev/null || true
  stopped=1
fi

for i in $(seq 1 20); do
  curl -s -o /dev/null http://localhost:8088/api/v1/health 2>/dev/null || break
  sleep 1
done

if [ "$stopped" = 1 ]; then
  echo "服务已全部停止"
else
  echo "未发现运行中的服务"
fi
