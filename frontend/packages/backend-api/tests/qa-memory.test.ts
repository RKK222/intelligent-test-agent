import { describe, expect, it, vi } from "vitest";
import { createBackendApiClient } from "../src";

function success(data: unknown) {
  return new Response(JSON.stringify({ success: true, traceId: "trace_memory", data }), { status: 200 });
}

describe("qa memory backend api", () => {
  it("uses the governed personal, team, skill and usage endpoints", async () => {
    const fetcher = vi.fn<typeof fetch>().mockImplementation(async () => success({ items: [], page: 1, size: 100, total: 0 }));
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_memory" });

    await client.listPersonalMemories({ applicationId: "app 1", page: 1, size: 100 });
    await client.createTeamMemoryProposal({ applicationId: "app_1", content: "结论必须有证据", taskTypes: ["DEFECT_ANALYSIS"] });
    await client.reviewTeamMemory("mem_1", { decision: "APPROVE", comment: "证据充分", expectedVersion: 2 });
    await client.queryQaMemoryRunUsage(["run_1", "run_2"]);
    await client.reviewMemorySkillProposal("msp_1", "APPROVE", 3);

    expect(fetcher.mock.calls.map((call) => call[0])).toEqual([
      "http://api/api/internal/platform/qa-memory/v1/personal?applicationId=app+1&page=1&size=100",
      "http://api/api/internal/platform/qa-memory/v1/team/proposals",
      "http://api/api/internal/platform/qa-memory/v1/team/mem_1/reviews",
      "http://api/api/internal/platform/qa-memory/v1/run-usage/query",
      "http://api/api/internal/platform/qa-memory/v1/skill-proposals/msp_1/reviews"
    ]);
    expect(fetcher.mock.calls[3]?.[1]?.body).toBe(JSON.stringify({ runIds: ["run_1", "run_2"] }));
    expect(fetcher.mock.calls[4]?.[1]?.body).toBe(JSON.stringify({ decision: "APPROVE", expectedVersion: 3 }));
  });

  it("keeps admin health, versioned settings and whitelist under system management", async () => {
    const fetcher = vi.fn<typeof fetch>().mockImplementation(async () => success({}));
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_memory" });

    await client.getQaMemoryAdminHealth();
    await client.updateQaMemorySettings({ primaryChatModelId: null, currentRunModelFallbackEnabled: false, expectedVersion: 7 });
    await client.enableQaMemoryUser("usr/a");
    await client.disableQaMemoryUser("usr/a");

    expect(fetcher.mock.calls.map((call) => call[0])).toEqual([
      "http://api/api/internal/platform/system-management/memory/health",
      "http://api/api/internal/platform/system-management/memory/settings",
      "http://api/api/internal/platform/system-management/memory/whitelist",
      "http://api/api/internal/platform/system-management/memory/whitelist/usr%2Fa"
    ]);
    expect(fetcher.mock.calls[1]?.[1]?.body).toBe(JSON.stringify({
      primaryChatModelId: null,
      currentRunModelFallbackEnabled: false,
      expectedVersion: 7
    }));
  });
});
