import { flushPromises, mount } from "@vue/test-utils";
import { createPinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const routerHarness = vi.hoisted(() => ({
  currentRoute: { value: { name: "workbench" } },
  isReady: vi.fn<() => Promise<void>>(),
  replace: vi.fn()
}));

vi.mock("vue-router", async () => {
  const { defineComponent, h } = await import("vue");
  return {
    RouterView: defineComponent({
      name: "RouterView",
      setup: () => () => h("div", { "data-testid": "router-view" }, "工作台")
    }),
    useRouter: () => routerHarness
  };
});

import App from "../src/App.vue";

function mountApp() {
  return mount(App, {
    global: {
      plugins: [createPinia()],
      stubs: {
        ElConfigProvider: {
          template: "<div><slot /></div>"
        }
      }
    }
  });
}

describe("App initial navigation", () => {
  beforeEach(() => {
    window.sessionStorage.setItem("test-agent.auth.token", "test-token");
    routerHarness.currentRoute.value = { name: "workbench" };
    routerHarness.isReady.mockReset();
    routerHarness.replace.mockReset();
  });

  afterEach(() => {
    window.sessionStorage.clear();
  });

  it("shows the branded entry state until the initial async route is ready", async () => {
    let resolveReady!: () => void;
    routerHarness.isReady.mockReturnValue(new Promise<void>((resolve) => {
      resolveReady = resolve;
    }));

    const wrapper = mountApp();

    expect(wrapper.get('[data-testid="app-entry-loading"]').text()).toContain("正在进入工作台");
    expect(wrapper.find('[data-testid="router-view"]').exists()).toBe(false);

    resolveReady();
    await flushPromises();

    expect(wrapper.find('[data-testid="app-entry-loading"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="router-view"]').text()).toBe("工作台");
  });

  it("does not leave a permanent blank screen when initial navigation rejects", async () => {
    routerHarness.isReady.mockRejectedValue(new Error("route chunk failed"));

    const wrapper = mountApp();
    await flushPromises();

    expect(wrapper.find('[data-testid="app-entry-loading"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="router-view"]').exists()).toBe(true);
  });
});
