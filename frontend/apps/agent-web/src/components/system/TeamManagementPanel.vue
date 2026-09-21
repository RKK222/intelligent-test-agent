<script setup lang="ts">
import { computed, inject, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { Archive, FolderGit2, RefreshCw, UserPlus, UsersRound } from "lucide-vue-next";
import type { BackendApiClient } from "@test-agent/backend-api";
import type {
  CurrentUser,
  FileTreeEntry,
  TeamApplication,
  TeamCommit,
  TeamContribution,
  TeamExport,
  TeamGitStatus,
  TeamPersonalWorkspace,
  TeamScopeMode,
  TeamScopeParams,
  TeamUser,
  TeamWorkspaceTemplate,
  TeamWorkspaceVersion
} from "@test-agent/shared-types";
import { hasSuperAdminCapability } from "../../auth/roleCapabilities";

const props = defineProps<{ currentUser: CurrentUser | null; pageActive: boolean }>();
const api = inject<BackendApiClient>("api")!;

type MainTab = "members" | "code";
type DetailTab = "changes" | "personal" | "published" | "files";

const mainTab = ref<MainTab>("members");
const scopeMode = ref<TeamScopeMode>("MY_TEAM");
const ownerUserId = ref("");
const owners = ref<TeamUser[]>([]);
const memberKeyword = ref("");
const members = ref<TeamUser[]>([]);
const memberPage = ref(1);
const memberPageSize = ref(20);
const memberTotal = ref(0);
const candidates = ref<TeamUser[]>([]);
const candidateUserId = ref("");
const candidateLoading = ref(false);
const loading = ref(false);
const mutationLoading = ref(false);
const errorMessage = ref("");

const applications = ref<TeamApplication[]>([]);
const templates = ref<TeamWorkspaceTemplate[]>([]);
const versions = ref<TeamWorkspaceVersion[]>([]);
const contributions = ref<TeamContribution[]>([]);
const selectedAppId = ref("");
const selectedTemplateId = ref("");
const selectedVersionId = ref("");
const selectedUserId = ref("");
const selectedPersonalWorkspaceId = ref("");
const detailTab = ref<DetailTab>("changes");
const gitStatus = ref<TeamGitStatus | null>(null);
const personalCommits = ref<TeamCommit[]>([]);
const publishedCommits = ref<TeamCommit[]>([]);
const attributionMessage = ref("");
const selectedCommit = ref<TeamCommit | null>(null);
const commitFiles = ref<{ status: string; oldPath?: string | null; path: string }[]>([]);
const commitPatch = ref("");
const filePath = ref("");
const fileEntries = ref<FileTreeEntry[]>([]);
const filePreview = ref("");
const filePreviewPath = ref("");
const exportJob = ref<TeamExport | null>(null);
const contributionStats = ref<Record<string, { published: number; personal: number; sync: number; changes: number }>>({});
let exportTimer: ReturnType<typeof setInterval> | undefined;
let statsGeneration = 0;

const superAdmin = computed(() => hasSuperAdminCapability(props.currentUser?.roles));
const scope = computed<TeamScopeParams>(() => ({
  scopeMode: scopeMode.value,
  ownerUserId: scopeMode.value === "SYSTEM_ADMIN_TEAM" ? ownerUserId.value : undefined
}));
const selectedContribution = computed(() =>
  contributions.value.find((item) => item.userId === selectedUserId.value) ?? null);
const selectedWorktree = computed(() => selectedContribution.value?.personalWorkspaces
  .find((item) => item.personalWorkspaceId === selectedPersonalWorkspaceId.value) ?? null);
const canMaintainMembers = computed(() => scopeMode.value !== "GLOBAL"
  && (scopeMode.value !== "SYSTEM_ADMIN_TEAM" || Boolean(ownerUserId.value)));
const exportProgress = computed(() => {
  const job = exportJob.value;
  if (!job || job.totalItems === 0) return 0;
  return Math.round(job.completedItems * 100 / job.totalItems);
});

function showError(error: unknown) {
  errorMessage.value = error instanceof Error ? error.message : String(error);
}

async function loadOwners() {
  if (!superAdmin.value) return;
  owners.value = (await api.listSystemAdmins("", 1, 200)).items;
}

async function loadMembers() {
  if (!canMaintainMembers.value) return;
  loading.value = true;
  errorMessage.value = "";
  try {
    const [memberResult, candidatePage] = await Promise.all([
      api.listSystemAdminTeamMembers(
        scope.value, memberKeyword.value, memberPage.value, memberPageSize.value),
      api.listSystemAdminTeamCandidates(scope.value, "", 1, 50)
    ]);
    members.value = memberResult.items;
    memberTotal.value = memberResult.total;
    candidates.value = candidatePage.items;
    if (!candidates.value.some((item) => item.userId === candidateUserId.value)) candidateUserId.value = "";
  } catch (error) {
    showError(error);
  } finally {
    loading.value = false;
  }
}

async function searchCandidates(keyword: string) {
  candidateLoading.value = true;
  try {
    candidates.value = (await api.listSystemAdminTeamCandidates(scope.value, keyword, 1, 50)).items;
  } catch (error) {
    showError(error);
  } finally {
    candidateLoading.value = false;
  }
}

function searchMembers() {
  memberPage.value = 1;
  void loadMembers();
}

async function addMember() {
  if (!candidateUserId.value) return;
  mutationLoading.value = true;
  try {
    await api.addSystemAdminTeamMember(scope.value, candidateUserId.value);
    await loadMembers();
  } catch (error) {
    showError(error);
  } finally {
    mutationLoading.value = false;
  }
}

async function removeMember(userId: string) {
  mutationLoading.value = true;
  try {
    await api.removeSystemAdminTeamMember(scope.value, userId);
    await loadMembers();
  } catch (error) {
    showError(error);
  } finally {
    mutationLoading.value = false;
  }
}

async function loadApplications() {
  loading.value = true;
  errorMessage.value = "";
  try {
    applications.value = await api.listTeamApplications(scope.value);
    if (!applications.value.some((item) => item.appId === selectedAppId.value)) {
      selectedAppId.value = applications.value[0]?.appId ?? "";
    }
  } catch (error) {
    showError(error);
  } finally {
    loading.value = false;
  }
}

async function loadTemplates() {
  templates.value = selectedAppId.value
    ? await api.listTeamWorkspaceTemplates(scope.value, selectedAppId.value) : [];
  if (!templates.value.some((item) => item.workspaceId === selectedTemplateId.value)) {
    selectedTemplateId.value = templates.value[0]?.workspaceId ?? "";
  }
}

async function loadVersions() {
  versions.value = selectedTemplateId.value
    ? await api.listTeamWorkspaceVersions(scope.value, selectedTemplateId.value) : [];
  if (!versions.value.some((item) => item.versionId === selectedVersionId.value)) {
    selectedVersionId.value = versions.value[0]?.versionId ?? "";
  }
}

async function loadContributions() {
  contributions.value = selectedVersionId.value
    ? await api.listTeamContributions(scope.value, selectedVersionId.value) : [];
  if (!contributions.value.some((item) => item.userId === selectedUserId.value)) {
    selectedUserId.value = contributions.value[0]?.userId ?? "";
  }
  void loadContributionStats(contributions.value);
}

async function allCommits(personalWorkspaceId: string, kind: "PERSONAL" | "PUBLISHED") {
  const result: TeamCommit[] = [];
  let offset = 0;
  while (true) {
    const page = await api.listTeamWorkspaceCommits(scope.value, personalWorkspaceId, kind, offset, 200);
    result.push(...page.items);
    if (!page.hasMore || page.items.length === 0) return result;
    offset += page.items.length;
  }
}

/** 复用现有只读接口并限制为四人并发，避免人员卡片统计压垮工作区节点。 */
async function loadContributionStats(items: TeamContribution[]) {
  const generation = ++statsGeneration;
  contributionStats.value = {};
  let cursor = 0;
  const workers = Array.from({ length: Math.min(4, items.length) }, async () => {
    while (cursor < items.length) {
      const item = items[cursor++];
      if (!item) return;
      const published = new Set<string>();
      const personal = new Set<string>();
      const sync = new Set<string>();
      let changes = 0;
      try {
        for (const worktree of item.personalWorkspaces) {
          const [status, personalItems, publishedItems] = await Promise.all([
            api.getTeamWorkspaceGitStatus(scope.value, worktree.personalWorkspaceId),
            allCommits(worktree.personalWorkspaceId, "PERSONAL"),
            allCommits(worktree.personalWorkspaceId, "PUBLISHED")
          ]);
          changes += status.files.length;
          for (const commit of publishedItems) {
            (commit.contributionType === "SYNC_MERGE" ? sync : published).add(commit.commit);
          }
          for (const commit of personalItems) {
            const key = `${worktree.personalWorkspaceId}:${commit.commit}`;
            if (commit.contributionType === "SYNC_MERGE") sync.add(commit.commit);
            else personal.add(key);
          }
        }
        if (generation === statsGeneration) {
          contributionStats.value = {
            ...contributionStats.value,
            [item.userId]: { published: published.size, personal: personal.size, sync: sync.size, changes }
          };
        }
      } catch {
        // 卡片统计是附加信息；详情入口仍展示精确错误，不以单个离线节点阻断整个成员列表。
      }
    }
  });
  await Promise.all(workers);
}

function chooseContribution(item: TeamContribution) {
  selectedUserId.value = item.userId;
  selectedPersonalWorkspaceId.value = item.personalWorkspaces[0]?.personalWorkspaceId ?? "";
}

async function loadWorktreeDetail() {
  const personalWorkspaceId = selectedPersonalWorkspaceId.value;
  if (!personalWorkspaceId) {
    gitStatus.value = null;
    personalCommits.value = [];
    publishedCommits.value = [];
    fileEntries.value = [];
    return;
  }
  loading.value = true;
  try {
    const [status, personal, published] = await Promise.all([
      api.getTeamWorkspaceGitStatus(scope.value, personalWorkspaceId),
      api.listTeamWorkspaceCommits(scope.value, personalWorkspaceId, "PERSONAL", 0, 100),
      api.listTeamWorkspaceCommits(scope.value, personalWorkspaceId, "PUBLISHED", 0, 100)
    ]);
    gitStatus.value = status;
    personalCommits.value = personal.items;
    publishedCommits.value = published.items;
    attributionMessage.value = published.attributionConfirmed ? "" : (published.attributionMessage ?? "无法归属");
    await loadFiles("");
  } catch (error) {
    showError(error);
  } finally {
    loading.value = false;
  }
}

async function loadFiles(path: string) {
  const worktree = selectedWorktree.value;
  if (!worktree) return;
  filePath.value = path;
  fileEntries.value = await api.listTeamWorkspaceFiles(
    scope.value, worktree.personalWorkspaceId, worktree.workspaceId, path);
}

async function openFile(entry: FileTreeEntry) {
  if (entry.type === "directory") {
    await loadFiles(entry.path);
    return;
  }
  const worktree = selectedWorktree.value;
  if (!worktree) return;
  const content = await api.readTeamWorkspaceFile(
    scope.value, worktree.personalWorkspaceId, worktree.workspaceId, entry.path);
  filePreviewPath.value = entry.path;
  filePreview.value = content.content;
}

async function openCommit(commit: TeamCommit, kind: "PERSONAL" | "PUBLISHED") {
  const personalWorkspaceId = selectedPersonalWorkspaceId.value;
  if (!personalWorkspaceId) return;
  selectedCommit.value = commit;
  const detail = await api.getTeamWorkspaceCommitDetail(scope.value, personalWorkspaceId, commit.commit, kind);
  commitFiles.value = detail.files;
  commitPatch.value = "";
  if (detail.files[0]) await openCommitFile(detail.files[0].path, kind);
}

async function openCommitFile(path: string, kind: "PERSONAL" | "PUBLISHED") {
  if (!selectedCommit.value || !selectedPersonalWorkspaceId.value) return;
  const result = await api.getTeamWorkspaceCommitDiff(
    scope.value, selectedPersonalWorkspaceId.value, selectedCommit.value.commit, path, kind);
  commitPatch.value = result.patch;
}

async function createExport() {
  if (!selectedVersionId.value) return;
  exportJob.value = await api.createTeamExport(scope.value, selectedVersionId.value);
  startExportPolling();
}

function startExportPolling() {
  if (exportTimer) clearInterval(exportTimer);
  exportTimer = setInterval(async () => {
    if (!exportJob.value) return;
    try {
      exportJob.value = await api.getTeamExport(exportJob.value.exportId);
      if (!["QUEUED", "RUNNING"].includes(exportJob.value.status)) {
        if (exportTimer) clearInterval(exportTimer);
        exportTimer = undefined;
      }
    } catch (error) {
      showError(error);
    }
  }, 1500);
}

async function downloadExport() {
  if (!exportJob.value) return;
  const route = await api.createTeamExportDownloadRoute(exportJob.value.exportId);
  window.location.assign(`${route.baseUrl.replace(/\/$/, "")}${route.downloadPath}`);
}

async function cancelExport() {
  if (!exportJob.value) return;
  await api.cancelTeamExport(exportJob.value.exportId);
  exportJob.value = await api.getTeamExport(exportJob.value.exportId);
}

watch(scope, async () => {
  statsGeneration += 1;
  memberPage.value = 1;
  selectedAppId.value = "";
  if (mainTab.value === "members") await loadMembers();
  await loadApplications();
}, { deep: true });
watch(selectedAppId, () => loadTemplates().catch(showError));
watch(selectedTemplateId, () => loadVersions().catch(showError));
watch(selectedVersionId, () => loadContributions().catch(showError));
watch(selectedUserId, () => {
  const first = selectedContribution.value?.personalWorkspaces[0];
  selectedPersonalWorkspaceId.value = first?.personalWorkspaceId ?? "";
});
watch(selectedPersonalWorkspaceId, () => loadWorktreeDetail().catch(showError));
watch(mainTab, (value) => {
  if (value === "members") void loadMembers();
});

onMounted(async () => {
  if (superAdmin.value) {
    scopeMode.value = "GLOBAL";
    mainTab.value = "code";
    await loadOwners();
  } else {
    await loadMembers();
  }
  await loadApplications();
});
onBeforeUnmount(() => {
  if (exportTimer) clearInterval(exportTimer);
});
</script>

<template>
  <section class="team-panel">
    <header class="team-header">
      <div>
        <h2>团队管理</h2>
        <p>按人员审阅版本提交、个人 worktree 与未提交修改。</p>
      </div>
      <div class="scope-controls">
        <el-select v-if="superAdmin" v-model="scopeMode" aria-label="查看范围" style="width: 180px">
          <el-option label="全平台视图" value="GLOBAL" />
          <el-option label="指定系统管理员" value="SYSTEM_ADMIN_TEAM" />
        </el-select>
        <el-select
          v-if="superAdmin && scopeMode === 'SYSTEM_ADMIN_TEAM'"
          v-model="ownerUserId"
          filterable
          placeholder="选择系统管理员"
          style="width: 220px"
        >
          <el-option v-for="owner in owners" :key="owner.userId" :label="`${owner.username} · ${owner.unifiedAuthId}`" :value="owner.userId" />
        </el-select>
        <el-button :icon="RefreshCw" circle aria-label="刷新" @click="mainTab === 'members' ? loadMembers() : loadApplications()" />
      </div>
    </header>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon closable @close="errorMessage = ''" />
    <el-tabs v-model="mainTab" class="main-tabs">
      <el-tab-pane label="我的组员" name="members" :disabled="scopeMode === 'GLOBAL'">
        <div class="member-toolbar">
          <el-input v-model="memberKeyword" clearable placeholder="搜索姓名、统一认证号或部门" @keyup.enter="searchMembers" />
          <el-button @click="searchMembers">搜索</el-button>
          <el-select
            v-model="candidateUserId"
            filterable
            remote
            :remote-method="searchCandidates"
            :loading="candidateLoading"
            placeholder="搜索并选择要添加的用户"
          >
            <el-option v-for="candidate in candidates" :key="candidate.userId" :label="`${candidate.username} · ${candidate.unifiedAuthId}`" :value="candidate.userId" />
          </el-select>
          <el-button type="primary" :icon="UserPlus" :loading="mutationLoading" :disabled="!candidateUserId" @click="addMember">添加组员</el-button>
        </div>
        <el-table v-loading="loading" :data="members" height="100%">
          <el-table-column prop="username" label="姓名" min-width="120" />
          <el-table-column prop="unifiedAuthId" label="统一认证号" min-width="150" />
          <el-table-column prop="department" label="部门" min-width="160" />
          <el-table-column label="角色" min-width="180"><template #default="{ row }">{{ row.roles.join(' / ') }}</template></el-table-column>
          <el-table-column label="操作" width="100"><template #default="{ row }"><el-button link type="danger" @click="removeMember(row.userId)">移除</el-button></template></el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="memberPage"
          v-model:page-size="memberPageSize"
          :total="memberTotal"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadMembers"
          @size-change="searchMembers"
        />
      </el-tab-pane>

      <el-tab-pane label="代码视图" name="code">
        <div class="code-layout">
          <div class="code-filters">
            <el-select v-model="selectedAppId" filterable placeholder="应用">
              <el-option v-for="item in applications" :key="item.appId" :value="item.appId" :label="`${item.appName}（当前 ${item.currentMemberCount} / 历史 ${item.historicalMemberCount}）`" />
            </el-select>
            <el-select v-model="selectedTemplateId" filterable placeholder="工作空间">
              <el-option v-for="item in templates" :key="item.workspaceId" :value="item.workspaceId" :label="item.workspaceName" />
            </el-select>
            <el-select v-model="selectedVersionId" filterable placeholder="版本">
              <el-option v-for="item in versions" :key="item.versionId" :value="item.versionId" :label="`${item.version} · ${item.branch}`" />
            </el-select>
            <el-button :icon="Archive" :disabled="!selectedVersionId" @click="createExport">整组导出</el-button>
          </div>

          <div v-if="exportJob" class="export-strip">
            <span>{{ exportJob.status }} · {{ exportJob.completedItems }}/{{ exportJob.totalItems }}</span>
            <el-progress :percentage="exportProgress" :stroke-width="6" />
            <el-button v-if="['QUEUED', 'RUNNING'].includes(exportJob.status)" link type="danger" @click="cancelExport">取消</el-button>
            <el-button v-if="['READY', 'PARTIAL_READY'].includes(exportJob.status)" link type="primary" @click="downloadExport">下载 ZIP</el-button>
          </div>

          <div class="code-columns">
            <aside class="people-column">
              <button
                v-for="item in contributions"
                :key="item.userId"
                type="button"
                :class="['person-card', { active: item.userId === selectedUserId }]"
                @click="chooseContribution(item)"
              >
                <span class="person-name">{{ item.username }}</span>
                <el-tag size="small" :type="item.membershipState === 'CURRENT' ? 'success' : 'info'">{{ item.membershipState }}</el-tag>
                <span>{{ item.personalWorkspaces.length }} 个 worktree</span>
                <span v-if="contributionStats[item.userId]" class="person-stats">
                  发布 {{ contributionStats[item.userId].published }} · 个人 {{ contributionStats[item.userId].personal }} ·
                  同步 {{ contributionStats[item.userId].sync }} · 修改 {{ contributionStats[item.userId].changes }}
                </span>
                <span v-else class="person-stats">统计加载中</span>
              </button>
              <el-empty v-if="!contributions.length" :image-size="64" description="该版本暂无团队成员" />
            </aside>

            <main class="detail-column">
              <template v-if="selectedContribution">
                <div class="member-summary">
                  <UsersRound /><strong>{{ selectedContribution.username }}</strong>
                  <span>{{ selectedContribution.organization }} / {{ selectedContribution.department }}</span>
                  <el-select v-if="selectedContribution.personalWorkspaces.length" v-model="selectedPersonalWorkspaceId" size="small" style="width: 260px">
                    <el-option v-for="item in selectedContribution.personalWorkspaces" :key="item.personalWorkspaceId" :value="item.personalWorkspaceId" :label="`${item.workspaceName} · ${item.branch}`" />
                  </el-select>
                </div>
                <el-empty v-if="!selectedWorktree" description="该成员在此版本没有个人 worktree" />
                <el-tabs v-else v-model="detailTab" class="detail-tabs">
                  <el-tab-pane name="changes" :label="`未提交修改 ${gitStatus?.files.length ?? 0}`">
                    <div class="metric-row">
                      <el-tag>staged {{ gitStatus?.stagedCount ?? 0 }}</el-tag>
                      <el-tag type="warning">unstaged {{ gitStatus?.unstagedCount ?? 0 }}</el-tag>
                      <el-tag type="info">untracked {{ gitStatus?.untrackedCount ?? 0 }}</el-tag>
                    </div>
                    <article v-for="file in gitStatus?.files ?? []" :key="`${file.rawStatus}:${file.path}`" class="diff-card">
                      <header><code>{{ file.rawStatus }}</code><strong>{{ file.path }}</strong><span>+{{ file.additions }} / -{{ file.deletions }}</span></header>
                      <pre>{{ file.patch || '无文本 diff' }}</pre>
                    </article>
                  </el-tab-pane>
                  <el-tab-pane name="personal" :label="`个人提交 ${personalCommits.filter(item => item.contributionType !== 'SYNC_MERGE').length}`">
                    <button v-for="item in personalCommits" :key="item.commit" class="commit-row" type="button" @click="openCommit(item, 'PERSONAL')">
                      <code>{{ item.commit.slice(0, 10) }}</code><span>{{ item.subject }}</span><el-tag v-if="item.contributionType === 'SYNC_MERGE'" size="small">SYNC_MERGE</el-tag>
                    </button>
                  </el-tab-pane>
                  <el-tab-pane name="published" :label="`已发布提交 ${publishedCommits.length}`">
                    <el-alert v-if="attributionMessage" :title="attributionMessage" type="warning" show-icon :closable="false" />
                    <button v-for="item in publishedCommits" :key="item.commit" class="commit-row" type="button" @click="openCommit(item, 'PUBLISHED')">
                      <code>{{ item.commit.slice(0, 10) }}</code><span>{{ item.subject }}</span>
                    </button>
                  </el-tab-pane>
                  <el-tab-pane name="files" label="只读文件">
                    <div class="file-browser">
                      <div class="file-list">
                        <button v-if="filePath" type="button" @click="loadFiles(filePath.split('/').slice(0, -1).join('/'))">..</button>
                        <button v-for="entry in fileEntries" :key="entry.path" type="button" @click="openFile(entry)">
                          <FolderGit2 v-if="entry.type === 'directory'" />{{ entry.name }}
                        </button>
                      </div>
                      <div class="file-preview"><strong>{{ filePreviewPath || '选择文件预览' }}</strong><pre>{{ filePreview }}</pre></div>
                    </div>
                  </el-tab-pane>
                </el-tabs>
                <div v-if="selectedCommit" class="commit-drawer">
                  <header><strong>{{ selectedCommit.subject }}</strong><button type="button" @click="selectedCommit = null">×</button></header>
                  <div class="commit-files"><button v-for="file in commitFiles" :key="file.path" type="button" @click="openCommitFile(file.path, detailTab === 'published' ? 'PUBLISHED' : 'PERSONAL')">{{ file.status }} {{ file.path }}</button></div>
                  <pre>{{ commitPatch }}</pre>
                </div>
              </template>
              <el-empty v-else description="请选择成员" />
            </main>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </section>
</template>

<style scoped>
.team-panel { height: 100%; min-height: 0; display: flex; flex-direction: column; padding: 20px; box-sizing: border-box; gap: 12px; background: #f6f8fb; color: #172033; }
.team-header { display: flex; align-items: center; justify-content: space-between; gap: 20px; }
.team-header h2 { margin: 0; font-size: 22px; }.team-header p { margin: 5px 0 0; color: #667085; font-size: 13px; }
.scope-controls,.member-toolbar,.code-filters,.metric-row,.member-summary { display: flex; align-items: center; gap: 10px; }
.main-tabs { flex: 1; min-height: 0; background: #fff; border: 1px solid #e4e9f1; border-radius: 10px; padding: 0 16px 16px; }
.member-toolbar { margin-bottom: 12px; }.member-toolbar .el-input,.member-toolbar .el-select { max-width: 360px; }
.el-pagination { margin-top: 12px; justify-content: flex-end; }
.code-layout { height: calc(100vh - 195px); min-height: 480px; display: flex; flex-direction: column; gap: 10px; }
.code-filters .el-select { width: 230px; }.export-strip { display: grid; grid-template-columns: auto minmax(160px, 360px) auto; align-items: center; gap: 12px; padding: 8px 12px; border-radius: 8px; background: #f3f7ff; font-size: 12px; }
.code-columns { flex: 1; min-height: 0; display: grid; grid-template-columns: 245px 1fr; border: 1px solid #e4e9f1; border-radius: 8px; overflow: hidden; }
.people-column { overflow: auto; padding: 10px; background: #f8fafc; border-right: 1px solid #e4e9f1; }.person-card { width: 100%; display: grid; grid-template-columns: 1fr auto; gap: 6px; margin-bottom: 8px; padding: 12px; border: 1px solid #e1e6ee; border-radius: 8px; background: #fff; text-align: left; color: #475467; cursor: pointer; }.person-card.active { border-color: #4f7cff; box-shadow: 0 0 0 2px #e9efff; }.person-name { color: #172033; font-weight: 650; }.person-stats { grid-column: 1 / -1; font-size: 11px; color: #667085; }
.detail-column { position: relative; overflow: auto; padding: 14px; }.member-summary { padding-bottom: 12px; border-bottom: 1px solid #edf0f5; }.member-summary svg { width: 18px; }.member-summary span { color: #667085; font-size: 12px; }.member-summary .el-select { margin-left: auto; }
.detail-tabs { margin-top: 4px; }.diff-card { margin: 10px 0; border: 1px solid #e3e8f0; border-radius: 8px; overflow: hidden; }.diff-card header { display: flex; gap: 12px; padding: 8px 10px; background: #f8fafc; }.diff-card header span { margin-left: auto; color: #667085; }.diff-card pre,.commit-drawer pre,.file-preview pre { margin: 0; padding: 12px; overflow: auto; background: #101828; color: #d6e3ff; font: 12px/1.55 ui-monospace, SFMono-Regular, Menlo, monospace; white-space: pre-wrap; }
.commit-row { width: 100%; display: grid; grid-template-columns: 100px 1fr auto; gap: 10px; padding: 9px 4px; border: 0; border-bottom: 1px solid #edf0f5; background: transparent; text-align: left; cursor: pointer; }.commit-row:hover { background: #f7f9fc; }
.file-browser { display: grid; grid-template-columns: 270px 1fr; min-height: 420px; border: 1px solid #e4e9f1; border-radius: 8px; overflow: hidden; }.file-list { display: flex; flex-direction: column; overflow: auto; border-right: 1px solid #e4e9f1; }.file-list button,.commit-files button { display: flex; align-items: center; gap: 7px; padding: 8px 10px; border: 0; background: transparent; text-align: left; cursor: pointer; }.file-list button:hover,.commit-files button:hover { background: #f1f5fb; }.file-list svg { width: 15px; }.file-preview { min-width: 0; overflow: auto; }.file-preview strong { display: block; padding: 9px 12px; }
.commit-drawer { position: absolute; inset: 62px 14px 14px; display: grid; grid-template-rows: auto auto 1fr; background: #fff; border: 1px solid #dfe5ee; border-radius: 8px; box-shadow: 0 12px 36px rgba(16,24,40,.16); overflow: hidden; }.commit-drawer header { display: flex; justify-content: space-between; padding: 10px 12px; }.commit-drawer header button { border: 0; background: transparent; font-size: 20px; cursor: pointer; }.commit-files { display: flex; overflow-x: auto; border-block: 1px solid #edf0f5; }
@media (max-width: 1100px) { .code-columns { grid-template-columns: 200px 1fr; }.file-browser { grid-template-columns: 220px 1fr; }.code-filters { flex-wrap: wrap; } }
</style>
