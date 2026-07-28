<script setup lang="ts">
import { computed, inject, onBeforeUnmount, onMounted, ref } from "vue";
import { AlertTriangle, CheckCircle2, GitBranch, Loader2, RefreshCw } from "lucide-vue-next";
import { BackendApiError, type BackendApiClient } from "@test-agent/backend-api";
import type {
  CurrentUser,
  PublicAgentConfigRolloutServerStatus,
  PublicAgentConfigRolloutStatus,
  PublicAgentRepositoryStatus
} from "@test-agent/shared-types";

const props = defineProps<{
  currentUser: CurrentUser | null;
}>();

const api = inject<BackendApiClient>("api")!;
const rows = ref<PublicAgentRepositoryStatus[]>([]);
const rollout = ref<PublicAgentConfigRolloutStatus | null>(null);
const loading = ref(false);
const initializing = ref(false);
const pulling = ref(false);
const errorMessage = ref("");
const successMessage = ref("");
const dialogOpen = ref(false);
const pullDialogOpen = ref(false);
const branchesLoading = ref(false);
const branches = ref<string[]>([]);
const selectedBranch = ref("");
const targetRepository = ref<PublicAgentRepositoryStatus | null>(null);
const initErrorMessage = ref("");
let rolloutTimer: number | null = null;

const hasSuperAdmin = computed(() => props.currentUser?.roles?.includes("SUPER_ADMIN") === true);
const canSubmitInitialize = computed(() => !!targetRepository.value && !!selectedBranch.value && !initializing.value && !branchesLoading.value);
const rolloutActive = computed(() => rollout.value?.status === "PREPARING" || rollout.value?.status === "DRAINING");
const canSubmitPull = computed(() => !!selectedBranch.value && !pulling.value && !branchesLoading.value && !rolloutActive.value);
const dirtyServers = computed(() => rows.value.filter((row) => row.localChangesPresent || row.status === "CONFLICT"));

onMounted(() => {
  if (hasSuperAdmin.value) {
    void refresh();
  }
});

onBeforeUnmount(stopRolloutPolling);

async function refresh() {
  loading.value = true;
  errorMessage.value = "";
  try {
    const [repositories, latestRollout] = await Promise.all([
      api.listPublicAgentRepositories(),
      api.getPublicAgentConfigRollout()
    ]);
    rows.value = repositories;
    rollout.value = latestRollout;
    scheduleRolloutPolling();
  } catch (error) {
    errorMessage.value = formatError(error, "加载公共配置仓库状态失败");
  } finally {
    loading.value = false;
  }
}

async function refreshRollout() {
  try {
    const latest = await api.getPublicAgentConfigRollout();
    const wasActive = rolloutActive.value;
    rollout.value = latest;
    if (wasActive && !rolloutActive.value) {
      rows.value = await api.listPublicAgentRepositories();
    }
    scheduleRolloutPolling();
  } catch (error) {
    errorMessage.value = formatError(error, "刷新公共 Agent 全局同步状态失败");
    scheduleRolloutPolling();
  }
}

function scheduleRolloutPolling() {
  stopRolloutPolling();
  if (rolloutActive.value) {
    rolloutTimer = window.setTimeout(() => void refreshRollout(), 2_000);
  }
}

function stopRolloutPolling() {
  if (rolloutTimer !== null) {
    window.clearTimeout(rolloutTimer);
    rolloutTimer = null;
  }
}

async function openInitializeDialog(repository: PublicAgentRepositoryStatus) {
  if (!repository.initializationAllowed || initializing.value) {
    return;
  }
  targetRepository.value = repository;
  selectedBranch.value = "";
  branches.value = [];
  initErrorMessage.value = "";
  dialogOpen.value = true;
  branchesLoading.value = true;
  try {
    const remoteBranches = await api.listPublicAgentBranches();
    branches.value = remoteBranches;
    selectedBranch.value = preferredBranch(repository, remoteBranches);
  } catch (error) {
    initErrorMessage.value = formatError(error, "加载远端分支失败");
  } finally {
    branchesLoading.value = false;
  }
}

function closeInitializeDialog() {
  if (initializing.value) {
    return;
  }
  dialogOpen.value = false;
  targetRepository.value = null;
  selectedBranch.value = "";
  branches.value = [];
  initErrorMessage.value = "";
}

async function submitInitialize() {
  const repository = targetRepository.value;
  if (!repository || !selectedBranch.value) {
    return;
  }
  initializing.value = true;
  initErrorMessage.value = "";
  successMessage.value = "";
  try {
    const updated = await api.initializePublicAgentRepository(repository.linuxServerId, selectedBranch.value, newOperationId());
    rows.value = rows.value.map((row) => (row.linuxServerId === updated.linuxServerId ? updated : row));
    successMessage.value = `服务器 ${updated.linuxServerId} 公共配置仓库已初始化`;
    dialogOpen.value = false;
    targetRepository.value = null;
    selectedBranch.value = "";
    branches.value = [];
  } catch (error) {
    initErrorMessage.value = formatError(error, "初始化公共配置仓库失败");
  } finally {
    initializing.value = false;
  }
}

async function openPullDialog() {
  if (pulling.value || rolloutActive.value) {
    return;
  }
  selectedBranch.value = "";
  branches.value = [];
  initErrorMessage.value = "";
  pullDialogOpen.value = true;
  branchesLoading.value = true;
  try {
    branches.value = await api.listPublicAgentBranches();
    selectedBranch.value = preferredGlobalBranch(rows.value, branches.value);
  } catch (error) {
    initErrorMessage.value = formatError(error, "加载远端分支失败");
  } finally {
    branchesLoading.value = false;
  }
}

function closePullDialog() {
  if (pulling.value) {
    return;
  }
  pullDialogOpen.value = false;
  selectedBranch.value = "";
  branches.value = [];
  initErrorMessage.value = "";
}

async function submitGlobalPull() {
  if (!selectedBranch.value || pulling.value || rolloutActive.value) {
    return;
  }
  const discardSharedRuntimeChanges = dirtyServers.value.length > 0;
  if (discardSharedRuntimeChanges) {
    const serverIds = dirtyServers.value.map((row) => row.linuxServerId).join("、");
    const confirmed = window.confirm(
      `服务器 ${serverIds} 的共享运行副本存在本地变更。继续将只恢复这些共享副本到远端目标 commit，并删除其中未跟踪文件；个人 worktree 的 staged、unstaged、untracked 内容不会被清理。是否继续？`
    );
    if (!confirmed) {
      return;
    }
  }
  pulling.value = true;
  errorMessage.value = "";
  successMessage.value = "";
  try {
    const operation = await api.updatePublicAgentConfig(
      selectedBranch.value,
      newOperationId(),
      discardSharedRuntimeChanges
    );
    successMessage.value = `已发起所有服务器刷新到远端目标 commit ${shortHash(operation.commitHash)}`;
    pullDialogOpen.value = false;
    rollout.value = await api.getPublicAgentConfigRollout();
    scheduleRolloutPolling();
  } catch (error) {
    errorMessage.value = formatError(error, "发起公共 Agent 全局刷新失败");
  } finally {
    pulling.value = false;
  }
}

function preferredBranch(repository: PublicAgentRepositoryStatus, remoteBranches: string[]) {
  const current = repository.currentBranch?.trim();
  if (current && remoteBranches.includes(current)) {
    return current;
  }
  return remoteBranches[0] ?? current ?? "main";
}

function preferredGlobalBranch(repositories: PublicAgentRepositoryStatus[], remoteBranches: string[]) {
  const current = repositories.find((repository) => repository.currentBranch?.trim())?.currentBranch?.trim();
  if (current && remoteBranches.includes(current)) {
    return current;
  }
  return remoteBranches[0] ?? current ?? "main";
}

function rolloutStatusText(status: string) {
  return ({
    PREPARING: "准备全局刷新",
    DRAINING: "正在同步并排空",
    COMPLETED: "已完成",
    ABORTED: "已终止"
  } as Record<string, string>)[status] ?? status;
}

function serverProgress(server: PublicAgentConfigRolloutServerStatus) {
  if (server.syncStatus !== "SYNCED") {
    return server.syncStatus;
  }
  if (server.targetPending > 0) {
    return `排空中（剩余 ${server.targetPending}/${server.targetTotal}）`;
  }
  if (server.targetAbandoned > 0) {
    return `已同步，${server.targetAbandoned} 个运行目标已放弃`;
  }
  return "已同步并排空";
}

function worktreeProgress(server: PublicAgentConfigRolloutServerStatus) {
  const total = server.worktreeTotal ?? 0;
  const synced = server.worktreeSynced ?? 0;
  const pending = server.worktreePending ?? 0;
  if (total === 0) {
    return "无待处理补偿";
  }
  const summary = `补偿已收敛 ${synced}/${total}`;
  return pending > 0 ? `${summary}，待用户处理 ${pending}` : summary;
}

function statusText(row: PublicAgentRepositoryStatus) {
  if (row.status === "CONFLICT" && row.initialized) {
    return "存在本地变更";
  }
  if (row.initialized) {
    return "已初始化";
  }
  if (row.status === "UNAVAILABLE") {
    return "后端不可用";
  }
  if (row.status === "INVALID") {
    return "状态异常";
  }
  return "未初始化";
}

function statusClass(row: PublicAgentRepositoryStatus) {
  if (row.status === "CONFLICT") {
    return "is-error";
  }
  if (row.initialized) {
    return "is-ready";
  }
  if (row.status === "UNAVAILABLE" || row.status === "INVALID") {
    return "is-error";
  }
  return "is-pending";
}

function shortHash(value?: string | null) {
  return value ? value.slice(0, 10) : "-";
}

function formatNullable(value?: string | null) {
  return value && value.trim() ? value : "-";
}

function formatError(error: unknown, fallback: string) {
  if (error instanceof BackendApiError) {
    return `${fallback}：${error.message}（traceId: ${error.traceId}）`;
  }
  return error instanceof Error ? `${fallback}：${error.message}` : fallback;
}

function newOperationId() {
  const random = typeof crypto !== "undefined" && "randomUUID" in crypto
    ? crypto.randomUUID().replaceAll("-", "")
    : `${Date.now().toString(36)}${Math.random().toString(36).slice(2)}`;
  return `aco_${random}`;
}
</script>

<template>
  <section class="ta-opencode-config">
    <div v-if="!hasSuperAdmin" class="ta-opencode-config-placeholder">当前账号无配置管理权限</div>
    <template v-else>
      <div class="ta-opencode-config-toolbar">
        <button type="button" class="ta-opencode-config-btn" :disabled="loading" @click="refresh">
          <Loader2 v-if="loading" class="ta-opencode-config-icon is-spin" />
          <RefreshCw v-else class="ta-opencode-config-icon" :stroke-width="1.6" />
          刷新
        </button>
        <button
          type="button"
          class="ta-opencode-config-btn is-primary"
          :disabled="loading || initializing || pulling || rolloutActive"
          @click="openPullDialog"
        >
          <Loader2 v-if="pulling || rolloutActive" class="ta-opencode-config-icon is-spin" />
          <RefreshCw v-else class="ta-opencode-config-icon" :stroke-width="1.6" />
          刷新公共 Agent Git
        </button>
        <span class="ta-opencode-config-toolbar-hint">一次选择远端分支，所有服务器共享副本同步到同一目标 commit</span>
      </div>

      <div v-if="errorMessage" class="ta-opencode-config-alert" role="alert">
        <AlertTriangle class="ta-opencode-config-icon" :stroke-width="1.6" />
        <span>{{ errorMessage }}</span>
      </div>
      <div v-if="successMessage" class="ta-opencode-config-success">
        <CheckCircle2 class="ta-opencode-config-icon" :stroke-width="1.6" />
        <span>{{ successMessage }}</span>
      </div>

      <section v-if="rollout" class="ta-opencode-config-rollout" aria-label="公共 Agent 全局刷新状态">
        <header>
          <div>
            <strong>{{ rolloutStatusText(rollout.status) }}</strong>
            <span>{{ rollout.branch }} · {{ shortHash(rollout.commitHash) }}</span>
          </div>
          <span class="ta-opencode-config-muted">{{ rollout.rolloutId }}</span>
        </header>
        <div v-if="rollout.failureReason" class="ta-opencode-config-diagnostic">{{ rollout.failureReason }}</div>
        <table class="ta-opencode-config-rollout-table">
          <thead>
            <tr>
              <th>服务器</th>
              <th>同步 / 排空</th>
              <th>个人 worktree 补偿</th>
              <th>重试</th>
              <th>last_error</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="server in rollout.servers" :key="server.linuxServerId">
              <td>{{ server.linuxServerId }}</td>
              <td>{{ serverProgress(server) }}</td>
              <td>{{ worktreeProgress(server) }}</td>
              <td>{{ server.retryCount }}</td>
              <td class="ta-opencode-config-message">{{ formatNullable(server.lastError) }}</td>
            </tr>
            <tr v-if="rollout.servers.length === 0">
              <td colspan="5" class="ta-opencode-config-empty">正在登记服务器同步任务</td>
            </tr>
          </tbody>
        </table>
      </section>

      <div class="ta-opencode-config-table-wrap">
        <table class="ta-opencode-config-table">
          <thead>
            <tr>
              <th>服务器</th>
              <th>状态</th>
              <th>Git 根目录</th>
              <th>配置目录</th>
              <th>worktree 根目录</th>
              <th>分支</th>
              <th>提交</th>
              <th>说明</th>
              <th class="ta-opencode-config-operation">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="rows.length === 0 && !loading">
              <td colspan="9" class="ta-opencode-config-empty">暂无在线后端服务器</td>
            </tr>
            <tr v-for="row in rows" :key="row.linuxServerId">
              <td>
                <div class="ta-opencode-config-server">{{ row.serverName || row.linuxServerId }}</div>
                <div class="ta-opencode-config-muted">{{ row.linuxServerId }}</div>
              </td>
              <td><span :class="['ta-opencode-config-status', statusClass(row)]">{{ statusText(row) }}</span></td>
              <td class="ta-opencode-config-path">{{ formatNullable(row.gitRootPath) }}</td>
              <td class="ta-opencode-config-path">{{ formatNullable(row.configDirPath) }}</td>
              <td class="ta-opencode-config-path">{{ formatNullable(row.worktreeRootPath) }}</td>
              <td>{{ formatNullable(row.currentBranch) }}</td>
              <td class="ta-opencode-config-mono">{{ shortHash(row.commitHash) }}</td>
              <td class="ta-opencode-config-message">
                <div>{{ formatNullable(row.message) }}</div>
                <small v-if="row.status === 'CONFLICT' && row.initialized" class="ta-opencode-config-diagnostic">
                  这是共享运行副本，不是个人 worktree。全局刷新会在明确确认后恢复到远端目标 commit。
                </small>
              </td>
              <td class="ta-opencode-config-operation">
                <div class="ta-opencode-config-actions">
                  <button
                    v-if="!row.initialized"
                    type="button"
                    class="ta-opencode-config-btn"
                    :disabled="!row.initializationAllowed || initializing"
                    @click="openInitializeDialog(row)"
                  >
                    初始化
                  </button>
                  <span v-else class="ta-opencode-config-muted">由顶部全局刷新统一同步</span>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <Teleport to="body">
      <div v-if="dialogOpen" class="ta-opencode-config-dialog-backdrop" @keydown.esc="closeInitializeDialog">
        <section role="dialog" aria-modal="true" aria-label="初始化公共配置仓库" class="ta-opencode-config-dialog">
          <header class="ta-opencode-config-dialog-header">
            <h2>初始化公共配置仓库</h2>
            <span>{{ targetRepository?.linuxServerId }}</span>
          </header>

          <div class="ta-opencode-config-dialog-body">
            <div v-if="initErrorMessage" class="ta-opencode-config-alert" role="alert">
              <AlertTriangle class="ta-opencode-config-icon" :stroke-width="1.6" />
              <span>{{ initErrorMessage }}</span>
            </div>
            <div class="ta-opencode-config-field">
              <label for="public-config-branch">远端分支</label>
              <div class="ta-opencode-config-select-wrap">
                <GitBranch class="ta-opencode-config-icon" :stroke-width="1.6" />
                <select id="public-config-branch" v-model="selectedBranch" :disabled="branchesLoading || initializing">
                  <option v-for="branch in branches" :key="branch" :value="branch">{{ branch }}</option>
                </select>
              </div>
              <span v-if="branchesLoading" class="ta-opencode-config-muted">正在实时读取远端分支</span>
              <span v-else-if="branches.length === 0" class="ta-opencode-config-muted">未读取到远端分支</span>
            </div>
          </div>

          <footer class="ta-opencode-config-dialog-footer">
            <button type="button" class="ta-opencode-config-btn" :disabled="initializing" @click="closeInitializeDialog">取消</button>
            <button type="button" class="ta-opencode-config-btn is-primary" :disabled="!canSubmitInitialize" @click="submitInitialize">
              <Loader2 v-if="initializing" class="ta-opencode-config-icon is-spin" />
              确定
            </button>
          </footer>
        </section>
      </div>

      <div v-if="pullDialogOpen" class="ta-opencode-config-dialog-backdrop" @keydown.esc="closePullDialog">
        <section role="dialog" aria-modal="true" aria-label="刷新公共 Agent Git" class="ta-opencode-config-dialog">
          <header class="ta-opencode-config-dialog-header">
            <h2>刷新公共 Agent Git</h2>
            <span>所有在线服务器同步到同一远端 commit</span>
          </header>

          <div class="ta-opencode-config-dialog-body">
            <div v-if="initErrorMessage" class="ta-opencode-config-alert" role="alert">
              <AlertTriangle class="ta-opencode-config-icon" :stroke-width="1.6" />
              <span>{{ initErrorMessage }}</span>
            </div>
            <div class="ta-opencode-config-field">
              <label for="public-config-pull-branch">远端分支</label>
              <div class="ta-opencode-config-select-wrap">
                <GitBranch class="ta-opencode-config-icon" :stroke-width="1.6" />
                <select id="public-config-pull-branch" v-model="selectedBranch" :disabled="branchesLoading || pulling">
                  <option v-for="branch in branches" :key="branch" :value="branch">{{ branch }}</option>
                </select>
              </div>
              <span v-if="branchesLoading" class="ta-opencode-config-muted">正在实时读取远端分支</span>
              <span v-else class="ta-opencode-config-muted">
                个人 worktree 使用原生 merge；Git 可安全合入时保留 staged、unstaged 和 untracked 内容。
              </span>
            </div>
          </div>

          <footer class="ta-opencode-config-dialog-footer">
            <button type="button" class="ta-opencode-config-btn" :disabled="pulling" @click="closePullDialog">取消</button>
            <button type="button" class="ta-opencode-config-btn is-primary" :disabled="!canSubmitPull" @click="submitGlobalPull">
              <Loader2 v-if="pulling" class="ta-opencode-config-icon is-spin" />
              开始全局刷新
            </button>
          </footer>
        </section>
      </div>
    </Teleport>
  </section>
</template>

<style scoped>
.ta-opencode-config {
  display: flex;
  height: 100%;
  min-height: 0;
  flex-direction: column;
  background: #f7f8fa;
  color: #1f2937;
}
.ta-opencode-config-placeholder,
.ta-opencode-config-empty {
  color: #6b7280;
  font-size: 13px;
}
.ta-opencode-config-placeholder {
  margin: 16px;
}
.ta-opencode-config-toolbar,
.ta-opencode-config-dialog-footer {
  display: flex;
  align-items: center;
  gap: 8px;
}
.ta-opencode-config-actions {
  display: flex;
  align-items: stretch;
  flex-direction: column;
  gap: 6px;
  white-space: nowrap;
}
.ta-opencode-config-diagnostic {
  display: block;
  margin-top: 4px;
  color: #92400e;
  line-height: 1.45;
}
.ta-opencode-config-toolbar {
  padding: 12px 14px;
  border-bottom: 1px solid #e5e7eb;
  background: #fff;
}
.ta-opencode-config-toolbar-hint {
  color: #6b7280;
  font-size: 12px;
}
.ta-opencode-config-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 28px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: #fff;
  color: #374151;
  cursor: pointer;
  font-size: 12px;
  padding: 0 10px;
}
.ta-opencode-config-btn:hover:not(:disabled),
.ta-opencode-config-btn:focus-visible:not(:disabled) {
  border-color: #9ca3af;
  color: #111827;
  outline: none;
}
.ta-opencode-config-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.ta-opencode-config-btn.is-primary {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
}
.ta-opencode-config-btn.is-danger {
  border-color: #fecaca;
  background: #fff7f7;
  color: #b91c1c;
}
.ta-opencode-config-btn.is-danger:hover:not(:disabled),
.ta-opencode-config-btn.is-danger:focus-visible:not(:disabled) {
  border-color: #ef4444;
  color: #991b1b;
}
.ta-opencode-config-icon {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
}
.ta-opencode-config-icon.is-spin {
  animation: ta-spin 0.9s linear infinite;
}
.ta-opencode-config-alert,
.ta-opencode-config-success {
  display: flex;
  gap: 6px;
  margin: 10px 14px 0;
  border-radius: 6px;
  padding: 8px 10px;
  font-size: 12px;
}
.ta-opencode-config-alert {
  background: #fff7ed;
  color: #9a3412;
}
.ta-opencode-config-success {
  background: #ecfdf5;
  color: #047857;
}
.ta-opencode-config-rollout {
  margin: 10px 14px 0;
  overflow: auto;
  border: 1px solid #dbeafe;
  border-radius: 6px;
  background: #fff;
  padding: 10px;
  font-size: 12px;
}
.ta-opencode-config-rollout > header,
.ta-opencode-config-rollout > header > div {
  display: flex;
  align-items: center;
  gap: 10px;
}
.ta-opencode-config-rollout > header {
  justify-content: space-between;
  margin-bottom: 8px;
}
.ta-opencode-config-rollout-table {
  width: 100%;
  border-collapse: collapse;
}
.ta-opencode-config-rollout-table th,
.ta-opencode-config-rollout-table td {
  border-top: 1px solid #e5e7eb;
  padding: 7px 8px;
  text-align: left;
  vertical-align: top;
}
.ta-opencode-config-table-wrap {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 12px 14px;
}
.ta-opencode-config-table {
  width: 100%;
  min-width: 1120px;
  border-collapse: collapse;
  background: #fff;
  font-size: 12px;
}
.ta-opencode-config-table th,
.ta-opencode-config-table td {
  border-bottom: 1px solid #e5e7eb;
  padding: 9px 10px;
  text-align: left;
  vertical-align: top;
}
.ta-opencode-config-table th {
  background: #f9fafb;
  color: #4b5563;
  font-weight: 600;
  white-space: nowrap;
}
.ta-opencode-config-operation {
  position: sticky;
  right: 0;
  z-index: 1;
  min-width: 124px;
  background: #fff;
  box-shadow: -10px 0 14px -14px rgb(15 23 42 / 55%);
}
.ta-opencode-config-table th.ta-opencode-config-operation {
  z-index: 2;
  background: #f9fafb;
}
.ta-opencode-config-server {
  font-weight: 600;
  color: #111827;
}
.ta-opencode-config-muted {
  color: #6b7280;
  font-size: 11px;
}
.ta-opencode-config-path,
.ta-opencode-config-message {
  max-width: 240px;
  overflow-wrap: anywhere;
}
.ta-opencode-config-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}
.ta-opencode-config-status {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  border-radius: 999px;
  padding: 0 8px;
  font-size: 12px;
}
.ta-opencode-config-status.is-ready {
  background: #ecfdf5;
  color: #047857;
}
.ta-opencode-config-status.is-pending {
  background: #fef3c7;
  color: #92400e;
}
.ta-opencode-config-status.is-error {
  background: #fef2f2;
  color: #b91c1c;
}
.ta-opencode-config-dialog-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1100;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgb(0 0 0 / 35%);
  padding: 20px;
}
.ta-opencode-config-dialog {
  width: min(420px, calc(100vw - 24px));
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 20px 50px rgb(15 23 42 / 25%);
}
.ta-opencode-config-dialog-header,
.ta-opencode-config-dialog-body,
.ta-opencode-config-dialog-footer {
  padding: 14px;
}
.ta-opencode-config-dialog-header {
  border-bottom: 1px solid #e5e7eb;
}
.ta-opencode-config-dialog-header h2 {
  margin: 0 0 4px;
  font-size: 15px;
}
.ta-opencode-config-dialog-footer {
  justify-content: flex-end;
  border-top: 1px solid #e5e7eb;
}
.ta-opencode-config-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.ta-opencode-config-field label {
  color: #4b5563;
  font-size: 12px;
  font-weight: 600;
}
.ta-opencode-config-select-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  padding: 0 9px;
  min-height: 34px;
}
.ta-opencode-config-select-wrap select {
  min-width: 0;
  flex: 1;
  border: 0;
  background: transparent;
  color: #111827;
  font-size: 13px;
  outline: none;
}
@keyframes ta-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
