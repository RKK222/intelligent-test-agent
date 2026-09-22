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
    createTeamExport: vi.fn(),
    getTeamExport: vi.fn(),
    cancelTeamExport: vi.fn(),
    createTeamExportDownloadRoute: vi.fn()
  };
}

describe("team review panes", () => {
  it("explains why platform-wide scope cannot change members and keeps the file tree read-only", async () => {
    const controller = createTeamManagementController(api());
    await controller.enter(true);
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

  it("keeps a floating review launcher when the right panel is collapsed", async () => {
    const controller = createTeamManagementController(api());
    await controller.enter(true);
    const provide = { [teamManagementKey as symbol]: controller };
    const review = mount(TeamReviewPane, {
      props: { rightPanelOpen: false },
      global: { provide }
    });

    const launcher = document.body.querySelector<HTMLButtonElement>("[aria-label='打开团队审阅']");
    expect(launcher).not.toBeNull();
    launcher?.click();
    await review.vm.$nextTick();
    expect(document.body.querySelector("[role='dialog'][aria-label='团队审阅对话框']")).not.toBeNull();

    const close = document.body.querySelector<HTMLButtonElement>("[aria-label='关闭审阅对话框']");
    close?.click();
    await review.vm.$nextTick();
    expect(document.body.querySelector("[role='dialog'][aria-label='团队审阅对话框']")).toBeNull();
    expect(document.body.querySelector("[aria-label='打开团队审阅']")).not.toBeNull();
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
    const controller = createTeamManagementController(backend);
    await controller.enter(true);
    const provide = { [teamManagementKey as symbol]: controller };
    const review = mount(TeamReviewPane, { global: { provide } });

    await review.get("button").trigger("click");
    expect(document.body.querySelector("[role='dialog'][aria-label='选择系统管理员团队']")).not.toBeNull();
    const owner = document.body.querySelector<HTMLButtonElement>(".team-owner-option");
    owner?.click();
    await review.vm.$nextTick();
    await vi.waitFor(() => expect(document.body.querySelector("[role='dialog'][aria-label='成员管理']")).not.toBeNull());
    expect(backend.listSystemAdminTeamMembers).toHaveBeenCalled();
    review.unmount();
  });

  it("closes the team picker and member dialog with Escape", async () => {
    const backend = api();
    backend.listSystemAdmins.mockResolvedValue({
      items: [{ userId: "owner-1", username: "系统管理员甲", unifiedAuthId: "owner-auth", department: "质量部" }],
      total: 1,
      page: 1,
      size: 200
    });
    const controller = createTeamManagementController(backend);
    await controller.enter(true);
    const provide = { [teamManagementKey as symbol]: controller };
    const review = mount(TeamReviewPane, { global: { provide } });

    await review.get("button").trigger("click");
    const picker = document.body.querySelector<HTMLElement>("[role='dialog'][aria-label='选择系统管理员团队']");
    expect(picker).not.toBeNull();
    await picker!.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
    expect(document.body.querySelector("[role='dialog'][aria-label='选择系统管理员团队']")).toBeNull();

    await controller.selectMemberManagementOwner("owner-1");
    const members = document.body.querySelector<HTMLElement>("[role='dialog'][aria-label='成员管理']");
    expect(members).not.toBeNull();
    await members!.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
    expect(document.body.querySelector("[role='dialog'][aria-label='成员管理']")).toBeNull();
    review.unmount();
  });
});
