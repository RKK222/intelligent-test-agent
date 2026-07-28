<script setup lang="ts">
import { computed, inject, onMounted, ref } from "vue";
import { AlertTriangle, CheckCircle2, GitPullRequest, Loader2, RefreshCw } from "lucide-vue-next";
import type { BackendApiClient } from "@test-agent/backend-api";
import { BackendApiError } from "@test-agent/backend-api";
import type {
  ApplicationGitRefreshScope,
  ApplicationGitRefreshScopeGroup,
  ApplicationGitRefreshResult,
  CurrentUser
} from "@test-agent/shared-types";

const props = defineProps<{
  currentUser: CurrentUser | null;
}>();

const api = inject<BackendApiClient>("api")!;

const applications = ref<ApplicationGitRefreshScope[]>([]);
const loading = ref(false);
const refreshingOperation = ref<string | null>(null);
const errorMessage = ref("");
const results = ref<Record<string, ApplicationGitRefreshResult>>({});
const hasSuperAdmin = computed(() => props.currentUser?.roles?.includes("SUPER_ADMIN") === true);

onMounted(() => {
  if (hasSuperAdmin.value) void loadApplications();
});

/** 配置管理使用平台应用主数据，不要求超级管理员先加入应用或启动个人 OpenCode。 */
async function loadApplications() {
  loading.value = true;
  errorMessage.value = "";
  try {
    applications.value = await api.listApplicationGitRefreshScopes();
  } catch (error) {
    errorMessage.value = formatError(error, "加载应用失败");
  } finally {
    loading.value = false;
  }
}

async function refreshApplication(application: ApplicationGitRefreshScope) {
  if (refreshingOperation.value) return;
  const branches = [...new Set(application.groups.map((group) => group.branch))];
  const scopeSummary = branches.length > 0
    ? `${application.totalGroups} 个 feature 分支组（${branches.join("、")}）`
    : "0 个 feature 分支组";
  const confirmed = window.confirm(
    `将刷新应用“${application.appName}”关联的全部 ${scopeSummary}，并把固定提交安全合入相关个人 worktree。`
      + "不会 stash、reset 或覆盖个人本地修改；存在覆盖风险或冲突的 worktree 会保留待处理。是否继续？"
  );
  if (!confirmed) return;

  const operationKey = `application:${application.appId}`;
  refreshingOperation.value = operationKey;
  errorMessage.value = "";
  try {
    const result = await api.refreshApplicationGit(application.appId);
    results.value = { ...results.value, [application.appId]: result };
  } catch (error) {
    errorMessage.value = formatError(error, `刷新应用 ${application.appName} 失败`);
  } finally {
    refreshingOperation.value = null;
  }
}

/** 单独刷新一个物理分支组；相同组下的多个工作空间目录会一起安全收敛。 */
async function refreshGroup(application: ApplicationGitRefreshScope, group: ApplicationGitRefreshScopeGroup) {
  if (refreshingOperation.value) return;
  const workspaceNames = group.workspaces.map((workspace) => workspace.workspaceName).join("、");
  const confirmed = window.confirm(
    `将只刷新应用“${application.appName}”的分支 ${group.branch}（${group.repositoryName} / 版本 ${group.version}，工作空间：${workspaceNames}），`
      + "并把固定提交安全合入该分支相关个人 worktree。不会影响同应用其它分支，也不会 stash、reset 或覆盖个人本地修改。是否继续？"
  );
  if (!confirmed) return;

  const operationKey = groupOperationKey(application, group);
  refreshingOperation.value = operationKey;
  errorMessage.value = "";
  try {
    const result = await api.refreshApplicationGitGroup(application.appId, {
      repositoryId: group.repositoryId,
      version: group.version,
      branch: group.branch
    });
    results.value = { ...results.value, [application.appId]: result };
  } catch (error) {
    errorMessage.value = formatError(error, `刷新分支 ${group.branch} 失败`);
  } finally {
    refreshingOperation.value = null;
  }
}

function groupOperationKey(application: ApplicationGitRefreshScope, group: ApplicationGitRefreshScopeGroup) {
  return `group:${application.appId}:${group.repositoryId}:${group.version}:${group.branch}`;
}

function resultSummary(result: ApplicationGitRefreshResult) {
  if (result.totalGroups === 0) return "暂无已创建的 feature 仓库组";
  const succeeded = result.updatedGroups + result.unchangedGroups;
  return `共 ${result.totalGroups} 组：成功 ${succeeded}，更新 ${result.updatedGroups}，已是最新 ${result.unchangedGroups}，失败 ${result.failedGroups}`;
}

function shortHash(value?: string | null) {
  return value ? value.slice(0, 10) : "-";
}

function formatError(error: unknown, fallback: string) {
  if (error instanceof BackendApiError) {
    return `${fallback}：${error.message}（traceId: ${error.traceId}）`;
  }
  return error instanceof Error ? `${fallback}：${error.message}` : fallback;
}
</script>

<template>
  <section class="ta-app-git-refresh">
    <div v-if="!hasSuperAdmin" class="ta-app-git-placeholder">当前账号无配置管理权限</div>
    <template v-else>
      <header class="ta-app-git-header">
        <div>
          <h2>应用 Git 刷新</h2>
          <p>可按单个实际分支或整个应用刷新物理 feature 仓库组，并安全收敛相关个人 worktree 与应用配置。</p>
        </div>
        <button type="button" class="ta-app-git-btn" :disabled="loading || !!refreshingOperation" @click="loadApplications">
          <Loader2 v-if="loading" class="ta-app-git-icon is-spin" />
          <RefreshCw v-else class="ta-app-git-icon" :stroke-width="1.6" />
          刷新列表
        </button>
      </header>

      <div v-if="errorMessage" class="ta-app-git-alert" role="alert">
        <AlertTriangle class="ta-app-git-icon" :stroke-width="1.6" />
        <span>{{ errorMessage }}</span>
      </div>

      <div class="ta-app-git-table-wrap">
        <table class="ta-app-git-table">
          <thead>
            <tr><th>应用</th><th>工作空间 / 版本 / 分支</th><th>状态</th><th>刷新结果</th><th>操作</th></tr>
          </thead>
          <tbody>
            <tr v-if="applications.length === 0 && !loading">
              <td colspan="5" class="ta-app-git-empty">暂无应用</td>
            </tr>
            <tr v-for="application in applications" :key="application.appId">
              <td>
                <strong>{{ application.appName }}</strong>
                <small>{{ application.appId }}</small>
              </td>
              <td>
                <span v-if="application.groups.length === 0" class="ta-app-git-muted">暂无已创建的 feature 分支</span>
                <ul v-else class="ta-app-git-scope-list">
                  <li
                    v-for="group in application.groups"
                    :key="`${group.repositoryId}:${group.version}:${group.branch}`"
                  >
                    <div>
                      <code>{{ group.branch }}</code>
                      <span>{{ group.repositoryName }} · 版本 {{ group.version }}</span>
                      <small>
                        {{ group.workspaces.map((workspace) => `${workspace.workspaceName}${workspace.enabled ? '' : '（停用）'}`).join('、') }}
                      </small>
                    </div>
                    <button
                      type="button"
                      class="ta-app-git-btn is-compact"
                      :disabled="!!refreshingOperation"
                      :aria-label="`刷新分支：${application.appName} / ${group.branch}`"
                      @click="refreshGroup(application, group)"
                    >
                      <Loader2 v-if="refreshingOperation === groupOperationKey(application, group)" class="ta-app-git-icon is-spin" />
                      <GitPullRequest v-else class="ta-app-git-icon" :stroke-width="1.6" />
                      {{ refreshingOperation === groupOperationKey(application, group) ? "正在刷新" : "刷新该分支" }}
                    </button>
                  </li>
                </ul>
              </td>
              <td><span :class="['ta-app-git-state', application.enabled ? 'is-ready' : 'is-disabled']">{{ application.enabled ? '启用' : '停用' }}</span></td>
              <td>
                <span v-if="!results[application.appId]" class="ta-app-git-muted">尚未执行</span>
                <div v-else class="ta-app-git-result">
                  <div :class="results[application.appId].failedGroups > 0 ? 'is-warning' : 'is-success'">
                    <AlertTriangle v-if="results[application.appId].failedGroups > 0" class="ta-app-git-icon" />
                    <CheckCircle2 v-else class="ta-app-git-icon" />
                    {{ resultSummary(results[application.appId]) }}
                  </div>
                  <details v-if="results[application.appId].groups.length > 0">
                    <summary>查看仓库组明细</summary>
                    <ul>
                      <li v-for="group in results[application.appId].groups" :key="`${group.repositoryId}:${group.version}:${group.branch}`">
                        <span>{{ group.repositoryName }} · {{ group.version }} · {{ group.branch }} · {{ group.workspaceCount }} 个 workspace</span>
                        <code>{{ shortHash(group.previousCommitHash) }} → {{ shortHash(group.commitHash) }}</code>
                        <em :class="`is-${group.status.toLowerCase()}`">{{ group.message }}<template v-if="group.errorCode">（{{ group.errorCode }}）</template></em>
                      </li>
                    </ul>
                  </details>
                </div>
              </td>
              <td>
                <button
                  type="button"
                  class="ta-app-git-btn is-primary"
                  :disabled="!!refreshingOperation"
                  :aria-label="`刷新应用 Git：${application.appName}`"
                  @click="refreshApplication(application)"
                >
                  <Loader2 v-if="refreshingOperation === `application:${application.appId}`" class="ta-app-git-icon is-spin" />
                  <GitPullRequest v-else class="ta-app-git-icon" :stroke-width="1.6" />
                  {{ refreshingOperation === `application:${application.appId}` ? "正在刷新" : "刷新全部分支" }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </section>
</template>

<style scoped>
.ta-app-git-refresh { display: flex; min-height: 0; height: 100%; flex-direction: column; gap: 12px; padding: 16px; box-sizing: border-box; overflow: auto; background: #f7f8fa; color: #1f2937; }
.ta-app-git-placeholder, .ta-app-git-empty { color: #6b7280; }
.ta-app-git-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.ta-app-git-header h2 { margin: 0; font-size: 17px; }
.ta-app-git-header p { max-width: 780px; margin: 6px 0 0; color: #6b7280; font-size: 12px; line-height: 1.6; }
.ta-app-git-btn { display: inline-flex; align-items: center; justify-content: center; gap: 6px; min-height: 30px; padding: 0 10px; border: 1px solid #d1d5db; border-radius: 6px; background: #fff; color: #374151; cursor: pointer; white-space: nowrap; }
.ta-app-git-btn.is-primary { border-color: #2563eb; background: #2563eb; color: #fff; }
.ta-app-git-btn:disabled { cursor: not-allowed; opacity: .55; }
.ta-app-git-icon { width: 15px; height: 15px; flex: 0 0 auto; }
.is-spin { animation: ta-app-git-spin 1s linear infinite; }
.ta-app-git-alert { display: flex; align-items: center; gap: 8px; padding: 10px 12px; border: 1px solid #fecaca; border-radius: 6px; background: #fef2f2; color: #b91c1c; font-size: 12px; }
.ta-app-git-table-wrap { overflow: auto; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; }
.ta-app-git-table { width: 100%; min-width: 1080px; border-collapse: collapse; font-size: 12px; }
.ta-app-git-table th, .ta-app-git-table td { padding: 10px 12px; border-bottom: 1px solid #e5e7eb; text-align: left; vertical-align: top; }
.ta-app-git-table th { background: #f9fafb; color: #4b5563; font-weight: 600; }
.ta-app-git-table td:first-child strong, .ta-app-git-table td:first-child small { display: block; }
.ta-app-git-table td:first-child small, .ta-app-git-muted { margin-top: 3px; color: #6b7280; }
.ta-app-git-state { display: inline-flex; padding: 2px 7px; border-radius: 999px; }
.ta-app-git-state.is-ready { background: #ecfdf5; color: #047857; }
.ta-app-git-state.is-disabled { background: #f3f4f6; color: #6b7280; }
.ta-app-git-scope-list { display: grid; gap: 8px; min-width: 330px; margin: 0; padding: 0; list-style: none; }
.ta-app-git-scope-list li { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: start; gap: 8px; }
.ta-app-git-scope-list li > div { display: grid; gap: 2px; }
.ta-app-git-scope-list code { color: #1d4ed8; font-weight: 600; }
.ta-app-git-scope-list span { color: #475569; }
.ta-app-git-scope-list small { color: #64748b; }
.ta-app-git-btn.is-compact { min-height: 26px; padding: 0 8px; font-size: 11px; }
.ta-app-git-result > div { display: flex; align-items: center; gap: 6px; }
.ta-app-git-result .is-success { color: #047857; }
.ta-app-git-result .is-warning { color: #b45309; }
.ta-app-git-result details { margin-top: 6px; }
.ta-app-git-result summary { cursor: pointer; color: #2563eb; }
.ta-app-git-result ul { display: grid; gap: 8px; margin: 8px 0 0; padding: 0; list-style: none; }
.ta-app-git-result li { display: grid; grid-template-columns: minmax(260px, 1fr) auto; gap: 3px 12px; padding: 8px; border-radius: 5px; background: #f9fafb; }
.ta-app-git-result code { color: #475569; }
.ta-app-git-result em { grid-column: 1 / -1; color: #64748b; font-style: normal; }
.ta-app-git-result em.is-failed { color: #b91c1c; }
@keyframes ta-app-git-spin { to { transform: rotate(360deg); } }
</style>
