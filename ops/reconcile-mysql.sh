#!/usr/bin/env sh
set -eu

: "${WMS_DB_HOST:?请设置 WMS_DB_HOST}"
: "${WMS_DB_PORT:=3306}"
: "${WMS_DB_NAME:?请设置 WMS_DB_NAME}"
: "${WMS_DB_USER:?请设置 WMS_DB_USER}"
: "${WMS_DB_PASSWORD:?请设置 WMS_DB_PASSWORD}"
repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
MYSQL_PWD="${WMS_DB_PASSWORD}" mysql \
  --host="${WMS_DB_HOST}" --port="${WMS_DB_PORT}" --user="${WMS_DB_USER}" \
  "${WMS_DB_NAME}" < "${repo_root}/test-artifacts/reconcile_inventory_transactions.sql"
