<script setup lang="ts">
import { computed, inject, onMounted, ref } from "vue";
import { AlertTriangle, CheckCircle2, GitPullRequest, Loader2, RefreshCw } from "lucide-vue-next";
import type { BackendApiClient } from "@test-agent/backend-api";
import { BackendApiError } from "@test-agent/backend-api";
import type {
  ApplicationDefinition,
  ApplicationGitRefreshResult,
  CurrentUser
} from "@test-agent/shared-types";

const props = defineProps<{
  currentUser: CurrentUser | null;
}>();

const api = inject<BackendApiClient>("api")!;

const applications = ref<ApplicationDefinition[]>([]);
const loading = ref(false);
const refreshingAppId = ref<string | null>(null);
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
    applications.value = await api.listApplications(false);
  } catch (error) {
    errorMessage.value = formatError(error, "加载应用失败");
  } finally {
    loading.value = false;
  }
}

async function refreshApplication(application: ApplicationDefinition) {
  if (refreshingAppId.value) return;
  const confirmed = window.confirm(
    `将刷新应用“${application.appName}”关联的全部 feature 仓库组，并把固定提交安全合入相关个人 worktree。`
      + "不会 stash、reset 或覆盖个人本地修改；存在覆盖风险或冲突的 worktree 会保留待处理。是否继续？"
  );
  if (!confirmed) return;

  refreshingAppId.value = application.appId;
  errorMessage.value = "";
  try {
    const result = await api.refreshApplicationGit(application.appId);
    results.value = { ...results.value, [application.appId]: result };
  } catch (error) {
    errorMessage.value = formatError(error, `刷新应用 ${application.appName} 失败`);
  } finally {
    refreshingAppId.value = null;
  }
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
          <p>按应用刷新全部物理 feature 仓库组，并触发各服务器相关个人 worktree 的安全合并与应用配置重载。</p>
        </div>
        <button type="button" class="ta-app-git-btn" :disabled="loading || !!refreshingAppId" @click="loadApplications">
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
            <tr><th>应用</th><th>状态</th><th>刷新结果</th><th>操作</th></tr>
          </thead>
          <tbody>
            <tr v-if="applications.length === 0 && !loading">
              <td colspan="4" class="ta-app-git-empty">暂无应用</td>
            </tr>
            <tr v-for="application in applications" :key="application.appId">
              <td>
                <strong>{{ application.appName }}</strong>
                <small>{{ application.appId }}</small>
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
                  :disabled="!!refreshingAppId"
                  :aria-label="`刷新应用 Git：${application.appName}`"
                  @click="refreshApplication(application)"
                >
                  <Loader2 v-if="refreshingAppId === application.appId" class="ta-app-git-icon is-spin" />
                  <GitPullRequest v-else class="ta-app-git-icon" :stroke-width="1.6" />
                  {{ refreshingAppId === application.appId ? "正在刷新" : "刷新应用 Git" }}
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
.ta-app-git-table { width: 100%; min-width: 900px; border-collapse: collapse; font-size: 12px; }
.ta-app-git-table th, .ta-app-git-table td { padding: 10px 12px; border-bottom: 1px solid #e5e7eb; text-align: left; vertical-align: top; }
.ta-app-git-table th { background: #f9fafb; color: #4b5563; font-weight: 600; }
.ta-app-git-table td:first-child strong, .ta-app-git-table td:first-child small { display: block; }
.ta-app-git-table td:first-child small, .ta-app-git-muted { margin-top: 3px; color: #6b7280; }
.ta-app-git-state { display: inline-flex; padding: 2px 7px; border-radius: 999px; }
.ta-app-git-state.is-ready { background: #ecfdf5; color: #047857; }
.ta-app-git-state.is-disabled { background: #f3f4f6; color: #6b7280; }
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
