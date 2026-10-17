import { expect, test, type Page } from "@playwright/test";

const appUrl = process.env.E2E_FIRST_URL ?? "http://localhost:5173";
const player = {
  publicId: "123e4567-e89b-42d3-a456-426614174000",
  username: "cache_test",
  displayName: "Cache Test",
  email: "cache-test@example.test",
};
const profile = {
  ...player,
  id: 1,
  bio: null,
  avatarUrl: null,
  searchable: true,
  totalGames: 0,
  wins: 0,
  losses: 0,
  draws: 0,
};
const settings = {
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
};

test("position editor restores a saved draft after a reload", async ({
  page,
}) => {
  await installApi(page);
  let savedDraft: {
    featureKey: string;
    payload: unknown;
    updatedAt: string;
  } | null = null;
  const savedPayloads: unknown[] = [];
  await page.route("**/api/users/me/drafts**", async (route) => {
    const request = route.request();
    if (request.method() === "PUT") {
      const payload = request.postDataJSON().payload;
      savedPayloads.push(payload);
      savedDraft = {
        featureKey: "position-setup",
        payload,
        updatedAt: new Date().toISOString(),
      };
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: envelope(savedDraft),
      });
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: envelope(savedDraft ? [savedDraft] : []),
    });
  });

  await page.goto(`${appUrl}/position-setup`);
  await page.getByRole("combobox", { name: "Start with" }).click();
  await page.getByRole("option", { name: "Empty board" }).click();
  await page.getByRole("button", { name: "Place white K" }).click();
  await page.locator('[data-square="e1"]').click();
  const fen = page.getByLabel("FEN import or export");
  await expect(fen).toHaveValue("8/8/8/8/8/8/8/4K3 w - - 0 1");
  await expect
    .poll(() => savedPayloads.at(-1) as { fen?: string } | undefined)
    .toMatchObject({ fen: "8/8/8/8/8/8/8/4K3 w - - 0 1" });

  await page.reload();

  await expect(fen).toHaveValue("8/8/8/8/8/8/8/4K3 w - - 0 1");
  await expect(page.getByText("Draft saved", { exact: false })).toBeVisible();
});

test("position editor restores an account draft without local browser data", async ({
  page,
}) => {
  await installApi(page);
  const savedDraft = {
    featureKey: "position-setup",
    payload: {
      mode: "DIRECT",
      fen: "8/8/8/8/8/8/8/4K3 w - - 0 1",
      start: "CUSTOM",
      fenInput: "8/8/8/8/8/8/8/4K3 w - - 0 1",
      setupMoves: [],
      setupFens: [],
    },
    updatedAt: new Date().toISOString(),
  };
  await page.route("**/api/users/me/drafts**", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: envelope([savedDraft]),
    }),
  );

  await page.goto(`${appUrl}/position-setup`);

  await expect(page.getByLabel("FEN import or export")).toHaveValue(
    "8/8/8/8/8/8/8/4K3 w - - 0 1",
  );
  await expect(page.getByText("Draft saved to your account.")).toBeVisible();
});

test("offline position edits restore locally and sync after reconnecting", async ({
  page,
}) => {
  await installApi(page);
  await page.route("**/api/friends", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: envelope([
        {
          publicUserId: "123e4567-e89b-42d3-a456-426614174001",
          username: "opponent",
          displayName: "Opponent",
          avatarUrl: null,
          presence: "OFFLINE",
        },
      ]),
    }),
  );
  const emptyPage = { items: [], page: 0, size: 20, totalElements: 0 };
  await page.route("**/api/challenges/incoming", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: envelope(emptyPage),
    }),
  );
  await page.route("**/api/challenges/outgoing", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: envelope(emptyPage),
    }),
  );
  let online = true;
  let savedDraft: {
    featureKey: string;
    payload: { fen?: string };
    updatedAt: string;
  } | null = null;
  await page.route("**/api/users/me/drafts**", async (route) => {
    if (!online) {
      await route.fulfill({
        status: 503,
        contentType: "application/json",
        body: JSON.stringify({ message: "Offline" }),
      });
      return;
    }
    if (route.request().method() === "PUT") {
      savedDraft = {
        featureKey: "position-setup",
        payload: route.request().postDataJSON().payload,
        updatedAt: new Date().toISOString(),
      };
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: envelope(savedDraft),
      });
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: envelope(savedDraft ? [savedDraft] : []),
    });
  });

  await page.goto(`${appUrl}/play`);
  await page.getByRole("combobox", { name: "Starting position" }).click();
  await page.getByRole("option", { name: "Custom position" }).click();
  await page.getByRole("button", { name: "Set up position" }).click();
  await expect(
    page.getByRole("heading", { name: "Set up a position" }),
  ).toBeVisible();
  await page.context().setOffline(true);
  online = false;
  await page.getByRole("combobox", { name: "Start with" }).click();
  await page.getByRole("option", { name: "Empty board" }).click();
  await page.getByRole("button", { name: "Place white K" }).click();
  await page.locator('[data-square="e1"]').click();

  await expect(
    page.getByText("Saved in this browser. Sync will retry when online."),
  ).toBeVisible();
  expect(savedDraft).toBeNull();

  await page.getByRole("button", { name: "Cancel" }).click();
  await expect(page).toHaveURL(/\/play$/);
  await page.getByRole("combobox", { name: "Starting position" }).click();
  await page.getByRole("option", { name: "Custom position" }).click();
  await page.getByRole("button", { name: "Set up position" }).click();

  await expect(page.getByLabel("FEN import or export")).toHaveValue(
    "8/8/8/8/8/8/8/4K3 w - - 0 1",
  );
  await expect(
    page.getByText("Saved in this browser. Sync will retry when online."),
  ).toBeVisible();

  online = true;
  await page.context().setOffline(false);
  await expect
    .poll(() => savedDraft?.payload.fen)
    .toBe("8/8/8/8/8/8/8/4K3 w - - 0 1");
  await expect(page.getByText("Draft saved to your account.")).toBeVisible();
});

test("profile data is shown from the browser cache after a reload", async ({
  page,
}) => {
  let profileRequests = 0;
  await installApi(page, () => {
    profileRequests += 1;
  });

  await page.goto(`${appUrl}/profile`);
  await expect(
    page.getByRole("heading", { name: "Your profile" }),
  ).toBeVisible();
  await expect.poll(() => profileRequests).toBe(1);
  await page.waitForTimeout(500);

  await page.reload();

  await expect(
    page.getByRole("heading", { name: "Your profile" }),
  ).toBeVisible();
  await expect.poll(() => profileRequests).toBe(1);
});

async function installApi(page: Page, onProfileRequest?: () => void) {
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
      body: envelope(settings),
    }),
  );
  await page.route("**/api/users/me", (route) => {
    onProfileRequest?.();
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: envelope(profile),
    });
  });
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
