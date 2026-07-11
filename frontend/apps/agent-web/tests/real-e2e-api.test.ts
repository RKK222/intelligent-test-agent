import { describe, expect, it, vi } from "vitest";

import { apiDelete, apiGet, apiPost, authHeaders, requestEnvelope } from "./real-e2e-api";

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
