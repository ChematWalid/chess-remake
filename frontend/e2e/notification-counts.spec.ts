import { expect, test, type Page } from "@playwright/test";

const appUrl = process.env.E2E_FIRST_URL ?? "http://localhost:5173";
const player = {
  publicId: "123e4567-e89b-42d3-a456-426614174000",
  username: "count_test",
  displayName: "Count Test",
  email: "count-test@example.test",
};
const incomingRequest = (requestId: number, username: string) => ({
  requestId,
  publicUserId: `123e4567-e89b-42d3-a456-42661417400${requestId}`,
  username,
  displayName: username,
  status: "PENDING",
  avatarUrl: null,
  createdAt: new Date().toISOString(),
});

test("Friends sections and navigation show request counts", async ({
  page,
}) => {
  await installAuthenticatedSession(page);
  await page.route("**/api/friends/requests/incoming", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: envelope([
        incomingRequest(1, "player_one"),
        incomingRequest(2, "player_two"),
      ]),
    }),
  );
  await page.route("**/api/friends/requests/outgoing", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: envelope([incomingRequest(3, "player_three")]),
    }),
  );

  await page.goto(`${appUrl}/friends`);

  await expect(page.getByLabel("2 incoming friend requests")).toHaveCount(2);
  await expect(page.getByLabel("1 sent friend requests")).toBeVisible();
  await expect(page.getByLabel("blocked players")).toHaveCount(0);
});

test("Play sections and navigation show pending challenge counts", async ({
  page,
}) => {
  await installAuthenticatedSession(page);
  await page.route("**/api/challenges/incoming", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: envelope(pageOf([challenge(1, "opponent_one")])),
    }),
  );
  await page.route("**/api/challenges/outgoing", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: envelope(pageOf([challenge(2, "opponent_two", "OUTGOING")])),
    }),
  );

  await page.goto(`${appUrl}/play`);

  await expect(page.getByLabel("1 incoming challenges")).toHaveCount(2);
  await expect(page.getByLabel("1 sent challenges")).toBeVisible();
});

async function installAuthenticatedSession(page: Page) {
  await page.route("**/api/auth/refresh", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: envelope({
        accessToken: "test-access-token",
        tokenType: "Bearer",
        expiresInSeconds: 3600,
        onboardingRequired: false,
        user: player,
      }),
    }),
  );
  await page.route("**/api/users/me/settings", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: envelope({
        boardPalette: "FOREST",
        boardOrientation: "AUTO",
        motionPreference: "SYSTEM",
        themePreference: "DARK",
        showCoordinates: true,
        showLegalMoveHints: true,
        premovesEnabled: true,
        moveConfirmation: false,
        gameSoundsEnabled: false,
        turnIndicatorEnabled: true,
        turnSoundEnabled: true,
        inAppNotificationsEnabled: true,
        notificationSoundsEnabled: false,
      }),
    }),
  );
  await page.route("**/api/users/me", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: envelope({
        ...player,
        id: 1,
        bio: null,
        avatarUrl: null,
        searchable: true,
        totalGames: 0,
        wins: 0,
        losses: 0,
        draws: 0,
      }),
    }),
  );
  await page.route("**/api/friends", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: envelope([]),
    }),
  );
  await page.route("**/api/friends/blocked", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: envelope([]),
    }),
  );
  await page.route("**/api/friends/requests/incoming", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: envelope([]),
    }),
  );
  await page.route("**/api/friends/requests/outgoing", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: envelope([]),
    }),
  );
  await page.route("**/api/challenges/incoming", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: envelope(pageOf([])),
    }),
  );
  await page.route("**/api/challenges/outgoing", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: envelope(pageOf([])),
    }),
  );
}

function challenge(
  challengeId: number,
  opponent: string,
  direction = "INCOMING",
) {
  return {
    challengeId,
    status: "PENDING",
    challengerPublicUserId: "123e4567-e89b-42d3-a456-426614174001",
    challengerUsername: opponent,
    challengedPublicUserId: player.publicId,
    challengedUsername: player.username,
    initialTimeSeconds: null,
    incrementSeconds: null,
    challengerSide: "WHITE",
    playerColor: null,
    roomCode: null,
    initialFen: null,
    setupMoves: [],
    setupFens: [],
    createdAt: new Date().toISOString(),
    expiresAt: new Date(Date.now() + 60_000).toISOString(),
    direction,
  };
}

function pageOf<T>(items: T[]) {
  return { items, page: 0, size: 20, totalElements: items.length };
}

function envelope(data: unknown): string {
  return JSON.stringify({
    success: true,
    code: 200,
    errorCode: null,
    message: "ok",
    data,
    errors: null,
    timestamp: new Date().toISOString(),
  });
}
