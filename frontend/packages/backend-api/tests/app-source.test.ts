import { describe, expect, it, vi } from "vitest";
import {
  createBackendApiClient,
  type WorkspaceWebSocketFactory
} from "../src";
import type {
  AppSourceMaterializationPayload,
  AppSourceProgressEvent
} from "@test-agent/shared-types";

describe("app-source backend client", () => {
  it("encodes every REST path/query and preserves the exact materialization payload", async () => {
    const fetcher = vi.fn<typeof fetch>().mockImplementation(async (input) => {
      const url = String(input);
      const data = url.endsWith("/recent-app-source") ? null
        : url.includes("/branches") ? ["main"]
          : url.includes("/tree?") ? []
            : url.includes("/app-source-repositories") ? []
              : { operationId: "aso_1", status: "RUNNING" };
      return new Response(JSON.stringify({ success: true, traceId: "trace_fixed", data }), { status: 200 });
    });
    const client = createBackendApiClient({
      baseUrl: "http://api",
      fetcher,
      traceIdFactory: () => "trace_fixed"
    });
    const materialization: AppSourceMaterializationPayload = {
      operationId: "aso_1",
      expectedGeneration: 3,
      branch: "feature/source",
      expectedTreeCommit: "b".repeat(40),
      selectedPaths: [{ path: "src/api", type: "DIRECTORY" }],
      purpose: "TEAM",
      retentionHours: 6,
      confirmReplace: true
    };

    await client.listAppSourceRepositories("app/demo");
    await client.listAppSourceBranches("app/demo", "repo/source");
    await client.listAppSourceTree("app/demo", "repo/source", "feature/source", "src/api");
    await client.materializeAppSource("app/demo", "repo/source", materialization);
    await client.retryAppSourceReplicas("app/demo", "repo/source", {
      operationId: "aso_retry",
      expectedGeneration: 4
    });
    await client.openAppSource("app/demo", "repo/source", 4);
    await client.getRecentAppSource();
    await client.clearRecentAppSource();
    await client.getAppSourceOperation("aso/id");
    await client.createAppSourceOperationTicket("aso/id");

    expect(fetcher.mock.calls.map((call) => [call[0], call[1]?.method, call[1]?.body])).toEqual([
      [
        "http://api/api/internal/platform/workspace-management/applications/app%2Fdemo/app-source-repositories",
        undefined,
        undefined
      ],
      [
        "http://api/api/internal/platform/workspace-management/applications/app%2Fdemo/app-source-repositories/repo%2Fsource/branches",
        undefined,
        undefined
      ],
      [
        "http://api/api/internal/platform/workspace-management/applications/app%2Fdemo/app-source-repositories/repo%2Fsource/tree?branch=feature%2Fsource&path=src%2Fapi",
        undefined,
        undefined
      ],
      [
        "http://api/api/internal/platform/workspace-management/applications/app%2Fdemo/app-source-repositories/repo%2Fsource/materializations",
        "POST",
        JSON.stringify(materialization)
      ],
      [
        "http://api/api/internal/platform/workspace-management/applications/app%2Fdemo/app-source-repositories/repo%2Fsource/replica-retries",
        "POST",
        JSON.stringify({ operationId: "aso_retry", expectedGeneration: 4 })
      ],
      [
        "http://api/api/internal/platform/workspace-management/applications/app%2Fdemo/app-source-repositories/repo%2Fsource/open",
        "POST",
        JSON.stringify({ generation: 4 })
      ],
      [
        "http://api/api/internal/platform/workspace-management/recent-app-source",
        undefined,
        undefined
      ],
      [
        "http://api/api/internal/platform/workspace-management/recent-app-source",
        "DELETE",
        undefined
      ],
      [
        "http://api/api/internal/platform/workspace-management/app-source-operations/aso%2Fid",
        undefined,
        undefined
      ],
      [
        "http://api/api/internal/platform/workspace-management/app-source-operations/aso%2Fid/ticket",
        "POST",
        undefined
      ]
    ]);
  });

  it("opens one caller-managed connection per fresh ticket and closing never calls a cancel API", async () => {
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(JSON.stringify({
        success: true,
        traceId: "trace_fixed",
        data: { ticket: "ast_1", expiresAt: "2026-07-28T04:01:00Z", webSocketUrl: "ws://server-a/progress?ticket=ast_1" }
      }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        success: true,
        traceId: "trace_fixed",
        data: { ticket: "ast_2", expiresAt: "2026-07-28T04:02:00Z", webSocketUrl: "ws://server-a/progress?ticket=ast_2" }
      }), { status: 200 }));
    const sockets: FakeSocket[] = [];
    const factory: WorkspaceWebSocketFactory = (url) => {
      const socket = new FakeSocket(url);
      sockets.push(socket);
      return socket;
    };
    const client = createBackendApiClient({
      baseUrl: "http://api",
      fetcher,
      webSocketFactory: factory,
      traceIdFactory: () => "trace_fixed"
    });
    const events: AppSourceProgressEvent[] = [];

    const first = await client.connectAppSourceProgress("aso/1", (event) => events.push(event));
    sockets[0]?.message(JSON.stringify({
      type: "snapshot",
      operationId: "aso/1",
      operation: { operationId: "aso/1", status: "RUNNING" }
    }));
    first.close();
    const second = await client.connectAppSourceProgress("aso/1", (event) => events.push(event));
    sockets[1]?.message(JSON.stringify({
      type: "completed",
      operationId: "aso/1",
      operation: { operationId: "aso/1", status: "SUCCEEDED" }
    }));
    second.close();

    expect(sockets.map((socket) => socket.url)).toEqual([
      "ws://server-a/progress?ticket=ast_1",
      "ws://server-a/progress?ticket=ast_2"
    ]);
    expect(events.map((event) => event.type)).toEqual(["snapshot", "completed"]);
    expect(fetcher.mock.calls.map((call) => [call[0], call[1]?.method])).toEqual([
      [
        "http://api/api/internal/platform/workspace-management/app-source-operations/aso%2F1/ticket",
        "POST"
      ],
      [
        "http://api/api/internal/platform/workspace-management/app-source-operations/aso%2F1/ticket",
        "POST"
      ]
    ]);
    expect(sockets.every((socket) => socket.closed)).toBe(true);
  });
});

class FakeSocket {
  onopen: ((event: unknown) => void) | null = null;
  onmessage: ((event: { data: string }) => void) | null = null;
  onerror: ((event: unknown) => void) | null = null;
  onclose: ((event: unknown) => void) | null = null;
  readyState = 1;
  closed = false;

  constructor(readonly url: string) {}

  send() {}

  close() {
    this.closed = true;
    this.onclose?.({});
  }

  message(data: string) {
    this.onmessage?.({ data });
  }
}
