import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import { BackendApiError } from "@test-agent/backend-api";
import AutomationReferenceConfigurationPanel from "../src/components/AutomationReferenceConfigurationPanel.vue";

function configuration(overrides: Record<string, unknown> = {}) {
  return {
    generation: 3,
    branch: "main",
    directoryPath: "scripts/e2e",
    description: "接口自动化库 / main / scripts/e2e，只读自动化引用",
    merge: false,
    targetCommitHash: "abcdef1234567890",
    alias: "automation-api-automation",
    logicalPath: "{env:OPENCODE_REFERENCES_DIR}/automation/app-key/api-automation/3/scripts/e2e",
    directoryName: "e2e",
    activatedAt: "2026-08-21T00:00:00Z",
    status: "READY",
    ...overrides
  };
}

function repository(overrides: Record<string, unknown> = {}) {
  return {
    appId: "app-demo",
    repositoryId: "repo_automation",
    name: "接口自动化库",
    englishName: "api-automation",
    gitUrl: "ssh://git.example.test/automation.git",
    status: "READY",
    operation: "CONFIGURE",
    lockVersion: 4,
    activeGeneration: 3,
    pendingGeneration: null,
    currentConfiguration: configuration(),
    pendingConfiguration: null,
    targetServerCount: 1,
    readyServerCount: 1,
    servers: [{
      linuxServerId: "linux-a",
      status: "READY",
      online: true,
      currentBranch: "main",
      currentCommitHash: "abcdef1234567890",
      matchesTarget: true,
      syncedAt: "2026-08-21T00:00:00Z",
      verifiedAt: null,
      error: null
    }],
    traceId: null,
    message: null,
    ...overrides
  };
}

function tree() {
  return [{
    name: "scripts",
    path: "scripts",
    type: "directory",
    children: [{ name: "e2e", path: "scripts/e2e", type: "directory", children: [] }]
  }];
}

function api(overrides: Record<string, unknown> = {}) {
  return {
    listAutomationReferenceRepositories: vi.fn().mockResolvedValue([repository()]),
    getAutomationReferenceRepositoryStatus: vi.fn().mockResolvedValue(repository()),
    listAutomationReferenceRepositoryBranches: vi.fn().mockResolvedValue(["main", "release/v2"]),
    listAutomationReferenceRepositoryTree: vi.fn().mockResolvedValue(tree()),
    configureAutomationReferenceRepository: vi.fn(),
    synchronizeAutomationReferenceRepository: vi.fn(),
    verifyAutomationReferenceRepository: vi.fn(),
    terminateAutomationReferenceRepository: vi.fn(),
    readWorkspaceAgentFile: vi.fn().mockRejectedValue(new BackendApiError(404, {
      success: false,
      code: "FILE_NOT_FOUND",
      message: "文件不存在",
      traceId: "trace_missing"
    })),
    writeWorkspaceAgentFile: vi.fn().mockResolvedValue(undefined),
    ...overrides
  };
}

function render(mockApi: ReturnType<typeof api>, canManage = true) {
  return mount(AutomationReferenceConfigurationPanel, {
    props: { open: true, appId: "app-demo", workspaceId: "wrk-personal", canManage },
    global: { provide: { api: mockApi } }
  });
}

describe("AutomationReferenceConfigurationPanel", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it("uses the asset repository layout while keeping arbitrary nested directory selection", async () => {
    const wrapper = render(api());
    await flushPromises();

    expect(wrapper.text()).toContain("1/1 台就绪");
    expect(wrapper.text()).toContain("目标 Git 指针");
    expect(wrapper.text()).toContain("实际 HEAD");
    expect(wrapper.text()).toContain("更新副本");
    expect(wrapper.text()).toContain("刷新 Git 指针");
    expect(wrapper.text()).toContain("切换分支");
    expect(wrapper.get('input[aria-label="目录名称（sdd-folder-name）"]').element).toHaveProperty("value", "e2e");
    expect(wrapper.text()).not.toContain("历史版本");
    expect(wrapper.text()).not.toContain("上一次引用");
  });

  it("clicking another repository only selects it and never starts synchronization", async () => {
    const second = repository({
      repositoryId: "repo_ui",
      name: "UI 自动化库",
      englishName: "ui-automation",
      currentConfiguration: configuration({
        branch: "release/ui",
        directoryPath: "ui/tests",
        alias: "automation-ui-automation",
        logicalPath: "{env:OPENCODE_REFERENCES_DIR}/automation/app-key/ui-automation/3/ui/tests"
      })
    });
    const mockApi = api({
      listAutomationReferenceRepositories: vi.fn().mockResolvedValue([repository(), second])
    });
    const wrapper = render(mockApi);
    await flushPromises();

    await wrapper.get('button[aria-label="选择UI 自动化库"]').trigger("click");
    await flushPromises();

    expect(mockApi.synchronizeAutomationReferenceRepository).not.toHaveBeenCalled();
    expect(mockApi.configureAutomationReferenceRepository).not.toHaveBeenCalled();
    expect(mockApi.listAutomationReferenceRepositoryBranches).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain("release/ui · ui/tests");
  });

  it("saves one app-repository configuration and reconciles the whole JSONC set after activation", async () => {
    vi.useFakeTimers();
    const pending = repository({
      status: "SYNCHRONIZING",
      operation: "CONFIGURE",
      pendingGeneration: 4,
      pendingConfiguration: configuration({
        generation: 4,
        branch: "release/v2",
        directoryPath: "scripts",
        logicalPath: "{env:OPENCODE_REFERENCES_DIR}/automation/app-key/api-automation/4/scripts",
        status: "SYNCHRONIZING"
      })
    });
    const ready = repository({
      activeGeneration: 4,
      currentConfiguration: configuration({
        generation: 4,
        branch: "release/v2",
        directoryPath: "scripts",
        logicalPath: "{env:OPENCODE_REFERENCES_DIR}/automation/app-key/api-automation/4/scripts"
      })
    });
    const mockApi = api({
      configureAutomationReferenceRepository: vi.fn().mockResolvedValue(pending),
      getAutomationReferenceRepositoryStatus: vi.fn().mockResolvedValue(ready),
      listAutomationReferenceRepositories: vi.fn()
        .mockResolvedValueOnce([repository()])
        .mockResolvedValueOnce([ready]),
      readWorkspaceAgentFile: vi.fn()
        .mockResolvedValueOnce({ content: "" })
        .mockResolvedValueOnce({ content: `{
  "references": {
    "old-main": {
      "path": "{env:OPENCODE_APP_WORKSPACE_ROOT}/old-main",
      "testagent-reference-kind": "automation",
      "testagent-automation-repository-id": "repo_automation"
    },
    "old-release": {
      "path": "{env:OPENCODE_APP_WORKSPACE_ROOT}/old-release",
      "testagent-reference-kind": "automation",
      "testagent-automation-workspace-id": "awp_old"
    }
  },
  "permission": { "external_directory": {
    "{env:OPENCODE_APP_WORKSPACE_ROOT}/old-main/*": "allow",
    "{env:OPENCODE_APP_WORKSPACE_ROOT}/old-release/*": "allow"
  } }
}` })
    });
    const wrapper = render(mockApi);
    await flushPromises();
    const switchButton = wrapper.findAll("button").find((button) => button.text() === "切换分支");
    expect(switchButton).toBeDefined();
    await switchButton!.trigger("click");
    await flushPromises();
    await wrapper.get('select[aria-label="目标分支"]').setValue("release/v2");
    const continueButton = wrapper.findAll("button").find((button) => button.text() === "继续");
    expect(continueButton).toBeDefined();
    await continueButton!.trigger("click");
    await flushPromises();
    await wrapper.get('button[aria-label="选择目录 scripts"]').trigger("click");
    await wrapper.get('textarea[aria-label="描述（description）"]').setValue("发布分支自动化脚本");
    expect(wrapper.get('button[aria-label="保存自动化配置并生效"]').attributes("disabled")).toBeUndefined();
    await wrapper.get("form").trigger("submit");
    await flushPromises();

    expect(mockApi.configureAutomationReferenceRepository).toHaveBeenCalledWith("app-demo", "repo_automation", {
      branch: "release/v2",
      directoryPath: "scripts",
      description: "发布分支自动化脚本",
      merge: false,
      expectedGeneration: 3,
      operationId: expect.stringMatching(/^aar_/)
    });

    await vi.advanceTimersByTimeAsync(1_000);
    await flushPromises();
    const written = mockApi.writeWorkspaceAgentFile.mock.calls.at(-1)?.[2] as string;
    expect(written).toContain('"testagent-automation-app-id": "app-demo"');
    expect(written).toContain('"testagent-automation-repository-id": "repo_automation"');
    expect(written).toContain('"testagent-automation-generation": 4');
    expect(written).toContain('"merge": false');
    expect(written).not.toContain("old-main");
    expect(written).not.toContain("old-release");
  });

  it("keeps members readonly while still showing current branch, directory and server status", async () => {
    const mockApi = api();
    const wrapper = render(mockApi, false);
    await flushPromises();

    expect(wrapper.text()).toContain("main · scripts/e2e");
    expect(wrapper.text()).toContain("READY");
    expect(wrapper.find('button[aria-label="保存自动化配置并生效"]').exists()).toBe(false);
    expect(wrapper.findAll("button").some((button) => button.text() === "切换分支")).toBe(false);
    expect(mockApi.listAutomationReferenceRepositories).toHaveBeenCalledWith("app-demo");
  });

  it("retries RETRY_WAIT by terminating the fenced generation before synchronizing again", async () => {
    const active = repository({
      status: "SYNCHRONIZING",
      pendingGeneration: 4,
      servers: [{
        linuxServerId: "linux-a",
        status: "RETRY_WAIT",
        online: true,
        currentBranch: "main",
        currentCommitHash: "abcdef1234567890",
        matchesTarget: true,
        error: "Git 操作超时"
      }]
    });
    const terminated = repository({ status: "FAILED", activeGeneration: 3, pendingGeneration: null });
    const retried = repository({ status: "SYNCHRONIZING", pendingGeneration: 5 });
    const mockApi = api({
      synchronizeAutomationReferenceRepository: vi.fn()
        .mockResolvedValueOnce(active)
        .mockResolvedValueOnce(retried),
      terminateAutomationReferenceRepository: vi.fn().mockResolvedValue(terminated)
    });
    const wrapper = render(mockApi);
    await flushPromises();

    await wrapper.get('button[aria-label="更新接口自动化库副本"]').trigger("click");
    await flushPromises();
    await wrapper.get('button[aria-label="重试自动化代码库同步"]').trigger("click");
    await flushPromises();

    expect(mockApi.terminateAutomationReferenceRepository)
      .toHaveBeenCalledWith("app-demo", "repo_automation", 4);
    expect(mockApi.synchronizeAutomationReferenceRepository).toHaveBeenCalledTimes(2);
    expect(mockApi.synchronizeAutomationReferenceRepository.mock.calls[1]?.slice(0, 3))
      .toEqual(["app-demo", "repo_automation", 3]);
  });
});
