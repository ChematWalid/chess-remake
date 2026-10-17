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

test("cached profile data shows immediately and refreshes in the background", async ({
  page,
}) => {
  let profileRequests = 0;
  await installApi(page);
  await page.unroute("**/api/users/me");
  await page.route("**/api/users/me", async (route) => {
    profileRequests += 1;
    if (profileRequests > 1)
      await new Promise((resolve) => setTimeout(resolve, 1_500));
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: envelope({
        ...profile,
        displayName: profileRequests > 1 ? "Updated Cache Test" : "Cache Test",
      }),
    });
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
  const displayName = page.getByRole("textbox", { name: "Display name" });
  await expect(displayName).toHaveValue("Cache Test");
  await expect.poll(() => profileRequests).toBe(2);
  await expect(displayName).toHaveValue("Updated Cache Test");
});

test("pagehide flush persists successful cached queries", async ({ page }) => {
  await page.addInitScript(() => {
    const nativeSetTimeout = window.setTimeout.bind(window);
    window.setTimeout = ((handler, timeout, ...args) =>
      nativeSetTimeout(
        handler,
        timeout === 250 ? 60_000 : timeout,
        ...args,
      )) as typeof window.setTimeout;
  });
  await installApi(page);

  await page.goto(`${appUrl}/profile`);
  await expect(
    page.getByRole("heading", { name: "Your profile" }),
  ).toBeVisible();
  await page.evaluate(() => window.dispatchEvent(new Event("pagehide")));

  const cached = await page.evaluate(
    (key) =>
      new Promise<
        { state: { queries: Array<{ queryKey: unknown[] }> } } | undefined
      >((resolve, reject) => {
        const openRequest = indexedDB.open("rook-browser-cache", 1);
        openRequest.onerror = () => reject(openRequest.error);
        openRequest.onsuccess = () => {
          const request = openRequest.result
            .transaction("entries")
            .objectStore("entries")
            .get(key);
          request.onsuccess = () => resolve(request.result);
          request.onerror = () => reject(request.error);
        };
      }),
    `query-cache:${player.publicId}`,
  );

  expect(
    cached?.state.queries.some((query) => query.queryKey[0] === "profile"),
  ).toBe(true);
});

test("draft restoration does not overwrite edits made during an IndexedDB read", async ({
  page,
}) => {
  await delayDraftLocalRead(page);
  await installApi(page);
  let savedValue: string | undefined;
  await page.route("**/api/users/me/drafts**", async (route) => {
    if (route.request().method() === "PUT") {
      savedValue = route.request().postDataJSON().payload.value;
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: envelope({
          featureKey: "player-search",
          payload: { value: savedValue },
          updatedAt: new Date().toISOString(),
          editedAt: route.request().postDataJSON().editedAt,
        }),
      });
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: envelope([]),
    });
  });

  await page.goto(`${appUrl}/search`);
  const search = page.getByRole("textbox", { name: "Search players" });
  await search.fill("new input");
  await expect(search).toHaveValue("new input");
  await page.waitForTimeout(3_200);

  await expect(search).toHaveValue("new input");
  await expect.poll(() => savedValue).toBe("new input");
});

test("offline draft sync adopts a newer server version", async ({ page }) => {
  await installApi(page);
  const oldEdit = new Date(Date.now() - 60_000).toISOString();
  let serverDraft = {
    featureKey: "player-search",
    payload: { value: "starting value" },
    updatedAt: oldEdit,
    editedAt: oldEdit,
  };
  let submittedEditAt: string | undefined;
  await page.route("**/api/users/me/drafts**", async (route) => {
    if (route.request().method() === "PUT") {
      submittedEditAt = route.request().postDataJSON().editedAt;
      serverDraft = {
        featureKey: "player-search",
        payload: { value: "newer edit from another device" },
        updatedAt: new Date().toISOString(),
        editedAt: new Date(Date.now() + 60_000).toISOString(),
      };
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: envelope(serverDraft),
      });
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: envelope([serverDraft]),
    });
  });

  await page.goto(`${appUrl}/search`);
  const search = page.getByRole("textbox", { name: "Search players" });
  await expect(search).toHaveValue("starting value");
  await page.context().setOffline(true);
  await search.fill("offline edit");
  await expect(
    page.getByText("Saved in this browser. Sync will retry when online."),
  ).toBeVisible();

  await page.context().setOffline(false);

  await expect(search).toHaveValue("newer edit from another device");
  expect(submittedEditAt).toBeDefined();
});

test("clearing a draft reports pending removal while offline", async ({
  page,
}) => {
  await installApi(page);
  let savedDraft: {
    featureKey: string;
    payload: { value: string };
    updatedAt: string;
    editedAt: string;
  } | null = {
    featureKey: "player-search",
    payload: { value: "saved search" },
    updatedAt: new Date().toISOString(),
    editedAt: new Date().toISOString(),
  };
  await page.route("**/api/users/me/drafts**", async (route) => {
    if (route.request().method() === "DELETE") {
      savedDraft = null;
      await route.fulfill({ status: 204 });
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: envelope(savedDraft ? [savedDraft] : []),
    });
  });

  await page.goto(`${appUrl}/search`);
  await expect(
    page.getByRole("textbox", { name: "Search players" }),
  ).toHaveValue("saved search");
  await page.context().setOffline(true);
  await page.getByRole("button", { name: "Clear saved draft" }).click();
  await expect(
    page.getByText("Draft removal is saved here and will sync when online."),
  ).toBeVisible();

  await page.context().setOffline(false);
  await expect(page.getByText("Saved draft cleared.")).toBeVisible();
  expect(savedDraft).toBeNull();
});

async function delayDraftLocalRead(page: Page) {
  await page.addInitScript(() => {
    const delayedRequests = new WeakSet<IDBRequest>();
    const originalGet = IDBObjectStore.prototype.get;
    IDBObjectStore.prototype.get = function (key: IDBValidKey) {
      const request = originalGet.call(this, key);
      if (String(key).startsWith("draft-local:")) delayedRequests.add(request);
      return request;
    };
    const descriptor = Object.getOwnPropertyDescriptor(
      IDBRequest.prototype,
      "onsuccess",
    );
    if (!descriptor?.get || !descriptor.set) return;
    Object.defineProperty(IDBRequest.prototype, "onsuccess", {
      configurable: true,
      enumerable: descriptor.enumerable,
      get: descriptor.get,
      set(handler: ((this: IDBRequest, event: Event) => unknown) | null) {
        const delayedHandler =
          handler && delayedRequests.has(this)
            ? function (this: IDBRequest, event: Event) {
                window.setTimeout(() => handler.call(this, event), 3_000);
              }
            : handler;
        descriptor.set?.call(this, delayedHandler);
      },
    });
  });
}

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
