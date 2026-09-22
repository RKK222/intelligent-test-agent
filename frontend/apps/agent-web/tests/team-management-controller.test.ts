import { describe, expect, it, vi } from "vitest";
import { BackendApiError } from "@test-agent/backend-api";
import type { TeamCommit, TeamContribution, TeamUser } from "@test-agent/shared-types";
import { createTeamManagementController, TEAM_MEMBER_SCOPE_HINT, canMaintainTeamMembers } from "../src/components/team/team-management-controller";

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}

function user(userId: string, username: string): TeamUser {
  return {
    userId,
    username,
    unifiedAuthId: `${username}-auth`,
    department: "质量部",
    status: "ACTIVE",
    roles: ["USER"]
  };
}

function worktree(personalWorkspaceId: string) {
  return {
    personalWorkspaceId,
    workspaceId: `ws-${personalWorkspaceId}`,
    workspaceName: "研发空间",
    branch: "feature/member",
    linuxServerId: "server-1",
    baseCommit: "abc123",
    status: "READY",
    updatedAt: "2026-09-21T08:00:00Z"
  };
}

function contribution(userId: string, personalWorkspaceId: string): TeamContribution {
  return {
    ...user(userId, userId),
    membershipState: "CURRENT",
    personalWorkspaces: [worktree(personalWorkspaceId)]
  };
}

function commit(id: string, type: TeamCommit["contributionType"] = "PERSONAL_COMMIT"): TeamCommit {
  return {
    commit: id,
    parents: [],
    authorName: "成员甲",
    authorEmail: "member@example.com",
    committerName: "成员甲",
    committerEmail: "member@example.com",
    committedAt: "2026-09-21T08:00:00Z",
    subject: id,
    contributionType: type
  };
}

function createApi() {
  return {
    listSystemAdmins: vi.fn().mockResolvedValue({ items: [user("owner-1", "系统管理员甲")], total: 1, page: 1, size: 200 }),
    listSystemAdminTeamMembers: vi.fn().mockResolvedValue({ items: [user("member-1", "成员甲")], total: 1, page: 1, size: 20 }),
    listSystemAdminTeamCandidates: vi.fn().mockResolvedValue({ items: [user("candidate-1", "候选人乙")], total: 1, page: 1, size: 50 }),
    addSystemAdminTeamMember: vi.fn().mockResolvedValue({ ownerUserId: "owner-1", memberUserId: "candidate-1", active: true }),
    removeSystemAdminTeamMember: vi.fn().mockResolvedValue({ ownerUserId: "owner-1", memberUserId: "member-1", active: false }),
    listTeamApplications: vi.fn().mockResolvedValue([]),
    listTeamWorkspaceTemplates: vi.fn().mockResolvedValue([]),
    listTeamWorkspaceVersions: vi.fn().mockResolvedValue([]),
    listTeamContributions: vi.fn().mockResolvedValue([]),
    getTeamWorkspaceGitStatus: vi.fn().mockResolvedValue({ files: [], stagedCount: 0, unstagedCount: 0, untrackedCount: 0 }),
    listTeamWorkspaceCommits: vi.fn().mockResolvedValue({ items: [], offset: 0, limit: 100, hasMore: false, attributionConfirmed: true }),
    getTeamWorkspaceCommitDetail: vi.fn().mockResolvedValue({ commit: commit("abc"), files: [] }),
    getTeamWorkspaceCommitDiff: vi.fn().mockResolvedValue({ commit: "abc", path: "a.ts", patch: "" }),
    listTeamWorkspaceFiles: vi.fn().mockResolvedValue([]),
    searchTeamWorkspaceFiles: vi.fn().mockResolvedValue([]),
    readTeamWorkspaceFile: vi.fn().mockResolvedValue({ path: "a.ts", content: "hello", encoding: "utf-8", readonly: true }),
    readTeamWorkspaceFilePreviewChunk: vi.fn(),
    closeTeamWorkspaceFileConnections: vi.fn(),
    createTeamExport: vi.fn(),
    getTeamExport: vi.fn(),
    cancelTeamExport: vi.fn(),
    createTeamExportDownloadRoute: vi.fn()
  };
}

describe("team management controller", () => {
  it("keeps a system administrator on their own team and allows member changes", async () => {
    const api = createApi();
    const controller = createTeamManagementController(api);
    await controller.enter(false);

    expect(controller.snapshot().scopeMode).toBe("MY_TEAM");
    expect(controller.snapshot().scopeLocked).toBe(true);
    expect(canMaintainTeamMembers(controller.snapshot())).toBe(true);
    expect(api.listTeamApplications).toHaveBeenCalledWith({ scopeMode: "MY_TEAM", ownerUserId: undefined });

    await controller.openMemberDrawer();
    expect(api.listSystemAdminTeamMembers).toHaveBeenCalled();
    await controller.removeMember("member-1");
    expect(api.removeSystemAdminTeamMember).toHaveBeenCalledWith(
      { scopeMode: "MY_TEAM", ownerUserId: undefined },
      "member-1"
    );
  });

  it("starts each super-admin entry on the read-only platform scope", async () => {
    const api = createApi();
    const controller = createTeamManagementController(api);
    await controller.enter(true);
    expect(controller.snapshot().scopeMode).toBe("GLOBAL");
    expect(canMaintainTeamMembers(controller.snapshot())).toBe(false);
    expect(TEAM_MEMBER_SCOPE_HINT).toContain("请选择具体系统管理员团队");
    expect(api.listSystemAdminTeamMembers).not.toHaveBeenCalled();

    await controller.selectScope("SYSTEM_ADMIN_TEAM", "owner-1");
    expect(canMaintainTeamMembers(controller.snapshot())).toBe(true);
    expect(api.listTeamApplications).toHaveBeenCalledWith({
      scopeMode: "SYSTEM_ADMIN_TEAM",
      ownerUserId: "owner-1"
    });
    expect(api.closeTeamWorkspaceFileConnections).toHaveBeenCalledWith();
  });

  it("ignores a late application response after the scope changes", async () => {
    const first = deferred<unknown[]>();
    const second = deferred<unknown[]>();
    const api = createApi();
    api.listTeamApplications.mockReset();
    api.listTeamApplications
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise);
    const controller = createTeamManagementController(api);
    const pending = controller.enter(true);
    await vi.waitFor(() => expect(api.listTeamApplications).toHaveBeenCalledTimes(1));
    const switched = controller.selectScope("SYSTEM_ADMIN_TEAM", "owner-1");
    second.resolve([{ appId: "new-app", appName: "新应用", enabled: true, currentMemberCount: 1, historicalMemberCount: 0 }]);
    await switched;
    first.resolve([{ appId: "old-app", appName: "旧应用", enabled: true, currentMemberCount: 0, historicalMemberCount: 0 }]);
    await pending;
    expect(controller.snapshot().applications.map((item) => item.appId)).toEqual(["new-app"]);
    expect(controller.snapshot().selectedAppId).toBe("new-app");
  });

  it("drops a file body that arrives after the worktree changes and closes the old connection", async () => {
    const read = deferred<{ path: string; content: string; encoding: string; readonly: boolean }>();
    const api = createApi();
    api.listTeamApplications.mockResolvedValue([
      { appId: "app-1", appName: "应用一", enabled: true, currentMemberCount: 2, historicalMemberCount: 0 }
    ]);
    api.listTeamWorkspaceTemplates.mockResolvedValue([
      { workspaceId: "ws-1", appId: "app-1", workspaceName: "研发空间", branch: "release", directoryPath: "repo", enabled: true }
    ]);
    api.listTeamWorkspaceVersions.mockResolvedValue([
      { versionId: "ver-1", applicationWorkspaceId: "ws-1", appId: "app-1", version: "v1", branch: "release", status: "READY", updatedAt: "2026-09-21T08:00:00Z" }
    ]);
    api.listTeamContributions.mockResolvedValue([
      contribution("member-1", "pw-1"),
      contribution("member-2", "pw-2")
    ]);
    api.listTeamWorkspaceFiles.mockResolvedValue([
      { path: "src/a.ts", name: "a.ts", type: "file" }
    ]);
    api.readTeamWorkspaceFile.mockReturnValue(read.promise);
    const controller = createTeamManagementController(api, { searchDelayMs: 0 });
    await controller.enter(false);
    const opening = controller.openEntry("src/a.ts", false);
    await vi.waitFor(() => expect(api.readTeamWorkspaceFile).toHaveBeenCalled());
    await controller.selectWorktree("pw-2");
    read.resolve({ path: "src/a.ts", content: "stale", encoding: "utf-8", readonly: true });
    await opening;
    expect(controller.snapshot().tabs.some((tab) => tab.content === "stale")).toBe(false);
    expect(api.closeTeamWorkspaceFileConnections).toHaveBeenCalledWith("pw-1");
    expect(controller.snapshot().selectedPersonalWorkspaceId).toBe("pw-2");
  });

  it("closes team file connections when a read is rejected for lost permission", async () => {
    const api = createApi();
    api.listTeamApplications.mockResolvedValue([
      { appId: "app-1", appName: "应用一", enabled: true, currentMemberCount: 1, historicalMemberCount: 0 }
    ]);
    api.listTeamWorkspaceTemplates.mockResolvedValue([
      { workspaceId: "ws-1", appId: "app-1", workspaceName: "研发空间", branch: "release", directoryPath: "repo", enabled: true }
    ]);
    api.listTeamWorkspaceVersions.mockResolvedValue([
      { versionId: "ver-1", applicationWorkspaceId: "ws-1", appId: "app-1", version: "v1", branch: "release", status: "READY", updatedAt: "2026-09-21T08:00:00Z" }
    ]);
    api.listTeamContributions.mockResolvedValue([contribution("member-1", "pw-1")]);
    api.readTeamWorkspaceFile.mockRejectedValue(new BackendApiError(403, {
      success: false,
      code: "FORBIDDEN",
      message: "已不在该团队",
      traceId: "trace-1",
      details: {}
    }));
    const controller = createTeamManagementController(api);
    await controller.enter(false);
    await controller.openEntry("src/a.ts", false);
    expect(api.closeTeamWorkspaceFileConnections).toHaveBeenCalledWith();
    expect(controller.snapshot().tabs).toEqual([]);
    expect(controller.snapshot().errorMessage).toContain("已不在该团队");
  });

  it("opens an uncommitted change as a read-only diff without a save path", async () => {
    const api = createApi();
    api.listTeamApplications.mockResolvedValue([
      { appId: "app-1", appName: "应用一", enabled: true, currentMemberCount: 1, historicalMemberCount: 0 }
    ]);
    api.listTeamWorkspaceTemplates.mockResolvedValue([
      { workspaceId: "ws-1", appId: "app-1", workspaceName: "研发空间", branch: "release", directoryPath: "repo", enabled: true }
    ]);
    api.listTeamWorkspaceVersions.mockResolvedValue([
      { versionId: "ver-1", applicationWorkspaceId: "ws-1", appId: "app-1", version: "v1", branch: "release", status: "READY", updatedAt: "2026-09-21T08:00:00Z" }
    ]);
    api.listTeamContributions.mockResolvedValue([contribution("member-1", "pw-1")]);
    api.getTeamWorkspaceGitStatus.mockResolvedValue({
      files: [{ path: "src/a.ts", rawStatus: "M", status: "modified", staged: false, patch: "@@ -1 +1 @@\n-a\n+b\n", additions: 1, deletions: 1 }],
      stagedCount: 0,
      unstagedCount: 1,
      untrackedCount: 0
    });
    const controller = createTeamManagementController(api);
    await controller.enter(false);
    controller.openChange("src/a.ts");
    const tab = controller.snapshot().tabs[0];
    expect(tab?.kind).toBe("diff");
    expect(tab?.patch).toContain("+b");
    controller.exit();
    expect(api.closeTeamWorkspaceFileConnections).toHaveBeenCalledWith();
    expect(controller.snapshot().active).toBe(false);
    expect(controller.snapshot().tabs).toEqual([]);
  });

  it("switches a too-large team file to a read-only preview chunk", async () => {
    const api = createApi();
    api.listTeamApplications.mockResolvedValue([
      { appId: "app-1", appName: "应用一", enabled: true, currentMemberCount: 1, historicalMemberCount: 0 }
    ]);
    api.listTeamWorkspaceTemplates.mockResolvedValue([
      { workspaceId: "ws-1", appId: "app-1", workspaceName: "研发空间", branch: "release", directoryPath: "repo", enabled: true }
    ]);
    api.listTeamWorkspaceVersions.mockResolvedValue([
      { versionId: "ver-1", applicationWorkspaceId: "ws-1", appId: "app-1", version: "v1", branch: "release", status: "READY", updatedAt: "2026-09-21T08:00:00Z" }
    ]);
    api.listTeamContributions.mockResolvedValue([contribution("member-1", "pw-1")]);
    api.readTeamWorkspaceFile.mockRejectedValue(new BackendApiError(413, {
      success: false,
      code: "PAYLOAD_TOO_LARGE",
      message: "文件超过整读上限",
      traceId: "trace-large",
      details: { reason: "PREVIEW_TOO_LARGE", size: 20, maxPreviewBytes: 8 }
    }));
    api.readTeamWorkspaceFilePreviewChunk.mockResolvedValue({
      path: "logs/large.log",
      content: "chunk",
      offset: 0,
      nextOffset: 5,
      size: 20,
      eof: false,
      warningThresholdBytes: 8,
      lastModifiedMillis: 10
    });
    const controller = createTeamManagementController(api);
    await controller.enter(false);
    await controller.openEntry("logs/large.log", false);
    const tab = controller.snapshot().tabs[0];
    expect(tab?.content).toBe("chunk");
    expect(tab?.progressive?.nextOffset).toBe(5);
    expect(api.readTeamWorkspaceFilePreviewChunk).toHaveBeenCalledWith(
      { scopeMode: "MY_TEAM", ownerUserId: undefined },
      "pw-1",
      "ws-pw-1",
      "logs/large.log",
      { offset: 0 }
    );
  });
});
