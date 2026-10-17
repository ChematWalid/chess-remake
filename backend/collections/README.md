# E2E collections

Import `chess-backend-e2e.json` in Hoppscotch using **Import → Hoppscotch**. Set up an environment with the variables referenced by the requests. Keep credentials and tokens in Hoppscotch’s local environment, never in the exported collection.

Required variables:

| Variable | Example or source |
| --- | --- |
| `baseUrl` | `http://localhost:8080` |
| `frontendOrigin` | configured `APP_CORS_ORIGINS` value, usually `http://localhost:5173` |
| `whiteUsername`, `blackUsername` | two verified local test accounts |
| `whiteEmail`, `testPassword`, `newPassword` | local test-account values |
| `whiteAccessToken`, `blackAccessToken`, `refreshToken` | login response and `chess_refresh` cookie |
| `verificationToken`, `resetToken`, `oauthExchangeCode` | Mailpit or the corresponding OAuth callback |
| `blackUsernamePrefix`, `blackPublicId`, `friendRequestId`, `challengeId`, `gameId`, `oauthUsername` | API responses from the workflow |
| `inviteToken` | response from Create friend invite |
| `internalToken` | local `backend/.env` only; do not commit or share it |
| `whiteDatabaseId`, `blackDatabaseId`, `roomCode` | local owner profiles and accepted challenge detail |

Run the stateful sequence in order: register/verify/login both users; create and accept a friendship or redeem an invite; accept a challenge through STOMP; complete a game through the C++ WebSocket; then fetch both histories, game detail and PGN. `chess-backend-websocket-e2e.md` lists the STOMP and C++ socket messages, negative probes, and expected outcomes. `run-live-e2e.py` is a local automated smoke scenario for typed STOMP errors and a checkmate game; it needs `websockets` for Python and the variables `CHESS_E2E_PASSWORD`, `CHESS_E2E_WHITE_USERNAME`, and `CHESS_E2E_BLACK_USERNAME`.

The REST collection covers authentication and OAuth onboarding, profiles and search, settings, avatar storage, friend and block management, invites, challenges, game history, replay, PGN download, and internal engine webhooks, with representative invalid-input probes. The Hoppscotch connector available in this environment can execute REST requests but cannot create authenticated Hoppscotch cloud collections or open WebSocket sessions. Import the REST collection locally; use the companion WebSocket guide and smoke script for those protocols.
