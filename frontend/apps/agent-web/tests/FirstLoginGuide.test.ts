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

function mountGuide(userId = "usr_guide_1") {
  return mount(FirstLoginGuide, {
    props: { userId },
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
    expect(wrapper.findAll("article")).toHaveLength(8);
    expect(wrapper.findAll("article")[0]?.attributes("data-target")).toBeUndefined();
    expect(wrapper.text()).toContain("这是你的工作面板");
    expect(wrapper.text()).toContain("08");
    expect(wrapper.text()).not.toContain("超级管理员");
    expect(wrapper.find('[data-target="[data-onboarding=\\\"settings\\\"]"]').text()).toContain("配置 SSH");

    wrapper.getComponent(tourStub).vm.$emit("finish");
    await wrapper.vm.$nextTick();
    expect(window.localStorage.getItem("test-agent.onboarding.v2:usr_guide_1")).toBe("seen");
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
    window.localStorage.setItem("test-agent.onboarding.v2:usr_guide_1", "seen");
    const wrapper = mountGuide();

    (wrapper.vm as unknown as { restart: () => void }).restart();
    await flushPromises();

    expect(wrapper.find('[data-testid="guide-tour"]').exists()).toBe(true);
    expect(wrapper.emitted("prepare")).toHaveLength(1);
  });
});
