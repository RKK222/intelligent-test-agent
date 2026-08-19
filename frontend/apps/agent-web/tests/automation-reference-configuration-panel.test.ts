import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import AutomationReferenceConfigurationPanel from "../src/components/AutomationReferenceConfigurationPanel.vue";

function repository() {
  return {
    repositoryId: "repo_automation",
    gitUrl: "ssh://git.example.test/automation.git",
    name: "接口自动化库",
    englishName: "api-automation",
    repositoryType: "AUTOMATION_CODE_REPOSITORY",
    repositoryTypeLabel: "自动化代码库",
    standard: false,
    createdAt: "2026-08-01T00:00:00Z",
    updatedAt: "2026-08-01T00:00:00Z"
  };
}

function template() {
  return {
    workspaceId: "awp_auto",
    appId: "app-demo",
    repositoryId: "repo_automation",
    branch: "main",
    directoryPath: "scripts/e2e",
    workspaceName: "接口自动化",
    enabled: true,
    standard: false,
    repositoryType: "AUTOMATION_CODE_REPOSITORY",
    activeVersion: {
      versionId: "awv_old",
      version: "20260812",
      branch: "main",
      replicaStatus: "READY",
      activatedAt: "2026-08-12T00:00:00Z"
    },
    createdAt: "2026-08-12T00:00:00Z",
    updatedAt: "2026-08-12T00:00:00Z"
  };
}

function version(versionId: string, value: string) {
  return {
    versionId,
    applicationWorkspaceId: "awp_auto",
    appId: "app-demo",
    repositoryId: "repo_automation",
    version: value,
    branch: "main",
    status: "ACTIVE",
    replicaStatus: "READY",
    createdAt: "2026-08-12T00:00:00Z",
    updatedAt: "2026-08-12T00:00:00Z"
  };
}

function synchronization(versionId = "awv_old", overrides: Record<string, unknown> = {}) {
  return {
    applicationWorkspaceId: "awp_auto",
    workspaceName: "接口自动化",
    repositoryId: "repo_automation",
    repositoryName: "接口自动化库",
    versionId,
    version: versionId === "awv_new" ? "20260819" : "20260812",
    branch: "main",
    targetCommitHash: "abc123",
    status: "READY",
    operation: "SYNCHRONIZE",
    targetServerCount: 2,
    readyServerCount: 2,
    servers: [
      { linuxServerId: "linux-a", serverName: "server-a", status: "READY", online: true, currentBranch: "main", currentCommitHash: "abc123" },
      { linuxServerId: "linux-b", serverName: "server-b", status: "READY", online: true, currentBranch: "main", currentCommitHash: "abc123" }
    ],
    traceId: "trace_auto_sync",
    message: null,
    ...overrides
  };
}

function api(overrides: Record<string, unknown> = {}) {
  return {
    listApplicationRepositories: vi.fn().mockResolvedValue([repository()]),
    listApplicationWorkspaces: vi.fn().mockResolvedValue([]),
    listWorkspaceTemplates: vi.fn().mockResolvedValue([]),
    listWorkspaceVersions: vi.fn().mockResolvedValue([]),
    listRepositoryBranches: vi.fn().mockResolvedValue(["release/automation-v2"]),
    getRepositoryTree: vi.fn().mockResolvedValue({
      nodes: [{
        name: "scripts",
        path: "scripts",
        type: "directory",
        children: [{ name: "e2e", path: "scripts/e2e", type: "directory", children: [] }]
      }]
    }),
    createApplicationWorkspace: vi.fn().mockResolvedValue({ operationId: "wco_auto", status: "ACCEPTED" }),
    getWorkspaceCreateOperation: vi.fn().mockResolvedValue({
      operationId: "wco_auto",
      status: "SUCCEEDED",
      currentStep: "COMPLETED",
      steps: []
    }),
    createWorkspaceVersion: vi.fn().mockResolvedValue(version("awv_new", "20260819")),
    activateAutomationWorkspaceVersion: vi.fn().mockResolvedValue({}),
    synchronizeAutomationWorkspaceVersion: vi.fn().mockImplementation(
      (_appId: string, _templateId: string, versionId: string) => Promise.resolve(synchronization(versionId))
    ),
    getAutomationWorkspaceVersionSynchronizationStatus: vi.fn().mockImplementation(
      (_appId: string, _templateId: string, versionId: string) => Promise.resolve(synchronization(versionId))
    ),
    updateApplicationWorkspace: vi.fn().mockResolvedValue({}),
    ...overrides
  };
}

function render(mockApi: ReturnType<typeof api>, canManage = true) {
  return mount(AutomationReferenceConfigurationPanel, {
    props: { open: true, appId: "app-demo", canManage },
    global: { provide: { api: mockApi } }
  });
}

describe("AutomationReferenceConfigurationPanel", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it("selects an arbitrary existing directory and creates an application-level readonly reference", async () => {
    const mockApi = api();
    vi.spyOn(globalThis.crypto, "randomUUID").mockReturnValue("42345678-1234-1234-1234-123456789abc");
    const wrapper = render(mockApi);
    await flushPromises();

    expect(mockApi.listRepositoryBranches).toHaveBeenCalledWith("repo_automation");
    expect(mockApi.getRepositoryTree).toHaveBeenCalledWith("app-demo", "repo_automation", "release/automation-v2");
    await wrapper.get('button[aria-label="选择目录 scripts/e2e"]').trigger("click");
    await wrapper.get('input[aria-label="自动化引用名称"]').setValue("接口回归");
    await wrapper.get('input[aria-label="自动化引用版本日期"]').setValue("20260819");
    await wrapper.get('button[aria-label="保存自动化目录引用"]').trigger("click");
    await flushPromises();

    expect(mockApi.createApplicationWorkspace).toHaveBeenCalledWith("app-demo", {
      repositoryId: "repo_automation",
      branch: "release/automation-v2",
      directoryPath: "scripts/e2e",
      workspaceName: "接口回归",
      version: "20260819",
      operationId: "wco_42345678123412341234123456789abc"
    });
    expect(wrapper.emitted("changed")).toBeTruthy();
  });

  it("shows configured versions and lets an administrator activate another version", async () => {
    const configuredTemplate = template();
    const mockApi = api({
      listWorkspaceTemplates: vi.fn().mockResolvedValue([configuredTemplate]),
      listWorkspaceVersions: vi.fn().mockResolvedValue([
        version("awv_new", "20260819"),
        version("awv_old", "20260812")
      ])
    });
    const wrapper = render(mockApi);
    await flushPromises();

    expect(wrapper.text()).toContain("scripts/e2e");
    const activate = wrapper.findAll("button").find((button) => button.text().includes("设为当前版本"));
    expect(activate).toBeTruthy();
    await activate!.trigger("click");
    await flushPromises();

    expect(mockApi.activateAutomationWorkspaceVersion).toHaveBeenCalledWith("app-demo", "awp_auto", "awv_new");
    expect(wrapper.emitted("changed")).toBeTruthy();
  });

  it("uses the shared three-stage dialog and shows per-server automation synchronization", async () => {
    const configuredTemplate = template();
    const pending = synchronization("awv_old", {
      status: "SYNCHRONIZING",
      readyServerCount: 1,
      servers: [
        { linuxServerId: "linux-a", serverName: "server-a", status: "READY", online: true, currentBranch: "main", currentCommitHash: "abc123" },
        { linuxServerId: "linux-b", serverName: "server-b", status: "PENDING", online: true }
      ]
    });
    const mockApi = api({
      listWorkspaceTemplates: vi.fn().mockResolvedValue([configuredTemplate]),
      listWorkspaceVersions: vi.fn().mockResolvedValue([version("awv_old", "20260812")]),
      getAutomationWorkspaceVersionSynchronizationStatus: vi.fn().mockResolvedValue(pending),
      synchronizeAutomationWorkspaceVersion: vi.fn().mockResolvedValue(pending)
    });
    const wrapper = render(mockApi);
    await flushPromises();

    expect(wrapper.text()).toContain("1/2 台就绪");
    await wrapper.get('button[aria-label="同步自动化版本 20260812"]').trigger("click");
    await flushPromises();

    const progress = wrapper.get('[aria-label="自动化代码库同步进度"]');
    expect(progress.text()).toContain("创建同步任务");
    expect(progress.text()).toContain("各服务器同步");
    expect(progress.text()).toContain("server-a");
    expect(progress.text()).toContain("已同步");
    expect(progress.text()).toContain("server-b");
    expect(progress.text()).toContain("等待同步");
    expect(wrapper.get('button[aria-label="关闭自动化代码库同步进度"]').attributes()).toHaveProperty("disabled");
    expect(mockApi.synchronizeAutomationWorkspaceVersion).toHaveBeenCalledWith(
      "app-demo", "awp_auto", "awv_old");

    wrapper.unmount();
  });

  it("keeps ordinary members readonly and avoids administrator-only repository APIs", async () => {
    const configuredTemplate = template();
    const mockApi = api({
      listWorkspaceTemplates: vi.fn().mockResolvedValue([configuredTemplate]),
      listWorkspaceVersions: vi.fn().mockResolvedValue([version("awv_old", "20260812")])
    });
    const wrapper = render(mockApi, false);
    await flushPromises();

    expect(mockApi.listApplicationRepositories).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain("接口自动化");
    expect(wrapper.text()).toContain("当前版本");
    expect(wrapper.text()).not.toContain("新增目录引用");
    expect(wrapper.text()).not.toContain("设为当前版本");
  });

  it("displays loading animation when branches and directory tree are being fetched", async () => {
    let resolveBranches!: (branches: string[]) => void;
    const branchesPromise = new Promise<string[]>((res) => { resolveBranches = res; });
    let resolveTree!: (tree: { nodes: Array<{ name: string; path: string; type: "file" | "directory"; children?: any[] }> }) => void;
    const treePromise = new Promise<{ nodes: Array<{ name: string; path: string; type: "file" | "directory"; children?: any[] }> }>((res) => { resolveTree = res; });

    const mockApi = api({
      listRepositoryBranches: vi.fn().mockReturnValue(branchesPromise),
      getRepositoryTree: vi.fn().mockReturnValue(treePromise)
    });
    const wrapper = render(mockApi);
    await flushPromises();

    // 在创建模式下分支加载中时展示拉取动画与文字
    expect(wrapper.text()).toContain("拉取分支中…");
    expect(wrapper.find('[data-component="spinner"]').exists()).toBe(true);

    resolveBranches(["main"]);
    await flushPromises();

    // 目录树加载中时展示目录读取动画与状态
    expect(wrapper.text()).toContain("正在读取目录…");

    resolveTree({ nodes: [] });
    await flushPromises();

    expect(wrapper.text()).not.toContain("正在读取目录…");
  });

  it("caches previously fetched branches and directory trees in memory to avoid duplicate requests", async () => {
    const mockApi = api({
      listRepositoryBranches: vi.fn().mockResolvedValue(["main", "release/v1"]),
      getRepositoryTree: vi.fn().mockResolvedValue({
        nodes: [
          { name: "scripts", path: "scripts", type: "directory", children: [] }
        ]
      })
    });
    const wrapper = render(mockApi);
    await flushPromises();

    // 第一次进入创建模式，拉取分支和当前分支目录
    expect(mockApi.listRepositoryBranches).toHaveBeenCalledTimes(1);
    expect(mockApi.getRepositoryTree).toHaveBeenCalledTimes(1);

    // 切换到 release/v1 分支，首次读取该分支目录
    const branchSelect = wrapper.get('select[aria-label="自动化引用分支"]');
    await branchSelect.setValue("release/v1");
    await flushPromises();
    expect(mockApi.getRepositoryTree).toHaveBeenCalledTimes(2);

    // 切回 main 分支：命中前端目录缓存，不再发起网络请求
    await branchSelect.setValue("main");
    await flushPromises();
    expect(mockApi.getRepositoryTree).toHaveBeenCalledTimes(2);

    // 重新切换分支：命中前端分支和目录缓存，不重复发起请求
    await branchSelect.setValue("main");
    await flushPromises();
    expect(mockApi.listRepositoryBranches).toHaveBeenCalledTimes(1);
    expect(mockApi.getRepositoryTree).toHaveBeenCalledTimes(2);
  });

  it("automatically synchronizes repository when clicking repository card or refresh button", async () => {
    const configuredTemplate = template();
    const ready = synchronization("awv_old", {
      status: "READY",
      readyServerCount: 2,
      servers: [
        { linuxServerId: "linux-a", serverName: "server-a", status: "READY", online: true, currentBranch: "main", currentCommitHash: "abc123" },
        { linuxServerId: "linux-b", serverName: "server-b", status: "READY", online: true, currentBranch: "main", currentCommitHash: "abc123" }
      ]
    });
    const mockApi = api({
      listWorkspaceTemplates: vi.fn().mockResolvedValue([configuredTemplate]),
      listWorkspaceVersions: vi.fn().mockResolvedValue([version("awv_old", "20260812")]),
      getAutomationWorkspaceVersionSynchronizationStatus: vi.fn().mockResolvedValue(ready),
      synchronizeAutomationWorkspaceVersion: vi.fn().mockResolvedValue(ready)
    });
    const wrapper = render(mockApi);
    await flushPromises();

    // 点击左侧代码库卡片，自动触发代码同步并弹出进度对话框
    await wrapper.get('button[aria-label="选择接口自动化库"]').trigger("click");
    await flushPromises();

    expect(mockApi.synchronizeAutomationWorkspaceVersion).toHaveBeenCalledWith(
      "app-demo", "awp_auto", "awv_old"
    );
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true);
    expect(wrapper.text()).toContain("创建同步任务");

    // 关闭同步进度对话框
    await wrapper.get('button[aria-label="关闭自动化代码库同步进度"]').trigger("click");
    await flushPromises();

    // 点击刷新 Git 指针按钮，再次触发同步
    await wrapper.get('button[aria-label="刷新接口自动化库 Git 指针"]').trigger("click");
    await flushPromises();
    expect(mockApi.synchronizeAutomationWorkspaceVersion).toHaveBeenCalledTimes(2);

    wrapper.unmount();
  });
});
