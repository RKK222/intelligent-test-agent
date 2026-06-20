import { describe, expect, it } from "vitest";
import type { PromptPart } from "@test-agent/shared-types";
import { canStartFollowUp, createFollowUpDraft, dequeueFollowUp, enqueueFollowUp, isRunBusyStatus } from "../src/components/follow-up-queue";

describe("follow-up queue", () => {
  const parts: PromptPart[] = [{ type: "text", text: "next prompt" }];

  it("treats queued, running and cancelling runs as busy", () => {
    expect(isRunBusyStatus("QUEUED")).toBe(true);
    expect(isRunBusyStatus("RUNNING")).toBe(true);
    expect(isRunBusyStatus("CANCELLING")).toBe(true);
    expect(isRunBusyStatus("SUCCEEDED")).toBe(false);
    expect(isRunBusyStatus(undefined)).toBe(false);
  });

  it("keeps follow-up drafts in FIFO order", () => {
    const first = createFollowUpDraft("first", parts, "2026-06-19T00:00:00Z");
    const second = createFollowUpDraft("second", parts, "2026-06-19T00:00:01Z");
    const queue = enqueueFollowUp(enqueueFollowUp([], first), second);

    const one = dequeueFollowUp(queue);
    const two = dequeueFollowUp(one.queue);

    expect(one.next?.prompt).toBe("first");
    expect(two.next?.prompt).toBe("second");
    expect(two.queue).toHaveLength(0);
  });

  it("starts follow-up only when no run or mutation is busy", () => {
    expect(canStartFollowUp({ status: "RUNNING" }, false)).toBe(false);
    expect(canStartFollowUp({ status: "SUCCEEDED" }, true)).toBe(false);
    expect(canStartFollowUp({ status: "SUCCEEDED" }, false)).toBe(true);
    expect(canStartFollowUp(null, false)).toBe(true);
  });
});
