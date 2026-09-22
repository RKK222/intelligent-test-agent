<script setup lang="ts">
import { computed } from "vue";
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
}>(), {
  rightPanelOpen: true
});

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

async function download() {
  const url = await controller.downloadExport();
  if (url) window.location.assign(url);
}
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
              <p class="team-review-subtitle">查看成员的文件变化、提交和发布归属</p>
              <label v-if="!state.scopeLocked" class="team-review-scope">
                <span>团队范围</span>
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
              </label>
              <p v-else class="team-review-scope-locked">{{ teamScopeLabel(state) }}</p>
              <p v-if="!canMaintain" class="team-review-hint">{{ TEAM_MEMBER_SCOPE_HINT }}</p>
            </div>
            <div class="team-review-actions">
              <button type="button" class="team-review-action-button" @click="openMemberManagement">成员管理</button>
              <button type="button" class="team-review-action-button team-review-action-button--primary" :disabled="!state.selectedVersionId" @click="controller.createExport()">整组导出</button>
              <button v-if="!props.rightPanelOpen" type="button" class="team-review-close-button" aria-label="关闭审阅对话框" autofocus @click="closeReviewDialog">关闭</button>
            </div>
          </header>

          <div class="team-review-context-strip">
            <span class="team-review-context-label">当前范围</span>
            <strong>{{ teamScopeLabel(state) }}</strong>
            <span v-if="state.selectedUserId" class="team-review-context-member">{{ state.reviewRoster.find((item) => item.userId === state.selectedUserId)?.username }}</span>
          </div>

          <p v-if="state.catalogError" class="team-review-error" role="alert">
            {{ state.catalogError }}
            <button type="button" @click="controller.retryCatalog()">重试</button>
          </p>
          <p v-if="state.errorMessage" class="team-review-error" role="alert">
            {{ state.errorMessage }}
            <button type="button" @click="controller.dismissError()">关闭</button>
          </p>

          <div v-if="state.exportJob" class="team-review-export">
            <span>{{ exportLabels[state.exportJob.status] }} · {{ state.exportJob.completedItems }}/{{ state.exportJob.totalItems }}</span>
            <progress :value="exportPercent" max="100" :aria-label="`导出进度 ${exportPercent}%`" />
            <button v-if="state.exportJob.status === 'QUEUED' || state.exportJob.status === 'RUNNING'" type="button" @click="controller.cancelExport()">取消</button>
            <button v-if="state.exportJob.status === 'READY' || state.exportJob.status === 'PARTIAL_READY'" type="button" @click="download">下载 ZIP</button>
          </div>

          <div class="team-review-scroll">
            <p v-if="state.catalogLoading && !state.reviewRoster.length" class="team-review-empty">正在读取可审阅成员…</p>
            <p v-else-if="!state.reviewRoster.length" class="team-review-empty">当前范围没有可审阅的成员。</p>
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
              <span class="team-review-person-chevron" aria-hidden="true">›</span>
            </button>

            <div v-if="state.selectedUserId" class="team-review-detail">
              <p v-if="state.missingDefaultWorkspace" class="team-review-hint">这个版本还没有名为 default 的个人工作空间。</p>

              <details class="team-review-group" open>
                <summary><span>未提交修改</span><strong>{{ state.gitStatus?.files.length ?? 0 }}</strong></summary>
                <p class="team-review-metrics">已暂存 {{ state.gitStatus?.stagedCount ?? 0 }} · 未暂存 {{ state.gitStatus?.unstagedCount ?? 0 }} · 未跟踪 {{ state.gitStatus?.untrackedCount ?? 0 }}</p>
                <button v-for="file in state.gitStatus?.files ?? []" :key="`${file.rawStatus}:${file.path}`" type="button" class="team-review-line" @click="controller.openChange(file.path)">
                  <code>{{ file.rawStatus }}</code><span>{{ file.path }}</span><span>+{{ file.additions }} / -{{ file.deletions }}</span>
                </button>
              </details>

              <details class="team-review-group">
                <summary><span>个人提交</span><strong>{{ personalCommits.length }}</strong></summary>
                <button v-for="item in personalCommits" :key="`personal:${item.commit}`" type="button" class="team-review-line" @click="controller.selectCommit(item, 'PERSONAL')">
                  <code>{{ item.commit.slice(0, 10) }}</code><span>{{ item.subject }}</span>
                </button>
              </details>

              <details class="team-review-group">
                <summary><span>已发布提交</span><strong>{{ publishedCommits.length }}</strong></summary>
                <p v-if="state.attributionMessage" class="team-review-hint">{{ state.attributionMessage }}</p>
                <button v-for="item in publishedCommits" :key="`published:${item.commit}`" type="button" class="team-review-line" @click="controller.selectCommit(item, 'PUBLISHED')">
                  <code>{{ item.commit.slice(0, 10) }}</code><span>{{ item.subject }}</span>
                </button>
              </details>

              <details class="team-review-group">
                <summary><span>同步提交</span><strong>{{ syncCommits.length }}</strong></summary>
                <button v-for="item in syncCommits" :key="`sync:${item.commit}`" type="button" class="team-review-line" @click="controller.selectCommit(item, commitKind(item))">
                  <code>{{ item.commit.slice(0, 10) }}</code><span>{{ item.subject }}</span><span>SYNC_MERGE</span>
                </button>
              </details>

              <section v-if="state.selectedCommit" class="team-review-commit-detail">
                <h3>提交文件 · {{ state.selectedCommit.subject }}</h3>
                <button v-for="file in state.commitFiles" :key="file.path" type="button" class="team-review-line" @click="controller.openCommitFile(file.path)">
                  <code>{{ file.status }}</code><span>{{ file.path }}</span>
                </button>
              </section>
            </div>
          </div>
        </section>
      </div>
    </Teleport>

    <Teleport to="body">
      <button
        v-if="!props.rightPanelOpen && state.active"
        type="button"
        class="team-review-launcher"
        aria-label="打开团队审阅"
        @click="controller.openReviewDialog()"
      >
        <span class="team-review-launcher-dot" aria-hidden="true" />
        <span>{{ state.selectedUserId ? (state.reviewRoster.find((item) => item.userId === state.selectedUserId)?.username ?? "团队审阅") : "团队审阅" }}</span>
      </button>
    </Teleport>

    <Teleport to="body">
      <div v-if="state.teamPickerDialogOpen" class="team-dialog-backdrop" @click.self="controller.closeTeamPickerDialog()">
        <section class="team-dialog team-dialog--compact" role="dialog" aria-modal="true" aria-label="选择系统管理员团队" @keydown="handleDialogKeydown($event, 'team-picker')">
          <header class="team-dialog-header">
            <div><span class="team-review-kicker">成员管理</span><h2>选择系统管理员团队</h2></div>
            <button type="button" aria-label="关闭团队选择" autofocus @click="controller.closeTeamPickerDialog()">关闭</button>
          </header>
          <p class="team-dialog-description">全平台只读只能查看审阅内容。请选择一个系统管理员团队后再维护成员。</p>
          <p v-if="state.ownersLoading" class="team-review-empty" role="status">正在读取可管理的系统管理员团队…</p>
          <p v-if="state.ownersError" class="team-review-error" role="alert">
            {{ state.ownersError }}
            <button type="button" @click="controller.retryOwners()">重试</button>
          </p>
          <div class="team-owner-list">
            <button v-for="owner in state.owners" :key="owner.userId" type="button" class="team-owner-option" @click="controller.selectMemberManagementOwner(owner.userId)">
              <span class="team-review-person-avatar" aria-hidden="true">{{ memberInitials(owner.username) }}</span>
              <span><strong>{{ owner.username }}</strong><small>{{ owner.unifiedAuthId }} · {{ owner.department || "未填写部门" }}</small></span>
              <span aria-hidden="true">›</span>
            </button>
            <p v-if="!state.ownersLoading && !state.ownersError && !state.owners.length" class="team-review-empty">暂无可管理的系统管理员团队。</p>
          </div>
        </section>
      </div>

      <div v-if="state.memberDialogOpen" class="team-dialog-backdrop" @click.self="controller.closeMemberDialog()">
        <section class="team-dialog team-dialog--members" role="dialog" aria-modal="true" aria-label="成员管理" @keydown="handleDialogKeydown($event, 'members')">
          <header class="team-dialog-header">
            <div><span class="team-review-kicker">{{ scopeDescription }}</span><h2>成员管理</h2></div>
            <button type="button" aria-label="关闭成员管理" autofocus @click="controller.closeMemberDialog()">关闭</button>
          </header>
          <p v-if="!canMaintain" class="team-review-hint">{{ TEAM_MEMBER_SCOPE_HINT }}</p>
          <template v-else>
            <form class="team-member-search" @submit.prevent>
              <input aria-label="搜索组员" placeholder="搜索姓名、统一认证号或部门" :value="state.memberKeyword" @input="onMemberKeyword">
            </form>
            <div class="team-member-add">
              <input aria-label="搜索要添加的用户" placeholder="搜索要添加的用户" @input="controller.searchCandidates(($event.target as HTMLInputElement).value)">
              <select aria-label="选择要添加的用户" :value="state.candidateUserId" @change="controller.chooseCandidate(($event.target as HTMLSelectElement).value)">
                <option value="">选择用户</option>
                <option v-for="candidate in state.candidates" :key="candidate.userId" :value="candidate.userId">{{ candidate.username }} · {{ candidate.unifiedAuthId }}</option>
              </select>
              <button type="button" :disabled="!state.candidateUserId || state.mutationLoading" @click="controller.addMember()">添加组员</button>
            </div>
            <div class="team-member-list">
              <p v-if="state.memberLoading">正在读取成员…</p>
              <article v-for="member in state.members" :key="member.userId" class="team-member-row">
                <div class="team-member-identity"><span class="team-review-person-avatar" aria-hidden="true">{{ memberInitials(member.username) }}</span><span><strong>{{ member.username }}</strong><small>{{ member.unifiedAuthId }} · {{ member.department || "未填写部门" }}</small></span></div>
                <button type="button" :disabled="state.mutationLoading" @click="controller.removeMember(member.userId)">移除</button>
              </article>
              <p v-if="!state.memberLoading && !state.members.length">没有匹配的组员。</p>
            </div>
            <div class="team-member-pager">
              <button type="button" :disabled="state.memberPage <= 1" @click="controller.setMemberPage(state.memberPage - 1)">上一页</button>
              <span>{{ state.memberPage }} / {{ Math.max(1, Math.ceil(state.memberTotal / state.memberPageSize)) }}</span>
              <button type="button" :disabled="state.memberPage * state.memberPageSize >= state.memberTotal" @click="controller.setMemberPage(state.memberPage + 1)">下一页</button>
            </div>
          </template>
        </section>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.team-review-host { display: flex; flex: 1 1 auto; flex-direction: column; height: 100%; min-height: 0; }
.team-review-panel-host { height: 100%; min-height: 0; }
.team-review, .team-dialog {
  color: var(--ta-shell-text, #202533);
  background: var(--ta-shell-surface, #fff);
  border: 1px solid var(--ta-shell-border, #e4e7ec);
  box-shadow: 0 12px 32px rgba(29, 37, 51, .12);
}
.team-review { display: flex; flex-direction: column; height: 100%; min-height: 0; border-width: 0 0 0 1px; box-shadow: none; }
.team-review--dialog { width: min(780px, calc(100vw - 32px)); height: min(760px, calc(100vh - 32px)); border-radius: 16px; overflow: hidden; }
.team-review-header, .team-dialog-header, .team-review-actions, .team-review-export, .team-member-add, .team-member-pager, .team-member-row { display: flex; align-items: center; gap: 8px; }
.team-review-header { justify-content: space-between; padding: 16px 16px 10px; border-bottom: 1px solid var(--ta-shell-border, #e4e7ec); background: var(--ta-shell-surface-subtle, #fafbfc); }
.team-review-heading { min-width: 0; }
.team-review-title-row { display: flex; align-items: center; flex-wrap: wrap; gap: 7px; }
.team-review-header h2, .team-dialog h2, .team-review-detail h3 { margin: 0; font-weight: 650; }
.team-review-header h2, .team-dialog h2 { font-size: 16px; }
.team-review-kicker { color: var(--ta-shell-muted, #697386); font-size: 11px; letter-spacing: .04em; }
.team-review-scope-chip, .team-review-person-badge { color: var(--ta-shell-accent, #c8161d); background: color-mix(in srgb, var(--ta-shell-accent, #c8161d) 9%, white); border-radius: 999px; padding: 2px 7px; font-size: 11px; }
.team-review-subtitle, .team-dialog-description, .team-review-hint, .team-review-empty, .team-review-metrics, .team-member-row small { color: var(--ta-shell-muted, #697386); font-size: 12px; line-height: 1.5; }
.team-review-subtitle { margin: 4px 0 0; }
.team-review-scope, .team-review-scope-locked { display: flex; flex-direction: column; gap: 4px; margin: 9px 0 0; font-size: 12px; }
.team-review-scope-locked { color: var(--ta-shell-muted, #697386); }
.team-review-scope select, .team-dialog input, .team-dialog select { width: 100%; height: 32px; border: 1px solid var(--ta-shell-border-strong, #cfd5df); border-radius: 7px; background: #fff; font-size: 13px; }
.team-review-actions { flex-shrink: 0; align-self: flex-start; }
.team-review button, .team-dialog button { border: 1px solid var(--ta-shell-border-strong, #cfd5df); border-radius: 7px; background: #fff; color: inherit; padding: 6px 9px; font-size: 13px; cursor: pointer; }
.team-review button:hover, .team-dialog button:hover { border-color: var(--ta-shell-accent, #c8161d); }
.team-review button:disabled, .team-dialog button:disabled { cursor: not-allowed; opacity: .45; }
.team-review-action-button--primary { color: #fff !important; border-color: var(--ta-shell-accent, #c8161d) !important; background: var(--ta-shell-accent, #c8161d) !important; }
.team-review-close-button { white-space: nowrap; }
.team-review button:focus-visible, .team-dialog button:focus-visible, .team-dialog input:focus-visible, .team-dialog select:focus-visible, .team-review select:focus-visible { outline: 2px solid var(--ta-shell-accent, #c8161d); outline-offset: 2px; }
.team-review-context-strip { display: flex; align-items: center; gap: 7px; padding: 8px 16px; border-bottom: 1px solid var(--ta-shell-border, #e4e7ec); font-size: 12px; }
.team-review-context-label { color: var(--ta-shell-muted, #697386); }
.team-review-context-member { color: var(--ta-shell-muted, #697386); }
.team-review-error { display: flex; justify-content: space-between; gap: 8px; margin: 9px 16px 0; color: var(--ta-shell-accent, #c8161d); font-size: 13px; }
.team-review-export { padding: 8px 16px; font-size: 12px; }
.team-review-export progress { flex: 1; height: 6px; }
.team-review-scroll { flex: 1; min-height: 0; overflow: auto; padding: 0 8px 16px; }
.team-review-person, .team-review-line { display: grid; width: 100%; border: 0 !important; border-radius: 0 !important; background: transparent !important; text-align: left; }
.team-review-person { grid-template-columns: auto minmax(0, 1fr) auto; gap: 8px; padding: 10px 8px; border-bottom: 1px solid var(--ta-shell-border, #e4e7ec) !important; }
.team-review-person.is-active { box-shadow: inset 3px 0 0 var(--ta-shell-accent, #c8161d); background: var(--ta-shell-surface-subtle, #fafbfc) !important; }
.team-review-person-avatar { display: inline-grid; flex: 0 0 auto; place-items: center; width: 30px; height: 30px; border-radius: 50%; color: var(--ta-shell-accent, #c8161d); background: color-mix(in srgb, var(--ta-shell-accent, #c8161d) 11%, white); font-size: 12px; font-weight: 650; }
.team-review-person-copy { display: grid; min-width: 0; grid-template-columns: auto auto; align-items: center; gap: 2px 7px; }
.team-review-person-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 650; }
.team-review-person-badge { justify-self: start; }
.team-review-person-stats { grid-column: 1 / -1; color: var(--ta-shell-muted, #697386); font-size: 11px; }
.team-review-person-chevron { align-self: center; color: var(--ta-shell-muted, #697386); font-size: 20px; }
.team-review-detail { padding: 4px 8px; }
.team-review-group { border-bottom: 1px solid var(--ta-shell-border, #e4e7ec); }
.team-review-group > summary { display: flex; align-items: center; justify-content: space-between; padding: 12px 0 8px; cursor: pointer; list-style: none; font-size: 13px; font-weight: 650; }
.team-review-group > summary::-webkit-details-marker { display: none; }
.team-review-group > summary::before { content: "⌄"; width: 16px; color: var(--ta-shell-muted, #697386); }
.team-review-group:not([open]) > summary::before { content: "›"; }
.team-review-group > summary span { flex: 1; }
.team-review-line { grid-template-columns: auto minmax(0, 1fr) auto; gap: 8px; padding: 7px 0; border-bottom: 1px solid var(--ta-shell-border, #eef0f3) !important; font-size: 13px; }
.team-review-line span:nth-child(2) { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.team-review-commit-detail { padding-top: 12px; }
.team-dialog-backdrop, .team-review-modal-backdrop { position: fixed; inset: 0; z-index: 80; display: grid; place-items: center; padding: 16px; background: rgba(25, 31, 43, .26); backdrop-filter: blur(7px); }
.team-dialog { width: min(560px, calc(100vw - 32px)); max-height: min(720px, calc(100vh - 32px)); display: flex; flex-direction: column; gap: 14px; padding: 18px; border-radius: 16px; overflow: hidden; }
.team-dialog--compact { width: min(480px, calc(100vw - 32px)); }
.team-dialog-header { justify-content: space-between; }
.team-dialog-header h2 { margin-top: 3px; }
.team-owner-list, .team-member-search, .team-member-add, .team-member-list { display: flex; flex-direction: column; gap: 8px; }
.team-owner-list, .team-member-list { min-height: 0; overflow: auto; }
.team-owner-option { display: grid !important; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: 10px; width: 100%; padding: 10px !important; text-align: left; }
.team-owner-option > span:nth-child(2), .team-member-identity > span:last-child { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.team-owner-option small, .team-member-identity small { color: var(--ta-shell-muted, #697386); font-size: 11px; }
.team-member-add { align-items: stretch; }
.team-member-add button { align-self: flex-start; }
.team-member-list { flex: 1; }
.team-member-row { justify-content: space-between; padding: 8px 0; border-bottom: 1px solid var(--ta-shell-border, #e4e7ec); }
.team-member-identity { display: flex; align-items: center; gap: 9px; min-width: 0; }
.team-member-pager { justify-content: flex-end; }
.team-review-launcher { position: fixed; top: 50%; right: 10px; z-index: 70; display: inline-flex; align-items: center; gap: 7px; max-width: 148px; padding: 10px 12px; border: 1px solid var(--ta-shell-border-strong, #cfd5df); border-radius: 999px 0 0 999px; background: var(--ta-shell-surface, #fff); color: var(--ta-shell-text, #202533); box-shadow: 0 8px 24px rgba(29, 37, 51, .14); transform: translateY(-50%); cursor: pointer; }
.team-review-launcher-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--ta-shell-accent, #c8161d); }
@media (prefers-reduced-motion: reduce) { .team-review-launcher, .team-review, .team-dialog { transition: none; } }
</style>
