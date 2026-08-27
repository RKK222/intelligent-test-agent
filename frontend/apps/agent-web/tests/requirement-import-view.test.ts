import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import RequirementImportView from "../src/views/RequirementImportView.vue";

const listApplications = vi.fn();
const listItems = vi.fn();
const importRequirements = vi.fn();

vi.mock("@test-agent/backend-api", () => ({
  createBackendApiClient: () => ({
    listRequirementImportApplications: listApplications,
    listWorkspaceRequirementImportItems: listItems,
    importWorkspaceRequirements: importRequirements
  })
}));

describe("RequirementImportView", () => {
  const postMessage = vi.fn();

  const mountView = () => mount(RequirementImportView);

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
        imported: true,
        children: [
          { itemNo: "SI-01", itemName: "登录校验", imported: true },
          { itemNo: "SI-02", itemName: "会话续期", imported: false }
        ]
      }
    ]);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("announces ready and ignores context from a different origin or source", async () => {
    const wrapper = mountView();
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
      failures: [{ fileName: "设计说明.bin", code: "VALIDATION_ERROR", message: "不支持的 TCDS 文档格式" }],
      workspaceRelativeDisplayPaths: ["spec/I-01-登录需求"]
    });
    const wrapper = mountView();
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

    await vi.waitFor(() => expect(listItems).toHaveBeenCalledWith(
      "wrk_1",
      "APP-B",
      expect.stringMatching(/^\d{4}年\d{1,2}月$/)
    ));
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
      result: expect.objectContaining({
        status: "PARTIAL",
        workspaceRelativeDisplayPaths: ["spec/I-01-登录需求"]
      })
    }), window.location.origin);
    expect(wrapper.find(".importing-mask").exists()).toBe(true);
    window.dispatchEvent(new MessageEvent("message", {
      origin: window.location.origin,
      source: window.parent,
      data: { type: "ITA_REQUIREMENT_IMPORT_TREE_REFRESHED", requestId: "req_1", success: true }
    }));
    await vi.waitFor(() => expect(wrapper.find(".importing-mask").exists()).toBe(false));
    expect(wrapper.text()).toContain("部分文档导入失败");
    wrapper.unmount();
  });

  it("shows imported state without disabling repeated selection", async () => {
    const wrapper = mountView();
    window.dispatchEvent(new MessageEvent("message", {
      origin: window.location.origin,
      source: window.parent,
      data: {
        type: "ITA_REQUIREMENT_IMPORT_CONTEXT",
        workspaceId: "wrk_1",
        requestId: "req_imported_state"
      }
    }));

    await vi.waitFor(() => expect(listItems).toHaveBeenCalled());
    const importedRow = wrapper.findAll(".child-row")
      .find((row) => row.text().includes("SI-01"))!;
    const pendingRow = wrapper.findAll(".child-row")
      .find((row) => row.text().includes("SI-02"))!;

    expect(importedRow.text()).toContain("已导入");
    expect(pendingRow.text()).toContain("未导入");
    expect(importedRow.get('input[type="checkbox"]').attributes("disabled")).toBeUndefined();
    await importedRow.get('input[type="checkbox"]').setValue(true);
    expect(wrapper.text()).toContain("已选 1 / 100");
    wrapper.unmount();
  });

  it("blocks repeated interaction with an animated mask while generation is running", async () => {
    let resolveImport!: (result: {
      status: "SUCCEEDED";
      createdDirectories: number;
      importedFiles: number;
      overwrittenFiles: number;
      failedFiles: number;
      failures: never[];
    }) => void;
    importRequirements.mockImplementationOnce(() => new Promise((resolve) => { resolveImport = resolve; }));
    const wrapper = mountView();
    window.dispatchEvent(new MessageEvent("message", {
      origin: window.location.origin,
      source: window.parent,
      data: {
        type: "ITA_REQUIREMENT_IMPORT_CONTEXT",
        workspaceId: "wrk_1",
        requestId: "req_mask"
      }
    }));

    await vi.waitFor(() => expect(listItems).toHaveBeenCalled());
    await wrapper.get('.child-row input[type="checkbox"]').setValue(true);
    await wrapper.get("footer button").trigger("click");

    await vi.waitFor(() => expect(importRequirements).toHaveBeenCalledTimes(1));
    expect(wrapper.get(".importing-mask").attributes("role")).toBe("status");
    expect(wrapper.get(".importing-mask").text()).toContain("正在生成需求目录和文档");
    expect(wrapper.find(".importing-spinner").exists()).toBe(true);
    expect(wrapper.get("footer button").attributes("disabled")).toBeDefined();

    resolveImport({
      status: "SUCCEEDED",
      createdDirectories: 6,
      importedFiles: 1,
      overwrittenFiles: 0,
      failedFiles: 0,
      failures: []
    });
    await vi.waitFor(() => expect(postMessage).toHaveBeenCalledWith(expect.objectContaining({
      type: "ITA_REQUIREMENT_IMPORT_COMPLETE",
      requestId: "req_mask"
    }), window.location.origin));
    expect(wrapper.find(".importing-mask").exists()).toBe(true);
    window.dispatchEvent(new MessageEvent("message", {
      origin: window.location.origin,
      source: window.parent,
      data: { type: "ITA_REQUIREMENT_IMPORT_TREE_REFRESHED", requestId: "other_request", success: true }
    }));
    expect(wrapper.find(".importing-mask").exists()).toBe(true);
    window.dispatchEvent(new MessageEvent("message", {
      origin: window.location.origin,
      source: window.parent,
      data: { type: "ITA_REQUIREMENT_IMPORT_TREE_REFRESHED", requestId: "req_mask", success: true }
    }));
    await vi.waitFor(() => expect(wrapper.find(".importing-mask").exists()).toBe(false));
    expect(wrapper.text()).toContain("导入成功");
    wrapper.unmount();
  });

  it("keeps select-all, parent selection, and child multi-selection behavior", async () => {
    const wrapper = mountView();
    window.dispatchEvent(new MessageEvent("message", {
      origin: window.location.origin,
      source: window.parent,
      data: {
        type: "ITA_REQUIREMENT_IMPORT_CONTEXT",
        workspaceId: "wrk_1",
        requestId: "req_selection"
      }
    }));

    await vi.waitFor(() => expect(listItems).toHaveBeenCalled());
    const selectAll = wrapper.get('.select-all-toggle input[type="checkbox"]');
    const parent = wrapper.get('.parent-row input[type="checkbox"]');
    const children = wrapper.findAll('.child-row input[type="checkbox"]');

    expect(children).toHaveLength(2);
    await selectAll.setValue(true);
    expect(wrapper.text()).toContain("已选 2 / 100");
    expect(children.every((checkbox) => (checkbox.element as HTMLInputElement).checked)).toBe(true);

    await children[0].setValue(false);
    expect((selectAll.element as HTMLInputElement).indeterminate).toBe(true);
    expect((parent.element as HTMLInputElement).indeterminate).toBe(true);

    await parent.setValue(true);
    expect(children.every((checkbox) => (checkbox.element as HTMLInputElement).checked)).toBe(true);
    wrapper.unmount();
  });

  it("keeps version and application selectors usable while a catalog request is pending", async () => {
    let resolveItems!: (items: unknown[]) => void;
    listItems
      .mockImplementationOnce(() => new Promise((resolve) => { resolveItems = resolve; }))
      .mockResolvedValue([]);
    const wrapper = mountView();
    window.dispatchEvent(new MessageEvent("message", {
      origin: window.location.origin,
      source: window.parent,
      data: {
        type: "ITA_REQUIREMENT_IMPORT_CONTEXT",
        workspaceId: "wrk_1",
        requestId: "req_filters"
      }
    }));

    await vi.waitFor(() => expect(listItems).toHaveBeenCalledTimes(1));
    const versionInput = wrapper.get('input[aria-label="TCDS 版本"]');
    const applicationInput = wrapper.get('input[aria-label="TCDS 应用"]');
    expect(versionInput.attributes("disabled")).toBeUndefined();
    expect(applicationInput.attributes("disabled")).toBeUndefined();

    await versionInput.trigger("focus");
    expect((versionInput.element as HTMLInputElement).selectionStart).toBe(0);
    expect((versionInput.element as HTMLInputElement).selectionEnd).toBe((versionInput.element as HTMLInputElement).value.length);
    await applicationInput.trigger("focus");
    expect((applicationInput.element as HTMLInputElement).selectionStart).toBe(0);
    expect((applicationInput.element as HTMLInputElement).selectionEnd).toBe((applicationInput.element as HTMLInputElement).value.length);

    await applicationInput.setValue("应用乙（APP-B）");
    await applicationInput.trigger("change");
    await vi.waitFor(() => expect(listItems).toHaveBeenLastCalledWith("wrk_1", "APP-B", expect.any(String)));
    const arbitraryVersion = "2024年1月";
    await versionInput.setValue(arbitraryVersion);
    await versionInput.trigger("change");
    await vi.waitFor(() => expect(listItems).toHaveBeenLastCalledWith("wrk_1", "APP-B", arbitraryVersion));

    resolveItems([]);
    wrapper.unmount();
  });

  it("preserves arbitrary parent application and version values and keeps the version when the application changes", async () => {
    const wrapper = mountView();
    window.dispatchEvent(new MessageEvent("message", {
      origin: window.location.origin,
      source: window.parent,
      data: {
        type: "ITA_REQUIREMENT_IMPORT_CONTEXT",
        workspaceId: "wrk_1",
        defaultAppName: "APP-OUTSIDE-CATALOG",
        defaultVersion: "2023年12月",
        requestId: "req_arbitrary_version"
      }
    }));

    await vi.waitFor(() => expect(listItems).toHaveBeenCalledWith(
      "wrk_1",
      "APP-OUTSIDE-CATALOG",
      "2023年12月"
    ));
    const versionInput = wrapper.get('input[aria-label="TCDS 版本"]');
    const applicationInput = wrapper.get('input[aria-label="TCDS 应用"]');
    expect((versionInput.element as HTMLInputElement).value).toBe("2023年12月");
    expect((applicationInput.element as HTMLInputElement).value).toBe("APP-OUTSIDE-CATALOG");

    await versionInput.trigger("focus");
    const versionOptions = wrapper.findAll('#requirement-import-version-options [role="option"]');
    expect(versionOptions).toHaveLength(8);
    expect(versionOptions[0].text()).toContain("2023年12月");
    expect(versionOptions[0].text()).toContain("当前");

    await applicationInput.trigger("focus");
    expect(wrapper.findAll('#requirement-import-application-options [role="option"]')).toHaveLength(2);

    await applicationInput.setValue("应用乙（APP-B）");
    await applicationInput.trigger("change");
    await vi.waitFor(() => expect(listItems).toHaveBeenLastCalledWith("wrk_1", "APP-B", "2023年12月"));
    expect((versionInput.element as HTMLInputElement).value).toBe("2023年12月");
    wrapper.unmount();
  });

  it("shows every quick version and TCDS application suggestion even when inputs already have values", async () => {
    const wrapper = mountView();
    window.dispatchEvent(new MessageEvent("message", {
      origin: window.location.origin,
      source: window.parent,
      data: {
        type: "ITA_REQUIREMENT_IMPORT_CONTEXT",
        workspaceId: "wrk_1",
        defaultAppName: "APP-A",
        requestId: "req_dropdowns"
      }
    }));

    await vi.waitFor(() => expect(listItems).toHaveBeenCalledTimes(1));
    const versionInput = wrapper.get('input[aria-label="TCDS 版本"]');
    const applicationInput = wrapper.get('input[aria-label="TCDS 应用"]');

    await versionInput.trigger("focus");
    expect(wrapper.findAll('#requirement-import-version-options [role="option"]')).toHaveLength(7);
    const currentVersion = (versionInput.element as HTMLInputElement).value;
    const anotherVersion = wrapper.findAll('#requirement-import-version-options button')
      .find((button) => !button.text().includes(currentVersion))!;
    const chosenVersion = anotherVersion.text();
    await anotherVersion.trigger("click");
    await vi.waitFor(() => expect(listItems).toHaveBeenLastCalledWith("wrk_1", "APP-A", chosenVersion));

    await applicationInput.trigger("focus");
    expect(wrapper.findAll('#requirement-import-application-options [role="option"]')).toHaveLength(2);
    const applicationB = wrapper.findAll('#requirement-import-application-options button')
      .find((button) => button.text().includes("APP-B"))!;
    await applicationB.trigger("click");
    await vi.waitFor(() => expect(listItems).toHaveBeenLastCalledWith(
      "wrk_1",
      "APP-B",
      (versionInput.element as HTMLInputElement).value
    ));
    wrapper.unmount();
  });

  it("keeps an exact application short-name filtered after the last character is entered", async () => {
    listApplications.mockResolvedValueOnce([
      { appName: "基础应用", appShortName: "f-base" },
      { appName: "其他应用", appShortName: "f-batch" }
    ]);
    const wrapper = mountView();
    window.dispatchEvent(new MessageEvent("message", {
      origin: window.location.origin,
      source: window.parent,
      data: {
        type: "ITA_REQUIREMENT_IMPORT_CONTEXT",
        workspaceId: "wrk_1",
        requestId: "req_exact_application_filter"
      }
    }));

    await vi.waitFor(() => expect(listItems).toHaveBeenCalledTimes(1));
    const applicationInput = wrapper.get('input[aria-label="TCDS 应用"]');
    (applicationInput.element as HTMLInputElement).value = "f-bas";
    await applicationInput.trigger("input");
    expect(wrapper.findAll('#requirement-import-application-options [role="option"]')).toHaveLength(1);
    expect(wrapper.get('#requirement-import-application-options [role="option"]').text()).toContain("f-base");

    (applicationInput.element as HTMLInputElement).value = "f-base";
    await applicationInput.trigger("input");
    expect(wrapper.findAll('#requirement-import-application-options [role="option"]')).toHaveLength(1);
    expect(wrapper.get('#requirement-import-application-options [role="option"]').text()).toContain("f-base");
    wrapper.unmount();
  });

  it("applies lowercase and unique fuzzy application input with the canonical short name", async () => {
    listApplications.mockResolvedValueOnce([
      { appName: "基础应用", appShortName: "F-BASE" },
      { appName: "其他应用", appShortName: "F-BATCH" }
    ]);
    const wrapper = mountView();
    window.dispatchEvent(new MessageEvent("message", {
      origin: window.location.origin,
      source: window.parent,
      data: {
        type: "ITA_REQUIREMENT_IMPORT_CONTEXT",
        workspaceId: "wrk_1",
        requestId: "req_case_insensitive_application"
      }
    }));

    await vi.waitFor(() => expect(listItems).toHaveBeenCalledTimes(1));
    const applicationInput = wrapper.get('input[aria-label="TCDS 应用"]');

    await applicationInput.setValue("f-batch");
    await applicationInput.trigger("keydown.enter");
    await vi.waitFor(() => expect(listItems).toHaveBeenLastCalledWith("wrk_1", "F-BATCH", expect.any(String)));
    expect((applicationInput.element as HTMLInputElement).value).toBe("其他应用（F-BATCH）");

    await applicationInput.setValue("基础");
    await applicationInput.trigger("change");
    await vi.waitFor(() => expect(listItems).toHaveBeenLastCalledWith("wrk_1", "F-BASE", expect.any(String)));
    expect((applicationInput.element as HTMLInputElement).value).toBe("基础应用（F-BASE）");
    wrapper.unmount();
  });

  it("keeps the application option selectable after fuzzy typing", async () => {
    const wrapper = mountView();
    window.dispatchEvent(new MessageEvent("message", {
      origin: window.location.origin,
      source: window.parent,
      data: {
        type: "ITA_REQUIREMENT_IMPORT_CONTEXT",
        workspaceId: "wrk_1",
        requestId: "req_fuzzy_application_click"
      }
    }));

    await vi.waitFor(() => expect(listItems).toHaveBeenCalledTimes(1));
    const applicationInput = wrapper.get('input[aria-label="TCDS 应用"]');
    await applicationInput.trigger("focus");
    (applicationInput.element as HTMLInputElement).value = "app-b";
    await applicationInput.trigger("input");
    const optionButton = wrapper.get('#requirement-import-application-options [role="option"] button');
    const mouseDown = new MouseEvent("mousedown", { bubbles: true, cancelable: true });
    optionButton.element.dispatchEvent(mouseDown);
    expect(mouseDown.defaultPrevented).toBe(true);
    await optionButton.trigger("click");

    await vi.waitFor(() => expect(listItems).toHaveBeenLastCalledWith("wrk_1", "APP-B", expect.any(String)));
    expect((applicationInput.element as HTMLInputElement).value).toBe("应用乙（APP-B）");
    wrapper.unmount();
  });

  it("filters sub-items by parent or child tokens, preserves selection, and supports selected-only view", async () => {
    listItems.mockResolvedValueOnce([
      {
        itemNo: "I-01",
        itemName: "登录需求",
        children: [
          { itemNo: "SI-01", itemName: "登录校验" },
          { itemNo: "SI-02", itemName: "会话续期" }
        ]
      },
      {
        itemNo: "I-02",
        itemName: "支付需求",
        children: [
          { itemNo: "SI-03", itemName: "支付确认" }
        ]
      }
    ]);
    const wrapper = mountView();
    window.dispatchEvent(new MessageEvent("message", {
      origin: window.location.origin,
      source: window.parent,
      data: {
        type: "ITA_REQUIREMENT_IMPORT_CONTEXT",
        workspaceId: "wrk_1",
        requestId: "req_item_filters"
      }
    }));

    await vi.waitFor(() => expect(wrapper.findAll(".child-row")).toHaveLength(3));
    const searchInput = wrapper.get('input[aria-label="筛选需求子条目"]');

    await searchInput.setValue("登录 SI-01");
    expect(wrapper.findAll(".child-row")).toHaveLength(1);
    expect(wrapper.get(".child-row").text()).toContain("SI-01");
    await wrapper.get('.child-row input[type="checkbox"]').setValue(true);

    await searchInput.setValue("登录");
    expect(wrapper.findAll(".child-row")).toHaveLength(2);
    await searchInput.setValue("");
    expect(wrapper.text()).toContain("已选 1 / 100 · 显示 3 / 3");

    await wrapper.get('input[aria-label="仅显示已选子条目"]').setValue(true);
    expect(wrapper.findAll(".child-row")).toHaveLength(1);
    expect(wrapper.get(".child-row").text()).toContain("SI-01");

    await searchInput.setValue("会话");
    expect(wrapper.text()).toContain("没有符合当前搜索条件的已选子条目");
    await wrapper.get("button.toolbar-link").trigger("click");
    expect((searchInput.element as HTMLInputElement).value).toBe("");
    expect((wrapper.get('input[aria-label="仅显示已选子条目"]').element as HTMLInputElement).checked).toBe(false);
    expect(wrapper.findAll(".child-row")).toHaveLength(3);
    expect(wrapper.text()).toContain("已选 1 / 100");
    wrapper.unmount();
  });

  it("accepts an arbitrary non-empty application value outside the TCDS suggestions", async () => {
    const wrapper = mountView();
    window.dispatchEvent(new MessageEvent("message", {
      origin: window.location.origin,
      source: window.parent,
      data: {
        type: "ITA_REQUIREMENT_IMPORT_CONTEXT",
        workspaceId: "wrk_1",
        requestId: "req_arbitrary_application"
      }
    }));

    await vi.waitFor(() => expect(listItems).toHaveBeenCalledTimes(1));
    const applicationInput = wrapper.get('input[aria-label="TCDS 应用"]');
    const currentVersion = (wrapper.get('input[aria-label="TCDS 版本"]').element as HTMLInputElement).value;
    await applicationInput.setValue("CUSTOM-APP");
    await applicationInput.trigger("change");

    await vi.waitFor(() => expect(listItems).toHaveBeenLastCalledWith(
      "wrk_1",
      "CUSTOM-APP",
      currentVersion
    ));
    expect(wrapper.text()).not.toContain("请选择当前用户有权访问");
    wrapper.unmount();
  });

  it("keeps arbitrary application input available when TCDS returns no suggestions", async () => {
    listApplications.mockResolvedValueOnce([]);
    const wrapper = mountView();
    window.dispatchEvent(new MessageEvent("message", {
      origin: window.location.origin,
      source: window.parent,
      data: {
        type: "ITA_REQUIREMENT_IMPORT_CONTEXT",
        workspaceId: "wrk_1",
        requestId: "req_empty_application_catalog"
      }
    }));

    await vi.waitFor(() => expect(listApplications).toHaveBeenCalledTimes(1));
    const applicationInput = wrapper.get('input[aria-label="TCDS 应用"]');
    expect(applicationInput.attributes("disabled")).toBeUndefined();
    await applicationInput.setValue("CUSTOM-APP-WITHOUT-SUGGESTIONS");
    await applicationInput.trigger("change");

    await vi.waitFor(() => expect(listItems).toHaveBeenCalledWith(
      "wrk_1",
      "CUSTOM-APP-WITHOUT-SUGGESTIONS",
      expect.any(String)
    ));
    wrapper.unmount();
  });

  it("requires only a non-empty version before querying TCDS", async () => {
    const wrapper = mountView();
    window.dispatchEvent(new MessageEvent("message", {
      origin: window.location.origin,
      source: window.parent,
      data: {
        type: "ITA_REQUIREMENT_IMPORT_CONTEXT",
        workspaceId: "wrk_1",
        requestId: "req_empty_version"
      }
    }));

    await vi.waitFor(() => expect(listItems).toHaveBeenCalledTimes(1));
    const versionInput = wrapper.get('input[aria-label="TCDS 版本"]');
    await versionInput.setValue("   ");
    await versionInput.trigger("change");

    expect(listItems).toHaveBeenCalledTimes(1);
    expect(wrapper.text()).toContain("请输入 TCDS 版本");
    wrapper.unmount();
  });

  it("constrains all three filters and catalog rows to the iframe width", () => {
    const source = readFileSync(resolve(process.cwd(), "apps/agent-web/src/views/RequirementImportView.vue"), "utf8");

    expect(source).toMatch(/\.filters\s*\{[^}]*display: grid;[^}]*grid-template-columns: minmax\(180px, 0\.8fr\) minmax\(240px, 1\.4fr\) minmax\(180px, 1fr\);/s);
    expect(source).toMatch(/\.filter-field\s*\{[^}]*min-width: 0;/s);
    expect(source).toMatch(/\.filter-control\s*\{[^}]*min-width: 0;[^}]*flex: 1;/s);
    expect(source).toMatch(/\.catalog\s*\{[^}]*box-sizing: border-box;[^}]*width: 100%;[^}]*min-width: 0;/s);
    expect(source).not.toContain("backdrop-filter");
  });
});
