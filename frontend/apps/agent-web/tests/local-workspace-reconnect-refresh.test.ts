import { describe, expect, it } from "vitest";

import { createLocalWorkspaceReconnectRefreshGate } from "../src/components/local-workspace-reconnect-refresh";

const onlineClient = {
  endpointId: "lci_current",
  runtimeKind: "LOCAL_CLIENT",
  online: true
};

describe("local workspace reconnect refresh", () => {
  it("retries an offline historical workspace only within the configured reconnect window", () => {
    const gate = createLocalWorkspaceReconnectRefreshGate(2);
    const workspaces = [{ runtimeKind: "LOCAL_CLIENT", online: false }];

    expect(gate.shouldRefresh([onlineClient], workspaces)).toBe(true);
    expect(gate.shouldRefresh([onlineClient], workspaces)).toBe(true);
    expect(gate.shouldRefresh([onlineClient], workspaces)).toBe(false);
  });

  it("refreshes legacy non-git projections and stops as soon as the workspace becomes usable", () => {
    const gate = createLocalWorkspaceReconnectRefreshGate();

    expect(gate.shouldRefresh([onlineClient], [{
      runtimeKind: "LOCAL_CLIENT",
      online: true,
      gitAccessStatus: "INACCESSIBLE",
      gitAccessReason: "NOT_GIT_REPOSITORY"
    }])).toBe(true);
    expect(gate.shouldRefresh([onlineClient], [{ runtimeKind: "LOCAL_CLIENT", online: true }])).toBe(false);
  });

  it("does not refresh without an online local client and resets for a replacement instance", () => {
    const gate = createLocalWorkspaceReconnectRefreshGate(1);
    const workspaces = [{ runtimeKind: "LOCAL_CLIENT", online: false }];

    expect(gate.shouldRefresh([], workspaces)).toBe(false);
    expect(gate.shouldRefresh([onlineClient], workspaces)).toBe(true);
    expect(gate.shouldRefresh([onlineClient], workspaces)).toBe(false);
    expect(gate.shouldRefresh([{ ...onlineClient, endpointId: "lci_reinstalled" }], workspaces)).toBe(true);
  });
});
