#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
environment_file="$project_dir/.env.deploy"
template_file="$project_dir/.env.deploy.example"

if [[ -e "$environment_file" ]]; then
    printf '%s already exists; leaving it unchanged.\n' "$environment_file"
    exit 0
fi

cp "$template_file" "$environment_file"

set_value() {
    local key="$1"
    local value="$2"
    sed -i "s|^${key}=.*$|${key}=${value}|" "$environment_file"
}

set_value DB_PASSWORD "$(openssl rand -hex 32)"
set_value APP_JWT_SECRET "$(openssl rand -hex 32)"
set_value APP_ENGINE_JWT_SECRET "$(openssl rand -hex 32)"
set_value APP_INTERNAL_TOKEN "$(openssl rand -hex 32)"
set_value APP_REDIS_SECRET "$(openssl rand -hex 32)"
set_value APP_STOMP_RELAY_PASSCODE "$(openssl rand -hex 32)"
set_value APP_S3_ACCESS_KEY "chess$(openssl rand -hex 16)"
set_value APP_S3_SECRET_KEY "$(openssl rand -hex 32)"
chmod 600 "$environment_file"

printf 'Created .env.deploy with generated secrets and mode 0600.\n'
printf 'Edit APP_DOMAIN, NGROK_AUTHTOKEN, SMTP settings, and Google OAuth credentials before deployment.\n'
