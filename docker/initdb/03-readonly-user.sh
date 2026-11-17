#!/bin/bash
# Creates a least-privilege, read-only account for sqlens.
# Runs once, on first container start, from docker-entrypoint-initdb.d.
# Credentials come from .env (SQLENS_RO_USER / SQLENS_RO_PASSWORD); nothing is hardcoded.
set -euo pipefail

if [ -z "${SQLENS_RO_USER:-}" ] || [ -z "${SQLENS_RO_PASSWORD:-}" ]; then
    echo "[sqlens] SQLENS_RO_USER / SQLENS_RO_PASSWORD not set; skipping read-only user creation"
    exit 0
fi

case "$SQLENS_RO_USER$SQLENS_RO_PASSWORD" in
    *\'*|*\\*) echo "[sqlens] read-only user/password must not contain ' or \\" >&2; exit 1 ;;
esac

MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot <<SQL
CREATE USER IF NOT EXISTS '${SQLENS_RO_USER}'@'%' IDENTIFIED BY '${SQLENS_RO_PASSWORD}';
GRANT SELECT, SHOW VIEW ON sakila.* TO '${SQLENS_RO_USER}'@'%';
FLUSH PRIVILEGES;
SQL

echo "[sqlens] created read-only user '${SQLENS_RO_USER}'"
