# Chess backend client contract

The REST API is described by OpenAPI at `/v3/api-docs` and the interactive Swagger UI at `/swagger-ui.html`. This document covers the pieces that OpenAPI cannot fully describe: browser cookies, STOMP events, and the C++ game-server connection.

## REST conventions

- Send `Authorization: Bearer <accessToken>` on protected requests. Access tokens expire after the configured access-token lifetime.
- JSON success responses use `{ "success": true, "code": 200, "message": "Success", "data": ... }`. Error responses use the same envelope with `success: false`, `errorCode`, and a safe message. Validation errors also include an `errors` object.
- `POST /api/auth/login` and token refresh return an access token in the JSON data and set the refresh token in the `HttpOnly` `chess_refresh` cookie. Send cookies with credentials enabled. Refresh and logout use that cookie; logout revokes it and returns `204`.
- JSON endpoints that return `204`, avatar bytes, or PGN text are not wrapped in `AppResponse`.
- Pagination uses zero-based `page` and `size`; maximum size is 50. Search requires at least two characters.

## Endpoints

| Method and path | Auth | Purpose / request |
|---|---|---|
| `POST /api/auth/register` | Public | `username`, `email`, `password`, optional `displayName`; sends email verification. |
| `POST /api/auth/login` | Public | `identifier`, `password`; returns access token and refresh cookie. |
| `POST /api/auth/refresh` | Refresh cookie | Rotates refresh cookie and returns a new access token. |
| `POST /api/auth/logout` | Refresh cookie | Revokes refresh token and clears cookie. |
| `POST /api/auth/verify-email` | Public | `token` from verification email. |
| `POST /api/auth/forgot-password` | Public | `email`; response does not reveal whether account exists. |
| `POST /api/auth/reset-password` | Public | `token`, `password`. |
| `POST /api/auth/oauth/exchange` | Public | `code` from the OAuth redirect; exchanges it for access token and refresh cookie. |
| `POST /api/auth/oauth/onboarding` | Onboarding access token | `username`; completes first-login OAuth onboarding. |
| `GET /api/users/me` | User | Current profile and stats. |
| `PUT /api/users/me` | User | Optional `username`, `displayName`, `bio`, `searchable`. |
| `POST /api/users/me/avatar` | User | Multipart field `file`; PNG, JPEG, or WEBP up to configured size. |
| `GET /api/users/{id}/avatar` | Public | Avatar bytes; use returned `avatarUrl` values. |
| `GET /api/users/{username}/profile` | User | Publicly visible profile and stats. |
| `GET /api/users/search?q={query}&page=0&size=10` | User | Search verified, searchable users by username prefix or display-name substring. |
| `GET /api/friends` | User | Accepted friends and presence state. |
| `GET /api/friends/requests/incoming` | User | Incoming requests. |
| `GET /api/friends/requests/outgoing` | User | Outgoing requests. |
| `POST /api/friends/request/{username}` | User | Send a request. A reciprocal pending request is accepted. |
| `POST /api/friends/accept/{requestId}` | User | Accept incoming request. |
| `POST /api/friends/decline/{requestId}` | User | Decline incoming request. |
| `DELETE /api/friends/{friendId}` | User | Remove accepted friend. |
| `POST /api/friends/block/{userId}` | User | Block user. |
| `DELETE /api/friends/block/{userId}` | User | Unblock user. |
| `POST /api/friends/invite` | User | Create one-time friend invite; returns its frontend link. |
| `POST /api/friends/invite/redeem` | User | Body `{ "token": "..." }`; redeem invite and establish friendship. |
| `GET /api/games/my-history?page=0&size=10` | User | Current user’s paginated games. |
| `GET /api/games/user/{userId}?page=0&size=10` | User | Another player’s paginated games. |
| `GET /api/games/{id}` | User | Full game details, move list, FEN list, and PGN. |
| `GET /api/games/{id}/pgn` | User | Download PGN text. |
| `POST /api/internal/games/record` | Internal token | C++ game completion webhook; see the engine contract below. |

OAuth starts at `/oauth2/authorization/google` or `/oauth2/authorization/github`. The backend redirects to the configured frontend URL with a short-lived, single-use exchange `code`; the frontend posts it to `/api/auth/oauth/exchange`. OAuth provider credentials and the allowed frontend origin must be configured in the environment.

## STOMP, presence, and challenges

- Connect to `/ws` using STOMP (SockJS fallback is enabled). Include `Authorization: Bearer <accessToken>` in the STOMP `CONNECT` frame.
- Subscribe only to user destinations: `/user/queue/presence`, `/user/queue/challenges`, and `/user/queue/notifications`.
- Send commands under `/app`: `/app/challenge.send` with `toUsername`, optional `challengerSide` (`WHITE`, `BLACK`, `RANDOM`), and optional paired `initialTimeSeconds` / `incrementSeconds`; `/app/challenge.accept`, `/app/challenge.decline`, and `/app/challenge.cancel` each take `{ "challengeId": 123 }`.
- Challenge events use `ChallengeEvent`: challenge ID/status, actor ID, optional challenger identity and clock settings, then assigned room code, color, connect URL, player-specific engine JWT, or status message as applicable.
- Presence events use `{ "userId": 123, "status": "ONLINE|IN_GAME|OFFLINE" }` and are sent to each accepted friend’s `/user/queue/presence` destination.
- Friend requests and other user notices are sent to `/user/queue/notifications`.

## C++ live-game and webhook contract

- Players connect directly to the configured C++ WebSocket URL. Each `ChallengeEvent` supplies the room code, player color, and player-specific signed token. The token signing key must match `APP_ENGINE_JWT_SECRET` and the C++ server’s `CHESS_JWT_SECRET`.
- When the game ends, the C++ server posts room code, player IDs, winner/draw, termination reason, move count, duration, PGN, SAN moves, and FEN snapshots to `POST /api/internal/games/record`, with `X-Internal-Token` matching `APP_INTERNAL_TOKEN` and the C++ server’s `INTERNAL_WEBHOOK_SECRET`.
- `roomToken` is a legacy C++ payload property currently equal to `roomCode`; the backend ignores it. Room identity is established by the room code and locked accepted challenge. The internal header authenticates the C++ service.
- A successful first delivery returns `201`; an identical repeat returns `200`. Both are successful delivery outcomes. A report updates both players’ stats and writes replay history atomically.

## Local setup and integration readiness

Start PostgreSQL, MinIO, Mailpit, and the backend with `docker compose up --build` from `backend/`. Copy `.env.example` to `.env`; configure strong distinct JWT/internal secrets and real Google credentials for provider sign-in. Email links go to Mailpit. Point the C++ server at `/api/internal/games/record` and set its webhook and JWT secrets to match. The backend does not include a frontend; a future client can use this document and the OpenAPI schema as its contract.
