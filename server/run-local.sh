#!/usr/bin/env bash
set -euo pipefail

server_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(cd "$server_dir/.." && pwd)"
environment_file="${CHESS_ENV_FILE:-$server_dir/.env}"
server_binary="${CHESS_SERVER_BINARY:-$project_dir/build/bin/chess_server}"

if [[ ! -f "$environment_file" ]]; then
    printf 'Missing server environment file: %s\nCopy server/.env.example to server/.env and configure it.\n' "$environment_file" >&2
    exit 2
fi

if [[ ! -x "$server_binary" ]]; then
    printf 'Missing executable chess server: %s\nBuild it with cmake --build build --target chess_server.\n' "$server_binary" >&2
    exit 2
fi

set -a
source "$environment_file"
set +a
exec "$server_binary"
