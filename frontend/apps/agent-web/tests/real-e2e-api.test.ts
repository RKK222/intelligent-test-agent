import { describe, expect, it, vi } from "vitest";

import {
  apiDelete,
  apiGet,
  apiPost,
  authHeaders,
  createCleanupScope,
  requestEnvelope,
  runCleanupTasks,
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
