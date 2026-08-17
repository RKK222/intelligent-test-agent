import { describe, expect, it, vi } from "vitest";
import { resolveSessionShareEntry } from "../src/session-share-route";

describe("resolveSessionShareEntry", () => {
  it("opens an active member link in delegated workbench mode", async () => {
    const access = {
      shareId: "shr_1",
      version: 1,
      actorUserId: "usr_member",
      actorUnifiedAuthId: "A1",
      actorUsername: "成员",
      executionOwnerUserId: "usr_owner",
      sessionId: "ses_1",
      workspaceId: "wrk_1",
      canChat: true,
      delegated: true,
      ownerAccess: false,
      expiresAt: "2026-08-10T00:00:00Z",
      participants: []
    };
    const sharedApi = { getSessionShareAccess: vi.fn().mockResolvedValue(access) };
    await expect(resolveSessionShareEntry("shr_1", sharedApi)).resolves.toEqual({
      kind: "shared",
      access
    });
  });

  it("redirects owners and sends /s/{sessionId} links to the readonly transcript", async () => {
    const ownerAccess = {
      ownerAccess: true,
      sessionId: "ses_owner"
    };
    await expect(resolveSessionShareEntry(
      "shr_owner",
      { getSessionShareAccess: vi.fn().mockResolvedValue(ownerAccess) }
    )).resolves.toEqual({ kind: "owner", sessionId: "ses_owner" });

    const sharedApi = { getSessionShareAccess: vi.fn() };
    await expect(resolveSessionShareEntry(
      "ses_legacy",
      sharedApi
    )).resolves.toEqual({ kind: "transcript", sessionId: "ses_legacy" });
    expect(sharedApi.getSessionShareAccess).not.toHaveBeenCalled();
  });
});
