import { expect, test, type Page } from "@playwright/test";

test("workbench opens a workspace file with mocked backend api", async ({ page }) => {
  await mockBackendApi(page, {
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    },
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1",
        appId: "app_gcms"
      }
    }
  });

  await gotoWorkbench(page, { selectConversation: false });

  await expect(page.getByText("MIMO测试智能体")).toBeVisible();
  await expect(page.getByRole("button", { name: "关闭运行与终端" })).toBeVisible();
  const workspaceTreeRow = page.getByRole("button", { name: /tests/ });
  await expect(workspaceTreeRow).toBeVisible();
  await expect.poll(() => workspaceTreeRow.evaluate((el) => getComputedStyle(el).height)).toBe("22px");
  await expect.poll(() => workspaceTreeRow.evaluate((el) => getComputedStyle(el).fontSize)).toBe("13px");
  const agentRootRow = page.locator(".agent-root-row").first();
  await expect(agentRootRow).toBeVisible();
  await expect.poll(() => agentRootRow.evaluate((el) => getComputedStyle(el).height)).toBe("22px");
  await page.getByRole("button", { name: /tests/ }).click();
  await page.getByRole("button", { name: /checkout.spec.ts/ }).click();
  await expect(page.getByText("tests/checkout.spec.ts", { exact: true }).first()).toBeVisible();
  await expect(page.getByRole("button", { name: /保存/ })).toBeVisible();
});

test("Markdown Mermaid Flowchart 和 Sequence 可视化编辑后复用保存链路", async ({ page }) => {
  test.setTimeout(60_000);
  const fileWriteRequests: Array<{ workspaceId: string; path: string; content: string }> = [];
  await mockBackendApi(page, {
    fileWriteRequests,
    fileContents: {
      "docs/mermaid.md": `# 可视化设计

\`\`\`mermaid
flowchart TD
A[开始] --> B[结束]
classDef important fill:red
\`\`\`

\`\`\`mermaid
sequenceDiagram
actor U as 用户
participant S as 服务
U->>S: 请求
Note over U,S: 保留说明
\`\`\``
    },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    },
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1",
        appId: "app_gcms"
      }
    }
  });

  await gotoWorkbench(page);
  await expect(page.getByText("MIMO测试智能体")).toBeVisible();
  await expect(page.getByRole("button", { name: /tests/ })).toBeVisible();
  await page.getByRole("button", { name: /docs/ }).click();
  await page.getByRole("button", { name: /mermaid.md/ }).click();
  await expect(page.locator(".monaco-editor")).toContainText("可视化设计", { timeout: 10_000 });
  await page.getByTestId("footer-markdown-preview").click();

  const visualButtons = page.getByRole("button", { name: "可视化编辑" });
  await expect(visualButtons).toHaveCount(2);
  await visualButtons.nth(0).click();
  const dialog = page.getByRole("dialog", { name: "Mermaid 可视化编辑" });
  await expect(dialog).toBeVisible();
  await dialog.locator(".vue-flow__node").filter({ hasText: "开始" }).click();
  await dialog.getByLabel("节点名称").fill("准备");
  await dialog.getByRole("button", { name: "应用到 Markdown" }).click();

  await expect(visualButtons).toHaveCount(2);
  await visualButtons.nth(1).click();
  await dialog.getByLabel("消息 1 标签").fill("登录请求");
  await dialog.getByRole("button", { name: "应用到 Markdown" }).click();

  await page.locator(".ta-workbench-footer-save").click();
  await expect.poll(() => fileWriteRequests.length).toBe(1);
  expect(fileWriteRequests[0]).toMatchObject({
    workspaceId: "wrk_personal_default",
    path: "docs/mermaid.md"
  });
  expect(fileWriteRequests[0]?.content).toContain('A["准备"]');
  expect(fileWriteRequests[0]?.content).toContain("U->>S: 登录请求");
  expect(fileWriteRequests[0]?.content).toContain("classDef important fill:red");
  expect(fileWriteRequests[0]?.content).toContain("Note over U,S: 保留说明");
});

test("switching to an application without recent workspace clears the previous file tree", async ({ page }) => {
  const fileRequests: Array<{ workspaceId: string; path: string }> = [];
  const defaultPersonalRequests: string[] = [];
  const personalWorkspaceRequests: string[] = [];
  await mockBackendApi(page, {
    fileRequests,
    defaultPersonalRequests,
    personalWorkspaceRequests,
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    },
    applications: [
      { appId: "app_gcms", appName: "F-GCMS", enabled: true },
      { appId: "app_coss", appName: "F-COSS", enabled: true }
    ],
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        workspaceId: "wrk_app_replica",
        name: "F-GCMS 报表 / 20260715",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1",
        appId: "app_gcms"
      },
      app_coss: null
    }
  });

  await gotoWorkbench(page);

  await expect(page.getByRole("button", { name: "F-GCMS" })).toBeVisible();
  await expect(page.getByRole("button", { name: /tests/ })).toBeVisible();

  await page.getByRole("button", { name: "F-GCMS" }).click();
  await page.getByRole("option", { name: /F-COSS/ }).click();

  await expect(page.getByRole("button", { name: "F-COSS" })).toBeVisible();
  await expect(page.getByText("当前应用尚未切换到可用工作区。")).toBeVisible();
  await expect(page.getByRole("button", { name: /tests/ })).toHaveCount(0);
  expect(fileRequests).toContainEqual({ workspaceId: "wrk_personal_default", path: "" });
  expect(personalWorkspaceRequests).toEqual(["awv_20260715"]);
  expect(defaultPersonalRequests).toEqual([]);
  const switcher = page.locator(".ta-workbench-footer-branch");
  await expect(switcher).toBeVisible();
  await switcher.click();
  await expect(page.locator(".ta-workbench-cascade-panel")).toContainText("应用：F-COSS");
});

test("workbench does not read a workspace file tree before an application is selected", async ({ page }) => {
  const fileRequests: Array<{ workspaceId: string; path: string }> = [];
  await mockBackendApi(page, {
    applications: [],
    authRoles: ["USER"],
    fileRequests
  });

  await gotoWorkbench(page);

  await expect(page.getByRole("button", { name: "未选择应用" })).toBeVisible();
  await expect(page.getByRole("button", { name: /tests/ })).toHaveCount(0);
  expect(fileRequests).toEqual([]);
});

test("application recent workspace loads existing default personal worktree before loading files", async ({ page }) => {
  const fileRequests: Array<{ workspaceId: string; path: string }> = [];
  const defaultPersonalRequests: string[] = [];
  const personalWorkspaceRequests: string[] = [];
  await mockBackendApi(page, {
    fileRequests,
    defaultPersonalRequests,
    personalWorkspaceRequests,
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    },
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        workspaceId: "wrk_app_replica",
        name: "F-GCMS 报表 / 20260715",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1",
        appId: "app_gcms"
      }
    }
  });

  await gotoWorkbench(page);

  await expect.poll(() => personalWorkspaceRequests).toEqual(["awv_20260715"]);
  expect(defaultPersonalRequests).toEqual([]);
  await expect.poll(() => fileRequests).toContainEqual({ workspaceId: "wrk_personal_default", path: "" });
  await expect(page.getByRole("button", { name: /worktree: feature_testagent_20260715_usr_admin_default/ }).first()).toBeVisible();
});

test("switch application forbidden feedback renders loading context details", async ({ page }) => {
  await mockBackendApi(page, {
    forbiddenRecentWorkspaces: {
      app_gcms: {
        code: "FORBIDDEN",
        message: "无该应用工作区权限",
        details: {
          appId: "app_gcms",
          appName: "F-GCMS",
          versionId: "awv_20260715",
          version: "20260715",
          workspaceKind: "default 私人工作区",
          workspaceName: "default",
          workspaceId: "wrk_personal_default",
          personalWorkspaceId: "psw_default"
        }
      }
    }
  });

  await gotoWorkbench(page);

  await expect(page.getByText("切换应用失败")).toBeVisible();
  await expect(page.getByText(/应用 F-GCMS\(app_gcms\)/)).toBeVisible();
  await expect(page.getByText(/版本 20260715/)).toBeVisible();
  await expect(page.getByText(/工作区 default 私人工作区:default/)).toBeVisible();
  await expect(page.getByText(/workspaceId: wrk_personal_default/)).toBeVisible();
});

test("user avatar menu logs out and returns to login", async ({ page }) => {
  const logoutRequests: string[] = [];
  const processStatusRequests: string[] = [];
  await mockBackendApi(page, { logoutRequests, authRoles: ["APP_ADMIN"], processStatusRequests });

  await gotoWorkbench(page);
  await expect.poll(() => processStatusRequests.length).toBeGreaterThanOrEqual(1);

  await page.getByRole("button", { name: "当前用户 admin" }).click();
  await expect.poll(() => processStatusRequests.length).toBeGreaterThanOrEqual(2);
  await expect(page.getByText("运行中(server-a / 10.8.0.12:4096)")).toBeVisible();
  // 灰显的「应用管理员」角色行应在菜单顶部，且在用户名 / 退出登录之前出现。
  const roleRow = page.locator(".figma-user-menu-role");
  await expect(roleRow).toBeVisible();
  await expect(roleRow).toHaveText("应用管理员");
  await expect(page.getByRole("menuitem", { name: "退出登录" })).toBeVisible();
  await page.getByRole("menuitem", { name: "退出登录" }).click();

  await expect(page.getByRole("heading", { name: "智能测试代理平台" })).toBeVisible();
  await expect.poll(() => logoutRequests).toEqual(["POST /api/auth/logout"]);
  await expect.poll(() => page.evaluate(() => sessionStorage.getItem("test-agent.auth.token"))).toBeNull();
});

test("login redirects to workbench and clears the initial opencode process checking state", async ({ page }) => {
  const loginRequests: Array<{ username?: string; password?: string }> = [];
  const processStatusRequests: string[] = [];
  await mockBackendApi(page, {
    skipInitialAuthToken: true,
    loginRequests,
    processStatusRequests
  });

  await page.goto("/login", { waitUntil: "domcontentloaded" });
  await page.getByPlaceholder("用户名").fill("888888888");
  await page.getByPlaceholder("密码").fill("123456");
  await page.getByRole("button", { name: "登录" }).click();

  await expect.poll(() => loginRequests).toEqual([{ username: "888888888", password: "123456" }]);
  await expect.poll(() => processStatusRequests.length).toBeGreaterThanOrEqual(1);
  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("登录后发送任务");
  await expect(page.getByRole("button", { name: "发送" })).toBeEnabled();
  await expect(page.getByText("正在检查 TestAgent 进程")).toHaveCount(0);
});

test("application switch menu excludes unjoined apps and keeps them in join dialog", async ({ page }) => {
  let releaseAuthMe!: () => void;
  const configurationApplicationRequests: string[] = [];
  const authMeGate = new Promise<void>((resolve) => {
    releaseAuthMe = resolve;
  });
  await mockBackendApi(page, {
    authRoles: ["SUPER_ADMIN"],
    authMeGate,
    configurationApplicationRequests,
    applications: [{ appId: "app_gcms", appName: "F-GCMS", enabled: true }],
    managedApplications: []
  });

  await gotoWorkbench(page);

  await expect(page.getByRole("button", { name: "未选择应用" })).toBeVisible();

  releaseAuthMe();

  await expect(page.getByRole("button", { name: "未选择应用" })).toBeVisible();
  expect(configurationApplicationRequests).toContain("GET /api/internal/platform/configuration-management/applications");
  await page.getByRole("button", { name: "未选择应用" }).click();
  await expect(page.getByRole("option", { name: /F-GCMS/ })).toHaveCount(0);
  await page.getByRole("option", { name: /加入其他应用/ }).click();
  await expect(page.locator(".figma-add-app-card")).toContainText("加入其他应用");
  await page.locator(".figma-add-app-select").click();
  await expect(page.getByRole("option", { name: "F-GCMS" })).toBeVisible();
});

test("super admin can open system management from the activity bar", async ({ page }) => {
  await mockBackendApi(page, { authRoles: ["SUPER_ADMIN"] });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: "系统管理" }).click();

  await expect(page.getByRole("navigation", { name: "系统管理导航" })).toBeVisible();
  await expect(page.getByRole("button", { name: "通用参数管理" })).toBeVisible();
});

test("settings dialog manages application context and SSH key metadata", async ({ page }) => {
  await mockBackendApi(page);

  await gotoWorkbench(page);

  await page.getByRole("button", { name: "系统设置" }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog).toBeVisible();
  await expect(page.getByText("应用人员管理")).toBeVisible();
  // Element Plus 的 el-select 是自定义组件：选中值显示在 .el-select__placeholder 的 span 中，
  // readonly input 的 value 始终为空，不能用 toHaveValue 校验。这里改为校验选中应用名。
  await expect(page.locator(".el-select").filter({ has: page.getByRole("combobox", { name: "应用选择" }) }).getByText("F-GCMS")).toBeVisible();

  await page.getByRole("button", { name: "个人设置" }).click();
  await page.getByPlaceholder("SSH key 名称").fill("work");
  await page.getByPlaceholder("-----BEGIN OPENSSH PRIVATE KEY-----").fill("-----BEGIN OPENSSH PRIVATE KEY-----\nsecret\n-----END OPENSSH PRIVATE KEY-----");
  await page.getByRole("button", { name: "添加 SSH key" }).click();

  await expect(dialog.getByText("SHA256:abc")).toBeVisible();
  await expect(dialog.getByText("secret")).toHaveCount(0);
  await expect(dialog.locator("textarea")).toHaveCount(0);
});

test("settings dialog grants application context to super admin", async ({ page }) => {
  await mockBackendApi(page, { authRoles: ["SUPER_ADMIN"] });

  await gotoWorkbench(page);

  await page.getByRole("button", { name: "系统设置" }).click();
  await expect(page.getByRole("button", { name: "应用管理" })).toBeVisible();
  await expect(page.getByText("应用人员管理")).toBeVisible();
  await expect(page.locator(".el-select").filter({ has: page.getByRole("combobox", { name: "应用选择" }) }).getByText("F-GCMS")).toBeVisible();
});

test("settings dialog loads application context after roles arrive while open", async ({ page }) => {
  let releaseAuthMe!: () => void;
  const configurationApplicationRequests: string[] = [];
  const authMeGate = new Promise<void>((resolve) => {
    releaseAuthMe = resolve;
  });
  await mockBackendApi(page, { authRoles: ["SUPER_ADMIN"], authMeGate, configurationApplicationRequests });

  await gotoWorkbench(page);

  await page.getByRole("button", { name: "系统设置" }).click();
  await expect(page.getByText("您当前角色[无角色]无该项设置权限。")).toBeVisible();
  expect(configurationApplicationRequests).toEqual([]);

  releaseAuthMe();
  await expect(page.getByText("应用人员管理")).toBeVisible();
  await expect(page.locator(".el-select").filter({ has: page.getByRole("combobox", { name: "应用选择" }) }).getByText("F-GCMS")).toBeVisible();
});

test("settings dialog shows permission placeholder for non app admins", async ({ page }) => {
  const configurationApplicationRequests: string[] = [];
  await mockBackendApi(page, { authRoles: ["USER"], configurationApplicationRequests });

  await gotoWorkbench(page);

  await page.getByRole("button", { name: "系统设置" }).click();
  await expect(page.getByRole("button", { name: "应用管理" })).toBeVisible();
  await expect(page.getByText("您当前角色[USER]无该项设置权限。")).toBeVisible();
  expect(configurationApplicationRequests).toEqual([]);
});

test("settings dialog shows empty role placeholder for users without roles", async ({ page }) => {
  await mockBackendApi(page, { authRoles: [] });

  await gotoWorkbench(page);

  await page.getByRole("button", { name: "系统设置" }).click();
  await expect(page.getByText("您当前角色[无角色]无该项设置权限。")).toBeVisible();
});

test("empty application workspace state does not expose local directory picker", async ({ page }) => {
  await mockBackendApi(page, { recentWorkspaces: { app_gcms: null } });

  await gotoWorkbench(page);

  await expect(page.getByText("当前应用尚未切换到可用工作区。")).toBeVisible();
});

test("application without recent version does not fallback to first template version", async ({ page }) => {
  const fileRequests: Array<{ workspaceId: string; path: string }> = [];
  const defaultPersonalRequests: string[] = [];
  await mockBackendApi(page, {
    fileRequests,
    defaultPersonalRequests,
    recentWorkspaces: { app_gcms: null },
    workspaceTemplates: {
      app_gcms: [
        {
          workspaceId: "awp_main",
          workspaceName: "F-GCMS 主服务",
          appId: "app_gcms",
          repositoryId: "repo_1",
          defaultBranch: "main",
          createdAt: "2026-06-24T00:00:00Z",
          updatedAt: "2026-06-24T00:00:00Z"
        }
      ]
    },
    workspaceVersions: {
      "app_gcms:awp_main": [
        {
          versionId: "awv_20260715",
          applicationWorkspaceId: "awp_main",
          appId: "app_gcms",
          repositoryId: "repo_1",
          version: "20260715",
          branch: "feature_testagent_20260715",
          repoRootPath: "/tmp/test-agent/appworkspace/awp_main/repo_1",
          workspaceRootPath: "/tmp/test-agent/appworkspace/awp_main/repo_1/F-GCMS/workspace",
          status: "ACTIVE",
          createdAt: "2026-06-24T00:00:00Z",
          updatedAt: "2026-06-24T00:00:00Z"
        }
      ]
    }
  });

  await gotoWorkbench(page);

  await expect(page.getByText("当前应用尚未切换到可用工作区。")).toBeVisible();
  await expect(page.locator(".ta-workbench-footer-branch")).toBeVisible();
  await expect.poll(() => defaultPersonalRequests).toEqual([]);
  expect(fileRequests).toEqual([]);
});

test("application recent version without default personal workspace stays empty", async ({ page }) => {
  const fileRequests: Array<{ workspaceId: string; path: string }> = [];
  const defaultPersonalRequests: string[] = [];
  const personalWorkspaceRequests: string[] = [];
  await mockBackendApi(page, {
    fileRequests,
    defaultPersonalRequests,
    personalWorkspaceRequests,
    personalWorkspaces: {
      awv_20260715: []
    },
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        workspaceId: "wrk_app_replica",
        name: "F-GCMS 报表 / 20260715",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1",
        appId: "app_gcms"
      }
    }
  });

  await gotoWorkbench(page);

  await expect(page.getByText("当前应用尚未切换到可用工作区。")).toBeVisible();
  await expect(page.locator(".ta-workbench-footer-branch")).toBeVisible();
  await expect.poll(() => personalWorkspaceRequests).toEqual(["awv_20260715"]);
  expect(defaultPersonalRequests).toEqual([]);
  expect(fileRequests).toEqual([]);
});

test("model picker groups models by provider and updates run model", async ({ page }) => {
  const runRequests: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, { runRequests });

  await gotoWorkbench(page);

  await page.getByRole("button", { name: "切换模型" }).click();
  await expect(page.getByRole("dialog", { name: "模型选择" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Anthropic" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Volcengine Ark" })).toBeVisible();
  await page.getByPlaceholder("搜索模型").fill("glm");
  await expect(page.getByRole("option", { name: /GLM-5.2/ })).toBeVisible();
  await page.getByRole("option", { name: /GLM-5.2/ }).click();
  await expect(page.getByRole("button", { name: "切换模型" })).toContainText("GLM-5.2");

  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("use selected model");
  await page.getByRole("button", { name: "发送" }).click();

  await expect.poll(() => runRequests.length).toBe(1);
  expect(runRequests[0]).toMatchObject({
    prompt: "use selected model",
    model: "volcengine/glm-5.2"
  });
});

test("new runs use one in-memory conversation context and a client request id", async ({ page }) => {
  const runRequests: Array<Record<string, unknown>> = [];
  const runContextRequests: string[] = [];
  await mockBackendApi(page, { ...runnableWorkspaceSetup(), runRequests, runContextRequests });

  await gotoWorkbench(page);
  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("context run");
  await page.getByRole("button", { name: "发送" }).click();

  await expect.poll(() => runRequests.length).toBe(1);
  expect(runContextRequests).toEqual(["ses_1"]);
  expect(runRequests[0]).toMatchObject({
    sessionId: "ses_1",
    contextToken: "ctx_e2e_1"
  });
  expect(String(runRequests[0]?.clientRequestId)).toMatch(/^req_/);
});

test("expired conversation context retries once with the same client request id", async ({ page }) => {
  const runRequests: Array<Record<string, unknown>> = [];
  const runContextRequests: string[] = [];
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    runRequests,
    runContextRequests,
    runContextTokens: ["ctx_old", "ctx_new"],
    runFailures: ["CONVERSATION_CONTEXT_EXPIRED"]
  });

  await gotoWorkbench(page);
  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("retry context");
  await page.getByRole("button", { name: "发送" }).click();

  await expect.poll(() => runRequests.length).toBe(2);
  expect(runContextRequests).toEqual(["ses_1", "ses_1"]);
  expect(runRequests.map((request) => request.contextToken)).toEqual(["ctx_old", "ctx_new"]);
  expect(runRequests[0]?.clientRequestId).toBe(runRequests[1]?.clientRequestId);
});

test("pending startRun does not poll active-run every 1.5 seconds", async ({ page }) => {
  let releaseRunRequest!: () => void;
  const runRequestGate = new Promise<void>((resolve) => {
    releaseRunRequest = resolve;
  });
  const runRequests: Array<Record<string, unknown>> = [];
  const activeRunRequests: string[] = [];
  const runEventRequests: string[] = [];
  await mockBackendApi(page, { ...runnableWorkspaceSetup(), runRequests, activeRunRequests, runEventRequests, runRequestGate });

  await gotoWorkbench(page);
  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("slow start");
  await page.getByRole("button", { name: "发送" }).click();

  await expect.poll(() => runRequests.length).toBe(1);
  await page.waitForTimeout(1800);
  expect(activeRunRequests.length).toBeLessThanOrEqual(1);
  const fallbackCount = activeRunRequests.length;
  await page.waitForTimeout(1800);
  expect(activeRunRequests).toHaveLength(fallbackCount);
  releaseRunRequest();
  await expect.poll(() => runEventRequests).toContain("/api/internal/agent/opencode/runs/run_1/events");
});

test("runtime-state uses the SSE snapshot without a parallel HTTP read", async ({ page }) => {
  const runtimeStateHttpRequests: string[] = [];
  await mockBackendApi(page, { runtimeStateHttpRequests });

  await gotoWorkbench(page);
  await page.waitForTimeout(200);

  expect(runtimeStateHttpRequests).toEqual([]);
});

test("run snapshot reset replaces stale live output with the materialized snapshot", async ({ page }) => {
  const runRequests: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    runRequests,
    runEvents: [
      event(1, "message.part.delta", {
        messageId: "msg_runtime",
        partId: "part_text",
        partType: "text",
        delta: "即将被快照替换的旧增量"
      }),
      event(0, "run.snapshot.reset", {
        reason: "TRANSIENT_SNAPSHOT_RECOVERY",
        resetGeneration: 0,
        earliestSeq: 1,
        snapshot: {
          barrierSeq: 1,
          runtimeVersion: 4,
          events: [
            { ...event(0, "message.updated", {
              messageId: "msg_input",
              role: "user",
              text: "验证快照恢复",
              message: {
                id: "msg_input",
                role: "user",
                text: "验证快照恢复"
              }
            }), eventId: "evt_snapshot_0" },
            { ...event(0, "message.part.updated", {
              messageId: "msg_runtime",
              partId: "part_text",
              part: {
                id: "part_text",
                messageID: "msg_runtime",
                type: "text",
                text: "Redis 物化快照中的最终回答"
              }
            }), eventId: "evt_snapshot_1" }
          ]
        }
      })
    ]
  });

  await gotoWorkbench(page);
  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("验证快照恢复");
  await page.getByRole("button", { name: "发送" }).click();

  await expect.poll(() => runRequests.length).toBe(1);
  await expect(page.getByText("Redis 物化快照中的最终回答")).toBeVisible();
  await expect(page.getByText("即将被快照替换的旧增量")).toHaveCount(0);
  await expect(page.getByText("验证快照恢复")).toBeVisible();
});

test("late session creation cannot replace a history switch", async ({ page }) => {
  let releaseSessionRequest!: () => void;
  const sessionRequestGate = new Promise<void>((resolve) => {
    releaseSessionRequest = resolve;
  });
  const sessionRequests: Array<Record<string, unknown>> = [];
  const runRequests: Array<Record<string, unknown>> = [];
  const runContextRequests: string[] = [];
  const sessionMessageRequests: string[] = [];
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    sessionRequestGate,
    sessionRequests,
    runRequests,
    runContextRequests,
    sessionMessageRequests,
    sessions: [{
      sessionId: "ses_history_target",
      workspaceId: "wrk_1234567890abcdef",
      title: "目标历史会话",
      status: "ACTIVE",
      pinned: false,
      createdAt: "2026-07-10T01:00:00Z",
      updatedAt: "2026-07-10T01:01:00Z"
    }],
    sessionMessagesBySessionId: {
      ses_history_target: [{
        messageId: "msg_history_target",
        sessionId: "ses_history_target",
        role: "ASSISTANT",
        content: "目标历史正文",
        createdAt: "2026-07-10T01:01:00Z"
      }]
    }
  });

  await gotoWorkbench(page);
  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("等待创建会话");
  await page.getByRole("button", { name: "发送" }).click();
  await expect.poll(() => sessionRequests.length).toBe(1);

  await page.getByRole("button", { name: /消息列表/ }).click();
  await page.getByRole("button", { name: "目标历史会话" }).click();
  await expect(page.getByText("目标历史正文")).toBeVisible();
  releaseSessionRequest();
  await page.waitForTimeout(200);

  expect(runRequests).toEqual([]);
  expect(runContextRequests).toEqual(["ses_history_target"]);
  expect(sessionMessageRequests).toContain(
    "/api/internal/platform/opencode-runtime/sessions/ses_history_target/messages?page=1&size=100&refresh=false"
  );
  await expect(page.getByText("目标历史正文")).toBeVisible();
});

test("agent picker updates the run agent", async ({ page }) => {
  const runRequests: Array<Record<string, unknown>> = [];
  const agentRequests: string[] = [];
  await mockBackendApi(page, {
    runRequests,
    agentRequests,
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        appId: "app_gcms",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1"
      }
    },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    },
    agents: [
      { id: "build", name: "Build", mode: "primary", description: "默认构建" },
      { id: "plan", name: "Plan", mode: "all", description: "可作为主 Agent" },
      { id: "review", name: "Review", mode: "subagent", description: "仅用于 @ 候选" }
    ]
  });

  await gotoWorkbench(page);
  await expect.poll(() => agentRequests.length).toBeGreaterThanOrEqual(1);
  await expect(page.getByRole("button", { name: "切换 Agent" })).toBeEnabled();

  await page.getByRole("button", { name: "切换 Agent" }).click();
  const agentDialog = page.getByRole("dialog", { name: "Agent 选择" });
  await expect(agentDialog).toBeVisible();
  await expect(agentDialog).toContainText("Build");
  await expect(agentDialog).toContainText("Plan");
  await expect(agentDialog).not.toContainText("Review");
  await agentDialog.getByRole("button", { name: /Plan/ }).click();
  await expect(page.getByRole("button", { name: "切换 Agent" })).toContainText("Plan");

  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("use selected agent");
  await page.getByRole("button", { name: "发送" }).click();

  await expect.poll(() => runRequests.length).toBe(1);
  expect(runRequests[0]).toMatchObject({
    prompt: "use selected agent",
    agent: "plan"
  });
});

test("agent picker retries after the catalog request fails", async ({ page }) => {
  const agentRequests: string[] = [];
  await mockBackendApi(page, {
    agentRequests,
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        appId: "app_gcms",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1"
      }
    },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    },
    agentResponses: [
      { status: 504, code: "OPENCODE_TIMEOUT", message: "Agent 目录加载超时" },
      { status: 504, code: "OPENCODE_TIMEOUT", message: "Agent 目录加载超时" },
      [{ id: "build", name: "Build", mode: "primary", description: "默认构建" }]
    ]
  });

  await gotoWorkbench(page);
  await expect.poll(() => agentRequests.length).toBe(1);

  await page.getByRole("button", { name: "切换 Agent" }).click();
  const agentDialog = page.getByRole("dialog", { name: "Agent 选择" });
  await expect(agentDialog).toBeVisible();
  await expect(agentDialog).toContainText("Agent 目录加载超时");

  await agentDialog.getByRole("button", { name: "重新加载 Agent" }).click();

  await expect.poll(() => agentRequests.length).toBeGreaterThanOrEqual(2);
  await expect(agentDialog).toContainText("Build");
});

test("agent catalog uses the current workspace when an older request finishes later", async ({ page }) => {
  let releaseGcmsAgents!: () => void;
  const gcmsAgentsGate = new Promise<void>((resolve) => {
    releaseGcmsAgents = resolve;
  });
  const agentRequests: string[] = [];
  await mockBackendApi(page, {
    agentRequests,
    applications: [
      { appId: "app_gcms", appName: "F-GCMS", enabled: true },
      { appId: "app_coss", appName: "F-COSS", enabled: true }
    ],
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        workspaceId: "wrk_app_gcms",
        appId: "app_gcms",
        versionId: "awv_gcms",
        applicationWorkspaceId: "awp_gcms"
      },
      app_coss: {
        ...workspace(),
        workspaceId: "wrk_app_coss",
        appId: "app_coss",
        versionId: "awv_coss",
        applicationWorkspaceId: "awp_coss"
      }
    },
    personalWorkspaces: {
      awv_gcms: [
        {
          ...defaultPersonalWorkspace("awv_gcms"),
          applicationWorkspaceId: "awp_gcms",
          runtimeWorkspace: {
            ...workspace(),
            workspaceId: "wrk_gcms_default",
            name: "gcms-default",
            appId: "app_gcms",
            versionId: "awv_gcms",
            applicationWorkspaceId: "awp_gcms"
          }
        }
      ],
      awv_coss: [
        {
          ...defaultPersonalWorkspace("awv_coss"),
          appId: "app_coss",
          applicationWorkspaceId: "awp_coss",
          runtimeWorkspace: {
            ...workspace(),
            workspaceId: "wrk_coss_default",
            name: "coss-default",
            appId: "app_coss",
            versionId: "awv_coss",
            applicationWorkspaceId: "awp_coss"
          }
        }
      ]
    },
    agentGatesByWorkspace: {
      wrk_gcms_default: gcmsAgentsGate
    },
    agentsByWorkspace: {
      wrk_gcms_default: [{ id: "gcms", name: "GCMS Agent", mode: "primary" }],
      wrk_coss_default: [{ id: "coss", name: "COSS Agent", mode: "primary" }]
    }
  });

  await gotoWorkbench(page);
  await expect(page.getByRole("button", { name: "F-GCMS" })).toBeVisible();

  await page.getByRole("button", { name: "F-GCMS" }).click();
  await page.getByRole("option", { name: /F-COSS/ }).click();

  await expect(page.getByRole("button", { name: "F-COSS" })).toBeVisible();
  await expect.poll(() => agentRequests.some((request) => request.includes("workspaceId=wrk_coss_default"))).toBe(true);
  await page.getByRole("button", { name: "切换 Agent" }).click();
  const agentDialog = page.getByRole("dialog", { name: "Agent 选择" });
  await expect(agentDialog).toContainText("COSS Agent");

  releaseGcmsAgents();

  await page.waitForTimeout(100);
  await expect(agentDialog).toContainText("COSS Agent");
  await expect(agentDialog).not.toContainText("GCMS Agent");
});

test("model picker keeps the selected model after page reload", async ({ page }) => {
  const runRequests: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, {
    runRequests,
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        appId: "app_gcms",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1"
      }
    },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    }
  });

  await gotoWorkbench(page);

  await page.getByRole("button", { name: "切换模型" }).click();
  await page.getByPlaceholder("搜索模型").fill("north");
  await page.getByRole("dialog", { name: "模型选择" }).getByRole("button", { name: /North Mini Code Free/ }).click();
  await expect(page.getByRole("button", { name: "切换模型" })).toContainText("North Mini Code Free");

  await page.reload({ waitUntil: "domcontentloaded" });
  await expect(page.getByRole("button", { name: "切换模型" })).toContainText("North Mini Code Free");

  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("use persisted model");
  await page.getByRole("button", { name: "发送" }).click();

  await expect.poll(() => runRequests.length).toBe(1);
  expect(runRequests[0]).toMatchObject({
    prompt: "use persisted model",
    model: "opencode-zen/north-mini-code"
  });
});

test("workbench clears stale persisted model and sends catalog default", async ({ page }) => {
  const runRequests: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, {
    runRequests,
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        appId: "app_gcms",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1"
      }
    },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    },
    models: [
      {
        id: "DeepSeek-V4-Flash-W8A8",
        providerId: "icbc-openai",
        name: "DeepSeek-V4-Flash-W8A8",
        defaultModel: true
      },
      { id: "Qwen3.6-27B", providerId: "icbc-openai", name: "Qwen3.6-27B" }
    ],
    providers: [{ id: "icbc-openai", providerId: "icbc-openai", name: "ICBC OpenAI", status: "ready" }]
  });
  await page.addInitScript(() => {
    localStorage.setItem("ta_selected_provider", "opencode-zen");
    localStorage.setItem("ta_selected_model", "opencode-zen/north-mini-code");
  });

  await gotoWorkbench(page);

  await expect(page.getByRole("button", { name: "切换模型" })).toContainText("DeepSeek-V4-Flash-W8A8");
  await expect.poll(() => page.evaluate(() => localStorage.getItem("ta_selected_model"))).toBe("icbc-openai/DeepSeek-V4-Flash-W8A8");

  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("use catalog default model");
  await page.getByRole("button", { name: "发送" }).click();

  await expect.poll(() => runRequests.length).toBe(1);
  expect(runRequests[0]).toMatchObject({
    prompt: "use catalog default model",
    model: "icbc-openai/DeepSeek-V4-Flash-W8A8"
  });
});

test("the first sent message becomes the new session title", async ({ page }) => {
  const sessionRequests: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, { sessionRequests });

  await gotoWorkbench(page);

  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("请生成登录测试案例");
  await page.getByRole("button", { name: "发送" }).click();

  await expect.poll(() => sessionRequests.length).toBe(1);
  expect(sessionRequests[0]).toEqual({
    workspaceId: "wrk_1234567890abcdef",
    title: "请生成登录测试案例"
  });
});

test("pet side-question streams progress, survives outside clicks, and calibrates replayed deltas", async ({ page }) => {
  const sideQuestionRequests: Array<Record<string, unknown>> = [];
  await installPetSideQuestionRunEventStream(page, {
    run_side_question_1: [
      streamEvent("evt_side_started", "run.started", {}, 20),
      streamEvent("evt_side_ready", "side_question.started", { sessionId: "ses_1" }, 30),
      streamEvent("evt_side_progress", "side_question.progress", { stage: "preparing_context" }, 80, true),
      // 模拟 Last-Event-ID 续传后 durable progress 与 transient delta 被重投。
      streamEvent("evt_side_progress", "side_question.progress", { stage: "preparing_context" }, 160),
      streamEvent("evt_side_delta_1", "side_question.delta", { delta: "增量片段" }, 2500),
      streamEvent("evt_side_delta_1", "side_question.delta", { delta: "增量片段" }, 2600),
      streamEvent("evt_side_delta_2", "side_question.delta", { delta: "（可能缺帧）" }, 2700),
      streamEvent("evt_side_terminal", "run.succeeded", { answer: "最终完整答案" }, 3200),
      streamEvent("evt_side_terminal", "run.succeeded", { answer: "最终完整答案" }, 3300)
    ]
  });
  await mockBackendApi(page, {
    sideQuestionRequests,
    sideQuestionRunIds: ["run_side_question_1"],
    recentWorkspaces: {
      app_gcms: { ...workspace(), appId: "app_gcms", versionId: "awv_20260715", applicationWorkspaceId: "awp_1" }
    },
    personalWorkspaces: { awv_20260715: [defaultPersonalWorkspace("awv_20260715")] },
    sessions: [session()],
    sessionMessages: [petContextMessage()]
  });
  await gotoWorkbench(page);

  await selectPetContextSession(page);
  await openPetSideQuestion(page);
  await page.getByTestId("robot-side-question-input").fill("这个对话干嘛了？");
  await page.getByTestId("robot-side-question-submit").click();

  await expect(page.getByTestId("robot-side-question-progress")).toHaveText("正在读取当前上下文");
  await page.locator(".figma-panel-center").dispatchEvent("click");
  await expect(page.getByTestId("robot-side-question")).toBeVisible();
  await expect(page.getByTestId("robot-side-question-answer")).toContainText("增量片段");
  await expect(page.getByTestId("robot-side-question-answer")).toHaveText("最终完整答案");
  await page.waitForTimeout(120);
  await expect(page.getByTestId("robot-side-question-answer")).toHaveText("最终完整答案");
  await expect.poll(() => page.evaluate(() => (
    window as Window & { __petSideQuestionReconnects?: Array<{ runId: string; lastEventId: string }> }
  ).__petSideQuestionReconnects)).toEqual([{ runId: "run_side_question_1", lastEventId: "evt_side_progress" }]);
  expect(sideQuestionRequests).toEqual([{
    question: "这个对话干嘛了？",
    messageId: "msg_remote_pet_context",
    model: "anthropic/sonnet"
  }]);
});

test("pet side-question keeps a failure editable and starts a fresh run on retry", async ({ page }) => {
  const sideQuestionRequests: Array<Record<string, unknown>> = [];
  await installPetSideQuestionRunEventStream(page, {
    run_side_question_failed: [
      streamEvent("evt_failed_progress", "side_question.progress", { stage: "reading" }, 30),
      streamEvent("evt_failed_terminal", "run.failed", { message: "旁路问答暂时失败" }, 80)
    ],
    run_side_question_retry: [
      streamEvent("evt_retry_progress", "side_question.progress", { stage: "composing" }, 30),
      streamEvent("evt_retry_terminal", "run.succeeded", { answer: "重试后的答案" }, 100)
    ]
  });
  await mockBackendApi(page, {
    sideQuestionRequests,
    sideQuestionRunIds: ["run_side_question_failed", "run_side_question_retry"],
    recentWorkspaces: {
      app_gcms: { ...workspace(), appId: "app_gcms", versionId: "awv_20260715", applicationWorkspaceId: "awp_1" }
    },
    personalWorkspaces: { awv_20260715: [defaultPersonalWorkspace("awv_20260715")] },
    sessions: [session()],
    sessionMessages: [petContextMessage()]
  });
  await gotoWorkbench(page);

  await selectPetContextSession(page);
  await openPetSideQuestion(page);
  const input = page.getByTestId("robot-side-question-input");
  await input.fill("第一次问题");
  await page.getByTestId("robot-side-question-submit").click();
  await expect(page.locator(".figma-robot-side-question-error")).toHaveText("旁路问答暂时失败");
  await expect(input).toBeEditable();

  await input.fill("修改后重试");
  await page.getByTestId("robot-side-question-submit").click();
  await expect(page.getByTestId("robot-side-question-answer")).toHaveText("重试后的答案");
  expect(sideQuestionRequests).toEqual([
    { question: "第一次问题", messageId: "msg_remote_pet_context", model: "anthropic/sonnet" },
    { question: "修改后重试", messageId: "msg_remote_pet_context", model: "anthropic/sonnet" }
  ]);
});

test("direct run projects remote question and permission to the platform session and replies", async ({ page }) => {
  const permissionReplies: Array<Record<string, unknown>> = [];
  const questionReplies: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    permissionReplies,
    questionReplies,
    runEvents: [
      event(1, "permission.asked", {
        requestId: "perm_1",
        sessionId: "ses_remote_root",
        title: "执行命令",
        description: "是否允许只读检查？"
      }),
      event(2, "question.asked", {
        requestId: "ques_1",
        sessionId: "ses_remote_root",
        questions: [{ question: "直接对话：请选择 A 或 B", options: [{ label: "A" }, { label: "B" }] }]
      })
    ]
  });

  await gotoWorkbench(page);
  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("直接验证交互映射");
  await page.getByRole("button", { name: "发送" }).click();

  const dock = page.locator(".figma-chat-question-dock");
  await expect(dock).toContainText("执行命令");
  await expect(dock).toContainText("直接对话：请选择 A 或 B");
  await page.getByRole("button", { name: "一次" }).click();
  await dock.getByRole("button", { name: "A", exact: true }).click();
  await dock.getByRole("button", { name: "提交", exact: true }).click();

  await expect.poll(() => permissionReplies).toEqual([{ decision: "once" }]);
  await expect.poll(() => questionReplies).toEqual([{ answers: [["A"]] }]);
});

test("a title-pending run keeps its SSE open until the synchronized native title arrives", async ({ page }) => {
  await page.addInitScript(() => {
    type StreamProbe = { runId: string; closed: boolean };
    const probes: StreamProbe[] = [];
    (window as Window & { __titleWatchRunStreams?: StreamProbe[] }).__titleWatchRunStreams = probes;

    class MockRunEventSource {
      onopen: ((event: Event) => void) | null = null;
      onerror: ((event: Event) => void) | null = null;
      private readonly listeners = new Map<string, Set<EventListener>>();
      private closed = false;
      private readonly probe: StreamProbe;

      constructor(readonly url: string) {
        const runId = decodeURIComponent(url).match(/\/runs\/([^/]+)\/events/)?.[1] ?? "run_1";
        this.probe = { runId, closed: false };
        probes.push(this.probe);
        window.setTimeout(() => this.onopen?.(new Event("open")), 0);
        window.setTimeout(() => this.emit("run.succeeded", runId, 1, { platformSessionTitlePending: true }), 10);
        window.setTimeout(() => this.emit("session.updated", runId, 2, {
          platformSessionTitleSynchronized: true,
          platformSessionTitle: "登录功能测试设计",
          isChildSession: false
        }), 80);
      }

      addEventListener(type: string, listener: EventListener) {
        const listeners = this.listeners.get(type) ?? new Set<EventListener>();
        listeners.add(listener);
        this.listeners.set(type, listeners);
      }

      removeEventListener(type: string, listener: EventListener) {
        this.listeners.get(type)?.delete(listener);
      }

      close() {
        this.closed = true;
        this.probe.closed = true;
      }

      private emit(type: string, runId: string, seq: number, payload: Record<string, unknown>) {
        if (this.closed) return;
        const event = new MessageEvent(type, {
          data: JSON.stringify({
            eventId: `evt_title_watch_${seq}`,
            runId,
            seq,
            type,
            traceId: "trace_e2e",
            occurredAt: "2026-06-19T00:00:00Z",
            payload
          }),
          lastEventId: String(seq)
        });
        this.listeners.get(type)?.forEach((listener) => listener(event));
      }
    }

    (window as Window & { EventSource: typeof EventSource }).EventSource = MockRunEventSource as unknown as typeof EventSource;
  });
  await mockBackendApi(page, {
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        appId: "app_gcms",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1"
      }
    },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    }
  });
  await gotoWorkbench(page);

  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("请设计登录功能测试");
  await page.getByRole("button", { name: "发送" }).click();

  await expect(page.getByText("任务完成")).toBeVisible();
  await expect.poll(() => page.evaluate(() => (window as Window & { __titleWatchRunStreams?: Array<{ closed: boolean }> }).__titleWatchRunStreams?.[0]?.closed)).toBe(false);
  await expect(page.locator(".figma-chat-title")).toHaveText("登录功能测试设计");
  await expect.poll(() => page.evaluate(() => (window as Window & { __titleWatchRunStreams?: Array<{ closed: boolean }> }).__titleWatchRunStreams?.[0]?.closed)).toBe(true);
});

test("a title-pending SSE closes when the backend closes the title watch or a new run starts", async ({ page }) => {
  await page.addInitScript(() => {
    type StreamProbe = { runId: string; closed: boolean };
    const probes: StreamProbe[] = [];
    (window as Window & { __titleWatchRunStreams?: StreamProbe[] }).__titleWatchRunStreams = probes;

    class MockRunEventSource {
      onopen: ((event: Event) => void) | null = null;
      onerror: ((event: Event) => void) | null = null;
      private readonly listeners = new Map<string, Set<EventListener>>();
      private closed = false;
      private readonly probe: StreamProbe;

      constructor(readonly url: string) {
        const runId = decodeURIComponent(url).match(/\/runs\/([^/]+)\/events/)?.[1] ?? "run_1";
        this.probe = { runId, closed: false };
        probes.push(this.probe);
        window.setTimeout(() => this.onopen?.(new Event("open")), 0);
        if (runId === "run_1") {
          window.setTimeout(() => this.emit("run.succeeded", runId, 1, { platformSessionTitlePending: true }), 10);
        }
      }

      addEventListener(type: string, listener: EventListener) {
        const listeners = this.listeners.get(type) ?? new Set<EventListener>();
        listeners.add(listener);
        this.listeners.set(type, listeners);
      }

      removeEventListener(type: string, listener: EventListener) {
        this.listeners.get(type)?.delete(listener);
      }

      close() {
        this.closed = true;
        this.probe.closed = true;
      }

      private emit(type: string, runId: string, seq: number, payload: Record<string, unknown>) {
        if (this.closed) return;
        const event = new MessageEvent(type, {
          data: JSON.stringify({
            eventId: `evt_title_watch_${runId}_${seq}`,
            runId,
            seq,
            type,
            traceId: "trace_e2e",
            occurredAt: "2026-06-19T00:00:00Z",
            payload
          }),
          lastEventId: String(seq)
        });
        this.listeners.get(type)?.forEach((listener) => listener(event));
      }
    }

    (window as Window & { EventSource: typeof EventSource }).EventSource = MockRunEventSource as unknown as typeof EventSource;
  });
  await mockBackendApi(page, {
    runIds: ["run_1", "run_2"],
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        appId: "app_gcms",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1"
      }
    },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    }
  });
  await gotoWorkbench(page);

  const composer = page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因");
  await composer.fill("第一轮等待原生标题");
  await page.getByRole("button", { name: "发送" }).click();

  await expect(page.getByText("任务完成")).toBeVisible();
  await expect.poll(() => page.evaluate(() => (window as Window & { __titleWatchRunStreams?: Array<{ closed: boolean }> }).__titleWatchRunStreams?.[0]?.closed)).toBe(false);

  await composer.fill("第二轮开始后关闭旧标题监听");
  await page.getByRole("button", { name: "发送" }).click();
  await expect.poll(() => page.evaluate(() => (window as Window & { __titleWatchRunStreams?: Array<{ closed: boolean }> }).__titleWatchRunStreams?.length)).toBe(2);
  await expect.poll(() => page.evaluate(() => (window as Window & { __titleWatchRunStreams?: Array<{ closed: boolean }> }).__titleWatchRunStreams?.[0]?.closed)).toBe(true);
});

test("retrying a failed chat run sends the previous prompt again", async ({ page }) => {
  const runRequests: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, {
    runRequests,
    runIds: ["run_1", "run_2"],
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        appId: "app_gcms",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1"
      }
    },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    },
    runEvents: [
      event(1, "run.failed", {
        error: { name: "ConnectionError", message: "Streaming response failed" }
      })
    ],
    runEventsByRunId: {
      run_2: []
    }
  });

  await gotoWorkbench(page);

  const composer = page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因");
  await composer.fill("重试这条测试任务");
  await page.getByRole("button", { name: "发送" }).click();
  await expect.poll(() => runRequests.length).toBe(1);
  await expect(page.locator(".figma-chat-retry-card-text")).toContainText("Streaming response failed");

  await page.locator(".figma-chat-retry-card-btn").click();

  await expect.poll(() => runRequests.length).toBe(2);
  expect(runRequests[1]).toMatchObject({ prompt: "重试这条测试任务" });
  await expect(page.locator(".figma-chat-retry-card")).toHaveCount(0);
});

test("new run success clears stale RunEvent SSE connection feedback", async ({ page }) => {
  const runRequests: Array<Record<string, unknown>> = [];
  await page.addInitScript(() => {
    class MockRunEventSource {
      onopen: ((event: Event) => void) | null = null;
      onerror: ((event: Event) => void) | null = null;
      private readonly listeners = new Map<string, Set<EventListener>>();
      private closed = false;

      constructor(readonly url: string) {
        const runId = decodeURIComponent(url).match(/\/runs\/([^/]+)\/events/)?.[1] ?? "run_1";
        window.setTimeout(() => this.onopen?.(new Event("open")), 0);
        if (runId === "run_1") {
          window.setTimeout(() => {
            if (!this.closed) {
              this.onerror?.(new Event("error"));
            }
          }, 20);
          window.setTimeout(() => {
            this.emit("run.failed", runId, 1, {
              error: { name: "ConnectionError", message: "Streaming response failed" }
            });
          }, 60);
        } else {
          window.setTimeout(() => {
            this.emit("run.succeeded", runId, 1, {});
          }, 20);
        }
      }

      addEventListener(type: string, listener: EventListener) {
        const listeners = this.listeners.get(type) ?? new Set<EventListener>();
        listeners.add(listener);
        this.listeners.set(type, listeners);
      }

      removeEventListener(type: string, listener: EventListener) {
        this.listeners.get(type)?.delete(listener);
      }

      close() {
        this.closed = true;
      }

      private emit(type: string, runId: string, seq: number, payload: Record<string, unknown>) {
        if (this.closed) {
          return;
        }
        const message = new MessageEvent(type, {
          data: JSON.stringify({
            eventId: `evt_mock_${runId}_${seq}`,
            runId,
            seq,
            type,
            traceId: "trace_e2e",
            occurredAt: "2026-06-19T00:00:00Z",
            payload
          }),
          lastEventId: String(seq)
        });
        this.listeners.get(type)?.forEach((listener) => listener(message));
      }
    }
    (window as Window & { EventSource: typeof EventSource }).EventSource = MockRunEventSource as unknown as typeof EventSource;
  });
  await mockBackendApi(page, {
    runRequests,
    runIds: ["run_1", "run_2"],
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        appId: "app_gcms",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1"
      }
    },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    }
  });

  await gotoWorkbench(page);

  const composer = page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因");
  await composer.fill("第一轮触发 SSE error");
  await page.getByRole("button", { name: "发送" }).click();
  await expect(page.getByText("RunEvent SSE 连接异常")).toBeVisible();
  await expect(page.locator(".figma-chat-retry-card")).toContainText("Streaming response failed");
  await expect(page.getByText("任务失败")).toBeVisible();

  await composer.fill("第二轮成功");
  await page.getByRole("button", { name: "发送" }).click();

  await expect.poll(() => runRequests.length).toBe(2);
  await expect(page.getByText("RunEvent SSE 连接异常")).toHaveCount(0);
  await expect(page.locator(".figma-chat-retry-card")).toHaveCount(0);
  await expect(page.getByText("Streaming response failed")).toHaveCount(0);
  await expect(page.getByText("任务失败")).toHaveCount(0);
  await expect(page.getByText("任务完成")).toBeVisible();
});

test("a live diff refreshes the changed file parent directory before the run finishes", async ({ page }) => {
  const fileRequests: Array<{ workspaceId: string; path: string }> = [];
  const gitDiffRequests: string[] = [];
  await mockBackendApi(page, {
    fileRequests,
    gitDiffRequests,
    runEvents: [
      event(1, "diff.proposed", {
        files: [{ path: "tests/generated.spec.ts", status: "added", additions: 8, deletions: 0 }]
      })
    ]
  });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: /tests/ }).click();
  await expect(page.getByRole("button", { name: /checkout.spec.ts/ })).toBeVisible();
  fileRequests.length = 0;
  gitDiffRequests.length = 0;

  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("生成新的测试文件");
  await page.getByRole("button", { name: "发送" }).click();

  await expect(page.getByText("1 个文件已更改")).toBeVisible();
  await expect.poll(() => fileRequests).toContainEqual({
    workspaceId: "wrk_1234567890abcdef",
    path: "tests"
  });
  await expect.poll(() => gitDiffRequests.length).toBeGreaterThan(0);
});

test("a live run diff does not hijack an open VCS diff panel", async ({ page }) => {
  await mockBackendApi(page, {
    authRoles: ["SUPER_ADMIN"],
    historyDiffFiles: [
      {
        path: "tests/checkout.spec.ts",
        status: "modified",
        staged: false,
        patch: "@@ -1 +1 @@\n-old\n+new",
        additions: 1,
        deletions: 1
      }
    ],
    runEvents: [
      event(1, "diff.proposed", {
        files: [{ path: "tests/generated.spec.ts", status: "added", additions: 8, deletions: 0 }]
      })
    ],
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        appId: "app_gcms",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1"
      }
    },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    }
  });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: "变更" }).click();
  await page.locator(".git-file-row").filter({ hasText: "tests/checkout.spec.ts" }).first().click();
  await expect(page.getByText("基线版本（只读）")).toBeVisible();

  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("继续生成文件");
  await page.getByRole("button", { name: "发送" }).click();

  await expect(page.getByText("基线版本（只读）")).toHaveCount(0);
  await expect(page.getByText("Run Diff")).toHaveCount(0);
});

test("discarding the last VCS diff closes the stale diff panel", async ({ page }) => {
  const diffFiles = [
    {
      path: "tests/checkout.spec.ts",
      status: "modified",
      staged: false,
      patch: "@@ -1 +1 @@\n-old\n+new",
      additions: 1,
      deletions: 1
    }
  ];
  await mockBackendApi(page, {
    authRoles: ["SUPER_ADMIN"],
    historyDiffFiles: diffFiles,
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        appId: "app_gcms",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1"
      }
    },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    }
  });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: "变更" }).click();
  const changeRow = page.locator(".git-file-row").filter({ hasText: "tests/checkout.spec.ts" }).first();
  await changeRow.click();
  await expect(page.getByText("基线版本（只读）")).toBeVisible();

  diffFiles.length = 0;
  await changeRow.hover();
  await page.getByTitle("回退文件改动").click();

  await expect(page.getByText("暂无 Diff")).toHaveCount(0);
  await expect(page.getByText("基线版本（只读）")).toHaveCount(0);
});

test("switching history restores assistant documents and the file changes summary", async ({ page }) => {
  const sessionTreeRequests: string[] = [];
  const sessionMessageRequests: string[] = [];
  await mockBackendApi(page, {
    sessionTreeRequests,
    sessionMessageRequests,
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        appId: "app_gcms",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1"
      }
    },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    },
    sessions: [
      {
        sessionId: "ses_history",
        workspaceId: "wrk_1234567890abcdef",
        title: "请生成登录测试报告",
        status: "ACTIVE",
        pinned: false,
        createdAt: "2026-06-28T08:00:00Z",
        updatedAt: "2026-06-28T08:01:00Z"
      }
    ],
    sessionMessages: [
      {
        messageId: "msg_user",
        sessionId: "ses_history",
        role: "USER",
        content: "请生成登录测试报告",
        createdAt: "2026-06-28T08:00:00Z",
        runId: "run_history"
      },
      {
        messageId: "msg_assistant",
        sessionId: "ses_history",
        role: "ASSISTANT",
        content: "测试报告已生成",
        createdAt: "2026-06-28T08:01:00Z",
        runId: "run_history",
        parts: [
          {
            id: "part_file",
            messageID: "msg_assistant",
            type: "file",
            name: "登录测试报告.md",
            path: "docs/登录测试报告.md",
            mimeType: "text/markdown"
          }
        ]
      }
    ],
    sessionTreeMessages: {
      sessionId: "ses_history",
      sessions: [{ rootSessionId: "ses_history", sessionId: "ses_history", childSession: false }],
      messagesBySessionId: {},
      childSessionIdByTaskPartId: {},
      events: [
        {
          type: "message.updated",
          rootSessionId: "ses_history",
          sessionId: "ses_history",
          childSession: false,
          payload: {
            rootSessionId: "ses_history",
            sessionId: "ses_history",
            message: { id: "remote_assistant", role: "assistant", content: "测试报告已生成" }
          }
        },
        {
          type: "message.part.updated",
          rootSessionId: "ses_history",
          sessionId: "ses_history",
          childSession: false,
          payload: {
            rootSessionId: "ses_history",
            sessionId: "ses_history",
            messageId: "remote_assistant",
            messageID: "remote_assistant",
            part: {
              id: "part_text",
              messageID: "remote_assistant",
              type: "text",
              text: "测试报告已生成"
            }
          }
        },
        {
          type: "message.part.updated",
          rootSessionId: "ses_history",
          sessionId: "ses_history",
          childSession: false,
          payload: {
            rootSessionId: "ses_history",
            sessionId: "ses_history",
            messageId: "remote_assistant",
            messageID: "remote_assistant",
            part: {
              id: "part_file",
              messageID: "remote_assistant",
              type: "file",
              name: "登录测试报告.md",
              path: "docs/登录测试报告.md",
              mimeType: "text/markdown"
            }
          }
        }
      ]
    },
    historyRun: {
      runId: "run_history",
      sessionId: "ses_history",
      workspaceId: "wrk_1234567890abcdef",
      status: "SUCCEEDED",
      createdAt: "2026-06-28T08:00:00Z",
      updatedAt: "2026-06-28T08:01:00Z"
    },
    historyDiffFiles: [
      {
        path: "docs/登录测试报告.md",
        patch: "--- /dev/null\n+++ b/登录测试报告.md\n@@ -0,0 +1,1 @@\n+# 登录测试报告",
        additions: 1,
        deletions: 0,
        status: "added"
      }
    ]
  });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: /消息列表/ }).click();
  await page.getByRole("button", { name: /请生成登录测试报告/ }).click();

  await expect.poll(() => sessionTreeRequests).toContain("/api/internal/agent/opencode/sessions/ses_history/session-tree/messages");
  await expect.poll(() => sessionMessageRequests).toContain("/api/internal/platform/opencode-runtime/sessions/ses_history/messages?page=1&size=100&refresh=false");
  await expect(page.getByText("测试报告已生成")).toBeVisible();
  const changesCard = page.getByRole("button", { name: /文件修改 1/ });
  await expect(changesCard).toContainText("+1");
  await changesCard.click();
  await expect(page.getByText("docs/登录测试报告.md")).toBeVisible();
});

test("history run projection keeps sending locked until stale details cannot overwrite a new run", async ({ page }) => {
  let releaseHistoryRun!: () => void;
  const historyRunGate = new Promise<void>((resolve) => {
    releaseHistoryRun = resolve;
  });
  const historyRunRequests: string[] = [];
  const runRequests: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    historyRunGate,
    historyRunRequests,
    runRequests,
    sessions: [{
      sessionId: "ses_history",
      workspaceId: "wrk_1234567890abcdef",
      title: "等待历史运行详情",
      status: "ACTIVE",
      pinned: false,
      createdAt: "2026-07-10T03:00:00Z",
      updatedAt: "2026-07-10T03:01:00Z"
    }],
    sessionMessages: [{
      messageId: "msg_history_projection",
      sessionId: "ses_history",
      role: "ASSISTANT",
      content: "历史正文已就绪",
      runId: "run_history",
      createdAt: "2026-07-10T03:01:00Z"
    }],
    historyRun: {
      runId: "run_history",
      sessionId: "ses_history",
      workspaceId: "wrk_1234567890abcdef",
      status: "SUCCEEDED",
      createdAt: "2026-07-10T03:00:00Z",
      updatedAt: "2026-07-10T03:01:00Z"
    }
  });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: /消息列表/ }).click();
  await page.getByRole("button", { name: "等待历史运行详情" }).click();
  await expect.poll(() => historyRunRequests).toContain("/api/internal/agent/opencode/runs/run_history");
  await expect(page.getByText("历史正文已就绪")).toBeVisible();
  await expect(page.getByText("正在加载消息列表…")).toHaveCount(0);

  const composer = page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因");
  const sendButton = page.getByRole("button", { name: "发送" });
  await composer.fill("历史投影完成后发送");
  await expect(sendButton).toBeDisabled();
  await sendButton.click({ force: true });
  expect(runRequests).toEqual([]);

  releaseHistoryRun();
  await expect(page.getByText("历史正文已就绪")).toBeVisible();
  await expect(sendButton).toBeEnabled();
  await sendButton.click();
  await expect.poll(() => runRequests.length).toBe(1);
  expect(runRequests[0]).toMatchObject({ sessionId: "ses_history", prompt: "历史投影完成后发送" });
});

test("switching history restores a pending native question dock instead of only its tool JSON", async ({ page }) => {
  const questionReplies: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    questionReplies,
    sessions: [{
      sessionId: "ses_history_question",
      workspaceId: "wrk_1234567890abcdef",
      title: "历史提问会话",
      status: "ACTIVE",
      createdAt: "2026-07-11T08:00:00Z",
      updatedAt: "2026-07-11T08:01:00Z"
    }],
    sessionMessages: [{
      messageId: "msg_history_question",
      sessionId: "ses_history_question",
      role: "ASSISTANT",
      content: "等待用户选择验证范围",
      createdAt: "2026-07-11T08:01:00Z",
      parts: [{
        id: "part_question_tool",
        messageID: "msg_history_question",
        type: "tool",
        tool: "question",
        state: { status: "running", input: { question: "请选择验证范围" } }
      }]
    }],
    sessionTreeMessages: {
      sessionId: "ses_history_question",
      sessions: [{ rootSessionId: "ses_history_question", sessionId: "ses_history_question", childSession: false }],
      messagesBySessionId: {},
      childSessionIdByTaskPartId: {},
      events: []
    },
    sessionQuestionsById: {
      ses_history_question: [{
        id: "que_history_question",
        sessionID: "ses_history_question",
        questions: [{
          question: "请选择验证范围",
          header: "验证范围",
          options: [{ label: "接口测试", description: "执行接口回归" }],
          multiple: false,
          custom: true
        }]
      }]
    }
  });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: "消息列表" }).click();
  await page.getByRole("button", { name: /历史提问会话/ }).click();
  const dock = page.locator(".figma-chat-question-dock");
  await expect(dock).toContainText("请选择验证范围");
  await page.getByRole("button", { name: "接口测试" }).click();
  await page.getByRole("button", { name: "提交" }).click();
  await expect.poll(() => questionReplies).toEqual([{ answers: [["接口测试"]] }]);
});

test("switching history restores a pending native permission dock and allows reply", async ({ page }) => {
  const permissionReplies: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, {
    permissionReplies,
    sessions: [{
      sessionId: "ses_history_permission",
      workspaceId: "wrk_1234567890abcdef",
      title: "历史权限会话",
      status: "ACTIVE",
      createdAt: "2026-07-11T08:00:00Z",
      updatedAt: "2026-07-11T08:01:00Z"
    }],
    sessionMessagesBySessionId: { ses_history_permission: [] },
    sessionTreeMessagesBySessionId: {
      ses_history_permission: {
        sessionId: "ses_history_permission",
        sessions: [{ rootSessionId: "ses_history_permission", sessionId: "ses_history_permission", childSession: false }],
        messagesBySessionId: {},
        childSessionIdByTaskPartId: {},
        events: []
      }
    },
    sessionPermissionsById: {
      ses_history_permission: [{
        id: "perm_history_permission",
        sessionID: "ses_remote_permission",
        permission: "edit",
        title: "允许修改测试文件",
        pattern: "tests/**"
      }]
    }
  });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: "消息列表" }).click();
  await page.getByRole("button", { name: /历史权限会话/ }).click();
  const dock = page.locator(".figma-chat-question-dock");
  await expect(dock).toContainText("允许修改测试文件");
  await page.getByRole("button", { name: "一次" }).click();
  await expect.poll(() => permissionReplies).toEqual([{ decision: "once" }]);
});

test("history pending interaction stays scoped to its own session", async ({ page }) => {
  const sessionMessageRequests: string[] = [];
  await mockBackendApi(page, {
    sessions: [
      {
        sessionId: "ses_history_question_a",
        workspaceId: "wrk_1234567890abcdef",
        title: "A 会话有提问",
        status: "ACTIVE",
        createdAt: "2026-07-11T08:00:00Z",
        updatedAt: "2026-07-11T08:01:00Z"
      },
      {
        sessionId: "ses_history_question_b",
        workspaceId: "wrk_1234567890abcdef",
        title: "B 会话无提问",
        status: "ACTIVE",
        createdAt: "2026-07-11T08:02:00Z",
        updatedAt: "2026-07-11T08:03:00Z"
      }
    ],
    sessionMessagesBySessionId: {
      ses_history_question_a: [],
      ses_history_question_b: []
    },
    sessionMessageRequests,
    sessionTreeMessagesBySessionId: {
      ses_history_question_a: {
        sessionId: "ses_history_question_a",
        sessions: [{ rootSessionId: "ses_history_question_a", sessionId: "ses_history_question_a", childSession: false }],
        messagesBySessionId: {},
        childSessionIdByTaskPartId: {},
        events: []
      },
      ses_history_question_b: {
        sessionId: "ses_history_question_b",
        sessions: [{ rootSessionId: "ses_history_question_b", sessionId: "ses_history_question_b", childSession: false }],
        messagesBySessionId: {},
        childSessionIdByTaskPartId: {},
        events: []
      }
    },
    sessionQuestionsById: {
      ses_history_question_a: [{
        id: "que_history_question_a",
        sessionID: "ses_history_question_a",
        questions: [{ question: "只属于 A 的问题", header: "范围", options: [{ label: "A" }], multiple: false }]
      }]
    }
  });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: "消息列表" }).click();
  await page.getByRole("button", { name: /A 会话有提问/ }).click();
  await expect(page.locator(".figma-chat-question-dock")).toContainText("只属于 A 的问题");
  await page.getByRole("button", { name: "消息列表" }).click();
  await page.getByRole("button", { name: "收起进程状态" }).click();
  await page.getByRole("button", { name: /B 会话无提问/ }).click({ force: true });
  await expect.poll(() => sessionMessageRequests).toContain(
    "/api/internal/platform/opencode-runtime/sessions/ses_history_question_b/messages?page=1&size=100&refresh=false"
  );
  await expect(page.locator(".figma-chat-question-dock")).toHaveCount(0);
  await expect(page.getByText("只属于 A 的问题")).toHaveCount(0);
});

test("switching to a running history maps its remote question event and allows reply", async ({ page }) => {
  const questionReplies: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, {
    questionReplies,
    sessions: [{
      sessionId: "ses_history_running",
      workspaceId: "wrk_1234567890abcdef",
      title: "运行中的历史提问",
      status: "ACTIVE",
      createdAt: "2026-07-11T08:00:00Z",
      updatedAt: "2026-07-11T08:01:00Z"
    }],
    sessionMessagesBySessionId: {
      ses_history_running: [{
        messageId: "msg_history_running",
        sessionId: "ses_history_running",
        runId: "run_history",
        role: "ASSISTANT",
        content: "正在等待用户选择",
        createdAt: "2026-07-11T08:01:00Z",
        parts: []
      }]
    },
    sessionTreeMessagesBySessionId: {
      ses_history_running: {
        sessionId: "ses_history_running",
        sessions: [{ rootSessionId: "ses_history_running", sessionId: "ses_history_running", childSession: false }],
        messagesBySessionId: {},
        childSessionIdByTaskPartId: {},
        events: []
      }
    },
    // 历史运行中的交互必须同时存在于当前 OpenCode pending 快照；旧 Event 单独回放不再伪造可回复弹框。
    sessionQuestionsById: {
      ses_history_running: [{
        id: "que_history_running",
        sessionID: "ses_remote_history",
        questions: [{ question: "历史运行中：选择继续方式", options: [{ label: "继续" }, { label: "停止" }] }]
      }]
    },
    runtimeStateSummary: {
      runningCount: 1,
      questionCount: 1,
      sessions: [{
        sessionId: "ses_history_running",
        runId: "run_history",
        runStatus: "RUNNING",
        attention: "QUESTION",
        attentionEventId: "evt_remote_question",
        updatedAt: "2026-07-11T08:01:00Z"
      }],
      generatedAt: "2026-07-11T08:01:00Z"
    },
    historyRun: {
      runId: "run_history",
      sessionId: "ses_history_running",
      workspaceId: "wrk_1234567890abcdef",
      status: "RUNNING",
      createdAt: "2026-07-11T08:00:00Z",
      updatedAt: "2026-07-11T08:01:00Z"
    },
    runEventsByRunId: {
      run_history: [{
        ...event(1, "question.asked", {
          requestId: "que_history_running",
          sessionId: "ses_remote_history",
          questions: [{ question: "历史运行中：选择继续方式", options: [{ label: "继续" }, { label: "停止" }] }]
        }),
        runId: "run_history"
      }]
    }
  });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: "消息列表" }).click();
  await page.getByRole("button", { name: "运行中的历史提问" }).click();

  const dock = page.locator(".figma-chat-question-dock");
  await expect(dock).toContainText("历史运行中：选择继续方式");
  await dock.getByRole("button", { name: "继续", exact: true }).click();
  await dock.getByRole("button", { name: "提交", exact: true }).click();
  await expect.poll(() => questionReplies).toEqual([{ answers: [["继续"]] }]);
});

test("switching history resumes the runtime-state run and reconciles active-run in background", async ({ page }) => {
  const activeRunRequests: string[] = [];
  const runEventRequests: string[] = [];
  const runContextRequests: string[] = [];
  await mockBackendApi(page, {
    activeRunRequests,
    runEventRequests,
    runContextRequests,
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        appId: "app_gcms",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1"
      }
    },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    },
    sessions: [
      {
        sessionId: "ses_history",
        workspaceId: "wrk_1234567890abcdef",
        title: "/generate-cases-orthogonal 车贷",
        status: "ACTIVE",
        pinned: false,
        createdAt: "2026-06-28T08:00:00Z",
        updatedAt: "2026-06-28T08:01:00Z"
      }
    ],
    sessionMessages: [
      {
        messageId: "msg_user",
        sessionId: "ses_history",
        role: "USER",
        content: "/generate-cases-orthogonal 车贷",
        createdAt: "2026-06-28T08:00:00Z",
        runId: "run_history"
      }
    ],
    historyRun: {
      runId: "run_history",
      sessionId: "ses_history",
      workspaceId: "wrk_1234567890abcdef",
      status: "SUCCEEDED",
      createdAt: "2026-06-28T08:00:00Z",
      updatedAt: "2026-06-28T08:01:00Z"
    },
    runtimeStateSummary: {
      runningCount: 1,
      questionCount: 0,
      sessions: [{
        sessionId: "ses_history",
        runId: "run_1",
        runStatus: "RUNNING",
        attention: null,
        updatedAt: "2026-06-28T08:02:01Z"
      }],
      generatedAt: "2026-06-28T08:02:02Z"
    },
    runEvents: [
      event(1, "message.part.delta", {
        messageId: "msg_live",
        messageID: "msg_live",
        partId: "part_text",
        partID: "part_text",
        delta: "正交表实时输出"
      })
    ]
  });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: /消息列表/ }).click();
  await page.getByRole("button", { name: /generate-cases-orthogonal/ }).click();

  await expect.poll(() => runEventRequests).toContain("/api/internal/agent/opencode/runs/run_1/events");
  expect(activeRunRequests).toEqual(["/api/internal/platform/opencode-runtime/sessions/ses_history/active-run"]);
  expect(runContextRequests).toEqual(["ses_history"]);
  await expect(page.getByText("正交表实时输出")).toBeVisible();
});

test("runtime-state outage performs only one active-run fallback", async ({ page }) => {
  const activeRunRequests: string[] = [];
  const runtimeStateEventRequests: string[] = [];
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    activeRunRequests,
    runtimeStateEventRequests,
    runtimeStateStreamFailure: true,
    sessions: [{
      sessionId: "ses_history",
      workspaceId: "wrk_1234567890abcdef",
      title: "恢复中的会话",
      status: "ACTIVE",
      pinned: false,
      createdAt: "2026-06-28T08:00:00Z",
      updatedAt: "2026-06-28T08:01:00Z"
    }],
    activeRun: null
  });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: /消息列表/ }).click();
  await page.getByRole("button", { name: /恢复中的会话/ }).click();
  await expect.poll(() => runtimeStateEventRequests.length).toBeGreaterThanOrEqual(2);
  await page.waitForTimeout(3500);

  expect(activeRunRequests).toEqual(["/api/internal/platform/opencode-runtime/sessions/ses_history/active-run"]);
});

test("runtime-state outage falls back once for each switched session in the same outage", async ({ page }) => {
  const activeRunRequests: string[] = [];
  const runtimeStateEventRequests: string[] = [];
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    activeRunRequests,
    runtimeStateEventRequests,
    runtimeStateStreamFailure: true,
    sessions: [
      {
        sessionId: "ses_outage_a",
        workspaceId: "wrk_1234567890abcdef",
        title: "故障会话 A",
        status: "ACTIVE",
        pinned: false,
        createdAt: "2026-06-28T08:00:00Z",
        updatedAt: "2026-06-28T08:01:00Z"
      },
      {
        sessionId: "ses_outage_b",
        workspaceId: "wrk_1234567890abcdef",
        title: "故障会话 B",
        status: "ACTIVE",
        pinned: false,
        createdAt: "2026-06-28T08:02:00Z",
        updatedAt: "2026-06-28T08:03:00Z"
      }
    ],
    activeRun: null
  });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: /消息列表/ }).click();
  await page.getByRole("button", { name: "故障会话 A" }).click();
  await expect.poll(() => activeRunRequests).toContain(
    "/api/internal/platform/opencode-runtime/sessions/ses_outage_a/active-run"
  );

  await page.getByRole("button", { name: /消息列表/ }).click();
  await page.getByRole("button", { name: "故障会话 B" }).click();
  await expect.poll(() => activeRunRequests).toContain(
    "/api/internal/platform/opencode-runtime/sessions/ses_outage_b/active-run"
  );
  await expect.poll(() => runtimeStateEventRequests.length).toBeGreaterThanOrEqual(2);
  await page.waitForTimeout(3500);

  expect(activeRunRequests).toEqual([
    "/api/internal/platform/opencode-runtime/sessions/ses_outage_a/active-run",
    "/api/internal/platform/opencode-runtime/sessions/ses_outage_b/active-run"
  ]);
});

test("a delayed history switch cannot overwrite a newer session and workspace", async ({ page }) => {
  let releaseWorkspaceA!: () => void;
  const workspaceAGate = new Promise<void>((resolve) => {
    releaseWorkspaceA = resolve;
  });
  const fileRequests: Array<{ workspaceId: string; path: string }> = [];
  const workspaceRequests: string[] = [];
  const runContextRequests: string[] = [];
  const sessionMessageRequests: string[] = [];
  const workspaceA = {
    ...workspace(),
    workspaceId: "wrk_race_a",
    name: "竞态工作区 A",
    rootPath: "/Users/huang/workspace/race-a",
    appId: "app_gcms"
  };
  const workspaceB = {
    ...workspace(),
    workspaceId: "wrk_race_b",
    name: "竞态工作区 B",
    rootPath: "/Users/huang/workspace/race-b",
    appId: "app_gcms"
  };
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    fileRequests,
    workspaceRequests,
    workspaceRequestGates: { wrk_race_a: workspaceAGate },
    runContextRequests,
    sessionMessageRequests,
    workspaces: [workspace(), workspaceA, workspaceB],
    markRecentWorkspaces: { wrk_race_a: workspaceA, wrk_race_b: workspaceB },
    sessions: [
      {
        sessionId: "ses_race_a",
        workspaceId: "wrk_race_a",
        title: "竞态会话 A",
        status: "ACTIVE",
        pinned: false,
        createdAt: "2026-07-10T02:00:00Z",
        updatedAt: "2026-07-10T02:01:00Z"
      },
      {
        sessionId: "ses_race_b",
        workspaceId: "wrk_race_b",
        title: "竞态会话 B",
        status: "ACTIVE",
        pinned: false,
        createdAt: "2026-07-10T02:02:00Z",
        updatedAt: "2026-07-10T02:03:00Z"
      }
    ],
    sessionMessagesBySessionId: {
      ses_race_a: [{
        messageId: "msg_race_a",
        sessionId: "ses_race_a",
        role: "ASSISTANT",
        content: "竞态正文 A",
        createdAt: "2026-07-10T02:01:00Z"
      }],
      ses_race_b: [{
        messageId: "msg_race_b",
        sessionId: "ses_race_b",
        role: "ASSISTANT",
        content: "竞态正文 B",
        createdAt: "2026-07-10T02:03:00Z"
      }]
    }
  });

  await gotoWorkbench(page);
  fileRequests.length = 0;
  await page.getByRole("button", { name: /消息列表/ }).click();
  await page.getByRole("button", { name: "竞态会话 A" }).click();
  await expect.poll(() => workspaceRequests).toContain("wrk_race_a");

  await page.getByRole("button", { name: /消息列表/ }).click();
  await page.getByRole("button", { name: "竞态会话 B" }).click();
  await expect(page.getByText("竞态正文 B")).toBeVisible();
  releaseWorkspaceA();
  await page.waitForTimeout(200);

  expect(runContextRequests).toEqual(["ses_race_b"]);
  expect(sessionMessageRequests).toEqual([
    "/api/internal/platform/opencode-runtime/sessions/ses_race_b/messages?page=1&size=100&refresh=false"
  ]);
  expect(fileRequests).toContainEqual({ workspaceId: "wrk_race_b", path: "" });
  expect(fileRequests).not.toContainEqual({ workspaceId: "wrk_race_a", path: "" });
  await expect(page.getByText("竞态正文 B")).toBeVisible();
  await expect(page.getByText("竞态正文 A")).toHaveCount(0);
});

test("history loading cannot send a run to the previous session", async ({ page }) => {
  let releaseTargetWorkspace!: () => void;
  const targetWorkspaceGate = new Promise<void>((resolve) => {
    releaseTargetWorkspace = resolve;
  });
  const runRequests: Array<Record<string, unknown>> = [];
  const workspaceRequests: string[] = [];
  const previousWorkspace = {
    ...workspace(),
    workspaceId: "wrk_history_send_previous",
    name: "发送保护旧工作区",
    rootPath: "/Users/huang/workspace/history-send-previous",
    appId: "app_gcms"
  };
  const targetWorkspace = {
    ...workspace(),
    workspaceId: "wrk_history_send_target",
    name: "发送保护目标工作区",
    rootPath: "/Users/huang/workspace/history-send-target",
    appId: "app_gcms"
  };
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    runRequests,
    workspaceRequests,
    workspaceRequestGates: { wrk_history_send_target: targetWorkspaceGate },
    workspaces: [workspace(), previousWorkspace, targetWorkspace],
    markRecentWorkspaces: {
      wrk_history_send_previous: previousWorkspace,
      wrk_history_send_target: targetWorkspace
    },
    sessions: [
      {
        sessionId: "ses_history_send_previous",
        workspaceId: "wrk_history_send_previous",
        title: "发送保护旧会话",
        status: "ACTIVE",
        pinned: false,
        createdAt: "2026-07-10T02:04:00Z",
        updatedAt: "2026-07-10T02:05:00Z"
      },
      {
        sessionId: "ses_history_send_target",
        workspaceId: "wrk_history_send_target",
        title: "发送保护目标会话",
        status: "ACTIVE",
        pinned: false,
        createdAt: "2026-07-10T02:06:00Z",
        updatedAt: "2026-07-10T02:07:00Z"
      }
    ],
    sessionMessagesBySessionId: {
      ses_history_send_previous: [{
        messageId: "msg_history_send_previous",
        sessionId: "ses_history_send_previous",
        role: "ASSISTANT",
        content: "发送保护旧正文",
        createdAt: "2026-07-10T02:05:00Z"
      }],
      ses_history_send_target: [{
        messageId: "msg_history_send_target",
        sessionId: "ses_history_send_target",
        role: "ASSISTANT",
        content: "发送保护目标正文",
        createdAt: "2026-07-10T02:07:00Z"
      }]
    }
  });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: /消息列表/ }).click();
  await page.getByRole("button", { name: "发送保护旧会话" }).click();
  await expect(page.getByText("发送保护旧正文")).toBeVisible();

  const composer = page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因");
  await composer.fill("切换中不得发送");
  await page.getByRole("button", { name: /消息列表/ }).click();
  await page.getByRole("button", { name: "发送保护目标会话" }).click();
  await expect.poll(() => workspaceRequests).toContain("wrk_history_send_target");

  const sendButton = page.getByRole("button", { name: "发送" });
  await expect(sendButton).toBeDisabled();
  await sendButton.click({ force: true });
  await page.waitForTimeout(100);
  expect(runRequests).toEqual([]);

  releaseTargetWorkspace();
  await expect(page.getByText("发送保护目标正文")).toBeVisible();
  expect(runRequests).toEqual([]);
});

test("a delayed history switch cannot survive a new conversation", async ({ page }) => {
  let releaseHistoryWorkspace!: () => void;
  const historyWorkspaceGate = new Promise<void>((resolve) => {
    releaseHistoryWorkspace = resolve;
  });
  const runContextRequests: string[] = [];
  const runRequests: Array<Record<string, unknown>> = [];
  const sessionRequests: Array<Record<string, unknown>> = [];
  const sessionMessageRequests: string[] = [];
  const fileRequests: Array<{ workspaceId: string; path: string }> = [];
  const historyWorkspace = {
    ...workspace(),
    workspaceId: "wrk_history_new_conversation",
    name: "新对话竞态工作区",
    rootPath: "/Users/huang/workspace/history-new-conversation",
    appId: "app_gcms"
  };
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    runContextRequests,
    runRequests,
    sessionRequests,
    sessionMessageRequests,
    fileRequests,
    workspaceRequestGates: { wrk_history_new_conversation: historyWorkspaceGate },
    workspaces: [workspace(), historyWorkspace],
    markRecentWorkspaces: { wrk_history_new_conversation: historyWorkspace },
    sessions: [{
      sessionId: "ses_history_new_conversation",
      workspaceId: "wrk_history_new_conversation",
      title: "等待后新建对话",
      status: "ACTIVE",
      pinned: false,
      createdAt: "2026-07-10T02:10:00Z",
      updatedAt: "2026-07-10T02:11:00Z"
    }],
    sessionMessagesBySessionId: {
      ses_history_new_conversation: [{
        messageId: "msg_history_new_conversation",
        sessionId: "ses_history_new_conversation",
        role: "ASSISTANT",
        content: "不应恢复的新对话正文",
        createdAt: "2026-07-10T02:11:00Z"
      }]
    }
  });

  await gotoWorkbench(page);
  fileRequests.length = 0;
  await page.getByRole("button", { name: /消息列表/ }).click();
  await page.getByRole("button", { name: "等待后新建对话" }).click();
  await page.getByRole("button", { name: "新建对话" }).click();
  releaseHistoryWorkspace();
  await page.waitForTimeout(200);

  expect(runContextRequests).not.toContain("ses_history_new_conversation");
  expect(sessionMessageRequests).not.toContain(
    "/api/internal/platform/opencode-runtime/sessions/ses_history_new_conversation/messages?page=1&size=100&refresh=false"
  );
  expect(fileRequests).not.toContainEqual({ workspaceId: "wrk_history_new_conversation", path: "" });
  await expect(page.getByText("不应恢复的新对话正文")).toHaveCount(0);

  const composer = page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因");
  const sendButton = page.getByRole("button", { name: "发送" });
  await composer.fill("新对话恢复发送");
  await expect(sendButton).toBeEnabled();
  await sendButton.click();
  await expect.poll(() => runRequests.length).toBe(1);

  expect(sessionRequests).toHaveLength(1);
  expect(sessionRequests[0]).toMatchObject({ workspaceId: "wrk_personal_default" });
  expect(runContextRequests).toEqual(["ses_1"]);
  expect(runRequests[0]).toMatchObject({ sessionId: "ses_1", prompt: "新对话恢复发送" });
});

test("a delayed history switch cannot overwrite a manual application workspace switch", async ({ page }) => {
  let releaseHistoryWorkspace!: () => void;
  const historyWorkspaceGate = new Promise<void>((resolve) => {
    releaseHistoryWorkspace = resolve;
  });
  const runContextRequests: string[] = [];
  const runRequests: Array<Record<string, unknown>> = [];
  const sessionRequests: Array<Record<string, unknown>> = [];
  const sessionMessageRequests: string[] = [];
  const fileRequests: Array<{ workspaceId: string; path: string }> = [];
  const historyWorkspace = {
    ...workspace(),
    workspaceId: "wrk_history_manual_switch",
    name: "迟到历史工作区",
    rootPath: "/Users/huang/workspace/history-manual-switch",
    appId: "app_gcms"
  };
  const cossWorkspace = {
    ...workspace(),
    workspaceId: "wrk_coss_manual_switch",
    name: "COSS 手动工作区",
    rootPath: "/Users/huang/workspace/coss-manual-switch",
    appId: "app_coss",
    versionId: "awv_coss_manual",
    applicationWorkspaceId: "awp_coss_manual"
  };
  await mockBackendApi(page, {
    fileRequests,
    runContextRequests,
    runRequests,
    sessionRequests,
    sessionMessageRequests,
    applications: [
      { appId: "app_gcms", appName: "F-GCMS", enabled: true },
      { appId: "app_coss", appName: "F-COSS", enabled: true }
    ],
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        appId: "app_gcms",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1"
      },
      app_coss: cossWorkspace
    },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")],
      awv_coss_manual: [{
        ...defaultPersonalWorkspace("awv_coss_manual"),
        appId: "app_coss",
        applicationWorkspaceId: "awp_coss_manual",
        runtimeWorkspace: cossWorkspace
      }]
    },
    workspaceRequestGates: { wrk_history_manual_switch: historyWorkspaceGate },
    workspaces: [workspace(), historyWorkspace, cossWorkspace],
    markRecentWorkspaces: {
      wrk_history_manual_switch: historyWorkspace,
      wrk_coss_manual_switch: cossWorkspace
    },
    sessions: [{
      sessionId: "ses_history_manual_switch",
      workspaceId: "wrk_history_manual_switch",
      title: "等待手动切工作区",
      status: "ACTIVE",
      pinned: false,
      createdAt: "2026-07-10T02:20:00Z",
      updatedAt: "2026-07-10T02:21:00Z"
    }]
  });

  await gotoWorkbench(page);
  fileRequests.length = 0;
  await page.getByRole("button", { name: /消息列表/ }).click();
  await page.getByRole("button", { name: "等待手动切工作区" }).click();

  await page.getByRole("button", { name: "F-GCMS" }).click();
  await page.getByRole("option", { name: /F-COSS/ }).click();
  await expect.poll(() => fileRequests).toContainEqual({ workspaceId: "wrk_coss_manual_switch", path: "" });
  releaseHistoryWorkspace();
  await page.waitForTimeout(200);

  expect(runContextRequests).not.toContain("ses_history_manual_switch");
  expect(sessionMessageRequests).not.toContain(
    "/api/internal/platform/opencode-runtime/sessions/ses_history_manual_switch/messages?page=1&size=100&refresh=false"
  );
  expect(fileRequests).not.toContainEqual({ workspaceId: "wrk_history_manual_switch", path: "" });
  await expect(page.getByRole("button", { name: "F-COSS" })).toBeVisible();

  const composer = page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因");
  const sendButton = page.getByRole("button", { name: "发送" });
  await composer.fill("新工作区恢复发送");
  await expect(sendButton).toBeEnabled();
  await sendButton.click();
  await expect.poll(() => runRequests.length).toBe(1);

  expect(sessionRequests).toHaveLength(1);
  expect(sessionRequests[0]).toMatchObject({ workspaceId: "wrk_coss_manual_switch" });
  expect(runContextRequests).toEqual(["ses_1"]);
  expect(runRequests[0]).toMatchObject({ sessionId: "ses_1", prompt: "新工作区恢复发送" });
});

test("a delayed history switch cannot survive an authentication change", async ({ page }) => {
  let releaseHistoryWorkspace!: () => void;
  const historyWorkspaceGate = new Promise<void>((resolve) => {
    releaseHistoryWorkspace = resolve;
  });
  const runContextRequests: string[] = [];
  const sessionMessageRequests: string[] = [];
  const logoutRequests: string[] = [];
  const historyWorkspace = {
    ...workspace(),
    workspaceId: "wrk_history_auth_change",
    name: "认证竞态工作区",
    rootPath: "/Users/huang/workspace/history-auth-change",
    appId: "app_gcms"
  };
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    logoutRequests,
    runContextRequests,
    sessionMessageRequests,
    workspaceRequestGates: { wrk_history_auth_change: historyWorkspaceGate },
    workspaces: [workspace(), historyWorkspace],
    markRecentWorkspaces: { wrk_history_auth_change: historyWorkspace },
    sessions: [{
      sessionId: "ses_history_auth_change",
      workspaceId: "wrk_history_auth_change",
      title: "等待认证变化",
      status: "ACTIVE",
      pinned: false,
      createdAt: "2026-07-10T02:30:00Z",
      updatedAt: "2026-07-10T02:31:00Z"
    }]
  });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: /消息列表/ }).click();
  await page.getByRole("button", { name: "等待认证变化" }).click();
  await page.getByRole("button", { name: /当前用户/ }).click();
  await page.getByRole("menuitem", { name: "退出登录" }).click();
  await expect.poll(() => logoutRequests).toEqual(["POST /api/auth/logout"]);
  releaseHistoryWorkspace();
  await page.waitForTimeout(200);

  expect(runContextRequests).not.toContain("ses_history_auth_change");
  expect(sessionMessageRequests).not.toContain(
    "/api/internal/platform/opencode-runtime/sessions/ses_history_auth_change/messages?page=1&size=100&refresh=false"
  );
});

test("a delayed conversation context cannot dispatch after switching history", async ({ page }) => {
  let releaseContextA!: () => void;
  const contextAGate = new Promise<void>((resolve) => {
    releaseContextA = resolve;
  });
  const runRequests: Array<Record<string, unknown>> = [];
  const runContextRequests: string[] = [];
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    runRequests,
    runContextRequests,
    runContextRequestGates: { ses_context_a: contextAGate },
    sessions: [
      {
        sessionId: "ses_context_a",
        workspaceId: "wrk_1234567890abcdef",
        title: "上下文会话 A",
        status: "ACTIVE",
        pinned: false,
        createdAt: "2026-07-10T03:00:00Z",
        updatedAt: "2026-07-10T03:01:00Z"
      },
      {
        sessionId: "ses_context_b",
        workspaceId: "wrk_1234567890abcdef",
        title: "上下文会话 B",
        status: "ACTIVE",
        pinned: false,
        createdAt: "2026-07-10T03:02:00Z",
        updatedAt: "2026-07-10T03:03:00Z"
      }
    ],
    sessionMessagesBySessionId: {
      ses_context_a: [{
        messageId: "msg_context_a",
        sessionId: "ses_context_a",
        role: "ASSISTANT",
        content: "上下文正文 A",
        createdAt: "2026-07-10T03:01:00Z"
      }],
      ses_context_b: [{
        messageId: "msg_context_b",
        sessionId: "ses_context_b",
        role: "ASSISTANT",
        content: "上下文正文 B",
        createdAt: "2026-07-10T03:03:00Z"
      }]
    }
  });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: /消息列表/ }).click();
  await page.getByRole("button", { name: "上下文会话 A" }).click();
  await expect(page.getByText("上下文正文 A")).toBeVisible();
  await expect.poll(() => runContextRequests).toContain("ses_context_a");

  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("等待上下文");
  await page.getByRole("button", { name: "发送" }).click();
  await page.getByRole("button", { name: /消息列表/ }).click();
  await page.getByRole("button", { name: "上下文会话 B" }).click();
  await expect(page.getByText("上下文正文 B")).toBeVisible();
  releaseContextA();
  await page.waitForTimeout(200);

  expect(runRequests).toEqual([]);
  await expect(page.getByText("上下文正文 B")).toBeVisible();
});

test("a delayed startRun response cannot replace a newer history session", async ({ page }) => {
  let releaseRunRequest!: () => void;
  const runRequestGate = new Promise<void>((resolve) => {
    releaseRunRequest = resolve;
  });
  const runRequests: Array<Record<string, unknown>> = [];
  const runEventRequests: string[] = [];
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    runRequestGate,
    runRequests,
    runEventRequests,
    sessions: [
      {
        sessionId: "ses_start_a",
        workspaceId: "wrk_1234567890abcdef",
        title: "启动会话 A",
        status: "ACTIVE",
        pinned: false,
        createdAt: "2026-07-10T04:00:00Z",
        updatedAt: "2026-07-10T04:01:00Z"
      },
      {
        sessionId: "ses_start_b",
        workspaceId: "wrk_1234567890abcdef",
        title: "启动会话 B",
        status: "ACTIVE",
        pinned: false,
        createdAt: "2026-07-10T04:02:00Z",
        updatedAt: "2026-07-10T04:03:00Z"
      }
    ],
    sessionMessagesBySessionId: {
      ses_start_a: [{
        messageId: "msg_start_a",
        sessionId: "ses_start_a",
        role: "ASSISTANT",
        content: "启动正文 A",
        createdAt: "2026-07-10T04:01:00Z"
      }],
      ses_start_b: [{
        messageId: "msg_start_b",
        sessionId: "ses_start_b",
        role: "ASSISTANT",
        content: "启动正文 B",
        createdAt: "2026-07-10T04:03:00Z"
      }]
    }
  });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: /消息列表/ }).click();
  await page.getByRole("button", { name: "启动会话 A" }).click();
  await expect(page.getByText("启动正文 A")).toBeVisible();

  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("等待启动结果");
  await page.getByRole("button", { name: "发送" }).click();
  await expect.poll(() => runRequests.length).toBe(1);
  await page.getByRole("button", { name: /消息列表/ }).click();
  await page.getByRole("button", { name: "启动会话 B" }).click();
  await expect(page.getByText("启动正文 B")).toBeVisible();
  releaseRunRequest();
  await page.waitForTimeout(200);

  expect(runEventRequests).not.toContain("/api/internal/agent/opencode/runs/run_1/events");
  await expect(page.getByText("启动正文 B")).toBeVisible();
});

test("an ambiguous startRun failure does not fail a run recovered by runtime-state", async ({ page }) => {
  let releaseRuntimeState!: () => void;
  let releaseRunRequest!: () => void;
  const runtimeStateEventGate = new Promise<void>((resolve) => {
    releaseRuntimeState = resolve;
  });
  const runRequestGate = new Promise<void>((resolve) => {
    releaseRunRequest = resolve;
  });
  const runRequests: Array<Record<string, unknown>> = [];
  const runEventRequests: string[] = [];
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    runtimeStateEventGate,
    runRequestGate,
    runRequests,
    runEventRequests,
    runFailureResponses: [{ status: 504, code: "OPENCODE_TIMEOUT", message: "启动确认超时" }],
    runtimeStateSummary: {
      runningCount: 1,
      questionCount: 0,
      sessions: [{
        sessionId: "ses_1",
        runId: "run_runtime_recovered",
        runStatus: "RUNNING",
        attention: null,
        updatedAt: "2026-07-10T05:01:00Z"
      }],
      generatedAt: "2026-07-10T05:01:01Z"
    },
    runEventsByRunId: { run_runtime_recovered: [] }
  });

  await gotoWorkbench(page);
  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("超时但已受理");
  await page.getByRole("button", { name: "发送" }).click();
  await expect.poll(() => runRequests.length).toBe(1);

  releaseRuntimeState();
  await expect.poll(() => runEventRequests).toContain(
    "/api/internal/agent/opencode/runs/run_runtime_recovered/events"
  );
  releaseRunRequest();
  await page.waitForTimeout(200);

  await expect(page.locator(".figma-chat-retry-card")).toHaveCount(0);
  await expect(page.getByText("启动 Run 失败")).toHaveCount(0);
  await expect(page.getByText("启动确认超时")).toHaveCount(0);
});

test("history loading does not wait for interaction snapshot or message feedback", async ({ page }) => {
  let releaseSessionMessages!: () => void;
  let releaseSessionInteractions!: () => void;
  let releaseMessageFeedback!: () => void;
  const sessionMessagesGate = new Promise<void>((resolve) => {
    releaseSessionMessages = resolve;
  });
  const messageFeedbackGate = new Promise<void>((resolve) => {
    releaseMessageFeedback = resolve;
  });
  const sessionInteractionsGate = new Promise<void>((resolve) => {
    releaseSessionInteractions = resolve;
  });
  const feedbackRequests: string[] = [];
  const sessionTreeRequests: string[] = [];
  const sessionMessageRequests: string[] = [];

  await mockBackendApi(page, {
    sessionTreeRequests,
    sessionMessageRequests,
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        appId: "app_gcms",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1"
      }
    },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    },
    sessions: [
      {
        sessionId: "ses_history",
        workspaceId: "wrk_1234567890abcdef",
        title: "历史加载测试",
        status: "ACTIVE",
        pinned: false,
        createdAt: "2026-06-28T08:00:00Z",
        updatedAt: "2026-06-28T08:01:00Z"
      }
    ],
    sessionMessages: [
      {
        messageId: "msg_1234567890abcdef1234567890abcdef",
        sessionId: "ses_history",
        role: "ASSISTANT",
        content: "历史正文已加载",
        createdAt: "2026-06-28T08:01:00Z"
      },
      {
        messageId: "msg_2234567890abcdef1234567890abcdef",
        sessionId: "ses_history",
        role: "ASSISTANT",
        content: "历史正文已加载",
        createdAt: "2026-06-28T08:01:01Z"
      }
    ],
    sessionTreeMessages: {
      sessionId: "ses_history",
      sessions: [{ rootSessionId: "ses_history", sessionId: "ses_history", childSession: false }],
      messagesBySessionId: {},
      childSessionIdByTaskPartId: {},
      events: [
        {
          type: "message.updated",
          rootSessionId: "ses_history",
          sessionId: "ses_history",
          childSession: false,
          payload: {
            rootSessionId: "ses_history",
            sessionId: "ses_history",
            message: { id: "remote_dup", role: "assistant", content: "历史正文已加载" }
          }
        },
        {
          type: "message.updated",
          rootSessionId: "ses_history",
          sessionId: "ses_history",
          childSession: false,
          payload: {
            rootSessionId: "ses_history",
            sessionId: "ses_history",
            message: { id: "remote_dup", role: "assistant", content: "历史正文已加载" }
          }
        }
      ]
    },
    sessionMessagesGate,
    sessionInteractionsGate,
    messageFeedbackGate,
    feedbackRequests
  });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: "消息列表" }).click();
  await page.getByRole("button", { name: /历史加载测试/ }).click();

  await expect(page.getByText("正在加载消息列表…")).toBeVisible();

  releaseSessionMessages();
  await expect(page.getByText("历史正文已加载")).toHaveCount(1);
  await expect(page.getByText("正在加载消息列表…")).toHaveCount(0);
  const composer = page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因");
  await composer.fill("等待历史交互快照完成后发送");
  await expect(page.getByRole("button", { name: "发送" })).toBeDisabled();

  releaseSessionInteractions();
  await expect.poll(() => sessionTreeRequests).toContain("/api/internal/agent/opencode/sessions/ses_history/session-tree/messages");
  await expect.poll(() => sessionMessageRequests).toContain("/api/internal/platform/opencode-runtime/sessions/ses_history/messages?page=1&size=100&refresh=false");
  await expect.poll(() => feedbackRequests).toEqual([
    "/api/internal/platform/opencode-runtime/messages/msg_1234567890abcdef1234567890abcdef/feedback/me"
  ]);
  await expect(page.getByRole("button", { name: "发送" })).toBeEnabled();

  releaseMessageFeedback();
});

test("switching history changes to the session application and workspace", async ({ page }) => {
  const fileRequests: Array<{ workspaceId: string; path: string }> = [];
  const markRecentRequests: string[] = [];
  const historyWorkspace = {
    ...workspace(),
    workspaceId: "wrk_history_coss",
    name: "F-COSS 历史工作区",
    rootPath: "/Users/huang/workspace/history-coss",
    appId: "app_coss",
    versionId: "awv_coss",
    applicationWorkspaceId: "awp_coss"
  };
  await mockBackendApi(page, {
    fileRequests,
    markRecentRequests,
    applications: [
      { appId: "app_gcms", appName: "F-GCMS", enabled: true },
      { appId: "app_coss", appName: "F-COSS", enabled: true }
    ],
    workspaces: [workspace(), historyWorkspace],
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        appId: "app_gcms",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1"
      }
    },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    },
    markRecentWorkspaces: {
      wrk_history_coss: historyWorkspace
    },
    sessions: [
      {
        sessionId: "ses_history_coss",
        workspaceId: "wrk_history_coss",
        title: "COSS 历史会话",
        status: "ACTIVE",
        pinned: false,
        createdAt: "2026-07-08T08:00:00Z",
        updatedAt: "2026-07-08T09:00:00Z",
        workspaceContext: {
          appId: "app_coss",
          appName: "F-COSS",
          applicationWorkspaceId: "awp_coss",
          workspaceName: "COSS 主干",
          versionId: "awv_coss",
          version: "20260708"
        }
      }
    ],
    sessionMessages: [
      {
        messageId: "msg_history_coss",
        sessionId: "ses_history_coss",
        role: "USER",
        content: "COSS 历史会话",
        createdAt: "2026-07-08T08:00:00Z"
      }
    ]
  });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: "消息列表" }).click();
  await expect(page.getByText("F-COSS · COSS 主干 · 20260708")).toBeVisible();
  await page.getByRole("button", { name: /COSS 历史会话/ }).click();

  await expect.poll(() => markRecentRequests).toContain("wrk_history_coss");
  await expect(page.getByRole("button", { name: "F-COSS" })).toBeVisible();
  await expect.poll(() => fileRequests).toContainEqual({ workspaceId: "wrk_history_coss", path: "" });
});

test("history switch failure keeps current context and makes the session readonly", async ({ page }) => {
  const fileRequests: Array<{ workspaceId: string; path: string }> = [];
  const markRecentRequests: string[] = [];
  const forbiddenWorkspace = {
    ...workspace(),
    workspaceId: "wrk_forbidden_coss",
    name: "F-COSS 已失效工作区",
    rootPath: "/Users/huang/workspace/forbidden-coss",
    appId: "app_coss",
    versionId: "awv_forbidden",
    applicationWorkspaceId: "awp_coss"
  };
  await mockBackendApi(page, {
    fileRequests,
    markRecentRequests,
    applications: [
      { appId: "app_gcms", appName: "F-GCMS", enabled: true },
      { appId: "app_coss", appName: "F-COSS", enabled: true }
    ],
    workspaces: [workspace(), forbiddenWorkspace],
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        appId: "app_gcms",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1"
      }
    },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    },
    markRecentFailures: {
      wrk_forbidden_coss: { code: "FORBIDDEN", message: "无该应用工作区权限" }
    },
    sessions: [
      {
        sessionId: "ses_forbidden_coss",
        workspaceId: "wrk_forbidden_coss",
        title: "失效应用历史",
        status: "ACTIVE",
        pinned: false,
        createdAt: "2026-07-08T08:00:00Z",
        updatedAt: "2026-07-08T09:00:00Z",
        workspaceContext: {
          appId: "app_coss",
          appName: "F-COSS",
          applicationWorkspaceId: "awp_coss",
          workspaceName: "COSS 主干",
          versionId: "awv_forbidden",
          version: "20260708"
        }
      }
    ],
    sessionMessages: [
      {
        messageId: "msg_forbidden_coss",
        sessionId: "ses_forbidden_coss",
        role: "ASSISTANT",
        content: "只读历史正文",
        createdAt: "2026-07-08T08:00:00Z"
      }
    ]
  });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: "消息列表" }).click();
  await page.getByRole("button", { name: /失效应用历史/ }).click();

  await expect.poll(() => markRecentRequests).toContain("wrk_forbidden_coss");
  await expect(page.getByRole("button", { name: "F-GCMS" })).toBeVisible();
  await expect.poll(() => fileRequests).not.toContainEqual({ workspaceId: "wrk_forbidden_coss", path: "" });
  await expect(page.getByText("只读历史正文")).toBeVisible();
  await expect(page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因")).toBeDisabled();
  await expect(page.locator(".figma-chat-send-card")).toHaveAttribute("title", "你已不属于该历史会话所属应用，当前会话只读。");
});

test("workbench disables chat until opencode process is initialized", async ({ page }) => {
  const processInitializations: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, { processStatus: "NEEDS_INITIALIZATION", processInitializations });

  await gotoWorkbench(page, { selectConversation: false });

  await expect(page.getByText("我还没有准备好运行进程，要现在帮你初始化吗？")).toBeVisible();
  await expect(page.getByRole("button", { name: "发送" })).toBeDisabled();
  await page.getByRole("button", { name: "初始化进程" }).click();

  await expect.poll(() => processInitializations.length).toBe(1);
  await expect(page.getByText("TestAgent 进程可用").first()).toBeVisible();
  await page.getByRole("button", { name: "新建对话" }).click();
  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("run after init");
  await expect(page.getByRole("button", { name: "发送" })).toBeEnabled();
});

test("workbench refetches opencode status when initialize returns a stale failure", async ({ page }) => {
  const processInitializations: Array<Record<string, unknown>> = [];
  const processStatusRequests: string[] = [];
  await mockBackendApi(page, {
    processStatus: "NEEDS_INITIALIZATION",
    processInitializations,
    processStatusRequests,
    initializeFailureThenReady: true
  });

  await gotoWorkbench(page, { selectConversation: false });

  await page.getByRole("button", { name: "初始化进程" }).click();

  await expect.poll(() => processInitializations.length).toBe(1);
  await expect.poll(() => processStatusRequests.length).toBeGreaterThanOrEqual(2);
  await expect(page.getByText("TestAgent 进程可用").first()).toBeVisible();
  await expect(page.getByText("初始化 TestAgent 进程失败")).toHaveCount(0);
});

test("workbench does not create default personal workspace while opencode becomes ready", async ({ page }) => {
  const fileRequests: Array<{ workspaceId: string; path: string }> = [];
  const defaultPersonalRequests: string[] = [];
  const personalWorkspaceRequests: string[] = [];
  const processInitializations: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, {
    processStatus: "NEEDS_INITIALIZATION",
    ensureDefaultRequiresReady: true,
    fileRequests,
    defaultPersonalRequests,
    personalWorkspaceRequests,
    processInitializations,
    personalWorkspaces: {
      awv_20260715: []
    },
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        workspaceId: "wrk_app_replica",
        name: "F-GCMS 报表 / 20260715",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1",
        appId: "app_gcms"
      }
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  await expect.poll(() => personalWorkspaceRequests).toEqual(["awv_20260715"]);
  expect(defaultPersonalRequests).toEqual([]);
  expect(fileRequests).toEqual([]);
  await expect(page.getByText("当前应用尚未切换到可用工作区。")).toBeVisible();

  await page.getByRole("button", { name: "初始化进程" }).click();

  await expect.poll(() => processInitializations.length).toBe(1);
  expect(defaultPersonalRequests).toEqual([]);
  expect(fileRequests).toEqual([]);
  await expect(page.getByText("当前应用尚未切换到可用工作区。")).toBeVisible();
});

test("workbench disables chat until a conversation is selected", async ({ page }) => {
  await mockBackendApi(page);

  await gotoWorkbench(page, { selectConversation: false });

  const composer = page.locator(".figma-chat-input-card");
  const textarea = page.getByPlaceholder("请先从消息列表选择对话，或新建对话");
  await expect(composer).toHaveClass(/is-disabled/);
  await expect(textarea).toBeDisabled();
  await expect(page.getByRole("button", { name: "发送" })).toBeDisabled();
  await expect(page.getByRole("button", { name: "新建对话" })).toBeEnabled();

  await page.getByRole("button", { name: "新建对话" }).click();

  await expect(composer).not.toHaveClass(/is-disabled/);
  await expect(page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因")).toBeEnabled();
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
  await page.locator(".oc-diff-summary__header").click();
  await expect(page.getByText("+1,2")).toBeVisible();
  await page.getByTitle("引用 hunk").click();
  await expect(page.getByRole("main").getByText("已引用当前 hunk")).toBeVisible();

  await page.getByRole("button", { name: "打开运行与终端" }).click();
  const bottomDrawer = page.getByRole("region", { name: "运行与终端" });
  await expect(bottomDrawer).toBeVisible();
  await expect.poll(async () => (await bottomDrawer.boundingBox())?.y ?? Number.POSITIVE_INFINITY).toBeLessThan(766);
  await page.getByRole("button", { name: "终端", exact: true }).click();
  await page.getByRole("button", { name: "连接终端" }).click();
  await expect.poll(() => terminalTickets.length).toBe(1);
  expect(terminalTickets[0]).toEqual({ workspaceId: "wrk_1234567890abcdef", cols: 120, rows: 32 });
});

test("slash skill starts a recoverable run instead of a direct session command", async ({ page }) => {
  const runRequests: Array<Record<string, unknown>> = [];
  const commandRequests: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, { runRequests, commandRequests });

  await gotoWorkbench(page);

  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因")
    .fill("/generate-cases-path 对车贷的开发文档，生成路径图");
  await page.getByRole("button", { name: "发送" }).click();

  await expect.poll(() => runRequests.length).toBe(1);
  expect(runRequests[0]).toMatchObject({
    sessionId: "ses_1",
    prompt: "/generate-cases-path 对车贷的开发文档，生成路径图",
    command: "generate-cases-path",
    arguments: "对车贷的开发文档，生成路径图"
  });
  expect(commandRequests).toEqual([]);
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

test("workspace cascade menu teleports panel and submenu above all other UI", async ({ page }) => {
  // 模拟后端返回两个工作空间模板，每个模板下两个版本。
  // 验证：
  // 1) 一级菜单 Teleport 到 body + position:fixed，不被 dockview 面板的 overflow:hidden 裁切；
  // 2) 一级菜单 z-index 高于父级；
  // 3) hover 一级菜单项后，二级菜单 Teleport 到 body 出现在右侧；
  // 4) 菜单没有横向滚动条（max-width 不会越界）。
  await mockBackendApi(page, {
    workspaceTemplates: {
      app_gcms: [
        {
          workspaceId: "awp_main",
          workspaceName: "F-GCMS 主服务",
          appId: "app_gcms",
          repositoryId: "repo_1",
          defaultBranch: "main",
          createdAt: "2026-06-24T00:00:00Z",
          updatedAt: "2026-06-24T00:00:00Z"
        },
        {
          workspaceId: "awp_v1",
          workspaceName: "F-GCMS v1 灰度",
          appId: "app_gcms",
          repositoryId: "repo_1",
          defaultBranch: "main",
          createdAt: "2026-06-24T00:00:00Z",
          updatedAt: "2026-06-24T00:00:00Z"
        }
      ]
    },
    workspaceVersions: {
      "app_gcms:awp_main": [
        {
          versionId: "awv_2024_01",
          applicationWorkspaceId: "awp_main",
          appId: "app_gcms",
          repositoryId: "repo_1",
          version: "2024年1月",
          branch: "feature_testagent_2024-01",
          repoRootPath: "/tmp/test-agent/appworkspace/awp_main/repo_1",
          workspaceRootPath: "/tmp/test-agent/appworkspace/awp_main/repo_1/F-GCMS/workspace",
          status: "ACTIVE",
          createdAt: "2026-06-24T00:00:00Z",
          updatedAt: "2026-06-24T00:00:00Z"
        },
        {
          versionId: "awv_2024_06",
          applicationWorkspaceId: "awp_main",
          appId: "app_gcms",
          repositoryId: "repo_1",
          version: "2024年6月",
          branch: "feature_testagent_2024-06",
          repoRootPath: "/tmp/test-agent/appworkspace/awp_main/repo_1",
          workspaceRootPath: "/tmp/test-agent/appworkspace/awp_main/repo_1/F-GCMS/workspace",
          status: "ACTIVE",
          createdAt: "2026-06-24T00:00:00Z",
          updatedAt: "2026-06-24T00:00:00Z"
        }
      ]
    }
  });

  await gotoWorkbench(page);

  // 触发按钮：label 是 "F-GCMS 工作空间"（无 selected version）
  const trigger = page.locator(".ta-workbench-footer-branch");
  await expect(trigger).toBeVisible();
  await trigger.click();

  // 一级菜单面板：Teleport 到 body，position:fixed，有真实宽度
  const panel = page.locator(".ta-workbench-cascade-panel");
  await expect(panel).toBeVisible();
  const panelBox = await panel.boundingBox();
  expect(panelBox).not.toBeNull();
  expect(panelBox!.width).toBeGreaterThan(0);
  expect(panelBox!.height).toBeGreaterThan(0);
  // 一级菜单 y 小于按钮 y（菜单在按钮正上方）；这个特性是用户反馈"最上面"的关键。
  const buttonBox = await trigger.boundingBox();
  expect(buttonBox).not.toBeNull();
  expect(panelBox!.y).toBeLessThan(buttonBox!.y);

  // 没有横向滚动条：scrollWidth 应等于 clientWidth
  const panelScroll = await panel.evaluate((el) => ({
    scrollWidth: (el as HTMLElement).scrollWidth,
    clientWidth: (el as HTMLElement).clientWidth
  }));
  expect(panelScroll.scrollWidth).toBeLessThanOrEqual(panelScroll.clientWidth);

  // hover 一级菜单项 → 二级菜单 Teleport 到 body 出现在一级菜单右侧
  const firstItem = page.getByRole("menuitem", { name: /F-GCMS 主服务/ });
  await firstItem.hover();
  const submenu = page.locator(".ta-workbench-cascade-submenu");
  await expect(submenu).toBeVisible();
  const submenuBox = await submenu.boundingBox();
  expect(submenuBox).not.toBeNull();
  expect(submenuBox!.width).toBeGreaterThan(0);
  // 二级菜单 left 应当 >= 一级菜单的 right（出现在右侧）
  expect(submenuBox!.x).toBeGreaterThanOrEqual(panelBox!.x + panelBox!.width - 1);

  // 二级菜单里展示版本（Teleport 后依然可被 role=menuitem 检索到）
  await expect(page.getByRole("menuitem", { name: /2024年1月/ }).first()).toBeVisible();
  await expect(page.getByRole("menuitem", { name: /2024年6月/ }).first()).toBeVisible();
});

test("workspace cascade menu +新增版本 dialog opens with yyyy年M月 label", async ({ page }) => {
  await mockBackendApi(page, {
    workspaceTemplates: {
      app_gcms: [
        {
          workspaceId: "awp_main",
          workspaceName: "F-GCMS 主服务",
          appId: "app_gcms",
          repositoryId: "repo_1",
          defaultBranch: "main",
          createdAt: "2026-06-24T00:00:00Z",
          updatedAt: "2026-06-24T00:00:00Z"
        }
      ]
    },
    workspaceVersions: { "app_gcms:awp_main": [] }
  });

  await gotoWorkbench(page);

  // 打开一级菜单 → hover 模板 → 出现二级菜单
  const trigger = page.locator(".ta-workbench-footer-branch");
  await trigger.click();
  const firstItem = page.getByRole("menuitem", { name: /F-GCMS 主服务/ });
  await firstItem.hover();
  const submenu = page.locator(".ta-workbench-cascade-submenu");
  await expect(submenu).toBeVisible();

  // 点「+新增版本」打开 el-dialog
  await page.getByRole("menuitem", { name: /新增版本/ }).first().click();
  const dialog = page.locator(".el-dialog");
  await expect(dialog).toBeVisible();
  // 弹窗内标签明确告诉用户格式是 yyyy年M月
  await expect(dialog.getByText("选择月份（格式 yyyy年M月）")).toBeVisible();
  // el-date-picker 的占位符必须是 "请选择月份"（不能是 Element Plus 默认的 "yyyy-MM"）
  await expect(dialog.locator(".el-date-editor input")).toHaveAttribute("placeholder", "请选择月份");
  // 没选日期时确定按钮处于 disabled
  await expect(dialog.getByRole("button", { name: "确定" })).toBeDisabled();

  // 打开日期面板，验证月份显示中文"1月/2月/…"而不是英文"Jan/Feb/…"
  // （依赖 main.ts 里的 dayjs.locale("zh-cn") + 自定义 months locale 覆盖）
  await dialog.locator(".el-date-editor input").click();
  const monthPanel = page.locator(".el-month-table");
  await expect(monthPanel).toBeVisible();
  // 第一个月文案应该是"1月"（不是 Element Plus 默认 zh-cn 的"一月"或英文的"Jan"）
  await expect(monthPanel.getByText(/^1月$/).first()).toBeVisible();
  await expect(monthPanel.getByText(/^6月$/).first()).toBeVisible();
});

test("workspace cascade submenu shifts up when it would overflow the viewport bottom", async ({ page, isMobile }) => {
  test.skip(isMobile, "viewport math is desktop-specific in this mock");
  // 构造一个触发 li 接近视口底部的场景：模板多到面板能填满视口。
  const manyTemplates = Array.from({ length: 20 }).map((_, idx) => ({
    workspaceId: `awp_${idx}`,
    workspaceName: `F-COSS 模板 ${idx}`,
    appId: "app_gcms",
    repositoryId: "repo_1",
    defaultBranch: "main",
    createdAt: "2026-06-24T00:00:00Z",
    updatedAt: "2026-06-24T00:00:00Z"
  }));
  await mockBackendApi(page, {
    workspaceTemplates: { app_gcms: manyTemplates },
    workspaceVersions: {
      "app_gcms:awp_19": Array.from({ length: 15 }).map((_, idx) => ({
        versionId: `awv_${idx}`,
        applicationWorkspaceId: "awp_19",
        appId: "app_gcms",
        repositoryId: "repo_1",
        version: `2024年${idx + 1}月`,
        branch: `feature_testagent_2024-${String(idx + 1).padStart(2, "0")}`,
        repoRootPath: "/tmp/test-agent/appworkspace/awp_19/repo_1",
        workspaceRootPath: "/tmp/test-agent/appworkspace/awp_19/repo_1/F-COSS/workspace",
        status: "ACTIVE",
        createdAt: "2026-06-24T00:00:00Z",
        updatedAt: "2026-06-24T00:00:00Z"
      }))
    }
  });

  await gotoWorkbench(page);
  const trigger = page.locator(".ta-workbench-footer-branch");
  await trigger.click();

  // hover 最后一个 li（最接近视口底部），让子菜单自然位置会溢出
  const lastItem = page.getByRole("menuitem", { name: /F-COSS 模板 19/ });
  await lastItem.hover();

  const submenu = page.locator(".ta-workbench-cascade-submenu");
  await expect(submenu).toBeVisible();
  // 等一帧让 Vue 完成 reactive 周期
  await page.waitForTimeout(200);
  const submenuBox = await submenu.boundingBox();
  const viewport = page.viewportSize();
  expect(submenuBox).not.toBeNull();
  expect(viewport).not.toBeNull();
  // 子菜单底部必须 <= 视口高度（不能被底部遮挡）
  expect(submenuBox!.y + submenuBox!.height).toBeLessThanOrEqual(viewport!.height);
});

type PetStreamEvent = {
  eventId: string;
  type: string;
  payload: Record<string, unknown>;
  delayMs: number;
  disconnectAfter?: boolean;
};

function streamEvent(
  eventId: string,
  type: string,
  payload: Record<string, unknown>,
  delayMs: number,
  disconnectAfter = false
): PetStreamEvent {
  return { eventId, type, payload, delayMs, disconnectAfter };
}

async function installPetSideQuestionRunEventStream(page: Page, scenarios: Record<string, PetStreamEvent[]>) {
  await page.addInitScript(({ scenarios }) => {
    const reconnects: Array<{ runId: string; lastEventId: string }> = [];
    (window as Window & { __petSideQuestionReconnects?: typeof reconnects }).__petSideQuestionReconnects = reconnects;
    const nativeFetch = window.fetch.bind(window);
    window.fetch = async (input, init) => {
      const requestUrl = new URL(
        typeof input === "string" ? input : input instanceof Request ? input.url : input.toString(),
        window.location.origin
      );
      if (!requestUrl.pathname.includes("/runs/") || !requestUrl.pathname.endsWith("/events")) {
        return nativeFetch(input, init);
      }
      const runId = decodeURIComponent(requestUrl.pathname).match(/\/runs\/([^/]+)\/events/)?.[1] ?? "run_1";
      const events = scenarios[runId] ?? (runId === "run_1"
        ? [{ eventId: "evt_main_terminal", type: "run.succeeded", payload: {}, delayMs: 10 }]
        : []);
      const lastEventId = requestUrl.searchParams.get("lastEventId") ?? "";
      const resumeIndex = lastEventId ? events.findIndex((item) => item.eventId === lastEventId) + 1 : 0;
      if (lastEventId) {
        reconnects.push({ runId, lastEventId });
      }
      const selected = events.slice(Math.max(0, resumeIndex));
      const disconnectIndex = lastEventId ? -1 : selected.findIndex((item) => item.disconnectAfter);
      const batch = disconnectIndex < 0 ? selected : selected.slice(0, disconnectIndex + 1);
      const encoder = new TextEncoder();
      let timers: number[] = [];
      const body = new ReadableStream<Uint8Array>({
        start(controller) {
          batch.forEach((item, index) => {
            timers.push(window.setTimeout(() => {
              controller.enqueue(encoder.encode(
                `id: ${item.eventId}\nevent: ${item.type}\ndata: ${JSON.stringify({
                  eventId: item.eventId,
                  runId,
                  seq: resumeIndex + index + 1,
                  type: item.type,
                  traceId: "trace_pet_side_question",
                  occurredAt: "2026-07-11T00:00:00Z",
                  payload: item.payload
                })}\n\n`
              ));
              if (item.disconnectAfter) controller.close();
            }, item.delayMs));
          });
        },
        cancel() {
          timers.forEach((timer) => window.clearTimeout(timer));
          timers = [];
        }
      });
      return new Response(body, { headers: { "content-type": "text/event-stream" } });
    };
  }, { scenarios });
}

async function selectPetContextSession(page: Page) {
  await page.getByRole("button", { name: "消息列表" }).click();
  await page.getByRole("button", { name: /E2E Session/ }).click();
  await expect(page.locator(".figma-chat-title")).toHaveText("E2E Session");
}

async function openPetSideQuestion(page: Page) {
  await page.getByTestId("robot-visibility-toggle").click();
  await page.getByTestId("figma-robot").click();
  await expect(page.getByTestId("robot-side-question")).toBeVisible();
}

async function mockBackendApi(
  page: Page,
  capture: {
    runRequests?: Array<Record<string, unknown>>;
    commandRequests?: Array<Record<string, unknown>>;
    sessionRequests?: Array<Record<string, unknown>>;
    permissionReplies?: Array<Record<string, unknown>>;
    questionReplies?: Array<Record<string, unknown>>;
    terminalTickets?: Array<Record<string, unknown>>;
    fileRequests?: Array<{ workspaceId: string; path: string }>;
    fileWriteRequests?: Array<{ workspaceId: string; path: string; content: string }>;
    gitDiffRequests?: string[];
    workspaces?: Array<ReturnType<typeof workspace> & Record<string, unknown>>;
    workspaceRequests?: string[];
    workspaceRequestGates?: Record<string, Promise<void>>;
    runEvents?: Array<ReturnType<typeof event>>;
    runIds?: string[];
    runEventsByRunId?: Record<string, Array<ReturnType<typeof event>>>;
    fileContents?: Record<string, string>;
    authRoles?: string[];
    authMeGate?: Promise<void>;
    logoutRequests?: string[];
    configurationApplicationRequests?: string[];
    agentRequests?: string[];
    agents?: Array<Record<string, unknown>>;
    agentResponses?: Array<Array<Record<string, unknown>> | { status?: number; code: string; message: string; details?: Record<string, unknown> }>;
    agentsByWorkspace?: Record<string, Array<Record<string, unknown>>>;
    agentGatesByWorkspace?: Record<string, Promise<void>>;
    models?: Array<Record<string, unknown>>;
    providers?: Array<Record<string, unknown>>;
    applications?: Array<{ appId: string; appName: string; enabled: boolean }>;
    managedApplications?: Array<{ appId: string; appName: string; enabled: boolean }>;
    recentWorkspaces?: Record<string, (ReturnType<typeof workspace> & Record<string, unknown>) | null>;
    forbiddenRecentWorkspaces?: Record<string, { code: string; message: string; details?: Record<string, unknown>; status?: number }>;
    markRecentRequests?: string[];
    markRecentWorkspaces?: Record<string, ReturnType<typeof workspace> & Record<string, unknown>>;
    markRecentFailures?: Record<string, { code: string; message: string; details?: Record<string, unknown>; status?: number }>;
    personalWorkspaces?: Record<string, Array<Record<string, unknown>>>;
    personalWorkspaceRequests?: string[];
    /** 自定义 /vcs/status 返回，覆盖默认的 { status: "ready", branch: "main", defaultBranch: "main" }。 */
    vcsStatus?: { status?: string; branch?: string; defaultBranch?: string };
    /** 收集「+新增版本」发出的 POST workspace-templates/{id}/versions 请求的 version 字段（用户原值）。 */
    createVersionRequests?: string[];
    /** 自定义 /applications/{appId}/workspace-templates 返回；不传则用默认空数组。 */
    workspaceTemplates?: Record<string, Array<Record<string, unknown>>>;
    /** 自定义 /applications/{appId}/workspace-templates/{tid}/versions 返回；key 用 `{appId}:{templateId}`。 */
    workspaceVersions?: Record<string, Array<Record<string, unknown>>>;
    defaultPersonalRequests?: string[];
    processStatus?: "READY" | "NEEDS_INITIALIZATION" | "UNAVAILABLE";
    processStatusRequests?: string[];
    processInitializations?: Array<Record<string, unknown>>;
    initializeFailureThenReady?: boolean;
    ensureDefaultRequiresReady?: boolean;
    sessions?: Array<Record<string, unknown>>;
    sessionRequestGate?: Promise<void>;
    sessionTreeMessages?: Record<string, unknown>;
    sessionTreeMessagesBySessionId?: Record<string, Record<string, unknown>>;
    sessionTreeRequests?: string[];
    sessionMessages?: Array<Record<string, unknown>>;
    sessionMessagesBySessionId?: Record<string, Array<Record<string, unknown>>>;
    /** 指定历史会话的 pending native question，键为 sessionId。 */
    sessionQuestionsById?: Record<string, Array<Record<string, unknown>>>;
    /** 指定历史会话的 pending native permission，键为 sessionId。 */
    sessionPermissionsById?: Record<string, Array<Record<string, unknown>>>;
    sessionMessageRequests?: string[];
    sessionMessagesGate?: Promise<void>;
    sessionInteractionsGate?: Promise<void>;
    messageFeedbackGate?: Promise<void>;
    feedbackRequests?: string[];
    historyRun?: Record<string, unknown>;
    historyDiffFiles?: Array<Record<string, unknown>>;
    historyRunGate?: Promise<void>;
    historyRunRequests?: string[];
    activeRun?: Record<string, unknown> | null;
    activeRunRequests?: string[];
    activeRunRequestGate?: Promise<void>;
    runEventRequests?: string[];
    runContextRequests?: string[];
    runContextTokens?: string[];
    runContextRequestGates?: Record<string, Promise<void>>;
    runFailures?: string[];
    runFailureResponses?: Array<{ status: number; code: string; message: string }>;
    runRequestGate?: Promise<void>;
    runtimeStateHttpRequests?: string[];
    runtimeStateSummary?: Record<string, unknown>;
    runtimeStateEventGate?: Promise<void>;
    runtimeStateStreamFailure?: boolean;
    runtimeStateEventRequests?: string[];
    skipInitialAuthToken?: boolean;
    loginRequests?: Array<{ username?: string; password?: string }>;
    sideQuestionRequests?: Array<Record<string, unknown>>;
    sideQuestionRunIds?: string[];
  } = {}
) {
  await page.exposeFunction("__taRecordWorkspaceFileRequest", (workspaceId: string, path: string) => {
    capture.fileRequests?.push({ workspaceId, path });
  });
  await page.exposeFunction("__taRecordWorkspaceFileWrite", (workspaceId: string, path: string, content: string) => {
    capture.fileWriteRequests?.push({ workspaceId, path, content });
  });
  if (!capture.skipInitialAuthToken) {
    await page.addInitScript(() => {
      sessionStorage.setItem("test-agent.auth.token", "test-token");
    });
  }
  await page.addInitScript(({ fileContents }) => {
    const recordFileRequest = (workspaceId: string, path: string) => {
      const win = window as Window & {
        __taRecordWorkspaceFileRequest?: (workspaceId: string, path: string) => void;
        __taRecordWorkspaceFileWrite?: (workspaceId: string, path: string, content: string) => void;
      };
      win.__taRecordWorkspaceFileRequest?.(workspaceId, path);
    };
    const recordFileWrite = (workspaceId: string, path: string, content: string) => {
      const win = window as Window & {
        __taRecordWorkspaceFileWrite?: (workspaceId: string, path: string, content: string) => void;
      };
      win.__taRecordWorkspaceFileWrite?.(workspaceId, path, content);
    };
    const entries = (path: string, workspaceId = "wrk_1234567890abcdef") => {
      if (workspaceId === "wrk_project_a") {
        return path === "src"
          ? [{ path: "src/main.ts", name: "main.ts", directory: false, size: 90, lastModifiedAt: "2026-06-19T00:00:00Z" }]
          : [{ path: "src", name: "src", directory: true, size: 0, lastModifiedAt: "2026-06-19T00:00:00Z" }];
      }
      if (path === "tests") {
        return [{ path: "tests/checkout.spec.ts", name: "checkout.spec.ts", directory: false, size: 120, lastModifiedAt: "2026-06-19T00:00:00Z" }];
      }
      const configuredFiles = Object.keys(fileContents as Record<string, string>);
      if (path) {
        const prefix = `${path}/`;
        const directFiles = configuredFiles
          .filter((filePath) => filePath.startsWith(prefix) && !filePath.slice(prefix.length).includes("/"))
          .map((filePath) => ({ path: filePath, name: filePath.slice(prefix.length), directory: false, size: (fileContents as Record<string, string>)[filePath]?.length ?? 0, lastModifiedAt: "2026-06-19T00:00:00Z" }));
        if (directFiles.length) return directFiles;
      }
      const configuredDirectories = Array.from(new Set(configuredFiles.filter((filePath) => filePath.includes("/")).map((filePath) => filePath.split("/")[0] ?? "")))
        .filter(Boolean)
        .map((name) => ({ path: name, name, directory: true, size: 0, lastModifiedAt: "2026-06-19T00:00:00Z" }));
      return [
        { path: "tests", name: "tests", directory: true, size: 0, lastModifiedAt: "2026-06-19T00:00:00Z" },
        ...configuredDirectories.filter((entry) => entry.name !== "tests"),
        { path: "package.json", name: "package.json", directory: false, size: 80, lastModifiedAt: "2026-06-19T00:00:00Z" }
      ];
    };
    const directories = (path?: string) => {
      if (path === "/Users/huang/workspace/project-a") {
        return { path, parentPath: "/Users/huang/workspace", entries: [{ name: "src", path: "/Users/huang/workspace/project-a/src" }] };
      }
      if (path === "/Users/huang/workspace/demo-tests") {
        return { path, parentPath: "/Users/huang/workspace", entries: [{ name: "tests", path: "/Users/huang/workspace/demo-tests/tests" }] };
      }
      return {
        path: "/Users/huang/workspace",
        parentPath: null,
        entries: [
          { name: "demo-tests", path: "/Users/huang/workspace/demo-tests" },
          { name: "project-a", path: "/Users/huang/workspace/project-a" }
        ]
      };
    };
    class MockWorkspaceFileWebSocket {
      static CONNECTING = 0;
      static OPEN = 1;
      static CLOSING = 2;
      static CLOSED = 3;
      onopen: ((event: Event) => void) | null = null;
      onmessage: ((event: MessageEvent) => void) | null = null;
      onerror: ((event: Event) => void) | null = null;
      onclose: ((event: CloseEvent) => void) | null = null;
      readyState = MockWorkspaceFileWebSocket.CONNECTING;
      constructor(readonly url: string) {
        window.setTimeout(() => {
          this.readyState = MockWorkspaceFileWebSocket.OPEN;
          this.onopen?.(new Event("open"));
        }, 0);
      }
      send(payload: string) {
        const request = JSON.parse(payload) as { id: string; op: string; params?: Record<string, string | undefined> };
        const params = request.params ?? {};
        let data: unknown = null;
        if (request.op === "workspace.list") {
          recordFileRequest(params.workspaceId ?? "", params.path ?? "");
          data = entries(params.path ?? "", params.workspaceId);
        } else if (request.op === "workspace.read") {
          const path = params.path ?? "tests/checkout.spec.ts";
          data = {
            path,
            content: (fileContents as Record<string, string>)[path] ?? "import { test } from '@playwright/test';\n\ntest('checkout', async () => {});\n",
            encoding: "utf-8",
            size: 80,
            readonly: false
          };
        } else if (request.op === "workspace.write") {
          const path = params.path ?? "";
          const content = params.content ?? "";
          recordFileWrite(params.workspaceId ?? "", path, content);
          (fileContents as Record<string, string>)[path] = content;
        } else if (request.op === "workspace.status") {
          data = { path: params.path ?? "", exists: true, directory: false, size: 80, lastModifiedAt: "2026-06-19T00:00:00Z" };
        } else if (request.op === "directory.list") {
          data = directories(params.path);
        } else if (request.op === "workspace.create") {
          data = {
            workspaceId: "wrk_project_a",
            name: params.name ?? "project-a",
            rootPath: params.rootPath ?? "/Users/huang/workspace/project-a",
            linuxServerId: "10.8.0.12",
            status: "ACTIVE",
            createdAt: "2026-06-19T00:00:00Z",
            updatedAt: "2026-06-19T00:00:00Z"
          };
        }
        window.setTimeout(() => {
          this.onmessage?.(new MessageEvent("message", {
            data: JSON.stringify({ id: request.id, type: "result", data, traceId: "trace_e2e" })
          }));
        }, 0);
      }
      close() {
        this.readyState = MockWorkspaceFileWebSocket.CLOSED;
        this.onclose?.(new CloseEvent("close"));
      }
    }
    Object.assign(MockWorkspaceFileWebSocket, {
      CONNECTING: MockWorkspaceFileWebSocket.CONNECTING,
      OPEN: MockWorkspaceFileWebSocket.OPEN,
      CLOSING: MockWorkspaceFileWebSocket.CLOSING,
      CLOSED: MockWorkspaceFileWebSocket.CLOSED
    });
    (window as Window & { WebSocket: typeof WebSocket }).WebSocket = MockWorkspaceFileWebSocket as unknown as typeof WebSocket;
  }, { fileContents: capture.fileContents ?? {} });
  // E2E 不依赖外部字体，避免 Google Fonts 网络波动阻塞 domcontentloaded。
  await page.route("https://fonts.googleapis.com/**", async (route) => {
    await route.fulfill({ status: 200, contentType: "text/css", body: "" });
  });
  await page.route("https://fonts.gstatic.com/**", async (route) => {
    await route.fulfill({ status: 200, body: "" });
  });
  const workspaceItems = capture.workspaces ?? [workspace()];
  const applications = capture.applications ?? [{ appId: "app_gcms", appName: "F-GCMS", enabled: true }];
  const managedApplications = capture.managedApplications ?? applications;
  const agentResponses = [...(capture.agentResponses ?? [])];
  let currentProcessStatus = capture.processStatus ?? "READY";
  let sshKeys: Array<Record<string, unknown>> = [];
  await page.route("**/api/**", async (route) => {
    const url = new URL(route.request().url());
    const method = route.request().method();
    if (method === "OPTIONS") {
      await route.fulfill({ status: 204, headers: corsHeaders() });
      return;
    }
    if (method === "POST" && url.pathname === "/api/auth/login") {
      const body = JSON.parse(route.request().postData() ?? "{}") as { username?: string; password?: string };
      capture.loginRequests?.push({ username: body.username, password: body.password });
      await route.fulfill(json({
        token: "test-token",
        tokenType: "Bearer",
        expiresAt: "2026-06-24T01:00:00Z"
      }));
      return;
    }
    if (method === "GET" && url.pathname === "/api/auth/me") {
      await capture.authMeGate;
      const roles = capture.authRoles ?? ["APP_ADMIN"];
      await route.fulfill(json({
        userId: "usr_admin",
        username: "admin",
        unifiedAuthId: "admin",
        roles,
        // E2E mock 直接把后端 translations 关系预生成好；用户菜单顶部灰显行会展示这里的中文标签。
        roleLabels: roles.map((role) => roleLabelOf(role))
      }));
      return;
    }
    if (method === "POST" && url.pathname === "/api/auth/logout") {
      capture.logoutRequests?.push(`${method} ${url.pathname}`);
      await route.fulfill(json(null));
      return;
    }
    if (url.pathname.startsWith("/api/internal/platform/configuration-management")) {
      if (!url.pathname.startsWith("/api/internal/platform/configuration-management/personal/ssh-keys")) {
        capture.configurationApplicationRequests?.push(`${method} ${url.pathname}`);
      }
      if (method === "GET" && url.pathname === "/api/internal/platform/configuration-management/applications") {
        await route.fulfill(json(applications));
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
    if (url.pathname.startsWith("/api/internal/platform/workspace-management")) {
      if (method === "GET" && url.pathname === "/api/internal/platform/workspace-management/backend-servers") {
        await route.fulfill(json([
          {
            linuxServerId: "10.8.0.12",
            name: "dev-backend",
            baseUrl: "http://127.0.0.1:8080",
            webSocketPath: "/api/internal/platform/workspace-management/file/ws",
            defaultDirectory: "/Users/huang/workspace",
            sameAsAgent: true
          }
        ]));
        return;
      }
      if (method === "POST" && url.pathname === "/api/internal/platform/workspace-management/file-ws/tickets") {
        await route.fulfill(json({
          ticket: "wft_e2e",
          expiresAt: "2026-06-19T00:01:00Z",
          webSocketUrl: "/api/internal/platform/workspace-management/file/ws?ticket=wft_e2e"
        }));
        return;
      }
      if (method === "GET" && url.pathname === "/api/internal/platform/workspace-management/applications") {
        await route.fulfill(json(managedApplications));
        return;
      }
      if (method === "GET" && url.pathname === "/api/internal/platform/workspace-management/recent-workspace") {
        await route.fulfill(json(null));
        return;
      }
      if (method === "GET" && url.pathname === "/api/internal/platform/workspace-management/applications/app_gcms/recent-workspace") {
        const forbidden = capture.forbiddenRecentWorkspaces?.app_gcms;
        if (forbidden) {
          await route.fulfill({
            status: forbidden.status ?? 403,
            ...jsonFailure(forbidden.code, forbidden.message, forbidden.details)
          });
          return;
        }
        await route.fulfill(json(capture.recentWorkspaces?.app_gcms ?? workspace()));
        return;
      }
      const recentWorkspaceMatch = url.pathname.match(/^\/api\/internal\/platform\/workspace-management\/applications\/([^/]+)\/recent-workspace$/);
      if (method === "GET" && recentWorkspaceMatch) {
        const appId = recentWorkspaceMatch[1] ?? "";
        const forbidden = capture.forbiddenRecentWorkspaces?.[appId];
        if (forbidden) {
          await route.fulfill({
            status: forbidden.status ?? 403,
            ...jsonFailure(forbidden.code, forbidden.message, forbidden.details)
          });
          return;
        }
        await route.fulfill(json(capture.recentWorkspaces?.[appId] ?? null));
        return;
      }
      if (method === "POST" && /^\/api\/internal\/platform\/workspace-management\/workspaces\/[^/]+\/recent$/.test(url.pathname)) {
        const workspaceId = url.pathname.match(/\/workspaces\/([^/]+)\/recent$/)?.[1] ?? "";
        capture.markRecentRequests?.push(workspaceId);
        const failure = capture.markRecentFailures?.[workspaceId];
        if (failure) {
          await route.fulfill({
            status: failure.status ?? 403,
            ...jsonFailure(failure.code, failure.message, failure.details)
          });
          return;
        }
        await route.fulfill(json(capture.markRecentWorkspaces?.[workspaceId] ?? null));
        return;
      }
      const workspaceTemplatesMatch = url.pathname.match(/^\/api\/internal\/platform\/workspace-management\/applications\/([^/]+)\/workspace-templates$/);
      if (method === "GET" && workspaceTemplatesMatch) {
        const appId = workspaceTemplatesMatch[1] ?? "";
        await route.fulfill(json(capture.workspaceTemplates?.[appId] ?? []));
        return;
      }
      const workspaceVersionsMatch = url.pathname.match(/^\/api\/internal\/platform\/workspace-management\/applications\/([^/]+)\/workspace-templates\/([^/]+)\/versions$/);
      if (method === "GET" && workspaceVersionsMatch) {
        const appId = workspaceVersionsMatch[1] ?? "";
        const templateId = url.pathname.match(/\/workspace-templates\/([^/]+)\/versions$/)?.[1] ?? "";
        await route.fulfill(json(capture.workspaceVersions?.[`${appId}:${templateId}`] ?? []));
        return;
      }
      if (method === "POST" && /\/api\/internal\/platform\/workspace-management\/applications\/app_gcms\/workspace-templates\/[^/]+\/versions$/.test(url.pathname)) {
        // 拦截「+新增版本」请求：捕获 payload，返回一个伪 ApplicationWorkspaceVersion 供前端刷新菜单使用。
        const body = JSON.parse(route.request().postData() ?? "{}") as { version?: string };
        capture.createVersionRequests ??= [];
        capture.createVersionRequests.push(body.version ?? "");
        await route.fulfill(json({
          versionId: "awv_new",
          applicationWorkspaceId: "awp_1",
          appId: "app_gcms",
          repositoryId: "repo_1",
          version: body.version ?? "2024年1月",
          branch: "feature_testagent_" + (body.version ?? "2024年1月"),
          repoRootPath: "/tmp/test-agent/appworkspace/new/repo_1",
          workspaceRootPath: "/tmp/test-agent/appworkspace/new/repo_1/F-GCMS/workspace",
          runtimeWorkspace: workspace(),
          status: "ACTIVE",
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString()
        }));
        return;
      }
      if (method === "GET" && /\/api\/internal\/platform\/workspace-management\/workspace-versions\/[^/]+\/personal-workspaces$/.test(url.pathname)) {
        const versionId = url.pathname.match(/\/workspace-versions\/([^/]+)\/personal-workspaces$/)?.[1] ?? "";
        capture.personalWorkspaceRequests?.push(versionId);
        await route.fulfill(json(capture.personalWorkspaces?.[versionId] ?? []));
        return;
      }
      if (method === "POST" && /\/api\/internal\/platform\/workspace-management\/workspace-versions\/[^/]+\/ensure-default-personal-workspace$/.test(url.pathname)) {
        const versionId = url.pathname.match(/\/workspace-versions\/([^/]+)\/ensure-default-personal-workspace$/)?.[1] ?? "";
        capture.defaultPersonalRequests?.push(versionId);
        if (capture.ensureDefaultRequiresReady && currentProcessStatus !== "READY") {
          await route.fulfill({
            status: 409,
            ...jsonFailure("OPENCODE_PROCESS_STARTING", "TestAgent 进程正在启动")
          });
          return;
        }
        await route.fulfill(json({
          personalWorkspaceId: "psw_default",
          personalWorkspaceName: "default",
          personalWorkspaceBranch: "feature_testagent_20260715_usr_admin_default",
          runtimeWorkspace: {
            ...workspace(),
            workspaceId: "wrk_personal_default",
            name: "default",
            rootPath: "/Users/huang/workspace/personal-default",
            appId: "app_gcms",
            versionId,
            applicationWorkspaceId: "awp_1"
          }
        }));
        return;
      }
      if (method === "GET" && /\/api\/internal\/platform\/workspace-management\/workspaces\/[^/]+\/git-diff$/.test(url.pathname)) {
        capture.gitDiffRequests?.push(`${method} ${url.pathname}`);
        await route.fulfill(json({ files: capture.historyDiffFiles ?? [] }));
        return;
      }
      if (method === "POST" && /\/api\/internal\/platform\/workspace-management\/workspaces\/[^/]+\/git-discard$/.test(url.pathname)) {
        await route.fulfill(json(null));
        return;
      }
    }
    if (method === "GET" && url.pathname === "/api/internal/agent/opencode/processes/me") {
      capture.processStatusRequests?.push(`${method} ${url.pathname}`);
      await route.fulfill(json(opencodeProcessStatus(currentProcessStatus)));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/agent/opencode/processes/me/health") {
      await route.fulfill(json({
        healthy: currentProcessStatus === "READY",
        status: currentProcessStatus === "READY" ? "HEALTHY" : "UNHEALTHY",
        message: currentProcessStatus === "READY" ? "TestAgent 进程可用" : "TestAgent 进程不可用",
        linuxServerId: url.searchParams.get("linuxServerId") ?? "server-a",
        containerId: url.searchParams.get("containerId") ?? "ctr_01",
        port: Number(url.searchParams.get("port") ?? 4096),
        checkedAt: "2026-07-10T00:00:00Z"
      }));
      return;
    }
    if (method === "POST" && url.pathname === "/api/internal/agent/opencode/processes/me/initialize") {
      capture.processInitializations?.push({});
      if (capture.initializeFailureThenReady) {
        currentProcessStatus = "READY";
        await route.fulfill({
          status: 409,
          ...jsonFailure("OPENCODE_PROCESS_STARTING", "TestAgent 进程正在启动")
        });
        return;
      }
      currentProcessStatus = "READY";
      await route.fulfill(json(opencodeProcessStatus(currentProcessStatus)));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/platform/workspace-management/workspaces") {
      await route.fulfill(json(pageOf(workspaceItems)));
      return;
    }
    if (method === "POST" && /^\/api\/internal\/platform\/workspace-management\/workspaces\/[^/]+\/file-ws-route$/.test(url.pathname)) {
      const workspaceId = url.pathname.match(/\/api\/internal\/platform\/workspace-management\/workspaces\/([^/]+)\/file-ws-route$/)?.[1] ?? "";
      await route.fulfill(json({
        workspaceId,
        linuxServerId: "10.8.0.12",
        baseUrl: "http://127.0.0.1:8080",
        webSocketPath: "/api/internal/platform/workspace-management/file/ws",
        sameServer: true,
        message: null
      }));
      return;
    }
    if (method === "GET" && /^\/api\/internal\/platform\/workspace-management\/workspaces\/[^/]+$/.test(url.pathname)) {
      const workspaceId = url.pathname.match(/\/api\/internal\/platform\/workspace-management\/workspaces\/([^/]+)$/)?.[1];
      if (workspaceId) {
        capture.workspaceRequests?.push(workspaceId);
        await capture.workspaceRequestGates?.[workspaceId];
      }
      await route.fulfill(json(workspaceItems.find((item) => item.workspaceId === workspaceId) ?? workspace()));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/agent/opencode/file/content") {
      const path = url.searchParams.get("path") ?? "";
      await route.fulfill(json({
        type: "file",
        content: capture.fileContents?.[path] ?? ""
      }));
      return;
    }
    if (method === "GET" && url.pathname.endsWith("/files")) {
      await route.fulfill({ status: 500, ...json({ error: "workspace files must use websocket" }) });
      return;
    }
    if (method === "GET" && url.pathname.endsWith("/files/content")) {
      await route.fulfill({ status: 500, ...json({ error: "workspace files must use websocket" }) });
      return;
    }
    if (method === "PUT" && url.pathname.endsWith("/files/content")) {
      await route.fulfill({ status: 500, ...json({ error: "workspace files must use websocket" }) });
      return;
    }
    if (method === "GET" && /\/api\/internal\/platform\/opencode-runtime\/workspaces\/[^/]+\/sessions$/.test(url.pathname)) {
      await route.fulfill(json(pageOf(capture.sessions ?? [])));
      return;
    }
    if (method === "POST" && url.pathname === "/api/internal/platform/opencode-runtime/sessions") {
      capture.sessionRequests?.push(JSON.parse(route.request().postData() ?? "{}") as Record<string, unknown>);
      await capture.sessionRequestGate;
      await route.fulfill(json(session()));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/platform/opencode-runtime/sessions") {
      await route.fulfill(json(pageOf(capture.sessions ?? [])));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/platform/opencode-runtime/sessions/runtime-state") {
      capture.runtimeStateHttpRequests?.push(url.pathname);
      await route.fulfill(json({ runningCount: 0, questionCount: 0, sessions: [], generatedAt: "2026-07-10T00:00:00Z" }));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/platform/opencode-runtime/sessions/runtime-state/events") {
      capture.runtimeStateEventRequests?.push(url.pathname);
      await capture.runtimeStateEventGate;
      if (capture.runtimeStateStreamFailure) {
        await route.fulfill({ status: 503, ...jsonFailure("RUNTIME_STATE_UNAVAILABLE", "运行态流不可用") });
        return;
      }
      const summary = capture.runtimeStateSummary ?? {
        runningCount: 0,
        questionCount: 0,
        sessions: [],
        generatedAt: "2026-07-10T00:00:00Z"
      };
      await route.fulfill({
        status: 200,
        headers: { ...corsHeaders(), "Content-Type": "text/event-stream", "Cache-Control": "no-cache" },
        body: `event: session-runtime.snapshot\ndata: ${JSON.stringify(summary)}\n\n`
      });
      return;
    }
    if (method === "POST" && /^\/api\/internal\/agent\/opencode\/sessions\/[^/]+\/run-context$/.test(url.pathname)) {
      const sessionId = decodeURIComponent(url.pathname.match(/\/sessions\/([^/]+)\/run-context$/)?.[1] ?? "ses_1");
      capture.runContextRequests?.push(sessionId);
      await capture.runContextRequestGates?.[sessionId];
      const contextIndex = (capture.runContextRequests?.length ?? 1) - 1;
      await route.fulfill(json({
        contextToken: capture.runContextTokens?.[contextIndex] ?? `ctx_e2e_${contextIndex + 1}`,
        contextVersion: contextIndex + 1,
        expiresAt: "2026-07-11T00:00:00Z"
      }));
      return;
    }
    if (method === "GET" && /^\/api\/internal\/agent\/opencode\/sessions\/[^/]+\/session-tree\/messages$/.test(url.pathname)) {
      capture.sessionTreeRequests?.push(`${url.pathname}${url.search}`);
      const sessionId = url.pathname.match(/\/sessions\/([^/]+)\/session-tree\/messages$/)?.[1] ?? "ses_history";
      await route.fulfill(json(capture.sessionTreeMessagesBySessionId?.[sessionId] ?? capture.sessionTreeMessages ?? {
        sessionId,
        sessions: [],
        messagesBySessionId: {},
        childSessionIdByTaskPartId: {},
        events: []
      }));
      return;
    }
    if (method === "GET" && /^\/api\/internal\/platform\/opencode-runtime\/sessions\/[^/]+\/messages$/.test(url.pathname)) {
      capture.sessionMessageRequests?.push(`${url.pathname}${url.search}`);
      await capture.sessionMessagesGate;
      const sessionId = url.pathname.match(/\/sessions\/([^/]+)\/messages$/)?.[1] ?? "ses_history";
      await route.fulfill(json(pageOf(capture.sessionMessagesBySessionId?.[sessionId] ?? capture.sessionMessages ?? [])));
      return;
    }
    if (method === "GET" && /^\/api\/internal\/platform\/opencode-runtime\/sessions\/[^/]+\/questions$/.test(url.pathname)) {
      const sessionId = url.pathname.match(/\/sessions\/([^/]+)\/questions$/)?.[1] ?? "";
      await capture.sessionInteractionsGate;
      await route.fulfill(json(capture.sessionQuestionsById?.[sessionId] ?? []));
      return;
    }
    if (method === "GET" && /^\/api\/internal\/platform\/opencode-runtime\/sessions\/[^/]+\/permissions$/.test(url.pathname)) {
      const sessionId = url.pathname.match(/\/sessions\/([^/]+)\/permissions$/)?.[1] ?? "";
      await capture.sessionInteractionsGate;
      await route.fulfill(json(capture.sessionPermissionsById?.[sessionId] ?? []));
      return;
    }
    if (method === "POST" && /^\/api\/internal\/platform\/opencode-runtime\/sessions\/[^/]+\/questions\/[^/]+\/reply$/.test(url.pathname)) {
      capture.questionReplies?.push(JSON.parse(route.request().postData() ?? "{}") as Record<string, unknown>);
      await route.fulfill(json({ accepted: true }));
      return;
    }
    if (method === "GET" && /^\/api\/internal\/platform\/opencode-runtime\/messages\/[^/]+\/feedback\/me$/.test(url.pathname)) {
      capture.feedbackRequests?.push(url.pathname);
      await capture.messageFeedbackGate;
      await route.fulfill(json(null));
      return;
    }
    if (method === "GET" && /^\/api\/internal\/platform\/opencode-runtime\/sessions\/[^/]+\/active-run$/.test(url.pathname)) {
      capture.activeRunRequests?.push(url.pathname);
      await capture.activeRunRequestGate;
      await route.fulfill(json(capture.activeRun ?? null));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/agent/opencode/runs/run_history") {
      capture.historyRunRequests?.push(url.pathname);
      await capture.historyRunGate;
      await route.fulfill(json(capture.historyRun ?? {}));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/agent/opencode/runs/run_history/diff") {
      await route.fulfill(json({ runId: "run_history", files: capture.historyDiffFiles ?? [] }));
      return;
    }
    if (method === "POST" && /^\/api\/internal\/platform\/opencode-runtime\/sessions\/[^/]+\/side-question\/runs$/.test(url.pathname)) {
      capture.sideQuestionRequests?.push(JSON.parse(route.request().postData() ?? "{}") as Record<string, unknown>);
      const requestIndex = (capture.sideQuestionRequests?.length ?? 1) - 1;
      const runId = capture.sideQuestionRunIds?.[requestIndex] ?? `run_side_question_${requestIndex + 1}`;
      await route.fulfill(json({ runId }));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/platform/opencode-runtime/agents") {
      const workspaceId = url.searchParams.get("workspaceId") ?? "";
      capture.agentRequests?.push(`${method} ${url.pathname}${url.search}`);
      await capture.agentGatesByWorkspace?.[workspaceId];
      const queued = agentResponses.length > 0 ? agentResponses.shift() : undefined;
      if (queued && !Array.isArray(queued)) {
        await route.fulfill({
          status: queued.status ?? 500,
          ...jsonFailure(queued.code, queued.message, queued.details)
        });
        return;
      }
      const agents = queued ?? capture.agentsByWorkspace?.[workspaceId] ?? capture.agents ?? [{ id: "build", name: "Build" }];
      await route.fulfill(json(agents));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/platform/opencode-runtime/models") {
      await route.fulfill(json(capture.models ?? [
        { id: "sonnet", providerId: "anthropic", name: "Sonnet" },
        { id: "opus", providerId: "anthropic", name: "Opus" },
        { id: "glm-5.2", providerId: "volcengine", name: "GLM-5.2" },
        { id: "north-mini-code", providerId: "opencode-zen", name: "North Mini Code Free", free: true }
      ]));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/platform/opencode-runtime/providers") {
      await route.fulfill(json(capture.providers ?? [
        { id: "anthropic", name: "Anthropic", status: "ready" },
        { id: "volcengine", name: "Volcengine Ark", status: "ready" },
        { id: "opencode-zen", name: "OpenCode Zen", status: "ready" }
      ]));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/platform/opencode-runtime/commands") {
      await route.fulfill(json([{ id: "test", name: "test", description: "Run tests" }]));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/platform/opencode-runtime/mcp/resources") {
      await route.fulfill(json([{ id: "issue-1", name: "Issue 1", uri: "mcp://issue/1", type: "issue" }]));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/platform/opencode-runtime/mcp/tools") {
      await route.fulfill(json(["bash"]));
      return;
    }
    if (
      method === "GET" &&
      [
        "/api/internal/platform/opencode-runtime/lsp/status",
        "/api/internal/platform/opencode-runtime/mcp/status",
        "/api/internal/platform/opencode-runtime/vcs/status"
      ].includes(url.pathname)
    ) {
      await route.fulfill(json(capture.vcsStatus ?? { status: "ready", branch: "main", defaultBranch: "main" }));
      return;
    }
    if (method === "POST" && url.pathname === "/api/internal/agent/opencode/runs") {
      const request = JSON.parse(route.request().postData() ?? "{}") as Record<string, unknown>;
      capture.runRequests?.push(request);
      await capture.runRequestGate;
      const requestIndex = (capture.runRequests?.length ?? 1) - 1;
      const failureResponse = capture.runFailureResponses?.shift();
      if (failureResponse) {
        await route.fulfill({
          status: failureResponse.status,
          ...jsonFailure(failureResponse.code, failureResponse.message)
        });
        return;
      }
      const failureCode = capture.runFailures?.shift();
      if (failureCode) {
        await route.fulfill({ status: 409, ...jsonFailure(failureCode, "会话运行上下文已失效") });
        return;
      }
      const runId = capture.runIds?.[requestIndex] ?? "run_1";
      await route.fulfill(json({
        runId,
        sessionId: String(request.sessionId ?? "ses_1"),
        workspaceId: "wrk_1234567890abcdef",
        status: "RUNNING",
        clientRequestId: request.clientRequestId,
        createdAt: "2026-06-19T00:00:00Z",
        updatedAt: "2026-06-19T00:00:00Z"
      }));
      return;
    }
    if (method === "POST" && /^\/api\/internal\/agent\/opencode\/session\/[^/]+\/command$/.test(url.pathname)) {
      capture.commandRequests?.push(JSON.parse(route.request().postData() ?? "{}") as Record<string, unknown>);
      await route.fulfill(json({ accepted: true }));
      return;
    }
    const runEventsMatch = url.pathname.match(/^\/api\/internal\/agent\/opencode\/runs\/([^/]+)\/events$/);
    if (method === "GET" && runEventsMatch) {
      const runId = runEventsMatch[1] ?? "run_1";
      capture.runEventRequests?.push(url.pathname);
      const runEvents = capture.runEventsByRunId?.[runId] ?? capture.runEvents;
      await route.fulfill({
        status: 200,
        headers: { ...corsHeaders(), "Content-Type": "text/event-stream", "Cache-Control": "no-cache" },
        body: sse(runEvents ?? [
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
    if (method === "POST" && url.pathname === "/api/internal/platform/opencode-runtime/sessions/ses_1/permissions/perm_1/reply") {
      capture.permissionReplies?.push(JSON.parse(route.request().postData() ?? "{}") as Record<string, unknown>);
      await route.fulfill(json({ accepted: true }));
      return;
    }
    if (method === "POST" && /^\/api\/internal\/platform\/opencode-runtime\/sessions\/[^/]+\/permissions\/[^/]+\/reply$/.test(url.pathname)) {
      capture.permissionReplies?.push(JSON.parse(route.request().postData() ?? "{}") as Record<string, unknown>);
      await route.fulfill(json({ accepted: true }));
      return;
    }
    if (method === "POST" && url.pathname === "/api/internal/platform/opencode-runtime/sessions/ses_1/questions/ques_1/reply") {
      capture.questionReplies?.push(JSON.parse(route.request().postData() ?? "{}") as Record<string, unknown>);
      await route.fulfill(json({ accepted: true }));
      return;
    }
    if (method === "POST" && url.pathname === "/api/internal/platform/opencode-runtime/sessions/ses_1/terminal/tickets") {
      capture.terminalTickets?.push(JSON.parse(route.request().postData() ?? "{}") as Record<string, unknown>);
      await route.fulfill(json({
        ticket: "pty_123",
        expiresAt: "2026-06-19T13:00:00Z",
        webSocketUrl: "/api/internal/platform/opencode-runtime/sessions/ses_1/terminal/ws?ticket=pty_123"
      }));
      return;
    }
    await route.fulfill(json({}));
  });
}

async function gotoWorkbench(page: Page, options: { selectConversation?: boolean } = {}) {
  await page.goto("/", { waitUntil: "domcontentloaded" });
  if (options.selectConversation === false) return;
  const newConversationButton = page.getByRole("button", { name: "新建对话" });
  // 部分用例会被路由到登录或只读页面；只有工作台实际渲染该入口时才进入新对话草稿。
  const buttonVisible = await newConversationButton
    .waitFor({ state: "visible", timeout: 1_000 })
    .then(() => true)
    .catch(() => false);
  if (buttonVisible && await newConversationButton.isEnabled()) {
    await newConversationButton.click();
  }
}

function json(data: unknown) {
  return {
    contentType: "application/json",
    headers: corsHeaders(),
    body: JSON.stringify({ success: true, traceId: "trace_e2e", data })
  };
}

function jsonFailure(code: string, message: string, details: Record<string, unknown> = {}) {
  return {
    contentType: "application/json",
    headers: corsHeaders(),
    body: JSON.stringify({ success: false, traceId: "trace_e2e", code, message, retryable: true, details })
  };
}

/**
 * E2E mock 用：把 role code 翻译成 mock 后端会返回的 dict_label 形式。
 * 与后端测试 JDBC fixture 的角色 dict_label 保持一致，避免 e2e 与单测走两套命名。
 */
function roleLabelOf(role: string): string {
  switch (role) {
    case "SUPER_ADMIN":
      return "超级管理员";
    case "SYSTEM_ADMIN":
      return "系统管理员";
    case "APP_ADMIN":
      return "应用管理员";
    case "USER":
      return "普通用户";
    default:
      return role;
  }
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
    linuxServerId: "10.8.0.12",
    status: "ACTIVE",
    createdAt: "2026-06-19T00:00:00Z",
    updatedAt: "2026-06-19T00:00:00Z"
  };
}

function runnableWorkspaceSetup() {
  return {
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        appId: "app_gcms",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1"
      }
    },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    }
  };
}

function defaultPersonalWorkspace(versionId: string) {
  return {
    personalWorkspaceId: "psw_default",
    versionId,
    appId: "app_gcms",
    applicationWorkspaceId: "awp_1",
    workspaceName: "default",
    branch: "feature_testagent_20260715_usr_admin_default",
    repoRootPath: "/Users/huang/workspace/personal-default",
    workspaceRootPath: "/Users/huang/workspace/personal-default",
    runtimeWorkspace: {
      ...workspace(),
      workspaceId: "wrk_personal_default",
      name: "default",
      rootPath: "/Users/huang/workspace/personal-default",
      appId: "app_gcms",
      versionId,
      applicationWorkspaceId: "awp_1"
    },
    baseCommit: "commit_base",
    status: "ACTIVE",
    createdAt: "2026-06-19T00:00:00Z",
    updatedAt: "2026-06-19T00:00:00Z"
  };
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

function petContextMessage() {
  return {
    messageId: "msg_pet_context",
    remoteMessageId: "msg_remote_pet_context",
    sessionId: "ses_1",
    role: "ASSISTANT",
    content: "当前任务上下文已准备",
    createdAt: "2026-06-19T00:00:00Z",
    runId: "run_history"
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

function opencodeProcessStatus(status: "READY" | "NEEDS_INITIALIZATION" | "UNAVAILABLE") {
  if (status === "READY") {
    return {
      status,
      initializable: false,
      message: "TestAgent 进程可用",
      processId: "ocp_1234567890abcdef",
      linuxServerId: "server-a",
      containerId: "ctr_01",
      port: 4096,
      baseUrl: "http://10.8.0.12:4096",
      serviceStatus: "RUNNING",
      serviceAddress: "10.8.0.12:4096",
      checkedAt: "2026-06-24T00:00:00Z"
    };
  }
  return {
    status,
    initializable: status === "NEEDS_INITIALIZATION",
    message: status === "NEEDS_INITIALIZATION" ? "需要初始化 TestAgent 进程" : "没有可用的 TestAgent 容器",
    checkedAt: "2026-06-24T00:00:00Z"
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
