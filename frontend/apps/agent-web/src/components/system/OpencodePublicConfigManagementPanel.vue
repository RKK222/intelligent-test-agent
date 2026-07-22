<script setup lang="ts">
import { computed, inject, onMounted, ref } from "vue";
import { AlertTriangle, CheckCircle2, GitBranch, Loader2, RefreshCw } from "lucide-vue-next";
import { BackendApiError, type BackendApiClient } from "@test-agent/backend-api";
import type { CurrentUser, PublicAgentRepositoryStatus } from "@test-agent/shared-types";

const props = defineProps<{
  currentUser: CurrentUser | null;
}>();

const api = inject<BackendApiClient>("api")!;
const rows = ref<PublicAgentRepositoryStatus[]>([]);
const loading = ref(false);
const initializing = ref(false);
const pullingServerId = ref<string | null>(null);
const errorMessage = ref("");
const successMessage = ref("");
const dialogOpen = ref(false);
const branchesLoading = ref(false);
const branches = ref<string[]>([]);
const selectedBranch = ref("");
const targetRepository = ref<PublicAgentRepositoryStatus | null>(null);
const initErrorMessage = ref("");
const dirtyPullDiagnostics = ref<Record<string, { repositoryKind: string; path: string; files: string[] }>>({});

const hasSuperAdmin = computed(() => props.currentUser?.roles?.includes("SUPER_ADMIN") === true);
const canSubmitInitialize = computed(() => !!targetRepository.value && !!selectedBranch.value && !initializing.value && !branchesLoading.value);

onMounted(() => {
  if (hasSuperAdmin.value) {
    void refresh();
  }
});

async function refresh() {
  loading.value = true;
  errorMessage.value = "";
  try {
    rows.value = await api.listPublicAgentRepositories();
    dirtyPullDiagnostics.value = {};
  } catch (error) {
    errorMessage.value = formatError(error, "加载公共配置仓库状态失败");
  } finally {
    loading.value = false;
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

async function pullRepository(repository: PublicAgentRepositoryStatus) {
  await pullRepositoryWithDiscard(repository, false);
}

async function discardAndPullRepository(repository: PublicAgentRepositoryStatus) {
  const confirmed = window.confirm(
    `将放弃服务器 ${repository.linuxServerId} 上当前管理员个人公共 worktree和共享运行副本中检测到的已跟踪文件修改后重新拉取。`
      + "其他管理员的个人 worktree不受影响，也不会删除未跟踪文件。是否继续？"
  );
  if (!confirmed) {
    return;
  }
  await pullRepositoryWithDiscard(repository, true);
}

async function pullRepositoryWithDiscard(repository: PublicAgentRepositoryStatus, discardLocalChanges: boolean) {
  const branch = repository.currentBranch?.trim();
  if (!repository.initialized || !branch || pullingServerId.value) {
    return;
  }
  pullingServerId.value = repository.linuxServerId;
  errorMessage.value = "";
  successMessage.value = "";
  try {
    const updated = await api.pullPublicAgentRepository(
      repository.linuxServerId,
      branch,
      newOperationId(),
      discardLocalChanges
    );
    rows.value = rows.value.map((row) => (row.linuxServerId === updated.linuxServerId ? updated : row));
    const { [updated.linuxServerId]: _removed, ...remainingDiagnostics } = dirtyPullDiagnostics.value;
    dirtyPullDiagnostics.value = remainingDiagnostics;
    successMessage.value = `服务器 ${updated.linuxServerId} 公共配置仓库已拉取到最新`;
  } catch (error) {
    errorMessage.value = formatError(error, "拉取公共配置仓库失败");
    if (error instanceof BackendApiError && error.details.discardLocalChangesAllowed === true) {
      dirtyPullDiagnostics.value = {
        ...dirtyPullDiagnostics.value,
        [repository.linuxServerId]: {
          repositoryKind: typeof error.details.repositoryKind === "string"
            ? error.details.repositoryKind
            : "UNKNOWN",
          path: typeof error.details.path === "string" ? error.details.path : "-",
          files: Array.isArray(error.details.dirtyFiles)
            ? error.details.dirtyFiles.filter((file): file is string => typeof file === "string")
            : []
        }
      };
    }
    // 共享副本状态仍由列表接口刷新；个人 worktree 诊断保留本次异常返回的真实路径。
    try {
      rows.value = await api.listPublicAgentRepositories();
    } catch {
      // 保留原始拉取错误；状态刷新只是诊断增强，失败不能覆盖真正原因。
    }
  } finally {
    pullingServerId.value = null;
  }
}

function preferredBranch(repository: PublicAgentRepositoryStatus, remoteBranches: string[]) {
  const current = repository.currentBranch?.trim();
  if (current && remoteBranches.includes(current)) {
    return current;
  }
  return remoteBranches[0] ?? current ?? "main";
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
      </div>

      <div v-if="errorMessage" class="ta-opencode-config-alert" role="alert">
        <AlertTriangle class="ta-opencode-config-icon" :stroke-width="1.6" />
        <span>{{ errorMessage }}</span>
      </div>
      <div v-if="successMessage" class="ta-opencode-config-success">
        <CheckCircle2 class="ta-opencode-config-icon" :stroke-width="1.6" />
        <span>{{ successMessage }}</span>
      </div>

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
              <th>操作</th>
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
                <small v-if="dirtyPullDiagnostics[row.linuxServerId]" class="ta-opencode-config-diagnostic">
                  最近拉取检测到{{ dirtyPullDiagnostics[row.linuxServerId].repositoryKind === 'PERSONAL_WORKTREE'
                    ? '当前管理员个人公共 worktree'
                    : '公共配置仓库' }} 存在本地变更；路径：{{ dirtyPullDiagnostics[row.linuxServerId].path }}；文件：{{
                    dirtyPullDiagnostics[row.linuxServerId].files.length > 0
                      ? dirtyPullDiagnostics[row.linuxServerId].files.join('、')
                      : '请在上述路径执行 git status --short'
                  }}。
                </small>
                <small v-if="row.status === 'CONFLICT' && row.initialized" class="ta-opencode-config-diagnostic">
                  这是该服务器的共享运行副本，不是个人公共 worktree。请先按上述路径核对本机修改；确认无需保留后，可放弃已跟踪修改并重新拉取。
                </small>
              </td>
              <td>
                <div class="ta-opencode-config-actions">
                  <button
                    type="button"
                    class="ta-opencode-config-btn"
                    :disabled="!row.initializationAllowed || initializing"
                    @click="openInitializeDialog(row)"
                  >
                    初始化
                  </button>
                  <button
                    type="button"
                    class="ta-opencode-config-btn"
                    :disabled="!row.initialized || !row.currentBranch || pullingServerId !== null"
                    @click="pullRepository(row)"
                  >
                    <Loader2 v-if="pullingServerId === row.linuxServerId" class="ta-opencode-config-icon is-spin" />
                    拉取
                  </button>
                  <button
                    v-if="(row.status === 'CONFLICT' && row.initialized) || dirtyPullDiagnostics[row.linuxServerId]"
                    type="button"
                    class="ta-opencode-config-btn is-danger"
                    :disabled="!row.currentBranch || pullingServerId !== null"
                    @click="discardAndPullRepository(row)"
                  >
                    <Loader2 v-if="pullingServerId === row.linuxServerId" class="ta-opencode-config-icon is-spin" />
                    放弃本地变更并拉取
                  </button>
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
  align-items: center;
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
.ta-opencode-config-table-wrap {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 12px 14px;
}
.ta-opencode-config-table {
  width: 100%;
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
