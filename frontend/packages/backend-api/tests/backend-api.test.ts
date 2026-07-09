import { describe, expect, it, vi } from "vitest";
import { BackendApiError, createBackendApiClient, type WorkspaceWebSocketFactory } from "../src";

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
      sessions: [expect.objectContaining({ sessionId: "ses_1", attention: "QUESTION" })]
    });

    expect(fetcher).toHaveBeenCalledWith(
      "http://api/api/internal/platform/opencode-runtime/sessions/runtime-state",
      expect.any(Object)
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
          data: { items: [{ messageId: "msg_1", sessionId: "ses_1", role: "USER", content: "hello" }], page: 1, size: 100, total: 1 }
        }),
        { status: 200 }
      )
    );
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await expect(client.listSessionMessages("ses_1", 1, 100, { refresh: false })).resolves.toMatchObject({
      items: [{ messageId: "msg_1", content: "hello" }]
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
            events: [{ type: "message.updated", sessionId: "ses_1", payload: { message: { id: "msg_1", role: "assistant" } } }]
          }
        }),
        { status: 200 }
      )
    );
    const client = createBackendApiClient({ baseUrl: "http://api", fetcher, traceIdFactory: () => "trace_fixed" });

    await expect(client.getSessionTreeMessages("ses_1")).resolves.toMatchObject({
      sessionId: "ses_1",
      events: [{ type: "message.updated" }]
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
        type: "file",
        size: 18,
        modifiedAt: "2026-06-26T09:00:00Z"
      }
    ]);

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
  });

  it("routes workspace agent config read and write through one file websocket", async () => {
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

    expect(fetcher).toHaveBeenCalledTimes(2);
    expect(JSON.parse(String(fetcher.mock.calls[1]?.[1]?.body))).toEqual({
      workspaceId: "wrk_1234567890abcdef",
      linuxServerId: "linux-1",
      mode: "agent-config",
      scope: "WORKSPACE"
    });
    expect(sockets).toHaveLength(1);
    expect(sockets[0]?.sentMessages.map((message) => message.op)).toEqual(["agent-config.read", "agent-config.write"]);
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
  onopen: WebSocketEventHandler = null;
  onmessage: WebSocketEventHandler = null;
  onerror: WebSocketEventHandler = null;
  onclose: WebSocketEventHandler = null;

  constructor(readonly url: string) {
    queueMicrotask(() => this.onopen?.({}));
  }

  send(payload: string) {
    const message = JSON.parse(payload) as { id: string; op: string };
    this.sentMessages.push(message);
    queueMicrotask(() => {
      this.onmessage?.({
        data: JSON.stringify({
          id: message.id,
          type: "result",
          data:
            message.op === "workspace.list"
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
              : null
        })
      });
    });
  }

  close() {
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
