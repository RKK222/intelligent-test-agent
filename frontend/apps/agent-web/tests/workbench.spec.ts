import { expect, test, type Page } from "@playwright/test";

test("workbench opens a workspace file with mocked backend api", async ({ page }) => {
  await mockBackendApi(page);

  await gotoWorkbench(page);

  await expect(page.getByText("TestAgent IDE")).toBeVisible();
  await expect(page.getByRole("button", { name: "打开运行与终端" })).toBeVisible();
  await page.getByRole("button", { name: /tests/ }).click();
  await page.getByRole("button", { name: /checkout.spec.ts/ }).click();
  await expect(page.getByText("tests/checkout.spec.ts", { exact: true }).first()).toBeVisible();
  await expect(page.getByRole("button", { name: /保存/ })).toBeVisible();
});

test("settings dialog manages application context and SSH key metadata", async ({ page }) => {
  await mockBackendApi(page);

  await gotoWorkbench(page);

  await page.getByRole("button", { name: "打开设置" }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog).toBeVisible();
  await expect(page.getByText("应用人员管理")).toBeVisible();
  await expect(page.getByLabel("应用选择")).toHaveValue("app_gcms");

  await page.getByRole("button", { name: "个人设置" }).click();
  await page.getByPlaceholder("SSH key 名称").fill("work");
  await page.getByPlaceholder("-----BEGIN OPENSSH PRIVATE KEY-----").fill("-----BEGIN OPENSSH PRIVATE KEY-----\nsecret\n-----END OPENSSH PRIVATE KEY-----");
  await page.getByRole("button", { name: "添加 SSH key" }).click();

  await expect(dialog.getByText("SHA256:abc")).toBeVisible();
  await expect(dialog.getByText("secret")).toHaveCount(0);
  await expect(dialog.locator("textarea")).toHaveCount(0);
});

test("workspace picker creates selected directory and loads its file tree", async ({ page, isMobile }) => {
  test.skip(isMobile, "mobile workspace picker layout is not part of this mock E2E");
  const workspaceCreates: Array<Record<string, unknown>> = [];
  const fileRequests: Array<{ workspaceId: string; path: string }> = [];
  await mockBackendApi(page, { workspaceCreates, fileRequests });

  await gotoWorkbench(page);

  await page.getByRole("button", { name: "选择工作区目录" }).click();
  await expect(page.getByRole("dialog", { name: "选择工作区目录" })).toBeVisible();
  await page.getByRole("button", { name: /project-a/ }).click();
  await page.getByRole("button", { name: "选择此目录" }).click();

  await expect.poll(() => workspaceCreates.length).toBe(1);
  expect(workspaceCreates[0]).toEqual({ name: "project-a", rootPath: "/Users/huang/workspace/project-a" });
  await expect(page.getByRole("button", { name: /src/ })).toBeVisible();
  expect(fileRequests).toContainEqual({ workspaceId: "wrk_1234567890abcdef", path: "" });
});

test("workspace picker switches to an existing workspace without recreating it", async ({ page, isMobile }) => {
  test.skip(isMobile, "mobile workspace picker layout is not part of this mock E2E");
  const workspaceCreates: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, { workspaceCreates });

  await gotoWorkbench(page);

  await page.getByRole("button", { name: "选择工作区目录" }).click();
  await page.getByRole("button", { name: /demo-tests/ }).click();
  await page.getByRole("button", { name: "选择此目录" }).click();

  await expect(page.getByRole("button", { name: /tests/ })).toBeVisible();
  expect(workspaceCreates).toEqual([]);
});

test("model picker groups models by provider and updates run model", async ({ page }) => {
  const runRequests: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, { runRequests });

  await gotoWorkbench(page);

  await expect(page.getByRole("button", { name: "选择模型" })).toContainText("Sonnet");
  await page.getByRole("button", { name: "选择模型" }).click();
  await expect(page.getByRole("dialog", { name: "模型选择" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Anthropic" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Volcengine Ark" })).toBeVisible();
  await page.getByPlaceholder("搜索模型").fill("glm");
  await expect(page.getByRole("option", { name: /GLM-5.2/ })).toBeVisible();
  await page.getByRole("option", { name: /GLM-5.2/ }).click();
  await expect(page.getByRole("button", { name: "选择模型" })).toContainText("GLM-5.2");

  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("use selected model");
  await page.getByRole("button", { name: "发送" }).click();

  await expect.poll(() => runRequests.length).toBe(1);
  expect(runRequests[0]).toMatchObject({
    prompt: "use selected model",
    model: "volcengine/glm-5.2"
  });
});

test("phase 11 runtime flow sends attachment parts and handles docks", async ({ page }) => {
  const runRequests: Array<Record<string, unknown>> = [];
  const permissionReplies: Array<Record<string, unknown>> = [];
  const questionReplies: Array<Record<string, unknown>> = [];
  const terminalTickets: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, { runRequests, permissionReplies, questionReplies, terminalTickets });

  await gotoWorkbench(page);

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
  expect(questionReplies[0]).toEqual({ answers: [["staging"]] });

  await expect(page.getByText("Agent 提出了文件修改")).toBeVisible();
  await page.getByRole("button", { name: "查看 Diff" }).click();
  await expect(page.getByText("+1,2")).toBeVisible();
  await page.getByTitle("引用 hunk").click();
  await expect(page.getByRole("main").getByText("已引用当前 hunk")).toBeVisible();

  await expect(page.getByRole("button", { name: "终端", exact: true })).toBeHidden();
  await page.getByRole("button", { name: "打开运行与终端" }).click();
  const bottomDrawer = page.getByRole("region", { name: "运行与终端" });
  await expect(bottomDrawer).toBeVisible();
  await expect.poll(async () => (await bottomDrawer.boundingBox())?.y ?? Number.POSITIVE_INFINITY).toBeLessThan(766);
  await page.getByRole("button", { name: "终端", exact: true }).click();
  await page.getByRole("button", { name: "连接终端" }).click();
  await expect.poll(() => terminalTickets.length).toBe(1);
  expect(terminalTickets[0]).toEqual({ workspaceId: "wrk_1234567890abcdef", cols: 120, rows: 32 });
});

test("live tracking opens changed file and shows line counts before run finishes", async ({ page }) => {
  await mockBackendApi(page, {
    runEvents: [
      event(1, "message.part.updated", {
        messageID: "msg_1",
        part: {
          id: "part_write",
          messageID: "msg_1",
          type: "tool",
          tool: "write",
          state: {
            status: "completed",
            input: { filePath: "/Users/huang/workspace/demo-tests/tests/checkout.spec.ts" },
            metadata: { filepath: "/Users/huang/workspace/demo-tests/tests/checkout.spec.ts" }
          }
        }
      }),
      event(2, "diff.proposed", {
        source: "tool",
        tool: "write",
        messageID: "msg_1",
        partID: "part_write",
        files: [
          {
            path: "/Users/huang/workspace/demo-tests/tests/checkout.spec.ts",
            patch: "@@ -1 +1,3 @@",
            additions: 3,
            deletions: 1,
            status: "modified"
          }
        ]
      })
    ],
    fileContents: {
      "tests/checkout.spec.ts": "import { test } from '@playwright/test';\n\n// live tracking content\n"
    }
  });

  await gotoWorkbench(page);

  const liveButton = page.getByRole("button", { name: "实时" });
  await liveButton.click();
  await expect(liveButton).toHaveAttribute("aria-pressed", "true");
  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("change checkout");
  await page.getByRole("button", { name: "发送" }).click();

  await expect(page.getByRole("button", { name: /checkout\.spec\.ts.*\+3.*-1/ })).toBeVisible();
});

async function mockBackendApi(
  page: Page,
  capture: {
    runRequests?: Array<Record<string, unknown>>;
    permissionReplies?: Array<Record<string, unknown>>;
    questionReplies?: Array<Record<string, unknown>>;
    terminalTickets?: Array<Record<string, unknown>>;
    workspaceCreates?: Array<Record<string, unknown>>;
    fileRequests?: Array<{ workspaceId: string; path: string }>;
    runEvents?: Array<ReturnType<typeof event>>;
    fileContents?: Record<string, string>;
  } = {}
) {
  await page.addInitScript(() => {
    localStorage.setItem("test-agent.auth.token", "test-token");
  });
  // E2E 不依赖外部字体，避免 Google Fonts 网络波动阻塞 domcontentloaded。
  await page.route("https://fonts.googleapis.com/**", async (route) => {
    await route.fulfill({ status: 200, contentType: "text/css", body: "" });
  });
  await page.route("https://fonts.gstatic.com/**", async (route) => {
    await route.fulfill({ status: 200, body: "" });
  });
  const workspaceItems = [workspace()];
  let sshKeys: Array<Record<string, unknown>> = [];
  await page.route("**/api/**", async (route) => {
    const url = new URL(route.request().url());
    const method = route.request().method();
    if (method === "OPTIONS") {
      await route.fulfill({ status: 204, headers: corsHeaders() });
      return;
    }
    if (method === "GET" && url.pathname === "/api/auth/me") {
      await route.fulfill(json({
        userId: "usr_admin",
        username: "admin",
        unifiedAuthId: "admin",
        roles: ["APP_ADMIN"]
      }));
      return;
    }
    if (url.pathname.startsWith("/api/internal/platform/configuration-management")) {
      if (method === "GET" && url.pathname === "/api/internal/platform/configuration-management/applications") {
        await route.fulfill(json([{ appId: "app_gcms", appName: "F-GCMS", enabled: true }]));
        return;
      }
      if (method === "GET" && url.pathname === "/api/internal/platform/configuration-management/applications/app_gcms/members") {
        await route.fulfill(json([]));
        return;
      }
      if (method === "GET" && url.pathname === "/api/internal/platform/configuration-management/repositories") {
        await route.fulfill(json(pageOf([])));
        return;
      }
      if (method === "GET" && url.pathname === "/api/internal/platform/configuration-management/applications/app_gcms/repositories") {
        await route.fulfill(json([]));
        return;
      }
      if (method === "GET" && url.pathname === "/api/internal/platform/configuration-management/applications/app_gcms/workspaces") {
        await route.fulfill(json([]));
        return;
      }
      if (method === "GET" && url.pathname === "/api/internal/platform/configuration-management/personal/ssh-keys") {
        await route.fulfill(json(sshKeys));
        return;
      }
      if (method === "POST" && url.pathname === "/api/internal/platform/configuration-management/personal/ssh-keys") {
        sshKeys = [{ sshKeyId: "ssh_1", name: "work", fingerprint: "SHA256:abc", createdAt: "2026-06-23T00:00:00Z" }];
        await route.fulfill(json(sshKeys[0]));
        return;
      }
      if (method === "DELETE" && url.pathname.startsWith("/api/internal/platform/configuration-management/personal/ssh-keys/")) {
        sshKeys = [];
        await route.fulfill(json(null));
        return;
      }
    }
    if (method === "GET" && url.pathname === "/api/workspaces") {
      await route.fulfill(json(pageOf(workspaceItems)));
      return;
    }
    if (method === "POST" && url.pathname === "/api/workspaces") {
      const payload = JSON.parse(route.request().postData() ?? "{}") as { name: string; rootPath: string };
      capture.workspaceCreates?.push(payload);
      const workspace = {
        workspaceId: "wrk_project_a",
        name: payload.name,
        rootPath: payload.rootPath,
        status: "ACTIVE",
        createdAt: "2026-06-19T00:00:00Z",
        updatedAt: "2026-06-19T00:00:00Z"
      };
      workspaceItems.unshift(workspace);
      await route.fulfill(json(workspace));
      return;
    }
    if (method === "GET" && url.pathname === "/api/workspace-directories") {
      await route.fulfill(json(workspaceDirectories(url.searchParams.get("path"))));
      return;
    }
    if (method === "GET" && url.pathname.endsWith("/files")) {
      const path = url.searchParams.get("path") ?? "";
      const workspaceId = url.pathname.match(/\/api\/workspaces\/([^/]+)\/files$/)?.[1] ?? "";
      capture.fileRequests?.push({ workspaceId, path });
      await route.fulfill(json(fileEntries(path, workspaceId)));
      return;
    }
    if (method === "GET" && url.pathname.endsWith("/files/content")) {
      const path = url.searchParams.get("path") ?? "tests/checkout.spec.ts";
      await route.fulfill(json({
        path,
        content: capture.fileContents?.[path] ?? "import { test } from '@playwright/test';\n\ntest('checkout', async () => {});\n",
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
    if (method === "GET" && url.pathname === "/api/internal/agent/opencode/api/agent") {
      await route.fulfill(json([{ id: "build", name: "Build" }]));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/agent/opencode/api/model") {
      await route.fulfill(json([
        { id: "sonnet", providerId: "anthropic", name: "Sonnet" },
        { id: "opus", providerId: "anthropic", name: "Opus" },
        { id: "glm-5.2", providerId: "volcengine", name: "GLM-5.2" },
        { id: "north-mini-code", providerId: "opencode-zen", name: "North Mini Code Free", free: true }
      ]));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/agent/opencode/api/provider") {
      await route.fulfill(json([
        { id: "anthropic", name: "Anthropic", status: "ready" },
        { id: "volcengine", name: "Volcengine Ark", status: "ready" },
        { id: "opencode-zen", name: "OpenCode Zen", status: "ready" }
      ]));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/agent/opencode/api/command") {
      await route.fulfill(json([{ id: "test", name: "test", description: "Run tests" }]));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/agent/opencode/experimental/resource") {
      await route.fulfill(json([{ id: "issue-1", name: "Issue 1", uri: "mcp://issue/1", type: "issue" }]));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/agent/opencode/experimental/tool/ids") {
      await route.fulfill(json(["bash"]));
      return;
    }
    if (method === "GET" && ["/api/internal/agent/opencode/lsp", "/api/internal/agent/opencode/mcp", "/api/internal/agent/opencode/vcs/status"].includes(url.pathname)) {
      await route.fulfill(json({ status: "ready", branch: "main" }));
      return;
    }
    if (method === "POST" && url.pathname === "/api/internal/agent/opencode/runs") {
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
    if (method === "GET" && url.pathname === "/api/internal/agent/opencode/runs/run_1/events") {
      await route.fulfill({
        status: 200,
        headers: { ...corsHeaders(), "Content-Type": "text/event-stream", "Cache-Control": "no-cache" },
        body: sse(capture.runEvents ?? [
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
    if (method === "GET" && url.pathname === "/api/internal/agent/opencode/runs/run_1/diff") {
      await route.fulfill(json({ runId: "run_1", files: [diffFile()] }));
      return;
    }
    if (method === "POST" && url.pathname === "/api/internal/agent/opencode/permission/perm_1/reply") {
      capture.permissionReplies?.push(JSON.parse(route.request().postData() ?? "{}") as Record<string, unknown>);
      await route.fulfill(json({ accepted: true }));
      return;
    }
    if (method === "POST" && url.pathname === "/api/internal/agent/opencode/question/ques_1/reply") {
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

async function gotoWorkbench(page: Page) {
  await page.goto("/", { waitUntil: "domcontentloaded" });
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

function workspace() {
  return {
    workspaceId: "wrk_1234567890abcdef",
    name: "demo-tests",
    rootPath: "/Users/huang/workspace/demo-tests",
    status: "ACTIVE",
    createdAt: "2026-06-19T00:00:00Z",
    updatedAt: "2026-06-19T00:00:00Z"
  };
}

function workspaceDirectories(path: string | null) {
  if (path === "/Users/huang/workspace/project-a") {
    return {
      path: "/Users/huang/workspace/project-a",
      parentPath: "/Users/huang/workspace",
      entries: [{ name: "src", path: "/Users/huang/workspace/project-a/src" }]
    };
  }
  if (path === "/Users/huang/workspace/demo-tests") {
    return {
      path: "/Users/huang/workspace/demo-tests",
      parentPath: "/Users/huang/workspace",
      entries: [{ name: "tests", path: "/Users/huang/workspace/demo-tests/tests" }]
    };
  }
  return {
    path: "/Users/huang/workspace",
    parentPath: null,
    entries: [
      { name: "demo-tests", path: "/Users/huang/workspace/demo-tests" },
      { name: "project-a", path: "/Users/huang/workspace/project-a" }
    ]
  };
}

function fileEntries(path: string, workspaceId = "wrk_1234567890abcdef") {
  if (workspaceId === "wrk_project_a") {
    return path === "src"
      ? [{ path: "src/main.ts", name: "main.ts", directory: false, size: 90, lastModifiedAt: "2026-06-19T00:00:00Z" }]
      : [{ path: "src", name: "src", directory: true, size: 0, lastModifiedAt: "2026-06-19T00:00:00Z" }];
  }
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
