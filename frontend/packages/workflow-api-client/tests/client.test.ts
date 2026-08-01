import { describe, expect, it, vi } from "vitest";
import { WorkflowApiClient, WorkflowApiError } from "../src";

describe("WorkflowApiClient", () => {
  it("calls Python directly with the platform bearer token", async () => {
    const fetcher = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      expect(String(input)).toBe("/workflow-api/v1/conversations");
      expect(new Headers(init?.headers).get("Authorization")).toBe("Bearer platform-token");
      return new Response(JSON.stringify({ data: [] }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    });
    const client = new WorkflowApiClient({ token: () => "platform-token", fetch: fetcher });

    await expect(client.conversations()).resolves.toEqual([]);
    expect(fetcher).toHaveBeenCalledTimes(1);
  });

  it("turns a successful SPA HTML fallback into a safe workflow routing error", async () => {
    const fetcher = vi.fn(async () => new Response("<!doctype html><title>agent-web</title>", {
      status: 200,
      headers: { "Content-Type": "text/html; charset=utf-8" },
    }));
    const client = new WorkflowApiClient({ token: () => "platform-token", fetch: fetcher });

    const failure = await client.me().catch((error: unknown) => error);

    expect(failure).toBeInstanceOf(WorkflowApiError);
    expect(failure).toMatchObject({
      status: 502,
      code: "WORKFLOW_API_INVALID_RESPONSE",
      details: {
        upstreamStatus: 200,
        contentType: "text/html; charset=utf-8",
      },
    });
    expect((failure as Error).message).toBe("工作流服务响应格式异常，请检查 /workflow-api/ 路由和 Python 服务");
    expect((failure as Error).message).not.toContain("<!doctype");
  });

  it("turns a non-JSON proxy failure into a safe availability error", async () => {
    const fetcher = vi.fn(async () => new Response("Bad Gateway", {
      status: 502,
      headers: { "Content-Type": "text/plain" },
    }));
    const client = new WorkflowApiClient({ token: () => "platform-token", fetch: fetcher });

    await expect(client.repositories()).rejects.toMatchObject({
      status: 502,
      code: "WORKFLOW_API_UNAVAILABLE",
      message: "工作流服务暂不可用（HTTP 502）",
      details: { contentType: "text/plain" },
    });
  });

  it("rejects an SPA HTML fallback from report download", async () => {
    const fetcher = vi.fn(async () => new Response("<!doctype html>", {
      status: 200,
      headers: { "Content-Type": "text/html" },
    }));
    const client = new WorkflowApiClient({ token: () => "platform-token", fetch: fetcher });

    await expect(client.downloadReport("report_1")).rejects.toMatchObject({
      status: 502,
      code: "WORKFLOW_API_INVALID_RESPONSE",
    });
  });

  it("rejects an SPA HTML fallback from the AG-UI event endpoint", async () => {
    const fetcher = vi.fn(async () => new Response("<!doctype html>", {
      status: 200,
      headers: { "Content-Type": "text/html" },
    }));
    const client = new WorkflowApiClient({ token: () => "platform-token", fetch: fetcher });

    const connection = client.connectEvents("conv_1", () => undefined, { reconnect: false });

    await expect(connection.done).rejects.toMatchObject({
      status: 502,
      code: "WORKFLOW_API_INVALID_RESPONSE",
    });
  });

  it("uses fetch SSE with Authorization and Last-Event-ID", async () => {
    const encoder = new TextEncoder();
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(encoder.encode(
          "id: 8\nevent: RUN_FINISHED\ndata: {\"type\":\"RUN_FINISHED\",\"runId\":\"run_1\"}\n\n",
        ));
        controller.close();
      },
    });
    const fetcher = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      const headers = new Headers(init?.headers);
      expect(headers.get("Authorization")).toBe("Bearer platform-token");
      expect(headers.get("Last-Event-ID")).toBe("7");
      return new Response(stream, { status: 200, headers: { "Content-Type": "text/event-stream" } });
    });
    const events: string[] = [];
    const client = new WorkflowApiClient({ token: () => "platform-token", fetch: fetcher });

    const connection = client.connectEvents(
      "conv_1",
      (event) => events.push(String(event.type)),
      { lastEventId: "7", reconnect: false },
    );
    await connection.done;

    expect(events).toEqual(["RUN_FINISHED"]);
    expect(connection.lastEventId()).toBe("8");
  });

  it("reconnects after a transient stream failure and resumes from the last durable event", async () => {
    const encoder = new TextEncoder();
    let attempt = 0;
    const seenHeaders: Array<string | null> = [];
    const fetcher = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      attempt += 1;
      seenHeaders.push(new Headers(init?.headers).get("Last-Event-ID"));
      if (attempt === 1) {
        let sent = false;
        return new Response(new ReadableStream<Uint8Array>({
          pull(controller) {
            if (!sent) {
              sent = true;
              controller.enqueue(encoder.encode(
                "id: 8\nevent: STATE_DELTA\ndata: {\"type\":\"STATE_DELTA\"}\n\n",
              ));
              return;
            }
            controller.error(new TypeError("connection reset"));
          },
        }), { status: 200, headers: { "Content-Type": "text/event-stream" } });
      }
      return new Response(new ReadableStream<Uint8Array>({
        start(controller) {
          controller.enqueue(encoder.encode(
            "id: 9\nevent: RUN_FINISHED\ndata: {\"type\":\"RUN_FINISHED\"}\n\n",
          ));
        },
      }), { status: 200, headers: { "Content-Type": "text/event-stream" } });
    });
    const events: string[] = [];
    const client = new WorkflowApiClient({ token: () => "platform-token", fetch: fetcher });
    let connection: ReturnType<WorkflowApiClient["connectEvents"]>;

    connection = client.connectEvents(
      "conv_1",
      (event) => {
        events.push(String(event.type));
        if (event.type === "RUN_FINISHED") connection.close();
      },
      { lastEventId: "7", reconnectDelayMs: 0 },
    );
    await connection.done;

    expect(events).toEqual(["STATE_DELTA", "RUN_FINISHED"]);
    expect(seenHeaders).toEqual(["7", "8"]);
    expect(connection.lastEventId()).toBe("9");
  });

  it("does not retry an authentication failure", async () => {
    const onUnauthorized = vi.fn();
    const fetcher = vi.fn(async () => new Response(JSON.stringify({
      code: "UNAUTHENTICATED",
      message: "登录状态已失效",
    }), { status: 401, headers: { "Content-Type": "application/json" } }));
    const client = new WorkflowApiClient({
      token: () => "expired-token",
      fetch: fetcher,
      onUnauthorized,
    });

    const connection = client.connectEvents("conv_1", () => undefined, {
      reconnectDelayMs: 0,
    });

    await expect(connection.done).rejects.toMatchObject({ status: 401 });
    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it("does not turn a consumer reducer failure into an endless reconnect", async () => {
    const encoder = new TextEncoder();
    const cancel = vi.fn();
    const fetcher = vi.fn(async () => new Response(new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(encoder.encode(
          "id: 1\nevent: RUN_STARTED\ndata: {\"type\":\"RUN_STARTED\"}\n\n",
        ));
      },
      cancel,
    }), { status: 200, headers: { "Content-Type": "text/event-stream" } }));
    const client = new WorkflowApiClient({ token: () => "platform-token", fetch: fetcher });

    const connection = client.connectEvents("conv_1", () => {
      throw new Error("reducer failed");
    }, { reconnectDelayMs: 0 });

    await expect(connection.done).rejects.toThrow("AG-UI事件消费失败");
    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(cancel).toHaveBeenCalledTimes(1);
  });
});
