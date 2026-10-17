# Oracle Cloud deployment

This project can run on one Oracle Linux/Ubuntu VM with Docker Compose. The deployment stack serves the React build, Spring API, STOMP endpoint, and C++ game WebSocket through Caddy. PostgreSQL remains private on the Compose network. Avatars use OCI Object Storage's S3-compatible API. The development stack remains separate and keeps PostgreSQL, MinIO, and Mailpit local.

The same deployment Compose stack also works on a home Linux machine. Point a domain or dynamic DNS hostname at the home's public IP, forward router TCP ports 80 and 443 to that machine, and allow those ports through its host firewall. Do not forward PostgreSQL, Spring, or C++ ports. Normal port forwarding cannot work through ISP CGNAT; in that case use a public VM or a tunnel service. The app's external S3 settings can point to OCI Object Storage or another compatible provider.

## 1. Create the Oracle resources

Create an [Always Free eligible](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm) Ampere A1 Ubuntu VM in your tenancy's home region. A1 Always Free capacity is limited to the eligible monthly OCPU and memory allocation and may not be available in every region. Attach a public IPv4 address.

In the VM's VCN security list or network security group, allow inbound TCP 22 only from your own IP, and TCP 80 and 443 from the internet. The deployment publishes no database, Spring, C++ server, or MinIO ports directly. Point a DNS A record for the site hostname at the VM's public IPv4 address. Caddy obtains and renews the TLS certificate; DNS must resolve before starting the stack.

Install Docker Engine and the Docker Compose plugin using [Docker's official Ubuntu installation instructions](https://docs.docker.com/engine/install/ubuntu/). SSH to the VM, install Git, clone this repository, and enter its root directory.

## 2. Prepare the deployment environment

Create the ignored deployment environment file. The helper generates independent secrets for the database, access tokens, engine tokens, and internal webhook:

```sh
./scripts/prepare-deploy-env.sh
```

Edit `.env.deploy` and set:

- `APP_DOMAIN` to the DNS hostname, without `https://`.
- `ACME_EMAIL` to an email address used by the certificate authority.
- Google OAuth client credentials. Add `https://YOUR_DOMAIN/login/oauth2/code/google` as an authorized redirect URI in Google Cloud.
- SMTP hostname, port, username, password, and sender. For STARTTLS, use the SMTP provider's supported port, usually 587.
- OCI Object Storage settings. Create a private bucket in the VM's region, create an OCI Customer Secret Key, and set its access key and secret. The endpoint format is `https://NAMESPACE.compat.objectstorage.REGION.oraclecloud.com`; set `APP_S3_REGION` to the OCI region identifier, and keep path-style addressing enabled. OCI documents the S3 compatibility API and these credentials [here](https://docs.oracle.com/en-us/iaas/Content/Object/Tasks/s3compatibleapi_topic-Amazon_S3_Compatibility_API_Support.htm) and [here](https://docs.oracle.com/en-us/iaas/Content/Identity/access/working-with-customer-secret-keys.htm).

Keep `.env.deploy` private. It is ignored by Git and the helper sets file permissions to owner-only. Do not paste its contents into chat or commit it.

Check that no sample placeholders remain and that secrets are long and distinct:

```sh
./scripts/validate-deploy-env.sh
```

## 3. Validate and start

Check the Compose configuration before building. This validates configuration syntax and required variables without printing the resolved secrets:

```sh
docker compose --env-file .env.deploy -f compose.deploy.yaml config --quiet
```

Build separately to avoid competing for memory on the small VM, then start the stack:

```sh
docker compose --env-file .env.deploy -f compose.deploy.yaml build backend
docker compose --env-file .env.deploy -f compose.deploy.yaml build chess-server
docker compose --env-file .env.deploy -f compose.deploy.yaml build frontend
docker compose --env-file .env.deploy -f compose.deploy.yaml up -d
docker compose --env-file .env.deploy -f compose.deploy.yaml ps
```

The frontend is served at `https://APP_DOMAIN`. The C++ server is reachable to browsers through `wss://APP_DOMAIN/engine/ws/play`; Caddy removes `/engine` before forwarding to the C++ server's `/ws/play` endpoint. The backend reports that public URL to players. The `/api` and STOMP `/ws` paths go to Spring Boot.

## Operations

```sh
docker compose --env-file .env.deploy -f compose.deploy.yaml logs -f
docker compose --env-file .env.deploy -f compose.deploy.yaml pull
docker compose --env-file .env.deploy -f compose.deploy.yaml up -d --build
docker compose --env-file .env.deploy -f compose.deploy.yaml down
```

The named PostgreSQL and Caddy volumes survive container replacement. Back up PostgreSQL before upgrades or maintenance:

```sh
docker compose --env-file .env.deploy -f compose.deploy.yaml exec -T postgres \
  pg_dump -U "$(sed -n 's/^DB_USERNAME=//p' .env.deploy)" "$(sed -n 's/^DB_NAME=//p' .env.deploy)" \
  > "chess-backup-$(date -u +%Y%m%dT%H%M%SZ).sql"
```

Store database backups outside the VM as well. PostgreSQL and avatars contain user data; persistent volumes alone are not a backup.

## Local development

The existing `backend/compose.yaml`, `backend/.env`, and `server/.env` local workflow is retained. A unified root development stack is also available:

```sh
cp .env.dev.example .env.dev
docker compose --env-file .env.dev -f compose.dev.yaml up --build
```

It serves Vite on `http://localhost:5173`, Spring on `http://localhost:8080`, C++ WebSocket on `ws://localhost:9002`, MinIO on `http://localhost:9003` (console `http://localhost:9004`), and Mailpit at `http://localhost:8025`. The local stack uses separate named volumes and does not share deployment data.
