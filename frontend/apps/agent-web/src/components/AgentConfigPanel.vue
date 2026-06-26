<script setup lang="ts">
import { computed, ref, watch } from "vue";
import {
  AlertTriangle,
  Check,
  FolderGit2,
  GitBranch,
  GitCompare,
  Globe2,
  Loader2,
  Plus,
  RefreshCw,
  Upload,
  Users
} from "lucide-vue-next";
import { createBackendApiClient, BackendApiError } from "@test-agent/backend-api";
import type {
  AgentConfigDiffFile,
  AgentConfigProgressEvent,
  AgentConfigStatus,
  AgentConfigWorktree,
  FileContent,
  FileTreeEntry
} from "@test-agent/shared-types";
import PublicDirectoryNode from "./PublicDirectoryNode.vue";

const props = defineProps<{
  baseUrl: string;
  workspaceId?: string;
  canWrite: boolean;
  hideHeader?: boolean;
  hideGitOps?: boolean;
}>();

const emit = defineEmits<{
  openFile: [payload: { scope: "PUBLIC" | "WORKSPACE"; path: string; content: FileContent; readonly: boolean; worktreeId?: string | null }];
}>();

const api = createBackendApiClient({ baseUrl: props.baseUrl });

type Scope = "PUBLIC" | "WORKSPACE";

const status = ref<{ PUBLIC?: AgentConfigStatus; WORKSPACE?: AgentConfigStatus }>({});
const entriesByScope = ref<Record<Scope, Record<string, FileTreeEntry[]>>>({ PUBLIC: {}, WORKSPACE: {} });
const rootExpanded = ref<Set<Scope>>(new Set(["PUBLIC"]));
const expandedByScope = ref<Record<Scope, Set<string>>>({ PUBLIC: new Set(), WORKSPACE: new Set() });
const loadingByScope = ref<Record<Scope, Set<string>>>({ PUBLIC: new Set(), WORKSPACE: new Set() });
const errorMessage = ref("");
const activeScope = ref<Scope>("PUBLIC");
const diffFiles = ref<AgentConfigDiffFile[]>([]);
const selectedDiffPath = ref("");
const commitMessage = ref("");
const progressEvents = ref<AgentConfigProgressEvent[]>([]);
const publicWorktree = ref<AgentConfigWorktree | null>(null);
const workspaceWorktree = ref<AgentConfigWorktree | null>(null);
const busy = ref(false);
const activeWorktree = computed(() => activeScope.value === "PUBLIC" ? publicWorktree.value : workspaceWorktree.value);
const selectedDiff = computed(() => diffFiles.value.find((file) => file.path === selectedDiffPath.value) ?? diffFiles.value[0]);

void refreshAll();

watch(
  () => props.workspaceId,
  () => {
    entriesByScope.value = { PUBLIC: entriesByScope.value.PUBLIC, WORKSPACE: {} };
    workspaceWorktree.value = null;
    void refreshStatus();
    if (rootExpanded.value.has("WORKSPACE")) {
      void loadDirectory("WORKSPACE", "");
    }
  }
);

async function refreshAll() {
  await refreshStatus();
  if (status.value.PUBLIC?.enabled !== false) {
    await loadDirectory("PUBLIC", "");
  }
  if (props.workspaceId) {
    await loadDirectory("WORKSPACE", "");
  }
}

async function refreshStatus() {
  try {
    const next: { PUBLIC?: AgentConfigStatus; WORKSPACE?: AgentConfigStatus } = {
      PUBLIC: await api.getPublicAgentConfigStatus()
    };
    if (props.workspaceId) {
      next.WORKSPACE = await api.getWorkspaceAgentConfigStatus(props.workspaceId);
    }
    status.value = next;
  } catch (error) {
    errorMessage.value = errorMessageFor(error, "加载 Agent 状态失败");
  }
}

function worktreeId(scope: Scope) {
  return scope === "PUBLIC" ? publicWorktree.value?.worktreeId : workspaceWorktree.value?.worktreeId;
}

async function loadDirectory(scope: Scope, path: string) {
  if (scope === "WORKSPACE" && !props.workspaceId) return;
  if (scope === "PUBLIC" && status.value.PUBLIC?.enabled === false) return;
  if (entriesByScope.value[scope][path] !== undefined || loadingByScope.value[scope].has(path)) return;
  loadingByScope.value = { ...loadingByScope.value, [scope]: new Set([...loadingByScope.value[scope], path]) };
  errorMessage.value = "";
  try {
    const entries = scope === "PUBLIC"
      ? await api.listPublicAgentFiles(path, worktreeId(scope))
      : await api.listWorkspaceAgentFiles(props.workspaceId!, path, worktreeId(scope));
    entriesByScope.value = {
      ...entriesByScope.value,
      [scope]: { ...entriesByScope.value[scope], [path]: entries }
    };
  } catch (error) {
    errorMessage.value = errorMessageFor(error, "加载 Agent 文件失败");
    const nextExpanded = new Set(expandedByScope.value[scope]);
    nextExpanded.delete(path);
    expandedByScope.value = { ...expandedByScope.value, [scope]: nextExpanded };
  } finally {
    const next = new Set(loadingByScope.value[scope]);
    next.delete(path);
    loadingByScope.value = { ...loadingByScope.value, [scope]: next };
  }
}

function toggleRoot(scope: Scope) {
  activeScope.value = scope;
  const next = new Set(rootExpanded.value);
  if (next.has(scope)) {
    next.delete(scope);
  } else {
    next.add(scope);
    void loadDirectory(scope, "");
  }
  rootExpanded.value = next;
}

function toggleDirectory(scope: Scope, path: string) {
  activeScope.value = scope;
  if (loadingByScope.value[scope].has(path)) return;
  const next = new Set(expandedByScope.value[scope]);
  if (next.has(path)) {
    next.delete(path);
  } else {
    next.add(path);
    void loadDirectory(scope, path);
  }
  expandedByScope.value = { ...expandedByScope.value, [scope]: next };
}

async function openFile(scope: Scope, path: string) {
  activeScope.value = scope;
  try {
    const file = scope === "PUBLIC"
      ? await api.readPublicAgentFile(path, worktreeId(scope))
      : await api.readWorkspaceAgentFile(props.workspaceId!, path, worktreeId(scope));
    emit("openFile", { scope, path, content: file, readonly: !props.canWrite, worktreeId: worktreeId(scope) });
  } catch (error) {
    errorMessage.value = errorMessageFor(error, "读取 Agent 文件失败");
  }
}

async function refreshScope(scope: Scope) {
  entriesByScope.value = { ...entriesByScope.value, [scope]: {} };
  await refreshStatus();
  if (scope !== "PUBLIC" || status.value.PUBLIC?.enabled !== false) {
    await loadDirectory(scope, "");
  }
}

async function updatePublicConfig() {
  if (!props.canWrite) return;
  const branches = await api.listPublicAgentBranches();
  const branch = window.prompt("选择公共配置分支", status.value.PUBLIC?.currentBranch ?? branches[0] ?? "main");
  if (!branch) return;
  const operationId = newOperationId();
  await runOperation(() => api.updatePublicAgentConfig(branch, operationId), "公共 Agent 更新", operationId);
  await refreshScope("PUBLIC");
}

async function createWorktree(scope: Scope) {
  if (!props.canWrite) return;
  if (scope === "WORKSPACE" && !props.workspaceId) return;
  const baseName = window.prompt("worktree 名称", "change-agent-md");
  if (!baseName) return;
  const branch = window.prompt("目标分支", status.value[scope]?.currentBranch ?? "main");
  if (!branch) return;
  const operationId = newOperationId();
  const created = await runOperation(
    () =>
      scope === "PUBLIC"
        ? api.createPublicAgentWorktree({ baseName, branch, operationId })
        : api.createWorkspaceAgentWorktree(props.workspaceId!, { baseName, branch, operationId }),
    "创建 Agent worktree",
    operationId
  );
  if (!created) return;
  if (scope === "PUBLIC") {
    publicWorktree.value = created;
  } else {
    workspaceWorktree.value = created;
  }
  entriesByScope.value = { ...entriesByScope.value, [scope]: {} };
  await loadDirectory(scope, "");
}

async function loadDiff(scope = activeScope.value) {
  if (scope === "WORKSPACE" && !props.workspaceId) return;
  activeScope.value = scope;
  busy.value = true;
  try {
    const diff = scope === "PUBLIC"
      ? await api.getPublicAgentDiff(worktreeId(scope))
      : await api.getWorkspaceAgentDiff(props.workspaceId!, worktreeId(scope));
    diffFiles.value = diff.files;
    selectedDiffPath.value = diff.files[0]?.path ?? "";
  } catch (error) {
    errorMessage.value = errorMessageFor(error, "加载 Agent Diff 失败");
  } finally {
    busy.value = false;
  }
}

async function stage(file: AgentConfigDiffFile) {
  if (!props.canWrite) return;
  busy.value = true;
  try {
    if (activeScope.value === "PUBLIC") {
      await api.stagePublicAgentFiles([file.path], worktreeId(activeScope.value));
    } else {
      await api.stageWorkspaceAgentFiles(props.workspaceId!, [file.path], worktreeId(activeScope.value));
    }
    await loadDiff(activeScope.value);
  } catch (error) {
    errorMessage.value = errorMessageFor(error, "暂存 Agent 文件失败");
  } finally {
    busy.value = false;
  }
}

async function commit() {
  if (!props.canWrite || !commitMessage.value.trim()) return;
  const message = commitMessage.value.trim();
  const operationId = newOperationId();
  await runOperation(
    () =>
      activeScope.value === "PUBLIC"
        ? api.commitPublicAgentConfig({ message, worktreeId: worktreeId(activeScope.value), operationId })
        : api.commitWorkspaceAgentConfig(props.workspaceId!, { message, worktreeId: worktreeId(activeScope.value), operationId }),
    "提交 Agent 配置",
    operationId
  );
  commitMessage.value = "";
  await loadDiff(activeScope.value);
}

async function publish() {
  if (!props.canWrite) return;
  const operationId = newOperationId();
  await runOperation(
    () =>
      activeScope.value === "PUBLIC"
        ? api.publishPublicAgentConfig(worktreeId(activeScope.value), operationId)
        : api.publishWorkspaceAgentConfig(props.workspaceId!, worktreeId(activeScope.value), operationId),
    "发布 Agent 配置",
    operationId
  );
  await refreshScope(activeScope.value);
}

async function runOperation<T>(action: () => Promise<T>, label: string, knownOperationId?: string): Promise<T | null> {
  busy.value = true;
  errorMessage.value = "";
  const operationId = knownOperationId ?? newOperationId();
  progressEvents.value = [{ type: "snapshot", operationId, currentStep: label, status: "RUNNING" }];
  let socket: { close: () => void } | null = null;
  try {
    socket = await api.connectAgentConfigProgress(operationId, (event) => {
      progressEvents.value = [...progressEvents.value.slice(-8), event];
    });
  } catch {
    socket = null;
  }
  try {
    const result = await action();
    return result;
  } catch (error) {
    errorMessage.value = errorMessageFor(error, `${label}失败`);
    return null;
  } finally {
    busy.value = false;
    setTimeout(() => socket?.close(), 1200);
  }
}

function newOperationId() {
  const random = typeof crypto !== "undefined" && "randomUUID" in crypto
    ? crypto.randomUUID().replaceAll("-", "")
    : `${Date.now().toString(36)}${Math.random().toString(36).slice(2)}`;
  return `aco_${random}`;
}

function errorMessageFor(error: unknown, fallback: string): string {
  if (error instanceof BackendApiError) return `${fallback}：${error.message}`;
  if (error instanceof Error) return `${fallback}：${error.message}`;
  return fallback;
}

defineExpose({
  refreshAll
});
</script>

<template>
  <div class="agent-config-panel">
    <div v-if="!hideHeader" class="agent-config-header">
      <span>Agent</span>
      <button type="button" class="agent-icon-btn" title="刷新" aria-label="刷新" @click="refreshAll">
        <RefreshCw class="h-3.5 w-3.5" :stroke-width="1.5" />
      </button>
    </div>
    <div v-if="errorMessage" class="agent-error">
      <AlertTriangle class="mt-[2px] h-3.5 w-3.5 shrink-0" :stroke-width="1.5" />
      <span>{{ errorMessage }}</span>
    </div>

    <div class="agent-tree">
      <div class="agent-root-row" :class="{ active: activeScope === 'PUBLIC' }">
        <button type="button" class="agent-root-main" @click="toggleRoot('PUBLIC')">
          <Globe2 class="h-3.5 w-3.5" :stroke-width="1.5" />
          <span>公共级</span>
          <span v-if="publicWorktree" class="agent-root-badge">{{ publicWorktree.worktreeName }}</span>
        </button>
        <button
          v-if="canWrite"
          type="button"
          class="agent-icon-btn"
          title="更新公共配置"
          aria-label="更新公共配置"
          :disabled="busy || status.PUBLIC?.enabled === false"
          @click="updatePublicConfig"
        >
          <GitBranch class="h-3.5 w-3.5" :stroke-width="1.5" />
        </button>
        <button
          v-if="canWrite"
          type="button"
          class="agent-icon-btn"
          title="创建公共 worktree"
          aria-label="创建公共 worktree"
          :disabled="busy || status.PUBLIC?.enabled === false"
          @click="createWorktree('PUBLIC')"
        >
          <Plus class="h-3.5 w-3.5" :stroke-width="1.5" />
        </button>
      </div>
      <div v-if="rootExpanded.has('PUBLIC')" class="agent-node-list">
        <div v-if="loadingByScope.PUBLIC.has('')" class="agent-loading"><Loader2 class="h-3.5 w-3.5 animate-spin" />加载中</div>
        <PublicDirectoryNode
          v-for="entry in entriesByScope.PUBLIC[''] ?? []"
          :key="`PUBLIC:${entry.path}`"
          :entry="entry"
          :depth="0"
          :entries-by-directory="entriesByScope.PUBLIC"
          :expanded-directories="expandedByScope.PUBLIC"
          :loading-path="loadingByScope.PUBLIC"
          @toggle="(path) => toggleDirectory('PUBLIC', path)"
          @open-file="(path) => openFile('PUBLIC', path)"
        />
      </div>

      <div class="agent-root-row" :class="{ active: activeScope === 'WORKSPACE' }">
        <button type="button" class="agent-root-main" :disabled="!workspaceId" @click="toggleRoot('WORKSPACE')">
          <Users class="h-3.5 w-3.5" :stroke-width="1.5" />
          <span>工作空间级</span>
          <span v-if="workspaceWorktree" class="agent-root-badge">{{ workspaceWorktree.worktreeName }}</span>
        </button>
        <button
          v-if="canWrite"
          type="button"
          class="agent-icon-btn"
          title="创建工作空间 worktree"
          aria-label="创建工作空间 worktree"
          :disabled="busy || !workspaceId"
          @click="createWorktree('WORKSPACE')"
        >
          <Plus class="h-3.5 w-3.5" :stroke-width="1.5" />
        </button>
      </div>
      <div v-if="rootExpanded.has('WORKSPACE')" class="agent-node-list">
        <div v-if="loadingByScope.WORKSPACE.has('')" class="agent-loading"><Loader2 class="h-3.5 w-3.5 animate-spin" />加载中</div>
        <PublicDirectoryNode
          v-for="entry in entriesByScope.WORKSPACE[''] ?? []"
          :key="`WORKSPACE:${entry.path}`"
          :entry="entry"
          :depth="0"
          :entries-by-directory="entriesByScope.WORKSPACE"
          :expanded-directories="expandedByScope.WORKSPACE"
          :loading-path="loadingByScope.WORKSPACE"
          @toggle="(path) => toggleDirectory('WORKSPACE', path)"
          @open-file="(path) => openFile('WORKSPACE', path)"
        />
      </div>
    </div>

    <div v-if="canWrite && !hideGitOps" class="agent-diff">
      <div class="agent-diff-toolbar">
        <button type="button" class="agent-action-btn" :disabled="busy" @click="loadDiff()">
          <GitCompare class="h-3.5 w-3.5" :stroke-width="1.5" />
          Diff
        </button>
        <button type="button" class="agent-action-btn" :disabled="busy" @click="publish">
          <Upload class="h-3.5 w-3.5" :stroke-width="1.5" />
          发布
        </button>
      </div>
      <div class="agent-diff-body">
        <div class="agent-diff-files">
          <button
            v-for="file in diffFiles"
            :key="file.path"
            type="button"
            class="agent-diff-file"
            :class="{ active: selectedDiff?.path === file.path }"
            @click="selectedDiffPath = file.path"
            @dblclick="stage(file)"
          >
            <span>{{ file.status || 'M' }}</span>
            <span>{{ file.path }}</span>
            <Check v-if="file.staged" class="h-3 w-3" :stroke-width="1.5" />
          </button>
        </div>
        <pre v-if="selectedDiff" class="agent-diff-preview">{{ selectedDiff.patch || '无文本差异' }}</pre>
      </div>
      <div class="agent-commit-row">
        <input v-model="commitMessage" class="agent-commit-input" placeholder="commit message" :disabled="busy" />
        <button type="button" class="agent-action-btn" :disabled="busy || !commitMessage.trim()" @click="commit">
          <FolderGit2 class="h-3.5 w-3.5" :stroke-width="1.5" />
          提交
        </button>
      </div>
      <div v-if="progressEvents.length" class="agent-progress">
        <div v-for="(event, index) in progressEvents" :key="`${event.type}-${index}`">
          {{ event.type }} · {{ event.currentStep ?? event.operation?.currentStep ?? '-' }} · {{ event.status ?? event.operation?.status ?? '' }}
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.agent-config-panel {
  display: flex;
  height: 100%;
  min-height: 0;
  flex-direction: column;
  background: var(--ta-panel, #fafafa);
  color: var(--ta-text, #18181b);
}
.agent-config-header,
.agent-root-row,
.agent-diff-toolbar,
.agent-commit-row {
  display: flex;
  align-items: center;
  gap: 6px;
}
.agent-config-header {
  height: 28px;
  justify-content: space-between;
  border-bottom: 1px solid var(--ta-border, #e4e4e7);
  padding: 0 8px;
  font-size: 12px;
  font-weight: 600;
  color: var(--ta-muted, #6b7280);
}
.agent-error {
  display: flex;
  gap: 4px;
  border-bottom: 1px solid var(--ta-border, #e4e4e7);
  background: #fff7ed;
  padding: 5px 8px;
  font-size: 12px;
  color: #9a3412;
}
.agent-tree {
  min-height: 160px;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 8px;
}
.agent-root-row {
  min-height: 28px;
  border-radius: 6px;
}
.agent-root-row.active {
  background: #f4f4f5;
}
.agent-root-main {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  gap: 6px;
  border: 0;
  background: transparent;
  color: inherit;
  font-size: 13px;
  cursor: pointer;
}
.agent-root-badge {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--ta-muted, #6b7280);
  font-size: 11px;
}
.agent-icon-btn,
.agent-action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  height: 24px;
  border: 1px solid transparent;
  border-radius: 5px;
  background: transparent;
  color: var(--ta-muted, #6b7280);
  font-size: 12px;
  cursor: pointer;
}
.agent-icon-btn {
  width: 24px;
  padding: 0;
}
.agent-action-btn {
  padding: 0 8px;
  border-color: var(--ta-border, #e4e4e7);
  background: #fff;
}
.agent-icon-btn:disabled,
.agent-action-btn:disabled,
.agent-root-main:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}
.agent-node-list {
  padding-left: 8px;
}
.agent-loading {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  color: var(--ta-muted, #6b7280);
  font-size: 12px;
}
.agent-diff {
  display: flex;
  min-height: 260px;
  flex: 1;
  flex-direction: column;
  border-top: 1px solid var(--ta-border, #e4e4e7);
  padding: 8px;
  gap: 8px;
}
.agent-diff-body {
  display: grid;
  min-height: 0;
  flex: 1;
  grid-template-columns: minmax(120px, 38%) 1fr;
  gap: 8px;
}
.agent-diff-files {
  min-height: 0;
  overflow: auto;
  border: 1px solid var(--ta-border, #e4e4e7);
  border-radius: 6px;
  background: #fff;
}
.agent-diff-file {
  display: grid;
  width: 100%;
  grid-template-columns: 22px minmax(0, 1fr) 14px;
  gap: 4px;
  border: 0;
  border-bottom: 1px solid #f4f4f5;
  background: transparent;
  padding: 5px 6px;
  text-align: left;
  font-size: 12px;
  cursor: pointer;
}
.agent-diff-file.active {
  background: #eef2ff;
}
.agent-diff-file span:nth-child(2) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.agent-diff-preview {
  min-height: 0;
  overflow: auto;
  margin: 0;
  border: 1px solid var(--ta-border, #e4e4e7);
  border-radius: 6px;
  background: #fff;
  padding: 8px;
  font-size: 11px;
  line-height: 1.45;
  white-space: pre-wrap;
}
.agent-commit-input {
  min-width: 0;
  flex: 1;
  height: 26px;
  border: 1px solid var(--ta-border, #e4e4e7);
  border-radius: 5px;
  padding: 0 8px;
  font-size: 12px;
}
.agent-progress {
  max-height: 76px;
  overflow: auto;
  border: 1px solid var(--ta-border, #e4e4e7);
  border-radius: 6px;
  background: #fff;
  padding: 6px;
  color: var(--ta-muted, #6b7280);
  font-size: 11px;
}
</style>
