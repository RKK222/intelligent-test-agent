import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { mount } from "@vue/test-utils";
import { defineComponent, h } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import RequirementImportView from "../src/views/RequirementImportView.vue";

const listApplications = vi.fn();
const listItems = vi.fn();
const importRequirements = vi.fn();

const ElSelectStub = defineComponent({
  name: "ElSelect",
  inheritAttrs: false,
  props: { modelValue: String, disabled: Boolean, loading: Boolean },
  emits: ["update:modelValue", "change"],
  setup(props, { attrs, emit, slots }) {
    return () => h("select", {
      ...attrs,
      value: props.modelValue,
      disabled: props.disabled,
      "data-loading": String(props.loading),
      onChange: (event: Event) => {
        const value = (event.target as HTMLSelectElement).value;
        emit("update:modelValue", value);
        emit("change", value);
      }
    }, slots.default?.());
  }
});

const ElOptionStub = defineComponent({
  name: "ElOption",
  props: { label: String, value: String },
  setup(props) {
    return () => h("option", { value: props.value }, props.label);
  }
});

vi.mock("@test-agent/backend-api", () => ({
  createBackendApiClient: () => ({
    listRequirementImportApplications: listApplications,
    listWorkspaceRequirementImportItems: listItems,
    importWorkspaceRequirements: importRequirements
  })
}));

describe("RequirementImportView", () => {
  const postMessage = vi.fn();

  const mountView = () => mount(RequirementImportView, {
    global: { stubs: { ElSelect: ElSelectStub, ElOption: ElOptionStub } }
  });

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
      failures: [{ fileName: "设计说明.bin", code: "VALIDATION_ERROR", message: "不支持的 TCDS 文档格式" }]
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
      result: expect.objectContaining({ status: "PARTIAL" })
    }), window.location.origin);
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
    const selectAll = wrapper.get('.catalog-toolbar input[type="checkbox"]');
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
    const versionSelect = wrapper.get('select[aria-label="TCDS 版本"]');
    const applicationSelect = wrapper.get('select[aria-label="TCDS 应用"]');
    expect(versionSelect.attributes("disabled")).toBeUndefined();
    expect(applicationSelect.attributes("disabled")).toBeUndefined();

    await applicationSelect.setValue("APP-B");
    await vi.waitFor(() => expect(listItems).toHaveBeenLastCalledWith("wrk_1", "APP-B", expect.any(String)));
    const currentVersion = (versionSelect.element as HTMLSelectElement).value;
    const anotherVersion = versionSelect.findAll("option")
      .find((option) => (option.element as HTMLOptionElement).value !== currentVersion)!;
    const nextVersion = (anotherVersion.element as HTMLOptionElement).value;
    await versionSelect.setValue(nextVersion);
    await vi.waitFor(() => expect(listItems).toHaveBeenLastCalledWith("wrk_1", "APP-B", nextVersion));

    resolveItems([]);
    wrapper.unmount();
  });

  it("constrains all three filters and catalog rows to the iframe width", () => {
    const source = readFileSync(resolve(process.cwd(), "apps/agent-web/src/views/RequirementImportView.vue"), "utf8");

    expect(source).toMatch(/\.filters\s*\{[^}]*display: grid;[^}]*grid-template-columns: minmax\(180px, 0\.8fr\) minmax\(240px, 1\.4fr\) minmax\(180px, 1fr\);/s);
    expect(source).toMatch(/\.filter-field\s*\{[^}]*min-width: 0;/s);
    expect(source).toMatch(/\.filter-control\s*\{[^}]*min-width: 0;[^}]*flex: 1;/s);
    expect(source).toMatch(/\.catalog\s*\{[^}]*box-sizing: border-box;[^}]*width: 100%;[^}]*min-width: 0;/s);
  });
});
