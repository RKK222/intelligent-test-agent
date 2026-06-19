import { expect, test, type Page } from "@playwright/test";

test("workbench opens a workspace file with mocked backend api", async ({ page }) => {
  await mockBackendApi(page);

  await page.goto("/");

  await expect(page.getByText("TestAgent IDE")).toBeVisible();
  await expect(page.getByRole("banner").getByText("demo-tests")).toBeVisible();
  await page.getByRole("button", { name: /tests/ }).click();
  await page.getByRole("button", { name: /checkout.spec.ts/ }).click();
  await expect(page.getByText("tests/checkout.spec.ts")).toBeVisible();
  await expect(page.getByRole("button", { name: /保存/ })).toBeVisible();
});

test("phase 11 runtime flow sends attachment parts and handles docks", async ({ page }) => {
  const runRequests: Array<Record<string, unknown>> = [];
  const permissionReplies: Array<Record<string, unknown>> = [];
  const questionReplies: Array<Record<string, unknown>> = [];
  const terminalTickets: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, { runRequests, permissionReplies, questionReplies, terminalTickets });

  await page.goto("/");

  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("analyze checkout");
  await page.locator('input[type="file"]').first().setInputFiles({
    name: "notes.txt",
    mimeType: "text/plain",
    buffer: Buffer.from("checkout failure log")
  });
  await expect(page.getByText("notes.txt")).toBeVisible();
  await page.getByRole("button", { name: "发送" }).click();

  await expect.poll(() => runRequests.length).toBe(1);
  expect(runRequests[0]).toMatchObject({
    sessionId: "ses_1",
    prompt: "analyze checkout",
    parts: expect.arrayContaining([
      { type: "text", text: "analyze checkout" },
      { type: "file", name: "notes.txt", mimeType: "text/plain", content: "checkout failure log" }
    ])
  });

  await expect(page.getByText("Run bash")).toBeVisible();
  await page.getByRole("button", { name: "一次" }).click();
  await expect.poll(() => permissionReplies.length).toBe(1);
  expect(permissionReplies[0]).toEqual({ decision: "once" });

  await expect(page.getByText("Need target env?")).toBeVisible();
  await page.getByPlaceholder("回答").fill("staging");
  await page.getByRole("button", { name: "回复" }).click();
  await expect.poll(() => questionReplies.length).toBe(1);
  expect(questionReplies[0]).toEqual({ answers: ["staging"] });

  await expect(page.getByText("Agent 提出了文件修改")).toBeVisible();
  await page.getByRole("button", { name: "查看 Diff" }).click();
  await expect(page.getByText("+1,2")).toBeVisible();
  await page.getByTitle("引用 hunk").click();
  await expect(page.getByRole("main").getByText("已引用当前 hunk")).toBeVisible();

  await page.getByRole("button", { name: "终端" }).click();
  await page.getByRole("button", { name: "连接终端" }).click();
  await expect.poll(() => terminalTickets.length).toBe(1);
  expect(terminalTickets[0]).toEqual({ workspaceId: "wrk_1234567890abcdef", cols: 120, rows: 32 });
});

async function mockBackendApi(
  page: Page,
  capture: {
    runRequests?: Array<Record<string, unknown>>;
    permissionReplies?: Array<Record<string, unknown>>;
    questionReplies?: Array<Record<string, unknown>>;
    terminalTickets?: Array<Record<string, unknown>>;
  } = {}
) {
  await page.route("**/api/**", async (route) => {
    const url = new URL(route.request().url());
    const method = route.request().method();
    if (method === "OPTIONS") {
      await route.fulfill({ status: 204, headers: corsHeaders() });
      return;
    }
    if (method === "GET" && url.pathname === "/api/workspaces") {
      await route.fulfill(json(workspacePage()));
      return;
    }
    if (method === "GET" && url.pathname.endsWith("/files")) {
      const path = url.searchParams.get("path") ?? "";
      await route.fulfill(json(fileEntries(path)));
      return;
    }
    if (method === "GET" && url.pathname.endsWith("/files/content")) {
      await route.fulfill(json({
        path: "tests/checkout.spec.ts",
        content: "import { test } from '@playwright/test';\n\ntest('checkout', async () => {});\n",
        encoding: "utf-8",
        size: 80,
        readonly: false
      }));
      return;
    }
    if (method === "PUT" && url.pathname.endsWith("/files/content")) {
      await route.fulfill(json(null));
      return;
    }
    if (method === "GET" && /\/api\/workspaces\/[^/]+\/sessions$/.test(url.pathname)) {
      await route.fulfill(json(pageOf([])));
      return;
    }
    if (method === "POST" && url.pathname === "/api/sessions") {
      await route.fulfill(json(session()));
      return;
    }
    if (method === "GET" && url.pathname === "/api/sessions") {
      await route.fulfill(json(pageOf([])));
      return;
    }
    if (method === "GET" && url.pathname === "/api/agents") {
      await route.fulfill(json([{ id: "build", name: "Build" }]));
      return;
    }
    if (method === "GET" && url.pathname === "/api/models") {
      await route.fulfill(json([{ id: "sonnet", providerId: "anthropic", name: "Sonnet" }]));
      return;
    }
    if (method === "GET" && url.pathname === "/api/providers") {
      await route.fulfill(json([{ id: "anthropic", name: "Anthropic", status: "ready" }]));
      return;
    }
    if (method === "GET" && url.pathname === "/api/commands") {
      await route.fulfill(json([{ id: "test", name: "test", description: "Run tests" }]));
      return;
    }
    if (method === "GET" && url.pathname === "/api/mcp/resources") {
      await route.fulfill(json([{ id: "issue-1", name: "Issue 1", uri: "mcp://issue/1", type: "issue" }]));
      return;
    }
    if (method === "GET" && url.pathname === "/api/mcp/tools") {
      await route.fulfill(json(["bash"]));
      return;
    }
    if (method === "GET" && ["/api/lsp/status", "/api/mcp/status", "/api/vcs/status"].includes(url.pathname)) {
      await route.fulfill(json({ status: "ready", branch: "main" }));
      return;
    }
    if (method === "POST" && url.pathname === "/api/runs") {
      capture.runRequests?.push(JSON.parse(route.request().postData() ?? "{}") as Record<string, unknown>);
      await route.fulfill(json({
        runId: "run_1",
        sessionId: "ses_1",
        workspaceId: "wrk_1234567890abcdef",
        status: "RUNNING",
        createdAt: "2026-06-19T00:00:00Z",
        updatedAt: "2026-06-19T00:00:00Z"
      }));
      return;
    }
    if (method === "GET" && url.pathname === "/api/runs/run_1/events") {
      await route.fulfill({
        status: 200,
        headers: { ...corsHeaders(), "Content-Type": "text/event-stream", "Cache-Control": "no-cache" },
        body: sse([
          event(1, "permission.asked", { requestId: "perm_1", sessionId: "ses_1", title: "Run bash", description: "Allow npm test?" }),
          event(2, "question.asked", {
            requestId: "ques_1",
            sessionId: "ses_1",
            questions: [{ id: "q1", text: "Need target env?", kind: "text" }]
          }),
          event(3, "diff.proposed", { files: [diffFile()] }),
          event(4, "run.succeeded", {})
        ])
      });
      return;
    }
    if (method === "GET" && url.pathname === "/api/runs/run_1/diff") {
      await route.fulfill(json({ runId: "run_1", files: [diffFile()] }));
      return;
    }
    if (method === "POST" && url.pathname === "/api/sessions/ses_1/permissions/perm_1/reply") {
      capture.permissionReplies?.push(JSON.parse(route.request().postData() ?? "{}") as Record<string, unknown>);
      await route.fulfill(json({ accepted: true }));
      return;
    }
    if (method === "POST" && url.pathname === "/api/sessions/ses_1/questions/ques_1/reply") {
      capture.questionReplies?.push(JSON.parse(route.request().postData() ?? "{}") as Record<string, unknown>);
      await route.fulfill(json({ accepted: true }));
      return;
    }
    if (method === "POST" && url.pathname === "/api/sessions/ses_1/terminal/tickets") {
      capture.terminalTickets?.push(JSON.parse(route.request().postData() ?? "{}") as Record<string, unknown>);
      await route.fulfill(json({
        ticket: "pty_123",
        expiresAt: "2026-06-19T13:00:00Z",
        webSocketUrl: "/api/sessions/ses_1/terminal/ws?ticket=pty_123"
      }));
      return;
    }
    await route.fulfill(json({}));
  });
}

function json(data: unknown) {
  return {
    contentType: "application/json",
    headers: corsHeaders(),
    body: JSON.stringify({ success: true, traceId: "trace_e2e", data })
  };
}

function corsHeaders() {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "Content-Type, X-Trace-Id, Authorization",
    "Access-Control-Allow-Methods": "GET, POST, PUT, PATCH, DELETE, OPTIONS"
  };
}

function pageOf(items: unknown[]) {
  return { items, page: 0, size: 30, total: items.length };
}

function workspacePage() {
  return pageOf([
    {
      workspaceId: "wrk_1234567890abcdef",
      name: "demo-tests",
      rootPath: "/tmp/demo",
      status: "ACTIVE",
      createdAt: "2026-06-19T00:00:00Z",
      updatedAt: "2026-06-19T00:00:00Z"
    }
  ]);
}

function fileEntries(path: string) {
  return path === "tests"
    ? [
        {
          path: "tests/checkout.spec.ts",
          name: "checkout.spec.ts",
          directory: false,
          size: 120,
          lastModifiedAt: "2026-06-19T00:00:00Z"
        }
      ]
    : [
        { path: "tests", name: "tests", directory: true, size: 0, lastModifiedAt: "2026-06-19T00:00:00Z" },
        { path: "package.json", name: "package.json", directory: false, size: 80, lastModifiedAt: "2026-06-19T00:00:00Z" }
      ];
}

function session() {
  return {
    sessionId: "ses_1",
    workspaceId: "wrk_1234567890abcdef",
    title: "E2E Session",
    status: "ACTIVE",
    createdAt: "2026-06-19T00:00:00Z",
    updatedAt: "2026-06-19T00:00:00Z"
  };
}

function diffFile() {
  return {
    path: "src/App.tsx",
    patch: "@@ -1 +1,2 @@ render\n-old();\n+newFlow();\n+assertReady();",
    additions: 2,
    deletions: 1,
    status: "modified"
  };
}

function event(seq: number, type: string, payload: Record<string, unknown>) {
  return {
    eventId: `evt_${seq}`,
    runId: "run_1",
    seq,
    type,
    traceId: "trace_e2e",
    occurredAt: "2026-06-19T00:00:00Z",
    payload
  };
}

function sse(events: ReturnType<typeof event>[]) {
  return events.map((item) => `event: ${item.type}\ndata: ${JSON.stringify(item)}\n\n`).join("");
}
