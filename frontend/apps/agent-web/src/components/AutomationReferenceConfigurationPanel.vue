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
import { Check, FolderGit2, Plus, RefreshCw } from "lucide-vue-next";
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

function formattedTime(value?: string | null) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
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
  <div class="reference-automation-layout">
    <aside class="reference-repository-column" aria-label="自动化代码库引用">
      <div class="reference-column-heading">
        <span>自动化代码库</span>
        <Spinner v-if="loading" class="h-3.5 w-3.5" />
      </div>
      <div v-if="loading && !templates.length" class="reference-state is-centered" role="status">
        <Spinner class="h-4 w-4" />
        <span>正在加载自动化引用…</span>
      </div>
      <template v-else>
        <div v-if="canManage && !repositories.length" class="reference-state">当前应用未关联自动化代码库。</div>
        <div v-if="!canManage && !templates.length" class="reference-state">当前应用暂无自动化代码库引用。</div>
        <div v-if="repositories.length || templates.length" class="reference-repository-list">
          <section
            v-for="repository in repositories"
            :key="repository.repositoryId"
            class="automation-repo-group"
          >
            <div class="automation-repo-group-title">
              <FolderGit2 class="h-3.5 w-3.5 shrink-0" />
              <span>{{ repository.name }}</span>
            </div>
            <article
              v-for="template in templatesForRepository(repository.repositoryId)"
              :key="template.workspaceId"
              class="reference-repository-card"
              :class="{ 'is-selected': selectedTemplateId === template.workspaceId && !createMode }"
            >
              <button
                type="button"
                class="reference-repository-main"
                :aria-pressed="selectedTemplateId === template.workspaceId && !createMode"
                :aria-label="`查看自动化引用 ${template.workspaceName}`"
                :disabled="saving"
                @click="selectTemplate(template)"
              >
                <FolderGit2 class="h-4 w-4 shrink-0" />
                <span class="min-w-0">
                  <strong>{{ template.workspaceName }}</strong>
                  <small :title="template.directoryPath">{{ template.directoryPath }}</small>
                </span>
                <span class="reference-status">{{ template.activeVersion?.version || "未激活" }}</span>
              </button>
              <div class="reference-repository-meta">
                <span>{{ template.enabled === false ? "已停用" : (template.activeVersion?.branch || template.branch || "main") }}</span>
                <span :class="{ 'is-online': template.activeVersion?.replicaStatus === 'READY' }">
                  {{ template.activeVersion?.replicaStatus || "UNKNOWN" }}
                </span>
              </div>
            </article>
            <Button
              v-if="canManage"
              size="sm"
              variant="ghost"
              class="automation-add-button"
              :class="{ 'is-active': createMode && selectedRepositoryId === repository.repositoryId }"
              :disabled="saving"
              @click="startCreate(repository.repositoryId)"
            >
              <Plus class="h-3.5 w-3.5" /> 新增目录引用
            </Button>
          </section>

          <template v-if="!canManage">
            <article
              v-for="template in templates"
              :key="template.workspaceId"
              class="reference-repository-card"
              :class="{ 'is-selected': selectedTemplateId === template.workspaceId }"
            >
              <button
                type="button"
                class="reference-repository-main"
                :aria-pressed="selectedTemplateId === template.workspaceId"
                :aria-label="`查看自动化引用 ${template.workspaceName}`"
                :disabled="saving"
                @click="selectTemplate(template)"
              >
                <FolderGit2 class="h-4 w-4 shrink-0" />
                <span class="min-w-0">
                  <strong>{{ template.workspaceName }}</strong>
                  <small :title="template.directoryPath">{{ template.directoryPath }}</small>
                </span>
                <span class="reference-status">{{ template.activeVersion?.version || "未激活" }}</span>
              </button>
              <div class="reference-repository-meta">
                <span>{{ template.enabled === false ? "已停用" : (template.activeVersion?.branch || template.branch || "main") }}</span>
                <span :class="{ 'is-online': template.activeVersion?.replicaStatus === 'READY' }">
                  {{ template.activeVersion?.replicaStatus || "UNKNOWN" }}
                </span>
              </div>
            </article>
          </template>
        </div>
      </template>
    </aside>

    <main class="reference-configuration-column">
      <div v-if="errorMessage" class="reference-state is-error" role="alert">
        <span>{{ errorMessage }}</span>
        <button
          type="button"
          class="reference-inline-action"
          :disabled="loading"
          @click="loadCatalog"
        >
          重试
        </button>
      </div>

      <!-- Create Mode: 新增自动化目录引用 (双栏树与表单) -->
      <template v-if="createMode && canManage">
        <div class="reference-selected-heading">
          <div>
            <strong>新增自动化目录引用</strong>
            <span>选择分支中的已有目录；保存后该目录以应用级只读引用展示。</span>
          </div>
          <div v-if="templates.length > 0" class="reference-selected-actions">
            <Button
              size="sm"
              variant="ghost"
              :disabled="saving"
              @click="selectTemplate(templates[0])"
            >
              返回引用列表
            </Button>
          </div>
        </div>

        <div class="reference-ready-layout">
          <section class="reference-tree-panel" aria-label="自动化目录树">
            <div class="reference-panel-title">
              <span class="automation-panel-title-left">
                <span>目录</span>
                <Spinner v-if="treeLoading" class="h-3 w-3 ml-1.5" />
              </span>
              <code v-if="selectedDirectory" class="reference-title-path">{{ selectedDirectory }}</code>
            </div>
            <div v-if="treeLoading" class="reference-compact-state is-centered" role="status">
              <Spinner class="h-4 w-4" />
              <span>正在读取目录…</span>
            </div>
            <div v-else-if="tree.length === 0" class="reference-compact-state">当前分支没有可选择的目录。</div>
            <div v-else class="reference-tree-wrap">
              <RepositoryDirectoryTree
                :nodes="tree"
                :selected-path="selectedDirectory"
                selection-mode="any-directory"
                @select="selectDirectory"
              />
            </div>
          </section>

          <section class="reference-form-panel" aria-label="新增自动化目录引用表单">
            <div class="reference-panel-title">配置</div>
            <form class="reference-form" @submit.prevent="createReference">
              <label>
                <span>版本库（repository）</span>
                <Input :model-value="selectedRepository?.name || selectedRepositoryId" readonly aria-label="自动化版本库" />
              </label>
              <label>
                <span class="automation-field-label">
                  <span>分支（branch）</span>
                  <span v-if="branchesLoading" class="automation-inline-loading" role="status">
                    <Spinner class="h-3 w-3" />
                    <small>拉取分支中…</small>
                  </span>
                </span>
                <select
                  v-model="selectedBranch"
                  class="reference-select"
                  aria-label="自动化引用分支"
                  :disabled="branchesLoading || saving"
                  @change="loadTree"
                >
                  <option v-if="branchesLoading && !branches.length" value="" disabled>正在拉取分支…</option>
                  <option v-for="branch in branches" :key="branch" :value="branch">{{ branch }}</option>
                </select>
              </label>
              <label>
                <span class="automation-field-label">
                  <span>引用目录（path） <b aria-hidden="true">*</b></span>
                  <span v-if="treeLoading" class="automation-inline-loading" role="status">
                    <Spinner class="h-3 w-3" />
                    <small>读取目录中…</small>
                  </span>
                </span>
                <Input :model-value="selectedDirectory" readonly :placeholder="treeLoading ? '正在读取目录…' : '请在左侧选择目录'" />
              </label>
              <label>
                <span>引用名称（workspace-name） <b aria-hidden="true">*</b></span>
                <Input v-model="referenceName" aria-label="自动化引用名称" placeholder="例如 接口自动化" :disabled="saving" />
              </label>
              <label>
                <span>版本日期（version） <b aria-hidden="true">*</b></span>
                <Input v-model="versionDate" aria-label="自动化引用版本日期" placeholder="YYYYMMDD" :disabled="saving" />
              </label>
              <div v-if="operation" class="reference-form-notice is-loading" role="status">
                <Spinner v-if="operation.status === 'RUNNING'" class="h-3.5 w-3.5 shrink-0" />
                <span>{{ operation.status === "RUNNING" ? "正在初始化共享只读副本…" : operation.status }}</span>
              </div>
              <div class="reference-form-actions">
                <Button
                  aria-label="保存自动化目录引用"
                  :disabled="!canCreateReference"
                  @click="createReference"
                >
                  <Spinner v-if="saving" class="h-3.5 w-3.5 mr-1.5" />
                  {{ saving ? "保存中…" : "保存目录引用" }}
                </Button>
              </div>
            </form>
          </section>
        </div>
      </template>

      <!-- Detail View: 查看已配置的自动化引用 -->
      <template v-else-if="selectedTemplate">
        <div class="reference-selected-heading">
          <div>
            <strong>{{ selectedTemplate.workspaceName }}</strong>
            <span>{{ repositoryName(selectedTemplate.repositoryId) }} · {{ selectedTemplate.directoryPath }}</span>
          </div>
          <div v-if="canManage" class="reference-selected-actions">
            <Button
              size="sm"
              variant="ghost"
              :disabled="saving"
              @click="toggleReference(selectedTemplate)"
            >
              <Spinner v-if="saving" class="h-3 w-3 mr-1" />
              {{ selectedTemplate.enabled === false ? "启用引用" : "停用引用" }}
            </Button>
          </div>
        </div>

        <section class="reference-pointer-panel" aria-label="当前版本">
          <div class="reference-pointer-target">
            <span>当前版本</span>
            <strong v-if="selectedTemplate.activeVersion">
              {{ selectedTemplate.activeVersion.version }} · {{ selectedTemplate.activeVersion.branch }}
            </strong>
            <strong v-else>尚未激活版本</strong>
            <span v-if="selectedTemplate.activeVersion" class="reference-pointer-status">
              副本 {{ selectedTemplate.activeVersion.replicaStatus || "UNKNOWN" }}
            </span>
            <span
              v-if="selectedTemplate.activeVersion"
              class="reference-pointer-match"
              :class="{
                'is-match': selectedTemplate.activeVersion.replicaStatus === 'READY',
                'is-mismatch': selectedTemplate.activeVersion.replicaStatus === 'FAILED'
              }"
            >
              <Check v-if="selectedTemplate.activeVersion.replicaStatus === 'READY'" class="h-3 w-3" />
              {{ selectedTemplate.activeVersion.replicaStatus === "READY" ? "就绪" : selectedTemplate.activeVersion.replicaStatus === "FAILED" ? "异常" : "同步中" }}
            </span>
          </div>
        </section>

        <div class="automation-detail-body">
          <section class="automation-versions-section" aria-label="版本列表">
            <div class="reference-panel-title">
              <span>版本列表</span>
            </div>
            <div v-if="!selectedVersions.length" class="reference-compact-state">暂无可用版本。</div>
            <div v-else class="reference-pointer-table-wrap">
              <table class="reference-pointer-table">
                <thead>
                  <tr>
                    <th>版本号</th>
                    <th>分支</th>
                    <th>副本状态</th>
                    <th>创建时间</th>
                    <th>状态</th>
                    <th v-if="canManage" style="text-align: right;">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="version in selectedVersions" :key="version.versionId">
                    <td>
                      <strong>{{ version.version }}</strong>
                    </td>
                    <td>
                      <code>{{ version.branch }}</code>
                    </td>
                    <td>
                      <span class="reference-pointer-status">{{ version.replicaStatus || "UNKNOWN" }}</span>
                    </td>
                    <td>
                      <time>{{ formattedTime(version.createdAt) }}</time>
                    </td>
                    <td>
                      <span
                        v-if="selectedTemplate.activeVersion?.versionId === version.versionId"
                        class="reference-pointer-match is-match"
                      >
                        <Check class="h-3 w-3" /> 当前版本
                      </span>
                      <span v-else class="reference-pointer-status">
                        {{ version.status === "ACTIVE" ? "可用" : version.status }}
                      </span>
                    </td>
                    <td v-if="canManage" style="text-align: right;">
                      <Button
                        v-if="selectedTemplate.activeVersion?.versionId !== version.versionId"
                        size="sm"
                        variant="ghost"
                        class="reference-inline-action"
                        :disabled="saving || version.status !== 'ACTIVE'"
                        @click="activateVersion(selectedTemplate, version)"
                      >
                        <Spinner v-if="saving" class="h-3 w-3 mr-1 inline-block" />
                        设为当前版本
                      </Button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>

          <section v-if="canManage" class="automation-create-version-section" aria-label="新增版本">
            <div class="reference-panel-title">
              <span>新增版本</span>
            </div>
            <form class="reference-form automation-version-form" @submit.prevent="createVersion">
              <div class="automation-version-fields">
                <label>
                  <span class="automation-field-label">
                    <span>新增版本分支（branch）</span>
                    <span v-if="branchesLoading" class="automation-inline-loading" role="status">
                      <Spinner class="h-3 w-3" />
                      <small>拉取分支中…</small>
                    </span>
                  </span>
                  <select
                    v-model="selectedBranch"
                    class="reference-select"
                    aria-label="新增自动化版本分支"
                    :disabled="branchesLoading || saving"
                  >
                    <option v-if="branchesLoading && !branches.length" value="" disabled>正在拉取分支…</option>
                    <option v-for="branch in branches" :key="branch" :value="branch">{{ branch }}</option>
                  </select>
                </label>
                <label>
                  <span>版本日期（YYYYMMDD） <b aria-hidden="true">*</b></span>
                  <Input
                    v-model="versionDate"
                    aria-label="新增自动化版本日期"
                    placeholder="YYYYMMDD"
                    :disabled="saving"
                  />
                </label>
              </div>
              <div class="reference-form-actions">
                <Button type="submit" :disabled="!canCreateVersion">
                  <Spinner v-if="saving" class="h-3.5 w-3.5 mr-1.5" />
                  {{ saving ? "创建中…" : "新增版本" }}
                </Button>
              </div>
            </form>
          </section>
        </div>
      </template>

      <div v-else-if="!loading" class="reference-state is-centered">
        {{ canManage ? "从左侧选择版本库并新增目录引用。" : "当前应用暂无可查看的自动化引用。" }}
      </div>
    </main>
  </div>
</template>

<style scoped>
.reference-automation-layout {
  display: grid;
  grid-template-columns: minmax(290px, 34%) minmax(0, 1fr);
  min-height: 0;
  height: 100%;
}

.reference-repository-column,
.reference-configuration-column {
  min-height: 0;
  overflow: auto;
}

.reference-repository-column {
  border-right: 1px solid var(--ta-border);
  background: var(--ta-panel-2);
}

.reference-column-heading,
.reference-selected-heading,
.reference-panel-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--ta-muted);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.reference-column-heading {
  position: sticky;
  top: 0;
  z-index: 2;
  height: 34px;
  border-bottom: 1px solid var(--ta-border);
  padding: 0 12px;
  background: var(--ta-panel-2);
}

.reference-repository-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px;
}

.automation-repo-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-bottom: 6px;
  border-bottom: 1px solid var(--ta-border);
}

.automation-repo-group:last-child {
  border-bottom: 0;
}

.automation-repo-group-title {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 6px 2px;
  color: var(--ta-muted);
  font-size: 11px;
  font-weight: 600;
}

.reference-repository-card {
  position: relative;
  overflow: hidden;
  border: 1px solid var(--ta-border);
  border-radius: 7px;
  background: var(--ta-surface);
}

.reference-repository-card.is-selected {
  border-color: var(--ta-border-strong);
  box-shadow: inset 3px 0 0 var(--ta-ink);
}

.reference-repository-main {
  display: grid;
  width: 100%;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  border: 0;
  padding: 9px 10px 6px;
  background: transparent;
  color: var(--ta-text);
  text-align: left;
  cursor: pointer;
}

.reference-repository-main:hover {
  background: var(--ta-hover);
}

.reference-repository-main strong,
.reference-repository-main small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.reference-repository-main strong {
  font-size: 12px;
  font-weight: 600;
}

.reference-repository-main small {
  margin-top: 3px;
  color: var(--ta-muted);
  font-family: "Geist Mono", monospace;
  font-size: 10px;
}

.reference-status {
  color: var(--ta-muted);
  font-family: "Geist Mono", monospace;
  font-size: 10px;
}

.reference-repository-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 10px 7px 34px;
  color: var(--ta-muted);
  font-size: 10px;
}

.reference-repository-meta .is-online {
  color: var(--ta-ok);
}

.automation-add-button {
  justify-content: flex-start;
  font-size: 11px;
  color: var(--ta-muted);
  margin-top: 2px;
}

.automation-add-button:hover,
.automation-add-button.is-active {
  color: var(--ta-text);
  background: var(--ta-hover);
}

.reference-configuration-column {
  display: flex;
  flex-direction: column;
  background: var(--ta-panel);
}

.reference-selected-heading {
  min-height: 44px;
  flex-shrink: 0;
  border-bottom: 1px solid var(--ta-border);
  padding: 0 14px;
  background: var(--ta-panel-2);
  color: var(--ta-text);
  letter-spacing: normal;
  text-transform: none;
}

.reference-selected-heading strong,
.reference-selected-heading span {
  display: block;
}

.reference-selected-heading strong {
  font-size: 12px;
}

.reference-selected-heading span {
  margin-top: 2px;
  color: var(--ta-muted);
  font-family: "Geist Mono", monospace;
  font-size: 10px;
  font-weight: 400;
}

.reference-selected-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.reference-pointer-panel {
  flex-shrink: 0;
  border-bottom: 1px solid var(--ta-border);
  background: var(--ta-surface);
}

.reference-pointer-target {
  min-height: 34px;
  gap: 8px;
  border-bottom: 1px solid var(--ta-border);
  padding: 0 12px;
  color: var(--ta-muted);
  font-size: 10px;
  display: flex;
  align-items: center;
}

.reference-pointer-target strong,
.reference-pointer-target code,
.reference-pointer-table code,
.reference-pointer-table time {
  color: var(--ta-text);
  font-family: "Geist Mono", monospace;
  font-size: 10px;
}

.reference-pointer-status {
  color: var(--ta-muted);
  font-family: "Geist Mono", monospace;
}

.reference-pointer-match {
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 10px;
}

.reference-pointer-match.is-match {
  color: var(--ta-ok);
}

.reference-pointer-match.is-mismatch {
  color: var(--ta-error);
}

.automation-detail-body {
  display: flex;
  flex-direction: column;
  min-height: 0;
  flex: 1;
  overflow: auto;
}

.automation-versions-section {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.automation-create-version-section {
  flex-shrink: 0;
  border-top: 1px solid var(--ta-border);
  background: var(--ta-panel-2);
}

.reference-pointer-table-wrap {
  overflow: auto;
  flex: 1;
}

.reference-pointer-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 10px;
  text-align: left;
}

.reference-pointer-table th,
.reference-pointer-table td {
  border-bottom: 1px solid var(--ta-border);
  padding: 6px 10px;
  vertical-align: middle;
  white-space: nowrap;
}

.reference-pointer-table th {
  position: sticky;
  top: 0;
  z-index: 1;
  background: var(--ta-panel-2);
  color: var(--ta-muted);
  font-weight: 600;
}

.reference-inline-action {
  border: 0;
  padding: 0;
  background: transparent;
  color: var(--ta-text);
  font-size: 11px;
  font-weight: 600;
  text-decoration: underline;
  text-underline-offset: 2px;
  cursor: pointer;
}

.reference-ready-layout {
  display: grid;
  min-height: 0;
  flex: 1;
  grid-template-columns: minmax(210px, 42%) minmax(280px, 1fr);
}

.reference-tree-panel,
.reference-form-panel {
  min-height: 0;
  overflow: auto;
}

.reference-tree-panel {
  border-right: 1px solid var(--ta-border);
  background: var(--ta-tree-bg);
  font-family: var(--ta-tree-font-family);
  font-size: var(--ta-tree-font-size);
}

.reference-tree-wrap {
  padding: 6px 4px;
}

.reference-form-panel {
  background: var(--ta-panel-2);
}

.reference-panel-title {
  position: sticky;
  top: 0;
  z-index: 1;
  height: 32px;
  border-bottom: 1px solid var(--ta-border);
  padding: 0 12px;
  background: var(--ta-panel-2);
}

.automation-panel-title-left {
  display: inline-flex;
  align-items: center;
}

.reference-title-path {
  overflow: hidden;
  color: var(--ta-ink);
  font-family: "Geist Mono", monospace;
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 260px;
}

.reference-form {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px;
}

.reference-form label > span {
  display: block;
  margin-bottom: 5px;
  color: var(--ta-muted);
  font-family: "Geist Mono", monospace;
  font-size: 10px;
}

.automation-field-label {
  display: flex !important;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 5px;
}

.automation-inline-loading {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--ta-muted);
  font-family: var(--font-sans, -apple-system, sans-serif);
  font-size: 10px;
  font-weight: 400;
}

.automation-inline-loading small {
  color: var(--ta-muted);
  font-size: 10px;
}

.reference-form label b {
  color: var(--ta-error);
}

.reference-select {
  width: 100%;
  height: 32px;
  border: 1px solid var(--ta-border);
  border-radius: 5px;
  padding: 0 8px;
  outline: none;
  background: var(--ta-surface);
  color: var(--ta-text);
  font-size: 12px;
}

.reference-select:focus {
  border-color: var(--ta-border-strong);
}

.automation-version-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.reference-form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
  margin-top: 6px;
}

.reference-state,
.reference-compact-state {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 16px 12px;
  color: var(--ta-muted);
  font-size: 12px;
}

.reference-state.is-centered,
.reference-compact-state.is-centered {
  min-height: 120px;
  align-items: center;
  justify-content: center;
  text-align: center;
  gap: 8px;
}

.reference-state.is-error,
.reference-compact-state.is-error,
.reference-form-notice.is-error {
  color: var(--ta-error);
}

.reference-state code,
.reference-compact-state code,
.reference-form-notice code {
  font-family: "Geist Mono", monospace;
  font-size: 10px;
}

.reference-form-notice {
  display: flex;
  flex-direction: column;
  gap: 3px;
  border: 1px solid var(--ta-border);
  border-radius: 5px;
  padding: 7px 8px;
  color: var(--ta-muted);
  font-size: 11px;
}

.reference-form-notice.is-loading {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 8px;
  border-color: var(--ta-cyan);
  background: rgba(79, 111, 122, 0.06);
  color: var(--ta-cyan);
}

.reference-form-notice.is-success {
  color: var(--ta-ok);
}

@media (max-width: 780px) {
  .reference-automation-layout {
    grid-template-columns: 1fr;
  }
  .reference-repository-column {
    max-height: 38vh;
    border-right: 0;
    border-bottom: 1px solid var(--ta-border);
  }
  .reference-ready-layout {
    grid-template-columns: 1fr;
  }
  .reference-tree-panel {
    min-height: 180px;
    border-right: 0;
    border-bottom: 1px solid var(--ta-border);
  }
}

@media (max-width: 780px) {
  .reference-automation-layout {
    grid-template-columns: 1fr;
  }
  .reference-repository-column {
    max-height: 38vh;
    border-right: 0;
    border-bottom: 1px solid var(--ta-border);
  }
  .reference-ready-layout {
    grid-template-columns: 1fr;
  }
  .reference-tree-panel {
    min-height: 180px;
    border-right: 0;
    border-bottom: 1px solid var(--ta-border);
  }
}
</style>
