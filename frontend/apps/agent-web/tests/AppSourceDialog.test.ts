import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import type {
  AppSourceOperation,
  AppSourceRepositorySummary,
  AppSourceTreeSnapshot
} from "@test-agent/shared-types";
import AppSourceDialog from "../src/components/AppSourceDialog.vue";

const repository: AppSourceRepositorySummary = {
  repositoryId: "repo-code",
  name: "应用代码库",
  englishName: "application-code",
  downloadState: "DOWNLOADED_ACTIVE",
  generation: 5,
  purpose: "TEAM",
  branch: "main",
  targetCommit: "stale-summary-commit",
  selectedPaths: [{ path: "src", type: "DIRECTORY" }],
  acceptedAt: "2026-07-30T00:00:00Z",
  expiresAt: "2026-08-01T00:00:00Z",
  maxRetentionHours: 168,
  occupied: true,
  openable: true,
  manageable: true,
  latestOperation: null,
  serverSummaries: []
};

const treeSnapshot: AppSourceTreeSnapshot = {
  targetCommit: "tree-snapshot-commit",
  nodes: [
    { name: "src", path: "src", type: "directory", children: [] },
    { name: "README.md", path: "README.md", type: "file", children: [] }
  ]
};

function operation(status: AppSourceOperation["status"]): AppSourceOperation {
  return {
    operationId: "aso-1",
    appId: "app-demo",
    repositoryId: "repo-code",
    sourceGeneration: 5,
    targetGeneration: 6,
    operationType: "UPDATE",
    status,
    purpose: "PERSONAL",
    branch: "release",
    targetCommit: "tree-snapshot-commit",
    selectedPaths: [{ path: "src", type: "DIRECTORY" }],
    expiresAt: "2026-07-31T00:00:00Z",
    traceId: "trace-source-1",
    acceptedAt: "2026-07-28T00:00:00Z",
    completedAt: status === "RUNNING" ? null : "2026-07-28T00:01:00Z",
    globalSteps: [{
      stepCode: "VALIDATE_SELECTION",
      sequence: 1,
      status: status === "RUNNING" ? "SUCCEEDED" : "FAILED",
      safeSummary: "校验精确选区",
      elapsedMillis: 80,
      updatedAt: "2026-07-28T00:00:05Z"
    }],
    serverSummaries: [{
      linuxServerId: "linux-a",
      replicaStatus: status === "RUNNING" ? "RUNNING" : "FAILED",
      attemptCount: 2,
      safeErrorCode: status === "PARTIAL_FAILED" ? "REPLICA_FAILED" : null,
      safeErrorMessage: status === "PARTIAL_FAILED" ? "副本写入失败" : null,
      targetCommit: "tree-snapshot-commit",
      steps: [{
        stepCode: "CHECKOUT",
        sequence: 2,
        status: status === "RUNNING" ? "RUNNING" : "FAILED",
        safeSummary: "检出固定提交",
        elapsedMillis: 1250,
        updatedAt: "2026-07-28T00:00:10Z"
      }]
    }]
  };
}

describe("AppSourceDialog", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("renders a searchable virtualized selector for a large branch list", async () => {
    const branches = ["main", ...Array.from({ length: 500 }, (_, index) => `feature/source-${index}`)];
    const wrapper = mount(AppSourceDialog, {
      props: { open: true, repositories: [repository], repository, branches },
      global: { stubs: { Teleport: true } }
    });

    await wrapper.get('button[aria-label="下一步：选择分支与目录"]').trigger("click");
    const selector = wrapper.findComponent({ name: "ElSelectV2" });

    expect(selector.exists()).toBe(true);
    expect(selector.props("filterable")).toBe(true);
    expect(selector.props("defaultFirstOption")).toBe(true);
    expect(selector.props("popperStyle")).toEqual({ zIndex: 3701 });
    expect(selector.props("options")).toEqual(branches.map((item) => ({ label: item, value: item })));
  });

  it("updates the active generation retention directly from step one", async () => {
    // 固定在快照未过期的时间点，避免写死的 2026-08-01 到期日随真实日期推进后让用例漂移。
    vi.spyOn(Date, "now").mockReturnValue(Date.parse("2026-07-30T12:00:00Z"));
    const wrapper = mount(AppSourceDialog, {
      props: { open: true, repositories: [repository], repository },
      global: { stubs: { Teleport: true } }
    });

    const input = wrapper.get('input[aria-label="当前源码总保留小时数"]');
    expect((input.element as HTMLInputElement).value).toBe("48");
    expect(input.attributes("max")).toBe("168");
    await input.setValue(120);
    await wrapper.get('button[aria-label="更新当前源码保留期"]').trigger("click");

    expect(wrapper.emitted("update-retention")).toEqual([[120]]);
  });

  it("keeps large descendants collapsed and submits one directory instead of every child", async () => {
    const largeTree: AppSourceTreeSnapshot = {
      targetCommit: "large-tree-commit",
      nodes: [{
        name: "src",
        path: "src",
        type: "directory",
        children: Array.from({ length: 1_000 }, (_, index) => ({
          name: `File${index}.java`,
          path: `src/File${index}.java`,
          type: "file" as const,
          children: []
        }))
      }]
    };
    const wrapper = mount(AppSourceDialog, {
      props: {
        open: true,
        repositories: [{ ...repository, selectedPaths: [] }],
        repository: { ...repository, selectedPaths: [] },
        branches: ["main"],
        treeSnapshot: largeTree,
        treeBranch: "main"
      },
      global: { stubs: { Teleport: true } }
    });

    await wrapper.get('button[aria-label="下一步：选择分支与目录"]').trigger("click");
    expect(wrapper.findAll('input[aria-label^="选择路径 "]')).toHaveLength(1);

    await wrapper.get('input[aria-label="选择路径 src"]').setValue(true);
    expect(wrapper.text()).toContain("整目录");
    expect(wrapper.findAll('input[aria-label^="选择路径 "]')).toHaveLength(1);

    await wrapper.get('button[aria-label="下一步：用途与保留时间"]').trigger("click");
    await wrapper.get('input[aria-label="确认覆盖当前源码"]').setValue(true);
    await wrapper.get('button[aria-label="提交源码物化"]').trigger("click");
    expect(wrapper.emitted("materialize")?.[0]?.[0]).toMatchObject({
      selectedPaths: [{ path: "src", type: "DIRECTORY" }]
    });
  });

  it("selects the first asynchronously loaded branch for a first download", async () => {
    const wrapper = mount(AppSourceDialog, {
      props: {
        open: true,
        repository: { ...repository, generation: null, branch: null, selectedPaths: [] },
        branches: []
      },
      global: { stubs: { Teleport: true } }
    });

    await wrapper.get('button[aria-label="下一步：选择分支与目录"]').trigger("click");
    await wrapper.setProps({ branches: ["main", "release"] });

    expect(wrapper.findComponent({ name: "ElSelectV2" }).props("modelValue")).toBe("main");
  });

  it("submits a PERSONAL to TEAM update with the branch-bound tree commit and 48-hour default", async () => {
    const wrapper = mount(AppSourceDialog, {
      props: {
        open: true,
        repositories: [{ ...repository, purpose: "PERSONAL" }],
        repository: { ...repository, purpose: "PERSONAL" },
        branches: ["main", "release"],
        treeSnapshot,
        treeBranch: "main"
      },
      global: { stubs: { Teleport: true } }
    });

    await wrapper.get('button[aria-label="下一步：选择分支与目录"]').trigger("click");
    const indexedSelection = wrapper.get('input[aria-label="选择路径 src"]');
    expect(indexedSelection.element).toHaveProperty("checked", true);
    expect(indexedSelection.element.closest("label")?.classList).toContain("is-indexed-selection");
    const branchSelector = wrapper.findComponent({ name: "ElSelectV2" });
    branchSelector.vm.$emit("update:modelValue", "release");
    branchSelector.vm.$emit("change", "release");
    await wrapper.vm.$nextTick();
    expect(wrapper.emitted("load-tree")?.at(-1)).toEqual(["release", ""]);
    await wrapper.setProps({ treeSnapshot, treeBranch: "release" });
    await wrapper.get('button[aria-label="下一步：用途与保留时间"]').trigger("click");
    await wrapper.get('input[aria-label="团队源码"]').setValue(true);
    await wrapper.get('input[aria-label="确认覆盖当前源码"]').setValue(true);
    await wrapper.get('button[aria-label="提交源码物化"]').trigger("click");

    expect(wrapper.emitted("materialize")).toEqual([[
      {
        expectedGeneration: 5,
        branch: "release",
        expectedTreeCommit: "tree-snapshot-commit",
        selectedPaths: [{ path: "src", type: "DIRECTORY" }],
        purpose: "TEAM",
        retentionHours: 48,
        confirmReplace: true
      }
    ]]);
  });

  it("prevents an existing TEAM generation from being downgraded to PERSONAL", async () => {
    const wrapper = mount(AppSourceDialog, {
      props: {
        open: true,
        repositories: [repository],
        repository,
        branches: ["main"],
        treeSnapshot,
        treeBranch: "main"
      },
      global: { stubs: { Teleport: true } }
    });

    await wrapper.get('button[aria-label="下一步：选择分支与目录"]').trigger("click");
    await wrapper.get('button[aria-label="下一步：用途与保留时间"]').trigger("click");

    const personal = wrapper.get('input[aria-label="个人源码"]');
    expect(personal.attributes()).toHaveProperty("disabled");
    expect(wrapper.text()).toContain("团队源码不能降级为个人源码");
    await personal.setValue(true);
    await wrapper.get('input[aria-label="确认覆盖当前源码"]').setValue(true);
    await wrapper.get('button[aria-label="提交源码物化"]').trigger("click");
    expect(wrapper.emitted("materialize")?.[0]?.[0]).toMatchObject({ purpose: "TEAM" });
  });

  it("lists every associated repository in step one and waits for an explicit manageable selection", async () => {
    const occupied = {
      ...repository,
      repositoryId: "repo-occupied",
      name: "个人占用库",
      downloadState: "PERSONAL_OCCUPIED" as const,
      manageable: false,
      openable: false,
      ownerName: "李四",
      ownerUnifiedAuthId: "10000001",
      unavailableReason: "由其他用户个人占用"
    };
    const notDownloaded = {
      ...repository,
      repositoryId: "repo-new",
      name: "待下载库",
      downloadState: "NOT_DOWNLOADED" as const,
      generation: null,
      purpose: null,
      branch: null,
      selectedPaths: [],
      expiresAt: null
    };
    const wrapper = mount(AppSourceDialog, {
      props: { open: true, repositories: [occupied, notDownloaded], repository: null },
      global: { stubs: { Teleport: true } }
    });

    expect(wrapper.text()).toContain("个人占用库");
    expect(wrapper.text()).toContain("李四 · 10000001");
    expect(wrapper.text()).toContain("由其他用户个人占用");
    expect(wrapper.text()).toContain("待下载库");
    expect(wrapper.get('button[aria-label="下一步：选择分支与目录"]').attributes()).toHaveProperty("disabled");
    expect(wrapper.get('button[aria-label="选择个人占用库版本库"]').attributes()).toHaveProperty("disabled");

    await wrapper.get('button[aria-label="选择待下载库版本库"]').trigger("click");
    expect(wrapper.emitted("select-repository")).toEqual([[notDownloaded]]);
  });

  it("keeps terminal history inspectable without locking configuration", async () => {
    const succeeded = operation("SUCCEEDED");
    const refreshedRepository = { ...repository, generation: 6, latestOperation: succeeded };
    const wrapper = mount(AppSourceDialog, {
      props: {
        open: true,
        repositories: [refreshedRepository],
        repository: { ...repository, latestOperation: succeeded },
        operation: succeeded
      },
      global: { stubs: { Teleport: true } }
    });

    expect(wrapper.get("h3").text()).toBe("版本库状态");
    await wrapper.get('button[aria-label="查看上次源码进度"]').trigger("click");
    expect(wrapper.text()).toContain("trace-source-1");
    await wrapper.get('button[aria-label="重新下载或更新配置"]').trigger("click");
    expect(wrapper.get("h3").text()).toBe("版本库状态");
    expect(wrapper.emitted("select-repository")?.at(-1)).toEqual([refreshedRepository]);

    await wrapper.setProps({ operation: { ...succeeded, operationId: "aso-running", status: "RUNNING", completedAt: null } });
    expect(wrapper.text()).toContain("后台任务执行中");
  });

  it("shows low-sensitivity errors in the step where they occur", async () => {
    const wrapper = mount(AppSourceDialog, {
      props: {
        open: true,
        repositories: [repository],
        repository,
        branches: ["main"],
        treeSnapshot,
        treeBranch: "main",
        branchesError: "CONFLICT: 分支暂时不可用（traceId: trace-branch）",
        treeError: "NOT_FOUND: 目录不存在（traceId: trace-tree）",
        materializationError: "VALIDATION_ERROR: exact set 无效（traceId: trace-submit）",
        progressError: "连接中断（traceId: trace-ws）"
      },
      global: { stubs: { Teleport: true } }
    });

    await wrapper.get('button[aria-label="下一步：选择分支与目录"]').trigger("click");
    expect(wrapper.text()).toContain("trace-branch");
    expect(wrapper.text()).toContain("trace-tree");
    expect(wrapper.text()).not.toContain("trace-submit");
    await wrapper.get('button[aria-label="重试加载源码目录"]').trigger("click");
    expect(wrapper.emitted("load-tree")?.at(-1)).toEqual(["main", ""]);
    await wrapper.setProps({ branchesError: null, treeError: null });
    await wrapper.get('button[aria-label="下一步：用途与保留时间"]').trigger("click");
    expect(wrapper.text()).toContain("trace-submit");
    expect(wrapper.text()).not.toContain("trace-tree");
    await wrapper.setProps({ operation: operation("RUNNING") });
    expect(wrapper.text()).toContain("trace-ws");
  });

  it("blocks a stale branch commit and exposes invisible historical paths for removal", async () => {
    const wrapper = mount(AppSourceDialog, {
      props: {
        open: true,
        repositories: [repository],
        repository,
        branches: ["main", "release"],
        treeSnapshot,
        treeBranch: "main"
      },
      global: { stubs: { Teleport: true } }
    });

    await wrapper.get('button[aria-label="下一步：选择分支与目录"]').trigger("click");
    const branchSelector = wrapper.findComponent({ name: "ElSelectV2" });
    branchSelector.vm.$emit("update:modelValue", "release");
    branchSelector.vm.$emit("change", "release");
    await wrapper.vm.$nextTick();
    expect(wrapper.get('button[aria-label="下一步：用途与保留时间"]').attributes()).toHaveProperty("disabled");
    expect(wrapper.text()).toContain("等待 release 分支的固定提交目录树");

    await wrapper.setProps({
      treeBranch: "release",
      treeSnapshot: {
        targetCommit: "release-commit",
        nodes: [{ name: "README.md", path: "README.md", type: "file", children: [] }]
      }
    });
    expect(wrapper.text()).toContain("当前分支中不可见或已失效的历史路径");
    expect(wrapper.text()).toContain("src");
    await wrapper.get('button[aria-label="移除失效路径 src"]').trigger("click");
    await wrapper.get('input[aria-label="选择路径 README.md"]').setValue(true);
    expect(wrapper.get('button[aria-label="下一步：用途与保留时间"]').attributes()).not.toHaveProperty("disabled");
  });

  it("shows a resumable server timeline without cancellation and retries partial replicas", async () => {
    const partial = operation("PARTIAL_FAILED");
    const wrapper = mount(AppSourceDialog, {
      props: { open: true, repository: { ...repository, latestOperation: partial }, operation: partial },
      global: { stubs: { Teleport: true } }
    });

    await wrapper.get('button[aria-label="查看上次源码进度"]').trigger("click");
    expect(wrapper.text()).toContain("linux-a");
    expect(wrapper.text()).toContain("校验精确选区");
    expect(wrapper.text()).toContain("检出固定提交");
    expect(wrapper.text()).toContain("1.25 秒");
    expect(wrapper.text()).toContain("tree-snapshot-commit");
    expect(wrapper.text()).toContain("trace-source-1");
    expect(wrapper.find('button[aria-label*="取消"]').exists()).toBe(false);

    await wrapper.get('button[aria-label="重试失败或缺失副本"]').trigger("click");
    expect(wrapper.emitted("retry")).toEqual([[partial]]);
    await wrapper.get('button[aria-label="关闭源码弹窗"]').trigger("click");
    expect(wrapper.emitted("close")).toHaveLength(1);
    expect(wrapper.emitted("cancel")).toBeUndefined();
  });
});
