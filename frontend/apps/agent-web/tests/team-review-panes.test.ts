import { mount } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";
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
    expect(files.text()).not.toContain("新建");
    expect(files.text()).not.toContain("上传");
    expect(files.text()).not.toContain("删除");
    expect(files.text()).not.toContain("重命名");
    expect(files.find('[aria-label="搜索文件"]').exists()).toBe(true);

    review.unmount();
    files.unmount();
  });
});