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
    workspaceName: "default",
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


function reviewEntry(path: string) {
  return { path, name: path.split("/").at(-1)!, directory: false, size: 20,
    selected: { source: { userId: "member-1", username: "成员甲", personalWorkspaceId: "pw-1", workspaceId: "ws-pw-1", linuxServerId: "server-1" },
      file: { path, name: path.split("/").at(-1)!, directory: false, size: 20, contentVersion: "sha256:test", author: "实际作者",
        changedAt: "2026-09-21T08:00:00Z", timeType: "GIT_COMMIT" as const, changeType: "COMMITTED", deleted: false } },
    latestUncertain: false, alternatives: [] };
}
function reviewRead(content: string, eof = true) {
  const { source, file } = reviewEntry("src/a.ts").selected;
  return { source, file, chunk: { path: file.path, content, offset: 0, nextOffset: content.length, size: eof ? content.length : 20,
    eof, warningThresholdBytes: 8, lastModifiedMillis: 10 } };
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
    createTeamReviewContext: vi.fn().mockImplementation(async (_scope, versionId, selectedUserId) => ({
      id: "trv_test", versionId, selectedUserId, sources: [], expiresAt: "2099-01-01T00:00:00Z"
    })),
    listTeamReviewFiles: vi.fn().mockResolvedValue({ entries: ["src/a.ts", "a.ts", "logs/large.log"].map(reviewEntry), unavailableMembers: [], complete: true }),
    searchTeamReviewFiles: vi.fn().mockResolvedValue({ entries: [], unavailableMembers: [], complete: true }),
    readTeamReviewFileChunk: vi.fn().mockResolvedValue(reviewRead("hello")),
    closeTeamReviewFileConnections: vi.fn(),
    createTeamExport: vi.fn(),
    getTeamExport: vi.fn(),
    cancelTeamExport: vi.fn(),
    createTeamExportDownloadRoute: vi.fn()
  };
}

describe("team management controller", () => {
  it("defaults to all members and uses the same source/version for the editor and on-demand chat", async () => {
    const api = createApi();
    api.listTeamApplications.mockResolvedValue([{ appId: "app", appName: "应用", enabled: true }]);
    api.listTeamWorkspaceTemplates.mockResolvedValue([{ workspaceId: "template", appId: "app", workspaceName: "spec", enabled: true }]);
    api.listTeamWorkspaceVersions.mockResolvedValue([{ versionId: "version", applicationWorkspaceId: "template", appId: "app", version: "latest" }]);
    api.listTeamContributions.mockResolvedValue([contribution("member-1", "pw-1"), contribution("member-2", "pw-2")]);
    const controller = createTeamManagementController(api);
    await controller.enter(false);
    expect(controller.snapshot().selectedUserId).toBe("");
    expect(controller.snapshot().selectedPersonalWorkspaceId).toBe("");
    expect(api.createTeamReviewContext).toHaveBeenCalledWith({ scopeMode: "MY_TEAM", ownerUserId: undefined }, "version", undefined);
    expect(controller.snapshot().entriesByDirectory[""]?.[0]?.review?.selected.file.author).toBe("实际作者");
    await controller.openEntry("src/a.ts", false);
    expect(api.readTeamReviewFileChunk).toHaveBeenCalledWith("trv_test", "src/a.ts", "sha256:test");
    const part = await controller.prepareChatContext();
    expect((part as { content: string }).content).toContain("全部成员最新文件");
    expect((part as { content: string }).content).not.toContain("hello");
    expect(api.readTeamWorkspaceFile).not.toHaveBeenCalled();
    await controller.selectMember("member-2");
    expect(controller.snapshot().selectedPersonalWorkspaceId).toBe("pw-2");
    await controller.selectMember("");
    expect(controller.snapshot().selectedPersonalWorkspaceId).toBe("");
  });

  it("does not open an uncertain candidate or allow a scope prepared before a member switch", async () => {
    const api = createApi();
    api.listTeamApplications.mockResolvedValue([{ appId: "app", appName: "应用", enabled: true }]);
    api.listTeamWorkspaceTemplates.mockResolvedValue([{ workspaceId: "template", appId: "app", workspaceName: "spec", enabled: true }]);
    api.listTeamWorkspaceVersions.mockResolvedValue([{ versionId: "version", applicationWorkspaceId: "template", appId: "app", version: "latest" }]);
    api.listTeamContributions.mockResolvedValue([contribution("member-1", "pw-1")]);
    api.listTeamReviewFiles.mockResolvedValue({ entries: [{ ...reviewEntry("src/a.ts"), latestUncertain: true }], unavailableMembers: [], complete: true });
    const controller = createTeamManagementController(api);
    await controller.enter(false);
    await controller.openEntry("src/a.ts", false);
    expect(api.readTeamReviewFileChunk).not.toHaveBeenCalled();
    const pendingScope = deferred<{ id: string; versionId: string; sources: []; expiresAt: string }>();
    api.createTeamReviewContext.mockReturnValueOnce(pendingScope.promise);
    const preparing = controller.prepareChatContext();
    const rejection = expect(preparing).rejects.toThrow("审阅范围已切换");
    await controller.selectMember("member-1");
    pendingScope.resolve({ id: "late", versionId: "version", sources: [], expiresAt: "2099-01-01T00:00:00Z" });
    await rejection;
  });
  it("keeps a system administrator on their own team and allows member changes", async () => {
    const api = createApi();
    const controller = createTeamManagementController(api);
    await controller.enter(false);
    await controller.selectMember("member-1");

    expect(controller.snapshot().scopeMode).toBe("MY_TEAM");
    expect(controller.snapshot().scopeLocked).toBe(true);
    expect(canMaintainTeamMembers(controller.snapshot())).toBe(true);
    expect(api.listTeamApplications).toHaveBeenCalledWith({
      scopeMode: "MY_TEAM",
      ownerUserId: undefined,
      targetUserId: "member-1"
    });

    await controller.openMemberDialog();
    expect(api.listSystemAdminTeamMembers).toHaveBeenCalled();
    await controller.removeMember("member-1");
    expect(api.removeSystemAdminTeamMember).toHaveBeenCalledWith(
      { scopeMode: "MY_TEAM", ownerUserId: undefined },
      "member-1"
    );
  });

  it("starts each super-admin entry on their own team and keeps the read-only platform scope available", async () => {
    const api = createApi();
    const controller = createTeamManagementController(api);
    await controller.enter(true);
    expect(controller.snapshot().scopeMode).toBe("MY_TEAM");
    expect(canMaintainTeamMembers(controller.snapshot())).toBe(true);
    expect(api.listSystemAdminTeamMembers).toHaveBeenCalledWith(
      { scopeMode: "MY_TEAM", ownerUserId: undefined }, "", 1, 200
    );

    await controller.selectScope("GLOBAL");
    expect(canMaintainTeamMembers(controller.snapshot())).toBe(false);
    expect(TEAM_MEMBER_SCOPE_HINT).toContain("我的团队");

    await controller.selectScope("SYSTEM_ADMIN_TEAM", "owner-1");
    expect(canMaintainTeamMembers(controller.snapshot())).toBe(true);
    expect(api.listTeamApplications).toHaveBeenLastCalledWith({
      scopeMode: "SYSTEM_ADMIN_TEAM",
      ownerUserId: "owner-1",
      targetUserId: undefined
    });
    expect(api.closeTeamWorkspaceFileConnections).toHaveBeenCalledWith();
  });

  it("opens a team picker before loading members from the global read-only scope", async () => {
    const api = createApi();
    const controller = createTeamManagementController(api);
    await controller.enter(true);
    await controller.selectScope("GLOBAL");

    api.listSystemAdminTeamMembers.mockClear();
    await controller.openMemberDialog();
    expect(controller.snapshot().teamPickerDialogOpen).toBe(true);
    expect(controller.snapshot().memberDialogOpen).toBe(false);
    expect(api.listSystemAdminTeamMembers).not.toHaveBeenCalled();

    await controller.selectMemberManagementOwner("owner-1");
    expect(controller.snapshot().scopeMode).toBe("SYSTEM_ADMIN_TEAM");
    expect(controller.snapshot().ownerUserId).toBe("owner-1");
    expect(controller.snapshot().teamPickerDialogOpen).toBe(false);
    expect(controller.snapshot().memberDialogOpen).toBe(true);
    expect(api.listSystemAdminTeamMembers).toHaveBeenCalledWith(
      { scopeMode: "SYSTEM_ADMIN_TEAM", ownerUserId: "owner-1" },
      "",
      1,
      20
    );
  });

  it("waits for the owner catalog and exposes a retry after a transient failure", async () => {
    const api = createApi();
    const controller = createTeamManagementController(api);
    await controller.enter(true);
    await controller.selectScope("GLOBAL");
    await vi.waitFor(() => expect(controller.snapshot().ownersLoaded).toBe(true));
    api.listSystemAdmins
      .mockRejectedValueOnce(new Error("系统管理员团队暂时不可用"))
      .mockResolvedValueOnce({ items: [user("owner-1", "系统管理员甲")], total: 1, page: 1, size: 200 });

    await controller.openMemberDialog();
    await controller.retryOwners();
    expect(controller.snapshot().ownersLoaded).toBe(false);
    expect(controller.snapshot().ownersError).toContain("系统管理员团队暂时不可用");
    expect(controller.snapshot().teamPickerDialogOpen).toBe(true);

    await controller.retryOwners();
    expect(controller.snapshot().ownersLoaded).toBe(true);
    expect(controller.snapshot().owners).toHaveLength(1);
    expect(controller.snapshot().ownersError).toBe("");
  });

  it("lets a super-admin add the first member to their own team without another system-admin owner", async () => {
    const api = createApi();
    api.listSystemAdmins.mockResolvedValue({ items: [], total: 0, page: 1, size: 200 });
    api.listSystemAdminTeamMembers.mockResolvedValue({ items: [], total: 0, page: 1, size: 20 });
    const controller = createTeamManagementController(api);
    await controller.enter(true);

    await controller.openMemberDialog();
    expect(controller.snapshot().memberDialogOpen).toBe(true);
    expect(controller.snapshot().teamPickerDialogOpen).toBe(false);
    expect(api.listSystemAdminTeamCandidates).toHaveBeenCalledWith(
      { scopeMode: "MY_TEAM", ownerUserId: undefined }, "", 1, 50
    );

    controller.chooseCandidate("candidate-1");
    await controller.addMember();
    expect(api.addSystemAdminTeamMember).toHaveBeenCalledWith(
      { scopeMode: "MY_TEAM", ownerUserId: undefined }, "candidate-1"
    );

    controller.closeMemberDialog();
    await controller.selectScope("GLOBAL");
    await controller.openMemberDialog();
    await controller.selectOwnMemberManagement();
    expect(controller.snapshot().scopeMode).toBe("MY_TEAM");
    expect(controller.snapshot().memberDialogOpen).toBe(true);
  });

  it("uses one keyword for current members and addable users", async () => {
    const api = createApi();
    const controller = createTeamManagementController(api);
    await controller.enter(false);
    await controller.selectMember("member-1");
    await controller.openMemberDialog();
    controller.chooseCandidate("candidate-1");

    await controller.searchMembers("成员甲");

    expect(controller.snapshot().memberKeyword).toBe("成员甲");
    expect(controller.snapshot().candidateUserId).toBe("");
    expect(api.listSystemAdminTeamMembers).toHaveBeenLastCalledWith(
      { scopeMode: "MY_TEAM", ownerUserId: undefined }, "成员甲", 1, 20
    );
    expect(api.listSystemAdminTeamCandidates).toHaveBeenLastCalledWith(
      { scopeMode: "MY_TEAM", ownerUserId: undefined }, "成员甲", 1, 50
    );
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
    const read = deferred<ReturnType<typeof reviewRead>>();
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
    api.readTeamReviewFileChunk.mockReturnValue(read.promise);
    const controller = createTeamManagementController(api, { searchDelayMs: 0 });
    await controller.enter(false);
    await controller.selectMember("member-1");
    const opening = controller.openEntry("src/a.ts", false);
    await vi.waitFor(() => expect(api.readTeamReviewFileChunk).toHaveBeenCalled());
    await controller.selectMember("member-2");
    read.resolve(reviewRead("stale"));
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
    api.readTeamReviewFileChunk.mockRejectedValue(new BackendApiError(403, {
      success: false,
      code: "FORBIDDEN",
      message: "已不在该团队",
      traceId: "trace-1",
      details: {}
    }));
    const controller = createTeamManagementController(api);
    await controller.enter(false);
    await controller.selectMember("member-1");
    await controller.openEntry("src/a.ts", false);
    expect(api.closeTeamWorkspaceFileConnections).toHaveBeenCalledWith();
    expect(controller.snapshot().tabs).toEqual([]);
    expect(controller.snapshot().errorMessage).toContain("已不在该团队");
  });

  it("opens latest member files read-only without requesting a hidden changes overview", async () => {
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
    await controller.selectMember("member-1");
    api.listTeamReviewFiles.mockResolvedValue({ entries: [reviewEntry("src/a.ts")], complete: true, unavailableMembers: [] });
    await controller.reloadTree();
    await controller.openEntry("src/a.ts", false);
    const tab = controller.snapshot().tabs[0];
    expect(tab?.kind).toBe("file");
    expect(api.getTeamWorkspaceGitStatus).not.toHaveBeenCalled();
    expect(api.listTeamWorkspaceCommits).not.toHaveBeenCalled();
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

    api.readTeamReviewFileChunk.mockResolvedValue(reviewRead("chunk", false));
    const controller = createTeamManagementController(api);
    await controller.enter(false);
    await controller.selectMember("member-1");
    await controller.openEntry("logs/large.log", false);
    const tab = controller.snapshot().tabs[0];
    expect(tab?.content).toBe("chunk");
    expect(tab?.progressive?.nextOffset).toBe(5);
    expect(api.readTeamReviewFileChunk).toHaveBeenCalledWith("trv_test", "logs/large.log", "sha256:test");
  });

  it("selects an enabled workspace when the first template is disabled", async () => {
    const api = createApi();
    api.listTeamApplications.mockResolvedValue([
      { appId: "app-1", appName: "应用一", enabled: true, currentMemberCount: 1, historicalMemberCount: 0 }
    ]);
    api.listTeamWorkspaceTemplates.mockResolvedValue([
      { workspaceId: "ws-off", appId: "app-1", workspaceName: "停用空间", branch: "old", directoryPath: "repo", enabled: false },
      { workspaceId: "ws-on", appId: "app-1", workspaceName: "可用空间", branch: "release", directoryPath: "repo", enabled: true }
    ]);
    api.listTeamWorkspaceVersions.mockImplementation(async (_scope, workspaceId: string) => [
      { versionId: `ver-${workspaceId}`, applicationWorkspaceId: workspaceId, appId: "app-1", version: "v1", branch: "release", status: "READY", updatedAt: "2026-09-21T08:00:00Z" }
    ]);
    api.listTeamContributions.mockResolvedValue([contribution("member-1", "pw-1")]);
    const controller = createTeamManagementController(api);
    await controller.enter(false);
    await controller.selectMember("member-1");
    expect(controller.snapshot().selectedTemplateId).toBe("ws-on");
    expect(controller.snapshot().selectedVersionId).toBe("ver-ws-on");
  });

  it("refreshes the platform roster from the current version without replacing the selected member", async () => {
    const api = createApi();
    api.listTeamApplications.mockResolvedValue([
      { appId: "app-1", appName: "应用一", enabled: true, currentMemberCount: 2, historicalMemberCount: 0, membershipState: "CURRENT" },
      { appId: "app-2", appName: "应用二", enabled: true, currentMemberCount: 2, historicalMemberCount: 0, membershipState: "CURRENT" }
    ]);
    api.listTeamWorkspaceTemplates.mockImplementation(async (_scope, appId: string) => [
      { workspaceId: `ws-${appId}`, appId, workspaceName: "研发空间", branch: "release", directoryPath: "repo", enabled: true, membershipState: "CURRENT" }
    ]);
    api.listTeamWorkspaceVersions.mockResolvedValue([
      { versionId: "ver-1", applicationWorkspaceId: "ws-app-1", appId: "app-1", version: "v1", branch: "release", status: "READY", updatedAt: "2026-09-21T08:00:00Z", membershipState: "CURRENT" }
    ]);
    api.listTeamContributions
      .mockResolvedValueOnce([contribution("member-1", "pw-1")])
      .mockResolvedValueOnce([contribution("member-1", "pw-1")])
      .mockResolvedValue([contribution("member-1", "pw-1"), contribution("member-2", "pw-2")]);
    const controller = createTeamManagementController(api);
    await controller.enter(true, { appId: "app-1", templateId: "ws-app-1", versionId: "ver-1" });
    await controller.selectMember("member-1");
    await controller.selectScope("GLOBAL");

    expect(controller.snapshot().selectedUserId).toBe("member-1");
    expect(controller.snapshot().reviewRoster.map((item) => item.userId)).toEqual(["member-1", "member-2"]);
    await controller.selectApplication("app-2");
    expect(controller.snapshot().selectedUserId).toBe("member-1");
    expect(controller.snapshot().reviewRoster.map((item) => item.userId)).toEqual(["member-1", "member-2"]);
    await controller.selectMember("member-2");
    expect(controller.snapshot().selectedAppId).toBe("app-2");
    expect(controller.snapshot().selectedUserId).toBe("member-2");
  });

  it("keeps the current application when entering and does not clear the member when the application changes", async () => {
    const api = createApi();
    api.listSystemAdminTeamMembers.mockResolvedValue({
      items: [user("member-1", "成员甲"), user("member-2", "成员乙")],
      total: 2,
      page: 1,
      size: 200
    });
    api.listTeamApplications.mockResolvedValue([
      { appId: "app-1", appName: "应用一", enabled: true, currentMemberCount: 1, historicalMemberCount: 0, membershipState: "CURRENT" },
      { appId: "app-2", appName: "应用二", enabled: true, currentMemberCount: 0, historicalMemberCount: 1, membershipState: "HISTORICAL" }
    ]);
    api.listTeamWorkspaceTemplates.mockImplementation(async (_scope, appId: string) => [
      { workspaceId: `ws-${appId}`, appId, workspaceName: "研发空间", branch: "release", directoryPath: "repo", enabled: true, membershipState: "CURRENT" }
    ]);
    api.listTeamWorkspaceVersions.mockResolvedValue([
      { versionId: "ver-1", applicationWorkspaceId: "ws-app-1", appId: "app-1", version: "v1", branch: "release", status: "READY", updatedAt: "2026-09-21T08:00:00Z", membershipState: "CURRENT" }
    ]);
    api.listTeamContributions.mockResolvedValue([
      contribution("member-1", "pw-1"),
      contribution("member-2", "pw-2")
    ]);
    const controller = createTeamManagementController(api);
    await controller.enter(false, { appId: "app-2", templateId: "ws-app-2", versionId: "ver-1" });
    expect(controller.snapshot().selectedUserId).toBe("");
    await controller.selectMember("member-1");

    expect(controller.snapshot().selectedAppId).toBe("app-2");
    expect(controller.snapshot().selectedUserId).toBe("member-1");
    await controller.selectApplication("app-1");
    expect(controller.snapshot().selectedUserId).toBe("member-1");
    expect(controller.snapshot().selectedAppId).toBe("app-1");
    await controller.selectMember("member-2");
    expect(controller.snapshot().selectedUserId).toBe("member-2");
    expect(controller.snapshot().selectedAppId).toBe("app-1");
    expect(api.listTeamApplications).toHaveBeenLastCalledWith({
      scopeMode: "MY_TEAM",
      ownerUserId: undefined,
      targetUserId: "member-2"
    });
  });

  it("shows an empty review state when the version has no default personal workspace", async () => {
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
    api.listTeamContributions.mockResolvedValue([{
      ...contribution("member-1", "pw-named"),
      personalWorkspaces: [{ ...worktree("pw-named"), workspaceName: "feature-space" }]
    }]);
    const controller = createTeamManagementController(api);
    await controller.enter(false);
    await controller.selectMember("member-1");
    expect(controller.snapshot().missingDefaultWorkspace).toBe(true);
    expect(controller.snapshot().selectedPersonalWorkspaceId).toBe("");
    expect(api.listTeamWorkspaceFiles).not.toHaveBeenCalled();
  });

  it("keeps a retryable error when the catalog, preview chunk, or commit diff fails", async () => {
    const api = createApi();
    api.listTeamApplications.mockRejectedValueOnce(new Error("应用列表失败"));
    const controller = createTeamManagementController(api);
    await controller.enter(false);
    expect(controller.snapshot().catalogError).toContain("应用列表失败");
    expect(controller.snapshot().catalogLoading).toBe(false);

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
    await controller.retryCatalog();
    await controller.selectMember("member-1");
    expect(controller.snapshot().catalogError).toBe("");
    expect(controller.snapshot().selectedAppId).toBe("app-1");


    api.readTeamReviewFileChunk.mockRejectedValueOnce(new Error("分段失败"));
    await controller.openEntry("logs/large.log", false);
    expect(controller.snapshot().tabs[0]?.loadState).toBe("error");
    api.readTeamReviewFileChunk.mockResolvedValue(reviewRead("chunk", false));
    await controller.retryTab(controller.snapshot().tabs[0]!.id);
    expect(controller.snapshot().tabs[0]?.content).toBe("chunk");

    api.getTeamWorkspaceCommitDetail.mockResolvedValue({
      commit: commit("abc"),
      files: [{ status: "M", path: "src/a.ts" }]
    });
    api.getTeamWorkspaceCommitDiff.mockRejectedValueOnce(new Error("差异失败"));
    await controller.selectCommit(commit("abc"), "PERSONAL");
    await controller.openCommitFile("src/a.ts");
    expect(controller.snapshot().tabs.at(-1)?.loadState).toBe("error");
    api.getTeamWorkspaceCommitDiff.mockResolvedValue({ commit: "abc", path: "src/a.ts", patch: "@@\n+ok\n" });
    await controller.retryTab(controller.snapshot().tabs.at(-1)!.id);
    expect(controller.snapshot().tabs.at(-1)?.patch).toContain("+ok");
  });

  it("does not let hidden legacy statistics failure block the latest files", async () => {
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
    api.getTeamWorkspaceGitStatus.mockRejectedValue(new Error("个人工作区目录不可用"));
    const controller = createTeamManagementController(api);
    await controller.enter(false);
    await controller.selectMember("member-1");
    expect(controller.snapshot().treeError).toBe("");
    expect(controller.snapshot().reviewContext).not.toBeNull();
    expect(controller.snapshot().detailLoading).toBe(false);
    expect(api.getTeamWorkspaceGitStatus).not.toHaveBeenCalled();
    expect(api.listTeamWorkspaceCommits).not.toHaveBeenCalled();
  });
});
