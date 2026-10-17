# Chess

Chess website built with a React/TypeScript frontend, Spring Boot backend, PostgreSQL, and a C++ WebSocket game server.

## Run locally

The existing backend and game-server local workflows are documented in [backend/README.md](backend/README.md) and [server/README.md](server/README.md). To run the complete local Compose stack:

```sh
cp .env.dev.example .env.dev
docker compose --env-file .env.dev -f compose.dev.yaml up --build
```

## Deploy

See [DEPLOYMENT.md](DEPLOYMENT.md) for the Oracle VM or home-server setup, required external credentials, deployment commands, and backups. Deployment and development use separate Compose files and ignored environment files so local data and settings remain separate.
