import { createPlatformApi } from "@/api/platform";

describe("platform api adapter", () => {
  it("uses same-origin /api paths by default instead of direct opencode server URLs", async () => {
    const calls: string[] = [];
    const api = createPlatformApi({
      fetcher: async (input) => {
        calls.push(String(input));
        return new Response(JSON.stringify({ success: true, traceId: "trace_1", data: { items: [], page: 1, size: 20, total: 0 } }));
      }
    });

    await api.listWorkspaces();

    expect(calls[0]).toBe("/api/workspaces?page=1&size=20");
  });
});
