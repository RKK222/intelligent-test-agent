import { describe, expect, it, vi } from "vitest";
import {
  BackendApiError,
  createBackendApiClient,
  LINUX_SERVER_ROUTE_HEADER,
  type ReferenceRepositoryStatus,
  type WorkspaceWebSocketFactory
} from "../src";

describe("backend-api", () => {
  it("filters OpenCode Zen from model and provider catalogs using the configured provider allowlist", async () => {
    const fetcher = vi.fn<typeof fetch>().mockImplementation(async (input) => {
      const url = String(input);
      const data = url.endsWith("/config")
        ? { enabled_providers: ["enterprise-qwen"] }
        : url.endsWith("/models")
          ? [
              { id: "ring-2.6-1t-free", providerID: "opencode", name: "Ring 2.6 1T Free" },
              { id: "Qwen3.6-27B", providerID: "enterprise-qwen", name: "Qwen3.6 27B" }
            ]
          : [
              { id: "opencode", name: "OpenCode Zen", models: { "ring-2.6-1t-free": { name: "Ring 2.6 1T Free" } } },
              { id: "enterprise-qwen", name: "企业通义", models: { "Qwen3.6-27B": { name: "Qwen3.6 27B" } } }
            ];
      return new Response(JSON.stringify({ success: true, traceId: "trace_fixed", data }), { status: 200 });
    });
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    const [models, providers] = await Promise.all([client.listModels(), client.listProviders()]);

    expect(models).toEqual([
      expect.objectContaining({ id: "Qwen3.6-27B", providerId: "enterprise-qwen" })
    ]);
    expect(providers).toEqual([
      expect.objectContaining({ providerId: "enterprise-qwen", name: "企业通义" })
    ]);
    expect(fetcher.mock.calls.filter((call) => String(call[0]).endsWith("/config"))).toHaveLength(1);
  });

  it("keeps the native catalog when enabled providers are not configured", async () => {
    const fetcher = vi.fn<typeof fetch>().mockImplementation(async (input) => {
      const data = String(input).endsWith("/config")
        ? {}
        : [{ id: "ring-2.6-1t-free", providerID: "opencode", name: "Ring 2.6 1T Free" }];
      return new Response(JSON.stringify({ success: true, traceId: "trace_fixed", data }), { status: 200 });
    });
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await expect(client.listModels()).resolves.toEqual([
      expect.objectContaining({ id: "ring-2.6-1t-free", providerId: "opencode" })
    ]);
  });

  it("maps OpenCode V2 model limits and provider all envelopes", async () => {
    const fetcher = vi.fn<typeof fetch>().mockImplementation(async (input) => {
      const path = String(input);
      const data = path.endsWith("/config")
        ? { enabled_providers: ["anthropic"] }
        : path.includes("/models")
          ? [{ id: "claude-sonnet", providerID: "anthropic", name: "Claude Sonnet", limit: { context: 200_000, output: 64_000 } }]
          : {
              all: [{ id: "anthropic", name: "Anthropic", models: {
                "claude-sonnet": { name: "Claude Sonnet", limit: { context: 200_000, output: 64_000 } }
              } }],
              connected: ["anthropic"]
            };
      return new Response(JSON.stringify({ success: true, traceId: "trace_fixed", data }), { status: 200 });
    });
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await expect(client.listModels("wrk_1")).resolves.toEqual([
      expect.objectContaining({
        id: "claude-sonnet",
        providerId: "anthropic",
        contextLimit: 200_000,
        outputLimit: 64_000
      })
    ]);
    await expect(client.listProviders("wrk_1")).resolves.toEqual([
      expect.objectContaining({
        providerId: "anthropic",
        name: "Anthropic",
        models: [expect.objectContaining({ id: "claude-sonnet", contextLimit: 200_000, outputLimit: 64_000 })]
      })
    ]);
  });

  it("keeps an explicitly empty Vite API base URL for same-origin deployment", async () => {
    vi.stubEnv("VITE_TEST_AGENT_API_BASE_URL", "");
    try {
      const fetcher = vi.fn<typeof fetch>().mockImplementation(async () =>
        new Response(JSON.stringify({ success: true, traceId: "trace_fixed", data: [] }), { status: 200 })
      );
      const client = createBackendApiClient({ fetcher, traceIdFactory: () => "trace_fixed" });

      await client.listCommonParameterMemoryValues();

      expect(fetcher.mock.calls[0]?.[0]).toBe(
        "/api/internal/platform/configuration-management/common-parameters/memory-values"
      );
    } finally {
      vi.unstubAllEnvs();
    }
  });

  it("uses the additive common parameter memory query and refresh endpoints", async () => {
    const fetcher = vi.fn<typeof fetch>().mockImplementation(async () =>
      new Response(JSON.stringify({ success: true, traceId: "trace_fixed", data: {} }), { status: 200 })
    );
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await client.listCommonParameterMemoryValues();
    await client.getCommonParameterMemoryValues("bjp_target/backend");
    await client.refreshCommonParameterMemoryValues();
    await client.refreshCommonParameterMemoryValuesForProcess("bjp_target/backend");

    expect(fetcher.mock.calls.map((call) => [call[0], call[1]?.method])).toEqual([
      ["http://api/api/internal/platform/configuration-management/common-parameters/memory-values", undefined],
      ["http://api/api/internal/platform/configuration-management/common-parameters/memory-values/bjp_target%2Fbackend", undefined],
      ["http://api/api/internal/platform/configuration-management/common-parameters/memory-values/refresh", "POST"],
      ["http://api/api/internal/platform/configuration-management/common-parameters/memory-values/bjp_target%2Fbackend/refresh", "POST"]
    ]);
  });

  it("uses the reusable internal model Token management endpoints", async () => {
    const fetcher = vi.fn<typeof fetch>().mockImplementation(async () =>
      new Response(JSON.stringify({ success: true, traceId: "trace_fixed", data: [] }), { status: 200 })
    );
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await client.listInternalModelTokens();
    await client.createInternalModelToken({ name: "Qwen Token", token: "external-create-secret" });
    await client.updateInternalModelToken(17, { name: "Qwen Token 2", token: "" });
    await client.deleteInternalModelToken(17);

    expect(fetcher.mock.calls.map((call) => [call[0], call[1]?.method, call[1]?.body])).toEqual([
      ["http://api/api/internal/platform/configuration-management/internal-model-tokens", undefined, undefined],
      [
        "http://api/api/internal/platform/configuration-management/internal-model-tokens",
        "POST",
        JSON.stringify({ name: "Qwen Token", token: "external-create-secret" })
      ],
      [
        "http://api/api/internal/platform/configuration-management/internal-model-tokens/17",
        "PATCH",
        JSON.stringify({ name: "Qwen Token 2", token: "" })
      ],
      [
        "http://api/api/internal/platform/configuration-management/internal-model-tokens/17",
        "DELETE",
        undefined
      ]
    ]);
  });

  it("calls every application reference repository endpoint with the current app id", async () => {
    const status: ReferenceRepositoryStatus = {
      repositoryId: "repo/assets",
      name: "需求资产库",
      englishName: "requirements",
      gitUrl: "ssh://git.example.test/requirements.git",
      repositoryPath: "/data/.testagent/agent-opencode/references/requirements",
      initialized: true,
      branch: "main",
      targetCommitHash: "abc123",
      generation: 2,
      status: "READY",
      operation: "VERIFY_POINTERS",
      targetServerCount: 1,
      readyServerCount: 1,
      servers: [{
        linuxServerId: "linux-a",
        status: "READY",
        currentBranch: "main",
        currentCommitHash: "abc123",
        online: true,
        matchesTarget: true,
        verifiedAt: "2026-07-18T10:00:00Z",
        syncedAt: "2026-07-18T09:59:00Z",
        error: null
      }],
      traceId: "trace_backend",
      message: null
    };
    const fetcher = vi.fn<typeof fetch>().mockImplementation(async (input) => {
      const path = String(input);
      const data = path.endsWith("/tree?path=docs%2Fapi")
        ? [{ path: "docs/api/openapi.yaml", name: "openapi.yaml", directory: false, size: 42, highlighted: false, selectable: false }]
        : path.endsWith("/reference-repositories")
          ? [status]
          : status;
      return new Response(JSON.stringify({ success: true, traceId: "trace_backend", data }), { status: 200 });
    });
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await client.listReferenceRepositories("app/demo");
    await client.initializeReferenceRepository("app/demo", "repo/assets", "feature/docs");
    await client.synchronizeReferenceRepository("app/demo", "repo/assets");
    await client.switchReferenceRepositoryBranch("app/demo", "repo/assets", "release/2026");
    await client.verifyReferenceRepositoryPointers("app/demo", "repo/assets");
    await expect(client.getReferenceRepositoryStatus("app/demo", "repo/assets")).resolves.toEqual(
      expect.objectContaining({
        repositoryPath: "/data/.testagent/agent-opencode/references/requirements",
        operation: "VERIFY_POINTERS",
        servers: [expect.objectContaining({
          online: true,
          matchesTarget: true,
          currentBranch: "main",
          currentCommitHash: "abc123",
          verifiedAt: "2026-07-18T10:00:00Z",
          syncedAt: "2026-07-18T09:59:00Z"
        })]
      })
    );
    await expect(client.listReferenceRepositoryTree("app/demo", "repo/assets", "docs/api")).resolves.toEqual([
      expect.objectContaining({ path: "docs/api/openapi.yaml", directory: false })
    ]);

    expect(fetcher.mock.calls.map((call) => [call[0], call[1]?.method, call[1]?.body])).toEqual([
      ["http://api/api/internal/platform/workspace-management/applications/app%2Fdemo/reference-repositories", undefined, undefined],
      [
        "http://api/api/internal/platform/workspace-management/applications/app%2Fdemo/reference-repositories/repo%2Fassets/initialize",
        "POST",
        JSON.stringify({ branch: "feature/docs" })
      ],
      [
        "http://api/api/internal/platform/workspace-management/applications/app%2Fdemo/reference-repositories/repo%2Fassets/synchronize",
        "POST",
        undefined
      ],
      [
        "http://api/api/internal/platform/workspace-management/applications/app%2Fdemo/reference-repositories/repo%2Fassets/switch-branch",
        "POST",
        JSON.stringify({ branch: "release/2026" })
      ],
      [
        "http://api/api/internal/platform/workspace-management/applications/app%2Fdemo/reference-repositories/repo%2Fassets/verify",
        "POST",
        undefined
      ],
      [
        "http://api/api/internal/platform/workspace-management/applications/app%2Fdemo/reference-repositories/repo%2Fassets/status",
        undefined,
        undefined
      ],
      [
        "http://api/api/internal/platform/workspace-management/applications/app%2Fdemo/reference-repositories/repo%2Fassets/tree?path=docs%2Fapi",
        undefined,
        undefined
      ]
    ]);
  });

  it("normalizes a pending native OpenCode question for a historical platform session", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          traceId: "trace_fixed",
          data: [
            {
              id: "que_history",
              // OpenCode 返回的是远端 sessionID；前端请求和展示使用平台 sessionId，二者会不同。
              sessionID: "ses_remote_history",
              questions: [
                {
                  question: "请选择验证范围",
                  header: "验证范围",
                  options: [{ label: "接口测试", description: "只执行接口回归" }],
                  multiple: true,
                  custom: true
                }
              ]
            }
          ]
        }),
        { status: 200 }
      )
    );
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await expect(client.listSessionQuestions("ses_history")).resolves.toEqual([
      {
        requestId: "que_history",
        sessionId: "ses_history",
        questions: [
          {
            questionId: "que_history:0",
            text: "请选择验证范围",
            header: "验证范围",
            kind: "multiple",
            options: [{ id: "接口测试", label: "接口测试", description: "只执行接口回归" }],
            custom: true,
            required: undefined
          }
        ],
        createdAt: expect.any(String)
      }
    ]);
  });

  it("sends trace id and unwraps successful responses", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify({
        success: true,
        traceId: "trace_fixed",
        data: {
          runId: "run_1",
          storageMode: "REDIS_SUMMARY",
          clientRequestId: "req_1",
          detailsAvailableUntil: "2026-07-11T00:00:00Z"
        }
      }), { status: 200 })
    );
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await expect(client.getRun("run_1")).resolves.toEqual({
      runId: "run_1",
      storageMode: "REDIS_SUMMARY",
      clientRequestId: "req_1",
      detailsAvailableUntil: "2026-07-11T00:00:00Z"
    });

    expect(fetcher).toHaveBeenCalledWith(
      "http://api/api/internal/agent/opencode/runs/run_1",
      expect.objectContaining({
        headers: expect.any(Headers)
      })
    );
    const headers = fetcher.mock.calls[0]?.[1]?.headers as Headers;
    expect(headers.get("X-Trace-Id")).toBe("trace_fixed");
  });

  it("adds the in-memory linux server hint only to routed requests", async () => {
    let linuxServerId = "";
    const fetcher = vi.fn<typeof fetch>().mockImplementation(async () =>
      new Response(JSON.stringify({ success: true, traceId: "trace_fixed", data: {} }), { status: 200 })
    );
    const client = createBackendApiClient({
      baseUrl: "http://api",
      fetcher,
      traceIdFactory: () => "trace_fixed",
      routeLinuxServerId: () => linuxServerId
    });

    await client.getMyOpencodeProcess();
    linuxServerId = " server-a ";
    await client.createSession("wrk_1", "新会话");
    await client.startRun("ses_1", "hello");
    await client.stageWorkspaceGitFiles("wrk_1", ["README.md"]);
    await client.listApplications();
    linuxServerId = "server-b";
    await client.getMyOpencodeProcessHealth({ linuxServerId: "server-b", containerId: "ctr_1", port: 4096 });

    const routeHeaders = fetcher.mock.calls.map((call) =>
      (call[1]?.headers as Headers).get(LINUX_SERVER_ROUTE_HEADER)
    );
    expect(routeHeaders).toEqual([null, "server-a", "server-a", "server-a", null, "server-b"]);
  });

  it("stages and unstages workspace files through platform git endpoints", async () => {
    const fetcher = vi.fn<typeof fetch>().mockImplementation(async () =>
      new Response(JSON.stringify({ success: true, traceId: "trace_fixed", data: null }), {
        status: 200
      })
    );
    const client = createBackendApiClient({
      baseUrl: "http://api",
      fetcher,
      traceIdFactory: () => "trace_fixed"
    });

    await client.stageWorkspaceGitFiles("wrk_123", ["src/Changed.java"]);
    await client.unstageWorkspaceGitFiles("wrk_123", ["src/Changed.java"]);

    expect(fetcher.mock.calls.map((call) => [call[0], call[1]?.method, call[1]?.body])).toEqual([
      [
        "http://api/api/internal/platform/workspace-management/workspaces/wrk_123/git-stage",
        "POST",
        JSON.stringify({ files: ["src/Changed.java"] })
      ],
      [
        "http://api/api/internal/platform/workspace-management/workspaces/wrk_123/git-unstage",
        "POST",
        JSON.stringify({ files: ["src/Changed.java"] })
      ]
    ]);
  });

  it("updates a managed user's role through system management endpoint", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          traceId: "trace_fixed",
          data: {
            userId: "usr_target",
            username: "alice",
            unifiedAuthId: "AUTH_1",
            status: "ACTIVE",
            roles: ["USER"],
            roleLabels: ["普通用户"],
            createdAt: "2026-06-26T00:00:00Z"
          }
        }),
        { status: 200 }
      )
    );
    const client = createBackendApiClient({
      baseUrl: "http://api",
      fetcher,
      traceIdFactory: () => "trace_fixed"
    });

    await expect(client.updateUserRole("usr_target", { role: "USER" })).resolves.toMatchObject({
      userId: "usr_target",
      roles: ["USER"]
    });

    expect(fetcher).toHaveBeenCalledWith(
      "http://api/api/internal/platform/system-management/users/usr_target/roles",
      expect.objectContaining({
        method: "PUT",
        body: JSON.stringify({ role: "USER" })
      })
    );
  });

  it("deletes and syncs managed users through single and batch endpoints", async () => {
    const fetcher = vi.fn<typeof fetch>().mockImplementation(async (input) => {
      const path = String(input);
      const data = path.includes("tcds-sync")
        ? { syncedUserIds: ["usr_a", "usr_b"], syncedCount: 2 }
        : { deletedUserIds: ["usr_a", "usr_b"], deletedCount: 2 };
      return new Response(JSON.stringify({ success: true, traceId: "trace_fixed", data }), { status: 200 });
    });
    const client = createBackendApiClient({
      baseUrl: "http://api",
      fetcher,
      traceIdFactory: () => "trace_fixed"
    });

    await client.deleteUser("usr/a");
    await client.deleteUsers({ userIds: ["usr_a", "usr_b"] });
    await client.syncUserFromTcds("usr/a");
    await client.syncUsersFromTcds({ userIds: ["usr_a", "usr_b"] });

    expect(fetcher.mock.calls.map((call) => [call[0], call[1]?.method, call[1]?.body])).toEqual([
      [
        "http://api/api/internal/platform/system-management/users/usr%2Fa",
        "DELETE",
        undefined
      ],
      [
        "http://api/api/internal/platform/system-management/users/batch-delete",
        "POST",
        JSON.stringify({ userIds: ["usr_a", "usr_b"] })
      ],
      [
        "http://api/api/internal/platform/system-management/users/usr%2Fa/tcds-sync",
        "POST",
        undefined
      ],
      [
        "http://api/api/internal/platform/system-management/users/tcds-sync",
        "POST",
        JSON.stringify({ userIds: ["usr_a", "usr_b"] })
      ]
    ]);
  });

  it("reports raw backend exchanges without exposing sensitive request headers", async () => {
    const responseText = JSON.stringify({
      success: true,
      traceId: "trace_backend",
      data: { runId: "run_1", sessionId: "ses_1" }
    });
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(responseText, {
        status: 200,
        headers: {
          "content-type": "application/json",
          "x-trace-id": "trace_backend"
        }
      })
    );
    const exchanges: Array<Record<string, unknown>> = [];
    const client = createBackendApiClient({
      baseUrl: "http://api",
      apiToken: "token_secret",
      fetcher,
      traceIdFactory: () => "trace_frontend",
      rawExchangeObserver: (exchange: Record<string, unknown>) => exchanges.push(exchange)
    } as Parameters<typeof createBackendApiClient>[0] & {
      rawExchangeObserver: (exchange: Record<string, unknown>) => void;
    });

    await client.startRun("ses_1", "hello");

    expect(exchanges).toHaveLength(1);
    expect(exchanges[0]).toMatchObject({
      method: "POST",
      url: "http://api/api/internal/agent/opencode/runs",
      path: "/api/internal/agent/opencode/runs",
      traceId: "trace_frontend",
      requestBody: JSON.stringify({ sessionId: "ses_1", prompt: "hello" }),
      responseStatus: 200,
      responseText
    });
    expect((exchanges[0].responseHeaders as Record<string, string>)["content-type"]).toBe("application/json");
    expect((exchanges[0].responseHeaders as Record<string, string>)["x-trace-id"]).toBe("trace_backend");
    expect(JSON.stringify(exchanges[0])).not.toContain("token_secret");
    expect(JSON.stringify(exchanges[0])).not.toContain("Authorization");
  });

  it("redacts conversation context tokens from raw request bodies", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          traceId: "trace_backend",
          data: { runId: "run_1", sessionId: "ses_1", workspaceId: "wrk_1", status: "RUNNING" }
        }),
        { status: 200 }
      )
    );
    const exchanges: Array<Record<string, unknown>> = [];
    const client = createBackendApiClient({
      baseUrl: "http://api",
      fetcher,
      traceIdFactory: () => "trace_frontend",
      rawExchangeObserver: (exchange) => exchanges.push(exchange)
    });

    await client.startRun({
      sessionId: "ses_1",
      prompt: "hello",
      contextToken: "ctx_secret_value",
      clientRequestId: "req_stable"
    });

    expect(JSON.parse(String(fetcher.mock.calls[0]?.[1]?.body))).toMatchObject({
      contextToken: "ctx_secret_value",
      clientRequestId: "req_stable"
    });
    expect(exchanges[0]?.requestBody).toBe(
      JSON.stringify({
        sessionId: "ses_1",
        prompt: "hello",
        contextToken: "[REDACTED]",
        clientRequestId: "req_stable"
      })
    );
    expect(JSON.stringify(exchanges)).not.toContain("ctx_secret_value");
  });

  it("redacts internal model token fields from observed request bodies", async () => {
    const fetcher = vi.fn<typeof fetch>().mockImplementation(async () =>
      new Response(JSON.stringify({ success: true, traceId: "trace_backend", data: {} }), { status: 200 })
    );
    const exchanges: Array<Record<string, unknown>> = [];
    const client = createBackendApiClient({
      baseUrl: "http://api",
      fetcher,
      traceIdFactory: () => "trace_frontend",
      rawExchangeObserver: (exchange) => exchanges.push(exchange)
    });

    await client.createInternalModelToken({ name: "Qwen Token", token: "new-provider-secret" });
    await client.updateInternalModelProviders({
      providers: [],
      authToken: "legacy-provider-secret"
    });

    expect(JSON.stringify(fetcher.mock.calls)).toContain("new-provider-secret");
    expect(JSON.stringify(fetcher.mock.calls)).toContain("legacy-provider-secret");
    expect(JSON.stringify(exchanges)).not.toContain("new-provider-secret");
    expect(JSON.stringify(exchanges)).not.toContain("legacy-provider-secret");
    expect(exchanges.map((exchange) => exchange.requestBody)).toEqual([
      JSON.stringify({ name: "Qwen Token", token: "[REDACTED]" }),
      JSON.stringify({ providers: [], authToken: "[REDACTED]" })
    ]);
  });

  it("redacts conversation context tokens from raw response bodies without changing returned data", async () => {
    const responseText = JSON.stringify({
      success: true,
      traceId: "trace_backend",
      data: {
        contextToken: "ctx_response_secret",
        contextVersion: 1,
        expiresAt: "2026-07-11T08:00:00Z"
      }
    });
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response(responseText, { status: 200 }));
    const exchanges: Array<Record<string, unknown>> = [];
    const client = createBackendApiClient({
      baseUrl: "http://api",
      fetcher,
      traceIdFactory: () => "trace_frontend",
      rawExchangeObserver: (exchange) => exchanges.push(exchange)
    });

    await expect(client.getRunContext("ses_1")).resolves.toMatchObject({
      contextToken: "ctx_response_secret"
    });
    expect(JSON.parse(String(exchanges[0]?.responseText))).toMatchObject({
      data: { contextToken: "[REDACTED]" }
    });
    expect(JSON.stringify(exchanges)).not.toContain("ctx_response_secret");
  });

  it("redacts context tokens from non-JSON raw response observations", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response('data: {"contextToken":"ctx_stream_secret","keep":"visible"}\n\n', { status: 503 })
    );
    const exchanges: Array<Record<string, unknown>> = [];
    const client = createBackendApiClient({
      baseUrl: "http://api",
      fetcher,
      traceIdFactory: () => "trace_frontend",
      rawExchangeObserver: (exchange) => exchanges.push(exchange)
    });

    await expect(client.getRunContext("ses_1")).rejects.toBeInstanceOf(BackendApiError);
    expect(exchanges[0]?.responseText).toContain('"contextToken":"[REDACTED]"');
    expect(exchanges[0]?.responseText).toContain('"keep":"visible"');
    expect(JSON.stringify(exchanges)).not.toContain("ctx_stream_secret");
  });

  it("redacts an unterminated quoted token without catastrophic backtracking", async () => {
    const malformed = `contextToken:"${"\\".repeat(20_000)}ctx_unterminated_secret`;
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response(malformed, { status: 503 }));
    const exchanges: Array<Record<string, unknown>> = [];
    const client = createBackendApiClient({
      baseUrl: "http://api",
      fetcher,
      rawExchangeObserver: (exchange) => exchanges.push(exchange)
    });
    const startedAt = performance.now();

    await expect(client.getRunContext("ses_1")).rejects.toBeInstanceOf(BackendApiError);

    expect(performance.now() - startedAt).toBeLessThan(1_000);
    expect(JSON.stringify(exchanges)).not.toContain("ctx_unterminated_secret");
    expect(exchanges[0]?.responseText).toContain('contextToken:"[REDACTED]');
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

  it("gets session runtime state from the platform opencode runtime URL", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          traceId: "trace_fixed",
          data: {
            runningCount: 1,
            questionCount: 1,
            sessions: [
              {
                sessionId: "ses_1",
                runId: "run_1",
                runStatus: "RUNNING",
                attention: "QUESTION",
                attentionEventId: "evt_1",
                attentionAt: "2026-07-08T08:01:00Z",
                updatedAt: "2026-07-08T08:01:02Z"
              }
            ],
            generatedAt: "2026-07-08T08:01:03Z"
          }
        }),
        { status: 200 }
      )
    );
    const client = createBackendApiClient({
      baseUrl: "http://api",
      fetcher,
      traceIdFactory: () => "trace_fixed"
    });

    await expect(client.getSessionRuntimeState()).resolves.toMatchObject({
      runningCount: 1,
      questionCount: 1,
      permissionCount: 0,
      sessions: [expect.objectContaining({ sessionId: "ses_1", attention: "QUESTION" })]
    });

    expect(fetcher).toHaveBeenCalledWith(
      "http://api/api/internal/platform/opencode-runtime/sessions/runtime-state",
      expect.any(Object)
    );
  });

  it("gets a conversation run context from the agent-scoped session URL", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          traceId: "trace_fixed",
          data: {
            contextToken: "ctx_opaque",
            contextVersion: 1,
            expiresAt: "2026-07-11T08:00:00Z"
          }
        }),
        { status: 200 }
      )
    );
    const client = createBackendApiClient({
      baseUrl: "http://api",
      fetcher,
      traceIdFactory: () => "trace_fixed"
    });

    await expect(client.getRunContext("ses_/with space")).resolves.toEqual({
      contextToken: "ctx_opaque",
      contextVersion: 1,
      expiresAt: "2026-07-11T08:00:00Z"
    });
    expect(fetcher).toHaveBeenCalledWith(
      "http://api/api/internal/agent/opencode/sessions/ses_%2Fwith%20space/run-context",
      expect.objectContaining({ method: "POST" })
    );
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
              serviceStatus: "RUNNING",
              serviceAddress: "10.8.0.12:4096",
              checkedAt: "2026-06-24T00:00:01Z"
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
              operationId: "opi_1234567890abcdef",
              status: "FAILED",
              currentStep: "HEALTH_CHECKING",
              errorCode: "OPENCODE_UNAVAILABLE",
              errorMessage: "启动后 10 秒内未通过健康检查：connection refused",
              processId: "ocp_1234567890abcdef",
              serviceAddress: "10.8.0.12:4096",
              traceId: "trace_fixed",
              steps: [
                { code: "VALIDATING_REQUEST", name: "校验请求", status: "SUCCEEDED" },
                { code: "HEALTH_CHECKING", name: "健康检查", status: "FAILED" }
              ],
              createdAt: "2026-06-24T00:00:00Z",
              updatedAt: "2026-06-24T00:00:05Z"
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
    await expect(client.initializeMyOpencodeProcess("opi_1234567890abcdef")).resolves.toMatchObject({
      status: "READY",
      baseUrl: "http://10.8.0.12:4096",
      serviceStatus: "RUNNING",
      serviceAddress: "10.8.0.12:4096"
    });
    await expect(client.getOpencodeProcessStartOperation("opi_1234567890abcdef")).resolves.toMatchObject({
      operationId: "opi_1234567890abcdef",
      status: "FAILED",
      currentStep: "HEALTH_CHECKING",
      errorCode: "OPENCODE_UNAVAILABLE",
      steps: expect.arrayContaining([expect.objectContaining({ code: "HEALTH_CHECKING", status: "FAILED" })])
    });

    expect(fetcher.mock.calls[0]?.[0]).toBe("http://api/api/internal/agent/opencode/processes/me");
    expect(fetcher.mock.calls[1]?.[0]).toBe("http://api/api/internal/agent/opencode/processes/me/initialize");
    expect(fetcher.mock.calls[2]?.[0]).toBe(
      "http://api/api/internal/agent/opencode/processes/me/initialize-operations/opi_1234567890abcdef"
    );
    expect(fetcher.mock.calls[1]?.[1]).toEqual(expect.objectContaining({ method: "POST" }));
    expect(fetcher.mock.calls[1]?.[1]?.body).toBe(JSON.stringify({ operationId: "opi_1234567890abcdef" }));
    const headers = fetcher.mock.calls[0]?.[1]?.headers as Headers;
    expect(headers.get("Authorization")).toBe("Bearer token_123");
  });

  it("checks current user opencode process weak health through agent-scoped URL", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          traceId: "trace_fixed",
          data: {
            healthy: true,
            status: "HEALTHY",
            serviceStatus: "RUNNING",
            linuxServerId: "server-a",
            containerId: "ctr_01",
            port: 4096,
            baseUrl: "http://10.8.0.12:4096",
            checkedAt: "2026-06-24T00:00:01Z",
            message: "ok"
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

    await expect(
      client.getMyOpencodeProcessHealth({ linuxServerId: "server-a", containerId: "ctr_01", port: 4096 })
    ).resolves.toMatchObject({
      healthy: true,
      status: "HEALTHY",
      serviceStatus: "RUNNING",
      baseUrl: "http://10.8.0.12:4096"
    });

    expect(fetcher.mock.calls[0]?.[0]).toBe(
      "http://api/api/internal/agent/opencode/processes/me/health?linuxServerId=server-a&containerId=ctr_01&port=4096"
    );
    expect((fetcher.mock.calls[0]?.[1]?.headers as Headers).get("Authorization")).toBe("Bearer token_123");
  });

  it("maps opencode runtime management overview through platform URL with filters", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          traceId: "trace_fixed",
          data: {
            generatedAt: "2026-06-24T00:00:00Z",
            summary: {
              linuxServers: 1,
              readyLinuxServers: 1,
              backendProcesses: 1,
              readyBackendProcesses: 1,
              containers: 1,
              readyContainers: 1,
              managers: 1,
              connectedManagers: 1,
              managerBackendConnections: 1,
              opencodeProcesses: 1,
              runningOpencodeProcesses: 1,
              userBindings: 1
            },
            linuxServers: [{ linuxServerId: "10.8.0.12", name: "10.8.0.12", status: "READY", capacitySummary: {}, lastHeartbeatAt: "2026-06-24T00:00:00Z", traceId: "trace_fixed" }],
            backendProcesses: [],
            containers: [],
            managers: [{
              managerId: "mgr_1234567890abcdef",
              containerId: "ctr_01",
              linuxServerId: "10.8.0.12",
              protocolVersion: "opencode-manager.v1",
              connectionStatus: "CONNECTED",
              capabilities: {},
              traceId: "trace_fixed",
              managedProcesses: [{
                port: 4096,
                pid: 12345,
                baseUrl: "http://10.8.0.12:4096",
                sessionPath: "/data/opencode/session/4096",
                configPath: "/data/opencode/.config/opencode/",
                startedAt: "2026-06-24T00:00:00Z",
                startCommand: "XDG_DATA_HOME=/data/opencode/session/4096 OPENCODE_CONFIG_DIR=/data/opencode/.config/opencode/ opencode serve --hostname 0.0.0.0 --port 4096 --print-logs",
                traceId: "trace_process",
                ownership: "BOUND",
                processId: "ocp_1234567890abcdef",
                processStatus: "RUNNING",
                healthMessage: "ok",
                userId: "usr_1234567890abcdef",
                username: "process-user",
                bindingAgentId: "opencode",
                bindingStatus: "ACTIVE",
                bindingUpdatedAt: "2026-06-24T00:00:00Z"
              }]
            }],
            managerBackendConnections: [],
            opencodeProcesses: {
              items: [
                {
                  processId: "ocp_1234567890abcdef",
                  userId: "usr_1234567890abcdef",
                  username: "process-user",
                  linuxServerId: "10.8.0.12",
                  containerId: "ctr_01",
                  port: 4096,
                  pid: 12345,
                  baseUrl: "http://10.8.0.12:4096",
                  status: "RUNNING",
                  sessionPath: "/data/opencode/session/4096",
                  configPath: "/data/opencode/.config/opencode/",
                  lastHealthCheckAt: "2026-06-24T00:00:00Z",
                  healthMessage: "ok",
                  traceId: "trace_fixed",
                  bindingAgentId: "opencode",
                  bindingStatus: "ACTIVE",
                  bindingUpdatedAt: "2026-06-24T00:00:00Z"
                }
              ],
              page: 1,
              size: 20,
              total: 1
            }
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

    await expect(
      client.getOpencodeRuntimeManagementOverview({
        status: "RUNNING",
        linuxServerId: "10.8.0.12",
        containerId: "ctr_01",
        username: "process-user",
        page: 1,
        size: 20
      })
    ).resolves.toMatchObject({
      summary: { runningOpencodeProcesses: 1 },
      managers: [{
        managedProcesses: [{
          ownership: "BOUND",
          username: "process-user",
          bindingStatus: "ACTIVE",
          startCommand: expect.stringContaining("opencode serve --hostname 0.0.0.0 --port 4096")
        }]
      }],
      opencodeProcesses: { total: 1 }
    });

    expect(fetcher.mock.calls[0]?.[0]).toBe(
      "http://api/api/internal/platform/opencode-runtime/management/overview?status=RUNNING&linuxServerId=10.8.0.12&containerId=ctr_01&username=process-user&page=1&size=20"
    );
    const headers = fetcher.mock.calls[0]?.[1]?.headers as Headers;
    expect(headers.get("Authorization")).toBe("Bearer token_123");
  });

  it("maps opencode runtime management user process lookup through platform URL", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          traceId: "trace_fixed",
          data: {
            items: [{
              processId: "ocp_1234567890abcdef",
              userId: "usr_1234567890abcdef",
              username: "process-user",
              linuxServerId: "10.8.0.12",
              containerId: "ctr_01",
              port: 4096,
              pid: 12345,
              baseUrl: "http://10.8.0.12:4096",
              status: "STOPPED",
              managerStatus: "NOT_RUNNING",
              healthStatus: "NOT_RUNNING",
              restartable: true,
              sessionPath: "/data/opencode/session/4096",
              configPath: "/data/opencode/.config/opencode/",
              lastHealthCheckAt: "2026-06-24T00:00:00Z",
              healthMessage: "process pid is not alive",
              traceId: "trace_fixed",
              bindingAgentId: "opencode",
              bindingStatus: "ACTIVE"
            }],
            page: 1,
            size: 20,
            total: 1
          }
        }),
        { status: 200 }
      )
    );
    const client = createBackendApiClient({
      baseUrl: "http://api",
      fetcher,
      traceIdFactory: () => "trace_fixed"
    });

    await expect(client.getOpencodeRuntimeManagementUserProcesses({ keyword: "process-user", page: 1, size: 20 }))
      .resolves.toMatchObject({
        total: 1,
        items: [{ status: "STOPPED", managerStatus: "NOT_RUNNING", healthStatus: "NOT_RUNNING", restartable: true }]
      });

    expect(fetcher.mock.calls[0]?.[0]).toBe(
      "http://api/api/internal/platform/opencode-runtime/management/user-processes?keyword=process-user&page=1&size=20"
    );
  });

  it("maps opencode runtime metric history APIs through platform URL", async () => {
    const fetcher = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          data: {
            generatedAt: "2026-06-24T00:00:00Z",
            containerId: "ctr_01",
            from: "2026-06-22T00:00:00Z",
            to: "2026-06-24T00:00:00Z",
            samples: [
              {
                sampledAt: "2026-06-24T00:00:00Z",
                cpuUsagePercent: 12.5,
                memoryUsagePercent: 50,
                memoryUsedBytes: 512
              }
            ]
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

    await expect(client.getOpencodeRuntimeContainerMetrics("ctr_01", { windowMinutes: 30, maxPoints: 720 })).resolves.toMatchObject({
      containerId: "ctr_01",
      samples: [{ cpuUsagePercent: 12.5 }]
    });
    expect(fetcher.mock.calls[0]?.[0]).toBe(
      "http://api/api/internal/platform/opencode-runtime/management/containers/ctr_01/metrics?windowMinutes=30&maxPoints=720"
    );

    fetcher.mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          success: true,
          data: {
            generatedAt: "2026-06-24T00:00:00Z",
            linuxServerId: "10.8.0.12",
            backendProcessId: "bjp_1234567890abcdef",
            from: "2026-06-22T00:00:00Z",
            to: "2026-06-24T00:00:00Z",
            samples: [{
              sampledAt: "2026-06-24T00:00:00Z",
              memoryAvailableBytes: 1536,
              jvmProcessResidentMemoryBytes: 700,
              jvmHeapUsedBytes: 200,
              jvmGcCollectionCountDelta: 3,
              jvmThreadsDaemon: 12,
              jvmThreadsLive: 42
            }]
          }
        }),
        { status: 200 }
      )
    );

    await expect(client.getOpencodeRuntimeBackendServerMetrics("10.8.0.12")).resolves.toMatchObject({
      linuxServerId: "10.8.0.12",
      backendProcessId: "bjp_1234567890abcdef",
      samples: [{
        memoryAvailableBytes: 1536,
        jvmProcessResidentMemoryBytes: 700,
        jvmHeapUsedBytes: 200,
        jvmGcCollectionCountDelta: 3,
        jvmThreadsDaemon: 12,
        jvmThreadsLive: 42
      }]
    });
    expect(fetcher.mock.calls[1]?.[0]).toBe(
      "http://api/api/internal/platform/opencode-runtime/management/linux-servers/10.8.0.12/backend-metrics"
    );

  });

  it("maps opencode runtime managed process actions through platform URL", async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({
        success: true,
        traceId: "trace_fixed",
        data: { command: "restart", status: "STARTED", port: 4096, pid: 12346, message: "opencode server started" }
      }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        success: true,
        traceId: "trace_fixed",
        data: { command: "stop", status: "STOPPED", port: 4097, pid: 22345, message: "opencode server stopped" }
      }), { status: 200 }));
    const client = createBackendApiClient({
      baseUrl: "http://api",
      apiToken: "token_123",
      fetcher,
      traceIdFactory: () => "trace_fixed"
    });

    await expect(client.restartOpencodeRuntimeManagedProcess("ctr_01", 4096))
      .resolves.toMatchObject({ command: "restart", status: "STARTED", port: 4096 });
    await expect(client.stopOpencodeRuntimeManagedProcess("ctr_01", 4097))
      .resolves.toMatchObject({ command: "stop", status: "STOPPED", port: 4097 });

    expect(fetcher.mock.calls.map((call) => [call[0], call[1]?.method])).toEqual([
      ["http://api/api/internal/platform/opencode-runtime/management/containers/ctr_01/processes/4096/restart", "POST"],
      ["http://api/api/internal/platform/opencode-runtime/management/containers/ctr_01/processes/4097/stop", "POST"]
    ]);
  });

  it("posts for an XXL-JOB one-time SSO ticket without putting the ticket in a URL", async () => {
    const exchanges: Array<Record<string, unknown>> = [];
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({
      success: true,
      traceId: "trace_fixed",
      data: {
        ticket: "one-time-secret",
        expiresAt: "2026-07-20T08:00:00Z",
        formAction: "/xxl-job-admin/platform-sso/login"
      }
    }), { status: 200 }));
    const client = createBackendApiClient({
      baseUrl: "http://api",
      apiToken: "token_123",
      fetcher,
      traceIdFactory: () => "trace_fixed",
      rawExchangeObserver: (exchange) => exchanges.push(exchange)
    });

    await expect(client.createXxlJobSsoTicket()).resolves.toMatchObject({
      ticket: "one-time-secret",
      formAction: "/xxl-job-admin/platform-sso/login"
    });

    expect(fetcher).toHaveBeenCalledWith(
      "http://api/api/internal/platform/xxl-job/sso-tickets",
      expect.objectContaining({ method: "POST" })
    );
    expect(String(fetcher.mock.calls[0]?.[0])).not.toContain("one-time-secret");
    expect(exchanges[0]?.responseText).toContain('"ticket":"[REDACTED]"');
    expect(exchanges[0]?.responseText).not.toContain("one-time-secret");
  });

  it("maps scheduler management APIs through platform URL", async () => {
    const taskPage = {
      items: [
        {
          taskKey: "daily.cleanup",
          name: "每日清理",
          cronExpression: "0 0 2 * * *",
          enabled: true,
          lockTtlSeconds: 300,
          registrationStatus: "REGISTERED",
          registrationStatusLabel: "已注册",
          currentRun: null,
          latestRun: null,
          traceId: "trace_fixed",
          createdAt: "2026-06-25T00:00:00Z",
          updatedAt: "2026-06-25T00:00:00Z"
        }
      ],
      page: 1,
      size: 20,
      total: 1
    };
    const run = {
      taskRunId: "str_1234567890abcdef",
      taskKey: "daily.cleanup",
      triggerType: "MANUAL",
      triggerTypeLabel: "手工触发",
      status: "STOPPING",
      statusLabel: "停止中",
      requestedByUserId: "usr_admin",
      scheduledFireAt: "2026-06-25T00:00:00Z",
      startedAt: "2026-06-25T00:00:01Z",
      stopRequestedAt: "2026-06-25T00:00:02Z",
      stopRequestedByUserId: "usr_admin",
      stopReason: "管理员手工停止",
      traceId: "trace_fixed",
      createdAt: "2026-06-25T00:00:00Z",
      updatedAt: "2026-06-25T00:00:02Z"
    };
    const diagnostics = {
      scheduler: {
        enabled: true,
        runnerRunning: false,
        instanceId: "scheduler-test-instance",
        scanIntervalSeconds: 30,
        dueTaskLimit: 50,
        manualRunLimit: 50,
        lastScanStartedAt: "2026-06-25T00:00:00Z",
        lastScanFinishedAt: "2026-06-25T00:00:01Z",
        lastScanErrorMessage: null
      },
      redisLock: {
        checkable: true,
        lockKey: "test-agent:scheduler:lock:daily.cleanup",
        locked: true,
        ttlMillis: 42000
      },
      task: {
        taskKey: "daily.cleanup",
        enabled: true,
        registrationStatus: "REGISTERED",
        registrationStatusLabel: "已注册",
        nextFireAt: "2026-06-25T02:00:00Z",
        lockTtlSeconds: 300,
        currentRun: null,
        latestRun: null,
        pendingManualRunCount: 1
      },
      diagnosis: {
        manualTriggerReady: false,
        cronReady: false,
        blockers: [{ code: "RUNNER_NOT_RUNNING", message: "后台扫描线程未运行" }]
      }
    };
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify({ success: true, traceId: "trace_fixed", data: taskPage }), { status: 200 })
    );
    fetcher
      .mockResolvedValueOnce(new Response(JSON.stringify({ success: true, traceId: "trace_fixed", data: taskPage }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ success: true, traceId: "trace_fixed", data: taskPage.items[0] }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ success: true, traceId: "trace_fixed", data: run }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ success: true, traceId: "trace_fixed", data: { items: [run], page: 1, size: 20, total: 1 } }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ success: true, traceId: "trace_fixed", data: run }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ success: true, traceId: "trace_fixed", data: run }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ success: true, traceId: "trace_fixed", data: diagnostics }), { status: 200 }));
    const client = createBackendApiClient({
      baseUrl: "http://api",
      apiToken: "token_123",
      fetcher,
      traceIdFactory: () => "trace_fixed"
    });

    await expect(client.listScheduledTasks({ page: 1, size: 20 })).resolves.toMatchObject({ total: 1 });
    await client.updateScheduledTask("daily.cleanup", { enabled: false, cronExpression: "0 0 3 * * *", lockTtlSeconds: 600 });
    await client.triggerScheduledTask("daily.cleanup");
    await client.listScheduledTaskRuns({ taskKey: "daily.cleanup", status: "RUNNING", triggerType: "MANUAL", page: 1, size: 20 });
    await client.getScheduledTaskRun("str_1234567890abcdef");
    await client.stopScheduledTaskRun("str_1234567890abcdef");
    await expect(client.getSchedulerDiagnostics("daily.cleanup")).resolves.toMatchObject({
      scheduler: { enabled: true, runnerRunning: false },
      diagnosis: { blockers: [{ code: "RUNNER_NOT_RUNNING" }] }
    });

    expect(fetcher.mock.calls.map((call) => call[0])).toEqual([
      "http://api/api/internal/platform/scheduler-management/tasks?page=1&size=20",
      "http://api/api/internal/platform/scheduler-management/tasks/daily.cleanup",
      "http://api/api/internal/platform/scheduler-management/tasks/daily.cleanup/trigger",
      "http://api/api/internal/platform/scheduler-management/runs?taskKey=daily.cleanup&status=RUNNING&triggerType=MANUAL&page=1&size=20",
      "http://api/api/internal/platform/scheduler-management/runs/str_1234567890abcdef",
      "http://api/api/internal/platform/scheduler-management/runs/str_1234567890abcdef/stop",
      "http://api/api/internal/platform/scheduler-management/diagnostics?taskKey=daily.cleanup"
    ]);
    expect(fetcher.mock.calls[1]?.[1]).toEqual(expect.objectContaining({
      method: "PATCH",
      body: JSON.stringify({ enabled: false, cronExpression: "0 0 3 * * *", lockTtlSeconds: 600 })
    }));
    expect(fetcher.mock.calls[2]?.[1]).toEqual(expect.objectContaining({ method: "POST" }));
    expect(fetcher.mock.calls[5]?.[1]).toEqual(expect.objectContaining({ method: "POST" }));
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
      mode: "build",
      contextToken: "ctx_opaque",
      clientRequestId: "req_stable"
    });

    expect(JSON.parse(String(fetcher.mock.calls[0]?.[1]?.body))).toEqual({
      sessionId: "ses_1",
      prompt: "fallback",
      parts: [{ type: "text", text: "hello" }],
      messageId: "msg_1",
      agent: "build",
      model: "anthropic/claude-sonnet-4-5",
      variant: "default",
      mode: "build",
      contextToken: "ctx_opaque",
      clientRequestId: "req_stable"
    });
  });

  it("sends side questions through the platform runtime endpoint", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify({
        success: true,
        traceId: "trace_fixed",
        data: { answer: "已完成", compacted: true }
      }), { status: 200 })
    );
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await expect(client.askSideQuestion("ses_1", {
      question: "刚才做了什么？",
      messageId: "msg_2",
      agent: "plan",
      model: "anthropic/claude-sonnet-4-5"
    })).resolves.toEqual({ answer: "已完成", compacted: true });

    expect(fetcher).toHaveBeenCalledWith(
      "http://api/api/internal/platform/opencode-runtime/sessions/ses_1/side-question",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({
          question: "刚才做了什么？",
          messageId: "msg_2",
          agent: "plan",
          model: "anthropic/claude-sonnet-4-5"
        })
      })
    );
  });

  it("starts a streaming side-question run without sending an agent override", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify({
        success: true,
        traceId: "trace_fixed",
        data: { runId: "run_1234567890abcdef" }
      }), { status: 200 })
    );
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await expect(client.startSideQuestionRun("ses_1", {
      question: "刚才做了什么？",
      messageId: "msg_2",
      model: "anthropic/claude-sonnet-4-5"
    })).resolves.toEqual({ runId: "run_1234567890abcdef" });

    expect(fetcher).toHaveBeenCalledWith(
      "http://api/api/internal/platform/opencode-runtime/sessions/ses_1/side-question/runs",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({
          question: "刚才做了什么？",
          messageId: "msg_2",
          model: "anthropic/claude-sonnet-4-5"
        })
      })
    );
  });

  it("starts a manual question run without a main session", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify({
        success: true,
        traceId: "trace_fixed",
        data: { runId: "run_manual1234567890" }
      }), { status: 200 })
    );
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await expect(client.startManualQuestionRun({
      workspaceId: "wrk_1",
      question: "怎样初始化工作区？",
      model: "anthropic/claude-sonnet-4-5"
    })).resolves.toEqual({ runId: "run_manual1234567890" });

    expect(fetcher).toHaveBeenCalledWith(
      "http://api/api/internal/platform/opencode-runtime/manual-question/runs",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({
          workspaceId: "wrk_1",
          question: "怎样初始化工作区？",
          model: "anthropic/claude-sonnet-4-5"
        })
      })
    );
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

  it("maps repository english names and workspace create progress through configuration APIs", async () => {
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(JSON.stringify({
        success: true,
        traceId: "trace_fixed",
        data: {
          repositoryId: "repo_1",
          gitUrl: "https://gitee.com/demo/repo.git",
          name: "演示库",
          englishName: "demorepo",
          deploymentMode: "INTERNAL",
          repositoryType: "TEST_WORK_REPOSITORY",
          repositoryTypeLabel: "测试工作库",
          standard: true,
          createdAt: "2026-06-26T00:00:00Z",
          updatedAt: "2026-06-26T00:00:00Z"
        }
      }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        success: true,
        traceId: "trace_fixed",
        data: {
          nodes: [
            {
              name: "F-COSS",
              path: "F-COSS",
              type: "directory",
              children: [
                { name: "W1", path: "F-COSS/W1", type: "directory", children: [] },
                { name: "case.md", path: "F-COSS/case.md", type: "file", children: [] }
              ]
            }
          ]
        }
      }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        success: true,
        traceId: "trace_fixed",
        data: {
          workspaceId: "awp_1",
          appId: "app_1",
          repositoryId: "repo_1",
          branch: "feature_testagent_20260707",
          directoryPath: "src",
          workspaceName: "src",
          initialVersion: { versionId: "awv_1", version: "20260707" },
          createdAt: "2026-06-26T00:00:00Z",
          updatedAt: "2026-06-26T00:00:00Z"
        }
      }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        success: true,
        traceId: "trace_fixed",
        data: {
          operationId: "wco_123",
          status: "RUNNING",
          currentStep: "PREPARING_REPOSITORY",
          steps: [{ code: "PREPARING_REPOSITORY", name: "下载代码", status: "RUNNING" }]
        }
      }), { status: 200 }));
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await client.createRepository({
      gitUrl: "https://gitee.com/demo/repo.git",
      name: "演示库",
      englishName: "demorepo",
      deploymentMode: "INTERNAL",
      repositoryType: "TEST_WORK_REPOSITORY",
      standard: true
    });
    await expect(client.getRepositoryTree("app_1", "repo_1", "feature_testagent_20260707"))
      .resolves.toEqual({
        nodes: [
          {
            name: "F-COSS",
            path: "F-COSS",
            type: "directory",
            children: [
              { name: "W1", path: "F-COSS/W1", type: "directory", children: [] },
              { name: "case.md", path: "F-COSS/case.md", type: "file", children: [] }
            ]
          }
        ]
      });
    await client.createApplicationWorkspace("app_1", {
      repositoryId: "repo_1",
      branch: "feature_testagent_20260707",
      directoryPath: "src",
      directoryNew: true,
      operationId: "wco_123"
    });
    await client.getWorkspaceCreateOperation("wco_123");

    expect(JSON.parse(String(fetcher.mock.calls[0]?.[1]?.body))).toMatchObject({
      englishName: "demorepo",
      deploymentMode: "INTERNAL",
      repositoryType: "TEST_WORK_REPOSITORY",
      standard: true
    });
    expect(fetcher.mock.calls[1]?.[0]).toBe("http://api/api/internal/platform/configuration-management/applications/app_1/repositories/repo_1/tree?branch=feature_testagent_20260707");
    expect(JSON.parse(String(fetcher.mock.calls[2]?.[1]?.body))).toMatchObject({ operationId: "wco_123", directoryNew: true });
    expect(fetcher.mock.calls[3]?.[0]).toBe("http://api/api/internal/platform/configuration-management/workspace-create-operations/wco_123");
  });

  it("loads repository type options from configuration APIs", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({
      success: true,
      traceId: "trace_fixed",
      data: [
        { typeCode: "TEST_WORK_REPOSITORY", typeLabel: "测试工作库" },
        { typeCode: "APPLICATION_CODE_REPOSITORY", typeLabel: "应用代码库" },
        { typeCode: "APPLICATION_ASSET_REPOSITORY", typeLabel: "应用资产库" }
      ]
    }), { status: 200 }));
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await expect(client.listRepositoryTypes()).resolves.toEqual([
      { typeCode: "TEST_WORK_REPOSITORY", typeLabel: "测试工作库" },
      { typeCode: "APPLICATION_CODE_REPOSITORY", typeLabel: "应用代码库" },
      { typeCode: "APPLICATION_ASSET_REPOSITORY", typeLabel: "应用资产库" }
    ]);

    expect(fetcher).toHaveBeenCalledWith(
      "http://api/api/internal/platform/configuration-management/repository-types",
      expect.any(Object)
    );
  });

  it("creates an enabled application through the configuration API", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({
      success: true,
      traceId: "trace_fixed",
      data: { appId: "F-NEW", appName: "新应用", enabled: true }
    }), { status: 200 }));
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await expect(client.createApplication({ appId: "F-NEW", appName: "新应用" }))
      .resolves.toEqual({ appId: "F-NEW", appName: "新应用", enabled: true });
    expect(fetcher).toHaveBeenCalledWith(
      "http://api/api/internal/platform/configuration-management/applications",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ appId: "F-NEW", appName: "新应用" })
      })
    );
  });

  it("loads repository deployment options from configuration APIs", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({
      success: true,
      traceId: "trace_fixed",
      data: {
        defaultDeploymentMode: "INTERNAL",
        internalSshPrefix: "ssh://001177621@",
        options: [
          { mode: "EXTERNAL", label: "外部部署" },
          { mode: "INTERNAL", label: "内部部署" }
        ]
      }
    }), { status: 200 }));
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await expect(client.getRepositoryDeploymentOptions()).resolves.toEqual({
      defaultDeploymentMode: "INTERNAL",
      internalSshPrefix: "ssh://001177621@",
      options: [
        { mode: "EXTERNAL", label: "外部部署" },
        { mode: "INTERNAL", label: "内部部署" }
      ]
    });

    expect(fetcher).toHaveBeenCalledWith(
      "http://api/api/internal/platform/configuration-management/repository-deployment-options",
      expect.any(Object)
    );
  });

  it("maps managed workspace APIs through platform URLs", async () => {
    const versionResponse = {
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
      targetCommitHash: "abc123",
      replicaCommitHash: "abc123",
      replicaLinuxServerId: "10.8.0.12",
      replicaStatus: "READY",
      createdAt: "2026-06-23T00:00:00Z",
      updatedAt: "2026-06-23T00:00:00Z"
    };
    const fetcher = vi.fn<typeof fetch>().mockImplementation(() =>
      Promise.resolve(
        new Response(JSON.stringify({ success: true, traceId: "trace_fixed", data: versionResponse }), {
          status: 200
        })
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

    await expect(client.gitPullWorkspaceVersion("awv_1")).resolves.toMatchObject({
      versionId: "awv_1",
      targetCommitHash: "abc123",
      replicaLinuxServerId: "10.8.0.12"
    });
    expect(fetcher.mock.calls[1]?.[0]).toBe(
      "http://api/api/internal/platform/workspace-management/workspace-versions/awv_1/git-pull"
    );
    expect(fetcher.mock.calls[1]?.[1]).toEqual(expect.objectContaining({ method: "POST" }));
  });

  it("checks version repository access before personal workspace creation", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify({
        success: true,
        traceId: "trace_fixed",
        data: {
          accessible: false,
          repositoryId: "repo_1",
          repositoryName: "GCMS 测试版本库",
          branch: "feature_testagent_20260707",
          reason: "REPOSITORY_PERMISSION_REQUIRED"
        }
      }), { status: 200 })
    );
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await expect(client.checkWorkspaceVersionGitAccess("awv_1")).resolves.toEqual({
      accessible: false,
      repositoryId: "repo_1",
      repositoryName: "GCMS 测试版本库",
      branch: "feature_testagent_20260707",
      reason: "REPOSITORY_PERMISSION_REQUIRED"
    });
    expect(fetcher).toHaveBeenCalledWith(
      "http://api/api/internal/platform/workspace-management/workspace-versions/awv_1/git-access",
      expect.objectContaining({ headers: expect.any(Headers) })
    );
    expect(fetcher.mock.calls[0]?.[1]?.method).toBeUndefined();
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

    const response = await client.addPersonalSshKey({
      name: "work",
      encryptedPrivateKey: "-----ENCRYPTED-----",
      encryptedAesKey: "-----AES-----",
      encryptionNonce: "-----NONCE-----",
      fingerprint: "SHA256:abc"
    });

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
      const request = client.listWorkspaces();
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

  it("aborts hanging requests and maps them to a timeout error using custom timeoutMs override", async () => {
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
      requestTimeoutMs: 30000
    });

    try {
      const request = client.startRun({ sessionId: "ses_1" });
      const expectation = expect(request).rejects.toMatchObject({
        code: "REQUEST_TIMEOUT",
        traceId: "trace_fixed",
        retryable: true
      });
      await vi.advanceTimersByTimeAsync(30000);
      expect(signal?.aborted).toBe(false);

      await vi.advanceTimersByTimeAsync(90000);
      expect(signal?.aborted).toBe(true);
      await expectation;
    } finally {
      vi.useRealTimers();
    }
  });

  it("aborts hanging postRuntime requests and maps them to a timeout error using custom timeoutMs override", async () => {
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
      requestTimeoutMs: 30000
    });

    try {
      const request = client.runSessionCommand("ses_1", { command: "test" });
      const expectation = expect(request).rejects.toMatchObject({
        code: "REQUEST_TIMEOUT",
        traceId: "trace_fixed",
        retryable: true
      });
      await vi.advanceTimersByTimeAsync(30000);
      expect(signal?.aborted).toBe(false);

      await vi.advanceTimersByTimeAsync(90000);
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

    expect(fetcher).toHaveBeenCalledWith(
      "http://api/api/internal/platform/opencode-runtime/agents?workspaceId=wrk_1234567890abcdef",
      expect.any(Object)
    );
  });

  it("lists runtime agents with caller cancellation and local timeout options", async () => {
    let capturedSignal: AbortSignal | undefined;
    let resolveFetch: ((response: Response) => void) | undefined;
    const fetcher = vi.fn<typeof fetch>().mockImplementation((_url, init) => {
      capturedSignal = init?.signal as AbortSignal | undefined;
      return new Promise<Response>((resolve) => {
        resolveFetch = resolve;
      });
    });
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });
    const controller = new AbortController();

    const request = client.listAgents("wrk_1234567890abcdef", { signal: controller.signal, timeoutMs: 8000 });

    await Promise.resolve();
    expect(fetcher).toHaveBeenCalledWith(
      "http://api/api/internal/platform/opencode-runtime/agents?workspaceId=wrk_1234567890abcdef",
      expect.any(Object)
    );
    const init = fetcher.mock.calls[0]?.[1] as RequestInit & { timeoutMs?: number };
    expect(init.timeoutMs).toBeUndefined();
    let assertionError: unknown;
    try {
      controller.abort();
      expect(capturedSignal?.aborted).toBe(true);
    } catch (error) {
      assertionError = error;
    } finally {
      resolveFetch?.(
        new Response(JSON.stringify({ success: true, traceId: "trace_fixed", data: [{ id: "build", name: "Build" }] }), { status: 200 })
      );
      await request.catch(() => undefined);
    }
    if (assertionError) {
      throw assertionError;
    }
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

  it("disposes the OpenCode runtime before reloading Agent and Skill catalogs", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify({ success: true, traceId: "trace_fixed", data: true }), { status: 200 })
    );
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await expect(client.disposeGlobal()).resolves.toBe(true);

    expect(fetcher).toHaveBeenCalledWith(
      "http://api/api/internal/platform/opencode-runtime/global/dispose",
      expect.objectContaining({ method: "POST" })
    );
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

  it("keeps a pending native permission on its requested historical platform session", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          traceId: "trace_fixed",
          data: [{
            id: "per_history",
            sessionID: "ses_remote_history",
            permission: "external_directory",
            patterns: ["/Users/huang/.testagent/agent-opencode/references/*", " /Users/huang/.testagent/agent-opencode/references/* "]
          }]
        }),
        { status: 200 }
      )
    );
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await expect(client.listSessionPermissions("ses_history")).resolves.toMatchObject([
      {
        requestId: "per_history",
        sessionId: "ses_history",
        type: "external_directory",
        patterns: ["/Users/huang/.testagent/agent-opencode/references/*"]
      }
    ]);
  });

  it("keeps the legacy singular permission pattern for old runtime payloads", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          traceId: "trace_fixed",
          data: [{ id: "per_legacy", permission: "edit", pattern: "src/**" }]
        }),
        { status: 200 }
      )
    );
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await expect(client.listSessionPermissions("ses_history")).resolves.toMatchObject([
      { requestId: "per_legacy", sessionId: "ses_history", type: "edit", pattern: "src/**", patterns: ["src/**"] }
    ]);
  });

  it("replies to session permission requests without exposing opencode URLs", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify({ success: true, traceId: "trace_fixed", data: { accepted: true } }), { status: 200 })
    );
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await client.replySessionPermission("ses_1234567890abcdef", "per_123", { decision: "once" });

    expect(fetcher).toHaveBeenCalledWith(
      "http://api/api/internal/platform/opencode-runtime/sessions/ses_1234567890abcdef/permissions/per_123/reply",
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
      "http://api/api/internal/platform/opencode-runtime/mcp/tools?workspaceId=wrk_1234567890abcdef",
      expect.any(Object)
    );
  });

  it("reads session messages for readonly transcript pages", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          traceId: "trace_fixed",
          data: {
            items: [{
              messageId: "msg_1",
              sessionId: "ses_1",
              role: "USER",
              content: "hello",
              contentKind: "SUMMARY",
              summaryStatus: "PARTIAL",
              summaryVersion: 1
            }],
            page: 1,
            size: 100,
            total: 1
          }
        }),
        { status: 200 }
      )
    );
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await expect(client.listSessionMessages("ses_1", 1, 100, { refresh: false })).resolves.toMatchObject({
      items: [{
        messageId: "msg_1",
        content: "hello",
        contentKind: "SUMMARY",
        summaryStatus: "PARTIAL",
        summaryVersion: 1
      }]
    });

    expect(fetcher).toHaveBeenCalledWith(
      "http://api/api/internal/platform/opencode-runtime/sessions/ses_1/messages?page=1&size=100&refresh=false",
      expect.any(Object)
    );
  });

  it("reads session tree messages through the agent-scoped history API", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          traceId: "trace_fixed",
          data: {
            sessionId: "ses_1",
            sessions: [],
            messagesBySessionId: {},
            childSessionIdByTaskPartId: {},
            events: [{ type: "message.updated", sessionId: "ses_1", payload: { message: { id: "msg_1", role: "assistant" } } }],
            historyRepresentation: "FULL",
            replayAvailable: true,
            detailsAvailableUntil: "2026-07-11T00:00:00Z"
          }
        }),
        { status: 200 }
      )
    );
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await expect(client.getSessionTreeMessages("ses_1")).resolves.toMatchObject({
      sessionId: "ses_1",
      events: [{ type: "message.updated" }],
      historyRepresentation: "FULL",
      replayAvailable: true,
      detailsAvailableUntil: "2026-07-11T00:00:00Z"
    });

    expect(fetcher).toHaveBeenCalledWith(
      "http://api/api/internal/agent/opencode/sessions/ses_1/session-tree/messages",
      expect.any(Object)
    );
  });

  it("reads the latest active run for session resume", async () => {
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

    await expect(client.getActiveRun("ses_1")).resolves.toMatchObject({ runId: "run_1", status: "RUNNING" });

    expect(fetcher).toHaveBeenCalledWith(
      "http://api/api/internal/platform/opencode-runtime/sessions/ses_1/active-run",
      expect.any(Object)
    );
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

    expect(fetcher.mock.calls[0]?.[0]).toBe("http://api/api/internal/platform/opencode-runtime/sessions?page=1&size=20&q=pin");
    expect(fetcher.mock.calls[1]?.[0]).toBe("http://api/api/internal/platform/opencode-runtime/sessions/ses_1");
    expect(fetcher.mock.calls[1]?.[1]).toEqual(expect.objectContaining({ method: "PATCH", body: JSON.stringify({ title: "Renamed", pinned: false }) }));
    expect(fetcher.mock.calls[2]?.[0]).toBe("http://api/api/internal/platform/opencode-runtime/sessions/ses_1");
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
            webSocketUrl: "/api/internal/platform/opencode-runtime/sessions/ses_1234567890abcdef/terminal/ws?ticket=pty_123"
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
      webSocketUrl: "/api/internal/platform/opencode-runtime/sessions/ses_1234567890abcdef/terminal/ws?ticket=pty_123"
    });

    expect(fetcher).toHaveBeenCalledWith(
      "http://api/api/internal/platform/opencode-runtime/sessions/ses_1234567890abcdef/terminal/tickets",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ workspaceId: "wrk_1234567890abcdef", cwd: "packages/app", cols: 120, rows: 32 })
      })
    );
  });

  it("creates server terminal tickets with target-bound confirmation", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify({
        success: true,
        traceId: "trace_fixed",
        data: { ticket: "pty_server", expiresAt: "2026-07-18T13:00:00Z", webSocketUrl: "wss://console/api/server/ws?ticket=pty_server" }
      }), { status: 200 })
    );
    const client = createBackendApiClient({ baseUrl: "https://console", fetcher, traceIdFactory: () => "trace_fixed" });

    await client.createServerTerminalTicket("server-a", {
      confirmationText: "SERVER@server-a",
      cols: 120,
      rows: 32
    });

    expect(fetcher).toHaveBeenCalledWith(
      "https://console/api/internal/platform/opencode-runtime/management/linux-servers/server-a/terminal/tickets",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ confirmationText: "SERVER@server-a", cols: 120, rows: 32 })
      })
    );
  });

  it("routes workspace file listing through target backend websocket", async () => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            success: true,
            traceId: "trace_fixed",
            data: {
              workspaceId: "wrk_1234567890abcdef",
              linuxServerId: "10.8.0.12",
              baseUrl: "http://10.8.0.12:8080",
              webSocketPath: "/api/internal/platform/workspace-management/file/ws",
              sameServer: true
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
              ticket: "wft_1234567890abcdef",
              expiresAt: "2026-06-26T10:00:00Z",
              webSocketUrl: "/api/internal/platform/workspace-management/file/ws?ticket=wft_1234567890abcdef"
            }
          }),
          { status: 200 }
        )
      );
    const sockets: FakeWorkspaceWebSocket[] = [];
    const client = createBackendApiClient({
      baseUrl: "http://api",
      apiToken: "token_123",
      fetcher,
      traceIdFactory: () => "trace_fixed",
      webSocketFactory: fakeWorkspaceWebSocketFactory(sockets)
    });

    await expect(client.listFiles("wrk_1234567890abcdef", "src")).resolves.toEqual([
      {
        path: "src/App.vue",
        name: "App.vue",
        type: "file",
        size: 42,
        modifiedAt: "2026-06-26T09:00:00Z"
      }
    ]);

    expect(fetcher.mock.calls.map((call) => call[0])).toEqual([
      "http://api/api/internal/platform/workspace-management/workspaces/wrk_1234567890abcdef/file-ws-route",
      "http://10.8.0.12:8080/api/internal/platform/workspace-management/file-ws/tickets"
    ]);
    expect(sockets[0]?.url).toBe("ws://10.8.0.12:8080/api/internal/platform/workspace-management/file/ws?ticket=wft_1234567890abcdef");
    expect(sockets[0]?.sentMessages[0]).toMatchObject({
      op: "workspace.list",
      params: { workspaceId: "wrk_1234567890abcdef", path: "src" }
    });
  });

  it("shares one workspace file connection while concurrent callers wait for open", async () => {
    let resolveRoute!: (response: Response) => void;
    const routeResponse = new Promise<Response>((resolve) => {
      resolveRoute = resolve;
    });
    const fetcher = vi.fn<typeof fetch>().mockImplementation(async (input) => {
      if (String(input).endsWith("/workspaces/wrk_concurrent/file-ws-route")) {
        return routeResponse;
      }
      return new Response(
        JSON.stringify({
          success: true,
          traceId: "trace_fixed",
          data: {
            ticket: "wft_concurrent",
            expiresAt: "2026-06-26T10:00:00Z",
            webSocketUrl: "/api/internal/platform/workspace-management/file/ws?ticket=wft_concurrent"
          }
        }),
        { status: 200 }
      );
    });
    const sockets: FakeWorkspaceWebSocket[] = [];
    const client = createBackendApiClient({
      baseUrl: "http://api",
      fetcher,
      traceIdFactory: () => "trace_fixed",
      webSocketFactory: manualWorkspaceWebSocketFactory(sockets)
    });

    const listRequest = client.listFiles("wrk_concurrent", "src");
    const readRequest = client.readFile("wrk_concurrent", "docs/design.md");

    expect(fetcher).toHaveBeenCalledTimes(1);
    resolveRoute(
      new Response(
        JSON.stringify({
          success: true,
          traceId: "trace_fixed",
          data: {
            workspaceId: "wrk_concurrent",
            linuxServerId: "linux-1",
            baseUrl: "http://10.8.0.12:8080",
            webSocketPath: "/api/internal/platform/workspace-management/file/ws",
            sameServer: true
          }
        }),
        { status: 200 }
      )
    );
    await vi.waitFor(() => expect(sockets).toHaveLength(1));
    expect(fetcher).toHaveBeenCalledTimes(2);
    expect(sockets[0]?.sentMessages).toEqual([]);

    sockets[0]?.openConnection();

    await expect(Promise.all([listRequest, readRequest])).resolves.toHaveLength(2);
    expect(sockets[0]?.sentMessages.map((message) => message.op)).toEqual(["workspace.list", "workspace.read"]);
  });

  it("maps composite workspace view list and readonly reference reads through workspace RPC", async () => {
    const fetcher = workspaceFileFetcher("wrk_view");
    const sockets: FakeWorkspaceWebSocket[] = [];
    const factory = ((url: string) => {
      const socket = new FakeWorkspaceWebSocket(url, false);
      socket.onSend = (message) => {
        queueMicrotask(() => {
          socket.onmessage?.({
            data: JSON.stringify({
              id: message.id,
              type: "result",
              data: message.op === "workspace.view.list"
                ? {
                    entries: [{
                      id: "ref:requirements:docs%2Fguide.md",
                      path: "docs/guide.md",
                      name: "guide.md",
                      directory: false,
                      size: 12,
                      locator: { kind: "REFERENCE", path: "docs/guide.md", referenceAlias: "requirements" },
                      source: "REFERENCE",
                      merged: true,
                      collision: false,
                      readonly: true,
                      referenceAliases: ["requirements"]
                    }],
                    warnings: [{ alias: "legacy", code: "REFERENCE_UNAVAILABLE", message: "副本不可用" }],
                    truncated: false
                  }
                : {
                    path: "docs/guide.md",
                    content: "reference",
                    size: 9,
                    readonly: true,
                    source: "REFERENCE",
                    referenceAlias: "requirements",
                    locator: { kind: "REFERENCE", path: "docs/guide.md", referenceAlias: "requirements" }
                  }
            })
          });
        });
      };
      sockets.push(socket);
      queueMicrotask(() => socket.openConnection());
      return socket;
    }) satisfies WorkspaceWebSocketFactory;
    const client = createBackendApiClient({
      baseUrl: "http://api",
      fetcher,
      traceIdFactory: () => "trace_fixed",
      webSocketFactory: factory
    });

    await expect(client.listWorkspaceView("wrk_view", { kind: "COMPOSITE", path: "" })).resolves.toEqual({
      entries: [expect.objectContaining({
        id: "ref:requirements:docs%2Fguide.md",
        type: "file",
        source: "REFERENCE",
        readonly: true
      })],
      warnings: [{ alias: "legacy", code: "REFERENCE_UNAVAILABLE", message: "副本不可用" }],
      truncated: false
    });
    await expect(client.readWorkspaceViewFile("wrk_view", {
      kind: "REFERENCE",
      path: "docs/guide.md",
      referenceAlias: "requirements"
    })).resolves.toMatchObject({
      path: "docs/guide.md",
      content: "reference",
      readonly: true,
      source: "REFERENCE",
      referenceAlias: "requirements"
    });

    expect(sockets[0]?.sentMessages).toEqual([
      expect.objectContaining({
        op: "workspace.view.list",
        params: { workspaceId: "wrk_view", locator: { kind: "COMPOSITE", path: "" } }
      }),
      expect.objectContaining({
        op: "workspace.view.read",
        params: {
          workspaceId: "wrk_view",
          locator: { kind: "REFERENCE", path: "docs/guide.md", referenceAlias: "requirements" }
        }
      })
    ]);
  });

  it("rejects a workspace connection closed before open and reconnects on the next call", async () => {
    const fetcher = vi.fn<typeof fetch>().mockImplementation(async (input) => {
      const isRoute = String(input).includes("/workspaces/wrk_reconnect/file-ws-route");
      return new Response(
        JSON.stringify({
          success: true,
          traceId: "trace_fixed",
          data: isRoute
            ? {
                workspaceId: "wrk_reconnect",
                linuxServerId: "linux-1",
                baseUrl: "http://10.8.0.12:8080",
                webSocketPath: "/api/internal/platform/workspace-management/file/ws",
                sameServer: true
              }
            : {
                ticket: `wft_reconnect_${fetcher.mock.calls.length}`,
                expiresAt: "2026-06-26T10:00:00Z",
                webSocketUrl: `/api/internal/platform/workspace-management/file/ws?ticket=wft_reconnect_${fetcher.mock.calls.length}`
              }
        }),
        { status: 200 }
      );
    });
    const sockets: FakeWorkspaceWebSocket[] = [];
    const client = createBackendApiClient({
      baseUrl: "http://api",
      fetcher,
      traceIdFactory: () => "trace_fixed",
      webSocketFactory: manualWorkspaceWebSocketFactory(sockets)
    });

    let firstError: unknown;
    const firstRequest = client.listFiles("wrk_reconnect", "src").catch((error: unknown) => {
      firstError = error;
    });
    await vi.waitFor(() => expect(sockets).toHaveLength(1));

    sockets[0]?.close();

    await vi.waitFor(() => expect(firstError).toBeInstanceOf(Error));
    await firstRequest;
    expect((firstError as Error).message).toContain("已关闭");

    const secondRequest = client.listFiles("wrk_reconnect", "src");
    await vi.waitFor(() => expect(sockets).toHaveLength(2));
    sockets[1]?.openConnection();

    await expect(secondRequest).resolves.toHaveLength(1);
    expect(fetcher).toHaveBeenCalledTimes(4);
  });

  it("rejects a workspace connection errored before open", async () => {
    const fetcher = workspaceFileFetcher("wrk_connect_error");
    const sockets: FakeWorkspaceWebSocket[] = [];
    const client = createBackendApiClient({
      baseUrl: "http://api",
      fetcher,
      traceIdFactory: () => "trace_fixed",
      webSocketFactory: manualWorkspaceWebSocketFactory(sockets)
    });
    const request = client.listFiles("wrk_connect_error", "src");
    await vi.waitFor(() => expect(sockets).toHaveLength(1));

    sockets[0]?.failConnection();

    await expect(request).rejects.toThrow("连接失败");
  });

  it("does not let a stale workspace socket close evict a newer active socket", async () => {
    const fetcher = vi.fn<typeof fetch>().mockImplementation(async (input) => {
      const isRoute = String(input).includes("/workspaces/wrk_stale_close/file-ws-route");
      return new Response(
        JSON.stringify({
          success: true,
          traceId: "trace_fixed",
          data: isRoute
            ? {
                workspaceId: "wrk_stale_close",
                linuxServerId: "linux-1",
                baseUrl: "http://10.8.0.12:8080",
                webSocketPath: "/api/internal/platform/workspace-management/file/ws",
                sameServer: true
              }
            : {
                ticket: `wft_stale_${fetcher.mock.calls.length}`,
                expiresAt: "2026-06-26T10:00:00Z",
                webSocketUrl: `/api/internal/platform/workspace-management/file/ws?ticket=wft_stale_${fetcher.mock.calls.length}`
              }
        }),
        { status: 200 }
      );
    });
    const sockets: FakeWorkspaceWebSocket[] = [];
    const client = createBackendApiClient({
      baseUrl: "http://api",
      fetcher,
      traceIdFactory: () => "trace_fixed",
      webSocketFactory: fakeWorkspaceWebSocketFactory(sockets)
    });

    await client.listFiles("wrk_stale_close", "src");
    sockets[0]?.close();
    await client.listFiles("wrk_stale_close", "src");

    sockets[0]?.close();
    await client.listFiles("wrk_stale_close", "src");

    expect(sockets).toHaveLength(2);
    expect(fetcher).toHaveBeenCalledTimes(4);
    expect(sockets[1]?.sentMessages).toHaveLength(2);
  });

  it("reads and mutates workspace files through the same target backend websocket", async () => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            success: true,
            traceId: "trace_fixed",
            data: {
              workspaceId: "wrk_1234567890abcdef",
              linuxServerId: "10.8.0.12",
              baseUrl: "http://10.8.0.12:8080",
              webSocketPath: "/api/internal/platform/workspace-management/file/ws",
              sameServer: true
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
              ticket: "wft_1234567890abcdef",
              expiresAt: "2026-06-26T10:00:00Z",
              webSocketUrl: "/api/internal/platform/workspace-management/file/ws?ticket=wft_1234567890abcdef"
            }
          }),
          { status: 200 }
        )
      );
    const sockets: FakeWorkspaceWebSocket[] = [];
    const client = createBackendApiClient({
      baseUrl: "http://api",
      apiToken: "token_123",
      fetcher,
      traceIdFactory: () => "trace_fixed",
      webSocketFactory: fakeWorkspaceWebSocketFactory(sockets)
    });

    await expect(client.readFile("wrk_1234567890abcdef", "docs/design.md")).resolves.toMatchObject({
      path: "docs/design.md",
      content: "# 设计"
    });
    await expect(client.readFilePreviewChunk(
      "wrk_1234567890abcdef",
      "docs/large.log",
      { offset: 524288, expectedSize: 1048576, expectedLastModifiedMillis: 1234 }
    )).resolves.toEqual({
      path: "docs/large.log",
      content: "preview chunk",
      offset: 524288,
      nextOffset: 524301,
      size: 1048576,
      eof: false,
      warningThresholdBytes: 5242880,
      lastModifiedMillis: 1234
    });
    await client.renameWorkspaceFile("wrk_1234567890abcdef", "docs/design.md", "详细设计.md");
    await client.copyWorkspaceFile("wrk_1234567890abcdef", "docs/design.md", "backup/design.md");
    await client.moveWorkspaceFile("wrk_1234567890abcdef", "docs/design.md", "archive/design.md");
    const uploadProgress: Array<{ uploadedBytes: number; totalBytes: number }> = [];
    await client.uploadWorkspaceFile(
      "wrk_1234567890abcdef",
      "assets/icon.bin",
      new Blob([new Uint8Array([0, 1, 2, 255])]),
      (progress) => uploadProgress.push(progress)
    );

    expect(sockets[0]?.sentMessages).toEqual([
      expect.objectContaining({
        op: "workspace.read",
        params: { workspaceId: "wrk_1234567890abcdef", path: "docs/design.md" }
      }),
      expect.objectContaining({
        op: "workspace.read.chunk",
        params: {
          workspaceId: "wrk_1234567890abcdef",
          path: "docs/large.log",
          offset: 524288,
          expectedSize: 1048576,
          expectedLastModifiedMillis: 1234
        }
      }),
      expect.objectContaining({
        op: "workspace.rename",
        params: { workspaceId: "wrk_1234567890abcdef", path: "docs/design.md", name: "详细设计.md" }
      }),
      expect.objectContaining({
        op: "workspace.copy",
        params: { workspaceId: "wrk_1234567890abcdef", sourcePath: "docs/design.md", targetPath: "backup/design.md" }
      }),
      expect.objectContaining({
        op: "workspace.move",
        params: { workspaceId: "wrk_1234567890abcdef", sourcePath: "docs/design.md", targetPath: "archive/design.md" }
      }),
      expect.objectContaining({
        op: "workspace.upload.begin",
        params: { workspaceId: "wrk_1234567890abcdef", path: "assets/icon.bin", size: 4 }
      }),
      expect.objectContaining({
        op: "workspace.upload.chunk",
        params: { workspaceId: "wrk_1234567890abcdef", uploadId: "upl_test_1", index: 0, contentBase64: "AAE=" }
      }),
      expect.objectContaining({
        op: "workspace.upload.chunk",
        params: { workspaceId: "wrk_1234567890abcdef", uploadId: "upl_test_1", index: 1, contentBase64: "Av8=" }
      }),
      expect.objectContaining({
        op: "workspace.upload.complete",
        params: { workspaceId: "wrk_1234567890abcdef", uploadId: "upl_test_1" }
      })
    ]);
    expect(uploadProgress).toEqual([
      { uploadedBytes: 0, totalBytes: 4 },
      { uploadedBytes: 2, totalBytes: 4 },
      { uploadedBytes: 4, totalBytes: 4 }
    ]);
  });

  it("reconnects once when workspace.read fails with an explicit transport close", async () => {
    const fetcher = vi.fn<typeof fetch>().mockImplementation(async (input) => {
      const isRoute = String(input).includes("/workspaces/wrk_read_retry/file-ws-route");
      return new Response(
        JSON.stringify({
          success: true,
          traceId: "trace_fixed",
          data: isRoute
            ? {
                workspaceId: "wrk_read_retry",
                linuxServerId: "linux-1",
                baseUrl: "http://10.8.0.12:8080",
                webSocketPath: "/api/internal/platform/workspace-management/file/ws",
                sameServer: true
              }
            : {
                ticket: `wft_read_retry_${fetcher.mock.calls.length}`,
                expiresAt: "2026-06-26T10:00:00Z",
                webSocketUrl: `/api/internal/platform/workspace-management/file/ws?ticket=wft_read_retry_${fetcher.mock.calls.length}`
              }
        }),
        { status: 200 }
      );
    });
    const sockets: FakeWorkspaceWebSocket[] = [];
    const client = createBackendApiClient({
      baseUrl: "http://api",
      fetcher,
      traceIdFactory: () => "trace_fixed",
      webSocketFactory: ((url: string) => {
        const socket = new FakeWorkspaceWebSocket(url);
        if (sockets.length === 0) {
          socket.onSend = () => socket.close();
        }
        sockets.push(socket);
        return socket;
      }) satisfies WorkspaceWebSocketFactory
    });

    await expect(client.readFile("wrk_read_retry", "docs/design.md")).resolves.toMatchObject({
      path: "docs/design.md",
      content: "# 设计"
    });

    expect(sockets).toHaveLength(2);
    expect(fetcher).toHaveBeenCalledTimes(4);
    expect(sockets.map((socket) => socket.sentMessages.map((message) => message.op))).toEqual([
      ["workspace.read"],
      ["workspace.read"]
    ]);
  });

  it("returns the second workspace.read transport failure without a third attempt", async () => {
    const fetcher = workspaceFileFetcher("wrk_read_twice");
    const sockets: FakeWorkspaceWebSocket[] = [];
    const client = createBackendApiClient({
      baseUrl: "http://api",
      fetcher,
      traceIdFactory: () => "trace_fixed",
      webSocketFactory: ((url: string) => {
        const socket = new FakeWorkspaceWebSocket(url);
        socket.onSend = () => socket.close();
        sockets.push(socket);
        return socket;
      }) satisfies WorkspaceWebSocketFactory
    });

    await expect(client.readFile("wrk_read_twice", "docs/design.md")).rejects.toThrow("已关闭");

    expect(sockets).toHaveLength(2);
    expect(fetcher).toHaveBeenCalledTimes(4);
  });

  it("does not retry workspace.read business errors", async () => {
    const fetcher = workspaceFileFetcher("wrk_business_error");
    const sockets: FakeWorkspaceWebSocket[] = [];
    const client = createBackendApiClient({
      baseUrl: "http://api",
      fetcher,
      traceIdFactory: () => "trace_fixed",
      webSocketFactory: ((url: string) => {
        const socket = new FakeWorkspaceWebSocket(url);
        socket.onSend = (message) => {
          queueMicrotask(() => {
            socket.onmessage?.({
              data: JSON.stringify({
                id: message.id,
                type: "error",
                code: "FILE_NOT_FOUND",
                message: "文件不存在",
                traceId: "trace_file_missing"
              })
            });
          });
        };
        sockets.push(socket);
        return socket;
      }) satisfies WorkspaceWebSocketFactory
    });

    await expect(client.readFile("wrk_business_error", "missing.md")).rejects.toMatchObject({
      name: "BackendApiError",
      code: "FILE_NOT_FOUND"
    });

    expect(sockets).toHaveLength(1);
    expect(fetcher).toHaveBeenCalledTimes(2);
  });

  it("does not retry workspace.read request timeouts", async () => {
    const fetcher = workspaceFileFetcher("wrk_read_timeout");
    const sockets: FakeWorkspaceWebSocket[] = [];
    const client = createBackendApiClient({
      baseUrl: "http://api",
      fetcher,
      traceIdFactory: () => "trace_fixed",
      webSocketFactory: manualWorkspaceWebSocketFactory(sockets)
    });
    const request = client.readFile("wrk_read_timeout", "docs/design.md");
    await vi.waitFor(() => expect(sockets).toHaveLength(1));
    sockets[0]!.onSend = () => undefined;

    vi.useFakeTimers();
    try {
      sockets[0]?.openConnection();
      const rejection = expect(request).rejects.toMatchObject({
        name: "BackendApiError",
        code: "REQUEST_TIMEOUT"
      });

      await vi.advanceTimersByTimeAsync(30000);
      await rejection;
      expect(sockets).toHaveLength(1);
      expect(fetcher).toHaveBeenCalledTimes(2);
    } finally {
      vi.useRealTimers();
    }
  });

  it("does not retry workspace.write after a transport close", async () => {
    const fetcher = workspaceFileFetcher("wrk_write_failure");
    const sockets: FakeWorkspaceWebSocket[] = [];
    const client = createBackendApiClient({
      baseUrl: "http://api",
      fetcher,
      traceIdFactory: () => "trace_fixed",
      webSocketFactory: ((url: string) => {
        const socket = new FakeWorkspaceWebSocket(url);
        socket.onSend = () => socket.close();
        sockets.push(socket);
        return socket;
      }) satisfies WorkspaceWebSocketFactory
    });

    await expect(client.writeFile("wrk_write_failure", "docs/design.md", "changed")).rejects.toThrow("已关闭");

    expect(sockets).toHaveLength(1);
    expect(fetcher).toHaveBeenCalledTimes(2);
  });

  it("rejects a synchronous websocket send failure immediately and clears its request timer", async () => {
    const fetcher = vi.fn<typeof fetch>().mockImplementation(async (input) => {
      const isRoute = String(input).includes("/workspaces/wrk_send_failure/file-ws-route");
      return new Response(
        JSON.stringify({
          success: true,
          traceId: "trace_fixed",
          data: isRoute
            ? {
                workspaceId: "wrk_send_failure",
                linuxServerId: "linux-1",
                baseUrl: "http://10.8.0.12:8080",
                webSocketPath: "/api/internal/platform/workspace-management/file/ws",
                sameServer: true
              }
            : {
                ticket: "wft_send_failure",
                expiresAt: "2026-06-26T10:00:00Z",
                webSocketUrl: "/api/internal/platform/workspace-management/file/ws?ticket=wft_send_failure"
              }
        }),
        { status: 200 }
      );
    });
    const sockets: FakeWorkspaceWebSocket[] = [];
    const client = createBackendApiClient({
      baseUrl: "http://api",
      fetcher,
      traceIdFactory: () => "trace_fixed",
      webSocketFactory: manualWorkspaceWebSocketFactory(sockets)
    });
    const request = client.writeFile("wrk_send_failure", "docs/design.md", "changed");
    await vi.waitFor(() => expect(sockets).toHaveLength(1));
    sockets[0]!.onSend = () => {
      throw new Error("send exploded");
    };

    vi.useFakeTimers();
    try {
      sockets[0]?.openConnection();

      await expect(request).rejects.toThrow("send exploded");
      expect(vi.getTimerCount()).toBe(0);
      expect(fetcher).toHaveBeenCalledTimes(2);
      expect(sockets).toHaveLength(1);
      expect(sockets[0]?.closeCalls).toBe(1);
      expect(sockets[0]?.closed).toBe(true);
    } finally {
      vi.useRealTimers();
    }
  });

  it("keeps the original send failure when closing the unusable socket also throws", async () => {
    const fetcher = workspaceFileFetcher("wrk_send_close_failure");
    const sockets: FakeWorkspaceWebSocket[] = [];
    const client = createBackendApiClient({
      baseUrl: "http://api",
      fetcher,
      traceIdFactory: () => "trace_fixed",
      webSocketFactory: manualWorkspaceWebSocketFactory(sockets)
    });
    const request = client.writeFile("wrk_send_close_failure", "docs/design.md", "changed");
    await vi.waitFor(() => expect(sockets).toHaveLength(1));
    sockets[0]!.onSend = () => {
      throw new Error("send exploded");
    };
    sockets[0]!.closeError = new Error("close exploded");

    vi.useFakeTimers();
    try {
      sockets[0]?.openConnection();

      await expect(request).rejects.toThrow("send exploded");
      expect(vi.getTimerCount()).toBe(0);
      expect(sockets[0]?.closeCalls).toBe(1);
      expect(sockets[0]?.closed).toBe(false);
    } finally {
      vi.useRealTimers();
    }
  });

  it("routes public agent config files through target backend websocket", async () => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            success: true,
            traceId: "trace_fixed",
            data: {
              scope: "PUBLIC",
              worktreeId: "agw_1234567890abcdef",
              linuxServerId: "linux-2",
              baseUrl: "http://10.8.0.13:8080",
              webSocketPath: "/api/internal/platform/workspace-management/file/ws",
              sameServer: false
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
              ticket: "wft_agentconfig",
              expiresAt: "2026-06-26T10:00:00Z",
              webSocketUrl: "/api/internal/platform/workspace-management/file/ws?ticket=wft_agentconfig"
            }
          }),
          { status: 200 }
        )
      );
    const sockets: FakeWorkspaceWebSocket[] = [];
    const client = createBackendApiClient({
      baseUrl: "http://api",
      apiToken: "token_123",
      fetcher,
      traceIdFactory: () => "trace_fixed",
      webSocketFactory: fakeWorkspaceWebSocketFactory(sockets)
    });

    await expect(client.listPublicAgentFiles("opencode/agents", "agw_1234567890abcdef", "linux-2")).resolves.toEqual([
      {
        path: "opencode/agents/review.md",
        name: "review.md",
        displayName: "测试案例审核",
        displayNameEn: "Test Case Review",
        type: "file",
        size: 18,
        modifiedAt: "2026-06-26T09:00:00Z"
      }
    ]);
    await expect(client.renamePublicAgentFile(
      "opencode/agents/review.md",
      "shared-review.md",
      "agw_1234567890abcdef",
      "linux-2"
    )).resolves.toBeNull();
    await expect(client.copyPublicAgentFile(
      "opencode/agents/review.md",
      "opencode/skills/review.md",
      "agw_1234567890abcdef",
      "linux-2"
    )).resolves.toBeNull();
    await expect(client.movePublicAgentFile(
      "opencode/agents/review.md",
      "opencode/templates/review.md",
      "agw_1234567890abcdef",
      "linux-2"
    )).resolves.toBeNull();
    await expect(client.uploadPublicAgentFile(
      "opencode/agents/icon.bin",
      new Blob([new Uint8Array([0, 1, 2, 255])]),
      "agw_1234567890abcdef",
      "linux-2"
    )).resolves.toBeUndefined();
    await expect(client.deletePublicAgentFile(
      "opencode/agents/obsolete.md",
      "agw_1234567890abcdef",
      "linux-2"
    )).resolves.toBeNull();

    expect(fetcher.mock.calls.map((call) => call[0])).toEqual([
      "http://api/api/internal/platform/workspace-management/agent-config/file-ws-route",
      "http://10.8.0.13:8080/api/internal/platform/workspace-management/file-ws/tickets"
    ]);
    expect(JSON.parse(String(fetcher.mock.calls[0]?.[1]?.body))).toEqual({
      scope: "PUBLIC",
      worktreeId: "agw_1234567890abcdef",
      linuxServerId: "linux-2"
    });
    expect(JSON.parse(String(fetcher.mock.calls[1]?.[1]?.body))).toEqual({
      linuxServerId: "linux-2",
      mode: "agent-config",
      scope: "PUBLIC",
      worktreeId: "agw_1234567890abcdef"
    });
    expect(sockets[0]?.url).toBe("ws://10.8.0.13:8080/api/internal/platform/workspace-management/file/ws?ticket=wft_agentconfig");
    expect(sockets[0]?.sentMessages[0]).toMatchObject({
      op: "agent-config.list",
      params: { scope: "PUBLIC", path: "opencode/agents", worktreeId: "agw_1234567890abcdef" }
    });
    expect(sockets[0]?.sentMessages[1]).toMatchObject({
      op: "agent-config.rename",
      params: {
        scope: "PUBLIC",
        path: "opencode/agents/review.md",
        name: "shared-review.md",
        worktreeId: "agw_1234567890abcdef"
      }
    });
    expect(sockets[0]?.sentMessages[2]).toMatchObject({
      op: "agent-config.copy",
      params: {
        scope: "PUBLIC",
        sourcePath: "opencode/agents/review.md",
        targetPath: "opencode/skills/review.md",
        worktreeId: "agw_1234567890abcdef"
      }
    });
    expect(sockets[0]?.sentMessages[3]).toMatchObject({
      op: "agent-config.move",
      params: {
        scope: "PUBLIC",
        sourcePath: "opencode/agents/review.md",
        targetPath: "opencode/templates/review.md",
        worktreeId: "agw_1234567890abcdef"
      }
    });
    expect(sockets[0]?.sentMessages[4]).toMatchObject({
      op: "agent-config.upload.begin",
      params: {
        scope: "PUBLIC",
        path: "opencode/agents/icon.bin",
        size: 4,
        worktreeId: "agw_1234567890abcdef"
      }
    });
    expect(sockets[0]?.sentMessages.slice(5, 8).map((message) => message.op)).toEqual([
      "agent-config.upload.chunk",
      "agent-config.upload.chunk",
      "agent-config.upload.complete"
    ]);
    expect(sockets[0]?.sentMessages[8]).toMatchObject({
      op: "agent-config.delete",
      params: {
        scope: "PUBLIC",
        path: "opencode/agents/obsolete.md",
        worktreeId: "agw_1234567890abcdef"
      }
    });
  });

  it("shares one agent-config connection while concurrent callers wait for open", async () => {
    let resolveRoute!: (response: Response) => void;
    const routeResponse = new Promise<Response>((resolve) => {
      resolveRoute = resolve;
    });
    const fetcher = vi.fn<typeof fetch>().mockImplementation(async (input) => {
      if (String(input).endsWith("/agent-config/file-ws-route")) {
        return routeResponse;
      }
      return new Response(
        JSON.stringify({
          success: true,
          traceId: "trace_fixed",
          data: {
            ticket: "wft_agent_concurrent",
            expiresAt: "2026-06-26T10:00:00Z",
            webSocketUrl: "/api/internal/platform/workspace-management/file/ws?ticket=wft_agent_concurrent"
          }
        }),
        { status: 200 }
      );
    });
    const sockets: FakeWorkspaceWebSocket[] = [];
    const client = createBackendApiClient({
      baseUrl: "http://api",
      fetcher,
      traceIdFactory: () => "trace_fixed",
      webSocketFactory: manualWorkspaceWebSocketFactory(sockets)
    });

    const listRequest = client.listPublicAgentFiles("opencode/agents", "agw_concurrent", "linux-2");
    const readRequest = client.readPublicAgentFile("opencode/agents/review.md", "agw_concurrent", "linux-2");

    expect(fetcher).toHaveBeenCalledTimes(1);
    resolveRoute(
      new Response(
        JSON.stringify({
          success: true,
          traceId: "trace_fixed",
          data: {
            scope: "PUBLIC",
            worktreeId: "agw_concurrent",
            linuxServerId: "linux-2",
            baseUrl: "http://10.8.0.13:8080",
            webSocketPath: "/api/internal/platform/workspace-management/file/ws",
            sameServer: false
          }
        }),
        { status: 200 }
      )
    );
    await vi.waitFor(() => expect(sockets).toHaveLength(1));
    expect(fetcher).toHaveBeenCalledTimes(2);
    expect(sockets[0]?.sentMessages).toEqual([]);

    sockets[0]?.openConnection();

    await expect(Promise.all([listRequest, readRequest])).resolves.toHaveLength(2);
    expect(sockets[0]?.sentMessages.map((message) => message.op)).toEqual(["agent-config.list", "agent-config.read"]);
  });

  it("reconnects once when agent-config.read fails with an explicit transport close", async () => {
    const fetcher = vi.fn<typeof fetch>().mockImplementation(async (input) => {
      const isRoute = String(input).endsWith("/agent-config/file-ws-route");
      return new Response(
        JSON.stringify({
          success: true,
          traceId: "trace_fixed",
          data: isRoute
            ? {
                scope: "PUBLIC",
                worktreeId: "agw_read_retry",
                linuxServerId: "linux-2",
                baseUrl: "http://10.8.0.13:8080",
                webSocketPath: "/api/internal/platform/workspace-management/file/ws",
                sameServer: false
              }
            : {
                ticket: `wft_agent_retry_${fetcher.mock.calls.length}`,
                expiresAt: "2026-06-26T10:00:00Z",
                webSocketUrl: `/api/internal/platform/workspace-management/file/ws?ticket=wft_agent_retry_${fetcher.mock.calls.length}`
              }
        }),
        { status: 200 }
      );
    });
    const sockets: FakeWorkspaceWebSocket[] = [];
    const client = createBackendApiClient({
      baseUrl: "http://api",
      fetcher,
      traceIdFactory: () => "trace_fixed",
      webSocketFactory: ((url: string) => {
        const socket = new FakeWorkspaceWebSocket(url);
        if (sockets.length === 0) {
          socket.onSend = () => socket.close();
        }
        sockets.push(socket);
        return socket;
      }) satisfies WorkspaceWebSocketFactory
    });

    await expect(
      client.readPublicAgentFile("opencode/agents/review.md", "agw_read_retry", "linux-2")
    ).resolves.toMatchObject({
      path: "review.md",
      content: "agent content"
    });

    expect(sockets).toHaveLength(2);
    expect(fetcher).toHaveBeenCalledTimes(4);
    expect(sockets.map((socket) => socket.sentMessages.map((message) => message.op))).toEqual([
      ["agent-config.read"],
      ["agent-config.read"]
    ]);
  });

  it("does not let a stale agent-config socket close evict a newer active socket", async () => {
    const fetcher = vi.fn<typeof fetch>().mockImplementation(async (input) => {
      const isRoute = String(input).endsWith("/agent-config/file-ws-route");
      return new Response(
        JSON.stringify({
          success: true,
          traceId: "trace_fixed",
          data: isRoute
            ? {
                scope: "PUBLIC",
                worktreeId: "agw_stale_close",
                linuxServerId: "linux-2",
                baseUrl: "http://10.8.0.13:8080",
                webSocketPath: "/api/internal/platform/workspace-management/file/ws",
                sameServer: false
              }
            : {
                ticket: `wft_agent_stale_${fetcher.mock.calls.length}`,
                expiresAt: "2026-06-26T10:00:00Z",
                webSocketUrl: `/api/internal/platform/workspace-management/file/ws?ticket=wft_agent_stale_${fetcher.mock.calls.length}`
              }
        }),
        { status: 200 }
      );
    });
    const sockets: FakeWorkspaceWebSocket[] = [];
    const client = createBackendApiClient({
      baseUrl: "http://api",
      fetcher,
      traceIdFactory: () => "trace_fixed",
      webSocketFactory: fakeWorkspaceWebSocketFactory(sockets)
    });

    await client.listPublicAgentFiles("opencode/agents", "agw_stale_close", "linux-2");
    sockets[0]?.close();
    await client.listPublicAgentFiles("opencode/agents", "agw_stale_close", "linux-2");

    sockets[0]?.close();
    await client.listPublicAgentFiles("opencode/agents", "agw_stale_close", "linux-2");

    expect(sockets).toHaveLength(2);
    expect(fetcher).toHaveBeenCalledTimes(4);
    expect(sockets[1]?.sentMessages).toHaveLength(2);
  });

  it("routes workspace agent config read, write, rename and delete through one file websocket", async () => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            success: true,
            traceId: "trace_fixed",
            data: {
              scope: "WORKSPACE",
              workspaceId: "wrk_1234567890abcdef",
              linuxServerId: "linux-1",
              baseUrl: "http://10.8.0.12:8080",
              webSocketPath: "/api/internal/platform/workspace-management/file/ws",
              sameServer: true
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
              ticket: "wft_workspace_agentconfig",
              expiresAt: "2026-06-26T10:00:00Z",
              webSocketUrl: "/api/internal/platform/workspace-management/file/ws?ticket=wft_workspace_agentconfig"
            }
          }),
          { status: 200 }
        )
      );
    const sockets: FakeWorkspaceWebSocket[] = [];
    const client = createBackendApiClient({
      baseUrl: "http://api",
      fetcher,
      traceIdFactory: () => "trace_fixed",
      webSocketFactory: fakeWorkspaceWebSocketFactory(sockets)
    });

    await expect(client.readWorkspaceAgentFile("wrk_1234567890abcdef", "review.md")).resolves.toMatchObject({
      path: "review.md",
      content: "agent content",
      encoding: "utf-8"
    });
    await expect(client.writeWorkspaceAgentFile("wrk_1234567890abcdef", "review.md", "changed")).resolves.toBeNull();
    await expect(
      client.renameWorkspaceAgentFile("wrk_1234567890abcdef", "review.md", "renamed.md")
    ).resolves.toBeNull();
    await expect(client.copyWorkspaceAgentFile(
      "wrk_1234567890abcdef", "agents/review.md", "skills/review.md"
    )).resolves.toBeNull();
    await expect(client.moveWorkspaceAgentFile(
      "wrk_1234567890abcdef", "agents/review.md", "tools/review.md"
    )).resolves.toBeNull();
    await expect(client.deleteWorkspaceAgentFile("wrk_1234567890abcdef", "skills/obsolete")).resolves.toBeNull();

    expect(fetcher).toHaveBeenCalledTimes(2);
    expect(JSON.parse(String(fetcher.mock.calls[1]?.[1]?.body))).toEqual({
      workspaceId: "wrk_1234567890abcdef",
      linuxServerId: "linux-1",
      mode: "agent-config",
      scope: "WORKSPACE"
    });
    expect(sockets).toHaveLength(1);
    expect(sockets[0]?.sentMessages.map((message) => message.op)).toEqual([
      "agent-config.read",
      "agent-config.write",
      "agent-config.rename",
      "agent-config.copy",
      "agent-config.move",
      "agent-config.delete"
    ]);
  });

  it("discards public and application Agent files through their Git worktree endpoints", async () => {
    const fetcher = vi.fn<typeof fetch>()
      .mockImplementation(async () => new Response(
        JSON.stringify({ success: true, traceId: "trace_fixed", data: null }),
        { status: 200 }
      ));
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await expect(client.discardPublicAgentFiles(
      ["opencode/agents/review.md"],
      "agw_public"
    )).resolves.toBeNull();
    await expect(client.discardWorkspaceAgentFiles(
      "wrk_feature",
      ["agents/review.md"],
      undefined
    )).resolves.toBeNull();

    expect(fetcher.mock.calls[0]?.[0]).toBe(
      "http://api/api/internal/platform/workspace-management/agent-config/public/discard"
    );
    expect(JSON.parse(String(fetcher.mock.calls[0]?.[1]?.body))).toEqual({
      files: ["opencode/agents/review.md"],
      worktreeId: "agw_public"
    });
    expect(fetcher.mock.calls[1]?.[0]).toBe(
      "http://api/api/internal/platform/workspace-management/agent-config/workspaces/wrk_feature/discard"
    );
    expect(JSON.parse(String(fetcher.mock.calls[1]?.[1]?.body))).toEqual({
      files: ["agents/review.md"]
    });
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

  it("manages public agent repository status and routes public worktree creation to a selected server", async () => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            success: true,
            traceId: "trace_fixed",
            data: [
              {
                linuxServerId: "linux-1",
                serverName: "linux-1",
                gitRootPath: "/data/opencode-public-config",
                configDirPath: "/data/opencode-public-config/opencode",
                worktreeRootPath: "/data/opencode-public-worktrees",
                status: "UNINITIALIZED",
                initialized: false,
                initializationAllowed: true,
                currentBranch: null,
                commitHash: null,
                message: "未初始化"
              }
            ]
          }),
          { status: 200 }
        )
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            success: true,
            traceId: "trace_fixed",
            data: [
              {
                worktreeId: "agw_1234567890abcdef",
                scope: "PUBLIC",
                workspaceId: null,
                linuxServerId: "linux-1",
                worktreeName: "change-agent-md-20260628",
                branch: "main",
                rootPath: "/data/opencode-public-worktrees/change-agent-md-20260628",
                agentDirectory: "/data/opencode-public-worktrees/change-agent-md-20260628/opencode/agent",
                status: "ACTIVE",
                createdAt: "2026-06-28T00:00:00Z",
                updatedAt: "2026-06-28T00:00:00Z",
                createdByUserId: "usr_admin",
                createdByUsername: "admin"
              }
            ]
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
              linuxServerId: "linux-1",
              serverName: "linux-1",
              gitRootPath: "/data/opencode-public-config",
              configDirPath: "/data/opencode-public-config/opencode",
              worktreeRootPath: "/data/opencode-public-worktrees",
              status: "READY",
              initialized: true,
              initializationAllowed: true,
              currentBranch: "main",
              commitHash: "abc1234",
              message: "已初始化"
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
              worktreeId: "acw_1234567890abcdef",
              scope: "PUBLIC",
              workspaceId: null,
              linuxServerId: "linux-1",
              worktreeName: "change-agent-md-20260628",
              branch: "main",
              rootPath: "/data/opencode-public-worktrees/change-agent-md-20260628",
              agentDirectory: "/data/opencode-public-worktrees/change-agent-md-20260628/opencode/agent",
              status: "ACTIVE",
              createdAt: "2026-06-28T00:00:00Z",
              updatedAt: "2026-06-28T00:00:00Z"
            }
          }),
          { status: 200 }
        )
      );
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await expect(client.listPublicAgentRepositories()).resolves.toHaveLength(1);
    await expect(client.listPublicAgentWorktrees("linux-1")).resolves.toMatchObject([
      {
        worktreeId: "agw_1234567890abcdef",
        createdByUserId: "usr_admin",
        createdByUsername: "admin"
      }
    ]);
    await expect(client.initializePublicAgentRepository("linux-1", "main", "aco_init")).resolves.toMatchObject({
      linuxServerId: "linux-1",
      initialized: true
    });
    await expect(
      client.createPublicAgentWorktree({
        baseName: "change-agent-md",
        branch: "main",
        linuxServerId: "linux-1",
        operationId: "aco_worktree"
      })
    ).resolves.toMatchObject({ worktreeId: "acw_1234567890abcdef", linuxServerId: "linux-1" });

    expect(fetcher.mock.calls.map((call) => call[0])).toEqual([
      "http://api/api/internal/platform/workspace-management/agent-config/public/repositories",
      "http://api/api/internal/platform/workspace-management/agent-config/public/worktrees?linuxServerId=linux-1",
      "http://api/api/internal/platform/workspace-management/agent-config/public/repositories/linux-1/initialize",
      "http://api/api/internal/platform/workspace-management/agent-config/public/worktrees"
    ]);
    expect(JSON.parse(String(fetcher.mock.calls[2]?.[1]?.body))).toEqual({ branch: "main", operationId: "aco_init" });
    expect(JSON.parse(String(fetcher.mock.calls[3]?.[1]?.body))).toEqual({
      baseName: "change-agent-md",
      branch: "main",
      linuxServerId: "linux-1",
      operationId: "aco_worktree"
    });
  });

  it("waits for agent config progress websocket open before resolving", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          success: true,
          traceId: "trace_fixed",
          data: {
            ticket: "agt_progress",
            expiresAt: "2026-06-26T10:00:00Z",
            webSocketUrl: "/api/internal/platform/workspace-management/agent-config/operations/aco_progress/ws?ticket=agt_progress"
          }
        }),
        { status: 200 }
      )
    );
    const socket: {
      onopen: WebSocketEventHandler;
      onmessage: WebSocketEventHandler;
      onerror: WebSocketEventHandler;
      onclose: WebSocketEventHandler;
      readyState: number;
      send: (payload: string) => void;
      close: () => void;
    } = {
      onopen: null,
      onmessage: null,
      onerror: null,
      onclose: null,
      readyState: 0,
      send: () => undefined,
      close: () => undefined
    };
    const progressWebSocketFactory: WorkspaceWebSocketFactory = () => socket;
    const client = createBackendApiClient({
      baseUrl: "http://api",
      fetcher,
      traceIdFactory: () => "trace_fixed",
      webSocketFactory: progressWebSocketFactory
    });

    const connection = client.connectAgentConfigProgress("aco_progress", vi.fn());
    let resolved = false;
    connection.then(() => {
      resolved = true;
    });

    await Promise.resolve();
    expect(resolved).toBe(false);
    socket.readyState = 1;
    socket.onopen?.({});

    await expect(connection).resolves.toBe(socket);
    expect(resolved).toBe(true);
  });
});

type WebSocketEventHandler = ((event: any) => void) | null;

class FakeWorkspaceWebSocket {
  readonly sentMessages: Array<Record<string, unknown>> = [];
  closeCalls = 0;
  closed = false;
  closeError?: Error;
  onSend?: (message: Record<string, unknown>) => void;
  onopen: WebSocketEventHandler = null;
  onmessage: WebSocketEventHandler = null;
  onerror: WebSocketEventHandler = null;
  onclose: WebSocketEventHandler = null;
  private readonly uploads = new Map<string, { uploadedBytes: number; totalBytes: number }>();
  private uploadSequence = 0;

  constructor(readonly url: string, autoOpen = true) {
    if (autoOpen) {
      queueMicrotask(() => this.openConnection());
    }
  }

  openConnection() {
    this.onopen?.({});
  }

  failConnection() {
    this.onerror?.({});
  }

  send(payload: string) {
    const message = JSON.parse(payload) as { id: string; op: string; params?: Record<string, unknown> };
    this.sentMessages.push(message);
    if (this.onSend) {
      this.onSend(message);
      return;
    }
    queueMicrotask(() => {
      this.onmessage?.({
        data: JSON.stringify({
          id: message.id,
          type: "result",
          data:
            message.op.endsWith(".read.chunk")
              ? {
                  path: String(message.params?.path ?? "docs/large.log"),
                  content: "preview chunk",
                  offset: Number(message.params?.offset ?? 0),
                  nextOffset: Number(message.params?.offset ?? 0) + 13,
                  size: 1048576,
                  eof: false,
                  warningThresholdBytes: 5242880,
                  lastModifiedMillis: 1234
                }
              : message.op.endsWith(".upload.begin")
              ? this.beginUpload(message.params)
              : message.op.endsWith(".upload.chunk")
                ? this.appendUpload(message.params)
                : message.op.endsWith(".upload.complete")
                  ? this.completeUpload(message.params)
                  : message.op.endsWith(".upload.abort")
                    ? null
                    : message.op === "workspace.list"
              ? [
                  {
                    path: "src/App.vue",
                    name: "App.vue",
                    directory: false,
                    size: 42,
                    lastModifiedAt: "2026-06-26T09:00:00Z"
                  }
                ]
              : message.op === "agent-config.list"
                ? [
                    {
                      path: "opencode/agents/review.md",
                      name: "review.md",
                      displayName: "测试案例审核",
                      displayNameEn: "Test Case Review",
                      directory: false,
                      size: 18,
                      lastModifiedAt: "2026-06-26T09:00:00Z"
                    }
                  ]
                : message.op === "agent-config.read"
              ? {
                      path: "review.md",
                      content: "agent content",
                      size: 13
                    }
                  : message.op === "workspace.read"
                    ? {
                        path: "docs/design.md",
                        content: "# 设计",
                        size: 8
                      }
                  : null
        })
      });
    });
  }

  private beginUpload(params: Record<string, unknown> | undefined) {
    const uploadId = `upl_test_${++this.uploadSequence}`;
    const totalBytes = Number(params?.size ?? 0);
    this.uploads.set(uploadId, { uploadedBytes: 0, totalBytes });
    return { uploadId, chunkBytes: 2, totalBytes };
  }

  private appendUpload(params: Record<string, unknown> | undefined) {
    const uploadId = String(params?.uploadId ?? "");
    const upload = this.uploads.get(uploadId);
    if (!upload) return null;
    upload.uploadedBytes += atob(String(params?.contentBase64 ?? "")).length;
    return { uploadedBytes: upload.uploadedBytes, totalBytes: upload.totalBytes };
  }

  private completeUpload(params: Record<string, unknown> | undefined) {
    const uploadId = String(params?.uploadId ?? "");
    const upload = this.uploads.get(uploadId);
    if (!upload) return null;
    this.uploads.delete(uploadId);
    return { size: upload.uploadedBytes };
  }

  close() {
    this.closeCalls += 1;
    if (this.closeError) {
      throw this.closeError;
    }
    this.closed = true;
    this.onclose?.({});
  }
}

function fakeWorkspaceWebSocketFactory(instances: FakeWorkspaceWebSocket[]) {
  return ((url: string) => {
    const socket = new FakeWorkspaceWebSocket(url);
    instances.push(socket);
    return socket;
  }) satisfies WorkspaceWebSocketFactory;
}

function manualWorkspaceWebSocketFactory(instances: FakeWorkspaceWebSocket[]) {
  return ((url: string) => {
    const socket = new FakeWorkspaceWebSocket(url, false);
    instances.push(socket);
    return socket;
  }) satisfies WorkspaceWebSocketFactory;
}

function workspaceFileFetcher(workspaceId: string) {
  return vi.fn<typeof fetch>().mockImplementation(async (input) => {
    const isRoute = String(input).includes(`/workspaces/${workspaceId}/file-ws-route`);
    return new Response(
      JSON.stringify({
        success: true,
        traceId: "trace_fixed",
        data: isRoute
          ? {
              workspaceId,
              linuxServerId: "linux-1",
              baseUrl: "http://10.8.0.12:8080",
              webSocketPath: "/api/internal/platform/workspace-management/file/ws",
              sameServer: true
            }
          : {
              ticket: `wft_${workspaceId}`,
              expiresAt: "2026-06-26T10:00:00Z",
              webSocketUrl: `/api/internal/platform/workspace-management/file/ws?ticket=wft_${workspaceId}`
            }
      }),
      { status: 200 }
    );
  });
}
