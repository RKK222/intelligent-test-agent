import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import FirstLoginGuide from "../src/components/FirstLoginGuide.vue";

const tourStub = {
  name: "ElTour",
  props: ["modelValue", "current", "contentStyle"],
  emits: ["update:modelValue", "update:current", "close", "finish"],
  template: '<section v-if="modelValue" data-testid="guide-tour"><slot /></section>'
};

const stepStub = {
  name: "ElTourStep",
  props: ["target", "showArrow"],
  template: '<article :data-target="target"><slot name="header" /><slot /></article>'
};

function mountGuide(userId = "usr_guide_1", appAdmin = true) {
  return mount(FirstLoginGuide, {
    props: { userId, appAdmin },
    global: { stubs: { ElTour: tourStub, ElTourStep: stepStub } }
  });
}

describe("FirstLoginGuide", () => {
  afterEach(() => {
    window.localStorage.clear();
    vi.unstubAllGlobals();
  });

  it("opens once per user and persists completion", async () => {
    vi.stubGlobal("requestAnimationFrame", (callback: FrameRequestCallback) => {
      callback(0);
      return 1;
    });
    const wrapper = mountGuide();
    await flushPromises();

    expect(wrapper.find('[data-testid="guide-tour"]').exists()).toBe(true);
    expect(wrapper.getComponent(tourStub).props("contentStyle")).toEqual({
      width: "min(320px, calc(100vw - 32px))"
    });
    expect(wrapper.findAll("article")).toHaveLength(12);
    expect(wrapper.findAll("article").map((step) => step.attributes("data-target"))).toEqual([
      undefined,
      '[data-onboarding="application"]',
      '[data-onboarding="workspace-selector"]',
      '[data-onboarding="workspace-reference"]',
      '[data-onboarding="new-conversation"]',
      '[data-onboarding="pet"]',
      '[data-onboarding="settings-personal"]',
      '[data-onboarding="settings-repository"]',
      '[data-onboarding="settings-app-members"]',
      '[data-onboarding="settings-app-repositories"]',
      '[data-onboarding="settings-app-workspaces"]',
      '[data-onboarding="manual"]'
    ]);
    expect(wrapper.findAll("article")[0]?.attributes("data-target")).toBeUndefined();
    expect(wrapper.text()).toContain("这是你的工作面板");
    expect(wrapper.text()).toContain("一定要选中 workspace");
    expect(wrapper.text()).toContain("小地球");
    expect(wrapper.text()).toContain("第一条消息会建立对话");
    expect(wrapper.text()).toContain("12");
    expect(wrapper.text()).not.toContain("超级管理员");
    const sshStep = wrapper.find('[data-target="[data-onboarding=\\\"settings-personal\\\"]"]');
    expect(sshStep.text()).toContain("粘贴私钥");
    const repositoryStep = wrapper.find('[data-target="[data-onboarding=\\\"settings-repository\\\"]"]');
    expect(repositoryStep.text()).toContain("版本库管理");
    expect(repositoryStep.text()).toContain("部署模式");
    expect(repositoryStep.text()).toContain("版本库英文名称");
    const membersStep = wrapper.find('[data-target="[data-onboarding=\\\"settings-app-members\\\"]"]');
    expect(membersStep.text()).toContain("添加成员");
    const repositoriesStep = wrapper.find('[data-target="[data-onboarding=\\\"settings-app-repositories\\\"]"]');
    expect(repositoriesStep.text()).toContain("选择版本库");
    expect(repositoriesStep.text()).toContain("点击“关联”");
    const workspacesStep = wrapper.find('[data-target="[data-onboarding=\\\"settings-app-workspaces\\\"]"]');
    expect(workspacesStep.text()).toContain("类型为“测试工作库”");
    expect(workspacesStep.text()).toContain("工作空间别名");
    expect(workspacesStep.text()).toContain("目录树");
    expect(workspacesStep.text()).toContain("feature_testagent_yyyymmdd");
    expect(workspacesStep.text()).toContain("先选择 workspace，再选择版本");

    wrapper.getComponent(tourStub).vm.$emit("update:current", 6);
    await wrapper.vm.$nextTick();
    expect(wrapper.emitted("settings-step")?.at(-1)).toEqual([true, "personal"]);
    wrapper.getComponent(tourStub).vm.$emit("update:current", 7);
    await wrapper.vm.$nextTick();
    expect(wrapper.emitted("settings-step")?.at(-1)).toEqual([true, "repository"]);
    wrapper.getComponent(tourStub).vm.$emit("update:current", 8);
    await wrapper.vm.$nextTick();
    expect(wrapper.emitted("settings-step")?.at(-1)).toEqual([true, "members"]);
    wrapper.getComponent(tourStub).vm.$emit("update:current", 9);
    await wrapper.vm.$nextTick();
    expect(wrapper.emitted("settings-step")?.at(-1)).toEqual([true, "repositories"]);
    wrapper.getComponent(tourStub).vm.$emit("update:current", 10);
    await wrapper.vm.$nextTick();
    expect(wrapper.emitted("settings-step")?.at(-1)).toEqual([true, "workspaces"]);
    wrapper.getComponent(tourStub).vm.$emit("update:current", 11);
    await wrapper.vm.$nextTick();
    expect(wrapper.emitted("settings-step")?.at(-1)).toEqual([false]);

    wrapper.getComponent(tourStub).vm.$emit("finish");
    await wrapper.vm.$nextTick();
    expect(window.localStorage.getItem("test-agent.onboarding.v7:usr_guide_1")).toBe("seen");
    expect(wrapper.emitted("finish")).toHaveLength(1);

    const second = mountGuide();
    await flushPromises();
    expect(second.find('[data-testid="guide-tour"]').exists()).toBe(false);
  });

  it("can be replayed from the manual after it was seen", async () => {
    vi.stubGlobal("requestAnimationFrame", (callback: FrameRequestCallback) => {
      callback(0);
      return 1;
    });
    window.localStorage.setItem("test-agent.onboarding.v7:usr_guide_1", "seen");
    const wrapper = mountGuide();

    (wrapper.vm as unknown as { restart: () => void }).restart();
    await flushPromises();

    expect(wrapper.find('[data-testid="guide-tour"]').exists()).toBe(true);
    expect(wrapper.emitted("prepare")).toHaveLength(1);
  });

  it("keeps application settings steps out of the regular user guide", async () => {
    vi.stubGlobal("requestAnimationFrame", (callback: FrameRequestCallback) => {
      callback(0);
      return 1;
    });
    const wrapper = mountGuide("usr_regular_1", false);
    await flushPromises();

    expect(wrapper.findAll("article")).toHaveLength(8);
    expect(wrapper.text()).toContain("SSH 配置");
    expect(wrapper.text()).not.toContain("应用与版本库配置");
    expect(wrapper.text()).toContain("08");
  });
});
