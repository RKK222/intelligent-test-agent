import { expect, test, type Browser, type BrowserContext, type Locator, type Page, type Response } from "@playwright/test";
import { chmodSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
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
  sessionId: string;
  startLatencyMs: number;
  requestStartedAt: number;
};

type LearnedMemory = {
  cardTestId: string;
  memoryId: string;
  summary: string;
  sessionId: string;
  sessionTitle: string;
  sessionHref: string;
  runId: string;
  content: string;
};

type ConcurrencyActor = Credentials & Partial<ExistingWorkspace> & {
  directoryQuery?: string;
  expectedMemoryId?: string;
};

type MemoryE2eState = Record<string, unknown> & {
  memoryId?: string;
  sessionId?: string;
  sessionTitle?: string;
  runId?: string;
  rawTranscriptMarker?: string;
  applicationName?: string;
  workspaceAlias?: string;
  teamMemoryId?: string;
  governanceMemoryId?: string;
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
        requiredEnv("TEST_AGENT_MEMORY_E2E_MEMBER_USER_QUERY"),
        ...concurrencyMemberQueriesFromEnv()
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
    const firstRun = await sendPromptAndWait(page, firstPrompt);

    const learned = await waitForLearnedMemory(page, {
      marker,
      baselineIds: beforeIds,
      expectedSessionId: firstRun.sessionId,
      expectedRunId: firstRun.runId,
      requireNew: true
    });
    writeMemoryState(learned, rawTranscriptMarker, { applicationName, workspaceAlias });
    expect(learned.sessionId).toBeTruthy();
    expect(learned.sessionHref).toBe(`/s/${encodeURIComponent(learned.sessionId)}`);
    expect(learned.runId).toBe(firstRun.runId);
    await expect(page.getByTestId("memory-detail-drawer")).not.toContainText(rawTranscriptMarker);
    await expectLegacyMemoryApiGone(page);

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
    const teamCardTestId = await teamCard.getAttribute("data-testid");
    const teamMemoryId = teamCardTestId?.replace(/^memory-card-/, "") ?? "";
    expect(teamMemoryId).toBeTruthy();
    mergeMemoryState({ teamMemoryId });

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
      await expect(memberPage.getByRole("button", { name: "批准", exact: true })).toHaveCount(0);
      await expect(memberPage.getByRole("button", { name: "拒绝", exact: true })).toHaveCount(0);

      // 不只验证页面隐藏入口：在该成员的真实浏览器登录态下直接请求消息资源，
      // 后端仍必须拒绝，防止手工拼接 /s/{sessionId} 绕过 UI。
      const transcriptStatus = await memberPage.evaluate(async (sessionId) => {
        const token = sessionStorage.getItem("test-agent.auth.token");
        const response = await fetch(
          `/api/internal/platform/opencode-runtime/sessions/${encodeURIComponent(sessionId)}/messages?page=1&size=20&refresh=false`,
          {
            credentials: "same-origin",
            headers: token ? { Authorization: `Bearer ${token}` } : {}
          }
        );
        return response.status;
      }, learned.sessionId);
      expect([403, 404]).toContain(transcriptStatus);

      // 再验证真实深链接也不会把所有者原文渲染给团队成员。
      await memberPage.goto(learned.sessionHref, { waitUntil: "domcontentloaded" });
      await expect(memberPage.locator(".figma-app")).toBeVisible({ timeout: 60_000 });
      await expect(memberPage.getByText(rawTranscriptMarker, { exact: false })).toHaveCount(0);
    } finally {
      await memberContext.close();
    }
  });

  test("手工记忆的编辑、范围提升、暂停和归档贯穿双集合写链", async ({ page }) => {
    test.skip(scenario !== "full", "仅完整业务验收继续覆盖记忆治理写链。");
    test.setTimeout(8 * 60_000);

    const state = readMemoryState();
    const workspace = workspaceFromState(state);
    const marker = `MEMORY_GOVERNANCE_${Date.now().toString(36).toUpperCase()}`;
    const initialContent = `${marker}：所有测试结论需要先列风险，再列建议。`;
    const updatedContent = `${marker}：所有测试结论先列风险，再给出可执行建议和负责人。`;

    await login(page, credentialsFromEnv("ADMIN"));
    await selectApplicationAndWorkspace(page, workspace);
    await page.getByRole("button", { name: "长期记忆" }).click();
    await expectMemoryAvailable(page);
    await page.getByTestId("add-personal-memory").click();
    const createDialog = page.getByRole("dialog", { name: "添加个人记忆" });
    await createDialog.getByRole("textbox", { name: "长期信息或偏好" }).fill(initialContent);
    await createDialog.getByLabel("当前应用").check();
    await createDialog.getByRole("button", { name: "保存", exact: true }).click();

    let card = page.locator('[data-testid^="memory-card-"]').filter({ hasText: marker }).first();
    await expect(card).toContainText(initialContent, { timeout: 30_000 });
    const cardTestId = await card.getAttribute("data-testid");
    const memoryId = cardTestId?.replace(/^memory-card-/, "") ?? "";
    expect(memoryId).toBeTruthy();
    const initialVersion = await memoryCardVersion(card);

    await card.getByRole("button", { name: "编辑记忆" }).click();
    const editDialog = page.getByRole("dialog", { name: "编辑记忆" });
    await editDialog.getByRole("textbox", { name: "长期信息或偏好" }).fill(updatedContent);
    await editDialog.getByRole("button", { name: "保存", exact: true }).click();
    card = page.getByTestId(`memory-card-${memoryId}`);
    await expect(card).toContainText(updatedContent, { timeout: 30_000 });
    await expect.poll(() => memoryCardVersion(card)).toBe(initialVersion + 1);

    await card.getByRole("button").first().click();
    await page.getByRole("button", { name: "提升为个人全局" }).click();
    card = page.getByTestId(`memory-card-${memoryId}`);
    await expect(card).toContainText("个人 · 全局", { timeout: 30_000 });
    await expect.poll(() => memoryCardVersion(card)).toBe(initialVersion + 2);

    await card.getByRole("button").first().click();
    await page.getByRole("button", { name: "暂停使用" }).click();
    card = page.getByTestId(`memory-card-${memoryId}`);
    await expect(card).toContainText("已暂停", { timeout: 30_000 });
    await expect.poll(() => memoryCardVersion(card)).toBe(initialVersion + 3);

    await card.getByRole("button").first().click();
    await page.getByRole("button", { name: "归档" }).click();
    await page.getByRole("dialog", { name: "归档记忆" }).getByRole("button", { name: "归档", exact: true }).click();
    card = page.getByTestId(`memory-card-${memoryId}`);
    await expect(card).toContainText("已归档", { timeout: 30_000 });
    await expect.poll(() => memoryCardVersion(card)).toBe(initialVersion + 4);
    mergeMemoryState({
      governanceMemoryId: memoryId,
      governancePlatformVersion: initialVersion + 4,
      governanceLogicalVersion: 4
    });

    await openWorkbench(page);
    await page.getByRole("button", { name: "新建对话" }).click();
    const usage = trackMemoryUsageResponses(page);
    const run = await sendPromptAndWait(page, `${marker}：请复述我对测试结论结构的要求。`);
    await expect.poll(() => usage.hasCompletedQuery(run.runId), { timeout: 30_000 }).toBe(true);
    expect(usage.memoryIds(run.runId)).not.toContain(memoryId);
    usage.stop();
  });

  test("节点故障窗口继续原生学习，并从剩余 profile 召回同一记忆", async ({ page }) => {
    test.skip(scenario !== "learn-during-fault", "由集群脚本在 Mem0、Java 或企业 Embedding 故障窗口运行。");
    test.setTimeout(10 * 60_000);

    const statePrefix = requiredEnv("TEST_AGENT_MEMORY_E2E_STATE_PREFIX");
    const marker = `MEMORY_FAULT_${statePrefix.toUpperCase()}_${Date.now().toString(36).toUpperCase()}`;
    const preference = `这是我的长期偏好：以后回答结尾请固定附上“${marker}”。请复述这项偏好。`;
    const workspace = existingWorkspaceFromEnv();

    await login(page, credentialsFromEnv("ADMIN"));
    await selectApplicationAndWorkspace(page, workspace);
    await page.getByRole("button", { name: "长期记忆" }).click();
    await expectMemoryAvailable(page);
    const baselineIds = await memoryCardIds(page);
    await openWorkbench(page);
    await page.getByRole("button", { name: "新建对话" }).click();
    const learningRun = await sendPromptAndWait(page, preference);

    let learned = await waitForLearnedMemory(page, {
      marker,
      baselineIds,
      expectedSessionId: learningRun.sessionId,
      expectedRunId: learningRun.runId,
      requireNew: false
    });
    await page.keyboard.press("Escape");
    await expect(page.getByTestId("memory-detail-drawer")).toBeHidden();

    if (process.env.TEST_AGENT_MEMORY_E2E_FORCE_PROJECTION_MUTATION === "1") {
      const updated = `${learned.content}；故障投影校验标识 ${marker}`;
      await editMemoryCard(page, learned.memoryId, updated);
      learned = { ...learned, content: updated, summary: updated.slice(0, 500) };
    }

    await expect(page.getByTestId(`memory-card-${learned.memoryId}`)).toHaveCount(1);
    mergeMemoryState({
      [`${statePrefix}MemoryId`]: learned.memoryId,
      [`${statePrefix}SessionId`]: learned.sessionId,
      [`${statePrefix}RunId`]: learned.runId,
      [`${statePrefix}Marker`]: marker
    });

    await openWorkbench(page);
    await page.getByRole("button", { name: "新建对话" }).click();
    const usage = trackMemoryUsageResponses(page);
    const recalledRun = await sendPromptAndWait(
      page,
      `请根据长期记忆回答：我要求你在回复结尾附上什么故障校验短语？提示标识是 ${marker}。`
    );
    await expect.poll(() => usage.memoryIds(recalledRun.runId), {
      timeout: 60_000,
      message: `故障窗口学习后必须召回同一平台记忆 ${learned.memoryId}`
    }).toContain(learned.memoryId);
    usage.stop();
  });

  test("企业 Embedding 中断期间浏览器可观察到真实投影积压", async ({ page }) => {
    test.skip(scenario !== "projection-backlog", "仅企业 Embedding 故障窗口检查投影积压。");
    test.setTimeout(4 * 60_000);

    await login(page, credentialsFromEnv("ADMIN"));
    await openMemoryAdmin(page);
    const expectedProfiles = boundedInteger("TEST_AGENT_MEMORY_E2E_EXPECT_PROFILE_COUNT", 2, 8);
    await expect(page.getByTestId("memory-health-embedding")).toContainText(
      new RegExp(`\\d+ / ${expectedProfiles} 可用`),
      { timeout: 30_000 }
    );
    await expect.poll(async () => {
      const backlog = await projectionBacklog(page);
      if (backlog === 0) await refreshMemoryAdmin(page);
      return backlog;
    }, {
      timeout: 180_000,
      intervals: [1_000, 2_000, 5_000],
      message: "故障期间至少一条跨 profile 投影必须进入共享 outbox"
    }).toBeGreaterThan(0);
    const counts = await projectionBacklogCounts(page);
    expect(counts.dead, "短时故障不应直接产生不可恢复死信").toBe(0);
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
      await expect.poll(() => usage.hasCompletedQuery(run.runId), { timeout: 10_000 }).toBe(true);
      expect(usage.memoryIds(run.runId)).toEqual([]);
      await expect(page.locator(".figma-chat-retry-card")).toHaveCount(0);
    }
    usage.stop();
  });

  test("企业 Embedding 恢复后自动清空投影积压", async ({ page }) => {
    test.skip(scenario !== "projection-recovery", "由集群故障脚本在恢复企业 Embedding 后运行。");
    test.setTimeout(4 * 60_000);

    await login(page, credentialsFromEnv("ADMIN"));
    await openMemoryAdmin(page);
    const expectedProfiles = boundedInteger("TEST_AGENT_MEMORY_E2E_EXPECT_PROFILE_COUNT", 2, 8);

    await expect.poll(async () => {
      const embedding = await page.getByTestId("memory-health-embedding").innerText();
      const backlog = await projectionBacklog(page);
      if (embedding.includes(`${expectedProfiles} / ${expectedProfiles} 可用`) && backlog === 0) return true;
      await refreshMemoryAdmin(page);
      return false;
    }, { timeout: 180_000, intervals: [1_000, 2_000, 5_000] }).toBe(true);
    const counts = await projectionBacklogCounts(page);
    expect(counts).toEqual({ pending: 0, processing: 0, dead: 0 });
  });

  test("按目标并发数从浏览器并行启动会话", async ({ browser }) => {
    test.skip(scenario !== "concurrency", "仅 TEST_AGENT_MEMORY_E2E_SCENARIO=concurrency 运行容量验收。");
    test.setTimeout(15 * 60_000);

    const concurrency = boundedInteger("TEST_AGENT_MEMORY_E2E_CONCURRENCY", 1, 64);
    const partitionMode = env("TEST_AGENT_MEMORY_E2E_PARTITION_MODE", "same");
    const actors = concurrencyCredentials(partitionMode, concurrency);
    const defaultWorkspace = existingWorkspaceFromEnv();
    const expectedInjectedMemoryIds = Array.from({ length: concurrency }, (_, index) => {
      const actor = actors[partitionMode === "same" ? 0 : index];
      const applicationName = actor.applicationName ?? defaultWorkspace.applicationName;
      return actor.expectedMemoryId?.trim()
        || expectedConcurrencyMemoryId(partitionMode, applicationName);
    });
    if (expectedInjectedMemoryIds.some((memoryId) => !memoryId)) {
      throw new Error(
        "每个并发 actor 都必须有可召回的基线记忆；同一新建 Application 可复用 teamMemoryId，其他 Application 请配置 expectedMemoryId"
      );
    }
    if (partitionMode === "distinct") {
      const partitionKeys = actors.slice(0, concurrency).map((actor) =>
        `${actor.username}\u0000${actor.applicationName ?? defaultWorkspace.applicationName}`);
      if (new Set(partitionKeys).size !== concurrency) {
        throw new Error("distinct 并发要求每个 actor 使用唯一的 username/Application 分区");
      }
    }
    const contexts: BrowserContext[] = [];
    const batchId = `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 7)}`.toUpperCase();
    try {
      // 先把所有浏览器准备到可发送状态，再同时点击发送；登录和工作区初始化耗时
      // 不应被混进并发检索 p99，也不能用串行创建上下文伪装并发。
      const prepared = await Promise.all(Array.from({ length: concurrency }, async (_, index) => {
        const actor = actors[partitionMode === "same" ? 0 : index];
        const context = await browser.newContext();
        contexts.push(context);
        const page = await context.newPage();
        await login(page, actor);
        await selectApplicationAndWorkspace(page, {
          applicationName: actor.applicationName ?? defaultWorkspace.applicationName,
          workspaceAlias: actor.workspaceAlias ?? defaultWorkspace.workspaceAlias
        });
        await page.getByRole("button", { name: "长期记忆" }).click();
        await expectMemoryAvailable(page);
        const baselineIds = await memoryCardIds(page);
        await openWorkbench(page);
        await page.getByRole("button", { name: "新建对话" }).click();
        return { page, baselineIds, usage: trackMemoryUsageResponses(page), index };
      }));

      const runs = await Promise.all(prepared.map(({ page, index }) => sendPromptAndWait(
        page,
        `MEMORY_CONCURRENCY_${batchId}_${index}：这是长期偏好；以后回答结尾附上批次编号 ${batchId}-${index}。请确认。`,
        8 * 60_000
      )));

      expect(new Set(runs.map((run) => run.runId)).size).toBe(concurrency);
      expect(new Set(runs.map((run) => run.sessionId)).size).toBe(concurrency);
      const latencies = runs.map((run) => run.startLatencyMs).sort((left, right) => left - right);
      const p99 = latencies[Math.max(0, Math.ceil(latencies.length * 0.99) - 1)];
      expect(p99, `Run 启动 p99=${p99}ms；该值包含路由与记忆检索阶段`).toBeLessThanOrEqual(2_000);
      const requestSpread = Math.max(...runs.map((run) => run.requestStartedAt))
        - Math.min(...runs.map((run) => run.requestStartedAt));
      expect(requestSpread, "全部浏览器必须在同一个 2 秒窗口发起请求").toBeLessThanOrEqual(2_000);

      await Promise.all(prepared.map(({ usage }, index) => expect.poll(
          () => usage.memoryIds(runs[index].runId),
          { timeout: 60_000, message: `并发 Run ${runs[index].runId} 必须注入基线团队/个人记忆` }
        ).toContain(expectedInjectedMemoryIds[index])));

      // 容量可到 64，但浏览器逐卡核验证据只抽取至多 4 个会话；完整版本、锁和
      // outbox 一致性由随后的共享库审计覆盖，避免 UI 轮询本身改变容量结果。
      const sampleCount = Math.min(concurrency, optionalBoundedInteger(
        "TEST_AGENT_MEMORY_E2E_CONCURRENCY_LEARNING_SAMPLES", 4, 1, 8
      ));
      const learned = await Promise.all(prepared.slice(0, sampleCount).map((item, index) =>
        waitForLearnedMemory(item.page, {
          marker: `MEMORY_CONCURRENCY_${batchId}_${index}`,
          baselineIds: item.baselineIds,
          expectedSessionId: runs[index].sessionId,
          expectedRunId: runs[index].runId,
          requireNew: false
        })));
      if (partitionMode === "distinct") {
        expect(new Set(learned.map((memory) => memory.memoryId)).size,
          "不同用户分区的个人记忆不能合并成同一个平台记忆").toBe(learned.length);
      }
      for (const { page } of prepared.slice(0, sampleCount)) {
        const ids = await memoryCardIds(page);
        expect(new Set(ids).size).toBe(ids.length);
      }
      mergeMemoryState({
        [`concurrency${capitalize(partitionMode)}RunIds`]: runs.map((run) => run.runId),
        [`concurrency${capitalize(partitionMode)}SessionIds`]: runs.map((run) => run.sessionId),
        [`concurrency${capitalize(partitionMode)}SampleMemoryIds`]: learned.map((memory) => memory.memoryId),
        [`concurrency${capitalize(partitionMode)}P99Ms`]: p99,
        [`concurrency${capitalize(partitionMode)}RequestSpreadMs`]: requestSpread
      });
      prepared.forEach(({ usage }) => usage.stop());
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
  const requestStartedAt = Date.now();
  const responsePromise = page.waitForResponse((response) => isRunStartResponse(response), { timeout: 60_000 });
  await page.getByRole("button", { name: "发送", exact: true }).click();
  const response = await responsePromise;
  if (!response.ok()) {
    throw new Error(`Run 启动失败：HTTP ${response.status()} ${await response.text()}`);
  }
  const startLatencyMs = Date.now() - requestStartedAt;
  const payload = await response.json() as {
    data?: { runId?: string; sessionId?: string };
    runId?: string;
    sessionId?: string;
  };
  const runId = payload.data?.runId ?? payload.runId;
  const sessionId = payload.data?.sessionId ?? payload.sessionId;
  if (!runId) throw new Error("Run 启动响应缺少 runId");
  if (!sessionId) throw new Error("Run 启动响应缺少 sessionId");

  // 等待页面处理启动响应，随后以发送按钮重新出现作为浏览器侧终态，不读取后端数据库或直接调用 API。
  await page.waitForTimeout(300);
  await expect(page.getByRole("button", { name: "发送", exact: true })).toBeVisible({ timeout: terminalTimeout });
  await expect(page.getByRole("button", { name: "发送", exact: true })).toBeEnabled({ timeout: terminalTimeout });
  return { runId, sessionId, startLatencyMs, requestStartedAt };
}

function isRunStartResponse(response: Response) {
  const url = new URL(response.url());
  return response.request().method() === "POST" && url.pathname === "/api/internal/agent/opencode/runs";
}

async function waitForLearnedMemory(page: Page, input: {
  marker: string;
  baselineIds: string[];
  expectedSessionId: string;
  expectedRunId: string;
  requireNew: boolean;
}): Promise<LearnedMemory> {
  await page.getByRole("button", { name: "长期记忆" }).click();
  await expectMemoryAvailable(page);
  const baseline = new Set(input.baselineIds);
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
      if (!cardTestId || (input.requireNew && baseline.has(cardTestId))) continue;
      const summary = (await card.locator(".memory-card__main > strong").innerText()).trim();
      await card.getByRole("button").first().click();
      const rail = page.getByTestId("memory-evidence-rail");
      await expect(rail).toBeVisible();
      const observations = rail.locator(".evidence-node--observation");
      let observationIndex = -1;
      let sessionId = "";
      let runId = "";
      for (let evidenceIndex = 0; evidenceIndex < await observations.count(); evidenceIndex += 1) {
        const details = await observations.nth(evidenceIndex).locator("small").filter({ hasText: "会话 ID" }).innerText();
        const candidateSessionId = /会话 ID\s+([^·\s]+)/.exec(details)?.[1] ?? "";
        const candidateRunId = /Run ID\s+([^·\s]+)/.exec(details)?.[1] ?? "";
        if (candidateSessionId === input.expectedSessionId && candidateRunId === input.expectedRunId) {
          observationIndex = evidenceIndex;
          sessionId = candidateSessionId;
          runId = candidateRunId;
          break;
        }
      }
      const observation = observationIndex >= 0 ? observations.nth(observationIndex) : observations.first();
      const sessionTitle = observationIndex >= 0
        ? (await observation.locator("strong").innerText()).trim()
        : "";
      const link = observation.getByRole("link", { name: "打开原始对话" });
      const sessionHref = observationIndex >= 0 ? (await link.getAttribute("href")) ?? "" : "";
      const content = (await page.locator(".memory-detail__summary p").innerText()).trim();
      if (sessionId === input.expectedSessionId
        && runId === input.expectedRunId
        && sessionHref
        && sessionTitle
        && sessionTitle !== "未命名对话") {
        found = {
          cardTestId,
          memoryId: cardTestId.replace(/^memory-card-/, ""),
          summary,
          sessionId,
          sessionTitle,
          sessionHref,
          runId,
          content
        };
        return true;
      }
      await page.keyboard.press("Escape");
      await expect(page.getByTestId("memory-detail-drawer")).toBeHidden();
    }
    return false;
  }, { timeout: 4 * 60_000, intervals: [2_000, 3_000, 5_000] }).toBe(true);

  if (!found) {
    throw new Error(
      `Mem0 学习完成但未找到来源 Session/Run 匹配的记忆：${input.marker} / ${input.expectedSessionId} / ${input.expectedRunId}`
    );
  }
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

async function memoryCardVersion(card: Locator) {
  const text = await card.locator(".memory-card__meta").innerText();
  const version = Number(/版本\s+(\d+)/.exec(text)?.[1]);
  if (!Number.isInteger(version)) throw new Error(`记忆卡片缺少可解析版本：${text}`);
  return version;
}

async function editMemoryCard(page: Page, memoryId: string, content: string) {
  const card = page.getByTestId(`memory-card-${memoryId}`);
  const previousVersion = await memoryCardVersion(card);
  await card.getByRole("button", { name: "编辑记忆" }).click();
  const dialog = page.getByRole("dialog", { name: "编辑记忆" });
  await dialog.getByRole("textbox", { name: "长期信息或偏好" }).fill(content);
  await dialog.getByRole("button", { name: "保存", exact: true }).click();
  await expect(card).toContainText(content, { timeout: 30_000 });
  await expect.poll(() => memoryCardVersion(card)).toBe(previousVersion + 1);
}

async function expectLegacyMemoryApiGone(page: Page) {
  const result = await page.evaluate(async () => {
    const token = sessionStorage.getItem("test-agent.auth.token");
    const response = await fetch("/api/internal/platform/qa-memory/v1/availability", {
      credentials: "same-origin",
      headers: token ? { Authorization: `Bearer ${token}` } : {}
    });
    return { status: response.status, body: await response.text() };
  });
  expect(result.status).toBe(410);
  expect(result.body).toContain("API_GONE");
}

async function openMemoryAdmin(page: Page) {
  await page.getByRole("button", { name: "系统管理" }).click();
  await page.getByRole("button", { name: "记忆能力", exact: true }).click();
  await expect(page.getByTestId("memory-admin-panel")).toBeVisible({ timeout: 30_000 });
  await expect(page.getByText("正在检查服务")).toHaveCount(0, { timeout: 30_000 });
}

async function refreshMemoryAdmin(page: Page) {
  const panel = page.getByTestId("memory-admin-panel");
  await panel.getByRole("button", { name: "刷新", exact: true }).click();
  await expect(panel.getByText("正在检查服务")).toHaveCount(0, { timeout: 30_000 });
}

async function projectionBacklogCounts(page: Page) {
  const text = await page.getByTestId("memory-health-projection").innerText();
  const pending = Number(/(\d+)\s+待投影/.exec(text)?.[1]);
  const processing = Number(/(\d+)\s+处理中/.exec(text)?.[1]);
  const dead = Number(/(\d+)\s+死信/.exec(text)?.[1]);
  if (![pending, processing, dead].every(Number.isInteger)) {
    throw new Error(`投影积压卡片格式不可解析：${text}`);
  }
  return { pending, processing, dead };
}

async function projectionBacklog(page: Page) {
  const counts = await projectionBacklogCounts(page);
  return counts.pending + counts.processing + counts.dead;
}

function credentialsFromEnv(role: "ADMIN" | "MEMBER"): Credentials {
  return {
    username: requiredEnv(`TEST_AGENT_MEMORY_E2E_${role}_USERNAME`),
    password: requiredEnv(`TEST_AGENT_MEMORY_E2E_${role}_PASSWORD`)
  };
}

function concurrencyCredentials(partitionMode: string, concurrency: number): ConcurrencyActor[] {
  if (partitionMode === "same") return [credentialsFromEnv("ADMIN")];
  if (partitionMode !== "distinct") throw new Error("TEST_AGENT_MEMORY_E2E_PARTITION_MODE 只能是 same 或 distinct");
  const raw = requiredEnv("TEST_AGENT_MEMORY_E2E_USERS_JSON");
  const parsed = JSON.parse(raw) as ConcurrencyActor[];
  if (!Array.isArray(parsed)
    || parsed.length < concurrency
    || parsed.some((item) => !item?.username || !item?.password)) {
    throw new Error(`TEST_AGENT_MEMORY_E2E_USERS_JSON 需要至少 ${concurrency} 个 username/password actor`);
  }
  return parsed;
}

function concurrencyMemberQueriesFromEnv() {
  const raw = process.env.TEST_AGENT_MEMORY_E2E_USERS_JSON?.trim();
  if (!raw) return [];
  const parsed = JSON.parse(raw) as ConcurrencyActor[];
  if (!Array.isArray(parsed) || parsed.some((item) => !item?.username || !item?.password)) {
    throw new Error("TEST_AGENT_MEMORY_E2E_USERS_JSON 必须是有效的 username/password actor 数组");
  }
  // 未指定外部 Application 的 actor 会参加 full 场景新建 Application 的并发验收，
  // 因此必须从浏览器应用管理入口真实加入成员，不能用后台脚本补数据。
  return parsed
    .filter((actor) => !actor.applicationName?.trim())
    .map((actor) => actor.directoryQuery?.trim() || actor.username);
}

function existingWorkspaceFromEnv(): ExistingWorkspace {
  const state = readMemoryState();
  if (typeof state.applicationName === "string" && typeof state.workspaceAlias === "string") {
    return workspaceFromState(state);
  }
  return {
    applicationName: requiredEnv("TEST_AGENT_MEMORY_E2E_EXISTING_APPLICATION_NAME"),
    workspaceAlias: requiredEnv("TEST_AGENT_MEMORY_E2E_EXISTING_WORKSPACE_ALIAS")
  };
}

function workspaceFromState(state: MemoryE2eState): ExistingWorkspace {
  if (typeof state.applicationName !== "string" || typeof state.workspaceAlias !== "string") {
    throw new Error("浏览器 E2E 状态缺少 applicationName/workspaceAlias；请先运行 full 场景");
  }
  return { applicationName: state.applicationName, workspaceAlias: state.workspaceAlias };
}

function boundedInteger(name: string, min: number, max: number) {
  const value = Number(requiredEnv(name));
  if (!Number.isInteger(value) || value < min || value > max) {
    throw new Error(`${name} 必须是 ${min}..${max} 的整数`);
  }
  return value;
}

function optionalBoundedInteger(name: string, fallback: number, min: number, max: number) {
  const raw = process.env[name]?.trim();
  if (!raw) return fallback;
  const value = Number(raw);
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

function readMemoryState(): MemoryE2eState {
  try {
    const parsed = JSON.parse(readFileSync(stateFile(), "utf8")) as unknown;
    return parsed && typeof parsed === "object" && !Array.isArray(parsed)
      ? parsed as MemoryE2eState
      : {};
  } catch {
    return {};
  }
}

function mergeMemoryState(patch: MemoryE2eState) {
  const file = stateFile();
  mkdirSync(dirname(file), { recursive: true });
  writeFileSync(file, `${JSON.stringify({ ...readMemoryState(), ...patch })}\n`, {
    encoding: "utf8",
    mode: 0o600
  });
  chmodSync(file, 0o600);
}

function writeMemoryState(
  memory: LearnedMemory,
  rawTranscriptMarker: string,
  workspace: ExistingWorkspace
) {
  mergeMemoryState({
    memoryId: memory.memoryId,
    sessionId: memory.sessionId,
    sessionTitle: memory.sessionTitle,
    runId: memory.runId,
    rawTranscriptMarker,
    applicationName: workspace.applicationName,
    workspaceAlias: workspace.workspaceAlias
  });
}

function expectedMemoryIdFromState() {
  const explicit = process.env.TEST_AGENT_MEMORY_E2E_EXPECTED_MEMORY_ID?.trim();
  if (explicit) return explicit;
  const stateKey = env("TEST_AGENT_MEMORY_E2E_EXPECTED_MEMORY_STATE_KEY", "memoryId");
  const value = readMemoryState()[stateKey];
  if (typeof value === "string" && value) return value;
  throw new Error(
    `故障验收缺少基线记忆 ID（state key=${stateKey}）；先运行 full/故障学习场景，或显式设置 ID`
  );
}

function expectedConcurrencyMemoryId(partitionMode: string, applicationName: string) {
  const explicit = process.env.TEST_AGENT_MEMORY_E2E_EXPECTED_CONCURRENCY_MEMORY_ID?.trim();
  if (explicit) return explicit;
  const state = readMemoryState();
  if (state.applicationName !== applicationName) return null;
  if (typeof state.teamMemoryId === "string" && state.teamMemoryId) return state.teamMemoryId;
  return partitionMode === "same" && typeof state.memoryId === "string" ? state.memoryId : null;
}

function capitalize(value: string) {
  return value ? `${value[0]?.toUpperCase() ?? ""}${value.slice(1)}` : value;
}

function trackMemoryUsageResponses(page: Page) {
  const byRunId = new Map<string, Set<string>>();
  const completedRunIds = new Set<string>();
  const failedRunIds = new Set<string>();
  const listener = (response: Response) => {
    const url = new URL(response.url());
    if (response.request().method() !== "POST"
      || url.pathname !== "/api/internal/platform/memory/v1/run-usage/query"
      || !response.ok()) return;
    let requestRunIds: string[] = [];
    try {
      const request = JSON.parse(response.request().postData() ?? "{}") as { runIds?: string[] };
      requestRunIds = (request.runIds ?? []).filter((runId): runId is string => typeof runId === "string");
    } catch {
      // 非法请求会由接口契约测试覆盖；真实 E2E 仍等待下一次合法批量恢复。
    }
    void response.json().then((payload: { data?: Array<{ runId?: string; memoryId?: string }> }) => {
      for (const item of payload.data ?? []) {
        if (!item.runId || !item.memoryId) continue;
        const ids = byRunId.get(item.runId) ?? new Set<string>();
        ids.add(item.memoryId);
        byRunId.set(item.runId, ids);
      }
    }).catch(() => {
      for (const runId of requestRunIds) failedRunIds.add(runId);
    }).finally(() => {
      // 空结果必须等响应体真正消费完成后才能断言，否则 response 事件与 json()
      // 解析之间的竞态会把“尚未解析”误判成“没有注入记忆”。
      for (const runId of requestRunIds) completedRunIds.add(runId);
    });
  };
  page.on("response", listener);
  const requireParsed = (runId: string) => {
    if (failedRunIds.has(runId)) throw new Error(`run-usage 响应体无法解析：${runId}`);
  };
  return {
    memoryIds: (runId: string) => {
      requireParsed(runId);
      return [...(byRunId.get(runId) ?? [])];
    },
    hasCompletedQuery: (runId: string) => {
      requireParsed(runId);
      return completedRunIds.has(runId);
    },
    stop: () => page.off("response", listener)
  };
}
