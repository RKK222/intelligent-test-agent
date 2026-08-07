import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import BatchTestCaseGenerationDialog from "../src/components/BatchTestCaseGenerationDialog.vue";
import { batchReferenceTestId } from "../src/components/batch-test-case-generation";

const references = Array.from({ length: 51 }, (_, index) => ({
  id: `spec/需求/01-需求/子条目${index + 1}`,
  requirementName: "需求",
  subitemName: `子条目${index + 1}`,
  filePaths: [`spec/需求/01-需求/子条目${index + 1}/需求.md`]
}));

describe("BatchTestCaseGenerationDialog", () => {
  it("uses the existing # candidates, default requirement and a 70% page dialog", async () => {
    const wrapper = mount(BatchTestCaseGenerationDialog, {
      props: { open: true, references, loading: false }
    });

    const dialog = wrapper.get('[data-testid="batch-test-case-dialog"]');
    expect(dialog.attributes("style")).toContain("width: 70vw");
    expect(dialog.attributes("style")).toContain("height: 70vh");
    expect(wrapper.get('[data-testid="batch-requirement-input"]').element)
      .toHaveProperty("value", "请生成子条目测试案例。");
    expect(wrapper.text()).toContain("子条目1");
    expect(wrapper.emitted("reload-candidates")).toBeTruthy();
  });

  it("limits selection to 50 and emits an immediate batch without editing the composer", async () => {
    const wrapper = mount(BatchTestCaseGenerationDialog, {
      props: { open: true, references, loading: false }
    });

    await wrapper.get('[data-testid="batch-select-all"]').trigger("click");
    expect(wrapper.findAll('input[data-testid="batch-item-checkbox"]:checked')).toHaveLength(50);
    expect(wrapper.text()).toContain("最多选择 50 个子条目");

    await wrapper.get('[data-testid="batch-execute-now"]').trigger("click");
    expect(wrapper.emitted("execute")?.[0]?.[0]).toEqual(expect.objectContaining({
      executionMode: "immediate",
      requirement: "请生成子条目测试案例。",
      referenceIds: references.slice(0, 50).map((item) => item.id)
    }));
    expect(wrapper.find('[data-testid="batch-creation-progress"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="batch-requirement-input"]').exists()).toBe(false);
  });

  it("locks the first submission synchronously and emits only once", async () => {
    const wrapper = mount(BatchTestCaseGenerationDialog, {
      props: { open: true, references: references.slice(0, 2), loading: false }
    });
    await wrapper.findAll('input[data-testid="batch-item-checkbox"]')[0]!.setValue(true);
    await wrapper.findAll('input[data-testid="batch-item-checkbox"]')[1]!.setValue(true);

    const execute = wrapper.get('[data-testid="batch-execute-now"]');
    await execute.trigger("click");
    await execute.trigger("click");

    expect(wrapper.emitted("execute")).toHaveLength(1);
    expect(wrapper.get('[data-testid="batch-creation-progress"]')).toBeTruthy();
    expect(wrapper.get('[data-testid="batch-dialog-close"]').attributes("disabled")).toBeDefined();
  });

  it("returns to the unchanged selection when top-level validation rejects submission", async () => {
    const wrapper = mount(BatchTestCaseGenerationDialog, {
      props: { open: true, references: references.slice(0, 1), loading: false }
    });
    await wrapper.get('input[data-testid="batch-item-checkbox"]').setValue(true);
    await wrapper.get('[data-testid="batch-requirement-input"]').setValue("保留这段要求");
    await wrapper.get('[data-testid="batch-execute-now"]').trigger("click");

    const controls = wrapper.emitted("execute")?.[0]?.[1] as { reject: () => void };
    controls.reject();
    await wrapper.vm.$nextTick();

    expect(wrapper.find('[data-testid="batch-creation-progress"]').exists()).toBe(false);
    expect(wrapper.get('input[data-testid="batch-item-checkbox"]').element).toHaveProperty("checked", true);
    expect(wrapper.get('[data-testid="batch-requirement-input"]').element).toHaveProperty("value", "保留这段要求");
  });

  it("supports single and bulk retry while keeping the progress page open", async () => {
    const selected = references.slice(0, 2);
    const wrapper = mount(BatchTestCaseGenerationDialog, {
      props: { open: true, references: selected, loading: false }
    });
    for (const checkbox of wrapper.findAll('input[data-testid="batch-item-checkbox"]')) {
      await checkbox.setValue(true);
    }
    await wrapper.get('[data-testid="batch-execute-now"]').trigger("click");
    await wrapper.setProps({ running: true });
    await wrapper.setProps({
      running: false,
      itemStates: {
        [selected[0]!.id]: { status: "succeeded", sessionId: "ses_1", runId: "run_1" },
        [selected[1]!.id]: { status: "failed", errorCode: "FILE_READ_FAILED", message: "读取失败" }
      }
    });

    await wrapper.get(`[data-testid="batch-retry-item-${batchReferenceTestId(selected[1]!.id)}"]`).trigger("click");
    expect(wrapper.emitted("execute")?.at(-1)?.[0]).toEqual(expect.objectContaining({
      retry: true,
      referenceIds: [selected[1]!.id]
    }));

    await wrapper.setProps({ running: true });
    await wrapper.setProps({ running: false });
    await wrapper.get('[data-testid="batch-retry-all"]').trigger("click");
    expect(wrapper.emitted("execute")?.at(-1)?.[0]).toEqual(expect.objectContaining({
      retry: true,
      referenceIds: [selected[1]!.id]
    }));
  });

  it("reports missing sessions on close and resets all local state after reopening", async () => {
    const selected = references.slice(0, 2);
    const wrapper = mount(BatchTestCaseGenerationDialog, {
      props: { open: true, references: selected, loading: false }
    });
    await wrapper.findAll('input[data-testid="batch-item-checkbox"]')[0]!.setValue(true);
    await wrapper.findAll('input[data-testid="batch-item-checkbox"]')[1]!.setValue(true);
    await wrapper.get('[data-testid="batch-requirement-input"]').setValue("自定义要求");
    await wrapper.get('[data-testid="batch-execute-now"]').trigger("click");
    await wrapper.setProps({ running: true });
    await wrapper.setProps({
      running: false,
      itemStates: {
        [selected[0]!.id]: { status: "failed", sessionId: "ses_1", message: "启动失败" },
        [selected[1]!.id]: { status: "failed", message: "创建失败" }
      }
    });

    await wrapper.get('[data-testid="batch-dialog-close"]').trigger("click");
    expect(wrapper.emitted("close")?.at(-1)?.[0]).toEqual({ incompleteCount: 1 });

    await wrapper.setProps({ open: false, itemStates: {} });
    await wrapper.setProps({ open: true });
    expect(wrapper.findAll('input[data-testid="batch-item-checkbox"]:checked')).toHaveLength(0);
    expect(wrapper.get('[data-testid="batch-requirement-input"]').element)
      .toHaveProperty("value", "请生成子条目测试案例。");
  });

  it("requires a fresh time allocation only for scheduled capacity conflicts", async () => {
    const selected = references.slice(0, 2);
    const nightSlots = {
      timeZone: "Asia/Shanghai",
      windowStart: "2026-08-07T13:00:00Z",
      windowEnd: "2026-08-07T23:00:00Z",
      capacity: 2,
      slots: [
        { slotStart: "2026-08-07T13:00:00Z", slotEnd: "2026-08-07T13:15:00Z", reservedCount: 0, capacity: 2, available: true, recommended: false }
      ]
    };
    const wrapper = mount(BatchTestCaseGenerationDialog, {
      props: { open: true, references: selected, loading: false, nightSlots }
    });
    for (const checkbox of wrapper.findAll('input[data-testid="batch-item-checkbox"]')) {
      await checkbox.setValue(true);
    }
    await wrapper.get('[data-testid="batch-open-schedule"]').trigger("click");
    await wrapper.get('[data-testid="batch-night-slot"]').trigger("click");
    await wrapper.get('[data-testid="batch-execute-scheduled"]').trigger("click");
    await wrapper.setProps({ running: true });
    await wrapper.setProps({
      running: false,
      itemStates: {
        [selected[0]!.id]: { status: "failed", errorCode: "SLOT_CAPACITY_CONFLICT", message: "容量冲突" },
        [selected[1]!.id]: { status: "failed", errorCode: "BATCH_ITEM_FAILED", message: "普通失败" }
      }
    });

    expect(wrapper.find('[data-testid="batch-retry-schedule"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="batch-retry-all"]').attributes("disabled")).toBeDefined();
    await wrapper.get('[data-testid="batch-night-slot"]').trigger("click");
    expect(wrapper.get('[data-testid="batch-retry-all"]').attributes("disabled")).toBeUndefined();
    await wrapper.get('[data-testid="batch-retry-all"]').trigger("click");

    expect(wrapper.emitted("execute")?.at(-1)?.[0]).toEqual(expect.objectContaining({
      retry: true,
      referenceIds: selected.map((item) => item.id),
      slotStarts: ["2026-08-07T13:00:00Z"]
    }));
  });

  it("supports multiple night slots and disables closing while running", async () => {
    const wrapper = mount(BatchTestCaseGenerationDialog, {
      props: {
        open: true,
        references: references.slice(0, 2),
        loading: false,
        running: true,
        nightSlots: {
          timeZone: "Asia/Shanghai",
          windowStart: "2026-08-07T13:00:00Z",
          windowEnd: "2026-08-07T23:00:00Z",
          capacity: 2,
          slots: [
            { slotStart: "2026-08-07T13:00:00Z", slotEnd: "2026-08-07T13:15:00Z", reservedCount: 0, capacity: 1, available: true, recommended: false },
            { slotStart: "2026-08-07T13:15:00Z", slotEnd: "2026-08-07T13:30:00Z", reservedCount: 0, capacity: 1, available: true, recommended: false }
          ]
        }
      }
    });

    expect(wrapper.get('[data-testid="batch-dialog-close"]').attributes("disabled")).toBeDefined();
    await wrapper.setProps({ running: false });
    await wrapper.findAll('input[data-testid="batch-item-checkbox"]')[0]!.setValue(true);
    await wrapper.findAll('input[data-testid="batch-item-checkbox"]')[1]!.setValue(true);
    await wrapper.get('[data-testid="batch-open-schedule"]').trigger("click");
    const slots = wrapper.findAll('[data-testid="batch-night-slot"]');
    await slots[0]!.trigger("click");
    await slots[1]!.trigger("click");
    await wrapper.get('[data-testid="batch-execute-scheduled"]').trigger("click");

    expect(wrapper.emitted("execute")?.at(-1)?.[0]).toEqual(expect.objectContaining({
      executionMode: "scheduled",
      scheduleMode: "NIGHT_WINDOW",
      slotStarts: ["2026-08-07T13:00:00Z", "2026-08-07T13:15:00Z"]
    }));
  });
});
