#!/usr/bin/env sh
set -eu

: "${WMS_DB_HOST:?请设置 WMS_DB_HOST}"
: "${WMS_DB_PORT:=3306}"
: "${WMS_DB_NAME:?请设置 WMS_DB_NAME}"
: "${WMS_DB_USER:?请设置 WMS_DB_USER}"
: "${WMS_DB_PASSWORD:?请设置 WMS_DB_PASSWORD}"
: "${WMS_BACKUP_FILE:?请设置 WMS_BACKUP_FILE}"
if [ "${WMS_CONFIRM_RESTORE:-}" != "YES" ]; then
  printf '%s\n' '恢复操作会覆盖目标数据库。设置 WMS_CONFIRM_RESTORE=YES 后才允许执行。' >&2
  exit 2
fi
if [ ! -f "${WMS_BACKUP_FILE}" ]; then
  printf '备份文件不存在：%s\n' "${WMS_BACKUP_FILE}" >&2
  exit 1
fi

case "${WMS_BACKUP_FILE}" in
  *.gz) gzip -dc "${WMS_BACKUP_FILE}" ;;
  *) cat "${WMS_BACKUP_FILE}" ;;
esac | MYSQL_PWD="${WMS_DB_PASSWORD}" mysql \
  --host="${WMS_DB_HOST}" --port="${WMS_DB_PORT}" --user="${WMS_DB_USER}" \
  "${WMS_DB_NAME}"
printf '恢复完成：%s -> %s\n' "${WMS_BACKUP_FILE}" "${WMS_DB_NAME}"
