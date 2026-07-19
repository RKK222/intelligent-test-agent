import { expect, test, type Page } from "@playwright/test";
import { applicationWorkspaceRestrictionsFixture as permissionFixture } from "../../../tests/fixtures/application-workspace-restrictions";

test("workbench opens a workspace file with mocked backend api", async ({ page }) => {
  const fileReadRequests: Array<{ workspaceId: string; path: string; attempt: number }> = [];
  await mockBackendApi(page, {
    fileReadRequests,
    fileContents: {
      "tests/checkout.spec.ts": "// nonempty workspace file\nexport const checkout = true;\n"
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
  await page.addInitScript(() => {
    localStorage.setItem("test-agent.onboarding.v2:usr_admin", "seen");
  });

  await gotoWorkbench(page, { selectConversation: false });

  await expect(page.getByText("MIMO测试智能体")).toBeVisible();
  await expect(page.getByRole("button", { name: "关闭运行与终端" })).toBeVisible();
  const workspaceTreeRow = page.getByRole("button", { name: "tests", exact: true });
  await expect(workspaceTreeRow).toBeVisible();
  await expect.poll(() => workspaceTreeRow.evaluate((el) => getComputedStyle(el).height)).toBe("22px");
  await expect.poll(() => workspaceTreeRow.evaluate((el) => getComputedStyle(el).fontSize)).toBe("13px");
  const agentRootRow = page.locator(".agent-root-row").first();
  await expect(agentRootRow).toBeVisible();
  await expect.poll(() => agentRootRow.evaluate((el) => getComputedStyle(el).height)).toBe("22px");
  await page.getByRole("button", { name: "tests", exact: true }).click();
  await page.getByRole("button", { name: "checkout.spec.ts", exact: true }).click();
  await expect(page.getByRole("tab", { name: /checkout\.spec\.ts/ })).toHaveAttribute("aria-selected", "true");
  await expect(page.getByRole("textbox", { name: "Editor content" })).toBeVisible();
  await expect(page.locator(".monaco-editor")).toContainText("nonempty workspace file", { timeout: 10_000 });
  expect(fileReadRequests).toEqual([
    { workspaceId: "wrk_personal_default", path: "tests/checkout.spec.ts", attempt: 1 }
  ]);
});

test("workspace directory move keeps expanded descendants and reloads a pending child tab before reverse undo", async ({ page }) => {
  const workspaceMoveRequests: Array<{ workspaceId: string; sourcePath: string; targetPath: string }> = [];
  const fileReadRequests: Array<{ workspaceId: string; path: string; attempt: number }> = [];
  const response = (entries: Array<Record<string, unknown>>) => ({ entries, warnings: [], truncated: false });
  await mockBackendApi(page, {
    workspaceMoveRequests,
    fileReadRequests,
    fileContents: { "src/nested/guide.md": "moved guide content" },
    fileReadDelays: { "src/nested/guide.md": [3000] },
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
    },
    workspaceViewLists: {
      "COMPOSITE::": response([
        workspaceViewDirectoryEntry("workspace:src-old", "src"),
        workspaceViewDirectoryEntry("workspace:archive", "archive")
      ]),
      "WORKSPACE::src": response([
        workspaceViewDirectoryEntry("workspace:src-nested-old", "src/nested")
      ]),
      "WORKSPACE::src/nested": response([
        workspaceViewFileEntry("workspace:src-guide-old", "src/nested/guide.md")
      ])
    },
    workspaceViewListsAfterMoves: [
      {
        "COMPOSITE::": response([workspaceViewDirectoryEntry("workspace:archive", "archive")]),
        "WORKSPACE::archive": response([
          workspaceViewDirectoryEntry("workspace:archive-src-new", "archive/src")
        ]),
        "WORKSPACE::archive/src": response([
          workspaceViewDirectoryEntry("workspace:archive-src-nested-new", "archive/src/nested")
        ]),
        "WORKSPACE::archive/src/nested": response([
          workspaceViewFileEntry("workspace:archive-src-guide-new", "archive/src/nested/guide.md")
        ])
      },
      {
        "COMPOSITE::": response([
          workspaceViewDirectoryEntry("workspace:src-restored", "src"),
          workspaceViewDirectoryEntry("workspace:archive", "archive")
        ]),
        "WORKSPACE::archive": response([]),
        "WORKSPACE::src": response([
          workspaceViewDirectoryEntry("workspace:src-nested-restored", "src/nested")
        ]),
        "WORKSPACE::src/nested": response([
          workspaceViewFileEntry("workspace:src-guide-restored", "src/nested/guide.md")
        ])
      }
    ]
  });

  await gotoWorkbench(page, { selectConversation: false });
  await page.getByRole("button", { name: "src", exact: true }).click();
  await page.getByRole("button", { name: "nested", exact: true }).click();
  await page.getByRole("button", { name: "guide.md", exact: true }).click();
  await expect.poll(() => fileReadRequests.some((request) => request.path === "src/nested/guide.md")).toBe(true);

  const source = page.getByRole("button", { name: "src", exact: true });
  const target = page.getByRole("button", { name: "archive", exact: true });
  const dataTransfer = await page.evaluateHandle(() => new DataTransfer());
  await source.dispatchEvent("dragstart", { dataTransfer });
  await target.dispatchEvent("dragover", { dataTransfer });
  await target.dispatchEvent("drop", { dataTransfer });
  await source.dispatchEvent("dragend", { dataTransfer });

  await expect.poll(() => workspaceMoveRequests).toEqual([
    { workspaceId: "wrk_personal_default", sourcePath: "src", targetPath: "archive/src" }
  ]);
  await expect(page.getByRole("button", { name: "src", exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "nested", exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "guide.md", exact: true })).toBeVisible();
  await expect(page.locator(".monaco-editor")).toContainText("moved guide content", { timeout: 10_000 });

  await page.getByRole("button", { name: "src", exact: true }).click({ button: "right" });
  await page.getByRole("menuitem", { name: /撤销/ }).click();
  await expect.poll(() => workspaceMoveRequests).toEqual([
    { workspaceId: "wrk_personal_default", sourcePath: "src", targetPath: "archive/src" },
    { workspaceId: "wrk_personal_default", sourcePath: "archive/src", targetPath: "src" }
  ]);
  await expect(page.getByRole("button", { name: "src", exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "nested", exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "guide.md", exact: true })).toBeVisible();
});

test("workspace tree merges references with source colors and exposes non-merged aliases", async ({ page }) => {
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
    },
    workspaceViewLists: {
      "COMPOSITE::": {
        entries: [
          {
            id: "composite:docs",
            path: "docs",
            name: "docs",
            directory: true,
            size: 0,
            locator: { kind: "COMPOSITE", path: "docs" },
            source: "MIXED",
            merged: true,
            collision: false,
            readonly: false,
            workspacePath: "docs",
            referenceAliases: ["docs-assets"]
          },
          {
            id: "reference:spec-assets",
            path: "spec-assets",
            name: "spec-assets",
            directory: true,
            size: 0,
            locator: { kind: "REFERENCE", path: "", referenceAlias: "spec-assets" },
            source: "REFERENCE",
            merged: false,
            collision: false,
            readonly: true,
            referenceAliases: ["spec-assets"]
          }
        ],
        warnings: [],
        truncated: false
      },
      "COMPOSITE::docs": {
        entries: [
          {
            id: "workspace:docs/local.md",
            path: "docs/local.md",
            name: "local.md",
            directory: false,
            size: 5,
            locator: { kind: "WORKSPACE", path: "docs/local.md" },
            source: "WORKSPACE",
            merged: false,
            collision: false,
            readonly: false,
            workspacePath: "docs/local.md",
            referenceAliases: []
          },
          {
            id: "reference:docs-assets:guide.md",
            path: "docs/guide.md",
            name: "guide.md",
            directory: false,
            size: 15,
            locator: { kind: "REFERENCE", path: "guide.md", referenceAlias: "docs-assets" },
            source: "REFERENCE",
            merged: true,
            collision: false,
            readonly: true,
            referenceAliases: ["docs-assets"]
          },
          {
            id: "reference:docs-assets:same.md",
            path: "docs/same.md",
            name: "same.md",
            directory: false,
            size: 9,
            locator: { kind: "REFERENCE", path: "same.md", referenceAlias: "docs-assets" },
            source: "REFERENCE",
            merged: true,
            collision: true,
            readonly: true,
            referenceAliases: ["docs-assets"]
          }
        ],
        warnings: [],
        truncated: false
      },
      "REFERENCE:spec-assets:": {
        entries: [
          {
            id: "reference:spec-assets:spec.md",
            path: "spec-assets/spec.md",
            name: "spec.md",
            directory: false,
            size: 13,
            locator: { kind: "REFERENCE", path: "spec.md", referenceAlias: "spec-assets" },
            source: "REFERENCE",
            merged: false,
            collision: false,
            readonly: true,
            referenceAliases: ["spec-assets"]
          }
        ],
        warnings: [],
        truncated: false
      }
    },
    workspaceViewContents: {
      "REFERENCE:docs-assets:guide.md": "reference guide",
      "REFERENCE:docs-assets:same.md": "collision",
      "REFERENCE:spec-assets:spec.md": "standalone spec"
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  const docs = page.getByRole("button", { name: "docs", exact: true });
  const alias = page.getByRole("button", { name: "spec-assets", exact: true });
  await expect(docs).toBeVisible();
  await expect(alias).toBeVisible();
  await expect(docs).not.toHaveClass(/is-reference-merged/);
  await expect(alias).not.toHaveClass(/is-reference-merged/);

  await docs.click();
  const guide = page.getByRole("button", { name: "guide.md", exact: true });
  await expect(guide).toHaveClass(/is-reference-merged/);
  await expect(page.getByRole("button", { name: "same.md", exact: true }))
    .toHaveClass(/is-reference-collision/);
  await guide.click();
  await expect(page.getByTestId("file-load-state")).toHaveAttribute("data-state", "loaded");
  await expect(page.locator(".monaco-editor")).toContainText("reference guide");
  await page.getByRole("textbox", { name: "Editor content" }).focus();
  await page.keyboard.press("End");
  await page.keyboard.type(" must remain readonly");
  await expect(page.locator(".monaco-editor")).not.toContainText("must remain readonly");

  await alias.click();
  const standalone = page.getByRole("button", { name: "spec.md", exact: true });
  await expect(standalone).toBeVisible();
  await expect(standalone).not.toHaveClass(/is-reference-merged/);
});

test("Agent files open through the parent loader for public and workspace scopes", async ({ page }) => {
  const agentFileFrames: Array<{
    op: string;
    scope: string;
    path: string;
    workspaceId?: string;
    worktreeId?: string;
    attempt?: number;
    content?: string;
  }> = [];
  await mockBackendApi(page, {
    ...agentWorkspaceSetup(),
    agentFileFrames,
    agentFileContents: {
      "PUBLIC:agents/public-agent.md": "# public Agent content\n",
      "WORKSPACE:agents/workspace-agent.md": "# workspace Agent content\n"
    },
    agentFileReadDelays: {
      "PUBLIC:agents/public-agent.md": [500]
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  const publicAgentsDirectory = page.getByRole("button", { name: "agents", exact: true });
  await expect(publicAgentsDirectory).toHaveCount(1);
  await publicAgentsDirectory.click();
  await page.getByRole("button", { name: "public-agent.md", exact: true }).click();
  await expect(page.getByTestId("file-load-state")).toHaveAttribute("data-state", "loading");
  await expect(page.locator(".monaco-editor")).toContainText("public Agent content", { timeout: 10_000 });

  await page.getByRole("button", { name: /应用级/ }).click();
  const agentDirectories = page.getByRole("button", { name: "agents", exact: true });
  await expect(agentDirectories).toHaveCount(2);
  await agentDirectories.nth(1).click();
  await page.getByRole("button", { name: "workspace-agent.md", exact: true }).click();
  await expect(page.locator(".monaco-editor")).toContainText("workspace Agent content", { timeout: 10_000 });

  await expect.poll(() => agentFileFrames.filter((frame) => frame.op === "agent-config.read")).toEqual([
    {
      op: "agent-config.read",
      scope: "PUBLIC",
      path: "agents/public-agent.md",
      workspaceId: undefined,
      worktreeId: undefined,
      attempt: 1
    },
    {
      op: "agent-config.read",
      scope: "WORKSPACE",
      path: "agents/workspace-agent.md",
      workspaceId: "wrk_feature_agent",
      worktreeId: undefined,
      attempt: 1
    }
  ]);

  await page.getByRole("textbox", { name: "Editor content" }).focus();
  await page.keyboard.press("ControlOrMeta+A");
  await page.keyboard.type("# workspace Agent updated");
  await page.locator(".ta-workbench-footer-save").click();
  await expect.poll(() => agentFileFrames.find((frame) => frame.op === "agent-config.write")).toMatchObject({
    op: "agent-config.write",
    scope: "WORKSPACE",
    path: "agents/workspace-agent.md",
    workspaceId: "wrk_feature_agent",
    worktreeId: undefined
  });
});

test("Agent loading distinguishes empty files, retries failures, and reuses loaded tab cache", async ({ page }) => {
  const agentFileFrames: Array<{
    op: string;
    scope: string;
    path: string;
    attempt?: number;
  }> = [];
  await mockBackendApi(page, {
    ...agentWorkspaceSetup(),
    agentFileFrames,
    agentFileContents: {
      "WORKSPACE:agents/empty-agent.md": "",
      "WORKSPACE:agents/retry-agent.md": "# retry Agent succeeded"
    },
    agentFileReadFailureAttempts: {
      "WORKSPACE:agents/retry-agent.md": [1]
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  await page.getByRole("button", { name: /应用级/ }).click();
  await page.getByRole("button", { name: "agents", exact: true }).last().click();
  await page.getByRole("button", { name: "empty-agent.md", exact: true }).click();
  await expect(page.getByTestId("file-load-state")).toHaveAttribute("data-state", "loaded");

  const retryRow = page.getByRole("button", { name: "retry-agent.md", exact: true });
  await retryRow.click();
  await expect(page.getByText("读取文件失败", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "重试读取文件" }).click();
  await expect(page.locator(".monaco-editor")).toContainText("retry Agent succeeded", { timeout: 10_000 });
  await page.getByRole("textbox", { name: "Editor content" }).focus();
  await page.keyboard.press("End");
  await page.keyboard.type(" and remains editable");
  await expect(page.locator(".monaco-editor")).toContainText("and remains editable");
  await page.locator(".ta-workbench-footer-save").click();
  await expect(page.locator(".ta-workbench-footer-save")).toHaveCount(0);
  const savedMessage = page.getByRole("alert").filter({ hasText: "文件已保存" });
  await expect(savedMessage).toBeVisible();
  await savedMessage.locator(".el-message__closeBtn").click();
  await expect(savedMessage).toBeHidden();

  const retryReads = () => agentFileFrames.filter((frame) => (
    frame.op === "agent-config.read" && frame.path === "agents/retry-agent.md"
  ));
  await expect.poll(retryReads).toHaveLength(2);
  await page.getByRole("tab").filter({ hasText: "empty-agent.md" }).click();
  await page.getByRole("tab").filter({ hasText: "retry-agent.md" }).click();
  await page.waitForTimeout(50);
  expect(retryReads()).toHaveLength(2);

  // 文件树重复点击表示显式刷新，clean tab 应重新读取；顶部 tab 激活则只使用缓存。
  await retryRow.click();
  await expect.poll(retryReads).toHaveLength(3);
});

test("late Agent responses update only their own tab and same-path stale responses are discarded", async ({ page }) => {
  const agentFileFrames: Array<{ op: string; scope: string; path: string; attempt?: number }> = [];
  await mockBackendApi(page, {
    ...agentWorkspaceSetup(),
    agentFileFrames,
    agentFileContents: {
      "PUBLIC:agents/agent-a.md": "# Agent A fallback",
      "PUBLIC:agents/agent-b.md": "# Agent B response",
      "PUBLIC:agents/agent-same.md": "# Agent same fallback",
      "PUBLIC:agents/agent-closing.md": "# Agent must stay closed"
    },
    agentFileReadDelays: {
      "PUBLIC:agents/agent-a.md": [250],
      "PUBLIC:agents/agent-b.md": [20],
      "PUBLIC:agents/agent-same.md": [250, 20],
      "PUBLIC:agents/agent-closing.md": [250]
    },
    agentFileReadResponses: {
      "PUBLIC:agents/agent-a.md": ["# Agent A response"],
      "PUBLIC:agents/agent-same.md": ["# stale Agent response", "# newest Agent response"]
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  await page.getByRole("button", { name: "agents", exact: true }).click();
  await page.getByRole("button", { name: "agent-a.md", exact: true }).click();
  await page.getByRole("tab").filter({ hasText: "agent-a.md" }).click();
  await page.getByRole("button", { name: "agent-b.md", exact: true }).click();
  await expect(page.locator(".monaco-editor")).toContainText("Agent B response", { timeout: 10_000 });
  await page.waitForTimeout(300);
  await expect(page.locator(".monaco-editor")).toContainText("Agent B response");
  await page.getByRole("tab").filter({ hasText: "agent-a.md" }).click();
  await expect(page.locator(".monaco-editor")).toContainText("Agent A response");

  const sameRow = page.getByRole("button", { name: "agent-same.md", exact: true });
  await sameRow.click();
  await sameRow.click();
  await expect(page.locator(".monaco-editor")).toContainText("newest Agent response", { timeout: 10_000 });
  await page.waitForTimeout(300);
  await expect(page.locator(".monaco-editor")).not.toContainText("stale Agent response");

  await page.getByRole("button", { name: "agent-closing.md", exact: true }).click();
  const closingTab = page.getByRole("tab").filter({ hasText: "agent-closing.md" });
  await closingTab.getByRole("button", { name: "关闭标签" }).click();
  await page.waitForTimeout(300);
  await expect(closingTab).toHaveCount(0);
  expect(agentFileFrames.filter((frame) => frame.op === "agent-config.read" && frame.path === "agents/agent-a.md")).toHaveLength(1);
  expect(agentFileFrames.filter((frame) => frame.op === "agent-config.read" && frame.path === "agents/agent-same.md")).toHaveLength(2);
});

test("dirty Agent tabs and edits made during refresh are never overwritten", async ({ page }) => {
  const agentFileFrames: Array<{
    op: string;
    scope: string;
    path: string;
    workspaceId?: string;
    attempt?: number;
    content?: string;
  }> = [];
  await mockBackendApi(page, {
    ...agentWorkspaceSetup(),
    agentFileFrames,
    agentFileContents: {
      "WORKSPACE:agents/dirty-agent.md": "initial Agent disk content"
    },
    agentFileReadDelays: {
      "WORKSPACE:agents/dirty-agent.md": [0, 350]
    },
    agentFileReadResponses: {
      "WORKSPACE:agents/dirty-agent.md": ["initial Agent disk content", "stale Agent refresh response"]
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  await page.getByRole("button", { name: /应用级/ }).click();
  await page.getByRole("button", { name: "agents", exact: true }).last().click();
  const row = page.getByRole("button", { name: "dirty-agent.md", exact: true });
  await row.click();
  await expect(page.locator(".monaco-editor")).toContainText("initial Agent disk content", { timeout: 10_000 });

  await page.locator(".monaco-editor .view-line").first().click();
  await page.keyboard.press("ControlOrMeta+A");
  await page.keyboard.type("local dirty Agent content");
  await row.click();
  await page.waitForTimeout(50);
  expect(agentFileFrames.filter((frame) => frame.op === "agent-config.read" && frame.path === "agents/dirty-agent.md")).toHaveLength(1);

  await page.locator(".ta-workbench-footer-save").click();
  await expect.poll(() => agentFileFrames.filter((frame) => frame.op === "agent-config.write")).toHaveLength(1);
  await row.click();
  await page.locator(".monaco-editor .view-line").first().click();
  await page.keyboard.press("ControlOrMeta+A");
  await page.keyboard.type("saved while Agent refresh is pending");
  await page.locator(".ta-workbench-footer-save").click();
  await expect.poll(() => agentFileFrames.filter((frame) => frame.op === "agent-config.write")).toHaveLength(2);
  await page.waitForTimeout(400);
  await expect(page.locator(".monaco-editor")).toContainText("saved while Agent refresh is pending");
  await expect(page.locator(".monaco-editor")).not.toContainText("stale Agent refresh response");
  await expect(page.locator(".ta-workbench-footer-save")).toHaveCount(0);
});

test("Agent refresh failures preserve cache and a missing clean file closes its tab", async ({ page }) => {
  const agentFileFrames: Array<{ op: string; scope: string; path: string; attempt?: number }> = [];
  await mockBackendApi(page, {
    ...agentWorkspaceSetup(),
    agentFileFrames,
    agentFileContents: {
      "PUBLIC:agents/cached-agent.md": "stable Agent cache",
      "PUBLIC:agents/removed-agent.md": "Agent file before removal"
    },
    agentFileReadDelays: {
      "PUBLIC:agents/cached-agent.md": [0, 250, 20]
    },
    agentFileReadFailureAttempts: {
      "PUBLIC:agents/cached-agent.md": [3]
    },
    agentFileReadNotFoundAttempts: {
      "PUBLIC:agents/removed-agent.md": [2]
    },
    agentFileReadResponses: {
      "PUBLIC:agents/cached-agent.md": ["stable Agent cache", "stale Agent cache refresh"]
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  await page.getByRole("button", { name: "agents", exact: true }).click();
  const cachedRow = page.getByRole("button", { name: "cached-agent.md", exact: true });
  await cachedRow.click();
  await expect(page.locator(".monaco-editor")).toContainText("stable Agent cache", { timeout: 10_000 });
  await cachedRow.click();
  await cachedRow.click();
  await expect(page.getByText(/刷新文件失败，已保留上次内容/)).toBeVisible();
  await expect(page.getByTestId("file-load-state")).toHaveAttribute("data-state", "loaded");
  await expect(page.locator(".monaco-editor")).toContainText("stable Agent cache");
  await page.waitForTimeout(300);
  await expect(page.locator(".monaco-editor")).not.toContainText("stale Agent cache refresh");

  await page.getByRole("button", { name: "removed-agent.md", exact: true }).click();
  const removedTab = page.getByRole("tab").filter({ hasText: "removed-agent.md" });
  await expect(page.locator(".monaco-editor")).toContainText("Agent file before removal");
  await page.getByRole("button", { name: "刷新", exact: true }).click();
  await expect(removedTab).toHaveCount(0);
});

test("switching application context discards a loading Agent response", async ({ page }) => {
  await mockBackendApi(page, {
    ...agentWorkspaceSetup(),
    applications: [
      { appId: "app_gcms", appName: "F-GCMS", enabled: true },
      { appId: "app_coss", appName: "F-COSS", enabled: true }
    ],
    recentWorkspaces: {
      ...agentWorkspaceSetup().recentWorkspaces,
      app_coss: null
    },
    agentFileContents: {
      "WORKSPACE:agents/context-agent.md": "# stale Agent context response"
    },
    agentFileReadDelays: {
      "WORKSPACE:agents/context-agent.md": [300]
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  await page.getByRole("button", { name: /应用级/ }).click();
  await page.getByRole("button", { name: "agents", exact: true }).last().click();
  await page.getByRole("button", { name: "context-agent.md", exact: true }).click();
  await page.getByRole("button", { name: "F-GCMS", exact: true }).click();
  await page.getByRole("option", { name: /F-COSS/ }).click();
  await expect(page.getByText("当前应用尚未切换到可用工作区。")).toBeVisible();
  await page.waitForTimeout(350);
  await expect(page.getByRole("tab").filter({ hasText: "context-agent.md" })).toHaveCount(0);
  await expect(page.getByText("stale Agent context response")).toHaveCount(0);
});

test("switching public Agent routes settles an old load and allows retry after returning", async ({ page }) => {
  await mockBackendApi(page, {
    ...agentWorkspaceSetup(),
    authRoles: ["SUPER_ADMIN"],
    publicAgentRepositories: [
      publicAgentRepository("server-a", "backend-a"),
      publicAgentRepository("server-b", "backend-b")
    ],
    publicAgentWorktreesByServer: {
      "server-a": [publicAgentWorktree("server-a")],
      "server-b": [publicAgentWorktree("server-b")]
    },
    agentFileContents: {
      "PUBLIC:public-route.md": "# fallback public route"
    },
    agentFileReadDelays: {
      "PUBLIC:public-route.md": [2_000, 0]
    },
    agentFileReadResponses: {
      "PUBLIC:public-route.md": ["# stale server A response", "# fresh server A response"]
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  await page.getByRole("button", { name: "public-route.md", exact: true }).click();
  await expect(page.getByTestId("file-load-state")).toHaveAttribute("data-state", "loading");
  // 折叠目录可让路由切换只验证 tab 失效/重试，不等待新服务器的目录重载。
  await page.getByRole("button", { name: /公共级/ }).click();

  await page.getByRole("button", { name: "更多操作" }).hover();
  await expect(page.getByRole("button", { name: "创建公共 worktree" })).toBeVisible();
  await page.getByRole("button", { name: "切换公共 worktree" }).click();
  await page.getByRole("dialog", { name: "切换公共 worktree" }).getByLabel("服务器").selectOption("server-b");
  await page.getByRole("dialog", { name: "切换公共 worktree" }).getByRole("button", { name: "确定" }).click();
  await expect(page.getByTestId("file-load-state")).toHaveAttribute("data-state", "error");

  await page.getByRole("button", { name: "更多操作" }).hover();
  await page.getByRole("button", { name: "切换公共 worktree" }).click();
  await page.getByRole("dialog", { name: "切换公共 worktree" }).getByLabel("服务器").selectOption("server-a");
  await page.getByRole("dialog", { name: "切换公共 worktree" }).getByRole("button", { name: "确定" }).click();
  await page.getByRole("tab").filter({ hasText: "public-route.md" }).click();
  await expect(page.locator(".monaco-editor")).toContainText("fresh server A response", { timeout: 10_000 });
  await page.waitForTimeout(2_050);
  await expect(page.locator(".monaco-editor")).not.toContainText("stale server A response");
});

test("workspace file loading distinguishes an empty file and supports retry after an initial failure", async ({ page }) => {
  const fileReadRequests: Array<{ workspaceId: string; path: string; attempt: number }> = [];
  await mockBackendApi(page, {
    fileReadRequests,
    fileContents: {
      "docs/empty.md": "",
      "docs/retry.md": "# retry succeeded"
    },
    fileReadFailuresBeforeSuccess: { "docs/retry.md": 2 },
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
  await page.getByRole("button", { name: "docs", exact: true }).click();
  await page.getByRole("button", { name: "empty.md", exact: true }).click();
  await expect(page.getByTestId("file-load-state")).toHaveAttribute("data-state", "loaded");

  await page.getByRole("button", { name: "retry.md", exact: true }).click();
  await expect(page.getByText("读取文件失败", { exact: true })).toBeVisible();
  await page.getByRole("tab").filter({ hasText: "empty.md" }).click();
  await page.getByRole("tab").filter({ hasText: "retry.md" }).click();
  await expect(page.getByText("读取文件失败", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "重试读取文件" }).click();
  await expect(page.locator(".monaco-editor")).toContainText("retry succeeded", { timeout: 10_000 });
  expect(fileReadRequests.filter((item) => item.path === "docs/retry.md")).toHaveLength(3);
});

test("initial file loading is not editable and applies the response readonly state", async ({ page }) => {
  const readonlyWorkspace = {
    ...workspace(),
    workspaceId: "wrk_readonly_history",
    name: "只读历史工作区",
    rootPath: "/Users/huang/workspace/readonly-history",
    appId: "app_coss",
    versionId: "awv_readonly_history",
    applicationWorkspaceId: "awp_readonly_history"
  };
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    fileContents: { "docs/initial-readonly.md": "disk readonly content" },
    fileReadDelays: { "docs/initial-readonly.md": [250] },
    applications: [
      { appId: "app_gcms", appName: "F-GCMS", enabled: true },
      { appId: "app_coss", appName: "F-COSS", enabled: true }
    ],
    workspaces: [workspace(), readonlyWorkspace],
    markRecentWorkspaces: { wrk_readonly_history: readonlyWorkspace },
    sessions: [{
      sessionId: "ses_readonly_history",
      workspaceId: "wrk_readonly_history",
      title: "只读历史会话",
      status: "ACTIVE",
      pinned: false,
      createdAt: "2026-07-08T08:00:00Z",
      updatedAt: "2026-07-08T09:00:00Z",
      workspaceContext: {
        appId: "app_coss",
        appName: "F-COSS",
        applicationWorkspaceId: "awp_readonly_history",
        workspaceName: "只读历史工作区",
        versionId: "awv_readonly_history",
        version: "20260708"
      }
    }],
    sessionMessages: [{
      messageId: "msg_readonly_history",
      sessionId: "ses_readonly_history",
      role: "USER",
      content: "只读历史会话",
      createdAt: "2026-07-08T08:00:00Z"
    }]
  });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: "消息列表" }).click();
  await page.getByRole("button", { name: /只读历史会话/ }).click();
  await expect(page.getByRole("button", { name: "F-COSS" })).toBeVisible();
  await expect(page.getByRole("button", { name: "docs", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "docs", exact: true }).click();
  await page.getByRole("button", { name: "initial-readonly.md", exact: true }).click();
  await expect(page.getByTestId("file-load-state")).toHaveAttribute("data-state", "loading");
  await expect(page.locator(".monaco-editor")).toHaveCount(0);
  await expect(page.getByTestId("file-load-state")).toHaveAttribute("data-state", "loaded");
  await expect(page.locator(".monaco-editor")).toContainText("disk readonly content");

  await page.getByRole("textbox", { name: "Editor content" }).focus();
  await page.keyboard.press("End");
  await page.keyboard.type(" must remain readonly");
  await expect(page.locator(".monaco-editor")).not.toContainText("must remain readonly");
});

test("late file responses update only their own tab and same-path stale responses are discarded", async ({ page }) => {
  const fileReadRequests: Array<{ workspaceId: string; path: string; attempt: number }> = [];
  await mockBackendApi(page, {
    fileReadRequests,
    fileContents: {
      "docs/a.md": "# A response",
      "docs/b.md": "# B response",
      "docs/same.md": "# fallback"
    },
    fileReadDelays: {
      "docs/a.md": [250],
      "docs/b.md": [20],
      "docs/same.md": [250, 20]
    },
    fileReadResponses: {
      "docs/same.md": ["# stale same-path response", "# newest same-path response"]
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

  await gotoWorkbench(page, { selectConversation: false });
  await page.getByRole("button", { name: "docs", exact: true }).click();
  await page.getByRole("button", { name: "a.md", exact: true }).click();
  await page.getByRole("tab").filter({ hasText: "a.md" }).click();
  expect(fileReadRequests.filter((item) => item.path === "docs/a.md")).toHaveLength(1);
  await page.getByRole("button", { name: "b.md", exact: true }).click();
  await expect(page.locator(".monaco-editor")).toContainText("B response", { timeout: 10_000 });
  await page.waitForTimeout(300);
  await expect(page.locator(".monaco-editor")).toContainText("B response");
  await page.getByRole("tab").filter({ hasText: "a.md" }).click();
  await expect(page.locator(".monaco-editor")).toContainText("A response");
  expect(fileReadRequests.filter((item) => item.path === "docs/a.md")).toHaveLength(1);

  await page.getByRole("button", { name: "same.md", exact: true }).click();
  await page.getByRole("button", { name: "same.md", exact: true }).click();
  await expect(page.locator(".monaco-editor")).toContainText("newest same-path response", { timeout: 10_000 });
  await page.waitForTimeout(300);
  await expect(page.locator(".monaco-editor")).not.toContainText("stale same-path response");
  expect(fileReadRequests.filter((item) => item.path === "docs/same.md")).toHaveLength(2);
});

test("dirty tabs are never reread or overwritten while a read is pending", async ({ page }) => {
  const fileReadRequests: Array<{ workspaceId: string; path: string; attempt: number }> = [];
  await mockBackendApi(page, {
    fileReadRequests,
    fileContents: { "docs/dirty.md": "initial disk content" },
    fileReadDelays: { "docs/dirty.md": [0, 250] },
    fileReadResponses: { "docs/dirty.md": ["initial disk content", "new disk content"] },
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
  await page.getByRole("button", { name: "docs", exact: true }).click();
  const dirtyRow = page.getByRole("button", { name: "dirty.md", exact: true });
  await dirtyRow.click();
  await expect(page.locator(".monaco-editor")).toContainText("initial disk content", { timeout: 10_000 });

  await dirtyRow.click();
  await page.locator(".monaco-editor .view-line").first().click();
  await page.keyboard.press("ControlOrMeta+A");
  await page.keyboard.type("local unsaved content");
  await expect(page.locator(".monaco-editor")).toContainText("local unsaved content");
  await page.waitForTimeout(300);
  await expect(page.locator(".monaco-editor")).toContainText("local unsaved content");

  const readsBeforeDirtyReopen = fileReadRequests.filter((item) => item.path === "docs/dirty.md").length;
  await dirtyRow.click();
  await page.waitForTimeout(50);
  expect(fileReadRequests.filter((item) => item.path === "docs/dirty.md")).toHaveLength(readsBeforeDirtyReopen);
});

test("a stale read cannot overwrite content edited and saved during refresh", async ({ page }) => {
  const fileWriteRequests: Array<{ workspaceId: string; path: string; content: string }> = [];
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    fileWriteRequests,
    fileContents: { "docs/save-during-refresh.md": "base disk content" },
    fileReadDelays: { "docs/save-during-refresh.md": [0, 400] },
    fileReadResponses: {
      "docs/save-during-refresh.md": ["base disk content", "stale refresh response"]
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  await page.getByRole("button", { name: "docs", exact: true }).click();
  const row = page.getByRole("button", { name: "save-during-refresh.md", exact: true });
  await row.click();
  await expect(page.locator(".monaco-editor")).toContainText("base disk content", { timeout: 10_000 });

  await row.click();
  await page.locator(".monaco-editor .view-line").first().click();
  await page.keyboard.press("ControlOrMeta+A");
  await page.keyboard.type("saved while refresh is pending");
  await page.locator(".ta-workbench-footer-save").click();
  await expect.poll(() => fileWriteRequests.length).toBe(1);
  expect(fileWriteRequests[0]).toMatchObject({
    workspaceId: "wrk_personal_default",
    path: "docs/save-during-refresh.md"
  });
  expect(fileWriteRequests[0]?.content).toContain("saved while refresh is pending");
  await expect(page.locator(".ta-workbench-footer-save")).toHaveCount(0);

  await page.waitForTimeout(450);
  await expect(page.locator(".monaco-editor")).toContainText("saved while refresh is pending");
  await expect(page.locator(".monaco-editor")).not.toContainText("stale refresh response");
  // 迟到响应后仍保持 clean，结合 Monaco 正文可证明 savedContent 仍是刚保存的本地版本。
  await expect(page.locator(".ta-workbench-footer-save")).toHaveCount(0);
});

test("overlapping refresh failure preserves the previously loaded cache", async ({ page }) => {
  const fileReadRequests: Array<{ workspaceId: string; path: string; attempt: number }> = [];
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    fileReadRequests,
    fileContents: { "docs/overlap.md": "stable cached content" },
    fileReadDelays: { "docs/overlap.md": [0, 250, 20] },
    fileReadFailureAttempts: { "docs/overlap.md": [3] },
    fileReadResponses: {
      "docs/overlap.md": ["stable cached content", "stale first refresh content"]
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  await page.getByRole("button", { name: "docs", exact: true }).click();
  const row = page.getByRole("button", { name: "overlap.md", exact: true });
  await row.click();
  await expect(page.locator(".monaco-editor")).toContainText("stable cached content", { timeout: 10_000 });

  await row.click();
  await row.click();
  await expect(page.getByText(/刷新文件失败，已保留上次内容/)).toBeVisible();
  await expect(page.getByTestId("file-load-state")).toHaveAttribute("data-state", "loaded");
  await expect(page.locator(".monaco-editor")).toContainText("stable cached content");
  await page.waitForTimeout(300);
  await expect(page.locator(".monaco-editor")).not.toContainText("stale first refresh content");
  expect(fileReadRequests.filter((item) => item.path === "docs/overlap.md")).toHaveLength(3);
});

test("closing a loading file tab discards its late response", async ({ page }) => {
  await mockBackendApi(page, {
    fileContents: { "docs/closing.md": "# must stay closed" },
    fileReadDelays: { "docs/closing.md": [250] },
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
  await page.getByRole("button", { name: "docs", exact: true }).click();
  await page.getByRole("button", { name: "closing.md", exact: true }).click();
  const tab = page.getByRole("tab").filter({ hasText: "closing.md" });
  await tab.getByRole("button", { name: "关闭标签" }).click();
  await page.waitForTimeout(300);
  await expect(tab).toHaveCount(0);
});

test("search results and conversation file entries reuse the workspace file loader", async ({ page }) => {
  const fileReadRequests: Array<{ workspaceId: string; path: string; attempt: number }> = [];
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    fileReadRequests,
    fileContents: {
      "docs/search-entry.md": "# opened from search",
      "docs/conversation-entry.md": "# opened from conversation"
    },
    runEvents: [
      event(1, "diff.proposed", {
        files: [{
          path: "docs/conversation-entry.md",
          patch: "@@ -0,0 +1 @@",
          additions: 1,
          deletions: 0,
          status: "modified"
        }]
      }),
      event(2, "run.succeeded", {})
    ]
  });

  await gotoWorkbench(page, { selectConversation: false });
  await page.getByRole("tablist", { name: "工作区面板" }).getByRole("button", { name: "搜索" }).click();
  await page.getByPlaceholder("搜索工作区文件").fill("search-entry");
  await page.getByRole("button", { name: /search-entry.md/ }).click();
  await expect(page.locator(".monaco-editor")).toContainText("opened from search", { timeout: 10_000 });

  await page.getByRole("button", { name: "新建对话" }).click();
  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("生成文件");
  await page.getByRole("button", { name: "发送" }).click();
  await page.getByRole("button", { name: "文件修改 1 文件总增减行" }).click();
  const conversationFile = page.locator(".oc-diff-file").filter({ hasText: "conversation-entry.md" });
  await expect(conversationFile).toBeVisible();
  await conversationFile.click();
  await expect(page.locator(".monaco-editor")).toContainText("opened from conversation", { timeout: 10_000 });

  expect(fileReadRequests.map((item) => item.path)).toEqual([
    "docs/search-entry.md",
    "docs/conversation-entry.md"
  ]);
});

test("switching workspace discards a loading file response from the previous workspace", async ({ page }) => {
  await mockBackendApi(page, {
    fileContents: { "docs/switching.md": "# stale previous workspace" },
    fileReadDelays: { "docs/switching.md": [250] },
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
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1",
        appId: "app_gcms"
      },
      app_coss: null
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  await page.getByRole("button", { name: "docs", exact: true }).click();
  await page.getByRole("button", { name: "switching.md", exact: true }).click();
  await page.getByRole("button", { name: "F-GCMS" }).click();
  await page.getByRole("option", { name: /F-COSS/ }).click();
  await expect(page.getByText("当前应用尚未切换到可用工作区。")).toBeVisible();
  await page.waitForTimeout(300);
  await expect(page.getByRole("tab").filter({ hasText: "switching.md" })).toHaveCount(0);
  await expect(page.getByText("stale previous workspace")).toHaveCount(0);
});

test("an old refresh loop stops before reading the next file in a new workspace", async ({ page }) => {
  test.setTimeout(45_000);
  const fileReadRequests: Array<{ workspaceId: string; path: string; attempt: number }> = [];
  const diffFiles = [
    { path: "docs/refresh-a.md", status: "modified", staged: false, patch: "@@ -1 +1 @@", additions: 1, deletions: 1 },
    { path: "docs/shared.md", status: "modified", staged: false, patch: "@@ -1 +1 @@", additions: 1, deletions: 1 }
  ];
  const cossPersonalWorkspace = {
    ...defaultPersonalWorkspace("awv_coss_refresh"),
    appId: "app_coss",
    applicationWorkspaceId: "awp_coss_refresh",
    runtimeWorkspace: {
      ...workspace(),
      workspaceId: "wrk_coss_personal",
      name: "coss-default",
      rootPath: "/Users/huang/workspace/coss-personal",
      appId: "app_coss",
      versionId: "awv_coss_refresh",
      applicationWorkspaceId: "awp_coss_refresh"
    }
  };
  await mockBackendApi(page, {
    fileReadRequests,
    fileContents: {
      "docs/refresh-a.md": "refresh A",
      "docs/shared.md": "shared content"
    },
    fileReadDelays: {
      "docs/refresh-a.md": [0, 800],
      "docs/shared.md": [0, 0]
    },
    historyDiffFiles: diffFiles,
    authRoles: ["SUPER_ADMIN"],
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
      app_coss: {
        ...workspace(),
        workspaceId: "wrk_coss_replica",
        appId: "app_coss",
        versionId: "awv_coss_refresh",
        applicationWorkspaceId: "awp_coss_refresh"
      }
    },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")],
      awv_coss_refresh: [cossPersonalWorkspace]
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  await page.getByRole("button", { name: "docs", exact: true }).click();
  await page.getByRole("button", { name: /^refresh-a\.md(?:\s|$)/ }).click();
  await expect(page.locator(".monaco-editor")).toContainText("refresh A", { timeout: 10_000 });
  await page.getByRole("button", { name: /^shared\.md(?:\s|$)/ }).click();
  await expect(page.locator(".monaco-editor")).toContainText("shared content");

  await page.getByRole("button", { name: "变更" }).click();
  diffFiles.length = 0;
  page.once("dialog", (dialog) => dialog.accept());
  await page.getByRole("button", { name: "丢弃全部应用工作空间改动" }).click();
  await expect.poll(() => fileReadRequests.filter((item) => (
    item.workspaceId === "wrk_personal_default" && item.path === "docs/refresh-a.md"
  )).length).toBe(2);

  await page.getByRole("button", { name: "F-GCMS" }).click();
  await page.getByRole("option", { name: /F-COSS/ }).click();
  await expect(page.getByRole("button", { name: "F-COSS" })).toBeVisible();
  await page.getByRole("tablist", { name: "工作区面板" }).getByRole("button", { name: "文件树" }).click();
  await page.getByRole("button", { name: "docs", exact: true }).click();
  await page.getByRole("button", { name: /^shared\.md(?:\s|$)/ }).click();
  await expect.poll(() => fileReadRequests.filter((item) => (
    item.workspaceId === "wrk_coss_personal" && item.path === "docs/shared.md"
  )).length).toBeGreaterThanOrEqual(1);

  await page.waitForTimeout(900);
  expect(fileReadRequests.filter((item) => (
    item.workspaceId === "wrk_coss_personal" && item.path === "docs/shared.md"
  ))).toHaveLength(1);
});

test("renaming while the source file is loading reloads the target without stale overwrite", async ({ page }) => {
  const fileReadRequests: Array<{ workspaceId: string; path: string; attempt: number }> = [];
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    fileReadRequests,
    fileContents: {
      "docs/race.md": "stable renamed content"
    },
    fileReadDelays: {
      "docs/race.md": [0, 0, 900]
    },
    fileReadNotFoundAttempts: {
      "docs/race.md": [3]
    },
    workspaceMutationDelays: {
      "workspace.rename": 600
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  await page.getByRole("button", { name: "docs", exact: true }).click();
  const sourceRow = page.getByRole("button", { name: "race.md", exact: true });
  await sourceRow.dblclick();
  const renameInput = page.getByRole("textbox", { name: "重命名工作区条目" });
  await expect.poll(() => fileReadRequests.filter((request) => request.path === "docs/race.md")).toHaveLength(2);
  await expect(page.getByTestId("file-load-state")).toHaveAttribute("data-state", "loaded");
  await renameInput.fill("renamed.md");
  await renameInput.press("Enter");
  await sourceRow.dispatchEvent("click");
  await expect.poll(() => fileReadRequests.filter((request) => request.path === "docs/race.md")).toHaveLength(3);
  await expect(page.getByTestId("file-load-state")).toHaveAttribute("data-state", "loading");

  const renamedTab = page.getByRole("tab").filter({ hasText: "renamed.md" });
  await expect(renamedTab).toHaveCount(1);
  await expect(page.getByTestId("file-load-state")).toHaveAttribute("data-state", "loaded", { timeout: 10_000 });
  await expect(page.locator(".monaco-editor")).toContainText("stable renamed content");
  await page.waitForTimeout(950);
  await expect(page.locator(".monaco-editor")).toContainText("stable renamed content");
});

test("application workspace mutation entries follow member and super administrator permissions", async ({ page, context }) => {
  const managedWorkspaceSetup = {
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
    },
    workspaceTemplates: {
      app_gcms: [{
        workspaceId: "awp_1",
        workspaceName: permissionFixture.application.appName,
        appId: "app_gcms",
        repositoryId: "repo_1",
        defaultBranch: permissionFixture.application.featureBranch,
        createdAt: "2026-07-15T00:00:00Z",
        updatedAt: "2026-07-15T00:00:00Z"
      }]
    },
    workspaceVersions: {
      "app_gcms:awp_1": [{
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1",
        appId: "app_gcms",
        repositoryId: "repo_1",
        version: "20260715",
        branch: permissionFixture.application.featureBranch,
        repoRootPath: "/tmp/test-agent/appworkspace/20260715/repo_1",
        workspaceRootPath: "/tmp/test-agent/appworkspace/20260715/repo_1/F-GCMS/workspace",
        runtimeWorkspace: {
          ...workspace(),
          workspaceId: permissionFixture.application.featureWorkspaceId
        },
        status: "ACTIVE",
        createdAt: "2026-07-15T00:00:00Z",
        updatedAt: "2026-07-15T00:00:00Z"
      }]
    }
  };

  await mockBackendApi(page, { ...managedWorkspaceSetup, authRoles: [...permissionFixture.roles.member] });
  await page.addInitScript(() => {
    localStorage.setItem("test-agent.onboarding.v2:usr_admin", "seen");
  });
  await gotoWorkbench(page, { selectConversation: false });

  await page.getByRole("button", { name: "tests", exact: true }).hover();
  await expect(page.getByRole("button", { name: "新建或上传到此目录" }).first()).toBeVisible();
  await expect(page.getByRole("button", { name: "初始化应用 Agent/Skill 配置包" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "创建应用 worktree" })).toHaveCount(0);

  const superPage = await context.newPage();
  await mockBackendApi(superPage, { ...managedWorkspaceSetup, authRoles: [...permissionFixture.roles.superAdmin] });
  await superPage.addInitScript(() => {
    localStorage.setItem("test-agent.onboarding.v2:usr_admin", "seen");
  });
  await gotoWorkbench(superPage, { selectConversation: false });

  await expect(superPage.getByRole("button", { name: "初始化应用 Agent/Skill 配置包" })).toBeVisible();
  await expect(superPage.getByRole("button", { name: "创建应用 worktree" })).toHaveCount(0);
  await superPage.close();
});

test("deleting an open workspace file or directory closes every affected tab", async ({ page }) => {
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    fileContents: {
      "files/delete-me.md": "delete this file",
      "docs/nested.md": "delete this directory"
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  await page.getByRole("button", { name: "files", exact: true }).click();
  const deleteMeRow = page.getByRole("button", { name: "delete-me.md", exact: true });
  await deleteMeRow.click();
  const deleteMeTab = page.getByRole("tab").filter({ hasText: "delete-me.md" });
  await expect(deleteMeTab).toHaveCount(1);
  await deleteMeRow.hover();
  await page.getByRole("button", { name: "删除 delete-me.md" }).click();
  await page.getByRole("dialog", { name: "删除文件" }).getByRole("button", { name: "确认删除" }).click();
  await expect(deleteMeTab).toHaveCount(0);

  const deletedFileMessage = page.getByRole("alert").filter({ hasText: "文件已删除" });
  await expect(deletedFileMessage).toBeVisible();
  await deletedFileMessage.locator(".el-message__closeBtn").click();
  await expect(deletedFileMessage).toBeHidden();

  const docsRow = page.getByRole("button", { name: "docs", exact: true });
  await docsRow.click();
  await page.getByRole("button", { name: "nested.md", exact: true }).click();
  const nestedTab = page.getByRole("tab").filter({ hasText: "nested.md" });
  await expect(nestedTab).toHaveCount(1);
  await docsRow.hover();
  await page.getByRole("button", { name: "删除 docs" }).click();
  await page.getByRole("dialog", { name: "删除文件夹" }).getByRole("button", { name: "确认删除" }).click();
  await expect(nestedTab).toHaveCount(0);
});

test("workbench home opens the embedded user manual", async ({ page }) => {
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
  await page.addInitScript(() => {
    localStorage.setItem("test-agent.onboarding.v2:usr_admin", "seen");
  });

  await gotoWorkbench(page, { selectConversation: false });

  const manualEntry = page.getByTestId("workbench-home-help");
  await expect(manualEntry).toBeVisible();
  await manualEntry.click();

  await expect(page.getByTestId("help-center-dialog")).toBeVisible();
  await expect(page.getByTestId("help-center-frame")).toHaveAttribute(
    "src",
    /\/help\/guide\/getting-started\.html$/
  );
  const directoryTopic = page.getByRole("button", { name: /开发与测试目录/ });
  await expect(directoryTopic).toBeVisible();
  await directoryTopic.click();
  await expect(page.getByTestId("help-center-frame")).toHaveAttribute(
    "src",
    /\/help\/guide\/directory-mapping\.html$/
  );
  const manualFrame = page.frameLocator('[data-testid="help-center-frame"]');
  await expect(manualFrame.getByRole("button", { name: "测试目录" })).toHaveCount(0);
  await expect(manualFrame.getByText("公共 Git", { exact: true }).first()).toBeVisible();
  await expect(manualFrame.getByText("应用 Git", { exact: true }).first()).toBeVisible();
  const sharedArchive = manualFrame.getByRole("treeitem", { name: /archive\// });
  const localSpec = manualFrame.getByRole("treeitem", { name: /^spec\// });
  const agentsRoot = manualFrame.getByRole("treeitem", { name: /^agents\// });
  const developmentAgent = manualFrame.getByRole("treeitem", { name: /01_需求智能体\// });
  const testingAgent = manualFrame.getByRole("treeitem", { name: /04_测试智能体\// });
  await expect(sharedArchive).toBeVisible();
  await expect(localSpec).toBeVisible();
  await expect(manualFrame.getByRole("treeitem", { name: /2601\// })).toHaveCount(0);
  await expect(sharedArchive.locator(".scope-badge")).toHaveText("开发 + 测试");
  await expect(sharedArchive.locator(".physical-badge")).toHaveText("应用 Git");
  await expect(localSpec.locator(".scope-badge")).toHaveText("个人本地");
  await expect(localSpec.locator(".physical-badge")).toHaveText("应用 Git 个人分支 · 仅本地提交");
  await expect(agentsRoot.locator(".scope-badge")).toHaveText("开发 + 测试");
  await expect(agentsRoot.locator(".physical-badge")).toHaveText("公共 Git + 应用 Git");
  await expect(developmentAgent.locator(".scope-badge")).toHaveText("开发");
  await expect(developmentAgent.locator(".role-badge")).toHaveText("Agent");
  await expect(developmentAgent.locator(".physical-badge")).toHaveText("应用 Git");
  await expect(testingAgent.locator(".scope-badge")).toHaveText("测试");
  await expect(testingAgent.locator(".physical-badge")).toHaveText("公共 Git + 应用 Git");
  await manualFrame.getByRole("button", { name: "全部展开" }).click();
  const developmentAsset = manualFrame.getByRole("treeitem", { name: "工程概览_A.md" });
  const testingAsset = manualFrame.getByRole("treeitem", { name: "测试概述.md" });
  const testDesignAgent = manualFrame.getByRole("treeitem", { name: /^01_测试设计\// });
  const testAnalysisWorkagent = manualFrame.getByRole("treeitem", { name: /^001 Test Analysis（测试分析）\// });
  const testGenerationWorkagent = manualFrame.getByRole("treeitem", { name: /^002 Test Case Generation（测试案例生成）\// });
  const testReviewWorkagent = manualFrame.getByRole("treeitem", { name: /^003 Test Case Review（测试案例审核）\// });
  const testExecutionAgent = manualFrame.getByRole("treeitem", { name: /^02_测试执行\// });
  const apiExecutionWorkagent = manualFrame.getByRole("treeitem", { name: /^001 API Test Execution（接口测试执行）\// });
  const applicationTestDesignAgent = manualFrame.getByRole("treeitem", { name: "<应用测试设计 Agent>.md" });
  const applicationTestDesignWorkagent = manualFrame.getByRole("treeitem", { name: "<应用测试设计 workagent>.md" });
  const applicationTestExecutionAgent = manualFrame.getByRole("treeitem", { name: "<应用测试执行 Agent>.md" });
  const applicationTestExecutionWorkagent = manualFrame.getByRole("treeitem", { name: "<应用测试执行 workagent>.md" });
  const testingRuleGroups = manualFrame.locator(".tree-row.testing").filter({ hasText: "测试公共规约与应用测试规约" });
  const publicTestRule = manualFrame.getByRole("treeitem", { name: /^测试设计公共规约\// });
  const applicationTestRules = manualFrame.getByRole("treeitem", { name: /^测试设计应用规约\// });
  const developmentSkills = manualFrame.getByRole("treeitem", { name: /^coding\// });
  const testingSkills = manualFrame.getByRole("treeitem", { name: /^test\// });
  const plannedCodeReviewSkill = manualFrame.getByRole("treeitem", { name: /^code-review-skill\// });
  const applicationTestSkills = manualFrame.getByRole("treeitem", { name: /^<应用专属测试 Skill>\// });
  const sharedDocsNodes = ["应用架构/", "功能模块/", "数据架构/"].map((name) =>
    manualFrame.getByRole("treeitem", { name: new RegExp(`^${name}`) })
  );
  const technicalArchitecture = manualFrame.getByRole("treeitem", { name: /^技术架构\// });
  await expect(developmentAsset).toBeVisible();
  await expect(testingAsset).toBeVisible();
  await expect(developmentAsset.locator(".physical-badge")).toHaveText("应用 Git");
  await expect(testingAsset.locator(".physical-badge")).toHaveText("应用 Git");
  await expect(testDesignAgent.locator(".role-badge")).toHaveText("Agent");
  await expect(testDesignAgent.locator(".physical-badge")).toHaveText("公共 Git + 应用 Git");
  await expect(testDesignAgent.locator(".implementation-badge")).toHaveText("已实现");
  await expect(testAnalysisWorkagent.locator(".role-badge")).toHaveText("workagent");
  await expect(testAnalysisWorkagent.locator(".physical-badge")).toHaveText("公共 Git");
  for (const implementedAgent of [testAnalysisWorkagent, testGenerationWorkagent, testReviewWorkagent, testExecutionAgent, apiExecutionWorkagent]) {
    await expect(implementedAgent.locator(".implementation-badge")).toHaveText("已实现");
  }
  await expect(testingRuleGroups).toHaveCount(2);
  for (let index = 0; index < 2; index += 1) {
    await expect(testingRuleGroups.nth(index).locator(".scope-badge")).toHaveText("测试");
  }
  for (const [applicationAgent, role] of [
    [applicationTestDesignAgent, "Agent"],
    [applicationTestDesignWorkagent, "workagent"],
    [applicationTestExecutionAgent, "Agent"],
    [applicationTestExecutionWorkagent, "workagent"]
  ] as const) {
    await expect(applicationAgent.locator(".role-badge")).toHaveText(role);
    await expect(applicationAgent.locator(".physical-badge")).toHaveText("应用 Git");
    await expect(applicationAgent.locator(".implementation-badge")).toHaveText("未实现");
    await expect(applicationAgent).toHaveClass(/planned/);
    await expect(applicationAgent).toHaveAttribute("aria-level", "6");
  }
  await expect(manualFrame.getByRole("treeitem", { name: /^<应用专属测试 Agent>\// })).toHaveCount(0);
  await expect(manualFrame.getByRole("treeitem", { name: /^<应用专属测试 workagent>\// })).toHaveCount(0);
  await expect(publicTestRule.locator(".physical-badge")).toHaveText("公共 Git");
  await expect(applicationTestRules.locator(".physical-badge")).toHaveText("应用 Git");
  await expect(applicationTestRules).toHaveAttribute("aria-expanded", "true");
  for (const applicationRule of [
    "接口测试设计应用规约.md",
    "UI测试设计应用规约.md",
    "异步任务测试设计应用规约.md",
    "批量任务测试设计应用规约.md",
    "其他测试设计应用规约.md"
  ]) {
    const row = manualFrame.getByRole("treeitem", { name: applicationRule });
    await expect(row).toBeVisible();
    await expect(row.locator(".physical-badge")).toHaveText("应用 Git");
    await expect(row).toHaveAttribute("aria-level", "8");
  }
  await expect(developmentSkills.locator(".physical-badge")).toHaveText("应用 Git");
  await expect(developmentSkills).toHaveAttribute("aria-level", "4");
  await expect(testingSkills.locator(".physical-badge")).toHaveText("公共 Git + 应用 Git");
  await expect(testingSkills).toHaveAttribute("aria-level", "4");
  await expect(plannedCodeReviewSkill.locator(".implementation-badge")).toHaveText("未实现");
  await expect(plannedCodeReviewSkill).toHaveClass(/planned/);
  await expect(applicationTestSkills.locator(".physical-badge")).toHaveText("应用 Git");
  await expect(applicationTestSkills.locator(".implementation-badge")).toHaveText("未实现");
  await expect(applicationTestSkills).toHaveClass(/planned/);
  const publicRuleGitBadge = manualFrame.getByRole("treeitem", { name: "接口测试设计规约.md" }).locator(".physical-badge");
  await expect.poll(() => publicRuleGitBadge.evaluate((element) => element.getBoundingClientRect().width)).toBeLessThan(340);
  for (const agentFile of [
    "test-design-orchestrator.md",
    "test-design-analysis.md",
    "test-design-generation.md",
    "test-design-review.md",
    "test-execution-agent.md",
    "test-execution-api.md"
  ]) {
    const row = manualFrame.getByRole("treeitem", { name: agentFile });
    await expect(row).toBeVisible();
    await expect(row.locator(".physical-badge")).toHaveText("公共 Git");
  }
  for (const skillDirectory of [
    "test-design/",
    "test-design-api/",
    "test-design-augment/",
    "test-design-direct/",
    "test-design-equivalence/",
    "test-design-orthogonal/",
    "test-design-path/",
    "test-design-scenario/",
    "api-execute-case/",
    "generate-api-automation-markdown/",
    "generate-test-messages/",
    "validate-automation-script-format/"
  ]) {
    const row = manualFrame.getByRole("treeitem", { name: new RegExp(`^${skillDirectory}`) });
    await expect(row).toBeVisible();
    await expect(row.locator(".physical-badge")).toHaveText("公共 Git");
    await expect(row.locator(".implementation-badge")).toHaveText("已实现");
    await expect(row).not.toHaveClass(/planned/);
    await expect(row).toHaveAttribute("aria-level", "5");
  }
  for (const sharedNode of sharedDocsNodes) {
    await expect(sharedNode).toBeVisible();
    await expect(sharedNode.locator(".scope-badge")).toHaveText("开发 + 测试");
    await expect(sharedNode.locator(".physical-badge")).toHaveText("应用 Git");
  }
  await expect(technicalArchitecture.locator(".scope-badge")).toHaveText("开发");
  await expect(technicalArchitecture.locator(".physical-badge")).toHaveText("应用 Git");
  for (const applicationScenario of ["应用场景说明书_XXX.md", "应用场景说明书_YYY.md"]) {
    const row = manualFrame.getByRole("treeitem", { name: applicationScenario });
    await expect(row).toBeVisible();
    await expect(row.locator(".scope-badge")).toHaveText("测试");
    await expect(row.locator(".physical-badge")).toHaveText("应用 Git");
    await expect(row).toHaveAttribute("aria-level", "4");
  }
  await expect(manualFrame.getByRole("treeitem", { name: "测试概述.md" })).toHaveAttribute("aria-level", "4");
  await expect(manualFrame.getByRole("treeitem", { name: /场景测试说明书_/ })).toHaveCount(0);
  await expect(manualFrame.getByRole("treeitem", { name: "流程测试设计.md" })).toBeVisible();
  await expect(manualFrame.getByRole("treeitem", { name: "S000001_测试案例.md" })).toBeVisible();
  await expect(manualFrame.getByText("工作 Agent 统一称为 workagent")).toBeVisible();
  await expect(manualFrame.getByText(/供上层 Agent 编排调用/).first()).toBeVisible();
  await manualFrame.getByRole("button", { name: "内容与责任" }).click();
  await expect(manualFrame.getByRole("cell", { name: "公共能力建设团队" })).toBeVisible();
  await expect(manualFrame.getByRole("cell", { name: "仅个人 worktree 本地提交，禁止发布", exact: true })).toBeVisible();
  await expect(manualFrame.getByRole("cell", { name: "docs/**", exact: true })).toBeVisible();
  await expect(manualFrame.getByRole("cell", { name: "具体研发阶段的个人输入输出产物" })).toBeVisible();
});

test("Markdown Mermaid Flowchart、Sequence 和 State 可视化编辑后复用保存链路", async ({ page }) => {
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
create participant W as 工作器
U->>+W: 请求
alt 成功
  W->>S: 执行
  par 记录
    Note over U,S: 保留说明
  and 通知
    S--)U: 完成
  end
else 失败
  W-->>U: 回退
end
deactivate W
destroy W
W-xU: 中断
\`\`\`

\`\`\`mermaid
stateDiagram-v2
[*] --> Idle
state "空闲" as Idle
Idle: 等待任务
Idle --> Running: 启动
state Running {
  direction LR
  [*] --> Frontend
  Frontend --> [*]
  --
  [*] --> Backend
  Backend --> [*]
}
Running --> [*]
note right of Idle: 可以启动
style Idle fill:#ABC,stroke:#123456,color:#FFF
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
  await expect(visualButtons).toHaveCount(3);
  await visualButtons.nth(0).click();
  const dialog = page.getByRole("dialog", { name: "Mermaid 可视化编辑" });
  await expect(dialog).toBeVisible();
  await dialog.locator(".vue-flow__node").filter({ hasText: "开始" }).dblclick();
  await page.getByLabel("节点文字").fill("准备");
  await page.getByRole("button", { name: "完成" }).click();
  await dialog.getByRole("button", { name: "应用到 Markdown" }).click();

  await expect(visualButtons).toHaveCount(3);
  await visualButtons.nth(1).click();
  await dialog.getByLabel("选择消息 请求").click();
  await dialog.getByLabel("消息文本").fill("登录请求");
  await dialog.getByRole("button", { name: "应用到 Markdown" }).click();

  await expect(visualButtons).toHaveCount(3);
  await visualButtons.nth(2).click();
  await dialog.getByLabel("状态 Idle").click();
  await dialog.getByLabel("状态名称").fill("就绪");
  await dialog.getByLabel("状态说明").fill("第一行\n第二行");
  await dialog.getByRole("button", { name: "应用到 Markdown" }).click();

  await page.locator(".ta-workbench-footer-save").click();
  await expect.poll(() => fileWriteRequests.length).toBe(1);
  expect(fileWriteRequests[0]).toMatchObject({
    workspaceId: "wrk_personal_default",
    path: "docs/mermaid.md"
  });
  expect(fileWriteRequests[0]?.content).toContain('A@{ shape: rect, label: "准备" }');
  expect(fileWriteRequests[0]?.content).toContain("U->>+W: 登录请求");
  expect(fileWriteRequests[0]?.content).toContain("classDef important fill:red");
  expect(fileWriteRequests[0]?.content).toContain("alt 成功");
  expect(fileWriteRequests[0]?.content).toContain("par 记录");
  expect(fileWriteRequests[0]?.content).toContain("Note over U,S: 保留说明");
  expect(fileWriteRequests[0]?.content).toContain("destroy W");
  expect(fileWriteRequests[0]?.content).toContain('state "就绪" as Idle');
  expect(fileWriteRequests[0]?.content).toContain("Idle: 第一行");
  expect(fileWriteRequests[0]?.content).toContain("state Running {");
  expect(fileWriteRequests[0]?.content).toContain("note right of Idle: 可以启动");
  expect(fileWriteRequests[0]?.content).toContain("style Idle fill:#AABBCC,stroke:#123456,color:#FFFFFF");
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

test("a blank conversation schedules a night task and restores it from the pending tab", async ({ page }) => {
  const nightTaskRequests: Array<Record<string, unknown>> = [];
  const nightTasks: Array<Record<string, unknown>> = [];
  const sessionMessagesBySessionId: Record<string, Array<Record<string, unknown>>> = {};
  const backendState = {
    ...runnableWorkspaceSetup(),
    nightTaskRequests,
    nightTasks,
    sessionMessagesBySessionId,
    activeRun: null as Record<string, unknown> | null,
    runEventsByRunId: { run_night_e2e: [] }
  };
  await mockBackendApi(page, backendState);

  await gotoWorkbench(page, { selectConversation: false });
  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("夜间执行完整回归");
  await page.getByRole("button", { name: "定时执行" }).click();
  await expect(page.getByTestId("night-slot-picker")).toBeVisible();
  await expect(page.getByTestId("night-slot-picker")).not.toContainText("执行位置");
  await expect(page.getByTestId("night-schedule-confirm")).toBeEnabled();
  await page.getByTestId("night-schedule-confirm").click();

  await expect.poll(() => nightTaskRequests.length).toBe(1);
  expect(nightTaskRequests[0]).toMatchObject({
    workspaceId: "wrk_personal_default",
    prompt: "夜间执行完整回归",
    slotStart: "2026-07-18T13:15:00Z"
  });
  expect(nightTaskRequests[0]?.sessionId).toBeUndefined();
  await expect(page.getByTestId("current-night-task-card")).toContainText("等待执行");
  await expect(page.getByRole("button", { name: "发送" })).toBeDisabled();

  await page.reload({ waitUntil: "domcontentloaded" });
  await page.getByTestId("night-tasks-tab").click();
  await expect(page.getByTestId("night-task-list")).toContainText("夜间执行完整回归");

  // 模拟后台按时投递：待执行项消失，既有会话接口返回真实 USER 消息和现有 Run。
  const dispatchedTask = nightTasks[0]!;
  dispatchedTask.status = "DISPATCHED";
  dispatchedTask.runId = "run_night_e2e";
  dispatchedTask.updatedAt = "2026-07-18T13:16:00Z";
  sessionMessagesBySessionId.ses_night_created = [{
    messageId: "msg_night_e2e",
    sessionId: "ses_night_created",
    runId: "run_night_e2e",
    role: "USER",
    content: "夜间执行完整回归",
    sourceType: "SCHEDULED_TASK",
    sourceRefId: dispatchedTask.taskId,
    createdAt: "2026-07-18T13:16:00Z",
    updatedAt: "2026-07-18T13:16:00Z"
  }];
  backendState.activeRun = {
    runId: "run_night_e2e",
    sessionId: "ses_night_created",
    workspaceId: "wrk_personal_default",
    status: "RUNNING",
    sourceType: "SCHEDULED_TASK",
    sourceRefId: dispatchedTask.taskId,
    createdAt: "2026-07-18T13:16:00Z",
    updatedAt: "2026-07-18T13:16:00Z"
  };
  await page.getByRole("button", { name: "查看对话" }).click();
  await expect(page.locator(".oc-user-message__source-badge")).toContainText("夜间定时执行");
  await expect(page.locator(".oc-user-message__source-badge")).toContainText("21:16");
  await page.evaluate(() => window.dispatchEvent(new Event("focus")));
  await expect(page.getByTestId("current-night-task-card")).toHaveCount(0);
});

test("an existing-session night task locks only that conversation", async ({ page }) => {
  const nightTaskRequests: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, {
    nightTaskRequests,
    sessions: [session()]
  });

  await gotoWorkbench(page, { selectConversation: false });
  await selectPetContextSession(page);
  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("已有会话夜间回归");
  await page.getByRole("button", { name: "定时执行" }).click();
  await expect(page.getByTestId("night-schedule-confirm")).toBeEnabled();
  await page.getByTestId("night-schedule-confirm").click();

  await expect.poll(() => nightTaskRequests.length).toBe(1);
  expect(nightTaskRequests[0]).toMatchObject({ sessionId: "ses_1", prompt: "已有会话夜间回归" });
  await expect(page.getByRole("button", { name: "发送" })).toBeDisabled();
  await expect(page.getByRole("button", { name: "新建对话" })).toBeEnabled();

  await page.getByRole("button", { name: "新建对话" }).click();
  const composer = page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因");
  await composer.fill("其他对话仍可发送");
  await expect(page.getByRole("button", { name: "发送" })).toBeEnabled();
  await expect(page.getByRole("button", { name: "定时执行" })).toBeEnabled();
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
        providerId: "enterprise-openai",
        name: "DeepSeek-V4-Flash-W8A8",
        defaultModel: true
      },
      { id: "Qwen3.6-27B", providerId: "enterprise-openai", name: "Qwen3.6-27B" }
    ],
    providers: [{ id: "enterprise-openai", providerId: "enterprise-openai", name: "Enterprise OpenAI", status: "ready" }]
  });
  await page.addInitScript(() => {
    localStorage.setItem("ta_selected_provider", "opencode-zen");
    localStorage.setItem("ta_selected_model", "opencode-zen/north-mini-code");
  });

  await gotoWorkbench(page);

  await expect(page.getByRole("button", { name: "切换模型" })).toContainText("DeepSeek-V4-Flash-W8A8");
  await expect.poll(() => page.evaluate(() => localStorage.getItem("ta_selected_model"))).toBe("enterprise-openai/DeepSeek-V4-Flash-W8A8");

  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("use catalog default model");
  await page.getByRole("button", { name: "发送" }).click();

  await expect.poll(() => runRequests.length).toBe(1);
  expect(runRequests[0]).toMatchObject({
    prompt: "use catalog default model",
    model: "enterprise-openai/DeepSeek-V4-Flash-W8A8"
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

test("conflicting terminal replay keeps one authenticated SSE and one legacy feedback recovery chain", async ({ page }) => {
  const sessionMessageRequests: string[] = [];
  const runFeedbackQueryRequests: Array<Record<string, unknown>> = [];
  await installAuthenticatedRunEventFetchStream(page, {
    run_1: [
      {
        delayMs: 10,
        events: [
          {
            seq: 1,
            type: "message.updated",
            payload: {
              messageId: "msg_remote_live",
              role: "assistant",
              message: { id: "msg_remote_live", role: "assistant" }
            }
          },
          {
            seq: 2,
            type: "message.part.updated",
            payload: {
              messageId: "msg_remote_live",
              partId: "part_remote_live",
              part: {
                id: "part_remote_live",
                messageID: "msg_remote_live",
                type: "text",
                text: "根终态最终回答"
              }
            }
          },
          {
            seq: 3,
            type: "run.failed",
            payload: { message: "候选网关失败" }
          },
          {
            seq: 4,
            type: "run.succeeded",
            payload: { platformSessionTitlePending: true }
          }
        ]
      },
      {
        delayMs: 1_600,
        events: [{
          seq: 5,
          type: "session.updated",
          payload: {
            platformSessionTitleSynchronized: true,
            platformSessionTitle: "登录功能测试设计",
            isChildSession: false
          }
        }]
      }
    ]
  });
  await mockBackendApi(page, {
    sessionMessageRequests,
    runFeedbackQueryRequests,
    sessionMessages: [{
      messageId: "msg_11111111111111111111111111111111",
      remoteMessageId: "msg_persisted_other",
      sessionId: "ses_1",
      role: "ASSISTANT",
      content: "已持久化但不是当前远端消息",
      createdAt: "2026-07-17T08:00:00Z",
      runId: "run_1"
    }],
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
  await page.waitForTimeout(1_100);
  expect(await page.evaluate(() => (
    window as Window & { __titleWatchRunStreams?: Array<{ closed: boolean }> }
  ).__titleWatchRunStreams?.length)).toBe(1);
  await expect.poll(() => sessionMessageRequests.length).toBe(3);
  await expect.poll(() => runFeedbackQueryRequests.length).toBe(3);
  expect(runFeedbackQueryRequests).toEqual(Array.from({ length: 3 }, () => ({ runIds: ["run_1"] })));
  expect(await page.evaluate(() => (
    window as Window & { __titleWatchRunStreams?: Array<{ authorization: string | null }> }
  ).__titleWatchRunStreams?.[0]?.authorization)).toBe("Bearer test-token");
  await expect.poll(() => page.evaluate(() => (
    window as Window & { __titleWatchRunStreams?: Array<{ closed: boolean }> }
  ).__titleWatchRunStreams?.[0]?.closed)).toBe(false);
  await expect(page.locator(".figma-chat-title")).toHaveText("登录功能测试设计");
  await expect.poll(() => page.evaluate(() => (
    window as Window & { __titleWatchRunStreams?: Array<{ closed: boolean }> }
  ).__titleWatchRunStreams?.[0]?.closed)).toBe(true);
  expect(sessionMessageRequests).toHaveLength(3);
  expect(runFeedbackQueryRequests).toHaveLength(3);
});

test("a new run replaces one title-pending fetch SSE and ignores the old stream's late title", async ({ page }) => {
  const runRequests: Array<Record<string, unknown>> = [];
  await installAuthenticatedRunEventFetchStream(page, {
    run_1: [
      {
        delayMs: 10,
        events: [{
          seq: 1,
          type: "run.succeeded",
          payload: { platformSessionTitlePending: true }
        }]
      },
      {
        delayMs: 600,
        events: [{
          seq: 2,
          type: "session.updated",
          payload: {
            platformSessionTitleSynchronized: true,
            platformSessionTitle: "旧 Run 晚到标题",
            isChildSession: false
          }
        }]
      }
    ],
    run_2: []
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
  await composer.fill("第一轮等待原生标题");
  await page.getByRole("button", { name: "发送" }).click();

  await expect(page.getByText("任务完成")).toBeVisible();
  await expect.poll(() => page.evaluate(() => (window as Window & { __titleWatchRunStreams?: Array<{ closed: boolean }> }).__titleWatchRunStreams?.[0]?.closed)).toBe(false);

  await composer.fill("第二轮开始后关闭旧标题监听");
  await page.getByRole("button", { name: "发送" }).click();
  await expect.poll(() => runRequests.length).toBe(2);
  await expect.poll(() => page.evaluate(() => (
    window as Window & { __titleWatchRunStreams?: Array<{ closed: boolean }> }
  ).__titleWatchRunStreams?.length)).toBe(2);
  await expect.poll(() => page.evaluate(() => (
    window as Window & { __titleWatchRunStreams?: Array<{ closed: boolean }> }
  ).__titleWatchRunStreams?.[0]?.closed)).toBe(true);
  await page.waitForTimeout(700);
  await expect(page.locator(".figma-chat-title")).not.toHaveText("旧 Run 晚到标题");
  expect(await page.evaluate(() => (
    window as Window & { __titleWatchRunStreams?: Array<{ runId: string; authorization: string | null; closed: boolean }> }
  ).__titleWatchRunStreams)).toEqual([
    { runId: "run_1", authorization: "Bearer test-token", closed: true },
    { runId: "run_2", authorization: "Bearer test-token", closed: false }
  ]);
});

test("a superseded title-pending run cannot restore its todos into the next turn", async ({ page }) => {
  let releaseSecondRun!: () => void;
  const secondRunGate = new Promise<void>((resolve) => {
    releaseSecondRun = resolve;
  });
  const runRequests: Array<Record<string, unknown>> = [];
  await page.addInitScript(() => {
    localStorage.setItem("test-agent.onboarding.v2:usr_admin", "seen");
    type StreamProbe = {
      runId: string;
      closed: boolean;
      emit: (type: string, seq: number, payload: Record<string, unknown>) => void;
      fail: () => void;
    };
    const probes: StreamProbe[] = [];
    const seededRunIds = new Set<string>();
    (window as Window & { __todoOwnershipRunStreams?: StreamProbe[] }).__todoOwnershipRunStreams = probes;
    const nativeFetch = window.fetch.bind(window);
    window.fetch = async (input, init) => {
      const requestUrl = new URL(
        typeof input === "string" ? input : input instanceof Request ? input.url : input.toString(),
        window.location.origin
      );
      const runId = decodeURIComponent(requestUrl.pathname).match(/\/runs\/([^/]+)\/events$/)?.[1];
      if (!runId) {
        return nativeFetch(input, init);
      }
      const encoder = new TextEncoder();
      let controller: ReadableStreamDefaultController<Uint8Array> | undefined;
      let closed = false;
      const probe: StreamProbe = {
        runId,
        closed: false,
        emit: (type, seq, payload) => {
          if (closed || !controller) return;
          controller.enqueue(encoder.encode(
            `id: ${seq}\nevent: ${type}\ndata: ${JSON.stringify({
              eventId: `evt_todo_owner_${runId}_${seq}_${type}`,
              runId,
              seq,
              type,
              traceId: "trace_e2e",
              occurredAt: "2026-07-15T09:00:00Z",
              payload
            })}\n\n`
          ));
        },
        fail: () => {
          if (closed || !controller) return;
          closed = true;
          probe.closed = true;
          controller.error(new Error("superseded stream failed"));
        }
      };
      probes.push(probe);
      const body = new ReadableStream<Uint8Array>({
        start(streamController) {
          controller = streamController;
          if (runId === "run_1" && !seededRunIds.has(runId)) {
            seededRunIds.add(runId);
            window.setTimeout(() => probe.emit("todo.updated", 1, {
              todos: Array.from({ length: 4 }, (_, index) => ({
                id: `todo_first_${index}`,
                content: `第一轮任务 ${index + 1}`,
                status: "completed"
              }))
            }), 10);
            window.setTimeout(() => probe.emit("run.succeeded", 2, {
              platformSessionTitlePending: true
            }), 20);
          } else if (runId === "run_2" && !seededRunIds.has(runId)) {
            seededRunIds.add(runId);
            window.setTimeout(() => probe.emit("todo.updated", 1, {
              todos: Array.from({ length: 9 }, (_, index) => ({
                id: `todo_second_${index}`,
                content: `第二轮任务 ${index + 1}`,
                status: "pending"
              }))
            }), 10);
          }
        },
        cancel() {
          closed = true;
          probe.closed = true;
        }
      });
      init?.signal?.addEventListener("abort", () => {
        closed = true;
        probe.closed = true;
        controller?.close();
      }, { once: true });
      return new Response(body, { headers: { "content-type": "text/event-stream" } });
    };
  });
  await mockBackendApi(page, {
    runRequests,
    runIds: ["run_1", "run_2"],
    runRequestGates: [Promise.resolve(), secondRunGate],
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
  await composer.fill("第一轮生成 4 个待办");
  await page.getByRole("button", { name: "发送" }).click();
  await expect(page.getByText("任务完成")).toBeVisible();
  await page.getByRole("button", { name: "展开已完成工作状态" }).click();
  await expect(page.getByText("共 4")).toBeVisible();

  await composer.fill("第二轮生成 9 个待办");
  await page.getByRole("button", { name: "发送" }).click();
  await expect.poll(() => runRequests.length).toBe(2);
  await expect(page.getByTestId("oc-work-status-dock").getByTestId("oc-todo-panel")).toHaveCount(0);

  await page.evaluate(() => {
    const probe = (window as Window & {
      __todoOwnershipRunStreams?: Array<{ runId: string; closed: boolean; fail: () => void }>;
    }).__todoOwnershipRunStreams?.find((item) => item.runId === "run_1" && !item.closed);
    probe?.fail();
  });
  await expect(page.getByText("RunEvent SSE 连接异常", { exact: true })).toHaveCount(0);
  await expect.poll(() => page.evaluate(() => (
    (window as Window & {
      __todoOwnershipRunStreams?: Array<{ runId: string; closed: boolean }>;
    }).__todoOwnershipRunStreams?.filter((item) => item.runId === "run_1" && !item.closed).length ?? 0
  ))).toBeGreaterThan(0);

  await page.evaluate(() => {
    const probe = (window as Window & {
      __todoOwnershipRunStreams?: Array<{
        runId: string;
        closed: boolean;
        emit: (type: string, seq: number, payload: Record<string, unknown>) => void;
        fail: () => void;
      }>;
    }).__todoOwnershipRunStreams?.filter((item) => item.runId === "run_1" && !item.closed).at(-1);
    probe?.emit("todo.updated", 3, { todos: [{ content: "旧 Run todo.updated 回灌", status: "completed" }] });
    probe?.emit("message.part.updated", 4, {
      messageID: "msg_old_todowrite",
      part: {
        id: "part_old_todowrite",
        messageID: "msg_old_todowrite",
        type: "tool",
        tool: "todowrite",
        state: { status: "completed", input: { todos: [{ content: "旧 Run todowrite 回灌", status: "completed" }] } }
      }
    });
    probe?.emit("run.snapshot.reset", 5, {
      snapshot: {
        events: [{
          eventId: "evt_old_snapshot_todo",
          runId: "run_1",
          seq: 1,
          type: "todo.updated",
          traceId: "trace_e2e",
          occurredAt: "2026-07-15T09:00:00Z",
          payload: { todos: [{ content: "旧 Run snapshot 回灌", status: "completed" }] }
        }]
      }
    });
    probe?.emit("session.updated", 6, {
      platformSessionTitleSynchronized: true,
      platformSessionTitle: "旧 Run 标题仍同步",
      isChildSession: false
    });
  });

  await expect(page.locator(".figma-chat-title")).toHaveText("旧 Run 标题仍同步");
  await expect(page.getByTestId("oc-work-status-dock").getByTestId("oc-todo-panel")).toHaveCount(0);
  await page.getByRole("button", { name: "展开历史工作状态" }).click();
  await expect(page.getByText("共 4")).toBeVisible();
  await expect(page.getByText(/旧 Run .*回灌/)).toHaveCount(0);

  releaseSecondRun();
  await expect(page.getByTestId("oc-work-status-dock").getByText("共 9")).toBeVisible();
  await expect(page.getByTestId("oc-work-status-dock").getByText("共 4")).toHaveCount(0);
});

test("retrying a failed chat run sends the previous prompt again", async ({ page }) => {
  const runRequests: Array<Record<string, unknown>> = [];
  await page.addInitScript(() => {
    localStorage.setItem("test-agent.onboarding.v2:usr_admin", "seen");
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
  await expect(page.getByTestId("oc-user-message")).toHaveCount(1);
  await expect(page.locator(".figma-chat-retry-card")).toHaveCount(0);
});

test("new run success clears stale RunEvent SSE connection feedback", async ({ page }) => {
  const runRequests: Array<Record<string, unknown>> = [];
  await page.addInitScript(() => {
    const nativeFetch = window.fetch.bind(window);
    const attempts: Record<string, number> = {};
    (window as Window & { __runEventFetchAttempts?: Record<string, number> }).__runEventFetchAttempts = attempts;
    window.fetch = async (input, init) => {
      const request = new Request(input, init);
      const requestUrl = new URL(request.url, window.location.origin);
      const runId = decodeURIComponent(requestUrl.pathname)
        .match(/^\/api\/internal\/agent\/opencode\/runs\/([^/]+)\/events$/)?.[1];
      if (!runId) {
        return nativeFetch(input, init);
      }
      const attempt = (attempts[runId] ?? 0) + 1;
      attempts[runId] = attempt;
      const encoder = new TextEncoder();
      let timer: number | undefined;
      let closed = false;
      const body = new ReadableStream<Uint8Array>({
        start(controller) {
          timer = window.setTimeout(() => {
            if (closed) return;
            if (runId === "run_1" && attempt === 1) {
              closed = true;
              controller.error(new Error("mock authenticated fetch SSE failure"));
              return;
            }
            const type = runId === "run_1" ? "run.failed" : "run.succeeded";
            const payload = runId === "run_1"
              ? { error: { name: "ConnectionError", message: "Streaming response failed" } }
              : {};
            controller.enqueue(encoder.encode(
              `id: evt_mock_${runId}_1\nevent: ${type}\ndata: ${JSON.stringify({
                eventId: `evt_mock_${runId}_1`,
                runId,
                seq: 1,
                type,
                traceId: "trace_e2e",
                occurredAt: "2026-07-17T08:00:00Z",
                payload
              })}\n\n`
            ));
          }, 20);
          request.signal.addEventListener("abort", () => {
            closed = true;
            if (timer !== undefined) window.clearTimeout(timer);
            try {
              controller.error(new DOMException("RunEvent stream aborted", "AbortError"));
            } catch {
              // reader 已结束时无需重复关闭。
            }
          }, { once: true });
        },
        cancel() {
          closed = true;
          if (timer !== undefined) window.clearTimeout(timer);
        }
      });
      return new Response(body, { headers: { "content-type": "text/event-stream" } });
    };
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
  await expect.poll(() => page.evaluate(() => (
    window as Window & { __runEventFetchAttempts?: Record<string, number> }
  ).__runEventFetchAttempts?.run_2)).toBe(1);
  await expect(page.getByText("任务完成")).toBeVisible();
  await expect(page.getByText("RunEvent SSE 连接异常")).toHaveCount(0);
  await expect(page.locator(".figma-chat-retry-card")).toHaveCount(0);
  await expect(page.getByText("Streaming response failed")).toHaveCount(0);
  await expect(page.getByText("任务失败")).toHaveCount(0);
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
        title: "/test-design-orthogonal 车贷",
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
        content: "/test-design-orthogonal 车贷",
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
  await page.getByRole("button", { name: /test-design-orthogonal/ }).click();

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

test("workbench accepts the first prompt without requiring new conversation while pet manual help remains available", async ({ page }) => {
  const runRequests: Array<Record<string, unknown>> = [];
  await page.addInitScript(() => {
    localStorage.setItem("test-agent.onboarding.v2:usr_admin", "seen");
  });
  await mockBackendApi(page, { runRequests, ...runnableWorkspaceSetup(), authRoles: ["APP_ADMIN"] });

  await gotoWorkbench(page, { selectConversation: false });

  await page.getByRole("button", { name: "唤起小宠物" }).click();
  await page.getByTestId("figma-robot").click();
  await expect(page.getByTestId("robot-side-question")).toBeVisible();
  await expect(page.getByTestId("robot-side-question-input")).toBeEnabled();
  await expect(page.getByRole("button", { name: "打开宠物小游戏" })).toHaveCount(0);

  const composer = page.locator(".figma-chat-input-card");
  const textarea = page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因");
  await expect(composer).not.toHaveClass(/is-disabled/);
  await expect(textarea).toBeEnabled();
  await expect(page.getByRole("button", { name: "发送" })).toBeDisabled();
  await expect(page.getByRole("button", { name: "新建对话" })).toBeEnabled();

  await textarea.fill("直接开始第一轮测试");
  await page.getByRole("button", { name: "发送" }).click();
  await expect.poll(() => runRequests.length).toBe(1);
  expect(runRequests[0]?.prompt).toBe("直接开始第一轮测试");
});

test("pet mini games are hidden from non-super administrators", async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem("test-agent.onboarding.v2:usr_admin", "seen");
  });
  await mockBackendApi(page, { ...runnableWorkspaceSetup(), authRoles: ["APP_ADMIN"] });
  await gotoWorkbench(page, { selectConversation: false });

  await page.getByRole("button", { name: "唤起小宠物" }).click();
  await page.getByTestId("figma-robot").click();
  await expect(page.getByTestId("robot-side-question")).toBeVisible();
  await expect(page.getByRole("button", { name: "打开宠物小游戏" })).toHaveCount(0);
  await expect(page.getByTestId("pet-mini-games")).toHaveCount(0);
});

test("pet drag continues after the pointer leaves the robot hit area", async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem("test-agent.onboarding.v2:usr_admin", "seen");
  });
  await mockBackendApi(page, { ...runnableWorkspaceSetup(), authRoles: ["SUPER_ADMIN"] });
  await gotoWorkbench(page, { selectConversation: false });

  await page.getByRole("button", { name: "唤起小宠物" }).click({ force: true });
  const robot = page.getByTestId("figma-robot");
  await expect(robot).toBeVisible();
  await page.evaluate(() => {
    // 模拟 Monaco 等工作台子组件拦截 Pointer Events，验证 window 捕获监听仍能完成拖动。
    document.addEventListener("pointermove", (event) => event.stopPropagation());
    document.addEventListener("pointerup", (event) => event.stopPropagation());
  });
  const box = await robot.boundingBox();
  expect(box).not.toBeNull();
  const start = await robot.evaluate((element) => ({
    x: Number.parseFloat((element as HTMLElement).style.left),
    y: Number.parseFloat((element as HTMLElement).style.top)
  }));

  await page.mouse.move(box!.x + box!.width / 2, box!.y + box!.height / 2);
  await page.mouse.down();
  await page.mouse.move(box!.x + box!.width / 2 + 100, box!.y + box!.height / 2 + 80, { steps: 3 });
  await page.mouse.up();

  await expect.poll(async () => robot.evaluate((element) => Number.parseFloat((element as HTMLElement).style.left))).toBeGreaterThan(start.x);
  await expect.poll(async () => robot.evaluate((element) => Number.parseFloat((element as HTMLElement).style.top))).toBeGreaterThan(start.y);
});

test("pet mini games support tetris, minesweeper, sudoku and snake interactions", async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem("test-agent.onboarding.v2:usr_admin", "seen");
  });
  await page.addInitScript(() => {
    Math.random = () => 0;
  });
  await mockBackendApi(page, { ...runnableWorkspaceSetup(), authRoles: ["SUPER_ADMIN"] });
  await gotoWorkbench(page, { selectConversation: false });

  await page.getByRole("button", { name: "唤起小宠物" }).click();
  await page.getByTestId("figma-robot").click();
  await page.getByRole("button", { name: "打开宠物小游戏" }).click();
  await expect(page.getByTestId("pet-mini-games")).toBeVisible();
  await expect(page.getByTestId("figma-robot")).toBeVisible();

  await page.getByTestId("pet-game-open-tetris").click();
  await expect(page.getByTestId("pet-tetris").locator(".pet-tetris-cell")).toHaveCount(160);
  await page.getByRole("button", { name: "右移" }).click();
  await page.getByRole("button", { name: "旋转" }).click();
  await page.getByRole("button", { name: "直接落下" }).click();
  await expect(page.getByTestId("pet-tetris")).toContainText("分数");

  await page.getByRole("button", { name: "扫雷", exact: true }).click();
  const mineCells = page.getByTestId("pet-minesweeper").locator(".pet-mine-cell");
  await expect(mineCells).toHaveCount(64);
  await mineCells.first().click();
  await expect(page.getByTestId("pet-minesweeper")).not.toContainText("踩雷了");
  await expect(mineCells.nth(2)).not.toHaveClass(/is-revealed/);
  await mineCells.nth(1).dblclick();
  await expect(mineCells.nth(2)).not.toHaveClass(/is-revealed/);
  await mineCells.nth(10).click({ button: "right" });
  await expect(mineCells.nth(10)).toHaveAttribute("aria-label", /已插旗/);
  await mineCells.nth(1).dblclick();
  await expect(mineCells.nth(2)).toHaveClass(/is-revealed/);

  await page.getByRole("button", { name: "数独", exact: true }).click();
  const sudokuCells = page.getByTestId("pet-sudoku").locator(".pet-sudoku-cell");
  await expect(sudokuCells).toHaveCount(81);
  await sudokuCells.nth(2).click();
  await page.getByRole("button", { name: "填写数字 4" }).click();
  await expect(sudokuCells.nth(2)).toHaveText("4");
  await expect(sudokuCells.nth(2)).not.toHaveClass(/is-error/);

  await page.getByRole("button", { name: "贪吃蛇", exact: true }).click();
  await expect(page.getByTestId("pet-snake").locator(".pet-snake-cell")).toHaveCount(144);
  await page.getByRole("button", { name: "贪吃蛇向上" }).click();
  await page.getByTestId("pet-snake").getByRole("button", { name: "暂停" }).click();
  await expect(page.getByTestId("pet-snake")).toContainText("已暂停");

  await page.getByRole("button", { name: "关闭宠物旁路问答" }).click();
  await expect(page.getByTestId("pet-mini-games")).toHaveCount(0);
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
    .fill("/test-design-path 对车贷的开发文档，生成路径图");
  await page.getByRole("button", { name: "发送" }).click();

  await expect.poll(() => runRequests.length).toBe(1);
  expect(runRequests[0]).toMatchObject({
    sessionId: "ses_1",
    prompt: "/test-design-path 对车贷的开发文档，生成路径图",
    command: "test-design-path",
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

type RunEventFetchBatch = {
  delayMs: number;
  events: Array<{ seq: number; type: string; payload: Record<string, unknown> }>;
};

/** 认证后的主 RunEvent 客户端走 fetch SSE；同一 batch 用于复现 durable 终态同步重放。 */
async function installAuthenticatedRunEventFetchStream(
  page: Page,
  scenarios: Record<string, RunEventFetchBatch[]>
) {
  await page.addInitScript(({ scenarios }) => {
    type StreamProbe = { runId: string; authorization: string | null; closed: boolean };
    const probes: StreamProbe[] = [];
    (window as Window & { __titleWatchRunStreams?: StreamProbe[] }).__titleWatchRunStreams = probes;
    const nativeFetch = window.fetch.bind(window);
    window.fetch = async (input, init) => {
      const request = new Request(input, init);
      const requestUrl = new URL(request.url, window.location.origin);
      const runId = decodeURIComponent(requestUrl.pathname)
        .match(/^\/api\/internal\/agent\/opencode\/runs\/([^/]+)\/events$/)?.[1];
      if (!runId) {
        return nativeFetch(input, init);
      }
      const probe: StreamProbe = {
        runId,
        authorization: request.headers.get("authorization"),
        closed: false
      };
      probes.push(probe);
      const encoder = new TextEncoder();
      let controller: ReadableStreamDefaultController<Uint8Array> | undefined;
      let timers: number[] = [];
      const closeProbe = () => {
        if (probe.closed) return;
        probe.closed = true;
        timers.forEach((timer) => window.clearTimeout(timer));
        timers = [];
      };
      const body = new ReadableStream<Uint8Array>({
        start(streamController) {
          controller = streamController;
          for (const batch of scenarios[runId] ?? []) {
            timers.push(window.setTimeout(() => {
              if (probe.closed) return;
              const frame = batch.events.map((item) => (
                `id: evt_title_${runId}_${item.seq}\nevent: ${item.type}\ndata: ${JSON.stringify({
                  eventId: `evt_title_${runId}_${item.seq}`,
                  runId,
                  seq: item.seq,
                  type: item.type,
                  traceId: "trace_e2e",
                  occurredAt: "2026-07-17T08:00:00Z",
                  payload: item.payload
                })}\n\n`
              )).join("");
              streamController.enqueue(encoder.encode(frame));
            }, batch.delayMs));
          }
        },
        cancel() {
          closeProbe();
        }
      });
      request.signal.addEventListener("abort", () => {
        closeProbe();
        try {
          controller?.error(new DOMException("RunEvent stream aborted", "AbortError"));
        } catch {
          // reader 已结束时无需重复关闭。
        }
      }, { once: true });
      return new Response(body, { headers: { "content-type": "text/event-stream" } });
    };
  }, { scenarios });
}

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
    fileReadRequests?: Array<{ workspaceId: string; path: string; attempt: number }>;
    fileReadDelays?: Record<string, number[]>;
    fileReadFailuresBeforeSuccess?: Record<string, number>;
    fileReadFailureAttempts?: Record<string, number[]>;
    fileReadNotFoundAttempts?: Record<string, number[]>;
    fileReadResponses?: Record<string, string[]>;
    workspaceMutationDelays?: Record<string, number>;
    fileWriteRequests?: Array<{ workspaceId: string; path: string; content: string }>;
    workspaceMoveRequests?: Array<{ workspaceId: string; sourcePath: string; targetPath: string }>;
    /** 组合工作区视图响应以 `kind:alias:path` 为键；未配置时自动映射普通工作区目录。 */
    workspaceViewLists?: Record<string, {
      entries: Array<Record<string, unknown>>;
      warnings?: Array<{ alias?: string; code: string; message: string }>;
      truncated?: boolean;
    }>;
    /** 每次 workspace.move 成功后替换组合树响应，用于覆盖稳定 ID 变化和反向撤销。 */
    workspaceViewListsAfterMoves?: Array<Record<string, {
      entries: Array<Record<string, unknown>>;
      warnings?: Array<{ alias?: string; code: string; message: string }>;
      truncated?: boolean;
    }>>;
    /** 引用文件正文以 `kind:alias:path` 为键。 */
    workspaceViewContents?: Record<string, string>;
    agentFileFrames?: Array<{
      op: string;
      scope: string;
      path: string;
      workspaceId?: string;
      worktreeId?: string;
      attempt?: number;
      content?: string;
    }>;
    /** Agent 文件配置以 `PUBLIC:path` / `WORKSPACE:path` 为键。 */
    agentFileContents?: Record<string, string>;
    agentFileReadDelays?: Record<string, number[]>;
    agentFileReadFailureAttempts?: Record<string, number[]>;
    agentFileReadNotFoundAttempts?: Record<string, number[]>;
    agentFileReadResponses?: Record<string, string[]>;
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
    publicAgentRepositories?: Array<Record<string, unknown>>;
    publicAgentWorktreesByServer?: Record<string, Array<Record<string, unknown>>>;
    defaultPersonalRequests?: string[];
    processStatus?: "READY" | "NEEDS_INITIALIZATION" | "UNAVAILABLE";
    processStatusRequests?: string[];
    processInitializations?: Array<Record<string, unknown>>;
    initializeFailureThenReady?: boolean;
    ensureDefaultRequiresReady?: boolean;
    sessions?: Array<Record<string, unknown>>;
    nightTaskRequests?: Array<Record<string, unknown>>;
    nightTasks?: Array<Record<string, unknown>>;
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
    runFeedbackQueryRequests?: Array<Record<string, unknown>>;
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
    runRequestGates?: Array<Promise<void>>;
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
  await page.exposeFunction("__taRecordWorkspaceFileRead", (workspaceId: string, path: string, attempt: number) => {
    capture.fileReadRequests?.push({ workspaceId, path, attempt });
  });
  await page.exposeFunction("__taRecordWorkspaceMove", (workspaceId: string, sourcePath: string, targetPath: string) => {
    capture.workspaceMoveRequests?.push({ workspaceId, sourcePath, targetPath });
  });
  await page.exposeFunction("__taRecordAgentFileFrame", (frame: {
    op: string;
    scope: string;
    path: string;
    workspaceId?: string;
    worktreeId?: string;
    attempt?: number;
    content?: string;
  }) => {
    capture.agentFileFrames?.push(frame);
  });
  if (!capture.skipInitialAuthToken) {
    await page.addInitScript(() => {
      sessionStorage.setItem("test-agent.auth.token", "test-token");
      // 工作台 E2E 默认跳过首次引导，避免遮罩拦截真实文件树与 tab 点击。
      localStorage.setItem("test-agent.onboarding.v7:usr_admin", "seen");
    });
  }
  await page.addInitScript(({
    fileContents,
    fileReadDelays,
    fileReadFailuresBeforeSuccess,
    fileReadFailureAttempts,
    fileReadNotFoundAttempts,
    fileReadResponses,
    workspaceMutationDelays,
    workspaceViewLists,
    workspaceViewListsAfterMoves,
    workspaceViewContents,
    agentFileContents,
    agentFileReadDelays,
    agentFileReadFailureAttempts,
    agentFileReadNotFoundAttempts,
    agentFileReadResponses
  }) => {
    const recordFileRequest = (workspaceId: string, path: string) => {
      const win = window as Window & {
        __taRecordWorkspaceFileRequest?: (workspaceId: string, path: string) => void;
        __taRecordWorkspaceFileRead?: (workspaceId: string, path: string, attempt: number) => void;
        __taRecordWorkspaceFileWrite?: (workspaceId: string, path: string, content: string) => void;
        __taRecordWorkspaceMove?: (workspaceId: string, sourcePath: string, targetPath: string) => void;
      };
      win.__taRecordWorkspaceFileRequest?.(workspaceId, path);
    };
    const recordWorkspaceMove = (workspaceId: string, sourcePath: string, targetPath: string) => {
      const win = window as Window & {
        __taRecordWorkspaceMove?: (workspaceId: string, sourcePath: string, targetPath: string) => void;
      };
      win.__taRecordWorkspaceMove?.(workspaceId, sourcePath, targetPath);
    };
    const recordFileWrite = (workspaceId: string, path: string, content: string) => {
      const win = window as Window & {
        __taRecordWorkspaceFileWrite?: (workspaceId: string, path: string, content: string) => void;
      };
      win.__taRecordWorkspaceFileWrite?.(workspaceId, path, content);
    };
    const recordFileRead = (workspaceId: string, path: string, attempt: number) => {
      const win = window as Window & {
        __taRecordWorkspaceFileRead?: (workspaceId: string, path: string, attempt: number) => void;
      };
      win.__taRecordWorkspaceFileRead?.(workspaceId, path, attempt);
    };
    const recordAgentFileFrame = (frame: {
      op: string;
      scope: string;
      path: string;
      workspaceId?: string;
      worktreeId?: string;
      attempt?: number;
      content?: string;
    }) => {
      const win = window as Window & {
        __taRecordAgentFileFrame?: (payload: typeof frame) => void;
      };
      win.__taRecordAgentFileFrame?.(frame);
    };
    const readAttempts: Record<string, number> = {};
    const agentReadAttempts: Record<string, number> = {};
    let workspaceMoveAttempt = 0;
    type ViewLocator = { kind?: string; path?: string; referenceAlias?: string };
    const viewKey = (locator: ViewLocator) =>
      `${locator.kind ?? "COMPOSITE"}:${locator.referenceAlias ?? ""}:${locator.path ?? ""}`;
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
    const agentEntries = (scope: string, path: string) => {
      const prefix = `${scope}:`;
      const files = Object.keys(agentFileContents as Record<string, string>)
        .filter((key) => key.startsWith(prefix))
        .map((key) => key.slice(prefix.length));
      const directoryPrefix = path ? `${path}/` : "";
      const children = new Map<string, { path: string; name: string; directory: boolean; size: number; lastModifiedAt: string }>();
      for (const filePath of files) {
        if (!filePath.startsWith(directoryPrefix)) continue;
        const rest = filePath.slice(directoryPrefix.length);
        if (!rest) continue;
        const name = rest.split("/")[0] ?? rest;
        const childPath = path ? `${path}/${name}` : name;
        const directory = rest.includes("/");
        const content = (agentFileContents as Record<string, string>)[`${scope}:${filePath}`] ?? "";
        children.set(childPath, {
          path: childPath,
          name,
          directory,
          size: directory ? 0 : content.length,
          lastModifiedAt: "2026-06-19T00:00:00Z"
        });
      }
      return [...children.values()];
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
        const locator = (request.params as unknown as { locator?: ViewLocator } | undefined)?.locator
          ?? { kind: "COMPOSITE", path: "" };
        let data: unknown = null;
        if (request.op === "workspace.view.list") {
          recordFileRequest(params.workspaceId ?? "", locator.path ?? "");
          const configured = (workspaceViewLists as Record<string, {
            entries: Array<Record<string, unknown>>;
            warnings?: Array<{ alias?: string; code: string; message: string }>;
            truncated?: boolean;
          }>)[viewKey(locator)];
          data = configured ?? {
            entries: entries(locator.path ?? "", params.workspaceId).map((entry) => ({
              id: `workspace:${entry.path}`,
              ...entry,
              locator: { kind: "WORKSPACE", path: entry.path },
              source: "WORKSPACE",
              merged: false,
              collision: false,
              readonly: false,
              workspacePath: entry.path,
              referenceAliases: []
            })),
            warnings: [],
            truncated: false
          };
        } else if (request.op === "workspace.list") {
          recordFileRequest(params.workspaceId ?? "", params.path ?? "");
          data = entries(params.path ?? "", params.workspaceId);
        } else if (request.op === "workspace.search") {
          const query = (params.query ?? "").toLowerCase();
          data = Object.keys(fileContents as Record<string, string>)
            .filter((path) => path.toLowerCase().includes(query))
            .map((path) => ({
              path,
              name: path.split("/").at(-1) ?? path,
              directory: path.includes("/") ? path.slice(0, path.lastIndexOf("/")) : "",
              size: (fileContents as Record<string, string>)[path]?.length ?? 0,
              lastModifiedAt: "2026-06-19T00:00:00Z"
            }));
        } else if (request.op === "workspace.view.read") {
          const content = (workspaceViewContents as Record<string, string>)[viewKey(locator)] ?? "";
          data = {
            path: locator.path ?? "",
            content,
            size: content.length,
            readonly: true,
            source: "REFERENCE",
            referenceAlias: locator.referenceAlias,
            locator
          };
        } else if (request.op === "workspace.read") {
          const path = params.path ?? "tests/checkout.spec.ts";
          const workspaceId = params.workspaceId ?? "";
          const attemptKey = `${workspaceId}:${path}`;
          const attempt = (readAttempts[attemptKey] ?? 0) + 1;
          readAttempts[attemptKey] = attempt;
          recordFileRead(workspaceId, path, attempt);
          const delay = (fileReadDelays as Record<string, number[]>)[path]?.[attempt - 1] ?? 0;
          const failures = (fileReadFailuresBeforeSuccess as Record<string, number>)[path] ?? 0;
          const failureAttempts = (fileReadFailureAttempts as Record<string, number[]>)[path] ?? [];
          const notFoundAttempts = (fileReadNotFoundAttempts as Record<string, number[]>)[path] ?? [];
          if (attempt <= failures || failureAttempts.includes(attempt) || notFoundAttempts.includes(attempt)) {
            const notFound = notFoundAttempts.includes(attempt);
            window.setTimeout(() => {
              this.onmessage?.(new MessageEvent("message", {
                data: JSON.stringify({
                  id: request.id,
                  type: "error",
                  code: notFound ? "NOT_FOUND" : "FILE_READ_FAILED",
                  message: notFound ? "mock file not found" : "mock file read failed",
                  traceId: "trace_e2e"
                })
              }));
            }, delay);
            return;
          }
          const content = (fileReadResponses as Record<string, string[]>)[path]?.[attempt - 1]
            ?? (fileContents as Record<string, string>)[path]
            ?? "import { test } from '@playwright/test';\n\ntest('checkout', async () => {});\n";
          data = {
            path,
            content,
            encoding: "utf-8",
            size: content.length
          };
          window.setTimeout(() => {
            this.onmessage?.(new MessageEvent("message", {
              data: JSON.stringify({ id: request.id, type: "result", data, traceId: "trace_e2e" })
            }));
          }, delay);
          return;
        } else if (request.op === "workspace.write") {
          const path = params.path ?? "";
          const content = params.content ?? "";
          recordFileWrite(params.workspaceId ?? "", path, content);
          (fileContents as Record<string, string>)[path] = content;
        } else if (request.op === "workspace.rename") {
          const path = params.path ?? "";
          const name = params.name ?? "";
          const separatorIndex = Math.max(path.lastIndexOf("/"), path.lastIndexOf("\\"));
          const parent = separatorIndex >= 0 ? path.slice(0, separatorIndex) : "";
          const separator = path.includes("\\") ? "\\" : "/";
          const nextPath = parent ? `${parent}${separator}${name}` : name;
          window.setTimeout(() => {
            const contents = fileContents as Record<string, string>;
            contents[nextPath] = contents[path] ?? "";
            delete contents[path];
            this.onmessage?.(new MessageEvent("message", {
              data: JSON.stringify({ id: request.id, type: "result", data: null, traceId: "trace_e2e" })
            }));
          }, (workspaceMutationDelays as Record<string, number>)[request.op] ?? 0);
          return;
        } else if (request.op === "workspace.move") {
          const workspaceId = params.workspaceId ?? "";
          const sourcePath = params.sourcePath ?? "";
          const targetPath = params.targetPath ?? "";
          recordWorkspaceMove(workspaceId, sourcePath, targetPath);
          const contents = fileContents as Record<string, string>;
          for (const currentPath of Object.keys(contents)) {
            if (currentPath !== sourcePath && !currentPath.startsWith(`${sourcePath}/`)) continue;
            const movedPath = `${targetPath}${currentPath.slice(sourcePath.length)}`;
            contents[movedPath] = contents[currentPath] ?? "";
            delete contents[currentPath];
          }
          const nextViewLists = (workspaceViewListsAfterMoves as Array<Record<string, {
            entries: Array<Record<string, unknown>>;
            warnings?: Array<{ alias?: string; code: string; message: string }>;
            truncated?: boolean;
          }>>)[workspaceMoveAttempt];
          workspaceMoveAttempt += 1;
          if (nextViewLists) {
            const currentViewLists = workspaceViewLists as Record<string, unknown>;
            for (const key of Object.keys(currentViewLists)) delete currentViewLists[key];
            Object.assign(currentViewLists, nextViewLists);
          }
        } else if (request.op === "agent-config.list") {
          const scope = params.scope ?? "PUBLIC";
          const path = params.path ?? "";
          recordAgentFileFrame({
            op: request.op,
            scope,
            path,
            workspaceId: params.workspaceId,
            worktreeId: params.worktreeId
          });
          data = agentEntries(scope, path);
        } else if (request.op === "agent-config.read") {
          const scope = params.scope ?? "PUBLIC";
          const path = params.path ?? "";
          const key = `${scope}:${path}`;
          const attemptKey = `${scope}:${params.workspaceId ?? ""}:${params.worktreeId ?? ""}:${path}`;
          const attempt = (agentReadAttempts[attemptKey] ?? 0) + 1;
          agentReadAttempts[attemptKey] = attempt;
          recordAgentFileFrame({
            op: request.op,
            scope,
            path,
            workspaceId: params.workspaceId,
            worktreeId: params.worktreeId,
            attempt
          });
          const delay = (agentFileReadDelays as Record<string, number[]>)[key]?.[attempt - 1] ?? 0;
          const failureAttempts = (agentFileReadFailureAttempts as Record<string, number[]>)[key] ?? [];
          const notFoundAttempts = (agentFileReadNotFoundAttempts as Record<string, number[]>)[key] ?? [];
          if (failureAttempts.includes(attempt) || notFoundAttempts.includes(attempt)) {
            const notFound = notFoundAttempts.includes(attempt);
            window.setTimeout(() => {
              this.onmessage?.(new MessageEvent("message", {
                data: JSON.stringify({
                  id: request.id,
                  type: "error",
                  code: notFound ? "NOT_FOUND" : "FILE_READ_FAILED",
                  message: notFound ? "mock Agent file not found" : "mock Agent file read failed",
                  traceId: "trace_agent_e2e"
                })
              }));
            }, delay);
            return;
          }
          const content = (agentFileReadResponses as Record<string, string[]>)[key]?.[attempt - 1]
            ?? (agentFileContents as Record<string, string>)[key]
            ?? "";
          data = { path, content, encoding: "utf-8", size: content.length };
          window.setTimeout(() => {
            this.onmessage?.(new MessageEvent("message", {
              data: JSON.stringify({ id: request.id, type: "result", data, traceId: "trace_agent_e2e" })
            }));
          }, delay);
          return;
        } else if (request.op === "agent-config.write") {
          const scope = params.scope ?? "PUBLIC";
          const path = params.path ?? "";
          const content = params.content ?? "";
          recordAgentFileFrame({
            op: request.op,
            scope,
            path,
            workspaceId: params.workspaceId,
            worktreeId: params.worktreeId,
            content
          });
          (agentFileContents as Record<string, string>)[`${scope}:${path}`] = content;
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
  }, {
    fileContents: capture.fileContents ?? {},
    fileReadDelays: capture.fileReadDelays ?? {},
    fileReadFailuresBeforeSuccess: capture.fileReadFailuresBeforeSuccess ?? {},
    fileReadFailureAttempts: capture.fileReadFailureAttempts ?? {},
    fileReadNotFoundAttempts: capture.fileReadNotFoundAttempts ?? {},
    fileReadResponses: capture.fileReadResponses ?? {},
    workspaceMutationDelays: capture.workspaceMutationDelays ?? {},
    workspaceViewLists: capture.workspaceViewLists ?? {},
    workspaceViewListsAfterMoves: capture.workspaceViewListsAfterMoves ?? [],
    workspaceViewContents: capture.workspaceViewContents ?? {},
    agentFileContents: capture.agentFileContents ?? {},
    agentFileReadDelays: capture.agentFileReadDelays ?? {},
    agentFileReadFailureAttempts: capture.agentFileReadFailureAttempts ?? {},
    agentFileReadNotFoundAttempts: capture.agentFileReadNotFoundAttempts ?? {},
    agentFileReadResponses: capture.agentFileReadResponses ?? {}
  });
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
  const nightTasks = capture.nightTasks ?? [];
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
      if (method === "GET" && url.pathname === "/api/internal/platform/workspace-management/agent-config/public/status") {
        await route.fulfill(json({
          scope: "PUBLIC",
          enabled: true,
          writable: true,
          gitUrl: "git@example.test:opencode-config.git",
          gitRootPath: "/mock/public-config",
          agentDirectory: "/mock/public-config/opencode",
          currentBranch: "main",
          commitHash: "public_commit"
        }));
        return;
      }
      if (method === "GET" && /^\/api\/internal\/platform\/workspace-management\/agent-config\/workspaces\/[^/]+\/status$/.test(url.pathname)) {
        await route.fulfill(json({
          scope: "WORKSPACE",
          enabled: true,
          writable: true,
          gitUrl: null,
          gitRootPath: "/mock/workspace-config",
          agentDirectory: "/mock/workspace-config/.opencode",
          currentBranch: "feature_testagent_20260715",
          commitHash: "workspace_agent_commit"
        }));
        return;
      }
      if (method === "GET" && url.pathname === "/api/internal/platform/workspace-management/agent-config/public/repositories") {
        await route.fulfill(json(capture.publicAgentRepositories ?? [{
          linuxServerId: "10.8.0.12",
          serverName: "dev-backend",
          gitRootPath: "/mock/public-config",
          configDirPath: "/mock/public-config/opencode",
          worktreeRootPath: "/mock/public-worktrees",
          status: "READY",
          initialized: true,
          initializationAllowed: true,
          currentBranch: "main",
          commitHash: "public_commit",
          message: null
        }]));
        return;
      }
      if (method === "GET" && url.pathname === "/api/internal/platform/workspace-management/agent-config/public/worktrees") {
        const linuxServerId = url.searchParams.get("linuxServerId") ?? "";
        await route.fulfill(json(capture.publicAgentWorktreesByServer?.[linuxServerId] ?? []));
        return;
      }
      if (method === "POST" && url.pathname === "/api/internal/platform/workspace-management/agent-config/file-ws-route") {
        const body = JSON.parse(route.request().postData() ?? "{}") as {
          scope?: string;
          workspaceId?: string;
          worktreeId?: string;
          linuxServerId?: string;
        };
        await route.fulfill(json({
          scope: body.scope ?? "PUBLIC",
          workspaceId: body.workspaceId,
          worktreeId: body.worktreeId,
          linuxServerId: body.linuxServerId ?? "10.8.0.12",
          baseUrl: "http://127.0.0.1:8080",
          webSocketPath: "/api/internal/platform/workspace-management/file/ws",
          sameServer: true,
          message: null
        }));
        return;
      }
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
    if (method === "GET" && url.pathname === "/api/internal/platform/opencode-runtime/night-execution/slots") {
      await route.fulfill(json({
        timeZone: "Asia/Shanghai",
        windowStart: "2026-07-18T13:00:00Z",
        windowEnd: "2026-07-18T23:00:00Z",
        capacity: 2,
        slots: [{
          slotStart: "2026-07-18T13:15:00Z",
          slotEnd: "2026-07-18T13:30:00Z",
          reservedCount: 0,
          capacity: 2,
          available: true,
          recommended: true
        }]
      }));
      return;
    }
    if (method === "POST" && url.pathname === "/api/internal/platform/opencode-runtime/night-execution/tasks") {
      const request = JSON.parse(route.request().postData() ?? "{}") as Record<string, unknown>;
      capture.nightTaskRequests?.push(request);
      const taskId = `net_e2e_${nightTasks.length + 1}`;
      const task = {
        taskId,
        sessionId: String(request.sessionId ?? "ses_night_created"),
        workspaceId: String(request.workspaceId ?? "wrk_1234567890abcdef"),
        sessionTitle: String(request.sessionTitle ?? "夜间任务"),
        contentPreview: String(request.prompt ?? "夜间任务"),
        status: "SCHEDULED",
        slotStart: String(request.slotStart ?? "2026-07-18T13:15:00Z"),
        slotEnd: "2026-07-18T13:30:00Z",
        windowEnd: "2026-07-18T23:00:00Z",
        rolloverCount: 0,
        runId: null,
        errorCode: null,
        errorMessage: null,
        createdAt: "2026-07-18T04:00:00Z",
        updatedAt: "2026-07-18T04:00:00Z"
      };
      nightTasks.push(task);
      await route.fulfill(json(task));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/platform/opencode-runtime/night-execution/tasks") {
      const sessionId = url.searchParams.get("sessionId");
      const pending = nightTasks.filter((task) =>
        (task.status === "SCHEDULED" || task.status === "DISPATCHING")
        && (!sessionId || task.sessionId === sessionId));
      const visibleFailure = sessionId
        ? nightTasks.find((task) => task.sessionId === sessionId && task.status === "FAILED") ?? null
        : null;
      const pageNumber = Math.max(Number(url.searchParams.get("page") ?? "1"), 1);
      const pageSize = Math.max(Number(url.searchParams.get("size") ?? "100"), 1);
      const start = (pageNumber - 1) * pageSize;
      await route.fulfill(json({
        items: pending.slice(start, start + pageSize),
        page: pageNumber,
        size: pageSize,
        total: pending.length,
        visibleFailure
      }));
      return;
    }
    if (method === "POST" && url.pathname === "/api/internal/platform/opencode-runtime/sessions") {
      capture.sessionRequests?.push(JSON.parse(route.request().postData() ?? "{}") as Record<string, unknown>);
      await capture.sessionRequestGate;
      await route.fulfill(json(session()));
      return;
    }
    if (method === "GET" && /^\/api\/internal\/platform\/opencode-runtime\/sessions\/[^/]+$/.test(url.pathname)) {
      const sessionId = decodeURIComponent(url.pathname.match(/\/sessions\/([^/]+)$/)?.[1] ?? "ses_1");
      const nightTask = nightTasks.find((task) => task.sessionId === sessionId);
      await route.fulfill(json(nightTask ? {
        sessionId,
        workspaceId: nightTask.workspaceId,
        title: nightTask.sessionTitle,
        status: "ACTIVE",
        sourceType: "SCHEDULED_TASK",
        sourceRefId: nightTask.taskId,
        createdAt: "2026-07-18T04:00:00Z",
        updatedAt: "2026-07-18T04:00:00Z"
      } : (capture.sessions ?? []).find((item) => item.sessionId === sessionId) ?? session()));
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
    if (method === "POST" && url.pathname === "/api/internal/platform/opencode-runtime/run-feedbacks/me/query") {
      const request = JSON.parse(route.request().postData() ?? "{}") as Record<string, unknown>;
      capture.runFeedbackQueryRequests?.push(request);
      const runIds = Array.isArray(request.runIds) ? request.runIds.filter((runId): runId is string => typeof runId === "string") : [];
      await route.fulfill(json(runIds.map((runId) => ({ runId, runStatus: "SUCCEEDED", feedback: null }))));
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
      const requestIndex = (capture.runRequests?.length ?? 1) - 1;
      await (capture.runRequestGates?.[requestIndex] ?? capture.runRequestGate);
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

function workspaceViewDirectoryEntry(id: string, path: string) {
  return {
    id,
    path,
    name: path.split("/").at(-1) ?? path,
    directory: true,
    size: 0,
    lastModifiedAt: "2026-06-19T00:00:00Z",
    locator: { kind: "WORKSPACE", path },
    source: "WORKSPACE",
    merged: false,
    collision: false,
    readonly: false,
    workspacePath: path,
    referenceAliases: []
  };
}

function workspaceViewFileEntry(id: string, path: string) {
  return {
    ...workspaceViewDirectoryEntry(id, path),
    directory: false,
    size: 12
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

function agentWorkspaceSetup() {
  return {
    ...runnableWorkspaceSetup(),
    workspaceTemplates: {
      app_gcms: [{
        workspaceId: "awp_1",
        workspaceName: "F-GCMS",
        appId: "app_gcms",
        repositoryId: "repo_1",
        defaultBranch: "main",
        createdAt: "2026-06-19T00:00:00Z",
        updatedAt: "2026-06-19T00:00:00Z"
      }]
    },
    workspaceVersions: {
      "app_gcms:awp_1": [{
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1",
        appId: "app_gcms",
        repositoryId: "repo_1",
        version: "2026年7月",
        branch: "feature_testagent_20260715",
        repoRootPath: "/Users/huang/workspace/app-feature",
        workspaceRootPath: "/Users/huang/workspace/app-feature/F-GCMS/workspace",
        runtimeWorkspace: {
          ...workspace(),
          workspaceId: "wrk_feature_agent",
          name: "F-GCMS feature",
          rootPath: "/Users/huang/workspace/app-feature/F-GCMS/workspace",
          appId: "app_gcms",
          versionId: "awv_20260715",
          applicationWorkspaceId: "awp_1"
        },
        status: "ACTIVE",
        createdAt: "2026-06-19T00:00:00Z",
        updatedAt: "2026-06-19T00:00:00Z"
      }]
    }
  };
}

function publicAgentRepository(linuxServerId: string, serverName: string) {
  return {
    linuxServerId,
    serverName,
    gitRootPath: `/mock/${linuxServerId}/public-config`,
    configDirPath: `/mock/${linuxServerId}/public-config/opencode`,
    worktreeRootPath: `/mock/${linuxServerId}/public-worktrees`,
    status: "READY",
    initialized: true,
    initializationAllowed: true,
    currentBranch: "main",
    commitHash: `${linuxServerId}_commit`,
    message: null
  };
}

function publicAgentWorktree(linuxServerId: string) {
  return {
    worktreeId: `agw_${linuxServerId}`,
    scope: "PUBLIC",
    workspaceId: null,
    linuxServerId,
    worktreeName: "public-usr_admin",
    branch: "public-usr_admin",
    rootPath: `/mock/${linuxServerId}/public-worktrees/public-usr_admin`,
    agentDirectory: `/mock/${linuxServerId}/public-worktrees/public-usr_admin/opencode`,
    status: "ACTIVE",
    createdAt: "2026-07-17T00:00:00Z",
    updatedAt: "2026-07-17T00:00:00Z",
    createdByUserId: "usr_admin",
    createdByUsername: "admin"
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
