#!/usr/bin/env sh
set -eu
umask 077

: "${WMS_DB_HOST:?请设置 WMS_DB_HOST}"
: "${WMS_DB_PORT:=3306}"
: "${WMS_DB_NAME:?请设置 WMS_DB_NAME}"
: "${WMS_DB_USER:?请设置 WMS_DB_USER}"
: "${WMS_DB_PASSWORD:?请设置 WMS_DB_PASSWORD}"
backup_dir="${WMS_BACKUP_DIR:-./backups}"
timestamp="$(date '+%Y%m%d-%H%M%S')"
backup_file="${backup_dir}/wms-${timestamp}.sql.gz"
temp_file="${backup_dir}/.wms-${timestamp}.sql"

mkdir -p "${backup_dir}"
trap 'rm -f "${temp_file}"' EXIT HUP INT TERM
MYSQL_PWD="${WMS_DB_PASSWORD}" mysqldump \
  --single-transaction --quick --routines --events --triggers --hex-blob \
  --host="${WMS_DB_HOST}" --port="${WMS_DB_PORT}" --user="${WMS_DB_USER}" \
  "${WMS_DB_NAME}" > "${temp_file}"
gzip -c "${temp_file}" > "${backup_file}"
sha256sum "${backup_file}" > "${backup_file}.sha256"
printf '备份完成：%s\n校验文件：%s\n' "${backup_file}" "${backup_file}.sha256"
