<script setup lang="ts">
import { computed, inject, onBeforeUnmount, ref, watch } from "vue";
import type { BackendApiClient } from "@test-agent/backend-api";
import type {
  ApplicationWorkspaceTemplate,
  ApplicationWorkspaceVersion,
  CodeRepositoryConfig,
  RepositoryTreeNode,
  WorkspaceCreateOperation
} from "@test-agent/shared-types";
import { Button, Input, Spinner } from "@test-agent/ui-kit";
import { FolderGit2, Plus, RefreshCw } from "lucide-vue-next";
import RepositoryDirectoryTree from "./RepositoryDirectoryTree.vue";

const AUTOMATION_REPOSITORY_TYPE = "AUTOMATION_CODE_REPOSITORY";
const OPERATION_POLL_INTERVAL_MS = 1_000;

const props = defineProps<{
  open: boolean;
  appId: string;
  canManage: boolean;
}>();

const emit = defineEmits<{
  changed: [];
}>();

const api = inject<BackendApiClient>("api")!;
const loading = ref(false);
const saving = ref(false);
const errorMessage = ref("");
const repositories = ref<CodeRepositoryConfig[]>([]);
const templates = ref<ApplicationWorkspaceTemplate[]>([]);
const versionsByTemplate = ref<Record<string, ApplicationWorkspaceVersion[]>>({});
const selectedRepositoryId = ref("");
const selectedTemplateId = ref("");
const createMode = ref(false);
const branches = ref<string[]>([]);
const branchesLoading = ref(false);
const selectedBranch = ref("");
const selectedDirectory = ref("");
const referenceName = ref("");
const versionDate = ref("");
const tree = ref<RepositoryTreeNode[]>([]);
const treeLoading = ref(false);
const operation = ref<WorkspaceCreateOperation | null>(null);
let catalogGeneration = 0;
let branchGeneration = 0;
let treeGeneration = 0;
let operationTimer: number | undefined;

const selectedTemplate = computed(() =>
  templates.value.find((template) => template.workspaceId === selectedTemplateId.value) ?? null
);
const selectedRepository = computed(() =>
  repositories.value.find((repository) => repository.repositoryId === selectedRepositoryId.value) ?? null
);
const selectedVersions = computed(() =>
  selectedTemplate.value ? versionsByTemplate.value[selectedTemplate.value.workspaceId] ?? [] : []
);
const canCreateReference = computed(() =>
  props.canManage
  && !saving.value
  && Boolean(selectedRepositoryId.value && selectedBranch.value && selectedDirectory.value && referenceName.value.trim())
  && /^\d{8}$/.test(versionDate.value)
);
const canCreateVersion = computed(() =>
  props.canManage
  && !saving.value
  && Boolean(selectedTemplate.value && selectedBranch.value)
  && /^\d{8}$/.test(versionDate.value)
);

function todayVersion() {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${year}${month}${day}`;
}

function repositoryName(repositoryId: string) {
  return repositories.value.find((repository) => repository.repositoryId === repositoryId)?.name ?? "自动化代码库";
}

function templatesForRepository(repositoryId: string) {
  return templates.value.filter((template) => template.repositoryId === repositoryId);
}

function normalizedTree(nodes: RepositoryTreeNode[]): RepositoryTreeNode[] {
  return [...nodes]
    .map((node) => ({ ...node, children: normalizedTree(node.children ?? []) }))
    .sort((left, right) => left.type === right.type
      ? left.name.localeCompare(right.name)
      : left.type === "directory" ? -1 : 1);
}

/** 普通成员只读取模板与当前版本；管理员才读取版本库元数据和可写配置入口。 */
async function loadCatalog() {
  const generation = ++catalogGeneration;
  loading.value = true;
  errorMessage.value = "";
  try {
    const [nextTemplates, nextRepositories, configuredWorkspaces] = await Promise.all([
      api.listWorkspaceTemplates(props.appId),
      props.canManage ? api.listApplicationRepositories(props.appId) : Promise.resolve([]),
      props.canManage ? api.listApplicationWorkspaces(props.appId) : Promise.resolve([])
    ]);
    if (!props.open || generation !== catalogGeneration) return;
    const automationRepositories = nextRepositories.filter((repository) => repository.repositoryType === AUTOMATION_REPOSITORY_TYPE);
    const automationRepositoryIds = new Set(automationRepositories.map((repository) => repository.repositoryId));
    const templateById = new Map(
      nextTemplates
        .filter((template) => template.repositoryType === AUTOMATION_REPOSITORY_TYPE)
        .map((template) => [template.workspaceId, template] as const)
    );
    // 模板接口按 enabled 过滤；管理员额外合并配置接口，确保停用引用仍可重新启用。
    if (props.canManage) {
      for (const workspace of configuredWorkspaces) {
        if (!automationRepositoryIds.has(workspace.repositoryId) || templateById.has(workspace.workspaceId)) continue;
        templateById.set(workspace.workspaceId, {
          ...workspace,
          standard: false,
          repositoryType: AUTOMATION_REPOSITORY_TYPE,
          activeVersion: null
        });
      }
    }
    const automationTemplates = [...templateById.values()];
    const versionEntries = await Promise.all(automationTemplates.map(async (template) => [
      template.workspaceId,
      await api.listWorkspaceVersions(props.appId, template.workspaceId)
    ] as const));
    if (!props.open || generation !== catalogGeneration) return;
    templates.value = automationTemplates;
    repositories.value = automationRepositories;
    versionsByTemplate.value = Object.fromEntries(versionEntries);

    const retainedTemplate = automationTemplates.find((template) => template.workspaceId === selectedTemplateId.value);
    if (retainedTemplate) {
      await selectTemplate(retainedTemplate);
      return;
    }
    const firstTemplate = automationTemplates[0];
    if (firstTemplate) {
      await selectTemplate(firstTemplate);
      return;
    }
    const firstRepository = repositories.value[0];
    if (props.canManage && firstRepository) await startCreate(firstRepository.repositoryId);
  } catch (error) {
    if (generation === catalogGeneration) {
      errorMessage.value = error instanceof Error ? error.message : "加载自动化引用配置失败";
    }
  } finally {
    if (generation === catalogGeneration) loading.value = false;
  }
}

async function loadBranches(repositoryId: string, preferredBranch = "") {
  const generation = ++branchGeneration;
  ++treeGeneration;
  branchesLoading.value = true;
  branches.value = [];
  selectedBranch.value = "";
  tree.value = [];
  selectedDirectory.value = "";
  try {
    const result = await api.listRepositoryBranches(repositoryId);
    if (generation !== branchGeneration || repositoryId !== selectedRepositoryId.value) return;
    branches.value = result;
    selectedBranch.value = result.includes(preferredBranch) ? preferredBranch : result[0] ?? "";
    if (selectedBranch.value && createMode.value) await loadTree();
  } catch (error) {
    if (generation === branchGeneration) errorMessage.value = error instanceof Error ? error.message : "加载分支失败";
  } finally {
    if (generation === branchGeneration) branchesLoading.value = false;
  }
}

async function loadTree() {
  if (!selectedRepositoryId.value || !selectedBranch.value || !createMode.value) return;
  const repositoryId = selectedRepositoryId.value;
  const branch = selectedBranch.value;
  const generation = ++treeGeneration;
  treeLoading.value = true;
  selectedDirectory.value = "";
  tree.value = [];
  try {
    const response = await api.getRepositoryTree(props.appId, repositoryId, branch);
    if (generation !== treeGeneration || repositoryId !== selectedRepositoryId.value || branch !== selectedBranch.value) return;
    tree.value = normalizedTree(response.nodes);
  } catch (error) {
    if (generation === treeGeneration) errorMessage.value = error instanceof Error ? error.message : "加载自动化代码库目录失败";
  } finally {
    if (generation === treeGeneration) treeLoading.value = false;
  }
}

async function startCreate(repositoryId: string) {
  if (!props.canManage) return;
  createMode.value = true;
  selectedTemplateId.value = "";
  selectedRepositoryId.value = repositoryId;
  selectedDirectory.value = "";
  referenceName.value = repositoryName(repositoryId);
  versionDate.value = todayVersion();
  operation.value = null;
  errorMessage.value = "";
  await loadBranches(repositoryId);
}

async function selectTemplate(template: ApplicationWorkspaceTemplate) {
  createMode.value = false;
  selectedTemplateId.value = template.workspaceId;
  selectedRepositoryId.value = template.repositoryId;
  selectedDirectory.value = template.directoryPath;
  referenceName.value = template.workspaceName;
  versionDate.value = todayVersion();
  tree.value = [];
  operation.value = null;
  errorMessage.value = "";
  if (props.canManage) await loadBranches(template.repositoryId, template.activeVersion?.branch ?? template.branch);
}

function selectDirectory(node: RepositoryTreeNode) {
  if (node.type === "directory") selectedDirectory.value = node.path;
}

function createOperationId() {
  const random = typeof crypto !== "undefined" && typeof crypto.randomUUID === "function"
    ? crypto.randomUUID().replaceAll("-", "")
    : `${Date.now()}${Math.random().toString(16).slice(2)}`;
  return `wco_${random.replace(/[^A-Za-z0-9_-]/g, "").slice(0, 64)}`;
}

function clearOperationPoll() {
  if (operationTimer !== undefined) window.clearTimeout(operationTimer);
  operationTimer = undefined;
}

/** 异步创建沿用既有工作空间 operation，仅在前端把它表达为只读目录引用初始化。 */
async function pollOperation(operationId: string) {
  clearOperationPoll();
  try {
    const next = await api.getWorkspaceCreateOperation(operationId);
    // 弹窗关闭后不再让迟到的 operation 回包恢复轮询或覆盖下一次打开的状态。
    if (!props.open || !saving.value) return;
    operation.value = next;
    if (next.status === "SUCCEEDED") {
      saving.value = false;
      await loadCatalog();
      emit("changed");
      return;
    }
    if (next.status === "FAILED") {
      saving.value = false;
      errorMessage.value = next.errorMessage || "创建自动化目录引用失败";
      return;
    }
  } catch {
    // 请求刚被接受时 operation 可能尚未可见，下一轮继续读取。
  }
  if (!props.open || !saving.value) return;
  operationTimer = window.setTimeout(() => void pollOperation(operationId), OPERATION_POLL_INTERVAL_MS);
}

async function createReference() {
  if (!canCreateReference.value) return;
  saving.value = true;
  errorMessage.value = "";
  const operationId = createOperationId();
  operation.value = {
    operationId,
    status: "RUNNING",
    currentStep: "VALIDATING_INPUT",
    steps: []
  };
  try {
    await api.createApplicationWorkspace(props.appId, {
      repositoryId: selectedRepositoryId.value,
      branch: selectedBranch.value,
      directoryPath: selectedDirectory.value,
      workspaceName: referenceName.value.trim(),
      version: versionDate.value,
      operationId
    });
    await pollOperation(operationId);
  } catch (error) {
    saving.value = false;
    errorMessage.value = error instanceof Error ? error.message : "创建自动化目录引用失败";
  }
}

async function createVersion() {
  const template = selectedTemplate.value;
  if (!template || !canCreateVersion.value) return;
  saving.value = true;
  errorMessage.value = "";
  try {
    await api.createWorkspaceVersion(props.appId, template.workspaceId, {
      version: versionDate.value,
      branch: selectedBranch.value
    });
    await loadCatalog();
    emit("changed");
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "新增自动化引用版本失败";
  } finally {
    saving.value = false;
  }
}

async function activateVersion(template: ApplicationWorkspaceTemplate, version: ApplicationWorkspaceVersion) {
  if (!props.canManage || saving.value) return;
  saving.value = true;
  errorMessage.value = "";
  try {
    await api.activateAutomationWorkspaceVersion(props.appId, template.workspaceId, version.versionId);
    await loadCatalog();
    emit("changed");
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "切换自动化引用版本失败";
  } finally {
    saving.value = false;
  }
}

async function toggleReference(template: ApplicationWorkspaceTemplate) {
  if (!props.canManage || saving.value) return;
  saving.value = true;
  errorMessage.value = "";
  try {
    await api.updateApplicationWorkspace(props.appId, template.workspaceId, { enabled: template.enabled === false });
    await loadCatalog();
    emit("changed");
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "更新自动化引用状态失败";
  } finally {
    saving.value = false;
  }
}

watch(
  () => [props.open, props.appId, props.canManage] as const,
  ([open]) => {
    catalogGeneration++;
    branchGeneration++;
    treeGeneration++;
    clearOperationPoll();
    if (open && props.appId) void loadCatalog();
  },
  { immediate: true }
);

onBeforeUnmount(() => {
  catalogGeneration++;
  branchGeneration++;
  treeGeneration++;
  clearOperationPoll();
});
</script>

<template>
  <div class="automation-reference-layout">
    <aside class="automation-reference-sidebar" aria-label="自动化代码库引用">
      <div class="automation-reference-heading">
        <span>自动化代码库</span>
        <Spinner v-if="loading" class="h-3.5 w-3.5" />
      </div>
      <div v-if="loading && !templates.length" class="automation-reference-state">正在加载自动化引用…</div>
      <template v-else>
        <div v-if="canManage && !repositories.length" class="automation-reference-state">当前应用未关联自动化代码库。</div>
        <div v-if="!canManage && !templates.length" class="automation-reference-state">当前应用暂无自动化代码库引用。</div>
        <section v-for="repository in repositories" :key="repository.repositoryId" class="automation-repository-group">
          <div class="automation-repository-title">
            <FolderGit2 class="h-4 w-4" />
            <span>{{ repository.name }}</span>
          </div>
          <button
            v-for="template in templatesForRepository(repository.repositoryId)"
            :key="template.workspaceId"
            type="button"
            class="automation-reference-card"
            :class="{ 'is-selected': selectedTemplateId === template.workspaceId && !createMode }"
            :aria-pressed="selectedTemplateId === template.workspaceId && !createMode"
            :aria-label="`查看自动化引用 ${template.workspaceName}`"
            @click="selectTemplate(template)"
          >
            <strong>{{ template.workspaceName }}</strong>
            <small>{{ template.directoryPath }}</small>
            <span>{{ template.activeVersion?.version || "尚未激活" }}</span>
          </button>
          <Button size="sm" variant="ghost" class="automation-add-button" @click="startCreate(repository.repositoryId)">
            <Plus class="h-3.5 w-3.5" /> 新增目录引用
          </Button>
        </section>
        <template v-if="!canManage">
          <button
            v-for="template in templates"
            :key="template.workspaceId"
            type="button"
            class="automation-reference-card"
            :class="{ 'is-selected': selectedTemplateId === template.workspaceId }"
            :aria-pressed="selectedTemplateId === template.workspaceId"
            :aria-label="`查看自动化引用 ${template.workspaceName}`"
            @click="selectTemplate(template)"
          >
            <strong>{{ template.workspaceName }}</strong>
            <small>{{ template.directoryPath }}</small>
            <span>{{ template.activeVersion?.version || "尚未激活" }}</span>
          </button>
        </template>
      </template>
    </aside>

    <main class="automation-reference-content">
      <div v-if="errorMessage" class="automation-reference-error" role="alert">
        <span>{{ errorMessage }}</span>
        <Button size="sm" variant="ghost" :disabled="loading" @click="loadCatalog">
          <RefreshCw class="h-3.5 w-3.5" /> 重试
        </Button>
      </div>

      <section v-if="createMode && canManage" class="automation-reference-create" aria-label="新增自动化目录引用">
        <div class="automation-reference-section-heading">
          <div>
            <h3>新增自动化目录引用</h3>
            <p>选择分支中的任意已有目录；保存后该目录以应用级只读引用展示。</p>
          </div>
        </div>
        <div class="automation-reference-form-grid">
          <label>
            <span>版本库</span>
            <Input :model-value="selectedRepository?.name || selectedRepositoryId" readonly aria-label="自动化版本库" />
          </label>
          <label>
            <span>分支</span>
            <select v-model="selectedBranch" class="automation-reference-select" aria-label="自动化引用分支" :disabled="branchesLoading || saving" @change="loadTree">
              <option v-for="branch in branches" :key="branch" :value="branch">{{ branch }}</option>
            </select>
          </label>
          <label>
            <span>引用名称</span>
            <Input v-model="referenceName" aria-label="自动化引用名称" placeholder="例如 接口自动化" :disabled="saving" />
          </label>
          <label>
            <span>版本日期</span>
            <Input v-model="versionDate" aria-label="自动化引用版本日期" placeholder="YYYYMMDD" :disabled="saving" />
          </label>
        </div>
        <div class="automation-reference-tree-panel">
          <div class="automation-reference-tree-heading">
            <span>目录</span>
            <code>{{ selectedDirectory || "请选择一个已有目录" }}</code>
          </div>
          <div v-if="treeLoading" class="automation-reference-state">正在读取目录…</div>
          <RepositoryDirectoryTree
            v-else-if="tree.length"
            :nodes="tree"
            :selected-path="selectedDirectory"
            selection-mode="any-directory"
            @select="selectDirectory"
          />
          <div v-else class="automation-reference-state">当前分支没有可选择的目录。</div>
        </div>
        <div v-if="operation" class="automation-operation-state" role="status">
          {{ operation.status === "RUNNING" ? "正在初始化共享只读副本…" : operation.status }}
        </div>
        <div class="automation-reference-actions">
          <Button aria-label="保存自动化目录引用" :disabled="!canCreateReference" @click="createReference">{{ saving ? "保存中…" : "保存目录引用" }}</Button>
        </div>
      </section>

      <section v-else-if="selectedTemplate" class="automation-reference-detail" aria-label="自动化引用详情">
        <div class="automation-reference-section-heading">
          <div>
            <h3>{{ selectedTemplate.workspaceName }}</h3>
            <p>{{ repositoryName(selectedTemplate.repositoryId) }} · {{ selectedTemplate.directoryPath }}</p>
          </div>
          <Button v-if="canManage" size="sm" variant="ghost" :disabled="saving" @click="toggleReference(selectedTemplate)">
            {{ selectedTemplate.enabled === false ? "启用引用" : "停用引用" }}
          </Button>
        </div>
        <div class="automation-current-version">
          <span>当前版本</span>
          <strong v-if="selectedTemplate.activeVersion">
            {{ selectedTemplate.activeVersion.version }} · {{ selectedTemplate.activeVersion.branch }}
          </strong>
          <strong v-else>尚未激活版本</strong>
          <small v-if="selectedTemplate.activeVersion">副本 {{ selectedTemplate.activeVersion.replicaStatus || "UNKNOWN" }}</small>
        </div>
        <div class="automation-version-list">
          <div v-for="version in selectedVersions" :key="version.versionId" class="automation-version-row">
            <div>
              <strong>{{ version.version }}</strong>
              <span>{{ version.branch }}</span>
              <small>{{ version.replicaStatus || "UNKNOWN" }}</small>
            </div>
            <span v-if="selectedTemplate.activeVersion?.versionId === version.versionId" class="automation-current-badge">当前版本</span>
            <Button
              v-else-if="canManage"
              size="sm"
              variant="ghost"
              :disabled="saving || version.status !== 'ACTIVE'"
              @click="activateVersion(selectedTemplate, version)"
            >设为当前版本</Button>
          </div>
          <div v-if="!selectedVersions.length" class="automation-reference-state">暂无可用版本。</div>
        </div>
        <form v-if="canManage" class="automation-version-create" @submit.prevent="createVersion">
          <label>
            <span>新增版本分支</span>
            <select v-model="selectedBranch" class="automation-reference-select" aria-label="新增自动化版本分支" :disabled="branchesLoading || saving">
              <option v-for="branch in branches" :key="branch" :value="branch">{{ branch }}</option>
            </select>
          </label>
          <label>
            <span>版本日期</span>
            <Input v-model="versionDate" aria-label="新增自动化版本日期" placeholder="YYYYMMDD" :disabled="saving" />
          </label>
          <Button type="submit" :disabled="!canCreateVersion">{{ saving ? "创建中…" : "新增版本" }}</Button>
        </form>
      </section>

      <div v-else-if="!loading" class="automation-reference-state is-centered">
        {{ canManage ? "从左侧选择版本库并新增目录引用。" : "当前应用暂无可查看的自动化引用。" }}
      </div>
    </main>
  </div>
</template>

<style scoped>
.automation-reference-layout {
  display: grid;
  grid-template-columns: minmax(220px, 280px) minmax(0, 1fr);
  min-height: 0;
  height: 100%;
}
.automation-reference-sidebar {
  min-height: 0;
  overflow-y: auto;
  border-right: 1px solid var(--border);
  background: var(--muted);
}
.automation-reference-heading,
.automation-repository-title,
.automation-reference-tree-heading,
.automation-reference-section-heading,
.automation-version-row,
.automation-reference-actions {
  display: flex;
  align-items: center;
}
.automation-reference-heading {
  justify-content: space-between;
  padding: 12px 14px;
  border-bottom: 1px solid var(--border);
  font-size: 13px;
  font-weight: 600;
}
.automation-repository-group {
  display: grid;
  gap: 6px;
  padding: 10px;
  border-bottom: 1px solid var(--border);
}
.automation-repository-title {
  gap: 7px;
  padding: 2px 4px;
  font-size: 13px;
  font-weight: 600;
}
.automation-reference-card {
  display: grid;
  gap: 3px;
  width: 100%;
  padding: 9px 10px;
  border: 1px solid var(--border);
  border-radius: 7px;
  background: var(--background);
  color: var(--foreground);
  text-align: left;
  cursor: pointer;
}
.automation-reference-card:hover,
.automation-reference-card.is-selected {
  border-color: var(--primary);
  background: var(--accent);
}
.automation-reference-card strong {
  font-size: 13px;
}
.automation-reference-card small,
.automation-reference-card span {
  overflow: hidden;
  color: var(--muted-foreground);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.automation-add-button {
  justify-content: flex-start;
}
.automation-reference-content {
  min-width: 0;
  min-height: 0;
  overflow-y: auto;
  padding: 18px;
}
.automation-reference-create,
.automation-reference-detail {
  display: grid;
  gap: 16px;
}
.automation-reference-section-heading {
  justify-content: space-between;
  gap: 16px;
}
.automation-reference-section-heading h3 {
  margin: 0;
  font-size: 18px;
}
.automation-reference-section-heading p {
  margin: 4px 0 0;
  color: var(--muted-foreground);
  font-size: 13px;
}
.automation-reference-form-grid,
.automation-version-create {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.automation-reference-form-grid label,
.automation-version-create label {
  display: grid;
  gap: 6px;
  color: var(--muted-foreground);
  font-size: 12px;
}
.automation-reference-select {
  min-height: 34px;
  padding: 0 10px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--background);
  color: var(--foreground);
}
.automation-reference-tree-panel {
  min-height: 240px;
  max-height: 52vh;
  overflow-y: auto;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--background);
}
.automation-reference-tree-heading {
  position: sticky;
  top: 0;
  z-index: 1;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 12px;
  border-bottom: 1px solid var(--border);
  background: var(--background);
  font-size: 13px;
  font-weight: 600;
}
.automation-reference-tree-heading code {
  overflow: hidden;
  color: var(--primary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.automation-reference-state,
.automation-reference-error,
.automation-operation-state {
  padding: 12px;
  color: var(--muted-foreground);
  font-size: 13px;
}
.automation-reference-state.is-centered {
  display: grid;
  min-height: 220px;
  place-items: center;
}
.automation-reference-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  border: 1px solid var(--destructive);
  border-radius: 7px;
  color: var(--destructive);
}
.automation-reference-actions {
  justify-content: flex-end;
}
.automation-current-version {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--muted);
  font-size: 13px;
}
.automation-current-version span,
.automation-current-version small {
  color: var(--muted-foreground);
}
.automation-version-list {
  display: grid;
  gap: 8px;
}
.automation-version-row {
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: 7px;
}
.automation-version-row > div {
  display: flex;
  align-items: baseline;
  gap: 10px;
}
.automation-version-row span,
.automation-version-row small {
  color: var(--muted-foreground);
  font-size: 12px;
}
.automation-current-badge {
  padding: 2px 7px;
  border-radius: 999px;
  background: var(--accent);
  color: var(--primary) !important;
  font-weight: 600;
}
.automation-version-create {
  align-items: end;
  padding-top: 4px;
}
@media (max-width: 900px) {
  .automation-reference-layout {
    grid-template-columns: 1fr;
  }
  .automation-reference-sidebar {
    max-height: 220px;
    border-right: 0;
    border-bottom: 1px solid var(--border);
  }
}
</style>
