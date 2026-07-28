import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
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
  expiresAt: "2026-07-30T00:00:00Z",
  occupied: false,
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

    expect(wrapper.get('select[aria-label="源码分支"]').element).toHaveProperty("value", "main");
  });

  it("submits an exact selection with the tree snapshot commit and replacement confirmation", async () => {
    const wrapper = mount(AppSourceDialog, {
      props: {
        open: true,
        repository,
        branches: ["main", "release"],
        treeSnapshot
      },
      global: { stubs: { Teleport: true } }
    });

    await wrapper.get('button[aria-label="下一步：选择分支与目录"]').trigger("click");
    const indexedSelection = wrapper.get('input[aria-label="选择路径 src"]');
    expect(indexedSelection.element).toHaveProperty("checked", true);
    expect(indexedSelection.element.closest("label")?.classList).toContain("is-indexed-selection");
    await wrapper.get('select[aria-label="源码分支"]').setValue("release");
    expect(wrapper.emitted("load-tree")?.at(-1)).toEqual(["release", ""]);
    await wrapper.get('button[aria-label="下一步：用途与保留时间"]').trigger("click");
    await wrapper.get('input[aria-label="个人源码"]').setValue(true);
    await wrapper.get('input[aria-label="保留小时数"]').setValue(72);
    await wrapper.get('input[aria-label="确认覆盖当前源码"]').setValue(true);
    await wrapper.get('button[aria-label="提交源码物化"]').trigger("click");

    expect(wrapper.emitted("materialize")).toEqual([[
      {
        expectedGeneration: 5,
        branch: "release",
        expectedTreeCommit: "tree-snapshot-commit",
        selectedPaths: [{ path: "src", type: "DIRECTORY" }],
        purpose: "PERSONAL",
        retentionHours: 72,
        confirmReplace: true
      }
    ]]);
  });

  it("shows a resumable server timeline without cancellation and retries partial replicas", async () => {
    const partial = operation("PARTIAL_FAILED");
    const wrapper = mount(AppSourceDialog, {
      props: { open: true, repository: { ...repository, latestOperation: partial }, operation: partial },
      global: { stubs: { Teleport: true } }
    });

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
