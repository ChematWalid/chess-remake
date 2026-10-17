# Self-host on your PC with ngrok

This Compose deployment runs the React site, Spring Boot API and STOMP endpoint, C++ game server, PostgreSQL, MinIO, Redis, and RabbitMQ on your PC. ngrok provides the public HTTPS hostname and tunnel; Caddy routes that traffic to the app. You do not need a purchased domain, router port forwarding, or a public home IP. PostgreSQL, MinIO, Redis, and RabbitMQ are private on the Compose network, and their data is kept in named volumes.

## What you need

- A PC that can stay powered on while people play, with Docker Engine and the Docker Compose plugin installed.
- A free ngrok account, its assigned HTTPS development domain, and an ngrok authtoken.
- Google OAuth credentials and SMTP credentials for Google sign-in and account email.

The PC must be connected to the internet, but it does not need inbound ports opened on your router or firewall. The ngrok agent makes an outbound connection and routes public requests through it. When the PC or agent is offline, the site is unavailable.

ngrok's free tier currently limits usage and shows visitors an interstitial warning page. Each visitor may need to continue through that page before using the site. Review ngrok's current [plan limits](https://ngrok.com/pricing).

## Configure the deployment

From the repository root, create the private deployment environment file. The helper generates independent database, JWT, internal webhook, and MinIO credentials and sets the file mode to owner-only:

```sh
./scripts/prepare-deploy-env.sh
```

Edit `.env.deploy`:

- Set `APP_DOMAIN` to the ngrok HTTPS domain assigned to your account, without `https://` (for example, `your-name.ngrok-free.dev`).
- Set `NGROK_AUTHTOKEN` to the token from your ngrok dashboard. Keep it private.
- Set `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, and `APP_EMAIL_FROM` to your mail provider's values. For STARTTLS, the usual port is 587.
- Set `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` from Google Cloud. Add `https://YOUR_NGROK_DOMAIN/login/oauth2/code/google` as an authorized redirect URI.
- Keep the generated database, JWT, webhook, MinIO, Redis, and RabbitMQ secrets. `APP_S3_ENDPOINT` should be `http://minio:9000`; MinIO uses the matching generated credentials, and the backend creates the configured bucket on startup.

Keep `.env.deploy` private. It is ignored by Git. Do not paste its contents into chat or commit it. Validate that required settings are present:

```sh
./scripts/validate-deploy-env.sh
```

## Build and start

Check Compose interpolation and syntax without printing resolved secrets, then build and start the services:

```sh
docker compose --env-file .env.deploy -f compose.deploy.yaml config --quiet
docker compose --env-file .env.deploy -f compose.deploy.yaml build backend
docker compose --env-file .env.deploy -f compose.deploy.yaml build chess-server
docker compose --env-file .env.deploy -f compose.deploy.yaml build frontend
docker compose --env-file .env.deploy -f compose.deploy.yaml up -d
docker compose --env-file .env.deploy -f compose.deploy.yaml ps
```

Get the public URL and check tunnel startup:

```sh
docker compose --env-file .env.deploy -f compose.deploy.yaml logs -f ngrok
```

Open `https://APP_DOMAIN`. ngrok terminates public HTTPS; Caddy receives the tunneled HTTP traffic and routes `/api`, OAuth callbacks, STOMP `/ws`, and game WebSockets at `/engine/ws/play` to the right services. The backend advertises secure `wss://APP_DOMAIN/engine/ws/play` connections to players.

## Updates and backups

```sh
docker compose --env-file .env.deploy -f compose.deploy.yaml logs -f
docker compose --env-file .env.deploy -f compose.deploy.yaml up -d --build
docker compose --env-file .env.deploy -f compose.deploy.yaml down
```

Back up PostgreSQL and MinIO data to a location outside this PC. Named volumes persist across container replacement, but they are not backups. PostgreSQL contains accounts and game history; MinIO contains avatars.

```sh
docker compose --env-file .env.deploy -f compose.deploy.yaml exec -T postgres \
  pg_dump -U "$(sed -n 's/^DB_USERNAME=//p' .env.deploy)" "$(sed -n 's/^DB_NAME=//p' .env.deploy)" \
  > "chess-backup-$(date -u +%Y%m%dT%H%M%SZ).sql"
```

Use a MinIO client attached to the Compose network to back up the `deploy-minio` volume. Do not publish MinIO ports.

## Local development

The unified root development stack uses separate environment files and volumes:

```sh
cp .env.dev.example .env.dev
docker compose --env-file .env.dev -f compose.dev.yaml up --build
```

It serves Vite on `http://localhost:5173`, Spring on `http://localhost:8080`, C++ WebSocket on `ws://localhost:9002`, MinIO on `http://localhost:9003` (console `http://localhost:9004`), and Mailpit at `http://localhost:8025`. Development data is separate from deployment data.

The public deployment remains a single backend instance. Redis shares auth limits and presence between backend instances, while RabbitMQ relays STOMP messages and broadcasts user session registries. This makes backend instances compatible with shared state, but Redis and RabbitMQ themselves are single nodes in this Compose setup. Use managed or clustered Redis and RabbitMQ before requiring high availability across hosts. For a local two-instance browser smoke test, start Compose with `--profile multi-instance` and follow [the frontend browser test instructions](frontend/e2e/README.md).
