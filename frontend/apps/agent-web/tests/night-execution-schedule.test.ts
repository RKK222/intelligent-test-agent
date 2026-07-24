import { describe, expect, it } from "vitest";
import {
  adminCustomScheduleBounds,
  customScheduleAtOffset,
  formatBeijingDateTimeInput,
  parseBeijingDateTimeInput,
  validateAdminCustomSchedule
} from "../src/utils/night-execution-schedule";

describe("night execution custom schedule time", () => {
  const now = new Date("2026-07-24T03:00:30.500Z");

  it("formats and parses datetime-local values explicitly as Beijing time", () => {
    expect(formatBeijingDateTimeInput(new Date("2026-07-24T06:31:00Z")))
      .toBe("2026-07-24T14:31");
    expect(parseBeijingDateTimeInput("2026-07-24T14:31")?.toISOString())
      .toBe("2026-07-24T06:31:00.000Z");
  });

  it("rounds quick choices from the current minute and clears seconds", () => {
    expect(customScheduleAtOffset(now, 1).toISOString()).toBe("2026-07-24T03:01:00.000Z");
    expect(customScheduleAtOffset(now, 3).toISOString()).toBe("2026-07-24T03:03:00.000Z");
    expect(customScheduleAtOffset(now, 5).toISOString()).toBe("2026-07-24T03:05:00.000Z");
  });

  it("exposes the next whole minute through the last valid minute within 24 hours", () => {
    expect(adminCustomScheduleBounds(now)).toEqual({
      min: "2026-07-24T11:01",
      max: "2026-07-25T11:00"
    });
  });

  it("validates the selected minute and returns an absolute ISO instant", () => {
    expect(validateAdminCustomSchedule("2026-07-24T11:01", now)).toEqual({
      valid: true,
      slotStart: "2026-07-24T03:01:00.000Z"
    });
    expect(validateAdminCustomSchedule("2026-07-24T11:00", now)).toEqual({
      valid: false,
      reason: "请选择下一完整分钟至未来 24 小时内的时间"
    });
    expect(validateAdminCustomSchedule("2026-07-25T11:01", now).valid).toBe(false);
    expect(validateAdminCustomSchedule("2026-02-30T10:00", now)).toEqual({
      valid: false,
      reason: "请输入有效的北京时间"
    });
  });
});
