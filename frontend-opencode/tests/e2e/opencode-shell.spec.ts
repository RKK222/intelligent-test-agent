import { expect, type Page, type Route, test } from "@playwright/test";

type Capture = {
  runRequests: Array<Record<string, unknown>>;
  abortRequests?: Array<Record<string, unknown>>;
  providerAuthRequests?: Array<Record<string, unknown>>;
  shareRequests?: Array<Record<string, unknown>>;
};

type MockOptions = {
  sessionDiff?: Array<Record<string, unknown>>;
};

test.beforeEach(async ({ page }) => {
  await installFakeEventSource(page);
});

test("renders desktop opencode workspace shell", async ({ page }) => {
  await mockBackendApi(page);

  await page.goto("/");

  await expect(page.getByRole("banner")).toContainText("opencode");
  await expect(page.getByRole("button", { name: /new session/i })).toBeVisible();
  await expect(page.getByLabel("Sessions").getByText("/repo")).toBeVisible();
});

test("opens and filters the opencode command palette", async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== "desktop", "Global command palette keyboard coverage is desktop-specific.");
  await mockBackendApi(page);

  await page.goto("/");
  await page.keyboard.press("Control+Shift+P");

  const palette = page.getByRole("dialog", { name: "Command palette" });
  await expect(palette).toBeVisible();
  await page.getByLabel("Search commands").fill("review");
  await expect(page.getByRole("option", { name: "/review Review staged changes" })).toHaveAttribute("aria-selected", "true");
  await expect(page.getByRole("option", { name: "/compact Summarize the session" })).toHaveCount(0);
  await page.keyboard.press("Escape");
  await expect(palette).toHaveCount(0);
});

test("manages provider auth from the settings dialog", async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== "desktop", "Provider settings are covered on the desktop dialog.");
  const capture: Capture = { runRequests: [], providerAuthRequests: [] };
  await mockBackendApi(page, capture);

  await page.goto("/");
  await page.getByRole("button", { name: "Settings" }).click();

  const dialog = page.getByRole("dialog", { name: "Settings" });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByText("Anthropic", { exact: true })).toBeVisible();

  await dialog.getByLabel("Anthropic API key").fill("sk-test");
  await dialog.getByRole("button", { name: "Save Anthropic key" }).click();
  await expect.poll(() => capture.providerAuthRequests?.some((entry) => entry.action === "set-api-key")).toBe(true);

  await dialog.getByRole("button", { name: "Authorize Anthropic OAuth" }).click();
  await expect(dialog.getByRole("link", { name: "Open Anthropic OAuth URL" })).toHaveAttribute(
    "href",
    "https://auth.example/anthropic"
  );
  await dialog.getByLabel("Anthropic OAuth code").fill("code-123");
  await dialog.getByRole("button", { name: "Complete Anthropic OAuth" }).click();
  await expect.poll(() => capture.providerAuthRequests?.some((entry) => entry.action === "oauth-callback")).toBe(true);

  await dialog.getByRole("button", { name: "Remove Anthropic auth" }).click();
  await expect.poll(() => capture.providerAuthRequests?.some((entry) => entry.action === "remove")).toBe(true);
});

test("opens a session, sends a prompt, and renders streamed RunEvent output", async ({ page }) => {
  const capture: Capture = { runRequests: [] };
  await mockBackendApi(page, capture);

  await page.goto("/w/wrk_1/session/ses_1");
  await expect(page.getByRole("heading", { name: "Demo session" })).toBeVisible();
  await expect(page.getByText("Initial prompt")).toBeVisible();

  await page.getByPlaceholder("Ask opencode to inspect, edit, test, or explain this workspace...").fill("Run unit tests");
  await page.getByRole("button", { name: "Send" }).click();

  await expect.poll(() => capture.runRequests.length).toBe(1);
  await expect.poll(() => eventSourceCount(page)).toBeGreaterThan(0);
  expect(capture.runRequests[0]).toMatchObject({
    sessionId: "ses_1",
    prompt: "Run unit tests",
    parts: [{ type: "text", text: "Run unit tests" }]
  });

  await emitRunEvent(page, {
    eventId: "evt_msg",
    runId: "run_1",
    seq: 1,
    type: "message.updated",
    traceId: "trace_e2e",
    occurredAt: "2026-06-20T00:00:02Z",
    payload: {
      message: {
        messageId: "msg_assistant",
        sessionId: "ses_1",
        role: "assistant",
        content: "",
        updatedAt: "2026-06-20T00:00:02Z"
      }
    }
  });
  await emitRunEvent(page, {
    eventId: "evt_delta",
    runId: "run_1",
    seq: 2,
    type: "message.part.delta",
    traceId: "trace_e2e",
    occurredAt: "2026-06-20T00:00:03Z",
    payload: {
      messageId: "msg_assistant",
      part: {
        partId: "part_text",
        type: "text",
        text: "All tests passed",
        status: "completed"
      }
    }
  });

  await expect(page.getByText("All tests passed")).toBeVisible();
});

test("publishes and unpublishes a session share link", async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== "desktop", "Session share popover is covered on the desktop toolbar.");
  const capture: Capture = { runRequests: [], shareRequests: [] };
  await mockBackendApi(page, capture);

  await page.goto("/w/wrk_1/session/ses_1");
  await expect(page.getByRole("heading", { name: "Demo session" })).toBeVisible();

  await page.getByRole("button", { name: "Share session" }).click();
  const share = page.getByRole("dialog", { name: "Session share" });
  await expect(share).toBeVisible();
  await share.getByRole("button", { name: "Publish session" }).click();

  await expect.poll(() => capture.shareRequests?.some((entry) => entry.action === "publish")).toBe(true);
  await expect(share.getByLabel("Shared session URL")).toHaveValue("https://share.example/ses_1");
  await expect(share.getByRole("link", { name: "View shared session" })).toHaveAttribute("href", "https://share.example/ses_1");

  await share.getByRole("button", { name: "Unpublish session" }).click();
  await expect.poll(() => capture.shareRequests?.some((entry) => entry.action === "unpublish")).toBe(true);
  await expect(share.getByRole("button", { name: "Publish session" })).toBeVisible();
});

test("aborts an active session run from the toolbar", async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== "desktop", "Toolbar abort is covered on the desktop session controls.");
  const capture: Capture = { runRequests: [], abortRequests: [] };
  await mockBackendApi(page, capture);

  await page.goto("/w/wrk_1/session/ses_1");
  await expect(page.getByRole("heading", { name: "Demo session" })).toBeVisible();

  const abort = page.getByRole("button", { name: "Abort session" });
  await expect(abort).toBeDisabled();
  await page.getByPlaceholder("Ask opencode to inspect, edit, test, or explain this workspace...").fill("Keep running until stopped");
  await page.getByRole("button", { name: "Send" }).click();
  await expect(abort).toBeEnabled();

  await abort.click();

  await expect.poll(() => capture.abortRequests?.some((entry) => entry.sessionId === "ses_1")).toBe(true);
  await expect(abort).toBeDisabled();
});

test("switches sessions from the session sidebar", async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== "desktop", "Session sidebar switching is covered on the desktop rail.");
  await mockBackendApi(page);

  await page.goto("/w/wrk_1/session/ses_1");
  await expect(page.getByRole("heading", { name: "Demo session" })).toBeVisible();

  await page.getByRole("button", { name: /Second session/ }).click();

  await expect(page).toHaveURL(/\/w\/wrk_1\/session\/ses_2$/);
  await expect(page.getByRole("heading", { name: "Second session" })).toBeVisible();
  await expect(page.getByText("Follow-up prompt")).toBeVisible();
});

test("filters the session sidebar with global search", async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== "desktop", "Session rail filtering is covered on the desktop shell.");
  await mockBackendApi(page);

  await page.goto("/w/wrk_1/session/ses_1");
  await expect(page.getByRole("heading", { name: "Demo session" })).toBeVisible();

  await page.getByPlaceholder("Search sessions, files, commands").fill("Second");

  const rail = page.getByLabel("Session navigation");
  await expect(rail.getByRole("button", { name: /Second session/ })).toBeVisible();
  await expect(rail.getByRole("button", { name: /Demo session/ })).toHaveCount(0);
});

test("opens the first workspace session when session id is omitted", async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== "desktop", "Optional session route fallback is covered on the desktop shell.");
  await mockBackendApi(page);

  await page.goto("/w/wrk_1/session");

  await expect(page).toHaveURL(/\/w\/wrk_1\/session\/ses_1$/);
  await expect(page.getByRole("heading", { name: "Demo session" })).toBeVisible();
  await expect(page.getByText("Initial prompt")).toBeVisible();
});

test("opens the session side panel as a mobile drawer", async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== "mobile", "Mobile drawer behavior is covered on the mobile viewport.");
  await mockBackendApi(page);

  await page.goto("/w/wrk_1/session/ses_1");
  await expect(page.getByRole("heading", { name: "Demo session" })).toBeVisible();
  await expect(page.getByRole("complementary", { name: "Session side panel" })).toBeHidden();

  await page.getByRole("button", { name: "Panel" }).click();

  await expect(page.getByRole("complementary", { name: "Session side panel" })).toBeVisible();
  await page.getByRole("tab", { name: "Status" }).click();
  await expect(page.getByText("Runtime requests")).toBeVisible();

  await page.getByRole("button", { name: "Close session panel" }).click();
  await expect(page.getByRole("complementary", { name: "Session side panel" })).toBeHidden();
});

test("loads the Monaco diff review editor for session diffs", async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== "desktop", "Monaco review loading is covered on the desktop review layout.");
  await mockBackendApi(page, { runRequests: [] }, { sessionDiff: diffFixture() });

  await page.goto("/w/wrk_1/session/ses_1");

  const editor = page.getByRole("region", { name: "Monaco diff editor" });
  await expect(editor).toContainText("src/App.vue");
  await expect(editor).toContainText("Unified editor", { timeout: 10_000 });
});

async function mockBackendApi(page: Page, capture: Capture = { runRequests: [] }, options: MockOptions = {}) {
  await page.route("**/*", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    // 只接管平台 API，避免把 Vite 的 /src/api/*.ts 模块误当成后端 JSON 响应。
    if (!path.startsWith("/api/")) {
      return route.fallback();
    }

    if (path === "/api/workspaces") {
      return json(route, {
        items: [
          {
            workspaceId: "wrk_1",
            name: "opencode",
            rootPath: "/repo",
            status: "ACTIVE",
            createdAt: "2026-06-20T00:00:00Z",
            updatedAt: "2026-06-20T00:00:00Z"
          }
        ],
        page: 1,
        size: 20,
        total: 1
      });
    }
    if (path === "/api/sessions" && request.method() === "GET") {
      return json(route, {
        items: [
          {
            sessionId: "ses_1",
            workspaceId: "wrk_1",
            title: "Demo session",
            status: "IDLE",
            createdAt: "2026-06-20T00:00:00Z",
            updatedAt: "2026-06-20T00:00:00Z"
          },
          {
            sessionId: "ses_2",
            workspaceId: "wrk_1",
            title: "Second session",
            status: "IDLE",
            createdAt: "2026-06-20T00:00:03Z",
            updatedAt: "2026-06-20T00:00:03Z"
          }
        ],
        page: 1,
        size: 20,
        total: 2
      });
    }
    if (path === "/api/commands") {
      return json(route, [
        { commandId: "compact", name: "compact", description: "Summarize the session" },
        { commandId: "review", name: "review", description: "Review staged changes", arguments: "--quick" }
      ]);
    }
    if (path === "/api/providers") {
      return json(route, [{ providerId: "anthropic", name: "Anthropic", status: "available" }]);
    }
    if (path === "/api/provider/auth") {
      return json(route, {
        anthropic: {
          methods: [{ type: "oauth", label: "OAuth" }]
        }
      });
    }
    if (path === "/api/auth/anthropic" && request.method() === "PUT") {
      capture.providerAuthRequests?.push({
        action: "set-api-key",
        providerId: "anthropic",
        body: JSON.parse(request.postData() ?? "{}") as Record<string, unknown>
      });
      return json(route, { providerId: "anthropic", status: "connected" });
    }
    if (path === "/api/auth/anthropic" && request.method() === "DELETE") {
      capture.providerAuthRequests?.push({ action: "remove", providerId: "anthropic" });
      return json(route, { providerId: "anthropic", status: "missing" });
    }
    if (path === "/api/provider/anthropic/oauth/authorize" && request.method() === "POST") {
      capture.providerAuthRequests?.push({
        action: "oauth-authorize",
        providerId: "anthropic",
        body: JSON.parse(request.postData() ?? "{}") as Record<string, unknown>
      });
      return json(route, { url: "https://auth.example/anthropic", method: "code", instructions: "Paste the browser code" });
    }
    if (path === "/api/provider/anthropic/oauth/callback" && request.method() === "POST") {
      capture.providerAuthRequests?.push({
        action: "oauth-callback",
        providerId: "anthropic",
        body: JSON.parse(request.postData() ?? "{}") as Record<string, unknown>
      });
      return json(route, { providerId: "anthropic", status: "connected" });
    }
    if (path === "/api/agents" || path === "/api/models") {
      return json(route, []);
    }
    if (path === "/api/sessions/ses_1") {
      return json(route, {
        sessionId: "ses_1",
        workspaceId: "wrk_1",
        title: "Demo session",
        status: "IDLE",
        createdAt: "2026-06-20T00:00:00Z",
        updatedAt: "2026-06-20T00:00:00Z"
      });
    }
    if (path === "/api/sessions/ses_2") {
      return json(route, {
        sessionId: "ses_2",
        workspaceId: "wrk_1",
        title: "Second session",
        status: "IDLE",
        createdAt: "2026-06-20T00:00:03Z",
        updatedAt: "2026-06-20T00:00:03Z"
      });
    }
    if (path === "/api/sessions/ses_1/messages") {
      return json(route, {
        items: [
          {
            messageId: "msg_user",
            sessionId: "ses_1",
            role: "USER",
            content: "Initial prompt",
            createdAt: "2026-06-20T00:00:01Z"
          }
        ],
        page: 1,
        size: 200,
        total: 1
      });
    }
    if (path === "/api/sessions/ses_2/messages") {
      return json(route, {
        items: [
          {
            messageId: "msg_user_2",
            sessionId: "ses_2",
            role: "USER",
            content: "Follow-up prompt",
            createdAt: "2026-06-20T00:00:04Z"
          }
        ],
        page: 1,
        size: 200,
        total: 1
      });
    }
    if (path === "/api/sessions/ses_1/diff") {
      return json(route, options.sessionDiff ?? []);
    }
    if (path === "/api/sessions/ses_2/diff") {
      return json(route, []);
    }
    if (path === "/api/sessions/ses_1/share" && request.method() === "POST") {
      capture.shareRequests?.push({ action: "publish", sessionId: "ses_1" });
      return json(route, { share: { url: "https://share.example/ses_1" } });
    }
    if (path === "/api/sessions/ses_1/share" && request.method() === "DELETE") {
      capture.shareRequests?.push({ action: "unpublish", sessionId: "ses_1" });
      return json(route, true);
    }
    if (path === "/api/sessions/ses_1/todo" || path === "/api/sessions/ses_1/permissions" || path === "/api/sessions/ses_1/questions") {
      return json(route, []);
    }
    if (path === "/api/sessions/ses_2/todo" || path === "/api/sessions/ses_2/permissions" || path === "/api/sessions/ses_2/questions") {
      return json(route, []);
    }
    if (path === "/api/runs" && request.method() === "POST") {
      capture.runRequests.push(JSON.parse(request.postData() ?? "{}") as Record<string, unknown>);
      return json(route, {
        runId: "run_1",
        sessionId: "ses_1",
        workspaceId: "wrk_1",
        status: "RUNNING",
        createdAt: "2026-06-20T00:00:02Z",
        updatedAt: "2026-06-20T00:00:02Z"
      });
    }
    if (path === "/api/sessions/ses_1/abort" && request.method() === "POST") {
      capture.abortRequests?.push({ sessionId: "ses_1" });
      return json(route, { cancelled: true });
    }

    return json(route, {});
  });
}

function diffFixture() {
  return [
    {
      path: "src/App.vue",
      status: "modified",
      additions: 2,
      deletions: 1,
      patch: ["@@ -1,3 +1,4 @@", " import { ref } from 'vue'", "-const title = 'old'", "+const title = 'new'", "+const mode = ref('review')"].join(
        "\n"
      )
    }
  ];
}

async function json(route: Route, data: unknown) {
  await route.fulfill({
    contentType: "application/json",
    body: JSON.stringify({ success: true, traceId: "trace_e2e", data })
  });
}

async function installFakeEventSource(page: Page) {
  await page.addInitScript(() => {
    type Listener = (event: MessageEvent<string>) => void;
    const sources: Array<{
      emit: (event: Record<string, unknown>) => void;
    }> = [];

    // 在 mock E2E 中复刻平台 RunEvent SSE，验证 reducer 与 timeline 渲染闭环。
    class FakeEventSource {
      onopen: ((event: Event) => void) | null = null;
      onerror: ((event: Event) => void) | null = null;
      private listeners = new Map<string, Set<Listener>>();

      constructor(readonly url: string) {
        sources.push(this);
        setTimeout(() => this.onopen?.(new Event("open")), 0);
      }

      addEventListener(type: string, listener: EventListener) {
        const set = this.listeners.get(type) ?? new Set<Listener>();
        set.add(listener as Listener);
        this.listeners.set(type, set);
      }

      removeEventListener(type: string, listener: EventListener) {
        this.listeners.get(type)?.delete(listener as Listener);
      }

      close() {
        this.listeners.clear();
      }

      emit(event: Record<string, unknown>) {
        const message = new MessageEvent(String(event.type ?? "message"), { data: JSON.stringify(event) });
        this.listeners.get(String(event.type))?.forEach((listener) => listener(message));
        this.listeners.get("message")?.forEach((listener) => listener(message));
      }
    }

    (window as unknown as { EventSource: typeof FakeEventSource }).EventSource = FakeEventSource;
    (window as unknown as { __eventSourceCount: () => number }).__eventSourceCount = () => sources.length;
    (window as unknown as { __emitRunEvent: (event: Record<string, unknown>) => void }).__emitRunEvent = (event) => {
      sources.forEach((source) => source.emit(event));
    };
  });
}

async function eventSourceCount(page: Page) {
  return page.evaluate(() => (window as unknown as { __eventSourceCount?: () => number }).__eventSourceCount?.() ?? 0);
}

async function emitRunEvent(page: Page, event: Record<string, unknown>) {
  await page.evaluate((payload) => {
    (window as unknown as { __emitRunEvent: (event: Record<string, unknown>) => void }).__emitRunEvent(payload);
  }, event);
}
