# Rook React client

This is the typed, feature-oriented React client for the Spring Boot chess backend and the separate C++ live-game server. REST transport and response validation live in feature `api/` modules; screens use typed data hooks and shared auth/realtime providers. Shared layout, API types, and settings live under `src/shared/`.

## Start locally

```sh
cd frontend
npm ci
cp .env.example .env
npm run dev
```

Vite serves the client on port `5173` and proxies `/api`, OAuth routes, and `/ws` to Spring Boot on port `8080`. Set `VITE_API_BASE_URL` in `.env` when the backend runs elsewhere; production deployments should point it at the public backend origin and configure backend CORS for the frontend origin. Configure Google OAuth and email delivery in the backend environment. Live games connect to the C++ URL returned by the accepted challenge API.

## Client workflows

- Landing, registration, sign-in, Google OAuth, first-login username setup, email verification, and password reset.
- Play with accepted friends: choose a time control and color, send/cancel invitations, accept/decline incoming challenges, reconnect to pending challenge state, and enter the assigned live room.
- Live chess: legal move validation for immediate feedback, drag/drop and click-to-move, promotion choice, optional move confirmation, premoves, clocks, draw offers, resignation, disconnect/reconnect, and finished-game status.
- Search verified profiles, view/edit profile details, upload avatars, send friend requests, challenge friends, and view another player’s game history.
- Manage incoming/outgoing friend requests, remove friends, create/redeem invite links, and block/unblock players.
- Browse paginated personal or player history, step/autoplay a replay, and download PGN.
- Responsive navigation and account settings for theme, board palette/orientation, motion, coordinates, legal move hints, premoves, move confirmation, sounds, in-app notifications, and search visibility.

## Settings persistence

Theme, board palette/orientation, motion, gameplay input preferences, sound/notification toggles, and profile search visibility are stored in the backend account so they follow the user across devices. System theme and reduced-motion choices follow the browser/device preference. The current navigation drawer state and active replay position remain local UI state and are intentionally not synchronized.

## Checks

`npm run build` runs strict TypeScript compilation and creates a production Vite bundle. Backend endpoint shapes and STOMP/C++ contracts are documented in [`../backend/API_CONTRACT.md`](../backend/API_CONTRACT.md). The frontend does not yet include an automated browser interaction suite; validate a deployment against configured OAuth/email services and a reachable C++ server as part of release acceptance.
