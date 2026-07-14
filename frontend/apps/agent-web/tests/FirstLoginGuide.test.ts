import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import FirstLoginGuide from "../src/components/FirstLoginGuide.vue";

const tourStub = {
  name: "ElTour",
  props: ["modelValue", "current"],
  emits: ["update:modelValue", "update:current", "close", "finish"],
  template: '<section v-if="modelValue" data-testid="guide-tour"><slot /></section>'
};

const stepStub = {
  name: "ElTourStep",
  template: '<article><slot name="header" /><slot /></article>'
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
    expect(wrapper.text()).toContain("01");
    expect(wrapper.text()).toContain("04");

    wrapper.getComponent(tourStub).vm.$emit("finish");
    await wrapper.vm.$nextTick();
    expect(window.localStorage.getItem("test-agent.onboarding.v1:usr_guide_1")).toBe("seen");
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
    window.localStorage.setItem("test-agent.onboarding.v1:usr_guide_1", "seen");
    const wrapper = mountGuide();

    (wrapper.vm as unknown as { restart: () => void }).restart();
    await flushPromises();

    expect(wrapper.find('[data-testid="guide-tour"]').exists()).toBe(true);
    expect(wrapper.emitted("prepare")).toHaveLength(1);
  });
});
