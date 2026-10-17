# Browser smoke test

The custom position challenge flow uses two backend instances connected to the same PostgreSQL, Redis, and RabbitMQ services.

Start the local stack with the optional second instance:

```sh
docker compose --env-file .env.dev -f compose.dev.yaml --profile multi-instance up -d --build
```

Then run the browser test from `frontend`:

```sh
npm run test:e2e
```

The test registers two fresh accounts, reads their verification links from Mailpit, creates a friendship, signs in through each frontend, sends a custom FEN challenge from one backend, and accepts it through the other. It checks that the accepted challenge retains the chosen FEN and color.

Override `E2E_FIRST_URL`, `E2E_SECOND_URL`, `E2E_API_URL`, and `E2E_MAILPIT_URL` when the services use different addresses. Playwright downloads Chromium with `npx playwright install chromium`.
