import { describe, expect, it, vi } from "vitest";
import { createBackendApiClient } from "../src/index";

describe("public agent config update", () => {
  it("sends the optional discard-local-changes flag", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          traceId: "trace_fixed",
          data: { status: "SUCCEEDED" }
        }),
        { status: 200 }
      )
    );
    const client = createBackendApiClient({
      baseUrl: "http://api",
      fetcher,
      traceIdFactory: () => "trace_fixed"
    });

    await client.updatePublicAgentConfig("main", "aco_update", true);

    expect(JSON.parse(String(fetcher.mock.calls[0]?.[1]?.body))).toEqual({
      branch: "main",
      operationId: "aco_update",
      discardLocalChanges: true
    });
  });

  it("sends target server and discard flag when pulling public agent repository", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          traceId: "trace_fixed",
          data: { linuxServerId: "linux-1", status: "READY" }
        }),
        { status: 200 }
      )
    );
    const client = createBackendApiClient({
      baseUrl: "http://api",
      fetcher,
      traceIdFactory: () => "trace_fixed"
    });

    await client.pullPublicAgentRepository("linux-1", "master", "aco_pull", false);

    expect(fetcher).toHaveBeenCalledWith(
      "http://api/api/internal/platform/workspace-management/agent-config/public/repositories/linux-1/pull",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ branch: "master", operationId: "aco_pull", discardLocalChanges: false })
      })
    );
  });
});
