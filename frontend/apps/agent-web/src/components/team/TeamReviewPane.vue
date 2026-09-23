<script setup lang="ts">
import { computed, onMounted, onUnmounted } from "vue";
import {
  MessageSquare,
  ChevronRight,
  X,
  RotateCcw,
  Download,
  Users,
  Search,
  Plus,
  Trash2,
  FolderGit2
} from "lucide-vue-next";
import type { TeamCommit, TeamExportStatus } from "@test-agent/shared-types";
import {
  canMaintainTeamMembers,
  TEAM_MEMBER_SCOPE_HINT,
  teamScopeLabel
} from "./team-management-controller";
import { useTeamManagementView } from "./useTeamManagementView";

const props = withDefaults(defineProps<{
  /** 右栏收起时仍保留浮动审阅入口，并把同一份内容移到 body 对话框。 */
  rightPanelOpen?: boolean;
  /** 工作台对话栏是否展开，用于动态吸附浮动审阅胶囊。 */
  chatOpen?: boolean;
}>(), {
  rightPanelOpen: true,
  chatOpen: false
});

const emit = defineEmits<{
  (e: "open-chat"): void;
}>();

const { controller, state } = useTeamManagementView();
const canMaintain = computed(() => canMaintainTeamMembers(state.value));
const personalCommits = computed(() =>
  state.value.personalCommits.filter((item) => item.contributionType !== "SYNC_MERGE"));
const publishedCommits = computed(() =>
  state.value.publishedCommits.filter((item) => item.contributionType !== "SYNC_MERGE"));
const syncCommits = computed(() => {
  const merged = new Map<string, TeamCommit>();
  for (const item of [...state.value.personalCommits, ...state.value.publishedCommits]) {
    if (item.contributionType === "SYNC_MERGE") merged.set(item.commit, item);
  }
  return [...merged.values()];
});
const exportPercent = computed(() => {
  const job = state.value.exportJob;
  if (!job || job.totalItems === 0) return 0;
  return Math.round(job.completedItems * 100 / job.totalItems);
});

const selectedOwner = computed(() =>
  state.value.owners.find((owner) => owner.userId === state.value.ownerUserId)
);

const scopeDescription = computed(() => {
  if (state.value.scopeMode === "GLOBAL") return "全平台只读";
  if (state.value.scopeMode === "SYSTEM_ADMIN_TEAM") return `${selectedOwner.value?.username ?? "系统管理员团队"}的成员与提交`;
  return "我的团队成员与提交";
});

const exportLabels: Record<TeamExportStatus, string> = {
  QUEUED: "排队中",
  RUNNING: "导出中",
  READY: "可以下载",
  PARTIAL_READY: "部分完成",
  FAILED: "导出失败",
  CANCELLED: "已取消",
  EXPIRED: "已过期"
};

function statsText(userId: string) {
  const stats = state.value.contributionStats[userId];
  if (!stats) return "统计加载中";
  if (stats.unavailable) return "统计失败";
  return `发布 ${stats.published} · 个人 ${stats.personal} · 同步 ${stats.sync} · 修改 ${stats.changes}`;
}

function membershipText(userId: string) {
  const membership = state.value.contributions.find((item) => item.userId === userId)?.membershipState;
  return membership === "HISTORICAL" ? "历史" : "";
}

function onScopeChange(event: Event) {
  const value = (event.target as HTMLSelectElement).value;
  if (value === "GLOBAL") void controller.selectScope("GLOBAL");
  else void controller.selectScope("SYSTEM_ADMIN_TEAM", value);
}

function onMemberKeyword(event: Event) {
  void controller.searchMembers((event.target as HTMLInputElement).value);
}

function commitKind(item: TeamCommit): "PERSONAL" | "PUBLISHED" {
  return state.value.publishedCommits.some((commit) => commit.commit === item.commit) ? "PUBLISHED" : "PERSONAL";
}

function memberInitials(username: string) {
  const value = username.trim();
  return value.slice(0, 2) || "成";
}

function openMemberManagement() {
  void controller.openMemberDialog();
}

function closeReviewDialog() {
  controller.closeReviewDialog();
}

function handleDialogKeydown(event: KeyboardEvent, dialog: "review" | "team-picker" | "members") {
  if (event.key !== "Escape") return;
  event.stopPropagation();
  if (dialog === "review") closeReviewDialog();
  else if (dialog === "team-picker") controller.closeTeamPickerDialog();
  else controller.closeMemberDialog();
}

/**
 * Teleport 弹窗动态插入 body 后，浏览器不会可靠地把焦点移入带 autofocus 的按钮。
 * 在组件存活期间从 window 接管 Escape，确保焦点仍停留在底层触发按钮时也能关闭最上层弹窗。
 */
function handleGlobalKeydown(event: KeyboardEvent) {
  if (event.key !== "Escape") return;
  if (state.value.memberDialogOpen) handleDialogKeydown(event, "members");
  else if (state.value.teamPickerDialogOpen) handleDialogKeydown(event, "team-picker");
  else if (!props.rightPanelOpen && state.value.reviewDialogOpen) handleDialogKeydown(event, "review");
}

onMounted(() => window.addEventListener("keydown", handleGlobalKeydown));
onUnmounted(() => window.removeEventListener("keydown", handleGlobalKeydown));

async function download() {
  const url = await controller.downloadExport();
  if (url) window.location.assign(url);
}

const launcherStyle = computed(() => ({
  right: props.chatOpen ? "460px" : "10px"
}));
</script>

<template>
  <div class="team-review-host">
    <!-- 右栏展开时直接嵌入工作台，收起后把同一份内容传送到页面中央。 -->
    <Teleport to="body" :disabled="props.rightPanelOpen">
      <div
        v-if="props.rightPanelOpen || state.reviewDialogOpen"
        :class="props.rightPanelOpen ? 'team-review-panel-host' : 'team-review-modal-backdrop'"
        @click.self="!props.rightPanelOpen && closeReviewDialog()"
      >
        <section
          class="team-review"
          :class="{ 'team-review--dialog': !props.rightPanelOpen }"
          :aria-label="props.rightPanelOpen ? '团队审阅' : '团队审阅对话框'"
          :aria-modal="!props.rightPanelOpen ? 'true' : undefined"
          :role="props.rightPanelOpen ? 'region' : 'dialog'"
          tabindex="-1"
          @keydown="!props.rightPanelOpen && handleDialogKeydown($event, 'review')"
        >
          <header class="team-review-header">
            <div class="team-review-heading">
              <div class="team-review-title-row">
                <span class="team-review-kicker">管理视角</span>
                <h2>团队审阅</h2>
                <span class="team-review-scope-chip">{{ scopeDescription }}</span>
              </div>
              <p class="team-review-subtitle">查看成员的提交和发布归属；文件变化请看左侧</p>
              <label v-if="!state.scopeLocked" class="team-review-scope">
                <span>团队范围</span>
                <div class="team-review-select-wrapper">
                  <select
                    aria-label="团队范围"
                    :value="state.scopeMode === 'GLOBAL' ? 'GLOBAL' : state.ownerUserId"
                    @change="onScopeChange"
                  >
                    <option value="GLOBAL">全平台只读</option>
                    <option v-for="owner in state.owners" :key="owner.userId" :value="owner.userId">
                      {{ owner.username }}
                    </option>
                  </select>
                </div>
              </label>
              <p v-else class="team-review-scope-locked">{{ teamScopeLabel(state) }}</p>
              <p v-if="!canMaintain" class="team-review-hint">{{ TEAM_MEMBER_SCOPE_HINT }}</p>
            </div>
            <div class="team-review-actions">
              <button type="button" class="team-review-action-button" @click="openMemberManagement">
                <Users class="team-icon-14" />
                <span>成员管理</span>
              </button>
              <button
                type="button"
                class="team-review-action-button team-review-action-button--primary"
                :disabled="!state.selectedVersionId"
                @click="controller.createExport()"
              >
                <Download class="team-icon-14" />
                <span>整组导出</span>
              </button>
              <button
                v-if="!props.rightPanelOpen"
                type="button"
                class="team-review-close-button"
                aria-label="关闭审阅对话框"
                autofocus
                @click="closeReviewDialog"
              >
                <X :size="15" />
                <span>关闭</span>
              </button>
            </div>
          </header>

          <div class="team-review-context-strip">
            <span class="team-review-context-label">当前范围</span>
            <strong>{{ teamScopeLabel(state) }}</strong>
            <span v-if="state.selectedUserId" class="team-review-context-member">
              · {{ state.reviewRoster.find((item) => item.userId === state.selectedUserId)?.username }}
            </span>
          </div>

          <p v-if="state.catalogError" class="team-review-error" role="alert">
            <span>{{ state.catalogError }}</span>
            <button type="button" @click="controller.retryCatalog()">
              <RotateCcw class="team-icon-13" />
              <span>重试</span>
            </button>
          </p>
          <p v-if="state.errorMessage" class="team-review-error" role="alert">
            <span>{{ state.errorMessage }}</span>
            <button type="button" @click="controller.dismissError()">关闭</button>
          </p>

          <div v-if="state.exportJob" class="team-review-export">
            <div class="team-review-export-meta">
              <span class="team-review-export-status">{{ exportLabels[state.exportJob.status] }}</span>
              <span class="team-review-export-counts">{{ state.exportJob.completedItems }}/{{ state.exportJob.totalItems }} 项</span>
              <span class="team-review-export-pct">{{ exportPercent }}%</span>
            </div>
            <div class="team-review-progress-track">
              <div class="team-review-progress-fill" :style="{ width: `${exportPercent}%` }" />
            </div>
            <progress :value="exportPercent" max="100" class="sr-only" :aria-label="`导出进度 ${exportPercent}%`" />
            <div class="team-review-export-actions">
              <button
                v-if="state.exportJob.status === 'QUEUED' || state.exportJob.status === 'RUNNING'"
                type="button"
                class="team-btn-secondary"
                @click="controller.cancelExport()"
              >
                取消
              </button>
              <button
                v-if="state.exportJob.status === 'READY' || state.exportJob.status === 'PARTIAL_READY'"
                type="button"
                class="team-btn-primary"
                @click="download"
              >
                <Download class="team-icon-14" />
                <span>下载 ZIP</span>
              </button>
            </div>
          </div>

          <div class="team-review-scroll">
            <p v-if="state.catalogLoading && !state.reviewRoster.length" class="team-review-empty">正在读取可审阅成员…</p>
            <p v-else-if="!state.reviewRoster.length" class="team-review-empty">当前范围没有可审阅的成员。</p>
            <div v-else class="team-review-roster-list">
              <button
                v-for="item in state.reviewRoster"
                :key="item.userId"
                type="button"
                :class="['team-review-person', item.userId === state.selectedUserId && 'is-active']"
                @click="controller.selectMember(item.userId)"
              >
                <span class="team-review-person-avatar" aria-hidden="true">{{ memberInitials(item.username) }}</span>
                <span class="team-review-person-copy">
                  <span class="team-review-person-name">{{ item.username }}</span>
                  <span v-if="membershipText(item.userId)" class="team-review-person-badge">{{ membershipText(item.userId) }}</span>
                  <span class="team-review-person-stats">{{ statsText(item.userId) }}</span>
                </span>
                <ChevronRight class="team-review-person-chevron" aria-hidden="true" :size="16" />
              </button>
            </div>

            <div v-if="state.selectedUserId" class="team-review-detail">
              <p v-if="state.missingDefaultWorkspace" class="team-review-hint">这个版本还没有名为 default 的个人工作空间。</p>

              <details class="team-review-group">
                <summary>
                  <span>个人提交</span>
                  <strong class="team-badge">{{ personalCommits.length }}</strong>
                </summary>
                <div class="team-review-group-body">
                  <div class="team-review-lines">
                    <button
                      v-for="item in personalCommits"
                      :key="`personal:${item.commit}`"
                      type="button"
                      class="team-review-line"
                      @click="controller.selectCommit(item, 'PERSONAL')"
                    >
                      <code class="team-commit-hash">{{ item.commit.slice(0, 8) }}</code>
                      <span class="team-commit-subject" :title="item.subject">{{ item.subject }}</span>
                    </button>
                  </div>
                </div>
              </details>

              <details class="team-review-group">
                <summary>
                  <span>已发布提交</span>
                  <strong class="team-badge">{{ publishedCommits.length }}</strong>
                </summary>
                <div class="team-review-group-body">
                  <p v-if="state.attributionMessage" class="team-review-hint">{{ state.attributionMessage }}</p>
                  <div class="team-review-lines">
                    <button
                      v-for="item in publishedCommits"
                      :key="`published:${item.commit}`"
                      type="button"
                      class="team-review-line"
                      @click="controller.selectCommit(item, 'PUBLISHED')"
                    >
                      <code class="team-commit-hash">{{ item.commit.slice(0, 8) }}</code>
                      <span class="team-commit-subject" :title="item.subject">{{ item.subject }}</span>
                    </button>
                  </div>
                </div>
              </details>

              <details class="team-review-group">
                <summary>
                  <span>同步提交</span>
                  <strong class="team-badge">{{ syncCommits.length }}</strong>
                </summary>
                <div class="team-review-group-body">
                  <div class="team-review-lines">
                    <button
                      v-for="item in syncCommits"
                      :key="`sync:${item.commit}`"
                      type="button"
                      class="team-review-line"
                      @click="controller.selectCommit(item, commitKind(item))"
                    >
                      <code class="team-commit-hash">{{ item.commit.slice(0, 8) }}</code>
                      <span class="team-commit-subject" :title="item.subject">{{ item.subject }}</span>
                      <span class="team-merge-chip">SYNC_MERGE</span>
                    </button>
                  </div>
                </div>
              </details>

              <section v-if="state.selectedCommit" class="team-review-commit-detail">
                <div class="team-review-commit-detail-header">
                  <FolderGit2 class="team-icon-14" />
                  <h3>提交文件 · {{ state.selectedCommit.subject }}</h3>
                </div>
                <div class="team-review-lines">
                  <button
                    v-for="file in state.commitFiles"
                    :key="file.path"
                    type="button"
                    class="team-review-line"
                    @click="controller.openCommitFile(file.path)"
                  >
                    <code class="team-status-tag">{{ file.status }}</code>
                    <span class="team-file-path" :title="file.path">{{ file.path }}</span>
                  </button>
                </div>
              </section>
            </div>
          </div>
        </section>
      </div>
    </Teleport>

    <!-- 浮动审阅胶囊：支持显示当前审阅人员，收缩状态下支持一键展开 AI 对话 -->
    <Teleport to="body">
      <div
        v-if="!props.rightPanelOpen && state.active"
        class="team-review-launcher-group"
        :style="launcherStyle"
      >
        <button
          type="button"
          class="team-review-launcher"
          aria-label="打开团队审阅"
          title="点击打开团队审阅"
          @click="controller.openReviewDialog()"
        >
          <span class="team-review-launcher-dot" aria-hidden="true" />
          <span class="team-review-launcher-text">{{ state.selectedUserId ? (state.reviewRoster.find((item) => item.userId === state.selectedUserId)?.username ?? "团队审阅") : "团队审阅" }}</span>
        </button>
        <div class="team-review-launcher-divider" aria-hidden="true" />
        <button
          type="button"
          class="team-review-launcher-chat-btn"
          aria-label="管理团队成员"
          title="管理团队成员"
          @click="openMemberManagement"
        >
          <Users :size="13" />
          <span>成员</span>
        </button>
        <div class="team-review-launcher-divider" aria-hidden="true" />
        <button
          type="button"
          class="team-review-launcher-chat-btn"
          aria-label="打开AI对话"
          title="展开智能体对话"
          @click="emit('open-chat')"
        >
          <MessageSquare :size="13" />
          <span>对话</span>
        </button>
      </div>
    </Teleport>

    <Teleport to="body">
      <div v-if="state.teamPickerDialogOpen" class="team-dialog-backdrop" @click.self="controller.closeTeamPickerDialog()">
        <section class="team-dialog team-dialog--compact" role="dialog" aria-modal="true" aria-label="选择系统管理员团队" @keydown="handleDialogKeydown($event, 'team-picker')">
          <header class="team-dialog-header">
            <div>
              <span class="team-review-kicker">成员管理</span>
              <h2>选择系统管理员团队</h2>
            </div>
            <button type="button" class="team-dialog-close-btn" aria-label="关闭团队选择" autofocus @click="controller.closeTeamPickerDialog()">
              <X :size="16" />
            </button>
          </header>
          <p class="team-dialog-description">全平台只读只能查看审阅内容。请选择一个系统管理员团队后再维护成员。</p>
          <p v-if="state.ownersLoading" class="team-review-empty" role="status">正在读取可管理的系统管理员团队…</p>
          <p v-if="state.ownersError" class="team-review-error" role="alert">
            <span>{{ state.ownersError }}</span>
            <button type="button" @click="controller.retryOwners()">重试</button>
          </p>
          <div class="team-owner-list">
            <button v-for="owner in state.owners" :key="owner.userId" type="button" class="team-owner-option" @click="controller.selectMemberManagementOwner(owner.userId)">
              <span class="team-review-person-avatar" aria-hidden="true">{{ memberInitials(owner.username) }}</span>
              <span class="team-owner-info">
                <strong>{{ owner.username }}</strong>
                <small>{{ owner.unifiedAuthId }} · {{ owner.department || "未填写部门" }}</small>
              </span>
              <ChevronRight class="team-owner-chevron" :size="16" aria-hidden="true" />
            </button>
            <p v-if="!state.ownersLoading && !state.ownersError && !state.owners.length" class="team-review-empty">暂无可管理的系统管理员团队。</p>
          </div>
        </section>
      </div>

      <div v-if="state.memberDialogOpen" class="team-dialog-backdrop" @click.self="controller.closeMemberDialog()">
        <section class="team-dialog team-dialog--members" role="dialog" aria-modal="true" aria-label="成员管理" @keydown="handleDialogKeydown($event, 'members')">
          <header class="team-dialog-header">
            <div>
              <span class="team-review-kicker">{{ scopeDescription }}</span>
              <h2>成员管理</h2>
            </div>
            <button type="button" class="team-dialog-close-btn" aria-label="关闭成员管理" autofocus @click="controller.closeMemberDialog()">
              <X :size="16" />
            </button>
          </header>
          <p v-if="!canMaintain" class="team-review-hint">{{ TEAM_MEMBER_SCOPE_HINT }}</p>
          <template v-else>
            <form class="team-member-search" @submit.prevent>
              <div class="team-input-with-icon">
                <Search class="team-input-icon" :size="14" />
                <input
                  aria-label="搜索组员"
                  placeholder="搜索姓名、统一认证号或部门"
                  :value="state.memberKeyword"
                  @input="onMemberKeyword"
                >
              </div>
            </form>
            <div class="team-member-add">
              <input
                aria-label="搜索要添加的用户"
                placeholder="搜索要添加的用户"
                @input="controller.searchCandidates(($event.target as HTMLInputElement).value)"
              >
              <select
                aria-label="选择要添加的用户"
                :value="state.candidateUserId"
                @change="controller.chooseCandidate(($event.target as HTMLSelectElement).value)"
              >
                <option value="">选择用户</option>
                <option v-for="candidate in state.candidates" :key="candidate.userId" :value="candidate.userId">
                  {{ candidate.username }} · {{ candidate.unifiedAuthId }}
                </option>
              </select>
              <button
                type="button"
                class="team-btn-primary"
                :disabled="!state.candidateUserId || state.mutationLoading"
                @click="controller.addMember()"
              >
                <Plus :size="14" />
                <span>添加组员</span>
              </button>
            </div>
            <div class="team-member-list">
              <p v-if="state.memberLoading" class="team-review-empty">正在读取成员…</p>
              <article v-for="member in state.members" :key="member.userId" class="team-member-row">
                <div class="team-member-identity">
                  <span class="team-review-person-avatar" aria-hidden="true">{{ memberInitials(member.username) }}</span>
                  <div class="team-member-names">
                    <strong>{{ member.username }}</strong>
                    <small>{{ member.unifiedAuthId }} · {{ member.department || "未填写部门" }}</small>
                  </div>
                </div>
                <button
                  type="button"
                  class="team-btn-danger"
                  :disabled="state.mutationLoading"
                  @click="controller.removeMember(member.userId)"
                >
                  <Trash2 :size="13" />
                  <span>移除</span>
                </button>
              </article>
              <p v-if="!state.memberLoading && !state.members.length" class="team-review-empty">没有匹配的组员。</p>
            </div>
            <div class="team-member-pager">
              <button type="button" class="team-btn-secondary" :disabled="state.memberPage <= 1" @click="controller.setMemberPage(state.memberPage - 1)">上一页</button>
              <span class="team-pager-indicator">{{ state.memberPage }} / {{ Math.max(1, Math.ceil(state.memberTotal / state.memberPageSize)) }}</span>
              <button type="button" class="team-btn-secondary" :disabled="state.memberPage * state.memberPageSize >= state.memberTotal" @click="controller.setMemberPage(state.memberPage + 1)">下一页</button>
            </div>
          </template>
        </section>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.team-review-host {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}
.team-review-panel-host {
  height: 100%;
  min-height: 0;
}

.team-review,
.team-dialog {
  color: var(--ta-text, #1e293b);
  background: var(--ta-surface, #ffffff);
  border: 1px solid var(--ta-border, #e2e8f0);
}

.team-review {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  border-width: 0 0 0 1px;
}

.team-review--dialog {
  width: min(840px, calc(100vw - 32px));
  height: min(780px, calc(100vh - 32px));
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 25px 50px -12px rgba(15, 23, 42, 0.25), 0 0 0 1px rgba(15, 23, 42, 0.05);
}

.team-review-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--ta-border, #e2e8f0);
  background: #ffffff;
}

.team-review-heading {
  min-width: 0;
  flex: 1;
}

.team-review-title-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.team-review-header h2,
.team-dialog h2,
.team-review-detail h3 {
  margin: 0;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: #0f172a;
}

.team-review-header h2,
.team-dialog h2 {
  font-size: 16px;
  line-height: 1.3;
}

.team-review-kicker {
  color: #475569;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 1px 7px;
  font-size: 11px;
  font-weight: 600;
}

.team-review-scope-chip,
.team-review-person-badge {
  color: #be123c;
  background: #fff1f2;
  border: 1px solid #ffe4e6;
  border-radius: 9999px;
  padding: 1px 9px;
  font-size: 11px;
  font-weight: 500;
}

.team-review-subtitle,
.team-dialog-description,
.team-review-hint,
.team-review-empty,
.team-review-metrics,
.team-member-row small {
  color: var(--ta-muted, #64748b);
  font-size: 12px;
  line-height: 1.5;
}

.team-review-subtitle {
  margin: 4px 0 0;
}

.team-review-scope,
.team-review-scope-locked {
  display: flex;
  flex-direction: column;
  gap: 5px;
  margin: 10px 0 0;
  font-size: 12px;
  color: #334155;
  font-weight: 500;
}

.team-review-scope-locked {
  color: var(--ta-muted, #64748b);
}

.team-review-select-wrapper {
  position: relative;
  display: inline-block;
  width: 100%;
}

.team-review-scope select,
.team-dialog input,
.team-dialog select {
  width: 100%;
  height: 34px;
  padding: 0 10px;
  border: 1px solid var(--ta-border-strong, #cbd5e1);
  border-radius: 8px;
  background: #ffffff;
  color: var(--ta-text, #1e293b);
  font-size: 13px;
  transition: all 0.15s ease;
  outline: none;
}

.team-review-scope select:focus,
.team-dialog input:focus,
.team-dialog select:focus {
  border-color: #be123c;
  box-shadow: 0 0 0 3px rgba(225, 29, 72, 0.12);
}

.team-review-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  align-self: flex-start;
}

.team-review-action-button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 1px solid var(--ta-border-strong, #cbd5e1);
  border-radius: 8px;
  background: #ffffff;
  color: #334155;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
}

.team-review-action-button:hover {
  background: #f8fafc;
  border-color: #94a3b8;
  color: #0f172a;
}

.team-review-action-button--primary {
  color: #ffffff !important;
  background: #0f172a !important;
  border-color: #0f172a !important;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.08);
}

.team-review-action-button--primary:hover:not(:disabled) {
  background: #1e293b !important;
  border-color: #1e293b !important;
  transform: translateY(-1px);
}

.team-review-action-button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
  transform: none;
}

.team-review-close-button {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
  color: #64748b;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}

.team-review-close-button:hover {
  background: #f1f5f9;
  color: #0f172a;
  border-color: #cbd5e1;
}

.team-review-context-strip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 20px;
  background: #f8fafc;
  border-bottom: 1px solid var(--ta-border, #e2e8f0);
  font-size: 12px;
  color: #475569;
}

.team-review-context-label {
  color: var(--ta-muted, #64748b);
}

.team-review-context-member {
  color: #0f172a;
  font-weight: 600;
}

.team-review-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin: 10px 20px 0;
  padding: 10px 14px;
  border-radius: 8px;
  background: #fff1f2;
  border: 1px solid #fecdd3;
  color: #be123c;
  font-size: 13px;
}

.team-review-error button {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  border-radius: 6px;
  border: 1px solid #fecdd3;
  background: #ffffff;
  color: #be123c;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
}

.team-review-export {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 20px;
  background: #f8fafc;
  border-bottom: 1px solid var(--ta-border, #e2e8f0);
  font-size: 12px;
}

.team-review-export-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 500;
  color: #334155;
}

.team-review-progress-track {
  width: 100%;
  height: 6px;
  border-radius: 9999px;
  background: #e2e8f0;
  overflow: hidden;
}

.team-review-progress-fill {
  height: 100%;
  border-radius: 9999px;
  background: linear-gradient(90deg, #be123c, #e11d48);
  transition: width 0.3s ease;
}

.team-review-export-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.team-review-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 12px 16px 20px;
}

.team-review-roster-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.team-review-person {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 9px 12px;
  border: 1px solid transparent !important;
  border-radius: 10px !important;
  background: transparent !important;
  text-align: left;
  cursor: pointer;
  transition: all 0.15s ease;
}

.team-review-person:hover {
  background: #f8fafc !important;
  border-color: #e2e8f0 !important;
}

.team-review-person.is-active {
  background: #fff5f5 !important;
  border-color: #fecdd3 !important;
  box-shadow: 0 1px 3px rgba(225, 29, 72, 0.06);
}

.team-review-person-avatar {
  display: inline-grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: linear-gradient(135deg, #fee2e2, #fecdd3);
  color: #991b1b;
  font-size: 12px;
  font-weight: 700;
  box-shadow: inset 0 1px 2px rgba(255, 255, 255, 0.8), 0 1px 2px rgba(0, 0, 0, 0.04);
}

.team-review-person-copy {
  display: grid;
  min-width: 0;
  grid-template-columns: auto auto;
  align-items: center;
  gap: 2px 8px;
}

.team-review-person-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
  color: #0f172a;
  font-size: 13px;
}

.team-review-person-badge {
  justify-self: start;
}

.team-review-person-stats {
  grid-column: 1 / -1;
  color: var(--ta-muted, #64748b);
  font-size: 11px;
}

.team-review-person-chevron {
  color: #94a3b8;
  transition: transform 0.15s;
}

.team-review-person:hover .team-review-person-chevron {
  color: #475569;
  transform: translateX(2px);
}

.team-review-detail {
  margin-top: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.team-review-group {
  border: 1px solid var(--ta-border, #e2e8f0);
  border-radius: 10px;
  background: #ffffff;
  overflow: hidden;
}

.team-review-group > summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  cursor: pointer;
  list-style: none;
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
  background: #f8fafc;
  transition: background 0.12s;
}

.team-review-group > summary:hover {
  background: #f1f5f9;
}

.team-review-group > summary::-webkit-details-marker {
  display: none;
}

.team-badge {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 9999px;
  background: #e2e8f0;
  color: #334155;
  font-size: 11px;
  font-weight: 600;
}

.team-review-group-body {
  padding: 8px 12px 12px;
}

.team-review-metrics {
  margin: 2px 0 8px;
}

.team-review-lines {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.team-review-line {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 6px 8px;
  border: none !important;
  border-radius: 6px !important;
  background: transparent !important;
  text-align: left;
  font-size: 12px;
  cursor: pointer;
  transition: background 0.12s;
}

.team-review-line:hover {
  background: #f1f5f9 !important;
}

.team-status-tag {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 4px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 11px;
  font-weight: 600;
  background: #f1f5f9;
  color: #475569;
}

.team-status-tag.is-modified {
  background: #eff6ff;
  color: #2563eb;
}

.team-status-tag.is-added {
  background: #ecfdf5;
  color: #059669;
}

.team-file-path,
.team-commit-subject {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #334155;
}

.team-diff-counts {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 11px;
}

.team-diff-add {
  color: #059669;
  font-weight: 600;
}

.team-diff-del {
  color: #e11d48;
  font-weight: 600;
}

.team-commit-hash {
  padding: 1px 5px;
  border-radius: 4px;
  background: #f1f5f9;
  color: #64748b;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 11px;
}

.team-merge-chip {
  padding: 1px 6px;
  border-radius: 9999px;
  background: #f3e8ff;
  color: #7e22ce;
  font-size: 10px;
  font-weight: 600;
}

.team-review-commit-detail {
  padding-top: 12px;
  border-top: 1px solid var(--ta-border, #e2e8f0);
}

.team-review-commit-detail-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  color: #0f172a;
}

.team-review-commit-detail-header h3 {
  font-size: 13px;
}

/* 浮动审阅胶囊 (Launcher) */
.team-review-launcher-group {
  position: fixed;
  top: 50%;
  z-index: 70;
  display: inline-flex;
  align-items: center;
  border: 1px solid #cbd5e1;
  border-radius: 9999px 0 0 9999px;
  background: #ffffff;
  box-shadow: 0 10px 25px -5px rgba(15, 23, 42, 0.15), 0 4px 6px -2px rgba(15, 23, 42, 0.05);
  transform: translateY(-50%);
  transition: right 0.25s cubic-bezier(0.4, 0, 0.2, 1), box-shadow 0.2s;
  padding: 3px 4px 3px 3px;
}

.team-review-launcher-group:hover {
  box-shadow: 0 14px 30px -5px rgba(15, 23, 42, 0.2), 0 6px 10px -2px rgba(15, 23, 42, 0.08);
}

.team-review-launcher {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  max-width: 160px;
  padding: 7px 11px;
  border: none !important;
  border-radius: 9999px !important;
  background: transparent !important;
  color: #0f172a;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s;
}

.team-review-launcher:hover {
  background: #f8fafc !important;
}

.team-review-launcher-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.team-review-launcher-dot {
  flex-shrink: 0;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #e11d48;
  box-shadow: 0 0 0 2px rgba(225, 29, 72, 0.2);
}

.team-review-launcher-divider {
  width: 1px;
  height: 18px;
  background: #e2e8f0;
  margin: 0 3px;
}

.team-review-launcher-chat-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 11px;
  border: none !important;
  border-radius: 9999px !important;
  background: #f1f5f9 !important;
  color: #334155;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
}

.team-review-launcher-chat-btn:hover {
  background: #e2e8f0 !important;
  color: #0f172a;
}

/* 对话框 / 弹窗通用体系 */
.team-dialog-backdrop,
.team-review-modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.45);
  backdrop-filter: blur(8px);
}

.team-dialog {
  width: min(580px, calc(100vw - 32px));
  max-height: min(720px, calc(100vh - 32px));
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 22px;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 25px 50px -12px rgba(15, 23, 42, 0.25), 0 0 0 1px rgba(15, 23, 42, 0.05);
}

.team-dialog--compact {
  width: min(500px, calc(100vw - 32px));
}

.team-dialog-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.team-dialog-close-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
  color: #64748b;
  cursor: pointer;
  transition: all 0.15s;
}

.team-dialog-close-btn:hover {
  background: #f1f5f9;
  color: #0f172a;
}

.team-owner-list,
.team-member-search,
.team-member-add,
.team-member-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.team-owner-list,
.team-member-list {
  min-height: 0;
  overflow-y: auto;
  padding: 2px 0;
}

.team-owner-option {
  display: grid !important;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 10px 12px !important;
  border: 1px solid #e2e8f0 !important;
  border-radius: 10px !important;
  background: #ffffff !important;
  text-align: left;
  cursor: pointer;
  transition: all 0.15s;
}

.team-owner-option:hover {
  background: #f8fafc !important;
  border-color: #cbd5e1 !important;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.team-owner-info,
.team-member-names {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.team-owner-info strong,
.team-member-names strong {
  font-size: 13px;
  color: #0f172a;
}

.team-owner-info small,
.team-member-names small {
  font-size: 11px;
  color: #64748b;
}

.team-owner-chevron {
  color: #94a3b8;
}

.team-input-with-icon {
  position: relative;
  display: flex;
  align-items: center;
}

.team-input-icon {
  position: absolute;
  left: 10px;
  color: #94a3b8;
  pointer-events: none;
}

.team-input-with-icon input {
  padding-left: 32px;
}

.team-member-add {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 8px;
}

.team-member-add input,
.team-member-add select {
  flex: 1;
  min-width: 0;
}

.team-btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 0 14px;
  height: 34px;
  border: 1px solid #0f172a;
  border-radius: 8px;
  background: #0f172a;
  color: #ffffff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s;
}

.team-btn-primary:hover:not(:disabled) {
  background: #1e293b;
  border-color: #1e293b;
}

.team-btn-primary:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.team-btn-secondary {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 0 12px;
  height: 32px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: #ffffff;
  color: #334155;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}

.team-btn-secondary:hover:not(:disabled) {
  background: #f8fafc;
  border-color: #94a3b8;
  color: #0f172a;
}

.team-btn-secondary:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.team-btn-danger {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 10px;
  border: 1px solid #fecdd3;
  border-radius: 6px;
  background: #fff1f2;
  color: #be123c;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}

.team-btn-danger:hover:not(:disabled) {
  background: #ffe4e6;
  border-color: #fda4af;
}

.team-btn-danger:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.team-member-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  background: #ffffff;
  transition: background 0.12s;
}

.team-member-row:hover {
  background: #f8fafc;
}

.team-member-identity {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.team-member-pager {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  padding-top: 6px;
  font-size: 12px;
}

.team-pager-indicator {
  color: #64748b;
  font-weight: 500;
}

.team-icon-14 {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
}

.team-icon-13 {
  width: 13px;
  height: 13px;
  flex-shrink: 0;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border-width: 0;
}

@media (prefers-reduced-motion: reduce) {
  .team-review-launcher-group,
  .team-review,
  .team-dialog {
    transition: none;
  }
}
</style>
