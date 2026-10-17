import { expect, test, type Page, type WebSocketRoute } from "@playwright/test";
import { Chess } from "chess.js";

const appUrl = process.env.E2E_FIRST_URL ?? "http://localhost:5173";
const player = {
  publicId: "123e4567-e89b-42d3-a456-426614174000",
  username: "turn_test",
  displayName: "Turn Test",
  email: "turn-test@example.test",
};
const profile = {
  ...player,
  id: 1,
  bio: null,
  avatarUrl: null,
  totalGames: 0,
  wins: 0,
  losses: 0,
  draws: 0,
};
const preferences = {
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

test("email and password registration submits and opens the sign-in step", async ({
  page,
}) => {
  let submitted: Record<string, string> | undefined;
  await page.route("**/api/auth/refresh", (route) =>
    route.fulfill({
      status: 401,
      contentType: "application/json",
      body: JSON.stringify({
        success: false,
        errorCode: "REFRESH_TOKEN_INVALID",
        message: "Sign in required",
      }),
    }),
  );
  await page.route("**/api/auth/register", async (route) => {
    submitted = route.request().postDataJSON();
    await route.fulfill({
      status: 202,
      contentType: "application/json",
      body: JSON.stringify({
        message: "Check your email to verify your account",
      }),
    });
  });

  await page.goto(`${appUrl}/register`);
  await page.getByLabel("Username", { exact: true }).fill("email_player");
  await page.getByLabel("Display name").fill("Email Player");
  await page.getByLabel("Email").fill("email-player@example.test");
  await page
    .getByRole("textbox", { name: "Password" })
    .fill("correct-horse-10");
  await page.getByRole("button", { name: "Create account" }).click();

  await expect(page).toHaveURL(`${appUrl}/login`);
  await expect(
    page.getByText("Check your email", { exact: true }),
  ).toBeVisible();
  expect(submitted).toEqual({
    username: "email_player",
    displayName: "Email Player",
    email: "email-player@example.test",
    password: "correct-horse-10",
  });
});

test("position editor undo and redo cover mode changes, board edits, FEN, and reset", async ({
  page,
}) => {
  await installAuthenticatedSession(page);
  await page.goto(`${appUrl}/position-setup`);
  await expect(
    page.getByRole("heading", { name: "Set up a position" }),
  ).toBeVisible();

  const moveMode = page.getByRole("button", { name: "Play a move sequence" });
  const directMode = page.getByRole("button", { name: "Arrange pieces" });
  await moveMode.click();
  await directMode.click();
  await page.getByRole("button", { name: "Undo" }).click();
  await expect(moveMode).toHaveAttribute("aria-pressed", "true");
  await page.getByRole("button", { name: "Redo" }).click();
  await expect(directMode).toHaveAttribute("aria-pressed", "true");

  const fen = page.getByLabel("FEN import or export");
  await page.getByRole("combobox", { name: "Start with" }).click();
  await page.getByRole("option", { name: "Empty board" }).click();
  await page.getByRole("button", { name: "Place white K" }).click();
  await page.locator('[data-square="e1"]').click();
  const kingPosition = "8/8/8/8/8/8/8/4K3 w - - 0 1";
  const emptyPosition = "8/8/8/8/8/8/8/8 w - - 0 1";
  await expect(fen).toHaveValue(kingPosition);

  await page.keyboard.press("Control+z");
  await expect(fen).toHaveValue(emptyPosition);
  await page.keyboard.press("Control+Shift+z");
  await expect(fen).toHaveValue(kingPosition);

  await fen.fill("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
  await page.getByRole("button", { name: "Import FEN" }).click();
  await expect(fen).toHaveValue(
    "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
  );
  await page.getByRole("button", { name: "Undo" }).click();
  await expect(fen).toHaveValue(kingPosition);

  await page.getByRole("combobox", { name: "Start with" }).click();
  await page.getByRole("option", { name: "Standard position" }).click();
  await expect(fen).toHaveValue(
    "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
  );
  await page.getByRole("button", { name: "Undo" }).click();
  await expect(fen).toHaveValue(kingPosition);

  await moveMode.click();
  await page.locator('[data-square="e2"]').click();
  await page.locator('[data-square="e4"]').click();
  await expect(page.getByText("Setup moves: e4")).toBeVisible();
  await page.getByRole("button", { name: "Undo" }).click();
  await expect(page.getByText("Setup moves: none")).toBeVisible();
  await page.getByRole("button", { name: "Redo" }).click();
  await expect(page.getByText("Setup moves: e4")).toBeVisible();
});

test("turn settings save and reload with their stored values", async ({
  page,
}) => {
  await installAuthenticatedSession(page);
  let stored = {
    ...preferences,
    turnIndicatorEnabled: true,
    turnSoundEnabled: false,
  };
  await page.route("**/api/users/me/settings", async (route) => {
    if (route.request().method() === "PUT")
      stored = route.request().postDataJSON();
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: envelope(stored),
    });
  });
  await page.route("**/api/users/me", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: envelope(profile),
    }),
  );

  await page.goto(`${appUrl}/settings`);
  await expect(page.getByRole("heading", { name: "Settings" })).toBeVisible();
  await page.getByRole("tab", { name: "Game" }).click();
  await page
    .getByRole("switch", { name: "Turn indicator" })
    .uncheck({ force: true });
  await page.getByRole("switch", { name: "Turn sound" }).check({ force: true });
  await page.getByRole("button", { name: "Save changes" }).click();
  await expect(
    page.getByText("Your preferences sync with your account."),
  ).toBeVisible();
  expect(stored.turnIndicatorEnabled).toBe(false);
  expect(stored.turnSoundEnabled).toBe(true);

  await page.reload();
  await expect(page.getByRole("heading", { name: "Settings" })).toBeVisible();
  await page.getByRole("tab", { name: "Game" }).click();
  await expect(
    page.getByRole("switch", { name: "Turn indicator" }),
  ).not.toBeChecked();
  await expect(page.getByRole("switch", { name: "Turn sound" })).toBeChecked();
});

test("unlimited clocks and delayed turn updates dispatch queued moves on sync", async ({
  page,
}) => {
  await installAuthenticatedSession(page);
  await page.addInitScript(() => {
    const frequencies: number[] = [];
    Object.defineProperty(window, "rookTestFrequencies", {
      value: frequencies,
    });
    class TestAudioContext {
      currentTime = 0;
      state = "running";
      destination = {};
      createOscillator() {
        const oscillator = {
          type: "sine",
          frequency: { value: 0 },
          connect() {},
          start() {
            frequencies.push(oscillator.frequency.value);
          },
          stop() {},
        };
        return oscillator;
      }
      createGain() {
        return {
          gain: { setValueAtTime() {}, exponentialRampToValueAtTime() {} },
          connect() {},
        };
      }
    }
    Object.defineProperty(window, "AudioContext", { value: TestAudioContext });
  });

  let finishSettingsFetch: () => void = () => {};
  const settingsLoaded = new Promise<void>((resolve) => {
    finishSettingsFetch = resolve;
  });
  await page.route("**/api/users/me/settings", (route) => {
    finishSettingsFetch();
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: envelope(preferences),
    });
  });
  await page.route("**/api/users/turn_rival/profile", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: envelope({
        ...profile,
        username: "turn_rival",
        displayName: "Rival",
      }),
    }),
  );
  await page.route("**/api/challenges/51", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: envelope({
        challengeId: 51,
        status: "ACCEPTED",
        opponentPublicUserId: "223e4567-e89b-42d3-a456-426614174000",
        opponentUsername: "turn_rival",
        initialTimeSeconds: null,
        incrementSeconds: null,
        challengerSide: "WHITE",
        playerColor: "WHITE",
        roomCode: "ROOM51",
        initialFen: null,
        setupMoves: [],
        setupFens: [],
        connectUrl: "ws://engine.test/game",
        token: "test-token",
        expiresAt: "2030-01-01T00:00:00Z",
      }),
    }),
  );

  const serverGame = new Chess();
  let socket: WebSocketRoute | undefined;
  let delayedOpponentEvent: string | undefined;
  const playedMoves: string[] = [];
  await page.routeWebSocket("ws://engine.test/game", (route) => {
    socket = route;
    route.onMessage((message) => {
      const event = JSON.parse(String(message)) as {
        action?: string;
        uci?: string;
      };
      if (event.action === "connect_token") {
        route.send(
          JSON.stringify({
            event: "room_joined",
            room_code: "ROOM51",
            role: "white",
          }),
        );
        route.send(
          JSON.stringify({
            event: "game_started",
            room_code: "ROOM51",
            fen: serverGame.fen(),
            time_control: "unlimited",
            white_time_ms: -1,
            black_time_ms: -1,
            moves: [],
            setup_moves: [],
            setup_fens: [],
          }),
        );
      }
      if (event.action === "move" && event.uci) {
        playedMoves.push(event.uci);
        const move = serverGame.move({
          from: event.uci.slice(0, 2),
          to: event.uci.slice(2, 4),
          promotion: event.uci[4] ?? "q",
        });
        route.send(JSON.stringify(moveEvent(serverGame, move.san, event.uci)));
      }
    });
  });

  const sendOpponentMove = () => {
    const move = serverGame.move({ from: "d7", to: "d5" });
    delayedOpponentEvent = JSON.stringify(
      moveEvent(serverGame, move.san, "d7d5"),
    );
  };
  await page.goto(`${appUrl}/game/51`);
  await settingsLoaded;
  await expect(page.getByText("∞")).toHaveCount(2);
  await expect(page.locator(".game-player-identity-turn")).toHaveCount(1);

  await page.locator('[data-square="e2"]').click();
  await page.locator('[data-square="e4"]').click();
  await expect.poll(() => playedMoves).toContain("e2e4");
  await expect(
    page
      .locator(".opponent-strip")
      .first()
      .locator(".game-player-identity--active"),
  ).toHaveCount(1);

  sendOpponentMove();
  await page.locator('[data-square="a2"]').click();
  await page.locator('[data-square="a3"]').click();
  expect(socket).toBeDefined();
  expect(delayedOpponentEvent).toBeDefined();
  socket?.send(delayedOpponentEvent!);
  await expect.poll(() => playedMoves).toContain("a2a3");
  await expect
    .poll(async () =>
      page.evaluate(
        () =>
          (window as Window & { rookTestFrequencies: number[] })
            .rookTestFrequencies,
      ),
    )
    .toContain(790);
  await expect(page.getByText("∞")).toHaveCount(2);
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
}

function envelope(data: unknown): string {
  return JSON.stringify({
    success: true,
    code: 200,
    message: "ok",
    data,
    errors: null,
    timestamp: null,
  });
}

function moveEvent(game: Chess, san: string, uci: string) {
  return {
    event: "move_made",
    san,
    uci,
    fen: game.fen(),
    side_to_move: game.turn() === "w" ? "white" : "black",
    is_check: game.isCheck(),
    white_time_ms: -1,
    black_time_ms: -1,
  };
}
