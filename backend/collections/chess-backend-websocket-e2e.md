# Chess Backend WebSocket E2E scenarios

The Hoppscotch MCP HTTP executor does not create or execute WebSocket sessions. The scenarios here are the companion collection for the REST request collection in `chess-backend-e2e.json`. Values such as JWTs, usernames, challenge IDs, room codes, and room tokens must come from the corresponding test run; never commit real tokens.

## Spring STOMP over SockJS

- Endpoint: `http://localhost:8080/ws` (SockJS enabled; WebSocket transport path is `/ws/{server-id}/{session-id}/websocket`).
- Set the WebSocket Origin to the configured frontend origin (`APP_CORS_ORIGINS` in the ignored local `.env`, normally `http://localhost:5173`).
- On STOMP `CONNECT`, send `accept-version:1.2`, `host:localhost`, and `Authorization:Bearer <access token>`.
- Subscribe as each authenticated user to `/user/topic/challenges`, `/user/topic/notifications`, `/user/topic/errors`, and `/user/topic/presence`.
- A SockJS transport wraps outgoing STOMP frames as a JSON array of strings and sends server frames as `a["<STOMP frame>"]`. Terminate each STOMP frame with NUL (`\\u0000`).

### Challenge success flow

As White, send to `/app/challenge.send`:

```json
{"toUsername":"<blackUsername>","challengerSide":"WHITE","initialTimeSeconds":300,"incrementSeconds":0}
```

Black receives an `INVITATION_RECEIVED` event at `/user/topic/challenges`; White receives `INVITATION_SENT`. Black then sends to `/app/challenge.accept`:

```json
{"challengeId":<challengeId>}
```

Both users receive `MATCH_ASSIGNED` with `roomCode`, `color`, and `connectUrl`. The STOMP event does not include a room token. Each player calls `GET /api/challenges/{challengeId}` with their own access token to receive a short-lived, player-specific room token. Connect to the returned `connectUrl`, then send `{"action":"connect_token","room_code":"<roomCode>","token":"<roomToken>"}` as the first WebSocket frame. Credentials stay out of URL query strings and STOMP broadcasts.

If both players have not connected and started the game within `APP_GAME_START_TIMEOUT` (default two minutes), Spring expires the accepted challenge, clears both players' in-game presence, and sends a `STATUS_CHANGED` event. On both player connections, the C++ server must confirm the exact room and assigned player IDs through `POST /api/internal/games/start` before it starts clocks or accepts moves.

### Challenge lifecycle and error probes

- Send a valid request with a non-friend username: expect `CHALLENGE_REQUIRES_FRIEND` on `/user/topic/errors`, with the STOMP connection kept open.
- Challenge own username: expect `SELF_CHALLENGE` on `/user/topic/errors`.
- Send `initialTimeSeconds:4`, negative increment, or only one of the two clock values: expect a typed validation/time-control error.
- Send the same challenge twice before resolving it: expect `CHALLENGE_ALREADY_PENDING`.
- Accept as a non-recipient, accept an unknown ID, and accept after decline/cancel: expect `CHALLENGE_NOT_FOUND`/unavailable typed errors; socket remains usable.
- Try malformed JSON and `challengeId:0`; expect a validation/protocol error on `/user/topic/errors`, without disconnecting.
- Test decline (`/app/challenge.decline`) and cancel (`/app/challenge.cancel`) using `{"challengeId":<challengeId>}`. The recipient may decline; only the challenger may cancel. Verify both parties receive the final status event and the REST incoming/outgoing queues no longer list it as pending.
- Connect with a missing, expired, malformed, or wrong-user JWT. STOMP authentication must fail without exposing exception details. Subscribe or send before CONNECT/authentication and verify it is rejected.

## C++ game WebSocket

Connect to each `connectUrl` returned by Spring (do not construct or persist room tokens in a frontend). It is `ws://localhost:9002/ws/play` with no credentials in the URL. Send each player's room token in the initial `connect_token` frame.

### Completed game and replay persistence

After both users connect, send the legal Fool's Mate sequence, alternating sides:

```json
{"action":"move","san":"f3"}
{"action":"move","san":"e5"}
{"action":"move","san":"g4"}
{"action":"move","san":"Qh4#"}
```

Verify both sockets receive move/game-over events; the terminal event includes the winner, termination reason, PGN, moves, and FENs. Then use Hoppscotch REST requests for both `/api/games/my-history`, `/api/games/user/<opponentPublicId>`, `/api/games/<gameId>`, and `/api/games/<gameId>/pgn`. Confirm total games/win/loss/draw stats on `/api/users/me`, and confirm duplicate webhook delivery does not create a second history item or double-count stats.

Also play separate games ending by resignation, agreed draw (offer/accept), stalemate or timeout as supported by the engine; verify result and termination reason map to the persisted `GameResult` and `TerminationReason` values.

### C++ WebSocket error probes

- Send gameplay before authenticating, omit credentials from `connect_token`, use an invalid or expired token, or send a token for another room. These connections must receive an error and close.
- Send invalid JSON, unknown action, missing move, invalid SAN/UCI, out-of-turn move, illegal move, malformed join, and duplicate join.
- Attempt to act as the wrong color, offer a draw twice, answer without a pending draw, and send move/resign after game over.
- Disconnect a player mid-game and reconnect using the same valid identity. Observe disconnect/reconnect notifications and ensure there is no false game record.
- Verify failures are JSON `event:error` responses or deliberate closes, do not crash the engine, do not leak JWTs, and do not write incomplete game records.

## Local values

Use `backend/.env` and `server/.env` only on the local machine. The C++ runtime values are sourced from `server/.env`; its webhook URL and shared internal credential must match the backend settings. The matching room signing secrets are `server/.env:CHESS_JWT_SECRET` and `backend/.env:APP_ENGINE_JWT_SECRET`. Do not paste either secret into the collection or check them in.
