import { mount } from "@vue/test-utils";
import { defineComponent } from "vue";
import { describe, expect, it, vi } from "vitest";

vi.mock("../src/components/FigmaFileExplorer.vue", () => ({
  default: defineComponent({
    name: "FigmaFileExplorer",
    props: {
      readonlyReview: Boolean,
      canWrite: Boolean,
      workspaceKind: String,
      changedFiles: Array,
      entriesByDirectory: Object,
      emptyWorkspaceMessage: String,
      fileTreeError: String
    },
    template: "<section data-testid=\"review-explorer\" />"
  })
}));

import TeamReviewFilePane from "../src/components/team/TeamReviewFilePane.vue";
import TeamReviewPane from "../src/components/team/TeamReviewPane.vue";
import {
  TEAM_MEMBER_SCOPE_HINT,
  createTeamManagementController,
  teamManagementKey
} from "../src/components/team/team-management-controller";


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

function api() {
  return {
    listSystemAdmins: vi.fn().mockResolvedValue({ items: [], total: 0, page: 1, size: 200 }),
    listSystemAdminTeamMembers: vi.fn().mockResolvedValue({ items: [], total: 0, page: 1, size: 20 }),
    listSystemAdminTeamCandidates: vi.fn().mockResolvedValue({ items: [], total: 0, page: 1, size: 50 }),
    addSystemAdminTeamMember: vi.fn(),
    removeSystemAdminTeamMember: vi.fn(),
    listTeamApplications: vi.fn().mockResolvedValue([]),
    listTeamWorkspaceTemplates: vi.fn().mockResolvedValue([]),
    listTeamWorkspaceVersions: vi.fn().mockResolvedValue([]),
    listTeamContributions: vi.fn().mockResolvedValue([]),
    getTeamWorkspaceGitStatus: vi.fn().mockResolvedValue({ files: [], stagedCount: 0, unstagedCount: 0, untrackedCount: 0 }),
    listTeamWorkspaceCommits: vi.fn().mockResolvedValue({ items: [], offset: 0, limit: 100, hasMore: false, attributionConfirmed: true }),
    getTeamWorkspaceCommitDetail: vi.fn(),
    getTeamWorkspaceCommitDiff: vi.fn(),
    listTeamWorkspaceFiles: vi.fn().mockResolvedValue([]),
    searchTeamWorkspaceFiles: vi.fn().mockResolvedValue([]),
    readTeamWorkspaceFile: vi.fn(),
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

describe("team review panes", () => {
  it("shows the selected member's latest files instead of a changes overview", async () => {
    const backend = api();
    backend.listSystemAdminTeamMembers.mockResolvedValue({ items: [{ userId: "member-1", username: "成员甲", unifiedAuthId: "member-auth", department: "质量部", status: "ACTIVE", roles: ["USER"] }], total: 1, page: 1, size: 200 });
    backend.listTeamApplications.mockResolvedValue([{ appId: "app-1", appName: "应用一", enabled: true, currentMemberCount: 1, historicalMemberCount: 0 }]);
    backend.listTeamWorkspaceTemplates.mockResolvedValue([{ workspaceId: "template-1", appId: "app-1", workspaceName: "空间", branch: "release", directoryPath: "repo", enabled: true }]);
    backend.listTeamWorkspaceVersions.mockResolvedValue([{ versionId: "version-1", applicationWorkspaceId: "template-1", appId: "app-1", version: "v1", branch: "release", status: "READY", updatedAt: "2026-09-21T08:00:00Z" }]);
    backend.listTeamContributions.mockResolvedValue([{
      userId: "member-1", username: "成员甲", unifiedAuthId: "member-auth", department: "质量部", membershipState: "CURRENT",
      personalWorkspaces: [{ personalWorkspaceId: "personal-1", workspaceId: "runtime-1", workspaceName: "default", branch: "feature/member", linuxServerId: "server-1", baseCommit: "abc", status: "READY", updatedAt: "2026-09-21T08:00:00Z" }]
    }]);
    backend.getTeamWorkspaceGitStatus.mockResolvedValue({ files: [{ path: "src/a.ts", rawStatus: " M", status: "modified", staged: false, patch: "@@ -1 +1 @@", additions: 1, deletions: 1 }], stagedCount: 0, unstagedCount: 1, untrackedCount: 0 });
    const controller = createTeamManagementController(backend);
    await controller.enter(false);
    await controller.selectMember("member-1");
    expect(controller.snapshot().entriesByDirectory[""]).toHaveLength(3);
    const files = mount(TeamReviewFilePane, { global: { provide: { [teamManagementKey as symbol]: controller } } });
    expect(files.getComponent({ name: "FigmaFileExplorer" }).props("changedFiles")).toEqual([]);
    expect(files.getComponent({ name: "FigmaFileExplorer" }).props("entriesByDirectory")[""][0].review.selected.file.author).toBe("实际作者");
    expect(backend.getTeamWorkspaceGitStatus).not.toHaveBeenCalled();
    expect(backend.listTeamWorkspaceCommits).not.toHaveBeenCalled();
    await controller.openEntry("src/a.ts", false);
    expect(controller.snapshot().tabs.some((tab) => tab.kind === "file" && tab.path === "src/a.ts")).toBe(true);
    files.unmount();
  });

  it("binds a fresh read-only scope without preloading or truncating file bodies", async () => {
    const backend = api();
    backend.listSystemAdminTeamMembers.mockResolvedValue({ items: [{ userId: "member-1", username: "成员甲", unifiedAuthId: "member-auth", department: "质量部", status: "ACTIVE", roles: ["USER"] }], total: 1, page: 1, size: 200 });
    backend.listTeamApplications.mockResolvedValue([{ appId: "app-1", appName: "应用一", enabled: true, currentMemberCount: 1, historicalMemberCount: 0 }]);
    backend.listTeamWorkspaceTemplates.mockResolvedValue([{ workspaceId: "template-1", appId: "app-1", workspaceName: "空间", branch: "release", directoryPath: "repo", enabled: true }]);
    backend.listTeamWorkspaceVersions.mockResolvedValue([{ versionId: "version-1", applicationWorkspaceId: "template-1", appId: "app-1", version: "v1", branch: "release", status: "READY", updatedAt: "2026-09-21T08:00:00Z" }]);
    backend.listTeamContributions.mockResolvedValue([{
      userId: "member-1", username: "成员甲", unifiedAuthId: "member-auth", department: "质量部", membershipState: "CURRENT",
      personalWorkspaces: [{ personalWorkspaceId: "personal-1", workspaceId: "runtime-1", workspaceName: "default", branch: "feature/member", linuxServerId: "server-1", baseCommit: "abc", status: "READY", updatedAt: "2026-09-21T08:00:00Z" }]
    }]);
    backend.searchTeamWorkspaceFiles.mockResolvedValue([
      { path: "src/app.ts", name: "app.ts", directory: false, size: 20 },
      { path: ".env", name: ".env", directory: false, size: 20 }
    ]);
    backend.readTeamWorkspaceFile.mockResolvedValue({ path: "src/app.ts", content: "export const answer = 42;", encoding: "utf-8", readonly: true });
    const controller = createTeamManagementController(backend);
    await controller.enter(false);
    await controller.selectMember("member-1");
    const part = await controller.prepareChatContext();
    expect(part?.type).toBe("reference");
    expect((part as { label: string }).label).toContain("team-review");
    expect((part as { label: string }).label).toContain("trv_test");
    expect((part as { label: string }).label).toContain("成员甲");
    expect((part as { label: string }).label).not.toContain("answer = 42");
    expect(backend.readTeamWorkspaceFile).not.toHaveBeenCalled();
    expect(backend.readTeamReviewFileChunk).not.toHaveBeenCalled();

  });

  it("explains why platform-wide scope cannot change members and keeps the file tree read-only", async () => {
    const controller = createTeamManagementController(api());
    await controller.enter(true);
    await controller.selectScope("GLOBAL");
    const provide = { [teamManagementKey as symbol]: controller };
    const review = mount(TeamReviewPane, { global: { provide } });
    const files = mount(TeamReviewFilePane, { global: { provide } });

    expect(review.text()).toContain(TEAM_MEMBER_SCOPE_HINT);
    expect(review.get("button").text()).toContain("成员管理");
    await review.get("button").trigger("click");
    expect(review.text()).toContain(TEAM_MEMBER_SCOPE_HINT);
    const explorer = files.getComponent({ name: "FigmaFileExplorer" });
    expect(explorer.props("readonlyReview")).toBe(true);
    expect(explorer.props("canWrite")).toBe(false);
    expect(explorer.props("workspaceKind")).toBe("MANAGED");
    expect(files.text()).not.toContain("worktree");
    expect(review.text()).not.toContain("worktree");

    review.unmount();
    files.unmount();
  });

  it("opens only a member picker from the floating username and returns to the selected workspace", async () => {
    const backend = api();
    backend.listSystemAdminTeamMembers.mockResolvedValue({
      items: [
        { userId: "member-1", username: "成员甲", unifiedAuthId: "one", department: "质量部" },
        { userId: "member-2", username: "成员乙", unifiedAuthId: "two", department: "研发部" }
      ], total: 2, page: 1, size: 200
    });
    const controller = createTeamManagementController(backend);
    await controller.enter(true);
    const provide = { [teamManagementKey as symbol]: controller };
    const review = mount(TeamReviewPane, {
      props: { rightPanelOpen: false },
      global: { provide }
    });

    const launcherHandle = document.body.querySelector<HTMLButtonElement>("[aria-label='展开成员入口']");
    expect(launcherHandle).not.toBeNull();
    launcherHandle?.click();
    await review.vm.$nextTick();
    const launcher = document.body.querySelector<HTMLButtonElement>("[aria-label='选择审阅成员']");
    expect(launcher).not.toBeNull();
    expect(document.body.querySelector("[aria-label='打开AI对话']")).toBeNull();
    expect(document.body.querySelector("[aria-label='管理团队成员']")).toBeNull();
    const dialog = document.body.querySelector("[role='dialog'][aria-label='选择成员']");
    expect(dialog).not.toBeNull();
    expect(document.activeElement).toBe(dialog?.querySelector("input[aria-label='搜索审阅成员']"));
    expect(dialog?.textContent).toContain("成员乙");
    expect(dialog?.textContent).not.toContain("个人提交");
    expect(dialog?.textContent).not.toContain("整组导出");
    const search = dialog?.querySelector<HTMLInputElement>("input[aria-label='搜索审阅成员']");
    expect(search?.closest(".team-review-member-filter")).not.toBeNull();
    if (search) {
      search.value = "成员乙";
      search.dispatchEvent(new Event("input", { bubbles: true }));
      await review.vm.$nextTick();
      expect(dialog?.textContent).not.toContain("成员甲");
      search.value = "";
      search.dispatchEvent(new Event("input", { bubbles: true }));
      await review.vm.$nextTick();
    }
    dialog?.querySelector<HTMLButtonElement>(".team-review-member-add")?.click();
    await review.vm.$nextTick();
    expect(document.body.querySelector("[role='dialog'][aria-label='成员管理']")).not.toBeNull();
    document.body.querySelector<HTMLButtonElement>("[aria-label='关闭成员管理']")?.click();
    await review.vm.$nextTick();
    launcher?.click();
    await review.vm.$nextTick();
    const reopened = document.body.querySelector("[role='dialog'][aria-label='选择成员']");
    expect(reopened).not.toBeNull();
    reopened?.querySelectorAll<HTMLButtonElement>("[role='option']")[2]?.click();
    await vi.waitFor(() => expect(controller.snapshot().selectedUserId).toBe("member-2"));
    expect(document.body.querySelector("[role='dialog'][aria-label='选择成员']")).toBeNull();
    expect(launcher?.textContent).toContain("成员乙");

    review.unmount();
  });

  it("keeps the member launcher beside the chat column without a conversation button", async () => {
    const controller = createTeamManagementController(api());
    await controller.enter(true);
    const provide = { [teamManagementKey as symbol]: controller };
    const review = mount(TeamReviewPane, {
      props: { rightPanelOpen: false, chatOpen: false },
      global: { provide }
    });

    const launcherGroup = document.body.querySelector<HTMLElement>(".team-review-launcher-group");
    expect(launcherGroup).not.toBeNull();
    expect(launcherGroup?.style.right).toBe("10px");
    expect(launcherGroup?.classList.contains("team-review-launcher-group--collapsed")).toBe(true);

    expect(document.body.querySelector("[aria-label='打开AI对话']")).toBeNull();

    await review.setProps({ chatOpen: true });
    expect(launcherGroup?.style.right).toBe("460px");
    document.body.querySelector<HTMLButtonElement>("[aria-label='展开成员入口']")?.click();
    await review.vm.$nextTick();
    expect(launcherGroup?.classList.contains("team-review-launcher-group--collapsed")).toBe(false);
    expect(document.body.querySelector("[role='dialog'][aria-label='选择成员']")).not.toBeNull();
    document.body.querySelector<HTMLButtonElement>("[aria-label='收起成员入口']")?.click();
    await review.vm.$nextTick();
    expect(launcherGroup?.classList.contains("team-review-launcher-group--collapsed")).toBe(true);
    review.unmount();
  });

  it("opens member management after selecting a system administrator team", async () => {
    const backend = api();
    backend.listSystemAdmins.mockResolvedValue({
      items: [{ userId: "owner-1", username: "系统管理员甲", unifiedAuthId: "owner-auth", department: "质量部" }],
      total: 1,
      page: 1,
      size: 200
    });
    backend.listSystemAdminTeamCandidates.mockResolvedValue({
      items: [{ userId: "candidate-1", username: "待添加成员", unifiedAuthId: "candidate-auth", department: "质量部" }],
      total: 1,
      page: 1,
      size: 50
    });
    const controller = createTeamManagementController(backend);
    await controller.enter(true);
    await controller.selectScope("GLOBAL");
    const provide = { [teamManagementKey as symbol]: controller };
    const review = mount(TeamReviewPane, { global: { provide } });

    await review.get("button").trigger("click");
    expect(document.body.querySelector("[role='dialog'][aria-label='选择要管理的团队']")).not.toBeNull();
    const owner = document.body.querySelector<HTMLButtonElement>(".team-owner-option:not([data-testid='team-own-management'])");
    owner?.click();
    await review.vm.$nextTick();
    await vi.waitFor(() => expect(document.body.querySelector("[role='dialog'][aria-label='成员管理']")).not.toBeNull());
    expect(backend.listSystemAdminTeamMembers).toHaveBeenCalled();
    const search = document.body.querySelector<HTMLInputElement>("input[aria-label='搜索或选择团队成员']");
    expect(search).not.toBeNull();
    search?.focus();
    await review.vm.$nextTick();
    expect(document.body.querySelectorAll("[role='dialog'][aria-label='成员管理'] input")).toHaveLength(1);
    document.body.querySelector<HTMLButtonElement>("#team-member-candidates [role='option']")?.click();
    await review.vm.$nextTick();
    expect(document.body.querySelector(".team-member-add button")?.textContent).toContain("待添加成员");
    document.body.querySelector<HTMLButtonElement>(".team-member-add button")?.click();
    await vi.waitFor(() => expect(backend.addSystemAdminTeamMember).toHaveBeenCalledWith(
      { scopeMode: "SYSTEM_ADMIN_TEAM", ownerUserId: "owner-1" }, "candidate-1"
    ));
    review.unmount();
  });

  it("lets a super-admin add a first member from their own team when no other owner exists", async () => {
    const backend = api();
    backend.listSystemAdminTeamCandidates.mockResolvedValue({
      items: [{ userId: "candidate-1", username: "待添加成员", unifiedAuthId: "candidate-auth", department: "质量部" }],
      total: 1,
      page: 1,
      size: 50
    });
    const controller = createTeamManagementController(backend);
    await controller.enter(true);
    const review = mount(TeamReviewPane, { global: { provide: { [teamManagementKey as symbol]: controller } } });

    expect((review.get("select[aria-label='团队范围']").element as HTMLSelectElement).value).toBe("MY_TEAM");
    await review.get("button").trigger("click");
    await vi.waitFor(() => expect(document.body.querySelector("[role='dialog'][aria-label='成员管理']")).not.toBeNull());
    expect(document.body.querySelector("[role='dialog'][aria-label='选择要管理的团队']")).toBeNull();

    const search = document.body.querySelector<HTMLInputElement>("input[aria-label='搜索或选择团队成员']");
    search?.focus();
    await review.vm.$nextTick();
    document.body.querySelector<HTMLButtonElement>("#team-member-candidates [role='option']")?.click();
    await review.vm.$nextTick();
    document.body.querySelector<HTMLButtonElement>(".team-member-add button")?.click();
    await vi.waitFor(() => expect(backend.addSystemAdminTeamMember).toHaveBeenCalledWith(
      { scopeMode: "MY_TEAM", ownerUserId: undefined }, "candidate-1"
    ));
    review.unmount();
  });

  it("closes the team picker and member dialog with Escape even when focus stays on the trigger", async () => {
    const backend = api();
    backend.listSystemAdmins.mockResolvedValue({
      items: [{ userId: "owner-1", username: "系统管理员甲", unifiedAuthId: "owner-auth", department: "质量部" }],
      total: 1,
      page: 1,
      size: 200
    });
    const controller = createTeamManagementController(backend);
    await controller.enter(true);
    await controller.selectScope("GLOBAL");
    const provide = { [teamManagementKey as symbol]: controller };
    const review = mount(TeamReviewPane, { attachTo: document.body, global: { provide } });

    const trigger = review.get("button");
    trigger.element.focus();
    await trigger.trigger("click");
    const picker = document.body.querySelector<HTMLElement>("[role='dialog'][aria-label='选择要管理的团队']");
    expect(picker).not.toBeNull();
    expect(document.activeElement).toBe(trigger.element);
    window.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
    await review.vm.$nextTick();
    expect(document.body.querySelector("[role='dialog'][aria-label='选择要管理的团队']")).toBeNull();

    await controller.selectMemberManagementOwner("owner-1");
    const members = document.body.querySelector<HTMLElement>("[role='dialog'][aria-label='成员管理']");
    expect(members).not.toBeNull();
    window.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
    await review.vm.$nextTick();
    expect(document.body.querySelector("[role='dialog'][aria-label='成员管理']")).toBeNull();
    review.unmount();
  });
});
