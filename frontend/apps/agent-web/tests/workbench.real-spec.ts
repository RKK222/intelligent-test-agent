import { expect, test, type Page } from "@playwright/test";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";

import { apiPost } from "./real-e2e-api";

const runRealE2e = process.env.TEST_AGENT_RUN_REAL_E2E === "1";
const backendBaseUrl = stripTrailingSlash(process.env.TEST_AGENT_BASE_URL ?? "http://127.0.0.1:8080");

test.describe("phase 11 real service integration", () => {
  test.skip(!runRealE2e, "Set TEST_AGENT_RUN_REAL_E2E=1 to run the real frontend/backend/opencode integration suite.");

  test("creates a real opencode-backed session and opens a PTY terminal websocket", async ({ page }) => {
    const workspaceRoot = await createWorkspaceFixture();
    try {
      const workspace = await apiPost<{ workspaceId: string }>("/api/internal/platform/workspace-management/workspaces", {
        name: `phase11-real-${Date.now()}`,
        rootPath: workspaceRoot
      });
      const session = await apiPost<{ sessionId: string }>("/api/internal/platform/opencode-runtime/sessions", {
        workspaceId: workspace.workspaceId,
        title: "Phase 11 real E2E"
      });

      const mappingTicket = await establishOpencodeMapping(session.sessionId);
      const ticket =
        mappingTicket ??
        (await apiPost<{ webSocketUrl: string }>(`/api/internal/platform/opencode-runtime/sessions/${session.sessionId}/terminal/tickets`, {
          workspaceId: workspace.workspaceId,
          cols: 120,
          rows: 32
        }));

      await page.goto("/");
      const terminalResult = await connectTerminalAndEcho(page, ticket.webSocketUrl, "phase11-real-e2e");

      expect(terminalResult.output).toContain("phase11-real-e2e");
      expect(terminalResult.error).toBeUndefined();

      const reusedTicketResult = await connectTerminalAndEcho(page, ticket.webSocketUrl, "phase11-ticket-reuse");
      expect(reusedTicketResult.error?.code).toBeTruthy();
    } finally {
      await rm(workspaceRoot, { recursive: true, force: true });
    }
  });
});

async function createWorkspaceFixture() {
  const root = await mkdtemp(path.join(os.tmpdir(), "phase11-real-e2e-"));
  await writeFile(path.join(root, "README.md"), "# Phase 11 real E2E\n", "utf8");
  return root;
}

async function establishOpencodeMapping(sessionId: string): Promise<{ webSocketUrl: string } | null> {
  try {
    await apiPost("/api/internal/agent/opencode/runs", {
      sessionId,
      prompt: "Reply with phase11-real-e2e-ready. Do not modify files.",
      parts: [{ type: "text", text: "Reply with phase11-real-e2e-ready. Do not modify files." }]
    });
    return null;
  } catch (error) {
    const ticket = await tryCreateTerminalTicket(sessionId);
    if (ticket) {
      return ticket;
    }
    throw error;
  }
}

async function tryCreateTerminalTicket(sessionId: string) {
  try {
    return await apiPost<{ webSocketUrl: string }>(`/api/internal/platform/opencode-runtime/sessions/${sessionId}/terminal/tickets`, { cols: 80, rows: 24 });
  } catch {
    return null;
  }
}

function stripTrailingSlash(value: string) {
  return value.replace(/\/$/, "");
}

type TerminalProbeResult = {
  output: string;
  error?: { code: string; message: string };
};

async function connectTerminalAndEcho(page: Page, webSocketUrl: string, marker: string): Promise<TerminalProbeResult> {
  return await page.evaluate(
    async ({ baseUrl, webSocketUrl, marker }) => {
      const url = new URL(webSocketUrl, baseUrl);
      url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
      return await new Promise<TerminalProbeResult>((resolve) => {
        const socket = new WebSocket(url.toString());
        let output = "";
        let settled = false;
        const timer = window.setTimeout(() => finish(), 10_000);

        function finish(error?: { code: string; message: string }) {
          if (settled) {
            return;
          }
          settled = true;
          window.clearTimeout(timer);
          try {
            socket.close();
          } catch {
            // Browser close can throw if the connection never opened.
          }
          resolve(error ? { output, error } : { output });
        }

        socket.onopen = () => {
          socket.send(JSON.stringify({ type: "input", data: `printf '${marker}\\n'\n` }));
        };
        socket.onerror = () => finish({ code: "PTY_SOCKET_ERROR", message: "terminal socket error" });
        socket.onclose = () => finish();
        socket.onmessage = (event) => {
          const message = JSON.parse(String(event.data)) as Record<string, unknown>;
          if (message.type === "output") {
            output += typeof message.data === "string" ? message.data : "";
            if (output.includes(marker)) {
              socket.send(JSON.stringify({ type: "close", reason: "e2e" }));
              finish();
            }
          }
          if (message.type === "error") {
            finish({
              code: typeof message.code === "string" ? message.code : "PTY_ERROR",
              message: typeof message.message === "string" ? message.message : "terminal error"
            });
          }
        };
      });
    },
    { baseUrl: backendBaseUrl, webSocketUrl, marker }
  );
}
