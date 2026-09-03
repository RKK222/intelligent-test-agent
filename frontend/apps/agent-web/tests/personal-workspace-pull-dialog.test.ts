import { mount } from "@vue/test-utils";
import { defineComponent } from "vue";
import { describe, expect, it } from "vitest";
import PersonalWorkspacePullDialog from "../src/components/PersonalWorkspacePullDialog.vue";

const ElDialogStub = defineComponent({
  name: "ElDialog",
  props: { modelValue: Boolean },
  template: `
    <section v-if="modelValue" role="dialog">
      <header><slot name="header" /></header>
      <main><slot /></main>
      <footer><slot name="footer" /></footer>
    </section>
  `
});

const ElCheckboxStub = defineComponent({
  name: "ElCheckbox",
  props: { modelValue: Boolean },
  emits: ["update:modelValue"],
  template: `
    <label>
      <input
        type="checkbox"
        :checked="modelValue"
        @change="$emit('update:modelValue', $event.target.checked)"
      />
      <slot />
    </label>
  `
});

function mountDialog(props: Record<string, unknown>) {
  return mount(PersonalWorkspacePullDialog, {
    props: {
      open: true,
      phase: "CONFIRM",
      appName: "结算测试应用",
      branch: "feature_testagent_20260727",
      ...props
    },
    global: {
      stubs: {
        ElDialog: ElDialogStub,
        ElCheckbox: ElCheckboxStub
      }
    }
  });
}

describe("PersonalWorkspacePullDialog", () => {
  it("confirms that Application Agent is included and Git merge runs directly", async () => {
    const wrapper = mountDialog({});

    expect(wrapper.text()).toContain("应用 Agent 也会一起更新");
    expect(wrapper.text()).toContain("系统会直接执行 Git merge");
    expect(wrapper.text()).toContain("不冲突的本地改动会保留");

    await wrapper.get('input[type="checkbox"]').setValue(true);
    await wrapper.get("button:last-child").trigger("click");

    expect(wrapper.emitted("confirm")).toEqual([[true]]);
  });

  it("confirms force pull only for Git-listed conflicting files", async () => {
    const wrapper = mountDialog({
      phase: "FORCE_CONFIRM",
      conflictingFiles: ["F-GCMS/workspace/.opencode/opencode.jsonc"]
    });

    expect(wrapper.text()).toContain("这些文件的本地修改将被放弃");
    expect(wrapper.text()).toContain(".opencode/opencode.jsonc");
    expect(wrapper.text()).toContain("其它本地文件不会被回退、暂存或提交");

    await wrapper.get("button:last-child").trigger("click");

    expect(wrapper.emitted("confirmForce")).toEqual([[]]);
  });

  it("shows the fetch to merge execution order while the request is running", () => {
    const wrapper = mountDialog({ phase: "PULLING" });

    expect(wrapper.text()).toContain("fetch → 比较提交 → merge");
    expect(wrapper.text()).toContain("获取并比较远程提交");
    expect(wrapper.text()).toContain("自动 merge 到个人 worktree");
    expect(wrapper.text()).toContain("拉取和 merge 完成前不能关闭");
  });

  it("lists changed files and the actual delayed dispose result", () => {
    const wrapper = mountDialog({
      phase: "SUCCEEDED",
      result: {
        personalWorkspaceId: "pws_current",
        versionId: "awv_current",
        remoteBranch: "feature_testagent_20260727",
        commitHash: "ad170bfc12345678",
        updated: true,
        agentConfigChanged: true,
        runtimeReloadStatus: "SCHEDULED",
        runtimeReloadId: "acr_personal_reload",
        changedFiles: ["F-GCMS/workspace/src/Main.java", ".opencode/agents/reviewer.md"],
        disposeStatus: "WAITING_IDLE",
        disposeMessage: "应用 Agent 已更新；后台已登记当前用户，Session 空闲后会自动 dispose。"
      }
    });

    expect(wrapper.get('[data-testid="personal-pull-result"]').text()).toContain("已更新 2 个文件");
    expect(wrapper.text()).toContain("F-GCMS/workspace/src/Main.java");
    expect(wrapper.text()).toContain(".opencode/agents/reviewer.md");
    expect(wrapper.text()).toContain("后台已登记当前用户");
    expect(wrapper.text()).toContain("等待空闲");
  });

  it("explains when no merge and no dispose are required", () => {
    const wrapper = mountDialog({
      phase: "SUCCEEDED",
      result: {
        personalWorkspaceId: "pws_current",
        versionId: "awv_current",
        remoteBranch: "feature_testagent_20260727",
        commitHash: "ad170bfc12345678",
        updated: false,
        agentConfigChanged: false,
        changedFiles: [],
        disposeStatus: "NOT_REQUIRED",
        disposeMessage: "本次没有更新应用 Agent 文件，无需 dispose。"
      }
    });

    expect(wrapper.text()).toContain("当前已是远程最新版本");
    expect(wrapper.text()).toContain("无需合并");
    expect(wrapper.text()).toContain("无需 dispose");
    expect(wrapper.text()).toContain("没有文件需要更新");
  });

  it("shows the backend trace id for an actionable pull failure", () => {
    const wrapper = mountDialog({
      phase: "FAILED",
      errorTitle: "拉取远程失败",
      errorDescription: "GIT_UNAVAILABLE: Git 仓库不可访问；请检查仓库权限和网络连通性。",
      errorTraceId: "trace_pull_network_failure"
    });

    expect(wrapper.text()).toContain("GIT_UNAVAILABLE");
    expect(wrapper.text()).toContain("请检查仓库权限和网络连通性");
    expect(wrapper.text()).toContain("traceId: trace_pull_network_failure");
  });
});
