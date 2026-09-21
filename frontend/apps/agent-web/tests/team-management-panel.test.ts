import { defineComponent, h, inject, provide } from "vue";
import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, waitFor } from "@testing-library/vue";
import type { BackendApiClient } from "@test-agent/backend-api";
import type { CurrentUser, TeamUser } from "@test-agent/shared-types";
import TeamManagementPanel from "../src/components/system/TeamManagementPanel.vue";

const tableKey = Symbol("team-table");

const ElSelectStub = defineComponent({
  props: ["modelValue", "placeholder", "ariaLabel"],
  emits: ["update:modelValue", "change"],
  setup(props, { emit, slots }) {
    const update = (event: Event) => {
      const value = (event.target as HTMLSelectElement).value;
      emit("update:modelValue", value);
      emit("change", value);
    };
    return () => h("select", {
      value: props.modelValue,
      "aria-label": props.ariaLabel || props.placeholder,
      onChange: update
    }, slots.default?.());
  }
});

const ElOptionStub = defineComponent({
  props: ["label", "value"],
  setup(props) {
    return () => h("option", { value: props.value }, String(props.label));
  }
});

const ElTableStub = defineComponent({
  props: ["data"],
  setup(props, { slots }) {
    provide(tableKey, props);
    return () => h("div", slots.default?.());
  }
});

const ElTableColumnStub = defineComponent({
  props: ["prop", "label"],
  setup(props, { slots }) {
    const table = inject<{ data?: TeamUser[] }>(tableKey, {});
    return () => h("div", (table.data ?? []).flatMap((row) =>
      slots.default?.({ row }) ?? [h("span", String(
        props.prop ? row[props.prop as keyof TeamUser] ?? "" : props.label
      ))]));
  }
});

function page(items: TeamUser[]) {
  return { items, page: 1, size: 20, total: items.length };
}

function user(userId: string, username: string, roles: string[]): TeamUser {
  return {
    userId,
    username,
    unifiedAuthId: `${username}-auth`,
    organization: "研发中心",
    rdDepartment: "平台研发部",
    department: "测试组",
    status: "ACTIVE",
    roles,
    addedAt: "2026-09-21T08:00:00Z"
  };
}

function createApi(overrides: Partial<BackendApiClient> = {}): Partial<BackendApiClient> {
  const member = user("member-1", "成员甲", ["USER"]);
  const candidate = user("candidate-1", "候选人乙", ["APP_ADMIN"]);
  return {
    listSystemAdmins: vi.fn().mockResolvedValue(page([user("owner-1", "系统管理员甲", ["SYSTEM_ADMIN"])])),
    listSystemAdminTeamMembers: vi.fn().mockResolvedValue(page([member])),
    listSystemAdminTeamCandidates: vi.fn().mockResolvedValue(page([candidate])),
    addSystemAdminTeamMember: vi.fn().mockResolvedValue(undefined),
    removeSystemAdminTeamMember: vi.fn().mockResolvedValue(undefined),
    listTeamApplications: vi.fn().mockResolvedValue([]),
    listTeamWorkspaceTemplates: vi.fn().mockResolvedValue([]),
    listTeamWorkspaceVersions: vi.fn().mockResolvedValue([]),
    listTeamContributions: vi.fn().mockResolvedValue([]),
    ...overrides
  };
}

function renderPanel(api: Partial<BackendApiClient>, currentUser: CurrentUser) {
  return render(TeamManagementPanel, {
    props: { currentUser, pageActive: true },
    global: {
      directives: { loading: () => undefined },
      stubs: {
        ElSelect: ElSelectStub,
        ElOption: ElOptionStub,
        ElTable: ElTableStub,
        ElTableColumn: ElTableColumnStub,
        ElTabs: { template: "<div><slot /></div>" },
        ElTabPane: { template: "<section><slot /></section>" },
        ElInput: { props: ["modelValue", "placeholder"], template: "<input :placeholder=\"placeholder\" :value=\"modelValue\" />" },
        ElButton: { props: ["disabled"], emits: ["click"], template: "<button :disabled=\"disabled\" @click=\"$emit('click')\"><slot /></button>" },
        ElPagination: { template: "<div />" },
        ElProgress: { template: "<div />" },
        ElAlert: { props: ["title"], template: "<div>{{ title }}</div>" },
        ElTag: { template: "<span><slot /></span>" },
        ElEmpty: { template: "<div />" }
      },
      provide: { api: api as BackendApiClient }
    }
  });
}

describe("TeamManagementPanel", () => {
  it("loads MY_TEAM members and candidates and removes a member", async () => {
    const api = createApi();
    const currentUser = user("owner-1", "系统管理员甲", ["SYSTEM_ADMIN"]);
    const view = renderPanel(api, currentUser);

    await waitFor(() => expect(api.listSystemAdminTeamMembers).toHaveBeenCalledWith(
      { scopeMode: "MY_TEAM", ownerUserId: undefined }, "", 1, 20));
    expect(await view.findByText("候选人乙 · 候选人乙-auth")).toBeTruthy();

    await fireEvent.click(view.getByRole("button", { name: "移除" }));
    await waitFor(() => expect(api.removeSystemAdminTeamMember).toHaveBeenCalledWith(
      { scopeMode: "MY_TEAM", ownerUserId: undefined }, "member-1"));
  });

  it("starts a super admin in GLOBAL and switches to a selected system-admin team", async () => {
    const api = createApi();
    const view = renderPanel(api, user("root-1", "超级管理员", ["SUPER_ADMIN"]));

    await waitFor(() => expect(api.listTeamApplications).toHaveBeenCalledWith(
      { scopeMode: "GLOBAL", ownerUserId: undefined }));
    expect(api.listSystemAdminTeamMembers).not.toHaveBeenCalled();

    await fireEvent.update(view.getByRole("combobox", { name: "查看范围" }), "SYSTEM_ADMIN_TEAM");
    await fireEvent.update(view.getByRole("combobox", { name: "选择系统管理员" }), "owner-1");
    await waitFor(() => expect(api.listTeamApplications).toHaveBeenCalledWith(
      { scopeMode: "SYSTEM_ADMIN_TEAM", ownerUserId: "owner-1" }));
  });

  it("cascades application, workspace, version and member worktree details", async () => {
    const worktree = {
      personalWorkspaceId: "pw-1", workspaceId: "ws-1", workspaceName: "研发空间",
      branch: "feature/member", linuxServerId: "server-1", baseCommit: "abc123",
      status: "READY", updatedAt: "2026-09-21T08:00:00Z"
    };
    const api = createApi({
      listTeamApplications: vi.fn().mockResolvedValue([
        { appId: "app-1", appName: "应用一", enabled: true, currentMemberCount: 1, historicalMemberCount: 0 }
      ]),
      listTeamWorkspaceTemplates: vi.fn().mockResolvedValue([
        { workspaceId: "ws-1", appId: "app-1", workspaceName: "研发空间", branch: "release", directoryPath: "repo", enabled: true }
      ]),
      listTeamWorkspaceVersions: vi.fn().mockResolvedValue([
        { versionId: "ver-1", applicationWorkspaceId: "ws-1", appId: "app-1", version: "v1", branch: "release", status: "READY", updatedAt: "2026-09-21T08:00:00Z" }
      ]),
      listTeamContributions: vi.fn().mockResolvedValue([
        { ...user("member-1", "成员甲", ["USER"]), membershipState: "CURRENT", personalWorkspaces: [worktree] }
      ]),
      getTeamWorkspaceGitStatus: vi.fn().mockResolvedValue({ files: [], stagedCount: 0, unstagedCount: 0, untrackedCount: 0 }),
      listTeamWorkspaceCommits: vi.fn().mockResolvedValue({ items: [], offset: 0, limit: 100, hasMore: false, attributionConfirmed: true }),
      listTeamWorkspaceFiles: vi.fn().mockResolvedValue([])
    });
    const view = renderPanel(api, user("owner-1", "系统管理员甲", ["SYSTEM_ADMIN"]));

    await waitFor(() => expect(api.listTeamContributions).toHaveBeenCalledWith(
      { scopeMode: "MY_TEAM", ownerUserId: undefined }, "ver-1"));
    await waitFor(() => expect(api.getTeamWorkspaceGitStatus).toHaveBeenCalledWith(
      { scopeMode: "MY_TEAM", ownerUserId: undefined }, "pw-1"));
    expect(api.listTeamWorkspaceTemplates).toHaveBeenCalledWith(
      { scopeMode: "MY_TEAM", ownerUserId: undefined }, "app-1");
    expect(api.listTeamWorkspaceVersions).toHaveBeenCalledWith(
      { scopeMode: "MY_TEAM", ownerUserId: undefined }, "ws-1");
    expect(view.getByText("CURRENT")).toBeTruthy();
    expect(view.getByText("1 个 worktree")).toBeTruthy();
  });
});
