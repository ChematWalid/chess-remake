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

test("Google OAuth button displays the provider logo", async ({ page }) => {
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

  await page.goto(`${appUrl}/login`);

  const googleButton = page.getByRole("link", { name: "Continue with Google" });
  await expect(googleButton).toHaveAttribute(
    "href",
    /oauth2\/authorization\/google/,
  );
  await expect(googleButton.locator("svg")).toHaveAttribute(
    "aria-hidden",
    "true",
  );
  await expect(googleButton.locator('path[fill="#4285F4"]')).toBeAttached();
  await expect(googleButton.locator('path[fill="#EA4335"]')).toBeAttached();
});

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

test("authenticated home stays at root and login redirects home", async ({
  page,
}) => {
  await installAuthenticatedSession(page);
  await page.goto(`${appUrl}/`);
  await expect(page).toHaveURL(`${appUrl}/`);
  await expect(page.getByRole("link", { name: "Start playing" })).toBeVisible();

  await page.goto(`${appUrl}/login`);
  await expect(page).toHaveURL(`${appUrl}/`);
});

test("top bar follows the saved profile name and photo", async ({ page }) => {
  await installAuthenticatedSession(page);
  let currentProfile = {
    ...profile,
    avatarUrl: "/avatars/turn-test.png",
  };
  await page.route("**/avatars/turn-test.png", (route) =>
    route.fulfill({
      status: 200,
      contentType: "image/svg+xml",
      body: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1 1"><rect width="1" height="1" fill="#9fca91" /></svg>',
    }),
  );
  await page.route("**/api/users/me", async (route) => {
    if (route.request().method() === "PUT") {
      currentProfile = {
        ...currentProfile,
        ...route.request().postDataJSON(),
      };
    }
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: envelope(currentProfile),
    });
  });

  await page.goto(`${appUrl}/profile`);
  await expect(
    page.getByRole("heading", { name: "Your profile" }),
  ).toBeVisible();
  const topBar = page.locator(".app-header");
  await expect(topBar.getByText("Turn Test")).toBeVisible();
  await expect(topBar.locator("img")).toHaveAttribute(
    "src",
    "/avatars/turn-test.png",
  );

  await page.getByLabel("Display name").fill("Updated Name");
  await page.getByRole("button", { name: "Save profile" }).click();
  await expect(topBar.getByText("Updated Name")).toBeVisible();
  await expect(topBar.locator("img")).toHaveAttribute(
    "src",
    "/avatars/turn-test.png",
  );
});

test("sidebar resizing persists after reload", async ({ page }) => {
  await installAuthenticatedSession(page);
  await page.goto(`${appUrl}/play`);
  const sidebar = page.locator(".side-nav");
  const separator = page.getByRole("separator", { name: "Resize sidebar" });
  await expect(separator).toBeVisible();
  const originalWidth = await sidebar.evaluate(
    (element) => element.getBoundingClientRect().width,
  );
  const handle = await separator.boundingBox();
  expect(handle).not.toBeNull();
  await page.mouse.move(handle!.x + handle!.width / 2, handle!.y + 80);
  await page.mouse.down();
  await page.mouse.move(handle!.x + handle!.width / 2 + 56, handle!.y + 80, {
    steps: 5,
  });
  await page.mouse.up();
  await expect
    .poll(() =>
      sidebar.evaluate((element) => element.getBoundingClientRect().width),
    )
    .toBeGreaterThan(originalWidth + 40);

  await page.reload();
  await expect(separator).toBeVisible();
  await expect
    .poll(() =>
      sidebar.evaluate((element) => element.getBoundingClientRect().width),
    )
    .toBeGreaterThan(originalWidth + 40);
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

test("game review responds to keyboard navigation and carries the shown position into a challenge", async ({
  page,
}) => {
  await installAuthenticatedSession(page);
  const game = new Chess();
  const fens = [game.fen()];
  const firstMove = game.move("e4");
  fens.push(game.fen());
  const secondMove = game.move("e5");
  fens.push(game.fen());
  const review = {
    summary: {
      id: 42,
      whiteUsername: player.username,
      blackUsername: "review_rival",
      draw: false,
      whitePublicUserId: player.publicId,
      blackPublicUserId: "223e4567-e89b-42d3-a456-426614174000",
      winnerPublicUserId: player.publicId,
      terminationReason: "CHECKMATE",
      totalMoves: 2,
      playedAt: "2026-01-01T00:00:00Z",
    },
    pgn: "",
    moves: [firstMove.san, secondMove.san],
    fens,
    initialTimeSeconds: null,
    incrementSeconds: null,
    durationSeconds: 10,
  };

  await page.route("**/api/games/42", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: envelope(review),
    }),
  );
  await page.route("**/api/users/*/profile", (route) => {
    const username = new URL(route.request().url()).pathname.split("/").at(-2);
    const currentPlayer = username === player.username;
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: envelope({
        ...profile,
        username,
        displayName: currentPlayer ? player.displayName : "Review Rival",
        publicId: currentPlayer
          ? player.publicId
          : "223e4567-e89b-42d3-a456-426614174000",
      }),
    });
  });
  await page.route("**/api/users/me/settings", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: envelope(preferences),
    }),
  );
  await page.route("**/api/friends", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: envelope([
        {
          publicUserId: "223e4567-e89b-42d3-a456-426614174000",
          username: "review_rival",
          displayName: "Review Rival",
          avatarUrl: null,
          presence: "ONLINE",
        },
      ]),
    }),
  );
  await page.route("**/api/challenges/incoming**", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: envelope({ items: [], page: 0, size: 15, totalElements: 0 }),
    }),
  );
  await page.route("**/api/challenges/outgoing**", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: envelope({ items: [], page: 0, size: 15, totalElements: 0 }),
    }),
  );

  await page.goto(`${appUrl}/games/42`);
  await expect(page.getByText("0/2", { exact: true })).toBeVisible();
  await page.keyboard.press("ArrowRight");
  await expect(page.getByText("1/2", { exact: true })).toBeVisible();
  await page.keyboard.press("ArrowRight");
  await expect(page.getByText("2/2", { exact: true })).toBeVisible();
  await page.keyboard.press("ArrowLeft");
  await expect(page.getByText("1/2", { exact: true })).toBeVisible();
  await page.keyboard.press("End");
  await expect(page.getByText("2/2", { exact: true })).toBeVisible();
  await page.keyboard.press("Home");
  await expect(page.getByText("0/2", { exact: true })).toBeVisible();

  await page.keyboard.press("ArrowRight");
  await expect(page.getByText("1/2", { exact: true })).toBeVisible();
  await page
    .getByRole("button", { name: "Challenge from this position" })
    .click();
  await expect(page).toHaveURL(`${appUrl}/play`);
  await expect(
    page.getByRole("combobox", { name: "Starting position" }),
  ).toHaveValue("Custom position");
  await expect(page.getByText("Black to move")).toBeVisible();
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
  const oldSettings: Record<string, unknown> = { ...preferences };
  delete oldSettings.turnIndicatorEnabled;
  delete oldSettings.turnSoundEnabled;
  let firstRead = true;
  await page.route("**/api/users/me/settings", async (route) => {
    if (route.request().method() === "PUT")
      stored = route.request().postDataJSON();
    const responseSettings = firstRead ? oldSettings : stored;
    firstRead = false;
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: envelope(responseSettings),
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
      resume() {
        this.state = "running";
        return Promise.resolve();
      }
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

  const draggedPawn = await page.locator('[data-square="e2"]').boundingBox();
  expect(draggedPawn).not.toBeNull();
  const pawnCenter = {
    x: draggedPawn!.x + draggedPawn!.width / 2,
    y: draggedPawn!.y + draggedPawn!.height / 2,
  };
  await page.mouse.move(pawnCenter.x, pawnCenter.y);
  await page.mouse.down();
  await page.mouse.move(pawnCenter.x, pawnCenter.y - draggedPawn!.height, {
    steps: 3,
  });
  await page.mouse.move(pawnCenter.x, pawnCenter.y, { steps: 3 });
  await page.mouse.up();
  await page.evaluate(() => {
    window.dispatchEvent(new Event("blur"));
    window.dispatchEvent(new Event("focus"));
  });

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

test("turn indicator follows the board when viewing as Black", async ({
  page,
}) => {
  await installAuthenticatedSession(page);
  await page.route("**/api/users/me/settings", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: envelope({ ...preferences, boardOrientation: "WHITE" }),
    }),
  );
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
  await page.route("**/api/challenges/52", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: envelope({
        challengeId: 52,
        status: "ACCEPTED",
        opponentPublicUserId: "223e4567-e89b-42d3-a456-426614174000",
        opponentUsername: "turn_rival",
        initialTimeSeconds: null,
        incrementSeconds: null,
        challengerSide: "WHITE",
        playerColor: "BLACK",
        roomCode: "ROOM52",
        initialFen: null,
        setupMoves: [],
        setupFens: [],
        connectUrl: "ws://engine.test/black-game",
        token: "test-token",
        expiresAt: "2030-01-01T00:00:00Z",
      }),
    }),
  );

  const blackToMove = new Chess(
    "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR b KQkq - 0 1",
  );
  await page.routeWebSocket("ws://engine.test/black-game", (route) => {
    route.onMessage((message) => {
      const event = JSON.parse(String(message)) as { action?: string };
      if (event.action !== "connect_token") return;
      route.send(
        JSON.stringify({
          event: "room_joined",
          room_code: "ROOM52",
          role: "black",
        }),
      );
      route.send(
        JSON.stringify({
          event: "game_started",
          room_code: "ROOM52",
          fen: blackToMove.fen(),
          time_control: "unlimited",
          white_time_ms: -1,
          black_time_ms: -1,
          moves: [],
          setup_moves: [],
          setup_fens: [],
        }),
      );
    });
  });

  await page.goto(`${appUrl}/game/52`);

  const strips = page.locator(".opponent-strip");
  await expect(strips.first().locator(".game-player-identity")).toHaveAttribute(
    "aria-label",
    "turn_test, Black, to move",
  );
  await expect(
    strips.first().locator(".game-player-identity--active"),
  ).toHaveCount(1);
  await expect(
    strips.last().locator(".game-player-identity--active"),
  ).toHaveCount(0);
});

test("queues a pawn recapture onto an occupied friendly square", async ({
  page,
}) => {
  await installAuthenticatedSession(page);
  await page.route("**/api/users/me/settings", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: envelope(preferences),
    }),
  );
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
  await page.route("**/api/challenges/53", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: envelope({
        challengeId: 53,
        status: "ACCEPTED",
        opponentPublicUserId: "223e4567-e89b-42d3-a456-426614174000",
        opponentUsername: "turn_rival",
        initialTimeSeconds: null,
        incrementSeconds: null,
        challengerSide: "BLACK",
        playerColor: "WHITE",
        roomCode: "ROOM53",
        initialFen: "7k/8/5p2/4P3/3P4/8/8/K7 b - - 0 1",
        setupMoves: [],
        setupFens: [],
        connectUrl: "ws://engine.test/recapture",
        token: "test-token",
        expiresAt: "2030-01-01T00:00:00Z",
      }),
    }),
  );

  const serverGame = new Chess("7k/8/5p2/4P3/3P4/8/8/K7 b - - 0 1");
  const playedMoves: string[] = [];
  let socket: WebSocketRoute | undefined;
  await page.routeWebSocket("ws://engine.test/recapture", (route) => {
    socket = route;
    route.onMessage((message) => {
      const request = JSON.parse(String(message)) as {
        action?: string;
        uci?: string;
      };
      if (request.action === "connect_token") {
        route.send(
          JSON.stringify({
            event: "room_joined",
            room_code: "ROOM53",
            role: "white",
          }),
        );
        route.send(
          JSON.stringify({
            event: "game_started",
            room_code: "ROOM53",
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
      if (request.action === "move" && request.uci) {
        playedMoves.push(request.uci);
        const move = serverGame.move({
          from: request.uci.slice(0, 2),
          to: request.uci.slice(2, 4),
          promotion: request.uci[4] ?? "q",
        });
        route.send(
          JSON.stringify(moveEvent(serverGame, move.san, request.uci)),
        );
      }
    });
  });

  await page.goto(`${appUrl}/game/53`);
  await page.locator('[data-square="d4"]').click();
  await page.locator('[data-square="e5"]').click();
  await expect(page.getByText("Premove queued")).toBeVisible();
  const opponentCapture = serverGame.move({ from: "f6", to: "e5" });
  expect(opponentCapture.san).toBe("fxe5");
  socket?.send(
    JSON.stringify(moveEvent(serverGame, opponentCapture.san, "f6e5")),
  );
  await expect.poll(() => playedMoves).toEqual(["d4e5"]);
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
