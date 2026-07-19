import { describe, expect, it, vi } from "vitest";
import { createBackendApiClient } from "../src";

describe("backend-api night execution", () => {
  it("uses the platform runtime endpoints for slots, create, query and task actions", async () => {
    const task = {
      taskId: "night_1",
      sessionId: "ses_1",
      workspaceId: "wrk_1",
      contentPreview: "执行夜间回归",
      status: "SCHEDULED",
      slotStart: "2026-07-18T13:15:00Z",
      slotEnd: "2026-07-18T13:30:00Z",
      windowEnd: "2026-07-18T23:00:00Z",
      rolloverCount: 0,
      createdAt: "2026-07-18T04:00:00Z",
      updatedAt: "2026-07-18T04:00:00Z"
    };
    const fetcher = vi.fn<typeof fetch>().mockImplementation(async (input) => {
      const url = String(input);
      const data = url.endsWith("/slots")
        ? { timeZone: "Asia/Shanghai", windowStart: task.slotStart, windowEnd: task.windowEnd, capacity: 2, slots: [] }
        : url.includes("/tasks?")
          ? { items: [task], page: 1, size: 20, total: 1, visibleFailure: null }
          : task;
      return new Response(JSON.stringify({ success: true, traceId: "trace_night", data }), { status: 200 });
    });
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await client.getNightExecutionSlots();
    await client.createNightExecutionTask({
      clientRequestId: "task_req_1",
      runClientRequestId: "run_req_1",
      sessionId: "ses_1",
      workspaceId: "wrk_1",
      prompt: "执行夜间回归",
      slotStart: task.slotStart
    });
    await client.listNightExecutionTasks({ sessionId: "ses_1", page: 1, size: 20 });
    await client.adjustNightExecutionTask("night/1", "2026-07-18T13:30:00Z");
    await client.cancelNightExecutionTask("night/1");
    await client.dismissNightExecutionTask("night/1");

    expect(fetcher.mock.calls.map((call) => [call[0], call[1]?.method, call[1]?.body])).toEqual([
      ["http://api/api/internal/platform/opencode-runtime/night-execution/slots", undefined, undefined],
      [
        "http://api/api/internal/platform/opencode-runtime/night-execution/tasks",
        "POST",
        JSON.stringify({
          clientRequestId: "task_req_1",
          runClientRequestId: "run_req_1",
          sessionId: "ses_1",
          workspaceId: "wrk_1",
          prompt: "执行夜间回归",
          slotStart: task.slotStart
        })
      ],
      [
        "http://api/api/internal/platform/opencode-runtime/night-execution/tasks?sessionId=ses_1&page=1&size=20",
        undefined,
        undefined
      ],
      [
        "http://api/api/internal/platform/opencode-runtime/night-execution/tasks/night%2F1",
        "PATCH",
        JSON.stringify({ slotStart: "2026-07-18T13:30:00Z" })
      ],
      ["http://api/api/internal/platform/opencode-runtime/night-execution/tasks/night%2F1/cancel", "POST", undefined],
      ["http://api/api/internal/platform/opencode-runtime/night-execution/tasks/night%2F1/dismiss", "POST", undefined]
    ]);
  });
});
