import { expect, type Page, type Route, test } from "@playwright/test";

type Capture = {
  runRequests: Array<Record<string, unknown>>;
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

async function mockBackendApi(page: Page, capture: Capture = { runRequests: [] }) {
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
          }
        ],
        page: 1,
        size: 20,
        total: 1
      });
    }
    if (path === "/api/agents" || path === "/api/models" || path === "/api/providers") {
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
    if (
      path === "/api/sessions/ses_1/todo" ||
      path === "/api/sessions/ses_1/diff" ||
      path === "/api/sessions/ses_1/permissions" ||
      path === "/api/sessions/ses_1/questions"
    ) {
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

    return json(route, {});
  });
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
