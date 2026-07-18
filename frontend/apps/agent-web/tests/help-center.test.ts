import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import HelpCenterDialog from "../src/components/HelpCenterDialog.vue";
import {
  buildManualQuestionPrompt,
  helpTopicById,
  helpDocumentUrl,
  normalizeHelpTopic,
  stripMarkdownFrontmatter
} from "../src/components/help-center";

const dialogStub = {
  props: ["modelValue"],
  emits: ["update:modelValue"],
  template: '<section v-if="modelValue"><slot name="header" /><slot /></section>'
};

const buttonStub = {
  props: ["disabled", "loading"],
  emits: ["click"],
  template: '<button type="button" :disabled="disabled || loading" @click="$emit(\'click\')"><slot /></button>'
};

const inputStub = {
  props: ["modelValue", "disabled"],
  emits: ["update:modelValue"],
  template: '<textarea :value="modelValue" :disabled="disabled" @input="$emit(\'update:modelValue\', $event.target.value)" />'
};

function mountHelpCenter(props: Record<string, unknown> = {}) {
  return mount(HelpCenterDialog, {
    props: {
      open: true,
      initialTopic: "getting-started",
      sideQuestionAvailable: true,
      ...props
    },
    global: {
      stubs: {
        "el-dialog": dialogStub,
        "el-button": buttonStub,
        "el-input": inputStub
      }
    }
  });
}

describe("help center", () => {
  it("keeps manual navigation under the same-origin help base and rejects unknown topics", () => {
    expect(helpDocumentUrl("process-initialization")).toBe("/help/guide/process-initialization.html");
    expect(helpDocumentUrl("settings")).toBe("/help/guide/settings.html");
    expect(helpDocumentUrl("directory-mapping")).toBe("/help/guide/directory-mapping.html");
    expect(normalizeHelpTopic("unknown")).toBe("getting-started");
  });

  it("keeps the directory chapter synchronized with the embedded Help navigation", async () => {
    const wrapper = mountHelpCenter();
    const directoryTopic = wrapper.findAll(".ta-help-center-topic")
      .find((button) => button.text().includes("开发与测试目录"));

    expect(directoryTopic).toBeDefined();
    await directoryTopic!.trigger("click");

    expect(wrapper.get('[data-testid="help-center-frame"]').attributes("src"))
      .toBe("/help/guide/directory-mapping.html");
    const prompt = buildManualQuestionPrompt("directory-mapping", "测试目录放什么？");
    expect(prompt).toContain("【当前章节】开发与测试目录");
    expect(prompt).toContain("公共 Git 与应用 Git");
    expect(prompt).toContain("个人 worktree");
    expect(prompt).not.toContain("directoryMapping:");
  });

  it("removes page frontmatter before building the pet manual context", () => {
    expect(stripMarkdownFrontmatter("---\naside: false\ndata:\n  value: 1\n---\n# 正文")).toBe("# 正文");
    expect(stripMarkdownFrontmatter("# 无 frontmatter")).toBe("# 无 frontmatter");
  });

  it("opens the requested chapter and switches the embedded manual", async () => {
    const wrapper = mountHelpCenter({ initialTopic: "process-initialization" });

    expect(wrapper.get('[data-testid="help-center-frame"]').attributes("src"))
      .toBe("/help/guide/process-initialization.html");

    const workspaceTopic = wrapper.findAll(".ta-help-center-topic")
      .find((button) => button.text().includes("应用与工作区"));
    expect(workspaceTopic).toBeDefined();
    await workspaceTopic!.trigger("click");

    expect(wrapper.get('[data-testid="help-center-frame"]').attributes("src"))
      .toBe("/help/guide/workspace.html");
  });

  it("grounds pet questions with the active manual chapter", async () => {
    const wrapper = mountHelpCenter({ initialTopic: "process-initialization" });
    const input = wrapper.get('[data-testid="help-center-question-input"]');
    await input.setValue("为什么初始化按钮不能点击？");
    await wrapper.get('[data-testid="help-center-question-submit"]').trigger("click");

    const emittedPrompt = wrapper.emitted("ask-pet")?.[0]?.[0] as string;
    expect(emittedPrompt).toContain("【当前章节】初始化进程");
    expect(emittedPrompt).toContain("分配专属进程");
    expect(emittedPrompt).toContain("为什么初始化按钮不能点击？");
    expect(emittedPrompt.length).toBeLessThan(3_900);
  });

  it("keeps the manual available when workspace runtime is not ready", () => {
    const wrapper = mountHelpCenter({ sideQuestionAvailable: false });

    expect(wrapper.find('[data-testid="help-center-frame"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="help-center-question-input"]').attributes("disabled")).toBeDefined();
    expect(wrapper.text()).toContain("无需建立主对话也能提问");
  });

  it("offers a replay entry for the first-login guide", async () => {
    const wrapper = mountHelpCenter();

    await wrapper.get('[data-testid="help-center-start-guide"]').trigger("click");

    expect(wrapper.emitted("start-guide")).toHaveLength(1);
  });

  it("builds a bounded manual prompt from the single Markdown source", () => {
    const prompt = buildManualQuestionPrompt("workspace", "个人工作区是什么？");
    expect(prompt).toContain("应用版本与个人工作区");
    expect(prompt).toContain("个人工作区是什么？");
    expect(prompt.length).toBeLessThan(3_900);
  });

  it("documents the implemented two-git permissions and personal HEAD publish flow", () => {
    const workspace = helpTopicById("workspace").content;
    const agentConfig = helpTopicById("agent-config").content;

    expect(workspace).toContain("当前平台使用两套物理 Git");
    expect(workspace).toContain("只把允许发布且已进入个人 `HEAD` 的文件");
    expect(workspace).toContain("`spec/**`");
    expect(workspace).toContain("只保留个人提交");
    expect(workspace).toContain("超级管理员也不能绕过该目录限制");
    expect(workspace).toContain("`docs/**`");
    expect(agentConfig).toContain("只有超级管理员可以创建公共 worktree");
    expect(agentConfig).toContain("不再创建独立的“应用配置 worktree”");
    expect(agentConfig).toContain("个人 `HEAD`");
    expect(agentConfig).toContain("`compatibility: opencode`");
    expect(agentConfig).toContain("公共配置推送成功后，平台会广播公共配置同步");
    expect(agentConfig).toContain("推送成功后更新应用版本 HEAD");
  });

  it("grounds settings questions in the role-aware operations chapter", () => {
    const settings = helpTopicById("settings").content;

    expect(settings).toContain("设置面板怎么用");
    expect(settings).toContain("普通用户：个人设置");
    expect(settings).toContain("用户配置");
    expect(settings).toContain("版本库配置");
    expect(settings).toContain("应用工作区配置");
    expect(settings).toContain("应用人员管理");
    expect(settings).toContain("应用与版本库关联");
    expect(settings).toContain("工作空间管理");
    expect(settings).toContain("超级管理员专属的“用户管理”");
  });
});
