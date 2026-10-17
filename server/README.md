# Chess game server

The C++ WebSocket server reads all runtime configuration from environment variables. For local work, copy `.env.example` to `.env`, set `CHESS_JWT_SECRET` to the same value as backend `APP_ENGINE_JWT_SECRET`, and set `INTERNAL_WEBHOOK_SECRET` to the same value as backend `APP_INTERNAL_TOKEN`.

From the repository root, build and run it with:

```sh
cmake -S . -B build
cmake --build build --target chess_server -j
./server/run-local.sh
```

The local environment file sets the listen address and port, Spring webhook URL, shared secrets, and durable webhook outbox directory. The server refuses to start if required settings are missing; it does not use a built-in signing secret.
