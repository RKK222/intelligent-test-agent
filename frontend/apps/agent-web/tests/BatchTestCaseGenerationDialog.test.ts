import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import BatchTestCaseGenerationDialog from "../src/components/BatchTestCaseGenerationDialog.vue";

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
