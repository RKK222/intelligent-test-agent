import { QueryClient, VueQueryPlugin } from "@tanstack/vue-query";
import { fireEvent, render, waitFor } from "@testing-library/vue";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { BackendApiClient } from "@test-agent/backend-api";
import type {
  CommonParameterMemoryCluster,
  CommonParameterMemoryProcess,
  CommonParameterChangeLog,
  CurrentUser,
  GeneralParameter,
  GeneralParameterListParams,
  PageResponse,
  RepositoryDeploymentOptions
} from "@test-agent/shared-types";
import GeneralParamManagementPanel from "../src/components/system/GeneralParamManagementPanel.vue";

const currentUser: CurrentUser = {
  userId: "usr_admin",
  username: "admin",
  unifiedAuthId: "AUTH_1",
  roles: ["SUPER_ADMIN"]
};

const macosParameter: GeneralParameter = {
  parameterId: "opencode_session_dir_macos",
  englishName: "OPENCODE_SESSION_DIR",
  chineseName: "OpenCode会话目录",
  parameterValue: "$TEST_AGENT_ROOT/temp/opencode-session",
  platform: "macos",
  editable: false,
  createdAt: "2026-06-29T00:00:00Z",
  updatedAt: "2026-06-29T00:00:00Z"
};

const editableParameter: GeneralParameter = {
  parameterId: "opencode_public_agent_git_url_all",
  englishName: "OPENCODE_PUBLIC_AGENT_GIT_URL",
  chineseName: "公共agent配置Git库地址",
  parameterValue: "https://example.com/agent.git",
  platform: "all",
  editable: true,
  createdAt: "2026-06-29T00:00:00Z",
  updatedAt: "2026-06-29T00:00:00Z"
};

const externalDeploymentOptions: RepositoryDeploymentOptions = {
  defaultDeploymentMode: "EXTERNAL",
  internalSshPrefix: "ssh://AUTH_1@",
  options: [
    { mode: "EXTERNAL", label: "外部部署" },
    { mode: "INTERNAL", label: "内部部署" }
  ]
};

function memoryProcess(overrides: Partial<CommonParameterMemoryProcess> = {}): CommonParameterMemoryProcess {
  return {
    backendProcessId: "bjp_backend_a",
    linuxServerId: "server-a",
    listenUrl: "http://server-a:8080",
    instanceId: "instance-a",
    capturedAt: "2026-07-19T12:00:00Z",
    status: "SUCCESS",
    errorCode: null,
    errorMessage: null,
    parameters: [{
      englishName: "NIGHT_EXECUTION_SLOT_CAPACITY",
      platform: "all",
      sourceValue: "20",
      memoryValue: "20",
      loadedAt: "2026-07-19T11:00:00Z",
      lastRefreshAttemptAt: "2026-07-19T11:00:00Z",
      refreshStatus: "SUCCESS",
      errorMessage: null
    }],
    ...overrides
  };
}

function memoryCluster(overrides: Partial<CommonParameterMemoryCluster> = {}): CommonParameterMemoryCluster {
  return {
    capturedAt: "2026-07-19T12:00:00Z",
    totalProcesses: 1,
    successfulProcesses: 1,
    partiallySuccessfulProcesses: 0,
    failedProcesses: 0,
    processes: [memoryProcess()],
    ...overrides
  };
}

function emptyParameterPage(): PageResponse<GeneralParameter> {
  return { items: [], page: 1, size: 50, total: 0 };
}

function backendApiWith(overrides: Partial<BackendApiClient>) {
  return {
    getRepositoryDeploymentOptions: vi.fn(async () => externalDeploymentOptions),
    listCommonParameterChangeLogs: vi.fn().mockResolvedValue([]),
    listCommonParameterMemoryValues: vi.fn().mockResolvedValue(memoryCluster()),
    refreshCommonParameterMemoryValues: vi.fn().mockResolvedValue(memoryCluster()),
    refreshCommonParameterMemoryValuesForProcess: vi.fn().mockResolvedValue(memoryProcess()),
    ...overrides
  } as Partial<BackendApiClient> as BackendApiClient;
}

function renderPanel(backendApi: BackendApiClient) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const view = render(GeneralParamManagementPanel, {
    props: { currentUser },
    global: {
      plugins: [[VueQueryPlugin, { queryClient: client }]],
      stubs: {
        ElButton: { emits: ["click"], template: `<button type="button" @click="$emit('click')"><slot /></button>` },
        ElSelect: {
          props: ["modelValue", "placeholder"],
          emits: ["update:modelValue", "change"],
          template: `<select :aria-label="placeholder" :value="modelValue" @change="$emit('update:modelValue', $event.target.value); $emit('change', $event.target.value)"><slot /></select>`
        },
        ElOption: { props: ["label", "value"], template: `<option :value="value">{{ label }}</option>` },
        ElDialog: { template: `<div><slot /></div>` },
        ElDrawer: {
          props: ["modelValue", "title"],
          emits: ["update:modelValue", "close"],
          template: `<section v-if="modelValue" role="dialog" :aria-label="title"><slot /></section>`
        },
        ElInput: { template: `<input />` },
        ElForm: { template: `<form><slot /></form>` },
        ElFormItem: { template: `<div><slot /></div>` },
        ElEmpty: { props: ["description"], template: `<div>{{ description }}<slot /></div>` },
        ElTag: { template: `<span><slot /></span>` }
      },
      provide: { api: backendApi }
    }
  });
  return { ...view, queryClient: client };
}

describe("general parameter management panel", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it("lists macos and refreshes immediately after platform selection", async () => {
    const listGeneralParameters = vi.fn(async (params: GeneralParameterListParams = {}) => ({
      items: params.platform === "macos" ? [macosParameter] : [],
      page: 1,
      size: 50,
      total: params.platform === "macos" ? 1 : 0
    } satisfies PageResponse<GeneralParameter>));
    const backendApi = backendApiWith({
      listGeneralParameters,
      listCommonParameterChangeLogs: vi.fn().mockResolvedValue([])
    });
    const view = renderPanel(backendApi);

    await waitFor(() => expect(listGeneralParameters).toHaveBeenCalledWith({
      platform: undefined,
      page: 1,
      size: 50
    }));
    expect(view.getByRole("option", { name: "macos" })).toBeTruthy();

    await fireEvent.update(view.getByRole("listbox", { name: "平台" }), "macos");

    await waitFor(() => expect(listGeneralParameters).toHaveBeenCalledWith({
      platform: "macos",
      page: 1,
      size: 50
    }));
    expect(await view.findByText("$TEST_AGENT_ROOT/temp/opencode-session")).toBeTruthy();
    view.queryClient.clear();
  });

  it("loads JVM memory values only after opening the drawer and shows every process", async () => {
    const listCommonParameterMemoryValues = vi.fn().mockResolvedValue(memoryCluster());
    const backendApi = backendApiWith({
      listGeneralParameters: vi.fn().mockResolvedValue(emptyParameterPage()),
      listCommonParameterMemoryValues
    });
    const view = renderPanel(backendApi);

    await waitFor(() => expect(backendApi.listGeneralParameters).toHaveBeenCalled());
    expect(listCommonParameterMemoryValues).not.toHaveBeenCalled();

    await fireEvent.click(view.getByRole("button", { name: "查看内存加载值" }));

    expect(await view.findByRole("dialog", { name: "JVM 内存参数值" })).toBeTruthy();
    await waitFor(() => expect(listCommonParameterMemoryValues).toHaveBeenCalledTimes(1));
    expect(await view.findByText("bjp_backend_a")).toBeTruthy();
    expect(await view.findByText("NIGHT_EXECUTION_SLOT_CAPACITY")).toBeTruthy();
    expect((await view.findAllByText("20")).length).toBeGreaterThan(0);
    view.queryClient.clear();
  });

  it("refreshes all or one Java and keeps partial failure details visible", async () => {
    const partial = memoryCluster({
      partiallySuccessfulProcesses: 1,
      failedProcesses: 0,
      successfulProcesses: 0,
      processes: [memoryProcess({
        status: "PARTIAL",
        errorCode: "REFRESH_PARTIAL",
        errorMessage: "1 个内存参数最近刷新失败，继续使用上一有效值",
        parameters: [{
          ...memoryProcess().parameters[0]!,
          refreshStatus: "FAILED",
          errorMessage: "读取或应用失败"
        }]
      })]
    });
    const refreshedProcess = memoryProcess({
      parameters: [{ ...memoryProcess().parameters[0]!, sourceValue: "24", memoryValue: "24" }]
    });
    const refreshAll = vi.fn().mockResolvedValue(partial);
    const refreshOne = vi.fn().mockResolvedValue(refreshedProcess);
    const backendApi = backendApiWith({
      listGeneralParameters: vi.fn().mockResolvedValue(emptyParameterPage()),
      listCommonParameterMemoryValues: vi.fn().mockResolvedValue(memoryCluster()),
      refreshCommonParameterMemoryValues: refreshAll,
      refreshCommonParameterMemoryValuesForProcess: refreshOne
    });
    const view = renderPanel(backendApi);
    await fireEvent.click(view.getByRole("button", { name: "查看内存加载值" }));
    await view.findByText("bjp_backend_a");

    await fireEvent.click(view.getByRole("button", { name: "刷新全部 Java" }));
    await waitFor(() => expect(refreshAll).toHaveBeenCalledTimes(1));
    expect(await view.findByText(/1 个 Java 部分成功/)).toBeTruthy();
    expect(await view.findByText("读取或应用失败")).toBeTruthy();

    await fireEvent.click(view.getByRole("button", { name: "刷新此 Java" }));
    await waitFor(() => expect(refreshOne).toHaveBeenCalledWith("bjp_backend_a"));
    expect((await view.findAllByText("24")).length).toBeGreaterThan(0);
    view.queryClient.clear();
  });

  it("shows an explicit error state when JVM memory values cannot be queried", async () => {
    const backendApi = backendApiWith({
      listGeneralParameters: vi.fn().mockResolvedValue(emptyParameterPage()),
      listCommonParameterMemoryValues: vi.fn().mockRejectedValue(new Error("内存值查询失败"))
    });
    const view = renderPanel(backendApi);

    await fireEvent.click(view.getByRole("button", { name: "查看内存加载值" }));

    expect(await view.findByText("内存值查询失败")).toBeTruthy();
    view.queryClient.clear();
  });

  it("shows an explicit empty state when no JVM memory parameter is registered", async () => {
    const backendApi = backendApiWith({
      listGeneralParameters: vi.fn().mockResolvedValue(emptyParameterPage()),
      listCommonParameterMemoryValues: vi.fn().mockResolvedValue(memoryCluster({
        totalProcesses: 0,
        successfulProcesses: 0,
        processes: []
      }))
    });
    const view = renderPanel(backendApi);

    await fireEvent.click(view.getByRole("button", { name: "查看内存加载值" }));

    expect(await view.findByText("暂无已注册的 JVM 内存参数")).toBeTruthy();
    view.queryClient.clear();
  });

  it("opens change logs drawer with current parameter details and history rows", async () => {
    const changeLog: CommonParameterChangeLog = {
      logId: "log_1",
      parameterId: macosParameter.parameterId,
      oldValue: "$OLD_ROOT/temp/opencode-session",
      newValue: macosParameter.parameterValue,
      changedByUserId: "usr_admin",
      changedByUsername: "admin",
      traceId: "trace_1",
      createdAt: "2026-06-30T02:00:00Z"
    };
    const listGeneralParameters = vi.fn(async () => ({
      items: [macosParameter],
      page: 1,
      size: 50,
      total: 1
    } satisfies PageResponse<GeneralParameter>));
    const listCommonParameterChangeLogs = vi.fn(async () => [changeLog]);
    const backendApi = backendApiWith({
      listGeneralParameters,
      listCommonParameterChangeLogs
    });
    const view = renderPanel(backendApi);

    await view.findByText("OPENCODE_SESSION_DIR");
    await fireEvent.click(view.getByRole("button", { name: "修改历史" }));

    expect(await view.findByRole("dialog", { name: "修改历史 - OPENCODE_SESSION_DIR" })).toBeTruthy();
    expect(view.getAllByText("OpenCode会话目录").length).toBeGreaterThan(0);
    expect(view.getAllByText("$TEST_AGENT_ROOT/temp/opencode-session").length).toBeGreaterThan(0);
    await waitFor(() => expect(listCommonParameterChangeLogs).toHaveBeenCalledWith(macosParameter.parameterId));
    expect(await view.findByText("$OLD_ROOT/temp/opencode-session")).toBeTruthy();
    view.queryClient.clear();
  });

  it("shows readonly markers when opening a non-editable parameter", async () => {
    const listGeneralParameters = vi.fn(async () => ({
      items: [macosParameter],
      page: 1,
      size: 50,
      total: 1
    } satisfies PageResponse<GeneralParameter>));
    const backendApi = backendApiWith({
      listGeneralParameters,
      listCommonParameterChangeLogs: vi.fn().mockResolvedValue([])
    });
    const view = renderPanel(backendApi);

    await view.findByText("OPENCODE_SESSION_DIR");
    await fireEvent.click(view.getByText("$TEST_AGENT_ROOT/temp/opencode-session"));

    expect(await view.findByText("只读参数")).toBeTruthy();
    expect(await view.findByText(/修改后将影响系统正常运行/)).toBeTruthy();
    view.queryClient.clear();
  });

  it("opens an editable parameter without readonly markers", async () => {
    const listGeneralParameters = vi.fn(async () => ({
      items: [editableParameter],
      page: 1,
      size: 50,
      total: 1
    } satisfies PageResponse<GeneralParameter>));
    const backendApi = backendApiWith({
      listGeneralParameters,
      listCommonParameterChangeLogs: vi.fn().mockResolvedValue([])
    });
    const view = renderPanel(backendApi);

    await view.findByText("OPENCODE_PUBLIC_AGENT_GIT_URL");
    await fireEvent.click(view.getByText("https://example.com/agent.git"));

    expect(view.queryByText("只读参数")).toBeNull();
    expect(view.queryByText(/修改后将影响系统正常运行/)).toBeNull();
    view.queryClient.clear();
  });

  it("shows the current deployment mode hint for public agent git parameter", async () => {
    const getRepositoryDeploymentOptions = vi.fn(async () => ({
      defaultDeploymentMode: "INTERNAL",
      internalSshPrefix: "ssh://AUTH_1@",
      options: [
        { mode: "EXTERNAL", label: "外部部署" },
        { mode: "INTERNAL", label: "内部部署" }
      ]
    } satisfies RepositoryDeploymentOptions));
    const listGeneralParameters = vi.fn(async () => ({
      items: [editableParameter],
      page: 1,
      size: 50,
      total: 1
    } satisfies PageResponse<GeneralParameter>));
    const backendApi = backendApiWith({
      getRepositoryDeploymentOptions,
      listGeneralParameters
    });
    const view = renderPanel(backendApi);

    expect(await view.findByText(/公共 Git 当前为内部部署/)).toBeTruthy();
    expect((await view.findAllByText(/host\[:port\]\/path/)).length).toBeGreaterThan(0);
    expect((await view.findAllByText(/ssh:\/\/AUTH_1@/)).length).toBeGreaterThan(0);
    await fireEvent.click(view.getByText("https://example.com/agent.git"));

    expect((await view.findAllByText(/后端会按当前用户拼接 ssh:\/\/AUTH_1@/)).length).toBeGreaterThan(0);
    expect(getRepositoryDeploymentOptions).toHaveBeenCalled();
    view.queryClient.clear();
  });

  it("allows selecting internal mode while editing public agent git parameter", async () => {
    const listGeneralParameters = vi.fn(async () => ({
      items: [editableParameter],
      page: 1,
      size: 50,
      total: 1
    } satisfies PageResponse<GeneralParameter>));
    const backendApi = backendApiWith({
      listGeneralParameters
    });
    const view = renderPanel(backendApi);

    await view.findByText("OPENCODE_PUBLIC_AGENT_GIT_URL");
    await fireEvent.click(view.getByText("https://example.com/agent.git"));
    await fireEvent.update(view.getByRole("combobox", { name: "公共 Git 部署模式" }), "INTERNAL");

    expect((await view.findAllByText("ssh://AUTH_1@")).length).toBeGreaterThan(0);
    expect(await view.findByText(/已选择内部部署/)).toBeTruthy();
    expect((await view.findAllByText(/host\[:port\]\/path/)).length).toBeGreaterThan(0);
    view.queryClient.clear();
  });
});
