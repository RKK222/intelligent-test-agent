import { expect, test, type Browser, type BrowserContext, type Page, type Response } from "@playwright/test";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";

const runMemoryE2e = process.env.TEST_AGENT_RUN_MEMORY_E2E === "1";
const scenario = process.env.TEST_AGENT_MEMORY_E2E_SCENARIO ?? "full";

type Credentials = {
  username: string;
  password: string;
};

type ExistingWorkspace = {
  applicationName: string;
  workspaceAlias: string;
};

type StartedRun = {
  runId: string;
  startLatencyMs: number;
};

type LearnedMemory = {
  cardTestId: string;
  memoryId: string;
  summary: string;
  sessionId: string;
  sessionTitle: string;
  sessionHref: string;
};

test.describe.configure({ mode: "serial" });

test.describe("通用记忆真实浏览器端到端", () => {
  test.skip(!runMemoryE2e, "设置 TEST_AGENT_RUN_MEMORY_E2E=1 后运行真实记忆链路。");

  test("从原生学习到跨会话召回，并保留团队可见的会话引用", async ({ page, browser }) => {
    test.skip(scenario !== "full", "仅 TEST_AGENT_MEMORY_E2E_SCENARIO=full 运行完整业务验收。");
    test.setTimeout(12 * 60_000);

    const admin = credentialsFromEnv("ADMIN");
    const member = credentialsFromEnv("MEMBER");
    const suffix = `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 7)}`;
    const applicationId = `${env("TEST_AGENT_MEMORY_E2E_APPLICATION_ID_PREFIX", "memory-e2e")}-${suffix}`.slice(0, 128);
    const applicationName = `${env("TEST_AGENT_MEMORY_E2E_APPLICATION_NAME_PREFIX", "Memory E2E")}-${suffix}`.slice(0, 255);
    const repositoryName = requiredEnv("TEST_AGENT_MEMORY_E2E_REPOSITORY_NAME");
    const repositoryBranch = requiredEnv("TEST_AGENT_MEMORY_E2E_BRANCH");
    const repositoryDirectory = requiredEnv("TEST_AGENT_MEMORY_E2E_DIRECTORY");
    const workspaceAlias = `${env("TEST_AGENT_MEMORY_E2E_WORKSPACE_ALIAS_PREFIX", "memory-e2e")}-${suffix}`.slice(0, 100);
    const marker = `MEMORY_E2E_${suffix.replaceAll("-", "_").toUpperCase()}`;
    const rawTranscriptMarker = `RAW_TRANSCRIPT_${suffix.replaceAll("-", "_").toUpperCase()}_${Math.random().toString(36).slice(2, 12).toUpperCase()}`;
    const firstPrompt = `${marker}：这是需要长期复用的通用偏好——回答我的问题时先给结论，再用中文说明依据。${rawTranscriptMarker} 只是本轮一次性原始对话审计串，不是偏好或事实。请确认你理解这项偏好。`;

    await login(page, admin);
    await createApplicationWorkspaceThroughUi(page, {
      applicationId,
      applicationName,
      repositoryName,
      repositoryBranch,
      repositoryDirectory,
      workspaceAlias,
      memberQueries: [
        requiredEnv("TEST_AGENT_MEMORY_E2E_ADMIN_USER_QUERY"),
        requiredEnv("TEST_AGENT_MEMORY_E2E_MEMBER_USER_QUERY")
      ]
    });

    // 新建应用加入当前管理员后刷新一次成员态；此后所有验收仍从页面入口完成。
    await page.reload({ waitUntil: "domcontentloaded" });
    await dismissFirstLoginGuide(page);
    await selectApplicationAndWorkspace(page, { applicationName, workspaceAlias });

    await page.getByRole("button", { name: "长期记忆" }).click();
    await expectMemoryAvailable(page);
    const beforeIds = await memoryCardIds(page);

    await openWorkbench(page);
    await sendPromptAndWait(page, firstPrompt);

    const learned = await waitForLearnedMemory(page, marker, beforeIds);
    writeMemoryState(learned, rawTranscriptMarker);
    expect(learned.sessionId).toBeTruthy();
    expect(learned.sessionHref).toBe(`/s/${encodeURIComponent(learned.sessionId)}`);

    await page.getByRole("link", { name: "打开原始对话" }).click();
    await expect(page).toHaveURL(new RegExp(`/s/${escapeRegex(learned.sessionId)}(?:$|[?#])`));
    await expect(page.getByText(rawTranscriptMarker, { exact: false })).toBeVisible({ timeout: 60_000 });

    await openWorkbench(page);
    await page.getByRole("button", { name: "新建对话" }).click();
    const secondPrompt = `${marker}：新对话中请告诉我，你应该采用怎样的回答结构和语言？`;
    const usage = trackMemoryUsageResponses(page);
    const recalledRun = await sendPromptAndWait(page, secondPrompt);
    await expect(page.getByTestId(`run-memory-usage-${recalledRun.runId}`)).toContainText(/参考了 [1-9]\d* 条记忆/, {
      timeout: 60_000
    });
    await expect.poll(() => usage.memoryIds(recalledRun.runId), {
      timeout: 60_000,
      message: "对话 B 必须召回对话 A 学到的同一条逻辑记忆"
    }).toContain(learned.memoryId);
    usage.stop();

    await page.getByRole("button", { name: "长期记忆" }).click();
    await page.getByTestId(learned.cardTestId).getByRole("button").first().click();
    await expect(page.getByTestId("memory-detail-drawer")).toBeVisible();
    await page.getByRole("button", { name: "提交为团队记忆" }).click();
    await page.getByRole("button", { name: "确定" }).click();

    await expect(page.getByTestId("memory-tab-team")).toHaveAttribute("aria-selected", "true");
    const teamCard = page.locator('[data-testid^="memory-card-"]').filter({ hasText: learned.summary }).first();
    await expect(teamCard).toContainText("待审核", { timeout: 30_000 });
    await teamCard.getByRole("button").first().click();
    await page.getByRole("button", { name: "批准" }).click();
    await expect(teamCard).toContainText("已生效", { timeout: 30_000 });

    const memberContext = await browser.newContext();
    try {
      const memberPage = await memberContext.newPage();
      await login(memberPage, member);
      await selectApplication(memberPage, applicationName);
      await memberPage.getByRole("button", { name: "长期记忆" }).click();
      await expectMemoryAvailable(memberPage);
      await memberPage.getByTestId("memory-tab-team").click();
      const visibleTeamCard = memberPage.locator('[data-testid^="memory-card-"]').filter({ hasText: learned.summary }).first();
      await expect(visibleTeamCard).toContainText("已生效", { timeout: 30_000 });
      await visibleTeamCard.getByRole("button").first().click();
      const evidence = memberPage.getByTestId("memory-evidence-rail");
      await expect(evidence.locator(".evidence-node--observation strong").first()).toHaveText(learned.sessionTitle, {
        timeout: 30_000
      });
      await expect(evidence).toContainText(`会话 ID ${learned.sessionId}`);
      await expect(evidence.getByRole("link", { name: "打开原始对话" })).toHaveCount(0);
      await expect(evidence.getByText("仅会话所有者可打开原始对话")).toBeVisible();

      // 不只验证页面隐藏入口：在该成员的真实浏览器登录态下直接请求消息资源，
      // 后端仍必须拒绝，防止手工拼接 /s/{sessionId} 绕过 UI。
      const transcriptStatus = await memberPage.evaluate(async (sessionId) => {
        const response = await fetch(
          `/api/internal/platform/opencode-runtime/sessions/${encodeURIComponent(sessionId)}/messages?page=1&size=20&refresh=false`,
          { credentials: "same-origin" }
        );
        return response.status;
      }, learned.sessionId);
      expect([403, 404]).toContain(transcriptStatus);
    } finally {
      await memberContext.close();
    }
  });

  test("故障切换后仍可召回，双 profile 全失效时 2 秒 fail-open", async ({ page }) => {
    test.skip(!["recall", "fail-open"].includes(scenario), "由集群故障脚本选择 recall 或 fail-open 场景。");
    test.setTimeout(6 * 60_000);

    await login(page, credentialsFromEnv("ADMIN"));
    await selectApplicationAndWorkspace(page, existingWorkspaceFromEnv());
    const marker = env("TEST_AGENT_MEMORY_E2E_RECALL_MARKER", "记忆热备验收");
    const prompt = `${marker}：请根据你已经掌握的长期偏好回答，并简要说明采用的回答方式。`;
    const expectedMemoryId = expectedMemoryIdFromState();
    const usage = trackMemoryUsageResponses(page);
    const run = await sendPromptAndWait(page, prompt);

    if (scenario === "recall") {
      await expect(page.getByTestId(`run-memory-usage-${run.runId}`)).toContainText(/参考了 [1-9]\d* 条记忆/, {
        timeout: 60_000
      });
      await expect.poll(() => usage.memoryIds(run.runId), {
        timeout: 60_000,
        message: `故障切换后必须召回同一逻辑记忆 ${expectedMemoryId}`
      }).toContain(expectedMemoryId);
    } else {
      // Run 启动响应覆盖 Java 侧记忆检索阶段；用浏览器可观察的整段时延执行更严格的 2 秒门禁。
      expect(run.startLatencyMs).toBeLessThanOrEqual(2_000);
      await expect(page.getByTestId(`run-memory-usage-${run.runId}`)).toHaveCount(0);
    }
    usage.stop();
  });

  test("企业 Embedding 恢复后自动清空投影积压", async ({ page }) => {
    test.skip(scenario !== "projection-recovery", "由集群故障脚本在恢复企业 Embedding 后运行。");
    test.setTimeout(4 * 60_000);

    await login(page, credentialsFromEnv("ADMIN"));
    await page.getByRole("button", { name: "系统管理" }).click();
    await page.getByRole("button", { name: "记忆能力" }).click();

    await expect.poll(async () => {
      const projection = page.getByTestId("memory-health-projection");
      if (await projection.isVisible()) {
        const text = await projection.innerText();
        if (/0 待投影/.test(text) && /0 死信/.test(text)) return true;
      }
      await page.getByTestId("memory-admin-panel").getByRole("button", { name: "刷新" }).click();
      return false;
    }, { timeout: 180_000, intervals: [1_000, 2_000, 5_000] }).toBe(true);
  });

  test("按目标并发数从浏览器并行启动会话", async ({ browser }) => {
    test.skip(scenario !== "concurrency", "仅 TEST_AGENT_MEMORY_E2E_SCENARIO=concurrency 运行容量验收。");
    test.setTimeout(15 * 60_000);

    const concurrency = boundedInteger("TEST_AGENT_MEMORY_E2E_CONCURRENCY", 1, 64);
    const partitionMode = env("TEST_AGENT_MEMORY_E2E_PARTITION_MODE", "same");
    const users = concurrencyCredentials(partitionMode);
    const workspace = existingWorkspaceFromEnv();
    const contexts: BrowserContext[] = [];
    try {
      const runs = await Promise.all(Array.from({ length: concurrency }, async (_, index) => {
        const context = await browser.newContext();
        contexts.push(context);
        const page = await context.newPage();
        await login(page, users[index % users.length]);
        await selectApplicationAndWorkspace(page, workspace);
        const marker = `MEMORY_CONCURRENCY_${Date.now()}_${index}`;
        return sendPromptAndWait(
          page,
          `${marker}：请按我的长期偏好简短回答“并发验收已收到”。`,
          8 * 60_000
        );
      }));

      expect(new Set(runs.map((run) => run.runId)).size).toBe(concurrency);
      const latencies = runs.map((run) => run.startLatencyMs).sort((left, right) => left - right);
      const p99 = latencies[Math.max(0, Math.ceil(latencies.length * 0.99) - 1)];
      expect(p99, `Run 启动 p99=${p99}ms；该值包含路由与记忆检索阶段`).toBeLessThanOrEqual(2_000);

      const auditPage = contexts[0].pages()[0];
      await auditPage.getByRole("button", { name: "长期记忆" }).click();
      await expectMemoryAvailable(auditPage);
      const ids = await memoryCardIds(auditPage);
      expect(new Set(ids).size).toBe(ids.length);
    } finally {
      await Promise.all(contexts.map((context) => context.close()));
    }
  });
});

async function login(page: Page, credentials: Credentials) {
  await page.goto("/985211", { waitUntil: "domcontentloaded" });
  await page.getByPlaceholder("用户名").fill(credentials.username);
  await page.getByPlaceholder("密码").fill(credentials.password);
  await page.getByRole("button", { name: "登录" }).click();
  await expect(page.locator(".figma-app")).toBeVisible({ timeout: 60_000 });
  await dismissFirstLoginGuide(page);
}

async function dismissFirstLoginGuide(page: Page) {
  const close = page.locator(".el-tour__close-btn");
  if (await close.isVisible().catch(() => false)) await close.click();
}

async function createApplicationWorkspaceThroughUi(page: Page, input: {
  applicationId: string;
  applicationName: string;
  repositoryName: string;
  repositoryBranch: string;
  repositoryDirectory: string;
  workspaceAlias: string;
  memberQueries: string[];
}) {
  await page.getByRole("button", { name: "系统设置" }).click();
  const settings = page.getByRole("dialog", { name: "设置" });
  await expect(settings).toBeVisible();
  const applicationManagement = settings.getByRole("button", { name: "应用管理" });
  if (await applicationManagement.isVisible().catch(() => false)) await applicationManagement.click();

  await settings.getByTestId("create-application-open").click();
  const createDialog = page.getByRole("dialog", { name: "新建应用" });
  await createDialog.getByPlaceholder("例如 F-COSS").fill(input.applicationId);
  await createDialog.getByPlaceholder("请输入应用名称").fill(input.applicationName);
  await createDialog.getByTestId("create-application-submit").click();
  await expect(createDialog).toBeHidden({ timeout: 30_000 });
  await expect(settings.getByRole("combobox", { name: "应用选择" })).toBeVisible();

  for (const query of [...new Set(input.memberQueries)]) {
    const search = settings.getByPlaceholder("输入用户ID、用户名或统一认证号（懒加载搜索）");
    await search.fill(query);
    await settings.getByRole("button", { name: "搜索", exact: true }).click();
    await settings.getByRole("button", { name: "添加", exact: true }).click();
    await expect(search).toHaveValue("");
  }

  await settings.getByText("应用与版本库关联", { exact: true }).click();
  await settings.getByRole("combobox", { name: "选择未关联版本库" }).click();
  await page.getByRole("option").filter({ hasText: input.repositoryName }).first().click();
  await settings.getByRole("button", { name: "关联", exact: true }).click();
  await expect(settings.locator(".ta-item-row").filter({ hasText: input.repositoryName }).first()).toBeVisible();

  await settings.getByText("工作空间管理", { exact: true }).click();
  await settings.getByPlaceholder("选择已关联版本库").click();
  await page.getByRole("option").filter({ hasText: input.repositoryName }).first().click();
  await settings.getByPlaceholder("选择分支").click();
  await page.getByRole("option", { name: input.repositoryBranch, exact: true }).click();

  const version = settings.getByPlaceholder("选择日期");
  if (await version.isVisible().catch(() => false)) {
    await version.fill(env("TEST_AGENT_MEMORY_E2E_VERSION", yyyymmdd(new Date())));
  }

  const directory = settings.locator(".ta-workspace-tree-node").filter({
    has: settings.locator(".ta-workspace-tree-path", { hasText: input.repositoryDirectory })
  }).first();
  await expect(directory).toBeVisible({ timeout: 60_000 });
  await directory.click();
  await settings.getByPlaceholder("ai-test").fill(input.workspaceAlias);
  await settings.getByRole("button", { name: /^保存$/ }).click();
  await expect(settings.locator(".ta-item-row").filter({ hasText: input.workspaceAlias }).filter({ hasText: input.repositoryDirectory })).toBeVisible({
    timeout: 5 * 60_000
  });

  await settings.getByRole("button", { name: "关闭", exact: true }).click();
  await expect(settings).toBeHidden();
}

async function selectApplicationAndWorkspace(page: Page, workspace: ExistingWorkspace) {
  await selectApplication(page, workspace.applicationName);
  const selector = page.getByTestId("header-workspace-selector");
  if (!(await selector.getAttribute("aria-label"))?.includes(workspace.workspaceAlias)) {
    await selector.click();
    await page.getByRole("option").filter({ hasText: workspace.workspaceAlias }).first().click();
  }
  await expect(selector).toHaveAttribute("aria-label", new RegExp(escapeRegex(workspace.workspaceAlias)), { timeout: 60_000 });
  await openWorkbench(page);
  await expect(page.locator(".figma-chat-textarea")).toBeEnabled({ timeout: 60_000 });
}

async function selectApplication(page: Page, applicationName: string) {
  const selector = page.locator('button[aria-label^="应用："]');
  if (!(await selector.getAttribute("aria-label"))?.includes(applicationName)) {
    await selector.click();
    await page.getByRole("option").filter({ hasText: applicationName }).first().click();
  }
  await expect(selector).toHaveAttribute("aria-label", new RegExp(escapeRegex(applicationName)), { timeout: 60_000 });
}

async function openWorkbench(page: Page) {
  await page.getByRole("button", { name: "打开工作台" }).click();
  await expect(page.locator(".figma-chat-textarea")).toBeVisible();
}

async function sendPromptAndWait(page: Page, prompt: string, terminalTimeout = 5 * 60_000): Promise<StartedRun> {
  const composer = page.locator(".figma-chat-textarea");
  await expect(composer).toBeEnabled({ timeout: 60_000 });
  await composer.fill(prompt);
  const startedAt = Date.now();
  const responsePromise = page.waitForResponse((response) => isRunStartResponse(response), { timeout: 60_000 });
  await page.getByRole("button", { name: "发送", exact: true }).click();
  const response = await responsePromise;
  if (!response.ok()) {
    throw new Error(`Run 启动失败：HTTP ${response.status()} ${await response.text()}`);
  }
  const startLatencyMs = Date.now() - startedAt;
  const payload = await response.json() as {
    data?: { runId?: string };
    runId?: string;
  };
  const runId = payload.data?.runId ?? payload.runId;
  if (!runId) throw new Error("Run 启动响应缺少 runId");

  // 等待页面处理启动响应，随后以发送按钮重新出现作为浏览器侧终态，不读取后端数据库或直接调用 API。
  await page.waitForTimeout(300);
  await expect(page.getByRole("button", { name: "发送", exact: true })).toBeVisible({ timeout: terminalTimeout });
  await expect(page.getByRole("button", { name: "发送", exact: true })).toBeEnabled({ timeout: terminalTimeout });
  return { runId, startLatencyMs };
}

function isRunStartResponse(response: Response) {
  const url = new URL(response.url());
  return response.request().method() === "POST" && url.pathname === "/api/internal/agent/opencode/runs";
}

async function waitForLearnedMemory(page: Page, marker: string, baselineIds: string[]): Promise<LearnedMemory> {
  await page.getByRole("button", { name: "长期记忆" }).click();
  await expectMemoryAvailable(page);
  const baseline = new Set(baselineIds);
  let found: LearnedMemory | null = null;

  await expect.poll(async () => {
    await page.getByRole("button", { name: "刷新记忆" }).click();
    await expect(page.getByText("正在加载")).toHaveCount(0, { timeout: 15_000 });
    const cards = page.locator('[data-testid^="memory-card-"]');
    const cardTestIds = await cards.evaluateAll((nodes) => nodes.map((node) => node.getAttribute("data-testid") ?? ""));
    const ordered = cardTestIds.map((_, index) => index).sort((left, right) =>
      Number(Boolean(cardTestIds[right] && !baseline.has(cardTestIds[right])))
      - Number(Boolean(cardTestIds[left] && !baseline.has(cardTestIds[left]))));
    for (const index of ordered) {
      const card = cards.nth(index);
      const cardTestId = await card.getAttribute("data-testid");
      if (!cardTestId || baseline.has(cardTestId)) continue;
      const summary = (await card.locator(".memory-card__main > strong").innerText()).trim();
      await card.getByRole("button").first().click();
      const rail = page.getByTestId("memory-evidence-rail");
      await expect(rail).toBeVisible();
      const observation = rail.locator(".evidence-node--observation").first();
      const sessionTitle = (await observation.locator("strong").innerText()).trim();
      const details = await observation.locator("small").filter({ hasText: "会话 ID" }).innerText();
      const sessionId = /会话 ID\s+([^·\s]+)/.exec(details)?.[1] ?? "";
      const link = observation.getByRole("link", { name: "打开原始对话" });
      const sessionHref = (await link.getAttribute("href")) ?? "";
      if (sessionId && sessionHref && sessionTitle && sessionTitle !== "未命名对话") {
        found = {
          cardTestId,
          memoryId: cardTestId.replace(/^memory-card-/, ""),
          summary,
          sessionId,
          sessionTitle,
          sessionHref
        };
        return true;
      }
      await page.keyboard.press("Escape");
      await expect(page.getByTestId("memory-detail-drawer")).toBeHidden();
    }
    return false;
  }, { timeout: 4 * 60_000, intervals: [2_000, 3_000, 5_000] }).toBe(true);

  if (!found) throw new Error(`Mem0 学习完成但未找到包含来源会话标题和 ID 的新记忆：${marker}`);
  return found;
}

async function expectMemoryAvailable(page: Page) {
  await expect(page.getByRole("heading", { name: "长期记忆" })).toBeVisible({ timeout: 30_000 });
  await expect(page.getByTestId("memory-unavailable")).toHaveCount(0);
  await expect(page.getByTestId("memory-tab-personal")).toBeVisible({ timeout: 30_000 });
}

async function memoryCardIds(page: Page) {
  return page.locator('[data-testid^="memory-card-"]').evaluateAll((nodes) => nodes
    .map((node) => node.getAttribute("data-testid"))
    .filter((value): value is string => Boolean(value)));
}

function credentialsFromEnv(role: "ADMIN" | "MEMBER"): Credentials {
  return {
    username: requiredEnv(`TEST_AGENT_MEMORY_E2E_${role}_USERNAME`),
    password: requiredEnv(`TEST_AGENT_MEMORY_E2E_${role}_PASSWORD`)
  };
}

function concurrencyCredentials(partitionMode: string): Credentials[] {
  if (partitionMode === "same") return [credentialsFromEnv("ADMIN")];
  if (partitionMode !== "distinct") throw new Error("TEST_AGENT_MEMORY_E2E_PARTITION_MODE 只能是 same 或 distinct");
  const raw = requiredEnv("TEST_AGENT_MEMORY_E2E_USERS_JSON");
  const parsed = JSON.parse(raw) as Credentials[];
  if (!Array.isArray(parsed) || parsed.length < 2 || parsed.some((item) => !item?.username || !item?.password)) {
    throw new Error("TEST_AGENT_MEMORY_E2E_USERS_JSON 至少需要两个 username/password 用户");
  }
  return parsed;
}

function existingWorkspaceFromEnv(): ExistingWorkspace {
  return {
    applicationName: requiredEnv("TEST_AGENT_MEMORY_E2E_EXISTING_APPLICATION_NAME"),
    workspaceAlias: requiredEnv("TEST_AGENT_MEMORY_E2E_EXISTING_WORKSPACE_ALIAS")
  };
}

function boundedInteger(name: string, min: number, max: number) {
  const value = Number(requiredEnv(name));
  if (!Number.isInteger(value) || value < min || value > max) {
    throw new Error(`${name} 必须是 ${min}..${max} 的整数`);
  }
  return value;
}

function requiredEnv(name: string) {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`缺少真实记忆 E2E 配置：${name}`);
  return value;
}

function env(name: string, fallback: string) {
  return process.env[name]?.trim() || fallback;
}

function yyyymmdd(value: Date) {
  return `${value.getFullYear()}${String(value.getMonth() + 1).padStart(2, "0")}${String(value.getDate()).padStart(2, "0")}`;
}

function escapeRegex(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function stateFile() {
  return process.env.TEST_AGENT_MEMORY_E2E_STATE_FILE?.trim()
    || resolve(process.cwd(), "../.tmp/memory-e2e-state.json");
}

function writeMemoryState(memory: LearnedMemory, rawTranscriptMarker: string) {
  const file = stateFile();
  mkdirSync(dirname(file), { recursive: true });
  writeFileSync(file, `${JSON.stringify({
    memoryId: memory.memoryId,
    sessionId: memory.sessionId,
    sessionTitle: memory.sessionTitle,
    rawTranscriptMarker
  })}\n`, { encoding: "utf8", mode: 0o600 });
}

function expectedMemoryIdFromState() {
  const explicit = process.env.TEST_AGENT_MEMORY_E2E_EXPECTED_MEMORY_ID?.trim();
  if (explicit) return explicit;
  try {
    const parsed = JSON.parse(readFileSync(stateFile(), "utf8")) as { memoryId?: string };
    if (parsed.memoryId) return parsed.memoryId;
  } catch {
    // 统一在下面给出缺失配置错误，不泄漏本地路径或文件内容。
  }
  throw new Error(
    "故障验收缺少基线记忆 ID；先运行 full 场景，或设置 TEST_AGENT_MEMORY_E2E_EXPECTED_MEMORY_ID"
  );
}

function trackMemoryUsageResponses(page: Page) {
  const byRunId = new Map<string, Set<string>>();
  const listener = (response: Response) => {
    const url = new URL(response.url());
    if (response.request().method() !== "POST"
      || url.pathname !== "/api/internal/platform/memory/v1/run-usage/query"
      || !response.ok()) return;
    void response.json().then((payload: { data?: Array<{ runId?: string; memoryId?: string }> }) => {
      for (const item of payload.data ?? []) {
        if (!item.runId || !item.memoryId) continue;
        const ids = byRunId.get(item.runId) ?? new Set<string>();
        ids.add(item.memoryId);
        byRunId.set(item.runId, ids);
      }
    }).catch(() => undefined);
  };
  page.on("response", listener);
  return {
    memoryIds: (runId: string) => [...(byRunId.get(runId) ?? [])],
    stop: () => page.off("response", listener)
  };
}
