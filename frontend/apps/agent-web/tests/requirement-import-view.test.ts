import { mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import RequirementImportView from "../src/views/RequirementImportView.vue";

const listApplications = vi.fn();
const listItems = vi.fn();
const importRequirements = vi.fn();

vi.mock("@test-agent/backend-api", () => ({
  createBackendApiClient: () => ({
    listRequirementImportApplications: listApplications,
    listRequirementImportItems: listItems,
    importWorkspaceRequirements: importRequirements
  })
}));

describe("RequirementImportView", () => {
  const postMessage = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    Object.defineProperty(window, "parent", { configurable: true, value: { postMessage } });
    listApplications.mockResolvedValue([
      { appName: "应用甲", appShortName: "APP-A" },
      { appName: "应用乙", appShortName: "APP-B" }
    ]);
    listItems.mockResolvedValue([
      {
        itemNo: "I-01",
        itemName: "登录需求",
        children: [
          { itemNo: "SI-01", itemName: "登录校验" },
          { itemNo: "SI-02", itemName: "会话续期" }
        ]
      }
    ]);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("announces ready and ignores context from a different origin or source", async () => {
    const wrapper = mount(RequirementImportView);
    expect(postMessage).toHaveBeenCalledWith(
      { type: "ITA_REQUIREMENT_IMPORT_READY" },
      window.location.origin
    );

    window.dispatchEvent(new MessageEvent("message", {
      origin: "https://attacker.example",
      source: window.parent,
      data: { type: "ITA_REQUIREMENT_IMPORT_CONTEXT", workspaceId: "wrk_1", requestId: "req_1" }
    }));
    window.dispatchEvent(new MessageEvent("message", {
      origin: window.location.origin,
      source: window,
      data: { type: "ITA_REQUIREMENT_IMPORT_CONTEXT", workspaceId: "wrk_1", requestId: "req_1" }
    }));
    await vi.waitFor(() => expect(listApplications).not.toHaveBeenCalled());
    wrapper.unmount();
  });

  it("loads trusted catalog, keeps filtered selection, and reports partial result to the exact origin", async () => {
    importRequirements.mockResolvedValue({
      status: "PARTIAL",
      createdDirectories: 6,
      importedFiles: 1,
      overwrittenFiles: 0,
      failedFiles: 1,
      failures: [{ fileName: "设计说明.bin", code: "VALIDATION_ERROR", message: "不支持的 TCDS 文档格式" }]
    });
    const wrapper = mount(RequirementImportView);
    window.dispatchEvent(new MessageEvent("message", {
      origin: window.location.origin,
      source: window.parent,
      data: {
        type: "ITA_REQUIREMENT_IMPORT_CONTEXT",
        workspaceId: "wrk_1",
        defaultAppName: "应用乙",
        requestId: "req_1"
      }
    }));

    await vi.waitFor(() => expect(listItems).toHaveBeenCalledWith("APP-B", expect.stringMatching(/^\d{4}年\d{1,2}月$/)));
    await wrapper.get('input[type="search"]').setValue("SI-01");
    const visibleCheckboxes = wrapper.findAll('.child-row input[type="checkbox"]');
    expect(visibleCheckboxes).toHaveLength(1);
    await visibleCheckboxes[0].setValue(true);
    await wrapper.get('input[type="search"]').setValue("");
    expect(wrapper.text()).toContain("已选 1 / 100");
    await wrapper.get("footer button").trigger("click");

    await vi.waitFor(() => expect(importRequirements).toHaveBeenCalledWith(expect.objectContaining({
      workspaceId: "wrk_1",
      appShortName: "APP-B",
      selectedSubItemNos: ["SI-01"],
      requestId: "req_1"
    })));
    expect(postMessage).toHaveBeenLastCalledWith(expect.objectContaining({
      type: "ITA_REQUIREMENT_IMPORT_COMPLETE",
      requestId: "req_1",
      result: expect.objectContaining({ status: "PARTIAL" })
    }), window.location.origin);
    expect(wrapper.text()).toContain("部分文档导入失败");
    wrapper.unmount();
  });
});
