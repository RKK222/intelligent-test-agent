import { resolve } from "node:path";
import { expect, type APIRequestContext, test } from "@playwright/test";

type ApiEnvelope<T> = {
  success: boolean;
  traceId?: string;
  data?: T;
  code?: string;
  message?: string;
};

type PageResponse<T> = {
  items?: T[];
};

type WorkspaceLike = {
  workspaceId: string;
  name?: string;
  rootPath?: string;
};

type SessionLike = {
  sessionId: string;
  workspaceId: string;
};

const runRealE2e = process.env.TEST_AGENT_RUN_REAL_E2E === "1";
const apiBaseUrl = (
  process.env.FRONTEND_OPENCODE_REAL_API_BASE_URL ??
  process.env.VITE_TEST_AGENT_API_PROXY_TARGET ??
  process.env.TEST_AGENT_API_BASE_URL ??
  process.env.VITE_TEST_AGENT_API_BASE_URL ??
  "http://127.0.0.1:8080"
).replace(/\/$/, "");
const apiToken = process.env.FRONTEND_OPENCODE_REAL_API_TOKEN ?? process.env.TEST_AGENT_API_TOKEN ?? process.env.VITE_TEST_AGENT_API_TOKEN;
const workspaceRoot = process.env.FRONTEND_OPENCODE_REAL_WORKSPACE_ROOT ?? resolve(process.cwd(), "..");
const promptText =
  process.env.FRONTEND_OPENCODE_REAL_PROMPT ??
  `Reply exactly with: frontend-opencode real e2e ${new Date().toISOString()}`;
const expectedText = process.env.FRONTEND_OPENCODE_REAL_EXPECT_TEXT ?? "frontend-opencode real e2e";
const streamTimeout = Number(process.env.FRONTEND_OPENCODE_REAL_STREAM_TIMEOUT_MS ?? "120000");

test.skip(!runRealE2e, "Set TEST_AGENT_RUN_REAL_E2E=1 to run the real frontend-opencode/backend/opencode suite.");

test("sends a real prompt and renders RunEvent SSE output", async ({ page, request }) => {
  const workspace = await resolveWorkspace(request);
  const sessionId = process.env.FRONTEND_OPENCODE_REAL_SESSION_ID;

  if (sessionId) {
    await page.goto(`/w/${workspace.workspaceId}/session/${sessionId}`);
  } else {
    await page.goto(`/new-session?workspaceId=${workspace.workspaceId}`);
  }

  const composer = page.getByPlaceholder("Ask opencode to inspect, edit, test, or explain this workspace...");
  await expect(composer).toBeVisible();
  await composer.fill(promptText);
  await page.getByRole("button", { name: "Send" }).click();

  await expect(page).toHaveURL(/\/w\/[^/]+\/session\/[^/]+/);
  const timeline = page.getByRole("region", { name: "Session timeline" });
  await expect(timeline.getByText(promptText, { exact: false })).toBeVisible({ timeout: 30_000 });
  await expect(timeline.locator('.message-row[data-role="assistant"] .message-body p').filter({ hasText: expectedText })).toBeVisible({
    timeout: streamTimeout
  });
});

async function resolveWorkspace(request: APIRequestContext) {
  const sessionId = process.env.FRONTEND_OPENCODE_REAL_SESSION_ID;
  if (sessionId && !process.env.FRONTEND_OPENCODE_REAL_WORKSPACE_ID) {
    const session = await platformApi<SessionLike>(request, `/api/sessions/${encodeURIComponent(sessionId)}`);
    return { workspaceId: session.workspaceId } satisfies WorkspaceLike;
  }

  const explicitWorkspaceId = process.env.FRONTEND_OPENCODE_REAL_WORKSPACE_ID;
  if (explicitWorkspaceId) {
    return { workspaceId: explicitWorkspaceId } satisfies WorkspaceLike;
  }

  const workspacePage = await platformApi<PageResponse<WorkspaceLike>>(request, "/api/workspaces?page=1&size=100");
  const existing = workspacePage.items?.find((workspace) => workspace.rootPath === workspaceRoot);
  if (existing) {
    return existing;
  }

  // 未显式提供 workspace 时，按当前仓库根目录创建真实 workspace，保证测试可在新数据库上自举。
  return platformApi<WorkspaceLike>(request, "/api/workspaces", {
    method: "POST",
    data: {
      name: process.env.FRONTEND_OPENCODE_REAL_WORKSPACE_NAME ?? "frontend-opencode-real-e2e",
      rootPath: workspaceRoot
    }
  });
}

async function platformApi<T>(request: APIRequestContext, path: string, options: { method?: string; data?: unknown } = {}) {
  const headers: Record<string, string> = {
    Accept: "application/json",
    "X-Trace-Id": `trace_frontend_opencode_real_${Date.now().toString(36)}`
  };
  if (apiToken) {
    headers.Authorization = `Bearer ${apiToken}`;
  }
  const response = await request.fetch(`${apiBaseUrl}${path}`, {
    method: options.method ?? "GET",
    headers,
    data: options.data
  });
  const text = await response.text();
  const body = text ? (JSON.parse(text) as ApiEnvelope<T>) : ({ success: true, data: undefined } as ApiEnvelope<T>);
  if (!response.ok || body.success !== true) {
    throw new Error(body.message ?? `平台 API 请求失败：HTTP ${response.status()} ${path}`);
  }
  return body.data as T;
}
