import { describe, expect, it, vi } from "vitest";
import { mkdtemp, mkdir, rm, symlink, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";

import {
  apiDelete,
  apiGet,
  apiPost,
  authHeaders,
  createCleanupScope,
  requestEnvelope,
  resolveOwnedOpenCodeDatabase,
  resolveRemoteSessionIdFromSources,
  resolveOwnedCleanupPath,
  assertNativeSessionAbsentInSqlite,
  runCleanupTasks,
  runCleanupStages,
  waitForWorkspaceOperation
} from "./real-e2e-api";

describe("real E2E API client", () => {
  it("sends GET with the configured base URL, trace header and bearer token", async () => {
    const fetcher = vi.fn().mockResolvedValue(jsonResponse({ success: true, traceId: "server-trace", data: { id: "one" } }));

    await expect(
      apiGet<{ id: string }>("/items/one", {
        baseUrl: "http://127.0.0.1:8080/",
        fetcher,
        token: "secret-token",
        traceId: "trace-get"
      })
    ).resolves.toEqual({ id: "one" });

    expect(fetcher).toHaveBeenCalledWith(
      "http://127.0.0.1:8080/items/one",
      expect.objectContaining({
        method: "GET",
        headers: expect.objectContaining({
          Accept: "application/json",
          Authorization: "Bearer secret-token",
          "X-Trace-Id": "trace-get"
        })
      })
    );
  });

  it("sends POST JSON and returns successful envelope data", async () => {
    const fetcher = vi.fn().mockResolvedValue(jsonResponse({ success: true, traceId: "server-trace", data: { created: true } }));

    await expect(apiPost("/items", { name: "case" }, { fetcher, token: "token", traceId: "trace-post" })).resolves.toEqual({ created: true });

    expect(fetcher.mock.calls[0]?.[1]).toMatchObject({
      method: "POST",
      body: JSON.stringify({ name: "case" }),
      headers: expect.objectContaining({ "Content-Type": "application/json", "X-Trace-Id": "trace-post" })
    });
  });

  it("sends DELETE without a body", async () => {
    const fetcher = vi.fn().mockResolvedValue(jsonResponse({ success: true, traceId: "server-trace", data: null }));

    await apiDelete("/items/one", { fetcher, token: "token", traceId: "trace-delete" });

    expect(fetcher.mock.calls[0]?.[1]).toMatchObject({ method: "DELETE" });
    expect(fetcher.mock.calls[0]?.[1]).not.toHaveProperty("body");
  });

  it("rejects HTTP failures without exposing the bearer token", async () => {
    const fetcher = vi.fn().mockResolvedValue(
      jsonResponse(
        { success: false, traceId: "server-trace", error: { code: "HTTP_FAILURE", message: "bad secret-token" } },
        { status: 500, statusText: "Internal Server Error" }
      )
    );

    const error = await captureError(() => apiDelete("/items/one", { fetcher, token: "secret-token", traceId: "trace-delete" }));

    expect(error.message).toContain("500 HTTP_FAILURE");
    expect(error.message).not.toContain("secret-token");
  });

  it("rejects a success=false business envelope on HTTP 200", async () => {
    const fetcher = vi.fn().mockResolvedValue(
      jsonResponse({ success: false, traceId: "business-trace", error: { code: "BUSINESS_FAILURE", message: "operation rejected" } })
    );

    await expect(requestEnvelope("/items", { fetcher, method: "GET", traceId: "trace-business" })).rejects.toThrow(
      "200 BUSINESS_FAILURE operation rejected"
    );
  });

  it("builds an authorization header only when a token is present", () => {
    expect(authHeaders("secret-token")).toEqual({ Authorization: "Bearer secret-token" });
    expect(authHeaders("")).toEqual({});
    expect(authHeaders(undefined)).toEqual({});
  });

  it("polls an asynchronous workspace operation until it succeeds", async () => {
    const getOperation = vi
      .fn()
      .mockResolvedValueOnce({ status: "RUNNING", currentStep: "PREPARING_REPOSITORY" })
      .mockResolvedValueOnce({ status: "SUCCEEDED", workspaceId: "awp-one", versionId: "awv-one" });
    const sleep = vi.fn().mockResolvedValue(undefined);

    await expect(waitForWorkspaceOperation("wco-one", { getOperation, intervalMs: 1, timeoutMs: 100, sleep })).resolves.toMatchObject({
      status: "SUCCEEDED",
      workspaceId: "awp-one",
      versionId: "awv-one"
    });
    expect(getOperation).toHaveBeenCalledTimes(2);
    expect(sleep).toHaveBeenCalledOnce();
  });

  it("reports the asynchronous workspace failure step", async () => {
    const getOperation = vi.fn().mockResolvedValue({
      status: "FAILED",
      currentStep: "PREPARING_REPOSITORY",
      errorCode: "GIT_FAILURE",
      errorMessage: "clone rejected"
    });

    await expect(waitForWorkspaceOperation("wco-one", { getOperation, sleep: vi.fn() })).rejects.toThrow(
      "Workspace operation wco-one failed at PREPARING_REPOSITORY: GIT_FAILURE clone rejected"
    );
  });

  it("runs every cleanup task and aggregates failures", async () => {
    const completed: string[] = [];

    const error = await captureError(() =>
      runCleanupTasks([
        async () => {
          completed.push("session");
          throw new Error("session cleanup failed");
        },
        async () => {
          completed.push("workspace");
        },
        async () => {
          completed.push("directory");
          throw new Error("directory cleanup failed");
        }
      ])
    );

    expect(completed).toEqual(["session", "workspace", "directory"]);
    expect(error).toBeInstanceOf(AggregateError);
    expect(error.message).toContain("2 cleanup task(s) failed");
  });

  it("runs dependent cleanup stages in order after an earlier failure", async () => {
    const completed: string[] = [];
    const error = await captureError(() =>
      runCleanupStages([
        async () => {
          completed.push("cancel");
          throw new Error("cancel failed");
        },
        async () => {
          completed.push("remote-delete");
        },
        async () => {
          completed.push("platform-archive");
        }
      ])
    );
    expect(completed).toEqual(["cancel", "remote-delete", "platform-archive"]);
    expect(error).toBeInstanceOf(AggregateError);
  });

  it("keeps cleanup ownership until a fixture is explicitly handed off", async () => {
    const cleanup = vi.fn().mockResolvedValue(undefined);
    const owned = createCleanupScope();
    owned.defer(cleanup);
    await owned.cleanup();
    expect(cleanup).toHaveBeenCalledOnce();

    cleanup.mockClear();
    const handedOff = createCleanupScope();
    handedOff.defer(cleanup);
    handedOff.release();
    await handedOff.cleanup();
    expect(cleanup).not.toHaveBeenCalled();
  });

  it("allows only a real marker segment below the owned workspace root", async () => {
    const root = await mkdtemp(path.join(os.tmpdir(), "real-e2e-owned-"));
    const marker = "phase11_real_owned";
    const owned = path.join(root, "versions", marker);
    const outside = await mkdtemp(path.join(os.tmpdir(), "real-e2e-outside-"));
    try {
      await mkdir(owned, { recursive: true });
      await expect(resolveOwnedCleanupPath(owned, root, marker)).resolves.toBe(await import("node:fs/promises").then((fs) => fs.realpath(owned)));
      await expect(resolveOwnedCleanupPath(root, root, marker)).rejects.toThrow(/owned root/);
      await expect(resolveOwnedCleanupPath(path.join(root, "versions", `${marker}-suffix`), root, marker)).rejects.toThrow(/marker segment/);
      await expect(resolveOwnedCleanupPath(path.join(root, "..", path.basename(outside)), root, marker)).rejects.toThrow(/outside owned root/);

      const link = path.join(root, marker);
      await symlink(outside, link);
      await expect(resolveOwnedCleanupPath(link, root, marker)).rejects.toThrow(/symbolic link/);
    } finally {
      await rm(root, { recursive: true, force: true });
      await rm(outside, { recursive: true, force: true });
    }
  });

  it("registers a tree remote id before later tree validation fails so finally can still clean it", async () => {
    let cleanupOwnedRemoteId: string | undefined;
    let resolutionError: Error | undefined;
    const nativeDelete = vi.fn().mockResolvedValue(undefined);

    try {
      await resolveRemoteSessionIdFromSources({
        loadTree: async () => ({ sessions: [{ sessionId: "ses_tree", childSession: false }] }),
        loadPlatformMessages: async () => ({ items: [] }),
        onObserved: (remoteId) => {
          cleanupOwnedRemoteId ??= remoteId;
        },
        validateTree: () => {
          throw new Error("tree projection failed after id observation");
        }
      });
    } catch (error) {
      resolutionError = error as Error;
    } finally {
      if (cleanupOwnedRemoteId) await nativeDelete(cleanupOwnedRemoteId);
    }

    expect(resolutionError?.message).toContain("tree projection failed");
    expect(nativeDelete).toHaveBeenCalledWith("ses_tree");
  });

  it("finds sessionID in tree-carried native events and platform messages", async () => {
    const observed: string[] = [];
    await expect(
      resolveRemoteSessionIdFromSources({
        loadTree: async () => ({ sessions: [], events: [{ type: "message.updated", payload: { message: { sessionID: "ses_event" } } }] }),
        loadPlatformMessages: async () => ({ items: [] }),
        onObserved: (remoteId) => observed.push(remoteId)
      })
    ).resolves.toBe("ses_event");
    expect(observed).toEqual(["ses_event"]);

    await expect(
      resolveRemoteSessionIdFromSources({
        loadTree: async () => ({ sessions: [], events: [] }),
        loadPlatformMessages: async () => ({ items: [{ parts: [{ type: "text", sessionID: "ses_message" }] }] })
      })
    ).resolves.toBe("ses_message");
  });

  it("continues with platform messages when the session tree request fails and still registers cleanup ownership", async () => {
    const observed: string[] = [];
    await expect(
      resolveRemoteSessionIdFromSources({
        loadTree: async () => {
          throw new Error("tree HTTP 503");
        },
        loadPlatformMessages: async () => ({ items: [{ parts: [{ sessionID: "ses_message_after_tree_failure" }] }] }),
        onObserved: (remoteId) => observed.push(remoteId)
      })
    ).resolves.toBe("ses_message_after_tree_failure");
    expect(observed).toEqual(["ses_message_after_tree_failure"]);
  });

  it("aggregates diagnostics when both remote session sources fail", async () => {
    const error = await captureError(() =>
      resolveRemoteSessionIdFromSources({
        loadTree: async () => {
          throw new Error("tree HTTP 503");
        },
        loadPlatformMessages: async () => {
          throw new Error("messages HTTP 502");
        }
      })
    );
    expect(error).toBeInstanceOf(AggregateError);
    expect(error.message).toContain("session tree and platform messages");
    expect((error as AggregateError).errors.map((failure) => String(failure))).toEqual([
      "Error: tree HTTP 503",
      "Error: messages HTTP 502"
    ]);
  });

  it("resolves only the matching manager state and owned SQLite database", async () => {
    const projectRoot = await mkdtemp(path.join(os.tmpdir(), "real-e2e-project-"));
    const stateRoot = path.join(projectRoot, ".tmp", "dev-services", "opencode-manager-state", "processes");
    const sessionPath = path.join(projectRoot, ".testagent", "agent-opencode", ".session", "users", "usr-test");
    const databasePath = path.join(sessionPath, "opencode", "opencode.db");
    const statePath = path.join(stateRoot, "43210.json");
    try {
      await mkdir(path.dirname(databasePath), { recursive: true });
      await mkdir(stateRoot, { recursive: true });
      await writeFile(databasePath, "sqlite-placeholder");
      // manager 使用可供 Java 访问的 advertised host，平台给浏览器返回 loopback host；二者端口必须一致但主机可不同。
      await writeFile(statePath, JSON.stringify({ port: 43210, pid: 24680, baseUrl: "http://manager.local:43210", sessionPath }));

      await expect(
        resolveOwnedOpenCodeDatabase(
          { port: 43210, baseUrl: "http://127.0.0.1:43210" },
          { projectRoot, stateRoot, findListenerPid: async () => 24680 }
        )
      ).resolves.toMatchObject({ databasePath: await import("node:fs/promises").then((fs) => fs.realpath(databasePath)), pid: 24680 });
    } finally {
      await rm(projectRoot, { recursive: true, force: true });
    }
  });

  it("rejects manager path escape, symlink segments and process mismatches", async () => {
    const projectRoot = await mkdtemp(path.join(os.tmpdir(), "real-e2e-project-"));
    const outside = await mkdtemp(path.join(os.tmpdir(), "real-e2e-manager-outside-"));
    const stateRoot = path.join(projectRoot, ".tmp", "dev-services", "opencode-manager-state", "processes");
    try {
      await mkdir(stateRoot, { recursive: true });
      await writeFile(
        path.join(stateRoot, "43210.json"),
        JSON.stringify({ port: 43210, pid: 24680, baseUrl: "http://127.0.0.1:43210", sessionPath: outside })
      );
      await expect(
        resolveOwnedOpenCodeDatabase({ port: 43210, baseUrl: "http://127.0.0.1:43210" }, { projectRoot, stateRoot, findListenerPid: async () => 24680 })
      ).rejects.toThrow(/owned session root/);

      const ownedUsers = path.join(projectRoot, ".testagent", "agent-opencode", ".session", "users");
      await mkdir(ownedUsers, { recursive: true });
      const linkedSession = path.join(ownedUsers, "usr-linked");
      await symlink(outside, linkedSession);
      await writeFile(
        path.join(stateRoot, "43210.json"),
        JSON.stringify({ port: 43210, pid: 24680, baseUrl: "http://127.0.0.1:43210", sessionPath: linkedSession })
      );
      await expect(
        resolveOwnedOpenCodeDatabase({ port: 43210, baseUrl: "http://127.0.0.1:43210" }, { projectRoot, stateRoot, findListenerPid: async () => 24680 })
      ).rejects.toThrow(/symbolic link/);

      const regularSession = path.join(ownedUsers, "usr-regular");
      await mkdir(path.join(regularSession, "opencode"), { recursive: true });
      await writeFile(path.join(regularSession, "opencode", "opencode.db"), "sqlite-placeholder");
      await writeFile(
        path.join(stateRoot, "43210.json"),
        JSON.stringify({ port: 43210, pid: 24680, baseUrl: "http://127.0.0.1:43210", sessionPath: regularSession })
      );
      await expect(
        resolveOwnedOpenCodeDatabase({ port: 43210, baseUrl: "http://127.0.0.1:43210" }, { projectRoot, stateRoot, findListenerPid: async () => 99999 })
      ).rejects.toThrow(/listener PID/);
    } finally {
      await rm(projectRoot, { recursive: true, force: true });
      await rm(outside, { recursive: true, force: true });
    }
  });

  it("requires native session, message and part SQLite counts to all be zero", async () => {
    await expect(
      assertNativeSessionAbsentInSqlite("/owned/opencode.db", "ses-clean", {
        queryCounts: async () => ({ session: 0, message: 0, part: 0 })
      })
    ).resolves.toEqual({ session: 0, message: 0, part: 0 });
    await expect(
      assertNativeSessionAbsentInSqlite("/owned/opencode.db", "ses-leaked", {
        queryCounts: async () => ({ session: 0, message: 1, part: 2 })
      })
    ).rejects.toThrow(/session=0, message=1, part=2/);
  });
});

function jsonResponse(body: unknown, init: ResponseInit = {}) {
  return new Response(JSON.stringify(body), {
    headers: { "Content-Type": "application/json" },
    ...init
  });
}

async function captureError(action: () => Promise<unknown>): Promise<Error> {
  try {
    await action();
  } catch (error) {
    return error as Error;
  }
  throw new Error("Expected action to reject");
}
