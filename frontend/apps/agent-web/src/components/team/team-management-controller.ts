import type { InjectionKey } from "vue";
import { BackendApiError, type BackendApiClient } from "@test-agent/backend-api";
import type {
  FileSearchResult,
  FileTreeEntry,
  TeamApplication,
  TeamCommit,
  TeamContribution,
  TeamExport,
  TeamGitStatus,
  TeamScopeMode,
  TeamScopeParams,
  TeamUser,
  TeamWorkspaceTemplate,
  TeamWorkspaceVersion
} from "@test-agent/shared-types";
import { progressivePreviewRequired } from "../fileProgressivePreview";

export type WorkbenchPerspective = "WORK" | "TEAM_MANAGEMENT";

export type TeamContextOption = {
  id: string;
  label: string;
  detail?: string;
};

/** 进入管理视角时沿用工作视角的应用、工作空间和版本，不回写工作视角。 */
export type TeamReviewContextSeed = {
  appId?: string;
  templateId?: string;
  versionId?: string;
};

export type TeamReviewMember = {
  userId: string;
  username: string;
  unifiedAuthId: string;
  department: string;
};

const DEFAULT_PERSONAL_WORKSPACE_NAME = "default";
const TEAM_ROSTER_PAGE_SIZE = 200;

export type TeamContributionStats = {
  published: number;
  personal: number;
  sync: number;
  changes: number;
  /** 统计请求失败后的终态，避免成员行一直停在“统计加载中”。 */
  unavailable?: boolean;
};

export type TeamReviewTab = {
  id: string;
  kind: "file" | "diff";
  path: string;
  title: string;
  personalWorkspaceId: string;
  content: string;
  patch: string;
  status: string;
  additions: number;
  deletions: number;
  loadState: "loading" | "loaded" | "error";
  errorMessage: string;
  progressive?: {
    loadedBytes: number;
    size: number;
    nextOffset: number;
    warningThresholdBytes: number;
    eof: boolean;
    lastModifiedMillis: number;
    loading: boolean;
  };
};

export type TeamCommitFile = {
  status: string;
  oldPath?: string | null;
  path: string;
};

export type TeamManagementState = {
  active: boolean;
  scopeLocked: boolean;
  scopeMode: TeamScopeMode;
  ownerUserId: string;
  owners: TeamUser[];
  applications: TeamApplication[];
  templates: TeamWorkspaceTemplate[];
  versions: TeamWorkspaceVersion[];
  contributions: TeamContribution[];
  contributionStats: Record<string, TeamContributionStats>;
  reviewRoster: TeamReviewMember[];
  selectedAppId: string;
  selectedTemplateId: string;
  selectedVersionId: string;
  selectedUserId: string;
  selectedPersonalWorkspaceId: string;
  gitStatus: TeamGitStatus | null;
  personalCommits: TeamCommit[];
  publishedCommits: TeamCommit[];
  attributionMessage: string;
  selectedCommit: TeamCommit | null;
  selectedCommitKind: "PERSONAL" | "PUBLISHED" | null;
  commitFiles: TeamCommitFile[];
  entriesByDirectory: Record<string, FileTreeEntry[]>;
  expandedPaths: string[];
  searchQuery: string;
  searchResults: FileSearchResult[] | null;
  tabs: TeamReviewTab[];
  activeTabId: string;
  memberDrawerOpen: boolean;
  memberKeyword: string;
  members: TeamUser[];
  memberPage: number;
  memberPageSize: number;
  memberTotal: number;
  candidates: TeamUser[];
  candidateUserId: string;
  exportJob: TeamExport | null;
  errorMessage: string;
  catalogError: string;
  treeError: string;
  missingDefaultWorkspace: boolean;
  catalogLoading: boolean;
  detailLoading: boolean;
  memberLoading: boolean;
  mutationLoading: boolean;
};

export type TeamManagementController = {
  snapshot(): TeamManagementState;
  subscribe(listener: (state: TeamManagementState) => void): () => void;
  enter(superAdmin: boolean, seed?: TeamReviewContextSeed): Promise<void>;
  exit(): void;
  selectScope(mode: TeamScopeMode, ownerUserId?: string): Promise<void>;
  selectApplication(appId: string): Promise<void>;
  selectTemplate(workspaceId: string): Promise<void>;
  selectVersion(versionId: string): Promise<void>;
  selectMember(userId: string): Promise<void>;
  toggleDirectory(path: string): Promise<void>;
  setFileSearch(query: string): void;
  openEntry(path: string, directory: boolean): Promise<void>;
  openChange(path: string): void;
  selectCommit(commit: TeamCommit, kind: "PERSONAL" | "PUBLISHED"): Promise<void>;
  openCommitFile(path: string): Promise<void>;
  activateTab(tabId: string): void;
  closeTab(tabId: string): void;
  loadMorePreview(tabId: string, loadAll?: boolean): Promise<void>;
  openMemberDrawer(): Promise<void>;
  closeMemberDrawer(): void;
  chooseCandidate(userId: string): void;
  searchMembers(keyword: string): Promise<void>;
  searchCandidates(keyword: string): Promise<void>;
  setMemberPage(page: number): Promise<void>;
  addMember(): Promise<void>;
  removeMember(userId: string): Promise<void>;
  createExport(): Promise<void>;
  cancelExport(): Promise<void>;
  downloadExport(): Promise<string | null>;
  dismissError(): void;
  retryCatalog(): Promise<void>;
  reloadTree(): Promise<void>;
  retryTab(tabId: string): Promise<void>;
};

export const teamManagementKey: InjectionKey<TeamManagementController> = Symbol("teamManagement");

type TeamApi = Pick<
  BackendApiClient,
  | "listSystemAdmins"
  | "listSystemAdminTeamMembers"
  | "listSystemAdminTeamCandidates"
  | "addSystemAdminTeamMember"
  | "removeSystemAdminTeamMember"
  | "listTeamApplications"
  | "listTeamWorkspaceTemplates"
  | "listTeamWorkspaceVersions"
  | "listTeamContributions"
  | "getTeamWorkspaceGitStatus"
  | "listTeamWorkspaceCommits"
  | "getTeamWorkspaceCommitDetail"
  | "getTeamWorkspaceCommitDiff"
  | "listTeamWorkspaceFiles"
  | "searchTeamWorkspaceFiles"
  | "readTeamWorkspaceFile"
  | "readTeamWorkspaceFilePreviewChunk"
  | "closeTeamWorkspaceFileConnections"
  | "createTeamExport"
  | "getTeamExport"
  | "cancelTeamExport"
  | "createTeamExportDownloadRoute"
>;

function emptyState(): TeamManagementState {
  return {
    active: false,
    scopeLocked: true,
    scopeMode: "MY_TEAM",
    ownerUserId: "",
    owners: [],
    applications: [],
    templates: [],
    versions: [],
    contributions: [],
    contributionStats: {},
    reviewRoster: [],
    selectedAppId: "",
    selectedTemplateId: "",
    selectedVersionId: "",
    selectedUserId: "",
    selectedPersonalWorkspaceId: "",
    gitStatus: null,
    personalCommits: [],
    publishedCommits: [],
    attributionMessage: "",
    selectedCommit: null,
    selectedCommitKind: null,
    commitFiles: [],
    entriesByDirectory: {},
    expandedPaths: [],
    searchQuery: "",
    searchResults: null,
    tabs: [],
    activeTabId: "",
    memberDrawerOpen: false,
    memberKeyword: "",
    members: [],
    memberPage: 1,
    memberPageSize: 20,
    memberTotal: 0,
    candidates: [],
    candidateUserId: "",
    exportJob: null,
    errorMessage: "",
    catalogError: "",
    treeError: "",
    missingDefaultWorkspace: false,
    catalogLoading: false,
    detailLoading: false,
    memberLoading: false,
    mutationLoading: false
  };
}

function fileTitle(path: string) {
  const normalized = path.replace(/\\/g, "/");
  return normalized.split("/").filter(Boolean).pop() || path;
}

function accessRevoked(error: unknown) {
  return error instanceof BackendApiError && (error.status === 401 || error.status === 403);
}

/**
 * 管理视角的请求模型。范围、版本和成员各自带代次，迟到响应不能写回新的选择。
 * 这里不进入 SelectedWorkspaceKind，避免把他人的个人工作空间写成当前用户的运行工作区。
 */
export function createTeamManagementController(
  api: TeamApi,
  options: { searchDelayMs?: number; exportPollMs?: number } = {}
): TeamManagementController {
  const searchDelayMs = options.searchDelayMs ?? 200;
  const exportPollMs = options.exportPollMs ?? 1500;
  let state = emptyState();
  let scopeEpoch = 0;
  let catalogEpoch = 0;
  let detailEpoch = 0;
  let fileEpoch = 0;
  let statsEpoch = 0;
  let memberEpoch = 0;
  let searchTimer: ReturnType<typeof setTimeout> | undefined;
  let exportTimer: ReturnType<typeof setInterval> | undefined;
  const listeners = new Set<(snapshot: TeamManagementState) => void>();

  function snapshot(): TeamManagementState {
    return {
      ...state,
      owners: state.owners.slice(),
      applications: state.applications.slice(),
      templates: state.templates.slice(),
      versions: state.versions.slice(),
      contributions: state.contributions.slice(),
      contributionStats: { ...state.contributionStats },
      reviewRoster: state.reviewRoster.slice(),
      personalCommits: state.personalCommits.slice(),
      publishedCommits: state.publishedCommits.slice(),
      commitFiles: state.commitFiles.slice(),
      entriesByDirectory: { ...state.entriesByDirectory },
      expandedPaths: state.expandedPaths.slice(),
      searchResults: state.searchResults ? state.searchResults.slice() : null,
      tabs: state.tabs.map((tab) => ({ ...tab, progressive: tab.progressive ? { ...tab.progressive } : undefined })),
      members: state.members.slice(),
      candidates: state.candidates.slice(),
      exportJob: state.exportJob ? { ...state.exportJob } : null,
      gitStatus: state.gitStatus ? { ...state.gitStatus, files: state.gitStatus.files.slice() } : null,
      selectedCommit: state.selectedCommit ? { ...state.selectedCommit } : null
    };
  }

  function publish() {
    const next = snapshot();
    listeners.forEach((listener) => listener(next));
  }

  function scopeParams(): TeamScopeParams {
    return {
      scopeMode: state.scopeMode,
      ownerUserId: state.scopeMode === "SYSTEM_ADMIN_TEAM" ? state.ownerUserId : undefined
    };
  }

  /** 目录查询带上当前审阅成员；文件和 Git 请求仍只使用团队范围。 */
  function catalogScope(): TeamScopeParams {
    return {
      ...scopeParams(),
      targetUserId: state.selectedUserId || undefined
    };
  }

  function reviewMember(user: {
    userId: string;
    username: string;
    unifiedAuthId: string;
    department?: string | null;
  }): TeamReviewMember {
    return {
      userId: user.userId,
      username: user.username,
      unifiedAuthId: user.unifiedAuthId,
      department: user.department ?? ""
    };
  }

  function defaultPersonalWorkspace(item: TeamContribution | undefined) {
    return item?.personalWorkspaces.find((workspace) => workspace.workspaceName === DEFAULT_PERSONAL_WORKSPACE_NAME) ?? null;
  }

  function scopeKey() {
    const scope = scopeParams();
    return `${scope.scopeMode}:${scope.ownerUserId ?? ""}`;
  }

  function canMaintainMembers() {
    return state.scopeMode !== "GLOBAL"
      && (state.scopeMode !== "SYSTEM_ADMIN_TEAM" || Boolean(state.ownerUserId));
  }

  function selectedWorktree() {
    const contribution = state.contributions.find((item) => item.userId === state.selectedUserId);
    return contribution?.personalWorkspaces.find((item) => item.personalWorkspaceId === state.selectedPersonalWorkspaceId) ?? null;
  }

  function stopExportPolling() {
    if (exportTimer) clearInterval(exportTimer);
    exportTimer = undefined;
  }

  function forgetReviewCache() {
    state.entriesByDirectory = {};
    state.expandedPaths = [];
    state.searchQuery = "";
    state.searchResults = null;
    state.tabs = [];
    state.activeTabId = "";
    state.gitStatus = null;
    state.personalCommits = [];
    state.publishedCommits = [];
    state.attributionMessage = "";
    state.selectedCommit = null;
    state.selectedCommitKind = null;
    state.commitFiles = [];
  }

  function showError(error: unknown) {
    state.errorMessage = error instanceof Error ? error.message : String(error);
  }

  function revokeReading(error: unknown) {
    fileEpoch += 1;
    detailEpoch += 1;
    api.closeTeamWorkspaceFileConnections();
    forgetReviewCache();
    state.errorMessage = error instanceof Error ? error.message : "当前团队范围已失效";
    publish();
  }

  function currentCatalog(scopeTicket: number, catalogTicket: number) {
    return state.active && scopeTicket === scopeEpoch && catalogTicket === catalogEpoch;
  }

  function currentDetail(ticket: number, personalWorkspaceId: string) {
    return state.active
      && ticket === detailEpoch
      && state.selectedPersonalWorkspaceId === personalWorkspaceId;
  }

  function currentFile(ticket: number, personalWorkspaceId: string) {
    return state.active
      && ticket === fileEpoch
      && state.selectedPersonalWorkspaceId === personalWorkspaceId;
  }

  async function allCommits(personalWorkspaceId: string, kind: "PERSONAL" | "PUBLISHED", ticket: number) {
    const scope = scopeParams();
    const result: TeamCommit[] = [];
    let offset = 0;
    while (ticket === statsEpoch && state.active) {
      const page = await api.listTeamWorkspaceCommits(scope, personalWorkspaceId, kind, offset, 200);
      if (ticket !== statsEpoch || !state.active) return result;
      result.push(...page.items);
      if (!page.hasMore || page.items.length === 0) return result;
      offset += page.items.length;
    }
    return result;
  }

  async function loadContributionStats(items: TeamContribution[], ticket: number) {
    state.contributionStats = {};
    publish();
    let cursor = 0;
    const workers = Array.from({ length: Math.min(4, items.length) }, async () => {
      while (cursor < items.length && ticket === statsEpoch) {
        const item = items[cursor];
        cursor += 1;
        if (!item) return;
        const published = new Set<string>();
        const personal = new Set<string>();
        const sync = new Set<string>();
        let changes = 0;
        try {
          const workspace = defaultPersonalWorkspace(item);
          if (workspace && ticket === statsEpoch) {
            const [status, personalItems, publishedItems] = await Promise.all([
              api.getTeamWorkspaceGitStatus(scopeParams(), workspace.personalWorkspaceId),
              allCommits(workspace.personalWorkspaceId, "PERSONAL", ticket),
              allCommits(workspace.personalWorkspaceId, "PUBLISHED", ticket)
            ]);
            if (ticket !== statsEpoch) return;
            changes += status.files.length;
            for (const commit of publishedItems) {
              (commit.contributionType === "SYNC_MERGE" ? sync : published).add(commit.commit);
            }
            for (const commit of personalItems) {
              if (commit.contributionType === "SYNC_MERGE") sync.add(commit.commit);
              else personal.add(`${workspace.personalWorkspaceId}:${commit.commit}`);
            }
          }
          if (ticket === statsEpoch) {
            state.contributionStats = { ...state.contributionStats, [item.userId]: { published: published.size, personal: personal.size, sync: sync.size, changes } };
            publish();
          }
        } catch {
          // 统计失败写入终态，成员列表仍可切换；详情区单独展示错误和重试。
          if (ticket !== statsEpoch) return;
          state.contributionStats = {
            ...state.contributionStats,
            [item.userId]: { published: 0, personal: 0, sync: 0, changes: 0, unavailable: true }
          };
          publish();
        }
      }
    });
    await Promise.all(workers);
  }

  async function loadDirectory(path: string, personalWorkspaceId: string, ticket: number) {
    const worktree = selectedWorktree();
    if (!worktree || worktree.personalWorkspaceId !== personalWorkspaceId) return;
    const entries = await api.listTeamWorkspaceFiles(scopeParams(), personalWorkspaceId, worktree.workspaceId, path);
    if (!currentFile(ticket, personalWorkspaceId)) return;
    state.entriesByDirectory = { ...state.entriesByDirectory, [path]: entries };
    publish();
  }

  async function loadWorktreeDetail(personalWorkspaceId: string, ticket: number, fileTicket: number) {
    if (!personalWorkspaceId) {
      forgetReviewCache();
      state.detailLoading = false;
      publish();
      return;
    }
    state.detailLoading = true;
    publish();
    try {
      const scope = scopeParams();
      const [status, personal, published] = await Promise.all([
        api.getTeamWorkspaceGitStatus(scope, personalWorkspaceId),
        api.listTeamWorkspaceCommits(scope, personalWorkspaceId, "PERSONAL", 0, 100),
        api.listTeamWorkspaceCommits(scope, personalWorkspaceId, "PUBLISHED", 0, 100)
      ]);
      if (!currentDetail(ticket, personalWorkspaceId)) return;
      state.gitStatus = status;
      state.personalCommits = personal.items;
      state.publishedCommits = published.items;
      state.attributionMessage = published.attributionConfirmed ? "" : (published.attributionMessage ?? "无法归属");
      state.detailLoading = false;
      publish();
      try {
        await loadDirectory("", personalWorkspaceId, fileTicket);
        if (!currentFile(fileTicket, personalWorkspaceId)) return;
        state.treeError = "";
        publish();
      } catch (treeFailure) {
        if (!currentFile(fileTicket, personalWorkspaceId)) return;
        if (accessRevoked(treeFailure)) {
          revokeReading(treeFailure);
          return;
        }
        state.treeError = treeFailure instanceof Error ? treeFailure.message : String(treeFailure);
        publish();
      }
    } catch (error) {
      if (!currentDetail(ticket, personalWorkspaceId)) return;
      if (accessRevoked(error)) {
        revokeReading(error);
        return;
      }
      state.detailLoading = false;
      state.treeError = error instanceof Error ? error.message : String(error);
      showError(error);
      publish();
    }
  }

  function adoptDefaultWorkspace() {
    const personal = defaultPersonalWorkspace(
      state.contributions.find((item) => item.userId === state.selectedUserId)
    );
    state.missingDefaultWorkspace = Boolean(state.selectedVersionId && state.selectedUserId && !personal);
    state.selectedPersonalWorkspaceId = personal?.personalWorkspaceId ?? "";
  }

  async function loadContributions(scopeTicket: number, catalogTicket: number) {
    statsEpoch += 1;
    const statsTicket = statsEpoch;
    detailEpoch += 1;
    fileEpoch += 1;
    const detailTicket = detailEpoch;
    const fileTicket = fileEpoch;
    api.closeTeamWorkspaceFileConnections();
    forgetReviewCache();
    const versionId = state.selectedVersionId;
    if (!versionId) {
      state.contributions = [];
      state.missingDefaultWorkspace = Boolean(state.selectedUserId);
      state.selectedPersonalWorkspaceId = "";
      publish();
      return;
    }
    const contributions = await api.listTeamContributions(scopeParams(), versionId);
    if (!currentCatalog(scopeTicket, catalogTicket) || state.selectedVersionId !== versionId) return;
    state.contributions = contributions;
    const rosterWasEmpty = state.reviewRoster.length === 0;
    // 全平台没有团队名单，右栏跟随当前版本的可审阅成员；已选成员不因版本变化改成第一人。
    if (state.scopeMode === "GLOBAL") {
      const nextRoster = contributions.map(reviewMember);
      if (state.selectedUserId && !nextRoster.some((item) => item.userId === state.selectedUserId)) {
        const kept = state.reviewRoster.find((item) => item.userId === state.selectedUserId);
        if (kept) nextRoster.unshift(kept);
      }
      state.reviewRoster = nextRoster;
      if (!state.selectedUserId) state.selectedUserId = nextRoster[0]?.userId ?? "";
    }
    adoptDefaultWorkspace();
    publish();
    // 全平台第一次还没有成员，目录是团队级结果；选定成员后收窄到该成员并保留已有应用。
    if (rosterWasEmpty && state.selectedUserId) {
      await loadApplications(scopeTicket, catalogTicket);
      return;
    }
    void loadContributionStats(contributions, statsTicket);
    await loadWorktreeDetail(state.selectedPersonalWorkspaceId, detailTicket, fileTicket);
  }

  async function loadVersions(scopeTicket: number, catalogTicket: number) {
    const templateId = state.selectedTemplateId;
    const versions = templateId
      ? await api.listTeamWorkspaceVersions(catalogScope(), templateId)
      : [];
    if (!currentCatalog(scopeTicket, catalogTicket) || state.selectedTemplateId !== templateId) return;
    state.versions = versions;
    state.selectedVersionId = versions.some((item) => item.versionId === state.selectedVersionId)
      ? state.selectedVersionId
      : (versions[0]?.versionId ?? "");
    state.exportJob = null;
    stopExportPolling();
    publish();
    await loadContributions(scopeTicket, catalogTicket);
  }

  async function loadTemplates(scopeTicket: number, catalogTicket: number) {
    const appId = state.selectedAppId;
    const templates = appId ? await api.listTeamWorkspaceTemplates(catalogScope(), appId) : [];
    if (!currentCatalog(scopeTicket, catalogTicket) || state.selectedAppId !== appId) return;
    state.templates = templates;
    // 顶部菜单沿用工作视角，不展示已停用的工作空间；默认选中也必须落在仍可打开的项上。
    const selectable = templates.filter((item) => item.enabled !== false);
    state.selectedTemplateId = selectable.some((item) => item.workspaceId === state.selectedTemplateId)
      ? state.selectedTemplateId
      : (selectable[0]?.workspaceId ?? "");
    publish();
    await loadVersions(scopeTicket, catalogTicket);
  }

  async function loadApplications(scopeTicket: number, catalogTicket: number) {
    if (state.scopeMode === "SYSTEM_ADMIN_TEAM" && !state.ownerUserId) {
      state.applications = [];
      state.templates = [];
      state.versions = [];
      state.contributions = [];
      state.catalogLoading = false;
      state.catalogError = "";
      publish();
      return;
    }
    state.catalogLoading = true;
    state.catalogError = "";
    publish();
    try {
      const capturedScope = scopeKey();
      const capturedUserId = state.selectedUserId;
      const applications = await api.listTeamApplications(catalogScope());
      if (!currentCatalog(scopeTicket, catalogTicket) || scopeKey() !== capturedScope || state.selectedUserId !== capturedUserId) return;
      state.applications = applications;
      state.selectedAppId = applications.some((item) => item.appId === state.selectedAppId)
        ? state.selectedAppId
        : (applications[0]?.appId ?? "");
      state.catalogLoading = false;
      publish();
      await loadTemplates(scopeTicket, catalogTicket);
    } catch (error) {
      if (!currentCatalog(scopeTicket, catalogTicket)) return;
      if (accessRevoked(error)) {
        revokeReading(error);
        return;
      }
      state.catalogLoading = false;
      state.catalogError = error instanceof Error ? error.message : String(error);
      publish();
    }
  }

  async function loadReviewRoster(scopeTicket: number) {
    if (state.scopeMode === "GLOBAL") return;
    try {
      const page = await api.listSystemAdminTeamMembers(scopeParams(), "", 1, TEAM_ROSTER_PAGE_SIZE);
      if (!state.active || scopeTicket !== scopeEpoch) return;
      state.reviewRoster = page.items.map(reviewMember);
      if (!state.reviewRoster.some((item) => item.userId === state.selectedUserId)) {
        state.selectedUserId = state.reviewRoster[0]?.userId ?? "";
      }
      publish();
    } catch (error) {
      if (!state.active || scopeTicket !== scopeEpoch) return;
      if (accessRevoked(error)) {
        revokeReading(error);
        return;
      }
      state.catalogError = error instanceof Error ? error.message : String(error);
      publish();
    }
  }

  async function loadOwners(scopeTicket: number) {
    try {
      const owners = (await api.listSystemAdmins("", 1, 200)).items;
      if (!state.active || scopeTicket !== scopeEpoch) return;
      state.owners = owners;
      publish();
    } catch (error) {
      if (!state.active || scopeTicket !== scopeEpoch) return;
      showError(error);
      publish();
    }
  }

  async function loadMembers(ticket: number, capturedScope: string) {
    if (!canMaintainMembers()) {
      state.members = [];
      state.memberTotal = 0;
      state.candidates = [];
      state.memberLoading = false;
      publish();
      return;
    }
    state.memberLoading = true;
    publish();
    try {
      const [memberResult, candidatePage] = await Promise.all([
        api.listSystemAdminTeamMembers(scopeParams(), state.memberKeyword, state.memberPage, state.memberPageSize),
        api.listSystemAdminTeamCandidates(scopeParams(), "", 1, 50)
      ]);
      if (ticket !== memberEpoch || scopeKey() !== capturedScope || !state.active) return;
      state.members = memberResult.items;
      state.memberTotal = memberResult.total;
      state.candidates = candidatePage.items;
      if (!state.candidates.some((item) => item.userId === state.candidateUserId)) state.candidateUserId = "";
      state.memberLoading = false;
      publish();
    } catch (error) {
      if (ticket !== memberEpoch || !state.active) return;
      state.memberLoading = false;
      showError(error);
      publish();
    }
  }

  function bumpReview(closeAll: boolean, personalWorkspaceId?: string) {
    detailEpoch += 1;
    fileEpoch += 1;
    statsEpoch += 1;
    if (searchTimer) clearTimeout(searchTimer);
    if (closeAll) api.closeTeamWorkspaceFileConnections();
    else if (personalWorkspaceId) api.closeTeamWorkspaceFileConnections(personalWorkspaceId);
    forgetReviewCache();
  }

  function upsertTab(tab: TeamReviewTab) {
    const index = state.tabs.findIndex((item) => item.id === tab.id);
    state.tabs = index >= 0
      ? state.tabs.map((item) => item.id === tab.id ? tab : item)
      : [...state.tabs, tab];
    state.activeTabId = tab.id;
  }

  async function readFileTab(path: string, personalWorkspaceId: string, ticket: number) {
    const worktree = selectedWorktree();
    if (!worktree) return;
    const tabId = `file:${personalWorkspaceId}:${path}`;
    upsertTab({
      id: tabId,
      kind: "file",
      path,
      title: fileTitle(path),
      personalWorkspaceId,
      content: "",
      patch: "",
      status: "",
      additions: 0,
      deletions: 0,
      loadState: "loading",
      errorMessage: ""
    });
    publish();
    try {
      const content = await api.readTeamWorkspaceFile(scopeParams(), personalWorkspaceId, worktree.workspaceId, path);
      if (!currentFile(ticket, personalWorkspaceId)) return;
      upsertTab({
        id: tabId,
        kind: "file",
        path,
        title: fileTitle(path),
        personalWorkspaceId,
        content: content.content,
        patch: "",
        status: "",
        additions: 0,
        deletions: 0,
        loadState: "loaded",
        errorMessage: ""
      });
      publish();
    } catch (error) {
      if (!currentFile(ticket, personalWorkspaceId)) return;
      if (accessRevoked(error)) {
        revokeReading(error);
        return;
      }
      const preview = progressivePreviewRequired(error);
      if (!preview) {
        upsertTab({
          id: tabId,
          kind: "file",
          path,
          title: fileTitle(path),
          personalWorkspaceId,
          content: "",
          patch: "",
          status: "",
          additions: 0,
          deletions: 0,
          loadState: "error",
          errorMessage: error instanceof Error ? error.message : String(error)
        });
        publish();
        return;
      }
      try {
        const chunk = await api.readTeamWorkspaceFilePreviewChunk(
          scopeParams(), personalWorkspaceId, worktree.workspaceId, path, { offset: 0 }
        );
        if (!currentFile(ticket, personalWorkspaceId)) return;
        upsertTab({
          id: tabId,
          kind: "file",
          path,
          title: fileTitle(path),
          personalWorkspaceId,
          content: chunk.content,
          patch: "",
          status: "",
          additions: 0,
          deletions: 0,
          loadState: "loaded",
          errorMessage: "",
          progressive: {
            loadedBytes: chunk.nextOffset,
            size: chunk.size || preview.size,
            nextOffset: chunk.nextOffset,
            warningThresholdBytes: chunk.warningThresholdBytes || preview.warningThresholdBytes,
            eof: chunk.eof,
            lastModifiedMillis: chunk.lastModifiedMillis,
            loading: false
          }
        });
        publish();
      } catch (chunkError) {
        if (!currentFile(ticket, personalWorkspaceId)) return;
        if (accessRevoked(chunkError)) {
          revokeReading(chunkError);
          return;
        }
        upsertTab({
          id: tabId,
          kind: "file",
          path,
          title: fileTitle(path),
          personalWorkspaceId,
          content: "",
          patch: "",
          status: "",
          additions: 0,
          deletions: 0,
          loadState: "error",
          errorMessage: chunkError instanceof Error ? chunkError.message : String(chunkError)
        });
        publish();
      }
    }
  }

  function startExportPolling(exportId: string) {
    stopExportPolling();
    exportTimer = setInterval(() => {
      void (async () => {
        if (!state.active || state.exportJob?.exportId !== exportId) return;
        try {
          const job = await api.getTeamExport(exportId);
          if (!state.active || state.exportJob?.exportId !== exportId) return;
          state.exportJob = job;
          publish();
          if (!["QUEUED", "RUNNING"].includes(job.status)) stopExportPolling();
        } catch (error) {
          if (!state.active || state.exportJob?.exportId !== exportId) return;
          showError(error);
          publish();
        }
      })();
    }, exportPollMs);
  }

  return {
    snapshot,
    subscribe(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    async enter(superAdmin: boolean, seed: TeamReviewContextSeed = {}) {
      stopExportPolling();
      if (searchTimer) clearTimeout(searchTimer);
      scopeEpoch += 1;
      catalogEpoch += 1;
      detailEpoch += 1;
      fileEpoch += 1;
      statsEpoch += 1;
      memberEpoch += 1;
      api.closeTeamWorkspaceFileConnections();
      state = emptyState();
      state.active = true;
      state.scopeLocked = !superAdmin;
      state.scopeMode = superAdmin ? "GLOBAL" : "MY_TEAM";
      state.selectedAppId = seed.appId ?? "";
      state.selectedTemplateId = seed.templateId ?? "";
      state.selectedVersionId = seed.versionId ?? "";
      publish();
      const scopeTicket = scopeEpoch;
      const catalogTicket = catalogEpoch;
      if (superAdmin) void loadOwners(scopeTicket);
      if (!superAdmin) await loadReviewRoster(scopeTicket);
      await loadApplications(scopeTicket, catalogTicket);
    },
    exit() {
      stopExportPolling();
      if (searchTimer) clearTimeout(searchTimer);
      scopeEpoch += 1;
      catalogEpoch += 1;
      detailEpoch += 1;
      fileEpoch += 1;
      statsEpoch += 1;
      memberEpoch += 1;
      api.closeTeamWorkspaceFileConnections();
      state = emptyState();
      publish();
    },
    async selectScope(mode: TeamScopeMode, ownerUserId = "") {
      if (!state.active || (state.scopeLocked && mode !== "MY_TEAM")) return;
      const nextOwner = mode === "SYSTEM_ADMIN_TEAM" ? ownerUserId : "";
      if (state.scopeMode === mode && state.ownerUserId === nextOwner) return;
      stopExportPolling();
      scopeEpoch += 1;
      catalogEpoch += 1;
      memberEpoch += 1;
      bumpReview(true);
      state.scopeMode = mode;
      state.ownerUserId = nextOwner;
      state.applications = [];
      state.templates = [];
      state.versions = [];
      state.contributions = [];
      state.contributionStats = {};
      state.selectedPersonalWorkspaceId = "";
      state.missingDefaultWorkspace = false;
      state.exportJob = null;
      state.members = [];
      state.memberTotal = 0;
      state.catalogError = "";
      if (mode !== "GLOBAL") state.reviewRoster = [];
      publish();
      if (state.memberDrawerOpen) void loadMembers(memberEpoch, scopeKey());
      if (mode !== "GLOBAL") await loadReviewRoster(scopeEpoch);
      await loadApplications(scopeEpoch, catalogEpoch);
    },
    async selectApplication(appId: string) {
      if (!state.active || state.selectedAppId === appId) return;
      catalogEpoch += 1;
      bumpReview(true);
      state.selectedAppId = appId;
      state.templates = [];
      state.versions = [];
      state.contributions = [];
      state.selectedTemplateId = "";
      state.selectedVersionId = "";
      state.selectedPersonalWorkspaceId = "";
      state.missingDefaultWorkspace = false;
      state.exportJob = null;
      state.catalogError = "";
      stopExportPolling();
      publish();
      try {
        await loadTemplates(scopeEpoch, catalogEpoch);
      } catch (error) {
        if (!state.active) return;
        if (accessRevoked(error)) revokeReading(error);
        else {
          state.catalogLoading = false;
          state.catalogError = error instanceof Error ? error.message : String(error);
          publish();
        }
      }
    },
    async selectTemplate(workspaceId: string) {
      if (!state.active || state.selectedTemplateId === workspaceId) return;
      catalogEpoch += 1;
      bumpReview(true);
      state.selectedTemplateId = workspaceId;
      state.versions = [];
      state.contributions = [];
      state.selectedVersionId = "";
      state.selectedPersonalWorkspaceId = "";
      state.missingDefaultWorkspace = false;
      state.exportJob = null;
      state.catalogError = "";
      stopExportPolling();
      publish();
      try {
        await loadVersions(scopeEpoch, catalogEpoch);
      } catch (error) {
        if (!state.active) return;
        if (accessRevoked(error)) revokeReading(error);
        else {
          state.catalogLoading = false;
          state.catalogError = error instanceof Error ? error.message : String(error);
          publish();
        }
      }
    },
    async selectVersion(versionId: string) {
      if (!state.active || state.selectedVersionId === versionId) return;
      catalogEpoch += 1;
      bumpReview(true);
      state.selectedVersionId = versionId;
      state.contributions = [];
      state.selectedPersonalWorkspaceId = "";
      state.missingDefaultWorkspace = false;
      state.exportJob = null;
      state.catalogError = "";
      stopExportPolling();
      publish();
      try {
        await loadContributions(scopeEpoch, catalogEpoch);
      } catch (error) {
        if (!state.active) return;
        if (accessRevoked(error)) revokeReading(error);
        else {
          state.catalogError = error instanceof Error ? error.message : String(error);
          publish();
        }
      }
    },
    async selectMember(userId: string) {
      if (!state.active || state.selectedUserId === userId) return;
      const previous = state.selectedPersonalWorkspaceId;
      catalogEpoch += 1;
      bumpReview(false, previous);
      state.selectedUserId = userId;
      state.selectedPersonalWorkspaceId = "";
      state.missingDefaultWorkspace = false;
      state.catalogError = "";
      publish();
      await loadApplications(scopeEpoch, catalogEpoch);
    },
    async toggleDirectory(path: string) {
      if (!state.active || !state.selectedPersonalWorkspaceId) return;
      if (state.expandedPaths.includes(path)) {
        state.expandedPaths = state.expandedPaths.filter((item) => item !== path);
        publish();
        return;
      }
      state.expandedPaths = [...state.expandedPaths, path];
      publish();
      if (!state.entriesByDirectory[path]) {
        try {
          await loadDirectory(path, state.selectedPersonalWorkspaceId, fileEpoch);
        } catch (error) {
          if (!currentFile(fileEpoch, state.selectedPersonalWorkspaceId)) return;
          if (accessRevoked(error)) revokeReading(error);
          else {
            state.treeError = error instanceof Error ? error.message : String(error);
            publish();
          }
        }
      }
    },
    setFileSearch(query: string) {
      state.searchQuery = query;
      if (searchTimer) clearTimeout(searchTimer);
      const ticket = fileEpoch;
      const personalWorkspaceId = state.selectedPersonalWorkspaceId;
      const worktree = selectedWorktree();
      if (!query.trim() || !worktree) {
        state.searchResults = null;
        publish();
        return;
      }
      publish();
      searchTimer = setTimeout(() => {
        void (async () => {
          try {
            const results = await api.searchTeamWorkspaceFiles(
              scopeParams(), personalWorkspaceId, worktree.workspaceId, query.trim()
            );
            if (!currentFile(ticket, personalWorkspaceId) || state.searchQuery !== query) return;
            state.searchResults = results;
            publish();
          } catch (error) {
            if (!currentFile(ticket, personalWorkspaceId)) return;
            if (accessRevoked(error)) revokeReading(error);
            else {
              showError(error);
              publish();
            }
          }
        })();
      }, searchDelayMs);
    },
    async openEntry(path: string, directory: boolean) {
      if (!state.selectedPersonalWorkspaceId) return;
      if (directory) {
        if (state.expandedPaths.includes(path)) {
          state.expandedPaths = state.expandedPaths.filter((item) => item !== path);
          publish();
          return;
        }
        state.expandedPaths = [...state.expandedPaths, path];
        publish();
        if (!state.entriesByDirectory[path]) {
          try {
            await loadDirectory(path, state.selectedPersonalWorkspaceId, fileEpoch);
          } catch (error) {
            if (!currentFile(fileEpoch, state.selectedPersonalWorkspaceId)) return;
            if (accessRevoked(error)) revokeReading(error);
            else {
              state.treeError = error instanceof Error ? error.message : String(error);
              publish();
            }
          }
        }
        return;
      }
      await readFileTab(path, state.selectedPersonalWorkspaceId, fileEpoch);
    },
    openChange(path: string) {
      const file = state.gitStatus?.files.find((item) => item.path === path);
      const personalWorkspaceId = state.selectedPersonalWorkspaceId;
      if (!file || !personalWorkspaceId) return;
      upsertTab({
        id: `diff:${personalWorkspaceId}:change:${path}`,
        kind: "diff",
        path,
        title: fileTitle(path),
        personalWorkspaceId,
        content: "",
        patch: file.patch,
        status: file.status || file.rawStatus,
        additions: file.additions,
        deletions: file.deletions,
        loadState: "loaded",
        errorMessage: ""
      });
      publish();
    },
    async selectCommit(commit: TeamCommit, kind: "PERSONAL" | "PUBLISHED") {
      const personalWorkspaceId = state.selectedPersonalWorkspaceId;
      if (!personalWorkspaceId) return;
      const ticket = detailEpoch;
      state.selectedCommit = commit;
      state.selectedCommitKind = kind;
      state.commitFiles = [];
      publish();
      try {
        const detail = await api.getTeamWorkspaceCommitDetail(scopeParams(), personalWorkspaceId, commit.commit, kind);
        if (!currentDetail(ticket, personalWorkspaceId) || state.selectedCommit?.commit !== commit.commit) return;
        state.selectedCommit = detail.commit;
        state.commitFiles = detail.files;
        publish();
      } catch (error) {
        if (!currentDetail(ticket, personalWorkspaceId)) return;
        if (accessRevoked(error)) revokeReading(error);
        else {
          showError(error);
          publish();
        }
      }
    },
    async openCommitFile(path: string) {
      const personalWorkspaceId = state.selectedPersonalWorkspaceId;
      const commit = state.selectedCommit;
      const kind = state.selectedCommitKind;
      if (!personalWorkspaceId || !commit || !kind) return;
      const ticket = fileEpoch;
      const tabId = `diff:${personalWorkspaceId}:${kind}:${commit.commit}:${path}`;
      upsertTab({
        id: tabId,
        kind: "diff",
        path,
        title: fileTitle(path),
        personalWorkspaceId,
        content: "",
        patch: "",
        status: state.commitFiles.find((item) => item.path === path)?.status ?? "",
        additions: 0,
        deletions: 0,
        loadState: "loading",
        errorMessage: ""
      });
      publish();
      try {
        const result = await api.getTeamWorkspaceCommitDiff(scopeParams(), personalWorkspaceId, commit.commit, path, kind);
        if (!currentFile(ticket, personalWorkspaceId) || state.selectedCommit?.commit !== commit.commit) return;
        upsertTab({
          id: tabId,
          kind: "diff",
          path,
          title: fileTitle(path),
          personalWorkspaceId,
          content: "",
          patch: result.patch,
          status: state.commitFiles.find((item) => item.path === path)?.status ?? "",
          additions: 0,
          deletions: 0,
          loadState: "loaded",
          errorMessage: ""
        });
        publish();
      } catch (error) {
        if (!currentFile(ticket, personalWorkspaceId)) return;
        if (accessRevoked(error)) revokeReading(error);
        else {
          upsertTab({
            id: tabId,
            kind: "diff",
            path,
            title: fileTitle(path),
            personalWorkspaceId,
            content: "",
            patch: "",
            status: state.commitFiles.find((item) => item.path === path)?.status ?? "",
            additions: 0,
            deletions: 0,
            loadState: "error",
            errorMessage: error instanceof Error ? error.message : String(error)
          });
          publish();
        }
      }
    },
    activateTab(tabId: string) {
      if (state.tabs.some((tab) => tab.id === tabId)) {
        state.activeTabId = tabId;
        publish();
      }
    },
    closeTab(tabId: string) {
      const index = state.tabs.findIndex((tab) => tab.id === tabId);
      if (index < 0) return;
      state.tabs = state.tabs.filter((tab) => tab.id !== tabId);
      if (state.activeTabId === tabId) {
        state.activeTabId = state.tabs[Math.min(index, state.tabs.length - 1)]?.id ?? "";
      }
      publish();
    },
    async loadMorePreview(tabId: string, loadAll = false) {
      const personalWorkspaceId = state.selectedPersonalWorkspaceId;
      const worktree = selectedWorktree();
      const initial = state.tabs.find((tab) => tab.id === tabId);
      if (!initial?.progressive || initial.progressive.eof || !worktree || !personalWorkspaceId) return;
      const ticket = fileEpoch;
      const markLoading = (loading: boolean) => {
        const current = state.tabs.find((tab) => tab.id === tabId);
        if (!current?.progressive) return;
        upsertTab({ ...current, progressive: { ...current.progressive, loading } });
        publish();
      };
      markLoading(true);
      try {
        while (currentFile(ticket, personalWorkspaceId)) {
          const current = state.tabs.find((tab) => tab.id === tabId);
          if (!current?.progressive || current.progressive.eof) return;
          const chunk = await api.readTeamWorkspaceFilePreviewChunk(
            scopeParams(),
            personalWorkspaceId,
            worktree.workspaceId,
            current.path,
            {
              offset: current.progressive.nextOffset,
              expectedSize: current.progressive.size,
              expectedLastModifiedMillis: current.progressive.lastModifiedMillis
            }
          );
          if (!currentFile(ticket, personalWorkspaceId)) return;
          const latest = state.tabs.find((tab) => tab.id === tabId);
          if (!latest?.progressive) return;
          upsertTab({
            ...latest,
            content: latest.content + chunk.content,
            loadState: "loaded",
            progressive: {
              loadedBytes: chunk.nextOffset,
              size: chunk.size,
              nextOffset: chunk.nextOffset,
              warningThresholdBytes: chunk.warningThresholdBytes,
              eof: chunk.eof,
              lastModifiedMillis: chunk.lastModifiedMillis,
              loading: false
            }
          });
          publish();
          if (!loadAll || chunk.eof) return;
          await new Promise((resolve) => setTimeout(resolve, 0));
        }
      } catch (error) {
        if (!currentFile(ticket, personalWorkspaceId)) return;
        if (accessRevoked(error)) revokeReading(error);
        else {
          const current = state.tabs.find((tab) => tab.id === tabId);
          if (current) {
            upsertTab({
              ...current,
              errorMessage: error instanceof Error ? error.message : String(error),
              progressive: current.progressive ? { ...current.progressive, loading: false } : undefined
            });
          }
          publish();
        }
      }
    },
    async openMemberDrawer() {
      state.memberDrawerOpen = true;
      publish();
      if (canMaintainMembers()) await loadMembers(memberEpoch, scopeKey());
    },
    closeMemberDrawer() {
      state.memberDrawerOpen = false;
      publish();
    },
    chooseCandidate(userId: string) {
      state.candidateUserId = userId;
      publish();
    },
    async searchMembers(keyword: string) {
      memberEpoch += 1;
      state.memberKeyword = keyword;
      state.memberPage = 1;
      await loadMembers(memberEpoch, scopeKey());
    },
    async searchCandidates(keyword: string) {
      if (!canMaintainMembers()) return;
      const ticket = memberEpoch;
      const capturedScope = scopeKey();
      state.memberLoading = true;
      publish();
      try {
        const page = await api.listSystemAdminTeamCandidates(scopeParams(), keyword, 1, 50);
        if (ticket !== memberEpoch || scopeKey() !== capturedScope) return;
        state.candidates = page.items;
        state.memberLoading = false;
        publish();
      } catch (error) {
        if (ticket !== memberEpoch) return;
        state.memberLoading = false;
        showError(error);
        publish();
      }
    },
    async setMemberPage(page: number) {
      memberEpoch += 1;
      state.memberPage = page;
      await loadMembers(memberEpoch, scopeKey());
    },
    async addMember() {
      if (!state.candidateUserId || !canMaintainMembers()) return;
      state.mutationLoading = true;
      publish();
      try {
        await api.addSystemAdminTeamMember(scopeParams(), state.candidateUserId);
        state.candidateUserId = "";
        state.mutationLoading = false;
        memberEpoch += 1;
        await loadMembers(memberEpoch, scopeKey());
        await loadReviewRoster(scopeEpoch);
        catalogEpoch += 1;
        await loadApplications(scopeEpoch, catalogEpoch);
      } catch (error) {
        state.mutationLoading = false;
        showError(error);
        publish();
      }
    },
    async removeMember(userId: string) {
      if (!canMaintainMembers()) return;
      state.mutationLoading = true;
      publish();
      try {
        await api.removeSystemAdminTeamMember(scopeParams(), userId);
        api.closeTeamWorkspaceFileConnections();
        if (state.selectedUserId === userId) {
          bumpReview(true);
          state.selectedUserId = "";
          state.selectedPersonalWorkspaceId = "";
        }
        state.reviewRoster = state.reviewRoster.filter((item) => item.userId !== userId);
        state.mutationLoading = false;
        memberEpoch += 1;
        await loadMembers(memberEpoch, scopeKey());
        await loadReviewRoster(scopeEpoch);
        catalogEpoch += 1;
        await loadApplications(scopeEpoch, catalogEpoch);
      } catch (error) {
        state.mutationLoading = false;
        if (accessRevoked(error)) revokeReading(error);
        else {
          showError(error);
          publish();
        }
      }
    },
    async createExport() {
      if (!state.selectedVersionId) return;
      try {
        state.exportJob = await api.createTeamExport(scopeParams(), state.selectedVersionId);
        publish();
        startExportPolling(state.exportJob.exportId);
      } catch (error) {
        showError(error);
        publish();
      }
    },
    async cancelExport() {
      if (!state.exportJob) return;
      const exportId = state.exportJob.exportId;
      await api.cancelTeamExport(exportId);
      if (state.exportJob?.exportId !== exportId) return;
      state.exportJob = await api.getTeamExport(exportId);
      publish();
      if (!["QUEUED", "RUNNING"].includes(state.exportJob.status)) stopExportPolling();
    },
    async downloadExport() {
      if (!state.exportJob) return null;
      const route = await api.createTeamExportDownloadRoute(state.exportJob.exportId);
      return `${route.baseUrl.replace(/\/$/, "")}${route.downloadPath}`;
    },
    dismissError() {
      state.errorMessage = "";
      publish();
    },
    async retryCatalog() {
      if (!state.active) return;
      catalogEpoch += 1;
      state.catalogError = "";
      await loadApplications(scopeEpoch, catalogEpoch);
    },
    async reloadTree() {
      const personalWorkspaceId = state.selectedPersonalWorkspaceId;
      if (!state.active || !personalWorkspaceId) return;
      state.treeError = "";
      publish();
      try {
        await loadDirectory("", personalWorkspaceId, fileEpoch);
      } catch (error) {
        if (!currentFile(fileEpoch, personalWorkspaceId)) return;
        if (accessRevoked(error)) revokeReading(error);
        else {
          state.treeError = error instanceof Error ? error.message : String(error);
          publish();
        }
      }
    },
    async retryTab(tabId: string) {
      const tab = state.tabs.find((item) => item.id === tabId);
      if (!tab || !state.active) return;
      if (tab.kind === "diff") await this.openCommitFile(tab.path);
      else await readFileTab(tab.path, tab.personalWorkspaceId, fileEpoch);
    }
  };
}

export function canMaintainTeamMembers(state: Pick<TeamManagementState, "scopeMode" | "ownerUserId">) {
  return state.scopeMode !== "GLOBAL" && (state.scopeMode !== "SYSTEM_ADMIN_TEAM" || Boolean(state.ownerUserId));
}

export const TEAM_MEMBER_SCOPE_HINT = "请选择具体系统管理员团队后管理成员";

export function teamScopeLabel(state: Pick<TeamManagementState, "scopeMode" | "ownerUserId" | "owners">) {
  if (state.scopeMode === "GLOBAL") return "全平台只读";
  if (state.scopeMode === "SYSTEM_ADMIN_TEAM") {
    const owner = state.owners.find((item) => item.userId === state.ownerUserId);
    return owner ? `${owner.username}` : "选择系统管理员";
  }
  return "我的团队";
}
