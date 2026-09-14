import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import type { AppSourceRepositorySummary } from "@test-agent/shared-types";
import CodeKnowledgeScopePicker from "../src/components/CodeKnowledgeScopePicker.vue";

function repository(
  repositoryId: string,
  name: string,
  overrides: Partial<AppSourceRepositorySummary> = {}
): AppSourceRepositorySummary {
  return {
    repositoryId,
    name,
    englishName: name,
    downloadState: "DOWNLOADED_ACTIVE",
    generation: 3,
    purpose: "TEAM",
    branch: "main",
    targetCommit: "abcdef0123456789",
    selectedPaths: [{ path: "src", type: "DIRECTORY" }],
    occupied: false,
    openable: true,
    manageable: true,
    latestOperation: null,
    serverSummaries: [],
    ...overrides
  };
}

describe("CodeKnowledgeScopePicker", () => {
  it("allows multiple repositories without allowing an empty scope", async () => {
    const repositories = [
      repository("repo_orders", "订单"),
      repository("repo_payments", "支付")
    ];
    const wrapper = mount(CodeKnowledgeScopePicker, {
      props: {
        available: true,
        repositories,
        selectedRepositoryIds: repositories.map((item) => item.repositoryId)
      }
    });

    await wrapper.get('[data-testid="code-knowledge-scope-trigger"]').trigger("click");
    expect(wrapper.get('[data-testid="code-knowledge-scope-dropdown"]').text()).toContain("不会切换当前工作区");

    await wrapper.get('input[aria-label="选择支付"]').setValue(false);
    expect(wrapper.emitted("update:selected-repository-ids")).toEqual([[["repo_orders"]]]);

    await wrapper.setProps({ selectedRepositoryIds: ["repo_orders"] });
    expect(wrapper.get('input[aria-label="选择订单"]').attributes()).toHaveProperty("disabled");
  });

  it("shows source state and reuses the preparation entry for a missing baseline", async () => {
    const missing = repository("repo_legacy", "旧系统", {
      downloadState: "NOT_DOWNLOADED",
      generation: null,
      branch: null,
      targetCommit: null,
      openable: false
    });
    const wrapper = mount(CodeKnowledgeScopePicker, {
      props: {
        available: true,
        repositories: [missing],
        selectedRepositoryIds: [missing.repositoryId]
      }
    });

    await wrapper.get('[data-testid="code-knowledge-scope-trigger"]').trigger("click");
    expect(wrapper.text()).toContain("源码未准备");

    await wrapper.get('button[aria-label="准备旧系统源码"]').trigger("click");
    expect(wrapper.emitted("prepare-source")).toEqual([["repo_legacy"]]);
  });

  it("stays hidden when the feature is disabled for the current user", () => {
    const wrapper = mount(CodeKnowledgeScopePicker, {
      props: { available: false, repositories: [], selectedRepositoryIds: [] }
    });

    expect(wrapper.find('[data-testid="code-knowledge-scope"]').exists()).toBe(false);
  });
});
