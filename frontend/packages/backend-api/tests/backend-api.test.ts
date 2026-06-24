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
      "http://api/api/internal/agent/opencode/runs/run_1",
      expect.objectContaining({
        headers: expect.any(Headers)
      })
    );
    const headers = fetcher.mock.calls[0]?.[1]?.headers as Headers;
    expect(headers.get("X-Trace-Id")).toBe("trace_fixed");
  });

  it("uses a custom agent id for agent-scoped run APIs", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify({ success: true, traceId: "trace_fixed", data: { runId: "run_1" } }), {
        status: 200
      })
    );
    const client = createBackendApiClient({
      baseUrl: "http://api",
      agentId: "OtherAgent",
      fetcher,
      traceIdFactory: () => "trace_fixed"
    });

    await client.startRun("ses_1", "hello");

    expect(fetcher).toHaveBeenCalledWith("http://api/api/internal/agent/otheragent/runs", expect.any(Object));
  });

  it("maps current user opencode process status and initialization through agent-scoped URLs", async () => {
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            success: true,
            traceId: "trace_fixed",
            data: {
              status: "NEEDS_INITIALIZATION",
              initializable: true,
              message: "需要初始化 opencode 进程",
              checkedAt: "2026-06-24T00:00:00Z"
            }
          }),
          { status: 200 }
        )
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            success: true,
            traceId: "trace_fixed",
            data: {
              status: "READY",
              initializable: false,
              message: "opencode 进程可用",
              processId: "ocp_1234567890abcdef",
              linuxServerId: "10.8.0.12",
              containerId: "ctr_01",
              port: 4096,
              baseUrl: "http://10.8.0.12:4096",
              checkedAt: "2026-06-24T00:00:01Z"
            }
          }),
          { status: 200 }
        )
      );
    const client = createBackendApiClient({
      baseUrl: "http://api",
      apiToken: "token_123",
      fetcher,
      traceIdFactory: () => "trace_fixed"
    });

    await expect(client.getMyOpencodeProcess()).resolves.toMatchObject({ status: "NEEDS_INITIALIZATION", initializable: true });
    await expect(client.initializeMyOpencodeProcess()).resolves.toMatchObject({
      status: "READY",
      baseUrl: "http://10.8.0.12:4096"
    });

    expect(fetcher.mock.calls[0]?.[0]).toBe("http://api/api/internal/agent/opencode/processes/me");
    expect(fetcher.mock.calls[1]?.[0]).toBe("http://api/api/internal/agent/opencode/processes/me/initialize");
    expect(fetcher.mock.calls[1]?.[1]).toEqual(expect.objectContaining({ method: "POST" }));
    const headers = fetcher.mock.calls[0]?.[1]?.headers as Headers;
    expect(headers.get("Authorization")).toBe("Bearer token_123");
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

  it("lists selectable workspace directories through the platform API", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          traceId: "trace_fixed",
          data: {
            path: "/Users/huang/workspace",
            parentPath: null,
            entries: [{ name: "demo", path: "/Users/huang/workspace/demo" }]
          }
        }),
        { status: 200 }
      )
    );
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await expect(client.listWorkspaceDirectories("/Users/huang/workspace")).resolves.toEqual({
      path: "/Users/huang/workspace",
      parentPath: null,
      entries: [{ name: "demo", path: "/Users/huang/workspace/demo" }]
    });

    expect(fetcher).toHaveBeenCalledWith("http://api/api/workspace-directories?path=%2FUsers%2Fhuang%2Fworkspace", expect.any(Object));
  });

  it("maps configuration management APIs through platform URLs", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          traceId: "trace_fixed",
          data: [{ appId: "app_gcms", appName: "F-GCMS", enabled: true }]
        }),
        { status: 200 }
      )
    );
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await expect(client.listApplications(true)).resolves.toEqual([{ appId: "app_gcms", appName: "F-GCMS", enabled: true }]);

    expect(fetcher).toHaveBeenCalledWith(
      "http://api/api/internal/platform/configuration-management/applications?enabled=true",
      expect.any(Object)
    );
  });

  it("maps managed workspace APIs through platform URLs", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          traceId: "trace_fixed",
          data: {
            versionId: "awv_1",
            applicationWorkspaceId: "aws_1",
            appId: "app_gcms",
            repositoryId: "repo_1",
            version: "20260707",
            branch: "feature_testagent_20260707",
            repoRootPath: "/data/appworkspace/20260707/repo_1",
            workspaceRootPath: "/data/appworkspace/20260707/repo_1/F-GCMS/workspace",
            runtimeWorkspace: {
              workspaceId: "wks_1",
              name: "F-GCMS-20260707",
              rootPath: "/data/appworkspace/20260707/repo_1/F-GCMS/workspace",
              status: "ACTIVE",
              createdAt: "2026-06-23T00:00:00Z",
              updatedAt: "2026-06-23T00:00:00Z"
            },
            status: "ACTIVE",
            createdAt: "2026-06-23T00:00:00Z",
            updatedAt: "2026-06-23T00:00:00Z"
          }
        }),
        { status: 200 }
      )
    );
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await expect(
      client.createWorkspaceVersion("app_gcms", "aws_1", { version: "20260707", branch: "feature_testagent_20260707" })
    ).resolves.toMatchObject({ versionId: "awv_1", runtimeWorkspace: { workspaceId: "wks_1" } });

    expect(fetcher).toHaveBeenCalledWith(
      "http://api/api/internal/platform/workspace-management/applications/app_gcms/workspace-templates/aws_1/versions",
      expect.objectContaining({ method: "POST" })
    );
    expect(JSON.parse(String(fetcher.mock.calls[0]?.[1]?.body))).toEqual({
      version: "20260707",
      branch: "feature_testagent_20260707"
    });
  });

  it("does not expose SSH private key content from personal key responses", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          traceId: "trace_fixed",
          data: { sshKeyId: "ssh_1", name: "work", fingerprint: "SHA256:abc", createdAt: "2026-06-23T00:00:00Z" }
        }),
        { status: 200 }
      )
    );
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    const response = await client.addPersonalSshKey({ name: "work", privateKey: "-----BEGIN OPENSSH PRIVATE KEY-----" });

    expect(response).toEqual({ sshKeyId: "ssh_1", name: "work", fingerprint: "SHA256:abc", createdAt: "2026-06-23T00:00:00Z" });
    expect(response).not.toHaveProperty("privateKey");
  });

  it("aborts hanging requests and maps them to a timeout error", async () => {
    vi.useFakeTimers();
    let signal: AbortSignal | undefined;
    const fetcher = vi.fn<typeof fetch>(
      (_url, init) =>
        new Promise((_resolve, reject) => {
          signal = init?.signal ?? undefined;
          signal?.addEventListener("abort", () => reject(new DOMException("Aborted", "AbortError")));
        })
    );
    const client = createBackendApiClient({
      baseUrl: "http://api",
      fetcher,
      traceIdFactory: () => "trace_fixed",
      requestTimeoutMs: 10
    });

    try {
      const request = client.listWorkspaceDirectories();
      const expectation = expect(request).rejects.toMatchObject({
        code: "REQUEST_TIMEOUT",
        traceId: "trace_fixed",
        retryable: true
      });
      await vi.advanceTimersByTimeAsync(10);

      expect(signal?.aborted).toBe(true);
      await expectation;
    } finally {
      vi.useRealTimers();
    }
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

    expect(fetcher).toHaveBeenCalledWith("http://api/api/internal/agent/opencode/api/agent?workspaceId=wrk_1234567890abcdef", expect.any(Object));
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
      "http://api/api/internal/agent/opencode/permission/per_123/reply?sessionId=ses_1234567890abcdef",
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

    expect(fetcher).toHaveBeenCalledWith(
      "http://api/api/internal/agent/opencode/experimental/tool/ids?workspaceId=wrk_1234567890abcdef",
      expect.any(Object)
    );
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

  it("persists and reads the (app, workspace) VCS branch preference through the platform API", async () => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            success: true,
            traceId: "trace_fixed",
            data: {
              appId: "app_gcms",
              workspaceId: "wks_123",
              branch: "feature/personalized",
              updatedAt: "2026-06-24T00:00:00Z"
            }
          }),
          { status: 200 }
        )
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            success: true,
            traceId: "trace_fixed",
            data: {
              appId: "app_gcms",
              workspaceId: "wks_123",
              branch: "feature/personalized",
              updatedAt: "2026-06-24T00:00:00Z"
            }
          }),
          { status: 200 }
        )
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ success: true, traceId: "trace_fixed", data: null }), { status: 200 })
      );
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await expect(client.markRecentBranch("app_gcms", "wks_123", "feature/personalized")).resolves.toEqual({
      appId: "app_gcms",
      workspaceId: "wks_123",
      branch: "feature/personalized",
      updatedAt: "2026-06-24T00:00:00Z"
    });
    expect(JSON.parse(String(fetcher.mock.calls[0]?.[1]?.body))).toEqual({ branch: "feature/personalized" });
    expect(fetcher.mock.calls[0]?.[0]).toBe(
      "http://api/api/internal/platform/workspace-management/applications/app_gcms/workspaces/wks_123/branch-preference"
    );

    await expect(client.getRecentBranch("app_gcms", "wks_123")).resolves.toEqual({
      appId: "app_gcms",
      workspaceId: "wks_123",
      branch: "feature/personalized",
      updatedAt: "2026-06-24T00:00:00Z"
    });
    expect(fetcher.mock.calls[1]?.[0]).toBe(
      "http://api/api/internal/platform/workspace-management/applications/app_gcms/workspaces/wks_123/branch-preference"
    );
    // 未设置偏好时返回 null：用于前端"无偏好"分支静默退出
    await expect(client.getRecentBranch("app_gcms", "wks_456")).resolves.toBeNull();
    expect(fetcher.mock.calls[2]?.[0]).toBe(
      "http://api/api/internal/platform/workspace-management/applications/app_gcms/workspaces/wks_456/branch-preference"
    );
  });
});
