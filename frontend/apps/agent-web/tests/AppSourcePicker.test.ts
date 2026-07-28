import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import type { AppSourceRepositorySummary } from "@test-agent/shared-types";
import AppSourcePicker from "../src/components/AppSourcePicker.vue";

function repository(overrides: Partial<AppSourceRepositorySummary> = {}): AppSourceRepositorySummary {
  return {
    repositoryId: "repo-ready",
    name: "应用代码库",
    englishName: "application-code",
    downloadState: "DOWNLOADED_ACTIVE",
    generation: 3,
    purpose: "TEAM",
    branch: "main",
    targetCommit: "commit-summary",
    selectedPaths: [{ path: "src", type: "DIRECTORY" }],
    expiresAt: "2026-07-30T00:00:00Z",
    occupied: false,
    openable: true,
    manageable: true,
    latestOperation: null,
    serverSummaries: [],
    ...overrides
  };
}

describe("AppSourcePicker", () => {
  it("opens only an active local replica and explains unavailable repositories", async () => {
    const ready = repository();
    const expired = repository({
      repositoryId: "repo-expired",
      name: "过期代码库",
      downloadState: "DOWNLOADED_EXPIRED",
      openable: false,
      unavailableReason: "源码已过期，请重新下载"
    });
    const occupied = repository({
      repositoryId: "repo-personal",
      name: "个人代码库",
      downloadState: "PERSONAL_OCCUPIED",
      ownerName: "李四",
      ownerUnifiedAuthId: "10000001",
      openable: false,
      unavailableReason: "由其他用户个人占用"
    });
    const wrapper = mount(AppSourcePicker, {
      props: { open: true, repositories: [ready, expired, occupied] },
      global: { stubs: { Teleport: true } }
    });

    expect(wrapper.get('[data-source-state="DOWNLOADED_ACTIVE"]').classes()).toContain("is-active");
    expect(wrapper.get('[data-source-state="DOWNLOADED_EXPIRED"]').classes()).toContain("is-expired");
    expect(wrapper.text()).toContain("源码已过期，请重新下载");
    expect(wrapper.text()).toContain("李四 · 10000001");
    expect(wrapper.get('button[aria-label="打开过期代码库源码"]').attributes()).toHaveProperty("disabled");

    await wrapper.get('button[aria-label="打开应用代码库源码"]').trigger("click");
    expect(wrapper.emitted("open-source")).toEqual([[ready]]);
  });

  it("keeps the download action fixed at the bottom", async () => {
    const wrapper = mount(AppSourcePicker, {
      props: { open: true, repositories: [] },
      global: { stubs: { Teleport: true } }
    });

    const action = wrapper.get('button[aria-label="下载版本库"]');
    expect(action.element.parentElement?.classList).toContain("app-source-picker-footer");
    await action.trigger("click");
    expect(wrapper.emitted("download")).toHaveLength(1);
  });
});
