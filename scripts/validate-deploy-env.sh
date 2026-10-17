#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
environment_file="$project_dir/.env.deploy"

if [[ ! -f "$environment_file" ]]; then
    printf '.env.deploy is missing. Run scripts/prepare-deploy-env.sh first.\n' >&2
    exit 1
fi

get_value() {
    local key="$1"
    awk -F= -v wanted="$key" '$1 == wanted { print substr($0, index($0, "=") + 1); exit }' "$environment_file"
}

require_configured() {
    local key="$1"
    local value
    value="$(get_value "$key")"
    case "$value" in
        ""|region|*replace-with*|*example.com*|*namespace.compat.objectstorage.region*)
            printf '%s needs a real deployment value in .env.deploy.\n' "$key" >&2
            exit 1
            ;;
    esac
}

for key in \
    APP_DOMAIN ACME_EMAIL DB_NAME DB_USERNAME DB_PASSWORD \
    APP_JWT_SECRET APP_ENGINE_JWT_SECRET APP_INTERNAL_TOKEN \
    APP_S3_ENDPOINT APP_S3_REGION APP_S3_BUCKET APP_S3_ACCESS_KEY APP_S3_SECRET_KEY \
    SMTP_HOST SMTP_USERNAME SMTP_PASSWORD APP_EMAIL_FROM GOOGLE_CLIENT_ID GOOGLE_CLIENT_SECRET
do
    require_configured "$key"
done

for key in APP_JWT_SECRET APP_ENGINE_JWT_SECRET APP_INTERNAL_TOKEN DB_PASSWORD
do
    value="$(get_value "$key")"
    if [[ ${#value} -lt 32 ]]; then
        printf '%s must be at least 32 characters.\n' "$key" >&2
        exit 1
    fi
done

if [[ "$(get_value APP_JWT_SECRET)" == "$(get_value APP_ENGINE_JWT_SECRET)" \
   || "$(get_value APP_JWT_SECRET)" == "$(get_value APP_INTERNAL_TOKEN)" \
   || "$(get_value APP_ENGINE_JWT_SECRET)" == "$(get_value APP_INTERNAL_TOKEN)" ]]; then
    printf 'JWT, engine JWT, and internal webhook secrets must all be different.\n' >&2
    exit 1
fi

printf 'Deployment environment is populated with required values.\n'
