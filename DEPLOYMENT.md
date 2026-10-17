# Self-host on your home PC

The deployment stack runs the React site, Spring Boot API and STOMP endpoint, C++ game server, PostgreSQL, and MinIO on one PC with Docker Compose. Caddy is the only service published to the internet; it serves HTTPS and routes API, realtime, and game traffic. PostgreSQL and MinIO remain private on the Compose network. Their data, along with Caddy state and unfinished-game records, is stored in named volumes.

## What you need

- A Linux PC that can stay powered on while people play, with Docker Engine and the Docker Compose plugin installed.
- A public IPv4 address and router access to forward **TCP 80 and TCP 443** to that PC. Do not forward PostgreSQL, Spring Boot, C++, or MinIO ports.
- A domain or dynamic DNS hostname pointed at your home public IP. If the IP changes, configure a dynamic DNS updater. Caddy obtains and renews the HTTPS certificate automatically.
- An ISP connection that accepts inbound connections. If the ISP uses CGNAT, ordinary router port forwarding will not reach your PC; you will need a public IP from the ISP or a tunnel/reverse-proxy service.
- Google OAuth credentials and SMTP credentials for Google sign-in and account email. MinIO and the database are hosted locally by this Compose stack; no external object storage account is needed.

Allow TCP 80 and 443 through the PC's host firewall as well as the router. Point DNS to your current public IP before starting the stack so Caddy can issue a certificate. For Google sign-in, add `https://YOUR_DOMAIN/login/oauth2/code/google` as an authorized redirect URI in Google Cloud.

## Configure the deployment

From the repository root on the PC, create the private deployment environment file. The script creates independent database, JWT, internal webhook, and MinIO credentials and sets the file mode to owner-only:

```sh
./scripts/prepare-deploy-env.sh
```

Edit `.env.deploy`:

- Set `APP_DOMAIN` to your domain or dynamic DNS hostname, without `https://`.
- Set `ACME_EMAIL` to an email address for certificate notices.
- Set `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, and `APP_EMAIL_FROM` to your mail provider's values. For STARTTLS, the usual port is 587.
- Set `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` from Google Cloud.
- Keep the generated database, JWT, webhook, and MinIO secrets. `APP_S3_ENDPOINT` should be `http://minio:9000`; the deployment's MinIO container uses the matching generated access and secret keys. The backend creates the configured bucket on startup.

Keep `.env.deploy` private. It is ignored by Git. Do not paste its contents into chat or commit it. Validate that required settings are present:

```sh
./scripts/validate-deploy-env.sh
```

## Build and start

Check Compose interpolation and syntax without printing the resolved secrets, then build each image and launch the services:

```sh
docker compose --env-file .env.deploy -f compose.deploy.yaml config --quiet
docker compose --env-file .env.deploy -f compose.deploy.yaml build backend
docker compose --env-file .env.deploy -f compose.deploy.yaml build chess-server
docker compose --env-file .env.deploy -f compose.deploy.yaml build frontend
docker compose --env-file .env.deploy -f compose.deploy.yaml up -d
docker compose --env-file .env.deploy -f compose.deploy.yaml ps
```

The site is available at `https://APP_DOMAIN`. Game connections use `wss://APP_DOMAIN/engine/ws/play`; Caddy routes them to the C++ server. `/api` and the Spring STOMP endpoint `/ws` are routed to the backend.

## Updates and backups

```sh
docker compose --env-file .env.deploy -f compose.deploy.yaml logs -f
docker compose --env-file .env.deploy -f compose.deploy.yaml up -d --build
docker compose --env-file .env.deploy -f compose.deploy.yaml down
```

Back up PostgreSQL and MinIO data to a location outside this PC. The volumes persist across container replacement, but they are not a backup. PostgreSQL contains accounts and game history; MinIO contains avatars.

```sh
docker compose --env-file .env.deploy -f compose.deploy.yaml exec -T postgres \
  pg_dump -U "$(sed -n 's/^DB_USERNAME=//p' .env.deploy)" "$(sed -n 's/^DB_NAME=//p' .env.deploy)" \
  > "chess-backup-$(date -u +%Y%m%dT%H%M%SZ).sql"
```

Use a MinIO client or the MinIO console from a trusted local network to back up the `deploy-minio` volume. Do not expose the MinIO ports on the router.

## Local development

The existing backend and game-server local workflows remain available. The unified root development stack uses separate environment files and volumes:

```sh
cp .env.dev.example .env.dev
docker compose --env-file .env.dev -f compose.dev.yaml up --build
```

It serves Vite on `http://localhost:5173`, Spring on `http://localhost:8080`, C++ WebSocket on `ws://localhost:9002`, MinIO on `http://localhost:9003` (console `http://localhost:9004`), and Mailpit at `http://localhost:8025`. Development data is separate from deployment data.
