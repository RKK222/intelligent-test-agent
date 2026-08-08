import { describe, expect, it } from "vitest";
import type { WorkspaceRequirementReference } from "../src/components/workbench-utils";
import {
  BatchItemPreparationError,
  allocateBatchSchedule,
  buildBatchItemRunInput,
  mapWithConcurrency,
  recommendBatchScheduleTimes
} from "../src/components/batch-test-case-generation";

const reference: WorkspaceRequirementReference = {
  id: "spec/需求一/01-需求/登录",
  requirementName: "需求一",
  subitemName: "登录",
  filePaths: [
    "spec/需求一/01-需求/登录/需求.md",
    "spec/需求一/02-设计/登录/设计.md",
    "spec/需求一/03-编码/登录/实现.ts",
    "spec/需求一/04-测试/登录/用例.md"
  ]
};

describe("batch test case generation", () => {
  it("builds isolated prompt parts in the existing requirement path order", async () => {
    const result = await buildBatchItemRunInput({
      reference,
      requirement: "请生成子条目测试案例。",
      readFile: async (path) => ({ content: `正文:${path}` })
    });

    expect(result.prompt).toBe("请生成子条目测试案例。\n\n需求项：需求一\n子条目：登录");
    expect(result.parts[0]).toEqual({ type: "text", text: result.prompt });
    expect(result.parts.slice(1).map((part) => part.type === "file" ? part.path : undefined))
      .toEqual(reference.filePaths);
  });

  it("skips binary files but rejects unreadable or oversized local contexts", async () => {
    const binarySkipped = await buildBatchItemRunInput({
      reference: { ...reference, filePaths: ["binary.dat", "readable.md"] },
      requirement: "生成测试",
      readFile: async (path) => ({ content: path === "binary.dat" ? "\u0000binary" : "正文" })
    });
    expect(binarySkipped.parts).toHaveLength(2);

    await expect(buildBatchItemRunInput({
      reference: { ...reference, filePaths: ["huge.md"] },
      requirement: "生成测试",
      readFile: async () => ({ content: "x".repeat(80_001) })
    })).rejects.toEqual(expect.objectContaining<Partial<BatchItemPreparationError>>({ code: "FILE_TOO_LARGE" }));

    await expect(buildBatchItemRunInput({
      reference: { ...reference, filePaths: ["broken.md"] },
      requirement: "生成测试",
      readFile: async () => { throw new Error("read failed"); }
    })).rejects.toEqual(expect.objectContaining<Partial<BatchItemPreparationError>>({ code: "FILE_READ_FAILED" }));
  });

  it("allocates night capacity in ascending round-robin order", () => {
    const allocation = allocateBatchSchedule({
      itemCount: 5,
      scheduleMode: "NIGHT_WINDOW",
      selectedTimes: ["2026-08-07T14:15:00Z", "2026-08-07T14:00:00Z"],
      slots: [
        { slotStart: "2026-08-07T14:00:00Z", slotEnd: "2026-08-07T14:15:00Z", reservedCount: 0, capacity: 2, available: true, recommended: false },
        { slotStart: "2026-08-07T14:15:00Z", slotEnd: "2026-08-07T14:30:00Z", reservedCount: 0, capacity: 3, available: true, recommended: false }
      ]
    });

    expect(allocation).toEqual({
      ok: true,
      times: [
        "2026-08-07T14:00:00Z",
        "2026-08-07T14:15:00Z",
        "2026-08-07T14:00:00Z",
        "2026-08-07T14:15:00Z",
        "2026-08-07T14:15:00Z"
      ]
    });
  });

  it("reports insufficient night capacity and caps orchestration at four workers", async () => {
    expect(allocateBatchSchedule({
      itemCount: 2,
      scheduleMode: "NIGHT_WINDOW",
      selectedTimes: ["2026-08-07T14:00:00Z"],
      slots: [
        { slotStart: "2026-08-07T14:00:00Z", slotEnd: "2026-08-07T14:15:00Z", reservedCount: 1, capacity: 2, available: true, recommended: false }
      ]
    })).toEqual({ ok: false, remainingCapacity: 1 });

    let active = 0;
    let peak = 0;
    const values = await mapWithConcurrency([1, 2, 3, 4, 5, 6], 4, async (value) => {
      active += 1;
      peak = Math.max(peak, active);
      await Promise.resolve();
      active -= 1;
      return value * 2;
    });
    expect(peak).toBe(4);
    expect(values).toEqual([2, 4, 6, 8, 10, 12]);
  });

  it("recommends the least-queued slot that covers the selected item count", () => {
    // 统一容量下“已排队最少”即“余量最多”，单个时段足以覆盖时只推荐该时段。
    expect(recommendBatchScheduleTimes({
      itemCount: 1,
      slots: [
        { slotStart: "2026-08-07T14:00:00Z", slotEnd: "2026-08-07T14:15:00Z", reservedCount: 2, capacity: 2, available: true, recommended: false },
        { slotStart: "2026-08-07T14:15:00Z", slotEnd: "2026-08-07T14:30:00Z", reservedCount: 0, capacity: 2, available: true, recommended: true }
      ]
    })).toEqual(["2026-08-07T14:15:00Z"]);
  });

  it("accumulates multiple least-queued slots until capacity covers the item count", () => {
    // 单时段余量不足时按已排队数升序补齐，并按时间升序返回。
    expect(recommendBatchScheduleTimes({
      itemCount: 5,
      slots: [
        { slotStart: "2026-08-07T14:15:00Z", slotEnd: "2026-08-07T14:30:00Z", reservedCount: 1, capacity: 2, available: true, recommended: false },
        { slotStart: "2026-08-07T14:00:00Z", slotEnd: "2026-08-07T14:15:00Z", reservedCount: 0, capacity: 2, available: true, recommended: true },
        { slotStart: "2026-08-07T14:30:00Z", slotEnd: "2026-08-07T14:45:00Z", reservedCount: 3, capacity: 2, available: true, recommended: false }
      ]
    })).toEqual(["2026-08-07T14:00:00Z", "2026-08-07T14:15:00Z"]);
  });

  it("returns all available slots when total capacity cannot cover the item count", () => {
    expect(recommendBatchScheduleTimes({
      itemCount: 10,
      slots: [
        { slotStart: "2026-08-07T14:00:00Z", slotEnd: "2026-08-07T14:15:00Z", reservedCount: 1, capacity: 2, available: true, recommended: false },
        { slotStart: "2026-08-07T14:15:00Z", slotEnd: "2026-08-07T14:30:00Z", reservedCount: 0, capacity: 2, available: true, recommended: true }
      ]
    })).toEqual(["2026-08-07T14:00:00Z", "2026-08-07T14:15:00Z"]);
  });

  it("ignores full or unavailable slots and returns nothing without slots or items", () => {
    expect(recommendBatchScheduleTimes({
      itemCount: 1,
      slots: [
        { slotStart: "2026-08-07T14:00:00Z", slotEnd: "2026-08-07T14:15:00Z", reservedCount: 2, capacity: 2, available: false, recommended: false }
      ]
    })).toEqual([]);
    expect(recommendBatchScheduleTimes({ itemCount: 3, slots: [] })).toEqual([]);
    expect(recommendBatchScheduleTimes({ itemCount: 0, slots: [
      { slotStart: "2026-08-07T14:00:00Z", slotEnd: "2026-08-07T14:15:00Z", reservedCount: 0, capacity: 2, available: true, recommended: true }
    ] })).toEqual([]);
  });
});
