<script setup lang="ts">
import { computed } from "vue";
import type { TeamCommit, TeamExportStatus } from "@test-agent/shared-types";
import {
  canMaintainTeamMembers,
  TEAM_MEMBER_SCOPE_HINT,
  teamScopeLabel
} from "./team-management-controller";
import { useTeamManagementView } from "./useTeamManagementView";

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

async function download() {
  const url = await controller.downloadExport();
  if (url) window.location.assign(url);
}
</script>

<template>
  <section class="team-review" aria-label="团队审阅">
    <header class="team-review-header">
      <div>
        <h2>审阅</h2>
        <p v-if="state.scopeLocked" class="team-review-hint">{{ teamScopeLabel(state) }}</p>
        <label v-else class="team-review-scope">
          <span>团队</span>
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
        <p v-if="!canMaintain" class="team-review-hint">{{ TEAM_MEMBER_SCOPE_HINT }}</p>
      </div>
      <div class="team-review-actions">
        <button type="button" @click="controller.openMemberDrawer()">成员管理</button>
        <button type="button" :disabled="!state.selectedVersionId" @click="controller.createExport()">整组导出</button>
      </div>
    </header>

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
      <button
        v-if="state.exportJob.status === 'QUEUED' || state.exportJob.status === 'RUNNING'"
        type="button"
        @click="controller.cancelExport()"
      >取消</button>
      <button
        v-if="state.exportJob.status === 'READY' || state.exportJob.status === 'PARTIAL_READY'"
        type="button"
        @click="download"
      >下载 ZIP</button>
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
        <span class="team-review-person-name">{{ item.username }}</span>
        <span v-if="membershipText(item.userId)">{{ membershipText(item.userId) }}</span>
        <span class="team-review-person-stats">{{ statsText(item.userId) }}</span>
      </button>

      <div v-if="state.selectedUserId" class="team-review-detail">
        <p v-if="state.missingDefaultWorkspace" class="team-review-hint">这个版本还没有名为 default 的个人工作空间。</p>

        <section>
          <h3>未提交修改 {{ state.gitStatus?.files.length ?? 0 }}</h3>
          <p class="team-review-metrics">
            已暂存 {{ state.gitStatus?.stagedCount ?? 0 }} · 未暂存 {{ state.gitStatus?.unstagedCount ?? 0 }} · 未跟踪 {{ state.gitStatus?.untrackedCount ?? 0 }}
          </p>
          <button
            v-for="file in state.gitStatus?.files ?? []"
            :key="`${file.rawStatus}:${file.path}`"
            type="button"
            class="team-review-line"
            @click="controller.openChange(file.path)"
          >
            <code>{{ file.rawStatus }}</code>
            <span>{{ file.path }}</span>
            <span>+{{ file.additions }} / -{{ file.deletions }}</span>
          </button>
        </section>

        <section>
          <h3>个人提交 {{ personalCommits.length }}</h3>
          <button
            v-for="item in personalCommits"
            :key="`personal:${item.commit}`"
            type="button"
            class="team-review-line"
            @click="controller.selectCommit(item, 'PERSONAL')"
          >
            <code>{{ item.commit.slice(0, 10) }}</code>
            <span>{{ item.subject }}</span>
          </button>
        </section>

        <section>
          <h3>已发布提交 {{ publishedCommits.length }}</h3>
          <p v-if="state.attributionMessage" class="team-review-hint">{{ state.attributionMessage }}</p>
          <button
            v-for="item in publishedCommits"
            :key="`published:${item.commit}`"
            type="button"
            class="team-review-line"
            @click="controller.selectCommit(item, 'PUBLISHED')"
          >
            <code>{{ item.commit.slice(0, 10) }}</code>
            <span>{{ item.subject }}</span>
          </button>
        </section>

        <section>
          <h3>同步提交 {{ syncCommits.length }}</h3>
          <button
            v-for="item in syncCommits"
            :key="`sync:${item.commit}`"
            type="button"
            class="team-review-line"
            @click="controller.selectCommit(item, commitKind(item))"
          >
            <code>{{ item.commit.slice(0, 10) }}</code>
            <span>{{ item.subject }}</span>
            <span>SYNC_MERGE</span>
          </button>
        </section>

        <section v-if="state.selectedCommit">
          <h3>提交文件 · {{ state.selectedCommit.subject }}</h3>
          <button
            v-for="file in state.commitFiles"
            :key="file.path"
            type="button"
            class="team-review-line"
            @click="controller.openCommitFile(file.path)"
          >
            <code>{{ file.status }}</code>
            <span>{{ file.path }}</span>
          </button>
        </section>
      </div>
    </div>

    <div
      v-if="state.memberDrawerOpen"
      class="team-member-drawer"
      role="dialog"
      aria-modal="false"
      aria-label="成员管理"
    >
      <header>
        <h2>成员管理</h2>
        <button type="button" aria-label="关闭成员管理" @click="controller.closeMemberDrawer()">关闭</button>
      </header>
      <p v-if="!canMaintain" class="team-review-hint">{{ TEAM_MEMBER_SCOPE_HINT }}</p>
      <template v-else>
        <form class="team-member-search" @submit.prevent>
          <input
            aria-label="搜索组员"
            placeholder="搜索姓名、统一认证号或部门"
            :value="state.memberKeyword"
            @input="onMemberKeyword"
          >
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
          <button type="button" :disabled="!state.candidateUserId || state.mutationLoading" @click="controller.addMember()">添加组员</button>
        </div>
        <div class="team-member-list">
          <p v-if="state.memberLoading">正在读取成员…</p>
          <article v-for="member in state.members" :key="member.userId" class="team-member-row">
            <div>
              <strong>{{ member.username }}</strong>
              <span>{{ member.unifiedAuthId }}</span>
              <span>{{ member.department || "未填写部门" }}</span>
            </div>
            <button type="button" :disabled="state.mutationLoading" @click="controller.removeMember(member.userId)">移除</button>
          </article>
          <p v-if="!state.memberLoading && !state.members.length">没有匹配的组员。</p>
        </div>
        <div class="team-member-pager">
          <button type="button" :disabled="state.memberPage <= 1" @click="controller.setMemberPage(state.memberPage - 1)">上一页</button>
          <span>{{ state.memberPage }} / {{ Math.max(1, Math.ceil(state.memberTotal / state.memberPageSize)) }}</span>
          <button
            type="button"
            :disabled="state.memberPage * state.memberPageSize >= state.memberTotal"
            @click="controller.setMemberPage(state.memberPage + 1)"
          >下一页</button>
        </div>
      </template>
    </div>
  </section>
</template>

<style scoped>
.team-review {
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: #fff;
  color: var(--ta-shell-text, #111);
}
.team-review-header,
.team-member-drawer header,
.team-review-actions,
.team-review-export,
.team-member-add,
.team-member-pager,
.team-member-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.team-review-header {
  justify-content: space-between;
  padding: 12px 14px 8px;
}
.team-review-header h2,
.team-member-drawer h2,
.team-review-detail h3 {
  margin: 0;
  font-weight: 600;
}
.team-review-header h2,
.team-member-drawer h2 {
  font-size: 16px;
}
.team-review-detail h3 {
  margin-top: 14px;
  font-size: 13px;
}
.team-review-hint,
.team-review-empty,
.team-review-metrics,
.team-member-row span {
  color: var(--ta-shell-muted, #6b7280);
  font-size: 12px;
  line-height: 1.5;
}
.team-review-error {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin: 0 14px 8px;
  color: var(--ta-shell-accent, #c8161d);
  font-size: 13px;
}
.team-review button,
.team-member-drawer button {
  border: 1px solid var(--ta-shell-border-strong, #d1d5db);
  border-radius: 6px;
  background: #fff;
  color: inherit;
  padding: 4px 8px;
  font-size: 13px;
  cursor: pointer;
}
.team-review button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}
.team-review button:focus-visible,
.team-member-drawer button:focus-visible,
.team-member-drawer input:focus-visible,
.team-member-drawer select:focus-visible,
.team-review select:focus-visible {
  outline: 2px solid var(--ta-shell-accent, #c8161d);
  outline-offset: 1px;
}
.team-review-export {
  padding: 0 14px 8px;
  font-size: 12px;
}
.team-review-export progress {
  flex: 1;
  height: 6px;
}
.team-review-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 0 8px 16px;
}
.team-review-person,
.team-review-line {
  display: grid;
  width: 100%;
  border: 0;
  border-radius: 0;
  background: transparent;
  text-align: left;
}
.team-review-person {
  grid-template-columns: 1fr auto;
  gap: 2px 8px;
  padding: 8px;
  border-bottom: 1px solid var(--ta-shell-border, #e5e7eb);
}
.team-review-person.is-active {
  box-shadow: inset 2px 0 0 var(--ta-shell-accent, #c8161d);
}
.team-review-person-name {
  font-weight: 600;
}
.team-review-person-stats {
  grid-column: 1 / -1;
  color: var(--ta-shell-muted, #6b7280);
  font-size: 12px;
}
.team-review-detail {
  padding: 8px;
}
.team-review-scope {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 6px;
  font-size: 12px;
}
.team-review-scope select,
.team-member-drawer input,
.team-member-drawer select {
  width: 100%;
  height: 32px;
  border: 1px solid var(--ta-shell-border-strong, #d1d5db);
  border-radius: 6px;
  background: #fff;
  font-size: 14px;
}
.team-review-line {
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 8px;
  padding: 7px 0;
  border-bottom: 1px solid var(--ta-shell-border, #e5e7eb);
  font-size: 13px;
}
.team-review-line span:nth-child(2) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.team-member-drawer {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px 14px;
  background: #fff;
}
.team-member-drawer header {
  justify-content: space-between;
}
.team-member-search,
.team-member-add,
.team-member-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.team-member-list {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
.team-member-row {
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid var(--ta-shell-border, #e5e7eb);
}
.team-member-row div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.team-member-pager {
  justify-content: flex-end;
}
</style>
