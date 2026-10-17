import { expect, test, type APIRequestContext, type Page } from "@playwright/test";

const firstUrl = process.env.E2E_FIRST_URL ?? "http://localhost:5173";
const secondUrl = process.env.E2E_SECOND_URL ?? "http://localhost:5174";
const apiUrl = process.env.E2E_API_URL ?? "http://localhost:8080";
const mailpitUrl = process.env.E2E_MAILPIT_URL ?? "http://localhost:8025";
const customFen = "k7/8/8/8/8/8/4K3/7R w - - 0 1";

test("custom position challenge reaches another backend and can be accepted", async ({
  browser,
  request,
}) => {
  const suffix = Date.now().toString(36);
  const firstUsername = `flow_${suffix}_a`;
  const secondUsername = `flow_${suffix}_b`;
  const password = process.env.E2E_PASSWORD ?? `Chess-flow-${crypto.randomUUID()}-9!`;
  const testIp = `198.51.100.${(Date.now() % 200) + 1}`;
  const firstEmail = `flow-${suffix}-a@example.test`;
  const secondEmail = `flow-${suffix}-b@example.test`;
  await registerAndVerify(request, firstUsername, firstEmail, password, testIp);
  await registerAndVerify(request, secondUsername, secondEmail, password, testIp);
  const firstToken = await login(request, firstUsername, password, testIp);
  const secondToken = await login(request, secondUsername, password, testIp);
  await ensureFriends(request, firstToken, secondToken, firstUsername, secondUsername);

  const firstContext = await browser.newContext({ baseURL: firstUrl });
  const secondContext = await browser.newContext({ baseURL: secondUrl });
  const firstPage = await firstContext.newPage();
  const secondPage = await secondContext.newPage();
  try {
    await signIn(firstPage, firstUrl, firstUsername, password, testIp);
    await signIn(secondPage, secondUrl, secondUsername, password, testIp);
    await secondPage.goto(`${secondUrl}/play`);

    await firstPage.goto(`${firstUrl}/position-setup`);
    await firstPage.getByLabel("FEN import or export").fill(customFen);
    await firstPage.getByRole("button", { name: "Import FEN" }).click();
    await firstPage.getByRole("button", { name: "Use this position" }).click();
    await expect(firstPage).toHaveURL(/\/play$/);
    await firstPage.getByLabel("Friend").click();
    await firstPage.getByRole("option", { name: new RegExp(secondUsername!, "i") }).click();
    await firstPage.getByLabel("Play as").click();
    await firstPage.getByRole("option", { name: "White" }).click();
    await firstPage.getByRole("button", { name: "Send challenge" }).click();

    const invitation = secondPage.locator(".challenge-row").filter({ hasText: firstUsername! });
    await expect(invitation).toBeVisible();
    await invitation.getByRole("button", { name: "Accept" }).click();
    await expect(firstPage).toHaveURL(/\/game\/\d+/);
    await expect(secondPage).toHaveURL(/\/game\/\d+/);

    const firstChallengeId = Number(firstPage.url().split("/").at(-1));
    const detail = await request.get(`${apiUrl}/api/challenges/${firstChallengeId}`, {
      headers: { Authorization: `Bearer ${firstToken}` },
    });
    expect(detail.ok()).toBeTruthy();
    const response = await detail.json();
    const challenge = response.data ?? response;
    expect(challenge.initialFen).toBe(customFen);
    expect(challenge.challengerSide).toBe("WHITE");
  } finally {
    await firstContext.close();
    await secondContext.close();
  }
});

async function registerAndVerify(
  request: APIRequestContext,
  username: string,
  email: string,
  password: string,
  testIp: string,
) {
  const registration = await request.post(`${apiUrl}/api/auth/register`, {
    data: { username, email, displayName: username, password },
    headers: { "X-Forwarded-For": testIp },
  });
  expect(registration.status()).toBe(202);
  let token = "";
  const deadline = Date.now() + 20_000;
  while (!token && Date.now() < deadline) {
    const messagesResponse = await request.get(`${mailpitUrl}/api/v1/messages?limit=50`);
    expect(messagesResponse.ok()).toBeTruthy();
    const messagesJson = await messagesResponse.json();
    const messages = messagesJson.messages ?? [];
    const message = messages.find((item: { To?: Array<{ Address: string }>; ID: string }) =>
      item.To?.some((recipient) => recipient.Address === email),
    );
    if (message) {
      const detailResponse = await request.get(`${mailpitUrl}/api/v1/message/${message.ID}`);
      const detail = await detailResponse.json();
      const content = `${detail.Text ?? ""} ${detail.HTML ?? ""}`;
      token = content.match(/verify-email\?token=([A-Za-z0-9_-]+)/)?.[1] ?? "";
    }
    if (!token) await new Promise((resolve) => setTimeout(resolve, 500));
  }
  expect(token, `Verification email was not received for ${username}`).not.toBe("");
  const verified = await request.post(`${apiUrl}/api/auth/verify-email`, { data: { token } });
  expect(verified.ok()).toBeTruthy();
}

async function login(request: APIRequestContext, username: string, password: string, testIp: string) {
  const response = await request.post(`${apiUrl}/api/auth/login`, {
    data: { identifier: username, password },
    headers: { "X-Forwarded-For": testIp },
  });
  expect(response.ok(), `Login failed for ${username}`).toBeTruthy();
  const json = await response.json();
  return (json.data ?? json).accessToken as string;
}

async function ensureFriends(
  request: APIRequestContext,
  firstToken: string,
  secondToken: string,
  firstUsername: string,
  secondUsername: string,
) {
  const existing = await request.get(`${apiUrl}/api/friends`, {
    headers: { Authorization: `Bearer ${firstToken}` },
  });
  expect(existing.ok()).toBeTruthy();
  const friendJson = await existing.json();
  const friends = friendJson.data ?? friendJson;
  if (friends.some((friend: { username: string }) => friend.username === secondUsername)) return;

  const requestResponse = await request.post(`${apiUrl}/api/friends/request/${encodeURIComponent(secondUsername)}`, {
    headers: { Authorization: `Bearer ${firstToken}` },
  });
  if (!requestResponse.ok()) {
    const outgoingResponse = await request.get(`${apiUrl}/api/friends/requests/outgoing`, {
      headers: { Authorization: `Bearer ${firstToken}` },
    });
    const outgoingJson = await outgoingResponse.json();
    const outgoing = outgoingJson.data ?? outgoingJson;
    if ((Array.isArray(outgoing) ? outgoing : outgoing.items).some(
      (item: { username: string; status: string }) => item.username === secondUsername && item.status === "PENDING",
    )) throw new Error("A pending friend request already exists; accept it before running this browser flow");
  }
  const incomingResponse = await request.get(`${apiUrl}/api/friends/requests/incoming`, {
    headers: { Authorization: `Bearer ${secondToken}` },
  });
  expect(incomingResponse.ok()).toBeTruthy();
  const json = await incomingResponse.json();
  const requests = json.data ?? json;
  const incoming = Array.isArray(requests) ? requests : requests.items;
  const match = incoming.find((item: { username: string }) => item.username === firstUsername);
  if (match) {
    const acceptResponse = await request.post(`${apiUrl}/api/friends/accept/${match.requestId}`, {
      headers: { Authorization: `Bearer ${secondToken}` },
    });
    expect(acceptResponse.ok()).toBeTruthy();
  }
}

async function signIn(page: Page, baseUrl: string, username: string, password: string, testIp: string) {
  await page.setExtraHTTPHeaders({ "X-Forwarded-For": testIp });
  await page.goto(`${baseUrl}/login`);
  await page.getByLabel("Username or email").fill(username);
  await page.getByLabel("Password").fill(password);
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page).toHaveURL(/\/(play)?$/);
  await expect(page.getByRole("button", { name: "Send challenge" })).toBeVisible({ timeout: 20_000 });
}
