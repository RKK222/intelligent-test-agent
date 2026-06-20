import { describe, expect, it, vi } from "vitest";
import { BackendApiError, createBackendApiClient } from "../src";

describe("backend-api", () => {
  it("sends trace id and unwraps successful responses", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify({ success: true, traceId: "trace_fixed", data: { runId: "run_1" } }), {
        status: 200
      })
    );
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await expect(client.getRun("run_1")).resolves.toEqual({ runId: "run_1" });

    expect(fetcher).toHaveBeenCalledWith(
      "http://api/api/runs/run_1",
      expect.objectContaining({
        headers: expect.any(Headers)
      })
    );
    const headers = fetcher.mock.calls[0]?.[1]?.headers as Headers;
    expect(headers.get("X-Trace-Id")).toBe("trace_fixed");
  });

  it("maps unified error responses to BackendApiError with trace id", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: false,
          code: "CONFLICT",
          message: "缺少 opencode messageID",
          traceId: "trace_server",
          details: { runId: "run_1" }
        }),
        { status: 409 }
      )
    );
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await expect(client.rejectRunDiff("run_1")).rejects.toMatchObject({
      code: "CONFLICT",
      traceId: "trace_server",
      retryable: false
    });
  });

  it("sends Phase 11 startRun payload while keeping the old prompt field", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          traceId: "trace_fixed",
          data: { runId: "run_1", sessionId: "ses_1", workspaceId: "wrk_1", status: "RUNNING" }
        }),
        { status: 200 }
      )
    );
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await client.startRun({
      sessionId: "ses_1",
      prompt: "fallback",
      parts: [{ type: "text", text: "hello" }],
      messageId: "msg_1",
      agent: "build",
      model: "anthropic/claude-sonnet-4-5",
      variant: "default",
      mode: "build"
    });

    expect(JSON.parse(String(fetcher.mock.calls[0]?.[1]?.body))).toEqual({
      sessionId: "ses_1",
      prompt: "fallback",
      parts: [{ type: "text", text: "hello" }],
      messageId: "msg_1",
      agent: "build",
      model: "anthropic/claude-sonnet-4-5",
      variant: "default",
      mode: "build"
    });
  });

  it("lists Phase 11 runtime agents through the platform API and maps stable fields", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          traceId: "trace_fixed",
          data: { location: { directory: "/tmp/demo" }, data: [{ id: "build", name: "Build", description: "Run tests" }] }
        }),
        { status: 200 }
      )
    );
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await expect(client.listAgents("wrk_1234567890abcdef")).resolves.toEqual([
      { agentId: "build", name: "Build", description: "Run tests" }
    ]);

    expect(fetcher).toHaveBeenCalledWith("http://api/api/agents?workspaceId=wrk_1234567890abcdef", expect.any(Object));
  });

  it("preserves command catalog source and hints for slash parameter forms", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          traceId: "trace_fixed",
          data: { data: [{ name: "review", description: "Review changes", source: "command", hints: ["$ARGUMENTS"] }] }
        }),
        { status: 200 }
      )
    );
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await expect(client.listCommands("wrk_1234567890abcdef")).resolves.toEqual([
      {
        commandId: "review",
        name: "review",
        description: "Review changes",
        source: "command",
        hints: ["$ARGUMENTS"]
      }
    ]);
  });

  it("maps session todo items from opencode runtime payloads", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          traceId: "trace_fixed",
          data: [{ id: "todo_1", content: "Fix checkout test", status: "in_progress", priority: "high" }]
        }),
        { status: 200 }
      )
    );
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await expect(client.getSessionTodo("ses_1234567890abcdef")).resolves.toEqual([
      { id: "todo_1", text: "Fix checkout test", status: "in_progress", priority: "high" }
    ]);
  });

  it("replies to session permission requests without exposing opencode URLs", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify({ success: true, traceId: "trace_fixed", data: { accepted: true } }), { status: 200 })
    );
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await client.replySessionPermission("ses_1234567890abcdef", "per_123", { decision: "once" });

    expect(fetcher).toHaveBeenCalledWith(
      "http://api/api/sessions/ses_1234567890abcdef/permissions/per_123/reply",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ decision: "once" })
      })
    );
  });

  it("maps MCP tool ids from the platform runtime API", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify({ success: true, traceId: "trace_fixed", data: ["bash", "read"] }), { status: 200 })
    );
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await expect(client.getMcpTools("wrk_1234567890abcdef")).resolves.toEqual([
      { toolId: "bash", name: "bash" },
      { toolId: "read", name: "read" }
    ]);

    expect(fetcher).toHaveBeenCalledWith("http://api/api/mcp/tools?workspaceId=wrk_1234567890abcdef", expect.any(Object));
  });

  it("reads session messages for readonly transcript pages", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          traceId: "trace_fixed",
          data: { items: [{ messageId: "msg_1", sessionId: "ses_1", role: "USER", content: "hello" }], page: 1, size: 100, total: 1 }
        }),
        { status: 200 }
      )
    );
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await expect(client.listSessionMessages("ses_1", 1, 100)).resolves.toMatchObject({
      items: [{ messageId: "msg_1", content: "hello" }]
    });

    expect(fetcher).toHaveBeenCalledWith("http://api/api/sessions/ses_1/messages?page=1&size=100", expect.any(Object));
  });

  it("uses platform session management APIs without direct opencode URLs", async () => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            success: true,
            traceId: "trace_fixed",
            data: { items: [{ sessionId: "ses_1", title: "Pinned", pinned: true }], page: 1, size: 20, total: 1 }
          }),
          { status: 200 }
        )
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({ success: true, traceId: "trace_fixed", data: { sessionId: "ses_1", title: "Renamed", pinned: false } }),
          { status: 200 }
        )
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ success: true, traceId: "trace_fixed", data: { sessionId: "ses_1", status: "ARCHIVED" } }), {
          status: 200
        })
      );
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await expect(client.listAllSessions(1, 20, "pin")).resolves.toMatchObject({
      items: [{ sessionId: "ses_1", pinned: true }]
    });
    await client.updateSession("ses_1", { title: "Renamed", pinned: false });
    await client.deleteSession("ses_1");

    expect(fetcher.mock.calls[0]?.[0]).toBe("http://api/api/sessions?page=1&size=20&q=pin");
    expect(fetcher.mock.calls[1]?.[0]).toBe("http://api/api/sessions/ses_1");
    expect(fetcher.mock.calls[1]?.[1]).toEqual(expect.objectContaining({ method: "PATCH", body: JSON.stringify({ title: "Renamed", pinned: false }) }));
    expect(fetcher.mock.calls[2]?.[0]).toBe("http://api/api/sessions/ses_1");
    expect(fetcher.mock.calls[2]?.[1]).toEqual(expect.objectContaining({ method: "DELETE" }));
  });

  it("maps VCS diff files from runtime envelopes", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          traceId: "trace_fixed",
          data: { data: [{ file: "src/app.ts", diff: "@@ -1 +1 @@\n-old\n+new", additions: 1, deletions: 1 }] }
        }),
        { status: 200 }
      )
    );
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await expect(client.getVcsDiffFiles("wrk_1234567890abcdef")).resolves.toEqual({
      files: [{ path: "src/app.ts", patch: "@@ -1 +1 @@\n-old\n+new", additions: 1, deletions: 1, status: "modified" }]
    });
  });

  it("creates terminal tickets through the platform API only", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          traceId: "trace_fixed",
          data: {
            ticket: "pty_123",
            expiresAt: "2026-06-19T13:00:00Z",
            webSocketUrl: "/api/sessions/ses_1234567890abcdef/terminal/ws?ticket=pty_123"
          }
        }),
        { status: 200 }
      )
    );
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await expect(
      client.createTerminalTicket("ses_1234567890abcdef", {
        workspaceId: "wrk_1234567890abcdef",
        cwd: "packages/app",
        cols: 120,
        rows: 32
      })
    ).resolves.toEqual({
      ticket: "pty_123",
      expiresAt: "2026-06-19T13:00:00Z",
      webSocketUrl: "/api/sessions/ses_1234567890abcdef/terminal/ws?ticket=pty_123"
    });

    expect(fetcher).toHaveBeenCalledWith(
      "http://api/api/sessions/ses_1234567890abcdef/terminal/tickets",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ workspaceId: "wrk_1234567890abcdef", cwd: "packages/app", cols: 120, rows: 32 })
      })
    );
  });
});
