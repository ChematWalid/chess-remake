# Chess backend

Spring Boot 3 and Java 21 backend for authentication, profiles, searchable users, friends, challenges, presence, avatar storage, and archived chess games. Persistence uses Hibernate/JPA entities and Spring Data repositories; Flyway owns schema changes. REST JSON responses use `AppResponse<T>` and centralized error handling.

## Feature structure

Business code is grouped by feature under `com.chess.backend.features`. Each feature owns its controllers, services, domain model, repositories, DTOs, and any feature-specific configuration, utilities, or exceptions it needs. Cross-feature operations go through the owning feature's service API; a feature should not reach into another feature's repository. Shared response, security, configuration, exception-base, enum, and utility code lives under `com.chess.backend.common`.

Application failures use a dedicated exception type per condition, derived from `AppException`. `ApiExceptionHandler` turns those exceptions and framework request failures into the common error response with an HTTP status, stable error code, and safe message. Infrastructure failures are logged centrally and return a generic internal error.

Live gameplay remains owned by the C++ server. It reports transient player disconnects to the opponent and records a completed abandonment as the `ABANDONED` game termination reason after its reconnect grace period. The backend persists that final result; it does not model a temporary disconnect as a backend exception.

## Local services

```sh
cp .env.example .env
docker compose up --build
```

The compose stack starts PostgreSQL, MinIO, and Mailpit with the backend. PostgreSQL is available on host port `5434`, MinIO on `9003` (console `9004`), and Mailpit at `http://localhost:8025`. Set `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GITHUB_CLIENT_ID`, and `GITHUB_CLIENT_SECRET` in `.env` to enable real provider login. Local placeholder credentials keep the backend bootable but cannot complete OAuth. Email verification and password reset links are delivered to Mailpit.

For local development without Docker:

```sh
mvn spring-boot:run
```

Set the database, storage, mail, and required token secrets in the environment first. `APP_JWT_SECRET`, `APP_ENGINE_JWT_SECRET`, and `APP_INTERNAL_TOKEN` must each be strong values of at least 32 bytes/characters. `APP_ENGINE_JWT_SECRET` must match the C++ server's `CHESS_JWT_SECRET`; `APP_INTERNAL_TOKEN` must match `INTERNAL_WEBHOOK_SECRET` on the game server.

## Main routes

- `/api/auth/*`: registration, login, email verification, password reset, refresh, logout, and OAuth code exchange/onboarding.
- `/api/users/*`: current and public profiles, prefix/sub-string search, profile updates, and avatars.
- `/api/friends/*`: requests, accepted friendships, blocks, and one-time invite links.
- STOMP `/ws`: authenticated presence and friend game challenges.
- `/api/games/*`: paginated history, full replay payload, and PGN download.
- `POST /api/internal/games/record`: C++ game server webhook, authenticated with `X-Internal-Token`.

REST endpoint details and DTO schemas are published at `/v3/api-docs` (interactive UI at `/swagger-ui.html`). STOMP event payloads, cookie behavior, and C++ WebSocket/webhook configuration are documented in [API_CONTRACT.md](API_CONTRACT.md).

The game-history webhook authenticates the C++ server with `X-Internal-Token`, locks the accepted room, and validates its room assignment, players, winner/draw consistency, and replay array lengths before saving history and updating player counters in one transaction. The C++ server currently sends a redundant `roomToken` field equal to `roomCode`; the backend ignores that legacy field. Repeated webhook delivery for a completed room is idempotent.

All JSON REST success bodies follow `{ "success": true, "code": 200, "message": "Success", "data": ... }`. Errors use the same envelope with `success: false`, `errorCode`, and a safe message. Sensitive DTO properties marked `@Sensitive` are only serialized when the authenticated principal matches the DTO's `ownerId`.
