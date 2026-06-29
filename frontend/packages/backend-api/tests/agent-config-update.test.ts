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
});
