import { describe, expect, it, vi } from "vitest";
import { WorkflowApiClient } from "../src";

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
});
