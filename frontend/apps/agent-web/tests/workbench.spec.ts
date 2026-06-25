import { expect, test, type Page } from "@playwright/test";

test("workbench opens a workspace file with mocked backend api", async ({ page }) => {
  await mockBackendApi(page);

  await gotoWorkbench(page);

  await expect(page.getByText("MIMO测试智能体")).toBeVisible();
  await expect(page.getByRole("button", { name: "关闭运行与终端" })).toBeVisible();
  await page.getByRole("button", { name: /tests/ }).click();
  await page.getByRole("button", { name: /checkout.spec.ts/ }).click();
  await expect(page.getByText("tests/checkout.spec.ts", { exact: true }).first()).toBeVisible();
  await expect(page.getByRole("button", { name: /保存/ })).toBeVisible();
});

test("switching to an application without recent workspace clears the previous file tree", async ({ page }) => {
  const fileRequests: Array<{ workspaceId: string; path: string }> = [];
  await mockBackendApi(page, {
    fileRequests,
    applications: [
      { appId: "app_gcms", appName: "F-GCMS", enabled: true },
      { appId: "app_coss", appName: "F-COSS", enabled: true }
    ],
    recentWorkspaces: {
      app_gcms: workspace(),
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
  expect(fileRequests).toContainEqual({ workspaceId: "wrk_1234567890abcdef", path: "" });
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

test("user avatar menu logs out and returns to login", async ({ page }) => {
  const logoutRequests: string[] = [];
  await mockBackendApi(page, { logoutRequests });

  await gotoWorkbench(page);

  await page.getByRole("button", { name: "当前用户 admin" }).click();
  await expect(page.getByRole("menuitem", { name: "退出登录" })).toBeVisible();
  await page.getByRole("menuitem", { name: "退出登录" }).click();

  await expect(page.getByRole("heading", { name: "智能测试代理平台" })).toBeVisible();
  await expect.poll(() => logoutRequests).toEqual(["POST /api/auth/logout"]);
  await expect.poll(() => page.evaluate(() => localStorage.getItem("test-agent.auth.token"))).toBeNull();
});

test("admin application fallback runs after auth roles arrive", async ({ page }) => {
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
  expect(configurationApplicationRequests).toEqual([]);

  releaseAuthMe();

  await expect(page.getByRole("button", { name: "F-GCMS" })).toBeVisible();
  await expect(page.getByRole("button", { name: /tests/ })).toBeVisible();
  expect(configurationApplicationRequests).toContain("GET /api/internal/platform/configuration-management/applications");
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
  await expect(page.getByRole("button", { name: "应用与工作区" })).toBeVisible();
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
  await expect(page.getByRole("button", { name: "应用与工作区" })).toBeVisible();
  await expect(page.getByText("您当前角色[USER]无该项设置权限。")).toBeVisible();
  expect(configurationApplicationRequests).toEqual([]);
});

test("settings dialog shows empty role placeholder for users without roles", async ({ page }) => {
  await mockBackendApi(page, { authRoles: [] });

  await gotoWorkbench(page);

  await page.getByRole("button", { name: "系统设置" }).click();
  await expect(page.getByText("您当前角色[无角色]无该项设置权限。")).toBeVisible();
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

test("workbench disables chat until opencode process is initialized", async ({ page }) => {
  const processInitializations: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, { processStatus: "NEEDS_INITIALIZATION", processInitializations });

  await gotoWorkbench(page);

  await expect(page.getByText("需要初始化 opencode 进程")).toBeVisible();
  await expect(page.getByRole("button", { name: "发送" })).toBeDisabled();
  await page.getByRole("button", { name: "初始化进程" }).click();

  await expect.poll(() => processInitializations.length).toBe(1);
  await expect(page.getByText("opencode 进程可用")).toBeVisible();
  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("run after init");
  await expect(page.getByRole("button", { name: "发送" })).toBeEnabled();
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

test("branch dropdown exposes a two-level menu grouped by source", async ({ page }) => {
  const changeBranchRequests: string[] = [];
  // 1) vcs.status 返回 feature 分支作为当前分支 + main 作为默认分支；
  // 2) branch-preference GET 返回 release 分支作为最近偏好；
  // 三者不同，验证两级菜单能同时出现"当前分支 / 默认分支 / 最近使用"三个分组。
  await mockBackendApi(page, {
    vcsStatus: { status: "ready", branch: "feature", defaultBranch: "main" },
    recentBranchPreference: {
      appId: "app_gcms",
      workspaceId: "wrk_1234567890abcdef",
      branch: "release",
      updatedAt: "2026-06-24T00:00:00Z"
    },
    changeBranchRequests
  });

  await gotoWorkbench(page);

  // footer 上的分支按钮显示当前分支名
  await expect(page.getByRole("button", { name: "feature" })).toBeVisible();

  // 点击按钮打开一级菜单：三个分组都应出现
  await page.getByRole("button", { name: "feature" }).click();
  const currentItem = page.getByRole("menuitem", { name: "当前分支" });
  const defaultItem = page.getByRole("menuitem", { name: "默认分支" });
  const recentItem = page.getByRole("menuitem", { name: "最近使用" });
  await expect(currentItem).toBeVisible();
  await expect(defaultItem).toBeVisible();
  await expect(recentItem).toBeVisible();

  // 验证菜单面板 Teleport 到 body、position:fixed、有非零大小（避免被父级 overflow:hidden 裁切）
  const panel = page.locator(".ta-workbench-branch-panel");
  await expect(panel).toBeVisible();
  const panelBox = await panel.boundingBox();
  expect(panelBox).not.toBeNull();
  expect(panelBox!.width).toBeGreaterThan(0);
  expect(panelBox!.height).toBeGreaterThan(0);
  // 面板 y 应小于按钮 y（菜单在按钮正上方）；Playwright boundingBox 用 y 表示 top
  const buttonBox = await page.getByRole("button", { name: "feature" }).boundingBox();
  expect(buttonBox).not.toBeNull();
  expect(panelBox!.y).toBeLessThan(buttonBox!.y);

  // hover 默认分支 → 二级菜单展示 main 分支名
  await defaultItem.hover();
  await expect(page.getByRole("menuitem", { name: /^main/ }).first()).toBeVisible();

  // 点击 main → 触发 change-branch 事件
  await page.getByRole("menuitem", { name: /^main/ }).first().click();
  await expect.poll(() => changeBranchRequests).toEqual(["main"]);
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
  const trigger = page.getByRole("button", { name: /工作空间/ });
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
  const trigger = page.getByRole("button", { name: /工作空间/ });
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
  // （依赖 main.ts 里的 dayjs.locale("zh-cn")）
  await dialog.locator(".el-date-editor input").click();
  const monthPanel = page.locator(".el-month-table");
  await expect(monthPanel).toBeVisible();
  // 调试：dump 月份面板的 HTML 看实际结构
  // eslint-disable-next-line no-console
  console.log("[monthPanel html]", await monthPanel.innerHTML());
  // 第一个月文案应该是"1月"（Element Plus 2.12 用 div.month 或 td）
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
  const trigger = page.getByRole("button", { name: /工作空间/ });
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
    authRoles?: string[];
    authMeGate?: Promise<void>;
    logoutRequests?: string[];
    configurationApplicationRequests?: string[];
    applications?: Array<{ appId: string; appName: string; enabled: boolean }>;
    managedApplications?: Array<{ appId: string; appName: string; enabled: boolean }>;
    recentWorkspaces?: Record<string, ReturnType<typeof workspace> | null>;
    /** 自定义 /vcs/status 返回，覆盖默认的 { status: "ready", branch: "main", defaultBranch: "main" }。 */
    vcsStatus?: { status?: string; branch?: string; defaultBranch?: string };
    /** 自定义最近 VCS 分支偏好（GET branch-preference）；null 表示不返回偏好。 */
    recentBranchPreference?: { appId: string; workspaceId: string; branch: string; updatedAt: string } | null;
    /** 收集用户通过分支下拉发出的"切换分支"请求（POST branch-preference 的 branch 字段）。 */
    changeBranchRequests?: string[];
    /** 收集「+新增版本」发出的 POST workspace-templates/{id}/versions 请求的 version 字段（用户原值）。 */
    createVersionRequests?: string[];
    /** 自定义 /applications/{appId}/workspace-templates 返回；不传则用默认空数组。 */
    workspaceTemplates?: Record<string, Array<Record<string, unknown>>>;
    /** 自定义 /applications/{appId}/workspace-templates/{tid}/versions 返回；key 用 `{appId}:{templateId}`。 */
    workspaceVersions?: Record<string, Array<Record<string, unknown>>>;
    processStatus?: "READY" | "NEEDS_INITIALIZATION" | "UNAVAILABLE";
    processInitializations?: Array<Record<string, unknown>>;
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
  const applications = capture.applications ?? [{ appId: "app_gcms", appName: "F-GCMS", enabled: true }];
  const managedApplications = capture.managedApplications ?? applications;
  let currentProcessStatus = capture.processStatus ?? "READY";
  let sshKeys: Array<Record<string, unknown>> = [];
  await page.route("**/api/**", async (route) => {
    const url = new URL(route.request().url());
    const method = route.request().method();
    if (method === "OPTIONS") {
      await route.fulfill({ status: 204, headers: corsHeaders() });
      return;
    }
    if (method === "GET" && url.pathname === "/api/auth/me") {
      await capture.authMeGate;
      await route.fulfill(json({
        userId: "usr_admin",
        username: "admin",
        unifiedAuthId: "admin",
        roles: capture.authRoles ?? ["APP_ADMIN"]
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
      if (method === "GET" && url.pathname === "/api/internal/platform/workspace-management/applications") {
        await route.fulfill(json(managedApplications));
        return;
      }
      if (method === "GET" && url.pathname === "/api/internal/platform/workspace-management/recent-workspace") {
        await route.fulfill(json(null));
        return;
      }
      if (method === "GET" && url.pathname === "/api/internal/platform/workspace-management/applications/app_gcms/recent-workspace") {
        await route.fulfill(json(capture.recentWorkspaces?.app_gcms ?? workspace()));
        return;
      }
      const recentWorkspaceMatch = url.pathname.match(/^\/api\/internal\/platform\/workspace-management\/applications\/([^/]+)\/recent-workspace$/);
      if (method === "GET" && recentWorkspaceMatch) {
        const appId = recentWorkspaceMatch[1] ?? "";
        await route.fulfill(json(capture.recentWorkspaces?.[appId] ?? null));
        return;
      }
      if (method === "POST" && /^\/api\/internal\/platform\/workspace-management\/workspaces\/[^/]+\/recent$/.test(url.pathname)) {
        await route.fulfill(json(null));
        return;
      }
      if (method === "GET" && /\/api\/internal\/platform\/workspace-management\/applications\/[^/]+\/workspaces\/[^/]+\/branch-preference$/.test(url.pathname)) {
        await route.fulfill(json(capture.recentBranchPreference ?? null));
        return;
      }
      if (method === "POST" && /\/api\/internal\/platform\/workspace-management\/applications\/[^/]+\/workspaces\/[^/]+\/branch-preference$/.test(url.pathname)) {
        const payload = JSON.parse(route.request().postData() ?? "{}") as { branch?: string };
        capture.changeBranchRequests?.push(payload.branch ?? "");
        await route.fulfill(json({
          appId: "app_gcms",
          workspaceId: "wrk_1234567890abcdef",
          branch: payload.branch ?? "",
          updatedAt: "2026-06-24T00:00:00Z"
        }));
        return;
      }
      if (method === "GET" && url.pathname === "/api/internal/platform/workspace-management/applications/app_gcms/workspace-templates") {
        await route.fulfill(json(capture.workspaceTemplates?.app_gcms ?? []));
        return;
      }
      if (method === "GET" && /\/api\/internal\/platform\/workspace-management\/applications\/app_gcms\/workspace-templates\/[^/]+\/versions$/.test(url.pathname)) {
        const templateId = url.pathname.match(/\/workspace-templates\/([^/]+)\/versions$/)?.[1] ?? "";
        await route.fulfill(json(capture.workspaceVersions?.[`app_gcms:${templateId}`] ?? []));
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
        await route.fulfill(json([]));
        return;
      }
    }
    if (method === "GET" && url.pathname === "/api/internal/agent/opencode/processes/me") {
      await route.fulfill(json(opencodeProcessStatus(currentProcessStatus)));
      return;
    }
    if (method === "POST" && url.pathname === "/api/internal/agent/opencode/processes/me/initialize") {
      capture.processInitializations?.push({});
      currentProcessStatus = "READY";
      await route.fulfill(json(opencodeProcessStatus(currentProcessStatus)));
      return;
    }
    if (method === "GET" && url.pathname === "/api/workspaces") {
      await route.fulfill(json(pageOf(workspaceItems)));
      return;
    }
    if (method === "GET" && /^\/api\/workspaces\/[^/]+$/.test(url.pathname)) {
      const workspaceId = url.pathname.match(/\/api\/workspaces\/([^/]+)$/)?.[1];
      await route.fulfill(json(workspaceItems.find((item) => item.workspaceId === workspaceId) ?? workspace()));
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
      await route.fulfill(json(capture.vcsStatus ?? { status: "ready", branch: "main", defaultBranch: "main" }));
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

function opencodeProcessStatus(status: "READY" | "NEEDS_INITIALIZATION" | "UNAVAILABLE") {
  if (status === "READY") {
    return {
      status,
      initializable: false,
      message: "opencode 进程可用",
      processId: "ocp_1234567890abcdef",
      linuxServerId: "10.8.0.12",
      containerId: "ctr_01",
      port: 4096,
      baseUrl: "http://10.8.0.12:4096",
      checkedAt: "2026-06-24T00:00:00Z"
    };
  }
  return {
    status,
    initializable: status === "NEEDS_INITIALIZATION",
    message: status === "NEEDS_INITIALIZATION" ? "需要初始化 opencode 进程" : "没有可用的 opencode 容器",
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
