import { defineComponent, h } from "vue";
import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it } from "vitest";
import TestCaseMaintenanceDialog from "../src/components/TestCaseMaintenanceDialog.vue";

const ElDialogStub = defineComponent({
  name: "ElDialog",
  props: { modelValue: Boolean },
  template: `<section v-if="modelValue" role="dialog"><slot name="header" /><slot /><slot name="footer" /></section>`
});

const ElSelectStub = defineComponent({
  name: "ElSelect",
  props: { modelValue: { type: Array, default: () => [] }, disabled: Boolean, ariaLabel: String },
  emits: ["update:modelValue"],
  setup(props, { emit, slots, attrs }) {
    return () => h("select", {
      ...attrs,
      "aria-label": props.ariaLabel,
      multiple: true,
      disabled: props.disabled,
      value: props.modelValue,
      onChange: (event: Event) => emit("update:modelValue", Array.from((event.target as HTMLSelectElement).selectedOptions).map((option) => option.value))
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

const ElCheckboxStub = defineComponent({
  name: "ElCheckbox",
  props: { modelValue: Boolean, disabled: Boolean },
  emits: ["update:modelValue", "change"],
  setup(props, { emit, attrs }) {
    return () => h("input", {
      ...attrs,
      type: "checkbox",
      checked: props.modelValue,
      disabled: props.disabled,
      onChange: (event: Event) => {
        const checked = (event.target as HTMLInputElement).checked;
        emit("update:modelValue", checked);
        emit("change", checked);
      }
    });
  }
});

const ButtonStub = defineComponent({
  name: "Button",
  props: { disabled: Boolean },
  setup(props, { slots, attrs }) {
    return () => h("button", { ...attrs, disabled: props.disabled }, slots.default?.());
  }
});

const taskTypeOptions = [
  { name: "业务风险防控测试任务", value: "2" },
  { name: "功能测试任务", value: "3" }
];

function mountDialog(overrides: Record<string, unknown> = {}) {
  return mount(TestCaseMaintenanceDialog, {
    props: {
      open: true,
      fileName: "案例.md",
      itemNo: "S20260703-000081",
      cases: [
        { name: "案例一", step: "步骤一", data: "数据一", expect: "结果一", taskTypes: [] },
        { name: "案例二", step: "步骤二", data: "数据二", expect: "结果二", taskTypes: [] }
      ],
      taskTypeOptions,
      ...overrides
    },
    global: {
      stubs: {
        ElDialog: ElDialogStub,
        ElSelect: ElSelectStub,
        ElOption: ElOptionStub,
        ElCheckbox: ElCheckboxStub,
        Button: ButtonStub
      }
    }
  });
}

describe("TestCaseMaintenanceDialog", () => {
  afterEach(() => { document.body.innerHTML = ""; });

  it("displays the complete task type names returned by TCDS", () => {
    const wrapper = mountDialog();
    const optionLabels = wrapper.findAll('select[aria-label="批量任务类型"] option')
      .map((option) => option.text());

    expect(optionLabels).toEqual(["业务风险防控测试任务", "功能测试任务"]);
  });

  it("applies multiple task types to all selected cases before confirming", async () => {
    const wrapper = mountDialog();
    await wrapper.get('select[aria-label="批量任务类型"]').setValue(["2", "3"]);
    await wrapper.findAll("button").find((button) => button.text().includes("批量修改任务类型"))!.trigger("click");
    await wrapper.findAll("button").find((button) => button.text() === "确定")!.trigger("click");

    const submitted = wrapper.emitted("confirm")?.[0]?.[0] as Array<{ name: string; taskTypes: string[] }>;
    expect(submitted.map((row) => row.name)).toEqual(["案例一", "案例二"]);
    expect(submitted.every((row) => new Set(row.taskTypes).size === 2
      && row.taskTypes.includes("2")
      && row.taskTypes.includes("3"))).toBe(true);
  });

  it("does not confirm while any case has no task type", async () => {
    const wrapper = mountDialog();
    await wrapper.findAll("button").find((button) => button.text() === "确定")!.trigger("click");
    expect(wrapper.emitted("confirm")).toBeUndefined();
  });

  it("disables task type actions while loading", () => {
    const wrapper = mountDialog({ taskTypesLoading: true, taskTypeOptions: [] });

    expect(wrapper.get('[role="status"]').text()).toContain("正在加载任务类型");
    expect(wrapper.get('select[aria-label="批量任务类型"]').attributes("disabled")).toBeDefined();
    expect(wrapper.findAll("button").find((button) => button.text() === "确定")!.attributes("disabled")).toBeDefined();
  });

  it("shows task type failure and emits retry", async () => {
    const wrapper = mountDialog({ taskTypesError: "TCDS 任务类型服务暂不可用", taskTypeOptions: [] });

    expect(wrapper.get('[role="alert"]').text()).toContain("TCDS 任务类型服务暂不可用");
    await wrapper.findAll("button").find((button) => button.text() === "重试")!.trigger("click");
    expect(wrapper.emitted("retryTaskTypes")).toHaveLength(1);
  });
});
