import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import { BackendApiError, type ReferenceRepositoryStatus } from "@test-agent/backend-api";
import ReferenceConfigurationDialog from "../src/components/ReferenceConfigurationDialog.vue";
import referenceConfigurationDialogSource from "../src/components/ReferenceConfigurationDialog.vue?raw";

function status(overrides: Partial<ReferenceRepositoryStatus> = {}): ReferenceRepositoryStatus {
  return {
    repositoryId: "repo-assets",
    name: "需求资产库",
    englishName: "requirements",
    gitUrl: "ssh://git.example.test/requirements.git",
    initialized: true,
    branch: "main",
    targetCommitHash: "abc123",
    generation: 1,
    status: "READY",
    targetServerCount: 1,
    readyServerCount: 1,
    servers: [{ linuxServerId: "linux-a", status: "READY", currentBranch: "main", currentCommitHash: "abc123" }],
    traceId: "trace_status",
    message: null,
    ...overrides
  };
}

function api(overrides: Record<string, unknown> = {}) {
  return {
    listReferenceRepositories: vi.fn().mockResolvedValue([status()]),
    listRepositoryBranches: vi.fn().mockResolvedValue(["main", "release"]),
    initializeReferenceRepository: vi.fn().mockResolvedValue(status({ status: "INITIALIZING", initialized: true, readyServerCount: 0 })),
    synchronizeReferenceRepository: vi.fn().mockResolvedValue(status({ status: "SYNCHRONIZING", readyServerCount: 0 })),
    getReferenceRepositoryStatus: vi.fn().mockResolvedValue(status()),
    listReferenceRepositoryTree: vi.fn().mockResolvedValue([]),
    readFile: vi.fn().mockRejectedValue(new BackendApiError(500, {
      success: false,
      code: "FILE_NOT_FOUND",
      message: "文件不存在",
      traceId: "trace_missing"
    })),
    writeFile: vi.fn().mockResolvedValue(undefined),
    ...overrides
  };
}

const mountedWrappers: Array<ReturnType<typeof mount>> = [];

function render(mockApi: ReturnType<typeof api>) {
  const wrapper = mount(ReferenceConfigurationDialog, {
    attachTo: document.body,
    props: { open: true, appId: "app-demo", workspaceId: "wrk-personal" },
    global: {
      provide: { api: mockApi },
      stubs: { Teleport: true }
    }
  });
  mountedWrappers.push(wrapper);
  return wrapper;
}

function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

async function selectReadyFolder(wrapper: ReturnType<typeof render>) {
  await wrapper.get('button[aria-label="选择需求资产库"]').trigger("click");
  await flushPromises();
  await wrapper.get('button[data-reference-selectable="true"]').trigger("click");
  await flushPromises();
}

describe("ReferenceConfigurationDialog", () => {
  afterEach(() => {
    vi.useRealTimers();
    for (const wrapper of mountedWrappers.splice(0)) wrapper.unmount();
    document.body.innerHTML = "";
  });

  it("lists the current application's asset repositories with Chinese/English names and Git URL", async () => {
    const mockApi = api();
    const wrapper = render(mockApi);
    await flushPromises();

    expect(mockApi.listReferenceRepositories).toHaveBeenCalledWith("app-demo");
    expect(wrapper.get('[role="dialog"]').attributes("aria-modal")).toBe("true");
    expect(wrapper.text()).toContain("需求资产库（requirements）");
    expect(wrapper.text()).toContain("ssh://git.example.test/requirements.git");
  });

  it("retries the initial repository list request after showing its traceId", async () => {
    const listRepositories = vi.fn()
      .mockRejectedValueOnce(new BackendApiError(503, {
        success: false,
        code: "REFERENCE_LIST_FAILED",
        message: "资产库列表暂时不可用",
        traceId: "trace_list_retry"
      }))
      .mockResolvedValueOnce([status()]);
    const mockApi = api({ listReferenceRepositories: listRepositories });
    const wrapper = render(mockApi);
    await flushPromises();

    expect(wrapper.text()).toContain("资产库列表暂时不可用");
    expect(wrapper.text()).toContain("trace_list_retry");
    await wrapper.get('button[aria-label="重试加载应用资产库"]').trigger("click");
    await flushPromises();

    expect(listRepositories).toHaveBeenCalledTimes(2);
    expect(listRepositories).toHaveBeenNthCalledWith(2, "app-demo");
    expect(wrapper.text()).toContain("需求资产库（requirements）");
    expect(wrapper.text()).not.toContain("资产库列表暂时不可用");
  });

  it("initializes from the existing branch API and keeps polling through VERIFYING until READY", async () => {
    vi.useFakeTimers();
    const uninitialized = status({ initialized: false, branch: null, status: "UNINITIALIZED", generation: 0, readyServerCount: 0, servers: [] });
    const mockApi = api({
      listReferenceRepositories: vi.fn().mockResolvedValue([uninitialized]),
      getReferenceRepositoryStatus: vi.fn()
        .mockResolvedValueOnce(status({ status: "VERIFYING", readyServerCount: 0 }))
        .mockResolvedValueOnce(status({
          servers: [{ linuxServerId: "linux-a", status: "READY", currentBranch: "main", currentCommitHash: "abc123" }]
        }))
    });
    const wrapper = render(mockApi);
    await flushPromises();

    await wrapper.get('button[aria-label="初始化需求资产库"]').trigger("click");
    await flushPromises();
    expect(mockApi.listRepositoryBranches).toHaveBeenCalledWith("repo-assets");
    expect(wrapper.text()).toContain("选择初始化分支");

    await wrapper.get('button[aria-label="确认初始化需求资产库"]').trigger("click");
    await flushPromises();
    expect(mockApi.initializeReferenceRepository).toHaveBeenCalledWith("app-demo", "repo-assets", "main");

    await vi.advanceTimersByTimeAsync(2000);
    await flushPromises();
    expect(mockApi.getReferenceRepositoryStatus).toHaveBeenCalledTimes(1);
    expect(wrapper.text()).toContain("VERIFYING");

    await vi.advanceTimersByTimeAsync(2000);
    await flushPromises();
    expect(mockApi.getReferenceRepositoryStatus).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain("linux-a");
    expect(wrapper.text()).toContain("READY");

    await vi.advanceTimersByTimeAsync(4000);
    expect(mockApi.getReferenceRepositoryStatus).toHaveBeenCalledTimes(2);
  });

  it("clears a transient polling error after the next successful status response", async () => {
    vi.useFakeTimers();
    const mockApi = api({
      getReferenceRepositoryStatus: vi.fn()
        .mockRejectedValueOnce(new BackendApiError(503, {
          success: false,
          code: "REFERENCE_STATUS_FAILED",
          message: "临时状态错误",
          traceId: "trace_transient"
        }))
        .mockResolvedValueOnce(status({ status: "VERIFYING", readyServerCount: 0 }))
        .mockResolvedValueOnce(status())
    });
    const wrapper = render(mockApi);
    await flushPromises();
    await wrapper.get('button[aria-label="选择需求资产库"]').trigger("click");
    await flushPromises();

    await vi.advanceTimersByTimeAsync(2000);
    await flushPromises();
    expect(wrapper.text()).toContain("临时状态错误");
    expect(wrapper.text()).toContain("trace_transient");

    await vi.advanceTimersByTimeAsync(2000);
    await flushPromises();
    expect(wrapper.text()).not.toContain("临时状态错误");
    expect(wrapper.text()).not.toContain("trace_transient");
  });

  it("synchronizes an initialized repository, exposes only orange selectable root folders, and saves through workspace RPC", async () => {
    vi.useFakeTimers();
    const mockApi = api({
      listReferenceRepositoryTree: vi.fn().mockImplementation((_appId: string, _repositoryId: string, path: string) =>
        Promise.resolve(path === ""
          ? [
              { path: "docs", name: "docs", directory: true, size: 0, highlighted: true, selectable: true },
              { path: "nested", name: "nested", directory: true, size: 0, highlighted: false, selectable: false },
              { path: "README.md", name: "README.md", directory: false, size: 10, highlighted: false, selectable: false }
            ]
          : [{ path: "docs/docs", name: "docs", directory: true, size: 0, highlighted: false, selectable: false }])
      )
    });
    const wrapper = render(mockApi);
    await flushPromises();

    await wrapper.get('button[aria-label="选择需求资产库"]').trigger("click");
    await flushPromises();
    expect(mockApi.synchronizeReferenceRepository).toHaveBeenCalledWith("app-demo", "repo-assets");

    await vi.advanceTimersByTimeAsync(2000);
    await flushPromises();
    expect(mockApi.listReferenceRepositoryTree).toHaveBeenCalledWith("app-demo", "repo-assets", "");
    expect(wrapper.findAll(".is-reference-selectable")).toHaveLength(1);
    expect(wrapper.get(".is-reference-selectable").text()).toContain("docs");

    await wrapper.get('button[aria-label="展开 docs"]').trigger("click");
    await flushPromises();
    expect(mockApi.listReferenceRepositoryTree).toHaveBeenCalledWith("app-demo", "repo-assets", "docs");
    expect(wrapper.findAll(".is-reference-selectable")).toHaveLength(1);

    await wrapper.get('button[data-reference-selectable="true"]').trigger("click");
    await flushPromises();
    expect(wrapper.get('input[aria-label="参考别名（alias）"]').element).toHaveProperty("value", "docs-requirements");
    expect(wrapper.get('input[aria-label="路径（path）"]').element).toHaveProperty(
      "value",
      "{env:OPENCODE_REFERENCES_DIR}/requirements/docs"
    );
    expect(wrapper.get('button[aria-label="保存引用配置"]').attributes()).toHaveProperty("disabled");

    await wrapper.get('textarea[aria-label="描述（description）"]').setValue("  产品需求与接口约束  ");
    expect(wrapper.get('button[aria-label="保存引用配置"]').attributes()).not.toHaveProperty("disabled");
    await wrapper.get('button[aria-label="保存引用配置"]').trigger("click");
    await flushPromises();

    expect(mockApi.readFile).toHaveBeenCalledWith("wrk-personal", ".opencode/opencode.jsonc");
    expect(mockApi.writeFile).toHaveBeenCalledWith(
      "wrk-personal",
      ".opencode/opencode.jsonc",
      expect.stringContaining('"description": "产品需求与接口约束"')
    );
    expect(wrapper.emitted("saved")).toEqual([[]]);
  });

  it("loads an existing local reference, enables Update only after a change, and preserves unknown fields", async () => {
    const existing = `{
  // keep root comment
  "references": {
    "docs-requirements": {
      "path": "{env:OPENCODE_REFERENCES_DIR}/requirements/docs",
      "merge": true,
      "sdd-folder-name": "docs",
      "description": "旧说明",
      "hidden": true,
    },
  },
}`;
    const mockApi = api({
      synchronizeReferenceRepository: vi.fn().mockResolvedValue(status()),
      listReferenceRepositoryTree: vi.fn().mockResolvedValue([
        { path: "docs", name: "docs", directory: true, size: 0, highlighted: true, selectable: true }
      ]),
      readFile: vi.fn().mockResolvedValue({ path: ".opencode/opencode.jsonc", content: existing, size: existing.length })
    });
    const wrapper = render(mockApi);
    await flushPromises();

    await wrapper.get('button[aria-label="选择需求资产库"]').trigger("click");
    await flushPromises();
    await wrapper.get('button[data-reference-selectable="true"]').trigger("click");
    await flushPromises();

    expect(wrapper.get('button[aria-label="更新引用配置"]').text()).toBe("更新");
    expect(wrapper.get('button[aria-label="更新引用配置"]').attributes()).toHaveProperty("disabled");
    expect(wrapper.get('textarea[aria-label="描述（description）"]').element).toHaveProperty("value", "旧说明");

    await wrapper.get('select[aria-label="是否合并（merge）"]').setValue("false");
    expect(wrapper.get('select[aria-label="是否合并（merge）"]').element).toHaveProperty("value", "false");
    expect(wrapper.get('button[aria-label="更新引用配置"]').attributes()).not.toHaveProperty("disabled");
    await wrapper.get('button[aria-label="更新引用配置"]').trigger("click");
    await flushPromises();

    const written = mockApi.writeFile.mock.calls[0]?.[2] as string;
    expect(written).toContain("// keep root comment");
    expect(written).toContain('"hidden": true');
    expect(written).toContain('"merge": false');
  });

  it("writes an immutable submitted snapshot and never marks edits made during the pending write as clean", async () => {
    const pendingWrite = deferred<void>();
    const existing = `{
  "references": {
    "docs-requirements": {
      "path": "{env:OPENCODE_REFERENCES_DIR}/requirements/docs",
      "merge": true,
      "sdd-folder-name": "docs",
      "description": "旧说明"
    }
  }
}`;
    const mockApi = api({
      synchronizeReferenceRepository: vi.fn().mockResolvedValue(status()),
      listReferenceRepositoryTree: vi.fn().mockResolvedValue([
        { path: "docs", name: "docs", directory: true, size: 0, highlighted: true, selectable: true }
      ]),
      readFile: vi.fn().mockResolvedValue({ path: ".opencode/opencode.jsonc", content: existing, size: existing.length }),
      writeFile: vi.fn().mockReturnValue(pendingWrite.promise)
    });
    const wrapper = render(mockApi);
    await flushPromises();
    await selectReadyFolder(wrapper);

    await wrapper.get('select[aria-label="是否合并（merge）"]').setValue("false");
    await wrapper.get('textarea[aria-label="描述（description）"]').setValue("  提交时说明  ");
    await wrapper.get('button[aria-label="更新引用配置"]').trigger("click");
    await flushPromises();

    expect(wrapper.get('select[aria-label="是否合并（merge）"]').attributes()).toHaveProperty("disabled");
    expect(wrapper.get('textarea[aria-label="描述（description）"]').attributes()).toHaveProperty("disabled");
    expect(wrapper.get('button[aria-label="选择需求资产库"]').attributes()).toHaveProperty("disabled");
    const written = mockApi.writeFile.mock.calls[0]?.[2] as string;
    expect(written).toContain('"merge": false');
    expect(written).toContain('"description": "提交时说明"');

    // 浏览器会阻止 disabled 控件输入；直接改 setup state 模拟已排队的迟到组件事件，验证 baseline 仍绑定提交快照。
    const internalInstance = wrapper.vm.$ as unknown as { setupState: { form: { description: string } } };
    const setupState = internalInstance.setupState;
    setupState.form.description = "写入等待期间的新说明";
    await wrapper.vm.$nextTick();
    pendingWrite.resolve();
    await flushPromises();

    expect(wrapper.get('textarea[aria-label="描述（description）"]').element).toHaveProperty("value", "写入等待期间的新说明");
    expect(wrapper.get('button[aria-label="更新引用配置"]').attributes()).not.toHaveProperty("disabled");
  });

  it("fences a pending save across close and reopen so the old finally cannot clear the new saving state", async () => {
    const firstWrite = deferred<void>();
    const secondWrite = deferred<void>();
    const existing = `{
  "references": {
    "docs-requirements": {
      "path": "{env:OPENCODE_REFERENCES_DIR}/requirements/docs",
      "merge": true,
      "sdd-folder-name": "docs",
      "description": "旧说明"
    }
  }
}`;
    const mockApi = api({
      synchronizeReferenceRepository: vi.fn().mockResolvedValue(status()),
      listReferenceRepositoryTree: vi.fn().mockResolvedValue([
        { path: "docs", name: "docs", directory: true, size: 0, highlighted: true, selectable: true }
      ]),
      readFile: vi.fn().mockResolvedValue({ path: ".opencode/opencode.jsonc", content: existing, size: existing.length }),
      writeFile: vi.fn()
        .mockReturnValueOnce(firstWrite.promise)
        .mockReturnValueOnce(secondWrite.promise)
    });
    const wrapper = render(mockApi);
    await flushPromises();
    await selectReadyFolder(wrapper);
    await wrapper.get('textarea[aria-label="描述（description）"]').setValue("第一次保存");
    await wrapper.get('button[aria-label="更新引用配置"]').trigger("click");
    await flushPromises();

    await wrapper.setProps({ open: false });
    await wrapper.setProps({ open: true, appId: "app-next", workspaceId: "wrk-next" });
    await flushPromises();
    expect(mockApi.listReferenceRepositories).toHaveBeenLastCalledWith("app-next");
    await selectReadyFolder(wrapper);
    expect(wrapper.get('textarea[aria-label="描述（description）"]').attributes()).not.toHaveProperty("disabled");
    await wrapper.get('textarea[aria-label="描述（description）"]').setValue("第二次保存");
    await wrapper.get('button[aria-label="更新引用配置"]').trigger("click");
    await flushPromises();
    expect(wrapper.get('textarea[aria-label="描述（description）"]').attributes()).toHaveProperty("disabled");

    firstWrite.resolve();
    await flushPromises();
    expect(wrapper.get('textarea[aria-label="描述（description）"]').attributes()).toHaveProperty("disabled");
    expect(wrapper.text()).not.toContain("引用配置已保存");

    secondWrite.resolve();
    await flushPromises();
    expect(wrapper.get('textarea[aria-label="描述（description）"]').attributes()).not.toHaveProperty("disabled");
    expect(wrapper.text()).toContain("引用配置已保存");
  });

  it("discards stale status responses after switching repositories and stops polling when closed", async () => {
    vi.useFakeTimers();
    let resolveFirst!: (value: ReferenceRepositoryStatus) => void;
    const firstStatus = new Promise<ReferenceRepositoryStatus>((resolve) => { resolveFirst = resolve; });
    const repoA = status({ repositoryId: "repo-a", name: "仓库 A", englishName: "repo-a" });
    const repoB = status({ repositoryId: "repo-b", name: "仓库 B", englishName: "repo-b" });
    const mockApi = api({
      listReferenceRepositories: vi.fn().mockResolvedValue([repoA, repoB]),
      synchronizeReferenceRepository: vi.fn().mockImplementation((_appId: string, repositoryId: string) =>
        Promise.resolve(status({ repositoryId, name: repositoryId === "repo-a" ? "仓库 A" : "仓库 B", englishName: repositoryId, status: "SYNCHRONIZING" }))
      ),
      getReferenceRepositoryStatus: vi.fn().mockImplementation((_appId: string, repositoryId: string) =>
        repositoryId === "repo-a" ? firstStatus : Promise.resolve(status({ repositoryId: "repo-b", name: "仓库 B", englishName: "repo-b" }))
      )
    });
    const wrapper = render(mockApi);
    await flushPromises();

    await wrapper.get('button[aria-label="选择仓库 A"]').trigger("click");
    await flushPromises();
    await vi.advanceTimersByTimeAsync(2000);
    await wrapper.get('button[aria-label="选择仓库 B"]').trigger("click");
    await flushPromises();
    resolveFirst(status({ repositoryId: "repo-a", name: "仓库 A", englishName: "repo-a", status: "FAILED", message: "旧错误" }));
    await flushPromises();
    expect(wrapper.text()).not.toContain("旧错误");

    const callsBeforeClose = mockApi.getReferenceRepositoryStatus.mock.calls.length;
    await wrapper.setProps({ open: false });
    await vi.advanceTimersByTimeAsync(6000);
    expect(mockApi.getReferenceRepositoryStatus).toHaveBeenCalledTimes(callsBeforeClose);
  });

  it("shows backend and per-server errors together with traceId", async () => {
    const failed = status({
      status: "FAILED",
      message: "总体同步失败",
      traceId: "trace_repo_failed",
      servers: [{ linuxServerId: "linux-b", status: "FAILED", error: "磁盘空间不足" }]
    });
    const mockApi = api({ listReferenceRepositories: vi.fn().mockResolvedValue([failed]) });
    const wrapper = render(mockApi);
    await flushPromises();

    expect(wrapper.text()).toContain("总体同步失败");
    expect(wrapper.text()).toContain("磁盘空间不足");
    expect(wrapper.text()).toContain("trace_repo_failed");
  });

  it("renders a child tree error with traceId at its directory and retries that level", async () => {
    const childFailure = new BackendApiError(500, {
      success: false,
      code: "REFERENCE_TREE_FAILED",
      message: "子目录读取失败",
      traceId: "trace_child_tree"
    });
    const listTree = vi.fn().mockImplementation((_appId: string, _repositoryId: string, path: string) => {
      if (path === "") {
        return Promise.resolve([
          { path: "docs", name: "docs", directory: true, size: 0, highlighted: true, selectable: true }
        ]);
      }
      if (listTree.mock.calls.filter((call) => call[2] === "docs").length === 1) return Promise.reject(childFailure);
      return Promise.resolve([
        { path: "docs/api", name: "api", directory: true, size: 0, highlighted: false, selectable: false }
      ]);
    });
    const mockApi = api({
      synchronizeReferenceRepository: vi.fn().mockResolvedValue(status()),
      listReferenceRepositoryTree: listTree
    });
    const wrapper = render(mockApi);
    await flushPromises();
    await wrapper.get('button[aria-label="选择需求资产库"]').trigger("click");
    await flushPromises();

    await wrapper.get('button[aria-label="展开 docs"]').trigger("click");
    await flushPromises();
    expect(wrapper.text()).toContain("子目录读取失败");
    expect(wrapper.text()).toContain("trace_child_tree");
    const treeList = wrapper.get('[role="list"]').element;
    expect(Array.from(treeList.children).every((child) => child.getAttribute("role") === "listitem")).toBe(true);

    await wrapper.get('button[aria-label="重试 docs"]').trigger("click");
    await flushPromises();
    expect(listTree.mock.calls.filter((call) => call[2] === "docs")).toHaveLength(2);
    expect(wrapper.text()).not.toContain("子目录读取失败");
    expect(wrapper.text()).toContain("api");
  });

  it("uses the required Chinese field labels, boolean yes/no options, and Chinese save/update button text", async () => {
    const mockApi = api({
      synchronizeReferenceRepository: vi.fn().mockResolvedValue(status()),
      listReferenceRepositoryTree: vi.fn().mockResolvedValue([
        { path: "docs", name: "docs", directory: true, size: 0, highlighted: true, selectable: true }
      ])
    });
    const wrapper = render(mockApi);
    await flushPromises();
    await selectReadyFolder(wrapper);

    expect(wrapper.get('input[aria-label="参考别名（alias）"]').element).toHaveProperty("readOnly", true);
    expect(wrapper.get('input[aria-label="路径（path）"]').element).toHaveProperty("readOnly", true);
    const merge = wrapper.get('select[aria-label="是否合并（merge）"]');
    expect(merge.findAll("option").map((option) => [option.text(), option.attributes("value")])).toEqual([
      ["是", "true"],
      ["否", "false"]
    ]);
    expect(wrapper.get('input[aria-label="规格驱动目录名称（sdd-folder-name）"]').element).toHaveProperty("value", "docs");
    await wrapper.get('textarea[aria-label="描述（description）"]').setValue("需求资料");
    expect(wrapper.get('button[aria-label="保存引用配置"]').text()).toBe("保存");
  });

  it("focuses the modal, traps Tab, handles window Escape, and restores the opener focus", async () => {
    const opener = document.createElement("button");
    opener.textContent = "打开引用配置";
    document.body.append(opener);
    const mockApi = api();
    const wrapper = mount(ReferenceConfigurationDialog, {
      attachTo: document.body,
      props: { open: false, appId: "app-demo", workspaceId: "wrk-personal" },
      global: {
        provide: { api: mockApi },
        stubs: { Teleport: true }
      }
    });
    opener.focus();

    await wrapper.setProps({ open: true });
    await flushPromises();
    const closeButton = wrapper.get('button[aria-label="关闭引用配置"]').element as HTMLButtonElement;
    expect(document.activeElement).toBe(closeButton);

    const dialog = wrapper.get('[role="dialog"]').element;
    const focusable = Array.from(dialog.querySelectorAll<HTMLElement>("button:not([disabled]), select:not([disabled]), textarea:not([disabled]), input:not([disabled])"));
    const first = focusable[0]!;
    const last = focusable.at(-1)!;
    last.focus();
    window.dispatchEvent(new KeyboardEvent("keydown", { key: "Tab", bubbles: true, cancelable: true }));
    expect(document.activeElement).toBe(first);
    first.focus();
    window.dispatchEvent(new KeyboardEvent("keydown", { key: "Tab", shiftKey: true, bubbles: true, cancelable: true }));
    expect(document.activeElement).toBe(last);

    window.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
    expect(wrapper.emitted("close")).toHaveLength(1);
    await wrapper.setProps({ open: false });
    await flushPromises();
    expect(document.activeElement).toBe(opener);
    wrapper.unmount();
  });

  it("uses list semantics for button-driven rows and allows the overlay to scroll on short viewports", async () => {
    const mockApi = api({
      synchronizeReferenceRepository: vi.fn().mockResolvedValue(status()),
      listReferenceRepositoryTree: vi.fn().mockResolvedValue([
        { path: "docs", name: "docs", directory: true, size: 0, highlighted: true, selectable: true }
      ])
    });
    const wrapper = render(mockApi);
    await flushPromises();
    await wrapper.get('button[aria-label="选择需求资产库"]').trigger("click");
    await flushPromises();

    expect(wrapper.find('[role="tree"]').exists()).toBe(false);
    expect(wrapper.get('[role="list"]')).toBeTruthy();
    expect(wrapper.findAll('[role="listitem"]')).toHaveLength(1);
    expect(referenceConfigurationDialogSource).toContain("overflow-y: auto");
    expect(referenceConfigurationDialogSource).toContain("min-height: min(520px, calc(100vh - 32px))");
    expect(referenceConfigurationDialogSource).toContain("@media (max-height: 560px)");
  });
});
