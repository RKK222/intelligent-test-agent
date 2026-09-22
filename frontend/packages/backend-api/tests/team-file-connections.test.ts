import { describe, expect, it, vi } from "vitest";
import { createBackendApiClient, type WorkspaceWebSocketFactory } from "../src";

type Handler = ((event: { data?: string }) => void) | null;

class FakeSocket {
  readonly sent: Array<Record<string, unknown>> = [];
  closeCalls = 0;
  onopen: Handler = null;
  onmessage: Handler = null;
  onerror: Handler = null;
  onclose: Handler = null;

  constructor(readonly url: string, autoOpen = true) {
    if (autoOpen) queueMicrotask(() => this.onopen?.({}));
  }

  send(payload: string) {
    const message = JSON.parse(payload) as { id: string; op: string; params?: Record<string, unknown> };
    this.sent.push(message);
    queueMicrotask(() => {
      const offset = Number(message.params?.offset ?? 0);
      this.onmessage?.({
        data: JSON.stringify({
          id: message.id,
          type: "result",
          data: message.op === "workspace.read.chunk"
            ? {
                path: message.params?.path,
                content: "chunk",
                offset,
                nextOffset: offset + 5,
                size: 20,
                eof: false,
                warningThresholdBytes: 1024,
                lastModifiedMillis: 10
              }
            : []
        })
      });
    });
  }

  close() {
    this.closeCalls += 1;
    this.onclose?.({});
  }
}

function teamFetcher() {
  return vi.fn<typeof fetch>().mockImplementation(async (input) => {
    const url = String(input);
    const isRoute = url.includes("/file-ws-route");
    return new Response(JSON.stringify({
      success: true,
      traceId: "trace-team",
      data: isRoute
        ? {
            workspaceId: "ws-1",
            linuxServerId: "linux-1",
            baseUrl: "http://10.8.0.12:8080",
            webSocketPath: "/api/internal/platform/workspace-management/file/ws",
            sameServer: false
          }
        : {
            ticket: "wft_team",
            expiresAt: "2026-09-22T10:00:00Z",
            webSocketUrl: "/api/internal/platform/workspace-management/file/ws?ticket=wft_team"
          }
    }), { status: 200, headers: { "content-type": "application/json" } });
  });
}

describe("team file connections", () => {
  it("reads a preview chunk over TEAM_READ_ONLY and closes that connection on request", async () => {
    const sockets: FakeSocket[] = [];
    const factory = ((url: string) => {
      const socket = new FakeSocket(url);
      sockets.push(socket);
      return socket;
    }) satisfies WorkspaceWebSocketFactory;
    const client = createBackendApiClient({
      baseUrl: "http://api",
      fetcher: teamFetcher(),
      webSocketFactory: factory,
      traceIdFactory: () => "trace-team"
    });
    const scope = { scopeMode: "MY_TEAM" as const };
    const chunk = await client.readTeamWorkspaceFilePreviewChunk(scope, "pw-1", "ws-1", "logs/large.log", { offset: 0 });
    expect(chunk.content).toBe("chunk");
    expect(chunk.nextOffset).toBe(5);
    expect(sockets[0]?.sent[0]?.op).toBe("workspace.read.chunk");
    expect(sockets[0]?.url).toContain("10.8.0.12");

    client.closeTeamWorkspaceFileConnections("pw-1");
    expect(sockets[0]?.closeCalls).toBe(1);

    await client.readTeamWorkspaceFilePreviewChunk(scope, "pw-1", "ws-1", "logs/large.log", { offset: 5 });
    expect(sockets).toHaveLength(2);
    expect(sockets[1]?.sent[0]?.params).toMatchObject({ path: "logs/large.log", offset: 5 });
  });

  it("does not keep a socket that finishes connecting after the team connection was closed", async () => {
    const sockets: FakeSocket[] = [];
    const factory = ((url: string) => {
      const socket = new FakeSocket(url, false);
      sockets.push(socket);
      return socket;
    }) satisfies WorkspaceWebSocketFactory;
    const client = createBackendApiClient({
      baseUrl: "http://api",
      fetcher: teamFetcher(),
      webSocketFactory: factory,
      traceIdFactory: () => "trace-team"
    });
    const pending = client.readTeamWorkspaceFilePreviewChunk(
      { scopeMode: "GLOBAL" },
      "pw-9",
      "ws-9",
      "src/late.ts",
      { offset: 0 }
    );
    await vi.waitFor(() => expect(sockets).toHaveLength(1));
    client.closeTeamWorkspaceFileConnections();
    sockets[0]?.onopen?.({});
    await expect(pending).rejects.toThrow(/团队文件连接已关闭/);
    expect(sockets).toHaveLength(1);
    expect(sockets[0]?.closeCalls).toBeGreaterThan(0);
  });
});
